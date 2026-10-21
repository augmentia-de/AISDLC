# ADR-005: Zustellkennungen und Empfänger-Header

## Kontext

FR-021: *„Die Zustellung ist at-least-once: ein Ereignis kann mehrfach ankommen.
Jede Zustellung trägt eine eigene Zustell-Kennung, damit der Empfänger doppelte
Zustellungen erkennen kann."*

Das Risiko R-01 zeigt den Widerspruch auf: Das Glossar der Anforderung
(Abschnitt 4) nennt den **einzelnen Versuch** „Zustellung“ und die Kette über
alle Versuche „Lieferung“. Nimmt man das wörtlich, trägt jeder Versuch eine
andere Kennung — dann kann der Empfänger eine Dublette **nicht** erkennen, und
genau daran scheitert die Zustellgarantie in der Praxis.

Ziel Z5 des PRD: *„Jeder Zustellversuch trägt Ereignis-ID, Zustelllauf-ID und
Versuchs-ID; ein Timeout-Duplikat ist am stabilen Zustelllauf erkennbar."*

D-06 legt drei Kennungen fest. Die Entscheidung **hier** ist, wie sie im
ausgehenden Vertrag aussehen und welche davon der Empfänger als
Deduplizierungsschlüssel benutzt.

## Entscheidung

### Drei Kennungen

| Kennung | Stabilität | Header | Empfänger nutzt sie als |
|---|---|---|---|
| **Ereignis-ID** | stabil über alle Zustellläufe, Endpunkte und Replays | `X-EchoBox-Event-Id` | **fachlicher Deduplizierungsschlüssel** — dieselbe ID = dasselbe Geschäftsvorfall |
| **Zustelllauf-ID** | stabil über alle Versuche **eines** Laufs; ein Replay erzeugt eine **neue** | `X-EchoBox-Delivery-Run-Id` | Erkennung einer **Wiederholung desselben Laufs** (Timeout-Dublette) |
| **Versuchs-ID** | für jeden HTTP-Versuch eindeutig | `X-EchoBox-Attempt-Id` | Erkennung, dass ein zweiter Versuch desselben Laufs kam |

Ergänzend: `X-EchoBox-Attempt-Number` (1-basiert, je Lauf) macht die
Wiederholung auch **ohne** UUID-Vergleich erkennbar — hilfreich in Logs und
Support-Fällen.

### Ablehnungs- und Ereignistyp-Header

`X-EchoBox-Event-Type` (Ereignistyp) und `X-EchoBox-Rejection: permanent`
(Ablehnungssignal, ADR-009) gehören ebenfalls zum eingefrorenen Header-Set.

### Warum die Kennungen in **Headern** stehen und nicht im Body

Der Request-Body sind **exakt die Nutzlastbytes** (ADR-004, AC-007-3). Die
Metadaten stehen deshalb in Headern — das ist ohnehin der kanonische Ort, und
der Empfänger kann die Kennungen lesen, **ohne** den Body zu parsen oder
weiterzureichen.

### Deduplizierungsempfehlung an die Empfänger

> Verwerfe alle Zustellungen mit einer **bereits gesehenen Ereignis-ID** —
> unabhängig davon, ob die Zustelllauf-ID gleich ist (fachliche Dublette) oder
> neu (Replay).
> Behandle zwei Zustellungen mit derselben **Zustelllauf-ID** und
> unterschiedlicher **Versuchs-ID** als **denselben Zustellvorgang** (Timeout-
> Dublette) und verarbeite ihn **nicht** zweimal.

Diese Empfehlung steht im Vertrag (`x-delivery-protocol.duplicate_handling`)
und in der Empfänger-Dokumentation.

## Konsequenzen

- **Positiv:** Der Empfänger braucht **eine** Deduplizierungsregel, keine drei.
- **Positiv:** Die Historie bleibt nachvollziehbar — ein Replay ist am neuen
  Zustelllauf erkennbar, am alten Datensatz bleibt er nachvollziehbar
  (AC-013-2, AC-013-5).
- **Negativ:** Drei Header plus Zeitstempel, Signatur, Typ und Versuchsnummer —
  sieben Header. Das ist viel, aber jeder ist tragend und keiner ist redundant.
- **Negativ:** Die Ereignis-ID ist mandantenübergreifend eindeutig, aber
  **nicht** vorhersehbar (UUID). Ein Empfänger, der Zustellungen über
  Mandantengrenzen hinweg vergleichen will, braucht dafür eine eigene
  Mandantensicht — EchoBox liefert sie nur dem `service_admin`.
- **Negativ:** Der Body enthält **keinen** Verweis auf das Ereignis. Empfänger,
  die den Body unverändert weiterreichen, verlieren die Kennungen — das ist
  eine bewusste Entscheidung zugunsten von AC-007-3.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Eine Kennung je Versuch** (wörtliche Lesart des Glossars) | Macht die Dublettenerkennung unmöglich (R-01) und widerspricht Z5. |
| **Kennungen im JSON-Envelope** | Bricht AC-007-3 (zugestellte Bytes ≠ eingelieferte Bytes). |
| **Sequenzielle Zähler je Endpunkt** | Nicht global eindeutig, nicht vorhersehbar-frei, und bei Replay schwer zu deuten. |
| **Ereignis-ID als einzige Kennung** | Replays wären für den Empfänger nicht unterscheidbar — ein Replay ist eine **neue** Zustellung desselben Ereignisses und soll erneut zugestellt werden. |
| **UUID v4** | UUID **v7** (zeitlich sortierbar) wird bevorzugt: die IDs werden in Logs und im Protokoll sortiert. |

## Status

**Angenommen** — der Header-Set ist im Vertrag `docs/api/openapi.yaml`
(`x-delivery-protocol.request.headers`) **eingefroren**. Bei Gate 1 am
2026-10-21 bestätigt.
