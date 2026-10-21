# ADR-012: Aufbewahrung

> **Bestätigt am 2026-10-21 (Gate 1, F-02):** Vorgabewert **30 Tage** —
> **getrennt** für Ereignisse und für Zustellprotokolle. Fristbeginn ist der
> **letzte Zustellversuch**. Beide Fristen bleiben **konfigurierbar**.

## Kontext

FR-031 verlangt die Liste der toten Zustellungen; die Rollentabelle nennt
zusätzlich „Aufbewahrung erzwingen“ als Aufgabe des Service-Admins.

Abschnitt 13 der Anforderung stellte die Aufbewahrung ausdrücklich offen:
*„30 Tage — für Ereignisse, für Zustellungen oder für beides? Und beginnt die
Frist mit dem Einliefern oder mit dem letzten Versuch?"*

D-12 legt fest: die Frist ist ein **konfigurierbarer** Parameter.

AC-016-5 verlangt: mit einer konfigurierten Frist (im Test 30 Tage) werden
Ereignisse, Zustellvorgänge und Protokolleinträge, die älter als die Frist sind,
**gelöscht**; jüngere bleiben erhalten; die Frist ist **konfigurierbar**.

Das Risiko R-04: ohne Frist wächst die Datenbank unbegrenzt (Kosten) und die
Löschpflicht wird verfehlt (Datenschutz).

**O-02 ist entschieden (2026-10-21, F-02).** **O-05** (AVV) ist eine
Livegang-Bedingung, kein Software-Thema — die technische Vorbereitung ist
Teil dieser ADR.

## Entscheidung

### 1. Zwei Fristen, getrennt konfigurierbar

| Frist | Betrifft | Vorgabewert (bestätigt) |
|---|---|---|
| `event_retention_days` | `events` (inkl. der zugehörigen Zustellläufe und Versuche) | **30 Tage** ab dem **letzten Versuch** |
| `delivery_retention_days` | `delivery_runs`, `delivery_attempts`, Zähler | **30 Tage** ab dem **letzten Versuch** |

Getrennt, weil die Frage des Fachbereichs ausdrücklich beide Optionen offen
hält („für Ereignisse, für Zustellungen oder für beides?“). Ein Wert je
Datenart macht die Antwort **konfigurierbar**, ohne sie zu entscheiden.

### 2. Der Bezugspunkt ist der **letzte Versuch**

Die Frage „beginnt die Frist mit dem Einliefern oder mit dem letzten
Versuch?“ ist entschieden: die Frist beginnt mit dem **letzten Zustellversuch**
(`retention.anchor = last_attempt_at`). Das ist der Zeitpunkt, an dem die
Zustellung tatsächlich endete. Die Einlieferung eines Ereignisses, das nie
zugestellt wurde, soll nicht unbegrenzt liegen.

Der Mechanismus kennt weiterhin **beide** Bezugspunkte
(`retention.anchor = accepted_at | last_attempt_at`); `last_attempt_at` ist der
bestätigte Vorgabewert und bleibt über die Konfiguration änderbar.

### 3. Zwei Stufen: markieren und endgültig löschen

| Stufe | Was passiert | Wann |
|---|---|---|
| **Markieren** | `deleted_at = now()` setzen | Der Lauf bzw. das Ereignis ist **sofort unsichtbar** — die RLS-Policy filtert `deleted_at IS NULL` |
| **Purge** | physische Löschung der Zeilen | nach einer konfigurierbaren **Karenzzeit** nach dem Markieren |

Warum zwei Stufen: die Frist ist eine **fachliche** Zusage („30 Tage“), die
endgültige Löschung eine **technische**. Die Karenzzeit erlaubt, in einer
Fehleruntersuchung noch zuzugreifen, ohne die Zusage zu brechen. Karenzzeit
ist konfigurierbar; ohne gesetzte Frist läuft der Vorgang als **No-Op**.

### 4. Reihenfolge und Kaskade

1. `delivery_attempts` löschen (Kind von `delivery_runs`)
2. `delivery_runs` löschen (Kind von `events`)
3. `events` löschen

`ON DELETE CASCADE` von `delivery_attempts → delivery_runs → events` deckt die
Kaskade ab; der Service löst sie **explizit** aus, damit die Zähler je Stufe
stimmen.

**Die Kaskade ist Teil der Semantik, nicht nur der Implementierung:** Das
Löschen eines **Ereignisses** nimmt seine **Zustellläufe und Versuche mit** —
auch wenn die Protokollfrist für sie noch nicht abgelaufen ist. Die
Protokollfrist gilt deshalb nur für Läufe, deren Ereignis **noch steht**
(E-04). Umgekehrt gilt: das Löschen eines Zustelllaufs nimmt **nicht** sein
Ereignis mit — die Ereignisfrist gilt unabhängig davon.

**Der Vertrag benennt das ausdrücklich** (`POST /api/v1/retention/enforcement`):
Anker je Datenart, die beiden Stufen mit getrennten Zählern (`marked_*` /
`purged_*`), die Karenzzeit (`purge_delay_days`), die Kaskade und den
Geltungsbereich (`scope: all` — alle Mandanten). Ohne diese Angaben ist
„älter als die Frist wird gelöscht" **nicht entscheidbar** (S-03, V-02).

### 5. Durchsetzung

| Weg | Auslöser | Rolle |
|---|---|---|
| **Automatisch** | zeitgesteuerter Vorgang (Standard: täglich) | System |
| **Manuell** | `POST /api/v1/retention/enforcement` (nur `service_admin`) | Service-Admin |

Der manuelle Aufruf hat `dry_run = true` als **Default**: er zählt, aber löscht
nicht. Ein versehentlicher Aufruf mit `dry_run = false` ist eine bewusste
Entscheidung. Die Antwort nennt die **angewandten Fristen**, die zugehörigen
Stichtage und die Zahl der betroffenen Datensätze.

### 6. Vorgabewert 30 Tage — je Datenart, getrennt

| Wert | Konfiguration | Vorgabe (bestätigt) |
|---|---|---|
| Frist Ereignisse | `echobox.retention.event-days` | **30** |
| Frist Zustellprotokolle | `echobox.retention.delivery-days` | **30** |
| Bezugspunkt | `echobox.retention.anchor` | **`last_attempt_at`** |
| Karenzzeit | `echobox.retention.purge-delay` | konfigurierbar (der Vorgabewert ist **nicht** entschieden) |

- Der Vorgabewert gilt **pro Umgebung**; je Mandant kann `retention_settings`
  beide Fristen überschreiben. `NULL` bedeutet: **kein Override** — es gilt
  die Vorgabe.
- Der automatische Durchlauf läuft damit **an**, nicht erst nach einer
  Mandantenentscheidung.
- Der manuelle Aufruf meldet die **beiden** angewandten Fristen und Stichtage.
- **Kein** Wert ist im Code verdrahtet; alle vier Werte kommen aus der
  Konfiguration.

### 7. Was **nicht** gelöscht wird

| Nicht gelöscht | Warum |
|---|---|
| Endpunkte und ihre Konfiguration | Ein Endpunkt ist eine Registrierung, kein Ereignis |
| `endpoint_secret_history` | Der Rotationsnachweis muss länger leben als ein Ereignis |
| `audit_log` | Der Nachweis der Aktionen hat eine **eigene** Frist (separat konfigurierbar) |

## Festlegung (Gate 1, 2026-10-21)

> **F-02 (O-02):** 30 Tage, getrennt für Ereignisse und Zustellprotokolle,
> jeweils ab dem **letzten Zustellversuch**; Durchsetzung automatisch,
> Erzwingung durch den Service-Admin zusätzlich möglich.
> *Warum das der Fachbereich (und der Datenschutz) entscheidet:* Die Frist ist
> eine Abwägung zwischen Nachvollziehbarkeit (Betrieb, Support) und
> Löschpflicht (Datenschutz, Datenvolumen) — das ist eine Produkt- und
> Rechtsentscheidung. Die Architektur hat sie so entworfen, dass **kein** Wert
> verdrahtet werden musste: beide Fristen, der Bezugspunkt und die Karenzzeit
> sind konfigurierbar.
>
> **O-05 (Auftragsverarbeitung/AVV)** bleibt eine **Freigabebedingung vor dem
> produktiven Betrieb (Gate 2)** und ist kein Software-Blocker. Technisch ist
> der Dienst vorbereitet: Datenminimierung (nur Metadaten und die zur
> Zustellung nötige Nutzlast), Mandantentrennung (ADR-002), konfigurierbare
> Löschfristen und Protokollierbarkeit.

## Konsequenzen

- **Positiv:** Der Dienst wächst nicht unbegrenzt — ab dem ersten Tag ist eine
  Frist gesetzt.
- **Positiv:** Der Mechanismus ist unabhängig vom Wert **testbar** (AC-016-5
  setzt die Frist im Test).
- **Positiv:** `dry_run` als Default macht den manuellen Aufruf gefahrlos.
- **Negativ:** Die Karenzzeit verlängert die tatsächliche Löschung über die
  Zusage hinaus. Sie ist deshalb bewusst **kürzer** als die Frist zu wählen und
  im Vertrag ausgewiesen.
- **Negativ:** Der automatische Durchlauf ist ein zusätzlicher Hintergrundvorgang
  — er gehört in dieselbe Betriebsrolle wie der Zustell-Worker.
- **Negativ:** Ereignisse, die nie zugestellt wurden, fallen unter dieselbe
  Frist wie Zustellprotokolle. Das ist die bewusst gewählte Folge des
  Bezugspunkts „letzter Versuch“ (mit `accepted_at` als Alternative).

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Feste 30 Tage hart verdrahtet** | D-12 verbietet das; der Wert gehört in die Konfiguration, nicht in den Code. |
| **Ein Wert für Ereignisse und Protokolle zusammen** | Die Frage des Fachbereichs nennt beide Datenarten getrennt; ein Wert je Datenart bleibt möglich, ohne sie zu entscheiden. |
| **Fristbeginn mit dem Einliefern (`accepted_at`)** | Ein Ereignis, das nie zugestellt wurde, würde unbegrenzt liegen. Der Mechanismus kennt den Bezugspunkt weiterhin, der bestätigte Vorgabewert ist `last_attempt_at`. |
| **Physische Löschung ohne Markierung** | Kein Zeitfenster für die Fehleruntersuchung; die Zusage „30 Tage“ würde faktisch kürzer. |
| **Nur physische Löschung, keine logische** | Der Service kann den Bestand nicht mehr erklären; die Zähler der Durchsetzung wären nicht prüfbar. |
| **Aufbewahrung je Ereignistyp** | Die Frage des Fachbereichs bezieht sich auf Datenarten, nicht auf Ereignistypen; das wäre ein Feature ohne Bedarf. |
| **Partitionierung mit automatischem Drop** | Eine technisch gute Alternative für reine Zeitreihen, aber die Zustelllogik braucht die Zeilen (z. B. Totenliste, Historie). |
| **Trennung von `audit_log` ohne Frist** | Ein Audit-Protokoll ohne Frist ist ein Datenschutzproblem eigener Art; es braucht eine **eigene**, getrennt konfigurierbare Frist. |

## Status

**Angenommen** — der Mechanismus und die Vorgabewerte sind am 2026-10-21 am
Gate 1 bestätigt (F-02, entschieden O-02). O-05 bleibt eine
Freigabebedingung vor Gate 2.
