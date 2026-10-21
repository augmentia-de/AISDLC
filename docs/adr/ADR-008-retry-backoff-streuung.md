# ADR-008: Wiederholung, Backoff und Streuung

## Kontext

FR-022: *„Eine fehlgeschlagene Zustellung wird erneut versucht, mit wachsendem
Abstand zwischen den Versuchen. Der Abstand soll nicht bei allen Endpunkten
gleichzeitig liegen, also mit Streuung."*

FR-023: *„Nach einer festen Anzahl erfolgloser Versuche wird die Zustellung
aufgegeben und landet in der Liste der toten Zustellungen."*

Der Ausgangsfall aus Abschnitt 1: *„Drei Teams haben inzwischen drei Varianten
von Retry, Backoff und ‚was tun wir bei 500' gebaut. Eine davon überschlägt nach
elf Fehlversuchen still Fehler."* — Also: **kein** stilles Überschlagen. Jede
Aufgabe muss sichtbar werden (FR-023, D-04).

D-09 legt fest: Backoff konfigurierbar, mit Verdopplung des Abstands und mit
Streuung; die konkreten Werte folgen aus **O-06**.

**Offener Punkt O-06** (Fachbereich): wie viele Versuche, welcher Backoff?
Die Architektur darf den Wert **nicht** festlegen — sie muss den Mechanismus
so bauen, dass jeder Wert konfigurierbar ist.

## Entscheidung

### 1. Wiederholungsregel (D-08)

| Empfänger-Antwort | Wiederholung? | Begründung |
|---|---|---|
| **2xx** | nein | Erfolg — der Zustelllauf ist `sent` (AC-011-4, AC-010-3) |
| **5xx** | ja | Der Empfänger ist vorübergehend nicht in der Lage |
| **408** | ja | Der Empfänger hat den Request nicht rechtzeitig bearbeitet |
| **429** | ja | Der Empfänger fordert ausdrücklich eine spätere Zustellung |
| **Netzwerkfehler** (DNS, Connect, TLS, Lese-Timeout) | ja | Unbekannter Ausgang — at-least-once |
| **410 mit Ablehnungssignal** | nein | Der Empfänger will das Ereignis dauerhaft nicht (FR-025) |
| **übriger 4xx** | nein | Der Empfänger lehnt ab (D-08, AC-014-4) |

Ein 4xx **ohne** das Ablehnungssignal führt ebenfalls zum Ende — der
Unterschied ist nur der `rejection_reason` (`client_error` statt
`permanent_rejection`). Das verhindert, dass ein Empfänger mit einem
versehentlichen 400 endlos wiederholt wird.

### 2. Backoff: Verdopplung mit Deckel

```
Abstand(n) = min(Basis × Multiplikator^(n-1), Deckel)
```

- **Basis**, **Multiplikator**, **Deckel** und **Streuung** sind
  Konfigurationsparameter (`echobox.delivery.*`), **keine** im Code
  verdrahteten Zahlen.
- Die Verdopplung erfüllt AC-011-1 wörtlich: der Abstand zum vorherigen
  Versuch ist **mindestens doppelt** so hoch wie der vorherige Abstand. (Der
  Deckel kann das Wachstum abbremsen — das ist gewollt und konfigurierbar.)
- AC-011-5: Die geplanten Wartezeiten steigen monoton an und **kein**
  geplanter Zeitpunkt liegt in der Vergangenheit. Das wird beim Setzen von
  `next_attempt_at` erzwungen (`max(now(), geplant)`).

### 3. Streuung (Jitter)

Auf den berechneten Abstand wird eine **Streuung** angewandt:

```
Abstand = Basiswert × (1 + U(−Streuung, +Streuung))
```

- **Volle Streuung** (uniform über das ganze Intervall) statt „Streuung nur
  nach oben“: sonst wären alle Versuche eines Endpunkts weiterhin
  synchronisiert, nur später (AC-011-2 verlangt, dass die Zeitpunkte **nicht
  identisch** sind).
- Die Streuung ist **deterministisch prüfbar**: die Berechnung bekommt den
  Seed aus dem Versuch (z. B. aus `delivery_run_id` und `attempt_number`),
  sodass ein Test denselben Zeitpunkt erwarten kann.
- Die Streuung darf den Abstand **nicht** auf 0 drücken (Mindestabstand 1 s).

### 4. Versuchsgrenze

- `max_attempts` wird **beim Anlegen des Zustelllaufs** als Momentaufnahme in
  `delivery_runs.max_attempts` festgeschrieben. So ändert eine spätere
  Konfigurationsänderung nicht rückwirkend laufende Zustellungen.
- Ist die Grenze erreicht, wird der Lauf **`abandoned`** mit `abandoned_at`
  gesetzt (DB-Constraint) und erscheint in der Totenliste (FR-023, AC-012-1).
- AC-012-4: Eine Konfiguration mit der Grenze 1 endet nach **genau einem**
  Versuch als `abandoned` — die Grenze wird nicht unterlaufen.
- Nach `abandoned` gibt es **keinen** automatischen Neuversuch (AC-012-3).
  Nur ein Replay (FR-024) setzt eine neue Runde in Gang.

### 5. Kein stilles Überschlagen

Jeder Versuch — auch der letzte — wird **protokolliert**
(`delivery_attempts`, mit `outcome`, `response_status`, `error_code`,
`next_attempt_at`). Es gibt keinen Pfad, auf dem ein Versuch ohne
Protokolleintag endet. Das ist die Antwort auf „eine Variante überschlägt nach
elf Fehlversuchen still Fehler“.

### 6. Wo die Werte gesetzt werden

| Wert | Ort | Vorgabe |
|---|---|---|
| Basisabstand | Konfiguration `echobox.delivery.backoff.base-delay` | konfigurierbar, **kein** fest verdrahteter Wert |
| Multiplikator | `…backoff.multiplier` | konfigurierbar |
| Deckel | `…backoff.max-delay` | konfigurierbar |
| Streuung | `…backoff.jitter` | konfigurierbar |
| Versuchsgrenze | `…max-attempts` | konfigurierbar, je Lauf eingefroren |

## Vorschlag zur Freigabe bei Gate 1

> **Vorschlag (Werte, nicht entschieden):** 8 Versuche, Basisabstand 1 Minute,
> Verdopplung, Deckel 60 Minuten, Streuung ± 20 %.
> *Warum das der Fachbereich entscheidet:* Die Versuchszahl bestimmt, wie
> lange ein Empfängerausfall toleriert wird und wie lange tote Zustellungen
> liegen. Das ist eine Betriebs- und Zusagenfrage gegenüber den abonnierenden
> Teams — und damit eine Frage der **Verfügbarkeitszusage**, nicht der
> Technik. Die Mechanik ist vollständig gebaut; jeder Wert ist über
> Konfiguration änderbar, ohne Code anzufassen.

## Konsequenzen

- **Positiv:** Kein Team muss eigene Retry-Logik bauen (Z4) — und keine
  Implementierung kann „still überschlagen“, weil jede Runde protokolliert wird.
- **Positiv:** Die Streuung verhindert, dass ein gemeinsamer Ausfall aller
  Endpunkte eine zweite Welle auslöst.
- **Negativ:** Bei 8 Versuchen und Basis 1 Minute mit ×2 und Deckel 60 Minuten
  liegt der letzte Versuch rund **4 Stunden** nach dem ersten. Das ist eine
  **Zusage** gegenüber den Empfängern — der Wert gehört deshalb in die
  Freigabe (O-06).
- **Negativ:** Der Backoff-Zeitpunkt muss in der Datenbank stehen, damit er
  einen Neustart überlebt (ADR-006).
- **Negativ:** Die volle Streuung bedeutet, dass zwei gleichartige Endpunkte
  unterschiedliche Zeitpunkte haben — im Test ist der Seed deshalb explizit
  zu setzen.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Feste Wartezeit** | Erzeugt Synchronität und Lastspitzen (FR-022 verlangt „wachsend“). |
| **Exponentiell ohne Streuung** | Alle Endpunkte laufen synchron (FR-022 verlangt Streuung). |
| **Streuung nur nach oben** | Verschiebt die Synchronität, beseitigt sie nicht. |
| **Retry nur bei 5xx** | 408 und 429 sind ausdrücklich „komm später“; 4xx sind dauerhafte Fehler. |
| **Unbegrenzte Wiederholung** | Widerspricht FR-023 (feste Anzahl) und dem Leitsatz „nichts verschwindet still“. |
| **Wiederholung auch bei 4xx** | Ein 400 wiederholt sich nicht besser — nur der Empfänger kann die Ursache beheben. |
| **Backoff im Speicher des Workers** | Überlebt keinen Neustart und ist nicht mandantenfest. |

## Status

**Angenommen** — die Werte selbst bleiben offen (O-06, Gate 1).
