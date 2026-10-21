# ADR-017: Protokollsuche ausschließlich über Metadaten

## Kontext

FR-012 verlangt, dass die Nutzlast im Ruhezustand verschlüsselt gespeichert ist
und nur für den Zustellvorgang entschlüsselt wird. D-11 grenzt die Protokollsuche
deshalb auf Metadaten ein: *„Das Zustellprotokoll ist über Metadaten
durchsuchbar (Ereignis-ID, Typ, Endpunkt, Status, Zeitraum). Eine Suche nach
Nutzlastinhalt gibt es nicht — das wäre mit FR-012 unvereinbar."*

AC-007-5 verlangt genau das: die Suche über Metadaten ist möglich, eine Suche
nach Nutzlastinhalt **existiert nicht**.

Abschnitt 13 der Anforderung stellt die Frage: *„Verschlüsselung im Ruhezustand:
‚Nach Möglichkeit' — ist das ein Muss? Falls ja: können wir dann im Zustellprotokoll
überhaupt noch nach einem Ereignis suchen?"* — O-04.

Diese ADR entscheidet die **Architekturseite**: welche Metadaten durchsuchbar
sind und wie die Suche technisch läuft.

## Entscheidung

### 1. Durchsuchbare Metadaten

| Metadatum | Quelle | Index |
|---|---|---|
| Ereignis-ID | `events.event_id` / `delivery_runs.event_id` | `delivery_runs (organisation_id, event_id)` |
| Ereignistyp | `events.event_type` / `delivery_runs.event_type` | `delivery_runs (organisation_id, event_type, created_at DESC)` |
| Endpunkt | `delivery_runs.endpoint_id` | `delivery_runs (organisation_id, endpoint_id, created_at DESC)` |
| Zustand | `delivery_runs.status` | `delivery_runs (organisation_id, status, next_attempt_at)` |
| Zeitraum | `created_at`, `accepted_at`, `last_attempt_at` | `delivery_runs (organisation_id, created_at DESC)` |
| Idempotenzschlüssel | `events.idempotency_key` | UNIQUE `(organisation_id, idempotency_key)` |

### 2. Denormalisierung als Suchstrategie

`delivery_runs.event_type` ist **denormalisiert** aus dem Ereignis. Grund: die
Protokollsuche ist der heißeste Lesepfad, und ein Join über `events` auf jedem
Listenaufruf wäre der teuerste Teil. Die Denormalisierung ist **verlustfrei**,
weil `event_type` sich nach der Einlieferung nie ändert.

Ebenso denormalisiert: `delivery_attempts.endpoint_id` und
`delivery_attempts.event_accepted_at` — für die Kennzahlen (ADR-011) und für
den Zeitraumfilter.

Der Preis der Denormalisierung wird bezahlt: `event_type` und `endpoint_id`
stehen in **zwei** Tabellen. Beide werden **ausschließlich** vom
Ereignis- bzw. Endpunktpfad geschrieben — nie nachträglich geändert.

### 3. Keine Volltextsuche — und kein Blind-Index

| Variante | Verworfen, weil |
|---|---|
| Suche über den Chiffretext | unmöglich und sinnlos |
| **Blind-Index** (HMAC über Token-Werte, um `order_id = 4711` zu finden) | Schwächt die Zusage „im Ruhezustand nicht im Klartext lesbar“ auf (ein Index über Token-Werte ist ein zweiter, angreifbarer Ort für die Daten) und ist ein **Feature ohne Bedarf** — die Anforderung fragt nicht danach |
| Klartext-Index | widerspricht FR-012 unmittelbar |
| Entschlüsseln und Filtern | unmöglich über einen Index; zusätzlich müsste der Dienst die Nutzlast im Klartext durch die Abfrage tragen |

### 4. Filter und Sortierung im Vertrag

Der Vertrag legt die Filter **verbindlich** fest (Filter sind Teil des
Vertrags, keine Implementierungsdetails):

| Liste | Filter |
|---|---|
| `GET /api/v1/deliveries` | `endpoint_id`, `event_id`, `event_type`, `status` (kommasepariert), `from`, `to` |
| `GET /api/v1/dead-deliveries` | `endpoint_id`, `event_type`, `status`, `from`, `to` |
| `GET /api/v1/events` | `event_type`, `idempotency_key`, `status`, `from`, `to` |

Die Filter sind **kombinierbar** (AC-015-4, AC-016-4). Sortierung: implizit
neueste zuerst (`created_at DESC`) — die Reihenfolge, in der ein Betrieb den
Fehler sucht.

### 5. Cursor-basierte Paginierung

`limit` (Default 50, Maximum 200) + `cursor`; Antwort unter `page` mit
`has_more` und `next_cursor`.

**Warum kein `total`?** Eine Zählung über die Zustelltabellen ist bei laufendem
Wachstum teuer und ändert sich während des Zählens. Für die Betriebsfragen
(„welche Endpunkte liefern nicht?“) ist die **Seitenliste** die richtige
Antwort; die Kennzahlen liefern die Aggregation (ADR-011).

**Warum kein Offset?** Bei laufend wachsendem Protokoll sind
Offset-Seiten instabil: ein neuer Eintrag verschiebt alles, und tiefe
Offsets werden langsam. Cursor sind stabil und günstig.

Der Cursor ist **opak** und HMAC-signiert: ein manipulierter Cursor wird mit
HTTP 422 abgelehnt, statt eine unerwartete Seite zu liefern.

### 6. Die Totenliste

`GET /api/v1/dead-deliveries` ist mandantenübergreifend für `service_admin`
(AC-016-1) und mandantengefiltert für alle anderen (403, AC-016-2). Sie zeigt
Ereignis-ID, Endpunkt, Ereignistyp, Versuchsanzahl, letzter Antwortcode und
den Zeitpunkt der Aufgabe.

Die Totenliste enthält **keine** Nutzlast und **keinen** Nutzlastinhalt.

## Konsequenzen

- **Positiv:** Die Suche im Protokoll bleibt schnell — sie läuft über Indizes
  auf Metadaten, nie über die Chiffre.
- **Positiv:** Es gibt **eine** Suche, nicht eine Metadaten-Suche *und* eine
  Volltextsuche — und damit keine zweite Wahrheit über die Daten.
- **Positiv:** Die Denormalisierung macht den heißesten Lesepfad indexierbar.
- **Negativ:** Wer nach einem Inhalt suchen will, findet ihn nicht. Das ist die
  **bewusste** Folge von FR-012 und der Grund, warum O-04 als Produktfrage
  beim Fachbereich liegt — nicht als Technikfrage.
- **Negativ:** Die Denormalisierung von `event_type` und `endpoint_id` muss an
  zwei Stellen gepflegt werden. Beide Felder sind nach der Einlieferung
  unveränderlich — das begrenzt das Risiko.
- **Negativ:** Cursor-Paginierung ist für den Menschen weniger bequem als
  Seitenzahlen. Die Oberfläche bietet deshalb „weitere laden“ statt
  „Seite 7“.

## Vorschlag zur Freigabe bei Gate 1

> **Vorschlag:** Die Protokollsuche bleibt **ausschließlich** über Metadaten
> (Ereignis-ID, Ereignistyp, Endpunkt, Status, Zeitraum, Idempotenzschlüssel).
> Eine Volltextsuche über Nutzlasten wird **nicht** gebaut.
> *Warum das der Fachbereich bestätigen sollte:* Die Frage aus Abschnitt 13
> („können wir dann im Zustellprotokoll noch nach einem Ereignis suchen?“)
> betrifft die **Produktzusage** gegenüber den Mandanten. Die Architektur hat
> den Metadatenweg vollständig ausgebaut; ob zusätzlich ein Inhaltsfilter
> gewünscht wird, ist eine Frage des Nutzens und der Datenschutz-Zusage — sie
> gehört nicht in die Technik.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Volltextsuche über die Nutzlast** | Widerspricht FR-012 unmittelbar. |
| **Blind-Index für einzelne Felder** | Schwächt die Verschlüsselungszusage und ist ein Feature ohne Anforderung. |
| **Suchindex (Elasticsearch) neben PostgreSQL** | Eine zweite Zustandshaltung, die konsistent gehalten werden muss — genau das, was die Anforderung vermeiden will. |
| **JSONB-Spalte mit der Nutzlast** | Widerspricht FR-012. |
| **Offset-Paginierung** | Instabil bei laufendem Wachstum und teuer bei tiefen Offsets. |
| **`total` je Seite** | Teure Zählung über die wachsende Zustelltabelle; die Aggregation liefert die Zahlen (ADR-011). |
| **Ohne Paginierung** | Unbegrenzte Antworten auf einer wachsenden Tabelle. |

## Status

**Angenommen** — Bestätigung bei Gate 1 durch den Menschen; die Frage der
fachlichen Durchsuchbarkeit bleibt O-04 beim Fachbereich.
