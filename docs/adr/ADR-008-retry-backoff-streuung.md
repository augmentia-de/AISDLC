# ADR-008: Wiederholung, Backoff, Streuung und Zeitlimit je Versuch

> **Zusammengeführt (Schritt 3):** Die frühere ADR-014 (Zeitlimit je
> Zustellversuch, O-07) ist in diese ADR eingearbeitet — der Inhalt ist
> vollständig erhalten, die Entscheidung steht hier. `ADR-014-zeitlimit-versuch.md`
> verweist nur noch hierher.

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

D-08 legt die Wiederholungsregel fest, D-09 die Form des Backoffs: konfigurierbar,
mit Verdopplung des Abstands und mit Streuung.

Zwei Fragen betreffen denselben Gegenstand — **den fehlgeschlagenen
Zustellversuch** — und wurden deshalb in dieser ADR zusammengeführt:

| Frage | Herkunft | Status |
|---|---|---|
| Wie viele Versuche, welcher Backoff? | O-06 (Fachbereich) | **entschieden 2026-10-21 (F-04)** |
| Wie lange wartet der Dienst auf einen Empfänger? | O-07 (Architektur) | **entschieden 2026-10-21** (früher ADR-014) |

Aus Abschnitt 11 der Anforderung: *„Die Zustellung erfolgt aus einem
Hintergrundprozess, nicht aus dem Request-Thread. Ein Empfänger, der 30
Sekunden braucht, darf den Annahme-Request nicht blockieren."* Daraus folgt die
Grenze: **ein einzelner Zustellversuch darf nicht länger als 30 Sekunden
hängen**, sonst hält ein hängender Empfänger den Worker dauerhaft belegt.

Auswirkungen: AC-011-3 (ein Fehlversuch, der im Hintergrund läuft),
AC-012-1 (Aufgabe nach erschöpften Versuchen), AC-023-4 (Wiederaufnahme nach
Absturz, Versuchszahl steigt nicht sprunghaft).

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
  verdrahteten Zahlen. Die bestätigten Vorgabewerte stehen in Abschnitt 7.
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
Protokolleintrag endet. Das ist die Antwort auf „eine Variante überschlägt nach
elf Fehlversuchen still Fehler“.

### 6. Zeitlimit je Versuch (O-07, ehemals ADR-014)

| Wert | Konfiguration | Vorgabe |
|---|---|---|
| Verbindungs-Timeout | `echobox.delivery.attempt.connect-timeout` | **5 s** |
| Lese-Timeout | `echobox.delivery.attempt.read-timeout` | **10 s** |
| Gesamtgrenze | `echobox.delivery.attempt.total-timeout` | **25 s** (Reserve über 5 s + 10 s) |

```
Gesamtgrenze = Verbindungs-Timeout (5 s) + Lese-Timeout (10 s) = 15 s  <  30 s
```

- Der Verbindungs-Timeout deckt DNS-Auflösung, TCP-Handshake und
  TLS-Handshake ab. Der Lese-Timeout deckt die Zeit zwischen „Verbindung
  steht“ und „Antwort vollständig gelesen“ ab.
- Die 15 Sekunden liegen **unter** der Grenze von 30 Sekunden aus Abschnitt 11
  — mit Reserve für die Antwortverarbeitung und die Protokollauswertung.
- Beide Werte sind **konfigurierbar**; keiner ist im Code verdrahtet. Ein
  Empfänger in einem abgeschotteten Netz mit hohem Latenz-Aufschlag braucht
  andere Werte.
- **Der Timeout ist ein Fehlversuch**, kein Sonderfall: Ergebnis `timeout`, der
  Lauf geht auf `failed`, ein weiterer Versuch wird nach Backoff geplant
  (FR-022). Der Empfänger hat möglicherweise trotzdem verarbeitet — das ist
  der Preis von at-least-once und wird durch die Zustell-Kennungen erkennbar
  gemacht (ADR-005).
- **Verhältnis zur Lease:** Die Lease des Zustelllaufs muss **länger** sein
  als die Gesamtgrenze plus Reserve — sonst gäbe es ein Fenster, in dem ein
  noch laufender Versuch seine Lease verliert (ADR-006).
  `Lease-Dauer > Gesamtgrenze (15 s) + Reserve`.
- **Verhältnis zum Backoff:** Der erste Backoff-Abstand ist **größer** als die
  Gesamtgrenze. Sonst wird ein hängender Empfänger in schneller Folge erneut
  getroffen — das ist genau die Last, die FR-033 (Endpunkt-Begrenzung)
  verhindern soll. Der bestätigte Basisabstand von 1 Minute erfüllt das.

**Begründung der Zeitlimits**

| Wert | Begründung |
|---|---|
| **5 s** Verbindungs-Timeout | Deckt einen normalen Handshake in einem europäischen Rechenzentrum mit Reserve ab. Ein Empfänger, der in 5 s keine TCP-Verbindung zustande bringt, ist entweder nicht erreichbar oder überlastet — beides ist ein Fehlversuch, kein Grund zu warten. |
| **10 s** Lese-Timeout | Gibt einem langsamen, aber arbeitenden Empfänger Zeit, die Nutzlast (bis 1 MB) zu verarbeiten und zu antworten. Kurz genug, um den Worker nicht zu blockieren; lang genug, um False-Positives zu vermeiden. |
| **< 30 s** | Die Vorgabe aus Abschnitt 11 wird eingehalten. Die Reserve deckt Verbindungsaufbau über Proxy, TLS-Handshake-Nacharbeit, Protokollierung und die Auswertung ab. |
| **konfigurierbar** | Eine Konstante im Code wäre ein Blocker für Mandanten mit abgeschotteten Netzen. |

### 7. Wo die Werte gesetzt werden (bestätigt am 2026-10-21, F-04)

| Wert | Ort | Vorgabe (bestätigt) |
|---|---|---|
| Versuchsgrenze | `echobox.delivery.max-attempts` | **8 Versuche** (je Lauf eingefroren) |
| Basisabstand | `echobox.delivery.backoff.base-delay` | **1 Minute** |
| Multiplikator | `…backoff.multiplier` | **2** (Verdopplung) |
| Deckel | `…backoff.max-delay` | **60 Minuten** |
| Streuung | `…backoff.jitter` | **± 20 %** |
| Verbindungs-Timeout | `echobox.delivery.attempt.connect-timeout` | **5 s** |
| Lese-Timeout | `echobox.delivery.attempt.read-timeout` | **10 s** |

Alle Werte sind **Vorgabewerte**, keine im Code verdrahteten Zahlen: jede
Umgebung kann sie über die Konfiguration ändern, ohne dass Code angefasst wird.

## Festlegung (Gate 1, 2026-10-21)

> **F-04 (O-06):** 8 Versuche, Basisabstand 1 Minute, Verdopplung, Deckel
> 60 Minuten, Streuung ± 20 %.
> **O-07 (Architektur):** Verbindungs-Timeout 5 s, Lese-Timeout 10 s,
> Gesamtgrenze 15 s < 30 s; ein Timeout ist ein Fehlversuch.
> *Warum der Fachbereich entscheidet:* Die Versuchszahl bestimmt, wie lange
> ein Empfängerausfall toleriert wird und wie lange tote Zustellungen liegen.
> Das ist eine Betriebs- und Zusagenfrage gegenüber den abonnierenden Teams —
> und damit eine Frage der **Verfügbarkeitszusage**, nicht der Technik. Die
> Mechanik ist vollständig gebaut; jeder Wert ist über Konfiguration
> änderbar, ohne Code anzufassen.

## Konsequenzen

- **Positiv:** Kein Team muss eigene Retry-Logik bauen (Z4) — und keine
  Implementierung kann „still überschlagen“, weil jede Runde protokolliert wird.
- **Positiv:** Die Streuung verhindert, dass ein gemeinsamer Ausfall aller
  Endpunkte eine zweite Welle auslöst.
- **Positiv:** Der Worker ist nicht von einem hängenden Empfänger blockiert;
  die Kapazität bleibt berechenbar.
- **Positiv:** Die Größe ist **ableitbar**: Worker-Kapazität =
  `Threads × (3600 s / 15 s)` Versuche je Stunde im ungünstigsten Fall.
- **Negativ:** Bei 8 Versuchen und Basis 1 Minute mit ×2 und Deckel 60 Minuten
  liegt der letzte Versuch rund **4 Stunden** nach dem ersten. Das ist eine
  **Zusage** gegenüber den Empfängern — deshalb steht der Wert hier.
- **Negativ:** Der Backoff-Zeitpunkt muss in der Datenbank stehen, damit er
  einen Neustart überlebt (ADR-006).
- **Negativ:** Die volle Streuung bedeutet, dass zwei gleichartige Endpunkte
  unterschiedliche Zeitpunkte haben — im Test ist der Seed deshalb explizit
  zu setzen.
- **Negativ:** Ein Empfänger, der regelmäßig mehr als 15 s braucht, wird als
  fehlerhaft behandelt und verliert Zustellungen. Das ist die **korrekte**
  Reaktion — ein Empfänger, der 30 Sekunden braucht, ist für einen Webhook
  zu langsam. Der Wert ist konfigurierbar, falls ein Mandant das anders
  bewertet.
- **Negativ:** Der Timeout ist eine **Faustregel**: bei Netzwerkpartitionen
  können Verbindungen länger „hängen“, als der Timeout vorsieht. Der Timeout
  greift, wenn keine Daten fließen; ein langsam fließender Strom ist durch
  das Lese-Timeout begrenzt, nicht durch die Gesamtdauer. Die zusätzliche
  **Gesamtdauergrenze** je Versuch (`attempt.total-timeout`, Default 25 s)
  schließt diese Lücke.

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
| **Ein einziger Timeout (z. B. 30 s)** | Ununterscheidbar, ob der Empfänger nicht erreichbar oder nur langsam ist — beides verdient aber ein anderes Verhalten (DNS-Fehler vs. Lesefehler). |
| **Kein Timeout** | Ein hängender Empfänger blockiert den Worker dauerhaft — der Betrieb verliert die Kontrolle über seine Kapazität. |
| **30 s exakt** | Reibt am Rand der Vorgabe aus Abschnitt 11 und lässt keine Reserve. |
| **Timeouts im Code verdrahtet** | Passt nicht zu Mandanten mit abgeschotteten Netzen; widerspricht der Konfigurationsregel des Projekts. |
| **Timeout als Abbruch des ganzen Laufs** | Ein einzelner langsamer Versuch darf nicht die Kette beenden — dafür ist der Backoff da. |
| **Timeouts im HTTP-Client global konfiguriert** | Der HTTP-Client wird auch für den Identity-Provider verwendet; die Werte des Zustellversuchs gehören in den **Zustellungs**-Adapter, nicht in die globale Client-Konfiguration. |

## Status

**Angenommen** — die Werte sind am 2026-10-21 am Gate 1 bestätigt (F-04 für
O-06; O-07 als Architekturentscheidung, ehemals ADR-014).
