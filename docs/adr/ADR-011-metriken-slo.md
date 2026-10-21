# ADR-011: Kennzahlen, Erfolgsquote und Zustelllatenz

## Kontext

FR-032: *„Für die Betriebsüberwachung werden je Endpunkt Erfolgsquote und
Zustelllatenz ausgewiesen."*

Abschnitt 5 der Anforderung: *„wir können im Betrieb sagen, wie hoch die
Erfolgsquote je Endpunkt ist."* Der Betrieb — also der Service-Admin — muss
erkennen können, ob ein Endpunkt Probleme macht.

AC-017-1 verlangt: Erfolgsquote = *erfolgreiche Versuche / alle Versuche* und
Zustelllatenz = *p95 der Dauer von der Annahme bis zur erfolgreichen Antwort*.
AC-017-2: die Kennzahlen sind über eine **dokumentierte Schnittstelle**
abrufbar. AC-017-3: ohne Daten im Fenster wird **„keine Daten“** ausgewiesen —
**nicht** 0 % und **nicht** 100 %. AC-017-4: ein Endpunkt eines fremden
Mandanten ⇒ HTTP 404.

Der Abschnitt 12 des PRD nennt die offene Frage: *„Zustellmechanismus des
Hintergrundprozesses“* und die Zusagen zu den Kennzahlen. Die **Zahlenwerte**
(Auswertungsfenster, Erfolgsdefinition) sind Parameter, keine Festlegungen.

## Entscheidung

### 1. Definitionen

| Kennzahl | Definition |
|---|---|
| **Erfolgsquote** | `attempts_successful / attempts_total` im Auswertungsfenster |
| **Erfolg** | ein Versuch mit Ergebnis `success` (Antwort 2xx) |
| **Zustelllatenz p95** | 95-Perzentil der Dauer von der **Annahme des Ereignisses** (`events.accepted_at`) bis zum **erfolgreichen** Versuch |
| **Auswertungsfenster** | konfigurierbar; Default: die letzten 7 Tage bis jetzt |

Die Latenz misst also die **gesamte** Zeit vom Einliefern bis zur erfolgreichen
Zustellung — nicht nur die Dauer des HTTP-Versuchs. Das ist die Zahl, die ein
Empfänger-Admin wirklich spürt. Die reine Versuchsdauer wird zusätzlich als
`duration_ms` je Versuch protokolliert.

### 2. Kein Erfolgsquoten-Signal für „keine Daten“

Wenn im Fenster **kein** Versuch stattgefunden hat:

```json
{
  "data_available": false,
  "attempts_total": null,
  "attempts_successful": null,
  "success_rate": null,
  "delivery_latency_p95_ms": null
}
```

**Nicht** `0` und **nicht** `0.0` — eine Erfolgsquote von 0 % sagt „alles
schlug fehl“, das wäre eine Falschaussage. `null` plus `data_available: false`
sagt „hier ist nichts passiert“ (AC-017-3).

### 3. Berechnung ohne Join

Damit die Kennzahlen ohne teure Verbünde entstehen, sind in
`delivery_attempts` denormalisiert:

- `endpoint_id` — für die Gruppierung je Endpunkt,
- `event_accepted_at` — für die Latenz (Annahmezeitpunkt des Ereignisses).

Die Indizes (`endpoint_id, started_at` und
`endpoint_id, event_accepted_at WHERE outcome = 'success'`) tragen beide
Auswertungen.

Die Kennzahlen werden **on demand** aus den Versuchen berechnet, nicht in
einer eigenen Kennzahlentabelle. Grund: eine zweite Wahrheit über Zustände
driftet; die Versuche sind die Quelle.

### 4. Schnittstelle

| Zweck | Schnittstelle |
|---|---|
| **Fachliche** Auswertung je Endpunkt (Service-Admin, Empfänger-Admin) | `GET /api/v1/endpoints/{endpointId}/metrics` — Teil des Vertrags (FR-032, AC-017-1) |
| **Maschinelle** Betriebsüberwachung | Actuator-Metriken (`/actuator/prometheus`) — Zähler und Histogramme für den Scraping-Zugriff |

Beide sind dokumentiert; die **fachliche** Schnittstelle ist der Vertrag, die
**technische** ist das Actuator (AC-017-2). Ein Metrics-Controller mit eigener
Ressource (`/metrics`) wurde bewusst **nicht** gebaut — er hätte die
Actuator-Oberfläche dupliziert.

### 5. Mandantentrennung

Ein Endpunkt eines fremden Mandanten ⇒ HTTP 404 (D-02, AC-017-4) — nicht 403,
damit die Existenz fremder Endpunkte verborgen bleibt.

### 6. Was **nicht** ausgewiesen wird

| Nicht ausgewiesen | Warum |
|---|---|
| Nutzlastinhalte, Nutzlastlängen je Inhalt | FR-012, D-11 |
| Empfängerseitige Kennzahlen | Der Dienst ist kein Monitoring-Gateway |
| Histogramm der Latenz **je** Endpunkt über lange Zeiträume | Datenmenge; das Auswertungsfenster ist begrenzt und konfigurierbar |

## Konsequenzen

- **Positiv:** Der Betrieb kann „welcher Endpunkt liefert nicht?“ mit einer
  Zahl beantworten — das ist die Kernaussage aus Abschnitt 5.
- **Positiv:** `data_available` trennt „nichts passiert“ von „alles schlägt
  fehl“ — ein Unterschied, der in der Betriebsüberwachung teuer ist.
- **Negativ:** Die p95-Berechnung über ein Fenster ist eine Aggregatabfrage. Bei
  sehr großen Datenmengen braucht sie einen Wartungsjob oder eine
  Zusammenfassungstabelle — dafür ist das Fenster konfigurierbar und die
  Berechnung bewusst **on demand**, solange das Volumen es trägt.
- **Negativ:** Die Latenzdefinition „von der Annahme bis zur erfolgreichen
  Antwort“ ist eine **Zusage** gegenüber dem Empfänger. Ein Ereignis, das nie
  erfolgreich zugestellt wurde, geht **nicht** in die p95 ein — die Quote
  deckt diesen Fall ab. Beide Zahlen sind deshalb **immer zusammen** zu lesen.
- **Negativ:** Die Zählung der Rate-Limit-Versuche steckt im
  `rate_limit`-Abschnitt derselben Antwort (ADR-010) — zwei Belange in einer
  Ressource. Das ist gewollt: beide beziehen sich auf denselben Endpunkt und
  dieselbe Zeitachse.

## Bestätigung bei Gate 1 (2026-10-21)

> **Bestätigt:** Auswertungsfenster 7 Tage (Vorgabewert); Erfolgsquote und
> p95-Latenz je Endpunkt; das Fenster ist je Abfrage
> über `from`/`to` **und** per Konfiguration einstellbar.
> *Warum das eine Betriebsfrage ist:* Das Auswertungsfenster bestimmt,
> wie stark kurzzeitige Schwankungen geglättet werden. Die **Definition** der
> Kennzahlen (Erfolg = 2xx, Latenz = Annahme bis
> erfolgreiche Antwort) folgt wörtlich aus AC-017-1 und ist damit keine
> offene Frage.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Erfolgsquote je Zustelllauf** (statt je Versuch) | AC-017-1 nennt „erfolgreiche Versuche / alle Versuche“ ausdrücklich. |
| **Mittelwert statt p95** | Der Mittelwert verdeckt genau die Ausreißer, die ein Endpunkt zum Problem machen (AC-017-1 nennt p95). |
| **0 % bei fehlenden Daten** | Falschaussage — AC-017-3 verbietet sie ausdrücklich. |
| **Eigene Kennzahlentabelle** | Eine zweite Wahrheit über Zustände; sie driftet. |
| **Nur Actuator-Metriken** | Nicht mandantenfest und nicht in der fachlichen Rolle abfragbar (AC-017-2 verlangt eine dokumentierte Schnittstelle für die Betriebsüberwachung — die fachliche). |
| **Latenz ab Versuchsbeginn** | Misserst die Wartezeit — die Zahl, die der Empfänger spürt. |

## Status

**Angenommen** — Definitionen und Fenstervorgabe (7 Tage) sind bei Gate 1 am
2026-10-21 bestätigt.
