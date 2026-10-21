# ADR-014: Zeitlimit je Zustellversuch — zusammengeführt in ADR-008

> **Zusammengeführt (Schritt 3, 2026-10-21).** Die Entscheidung dieser ADR
> (Zeitlimit je Zustellversuch, O-07) ist vollständig in
> **[ADR-008](ADR-008-retry-backoff-streuung.md)** eingearbeitet — dort in
> Abschnitt 6 („Zeitlimit je Versuch“), in der Wertetabelle in Abschnitt 7,
> in den Konsequenzen und in der Alternativentabelle.
>
> Diese Datei bleibt als **Verweis** erhalten, damit bestehende Verweise
> (z. B. auf `ADR-014` in anderen Dokumenten) nicht ins Leere zeigen. Es gibt
> **keine eigene Entscheidung** mehr an dieser Stelle.

## Kurzfassung der übernommenen Entscheidung

| Wert | Konfiguration | Vorgabe |
|---|---|---|
| Verbindungs-Timeout | `echobox.delivery.attempt.connect-timeout` | 5 s |
| Lese-Timeout | `echobox.delivery.attempt.read-timeout` | 10 s |
| Gesamtgrenze | `echobox.delivery.attempt.total-timeout` | 25 s |

`Gesamtgrenze` ist die Reserve über die Summe aus Verbindungs- und
Lese-Timeout (5 s + 10 s = 15 s < 30 s aus Abschnitt 11 der Anforderung).
Ein Timeout ist ein **Fehlversuch** (Ergebnis `timeout`, Lauf `failed`,
weiterer Versuch nach Backoff). Die Lease muss länger sein als die
Gesamtgrenze plus Reserve (ADR-006).

## Status

**Zusammengeführt** — die Entscheidung steht in ADR-008. Der Mensch hat den
Zusammenschluss am Gate 1 (2026-10-21) zur Kenntnis genommen.
