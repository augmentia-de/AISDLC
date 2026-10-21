# ADR-004: Signaturverfahren und Header-Namen (eingefroren)

## Kontext

FR-020: *„Jede Zustellung trägt eine Signatur über Zeitstempel und Nutzlast,
damit der Empfänger die Echtheit prüfen kann. Verfahren und Header-Name sind
Teil der öffentlichen Schnittstelle und dürfen sich danach nicht mehr ändern —
sonst brechen alle bestehenden Empfänger."*

Das Risiko R-06 bewertet einen Fehlgriff hier als **Totalausfall**: Wer das
Verfahren oder den Header-Namen falsch wählt, muss alle Empfänger neu ausrollen.
Diese Entscheidung ist damit **nicht** im Vorbeigehen änderbar.

D-07 legt fest: Signiert werden Zeitstempel (UTC) und die exakt gesendeten
Nutzlastbytes; das Schlüssel ist das Endpunkt-Secret; Verfahren und Header-Name
werden im Vertrag festgeschrieben und sind danach versioniert — eine Änderung
ist ein Breaking Change und braucht eine neue Hauptversion.

## Entscheidung

### Verfahren

**HMAC-SHA256** über das Endpunkt-Secret des Empfängers.

- **Warum HMAC und nicht ein asymmetrisches Verfahren?** Der Empfänger ist der
  Empfänger **und** der Besitzer des Secrets — es gibt keine dritte Partei, die
  nur verifizieren soll. HMAC ist schneller, einfacher zu implementieren und
  braucht keinen Schlüsselaustausch mit dem Empfänger. Asymmetrische Signaturen
  lohnen sich erst, wenn Dritte verifizieren sollen — das ist hier nicht der Fall.
- **Warum SHA-256 und nicht SHA-1?** SHA-1 ist für HMAC formal noch nicht
  gebrochen, gilt aber als schwach und ist in neuen Systemen nicht mehr
  vertretbar. SHA-256 ist der Standard.

### Header

| Element | Festlegung |
|---|---|
| Header-Name | **`X-EchoBox-Signature`** (eingefroren) |
| Header-Wert | **`v1=<hex-lowercase>`** — der Präfix `v1` ist die Vertragsversion |
| Zeitstempel-Header | **`X-EchoBox-Timestamp`** — ISO-8601 in UTC, z. B. `2026-10-21T09:15:22.481Z` |
| Signier-Eingabe | **`<timestamp>.<payload_bytes>`** — ASCII des **exakten** `X-EchoBox-Timestamp`-Werts, ein Punkt, dann die **exakt gesendeten** Body-Bytes |
| Schlüssel | das Endpunkt-Secret des Empfängers (Klartext nur im Worker) |
| Vergleich | **zeitkonstant**; der Empfänger vergleicht den Hexanteil mit dem eigenen HMAC |
| Zeitfenster | der Empfänger darf einen Zeitstempel ablehnen, der mehr als `signature_tolerance_seconds` (Konfiguration, **Default 300 s**) von der eigenen Uhr abweicht — Replay-Schutz |

### Keine Normalisierung

Die Nutzlastbytes werden **unverändert** signiert. Der Dienst normalisiert
**nichts** — weder Zeilenenden noch Zeichenkodierung noch Leerzeichen. Damit ist
„die Signatur über genau die gesendeten Bytes“ (AC-009-5) wörtlich wahr.

### Vorverschlüsselte Ereignisse

Bei FR-013 sind die gesendeten Bytes die Chiffre. Die Signatur wird dann über
die **Chiffre** gebildet. Das ist möglich, weil der Empfänger die ihm
zugestellten Bytes ohnehin erhält und daraus denselben HMAC berechnen kann —
zum Prüfen der Signatur braucht er **nicht** zu entschlüsseln.

### Versionierung

Der Präfix `v1=` ist Teil der Signier-Konvention. Ein neues Verfahren (z. B.
ein Wechsel auf Ed25519) erhält einen **neuen** Präfix und eine **neue
Hauptversion** des ausgehenden Vertrags (`/api/v2` bzw. `X-EchoBox-Signature:
v2=…`). Der alte Präfix bleibt parallel unterstützt, damit bestehende
Empfänger nicht brechen.

Der Vertragstest schlägt fehl, wenn Verfahren **oder** Header-Name im Vertrag
fehlen (AC-009-3) — das ist die technische Absicherung gegen R-06.

## Konsequenzen

- **Positiv:** Ein einzelner Header und eine einzelne Formel — die
  Empfängerseite braucht wenige Zeilen Code.
- **Positiv:** Der Zeitstempel in der Signatur macht Wiederverwendung einer
  alten Zustellung erkennbar.
- **Negativ:** Jede Änderung an Verfahren oder Header-Name ist ein
  **breaking change** und braucht eine neue Hauptversion plus eine
  Parallelphase. Das ist gewollt: die Alternative (stille Änderung) ist genau
  der Totalausfall aus R-06.
- **Negativ:** Der Empfänger muss seinen Zeitversatz im Blick behalten. Das
  Zeitfenster von 300 s ist ein Default und konfigurierbar.
- **Negativ:** Der Dienst muss den **exakten** Zeitstempelwert behalten, den er
  in den Header schreibt — er darf ihn nicht beim Signieren neu formatieren.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **RSA/Ed25519 (asymmetrisch)** | Der Empfänger ist der Secret-Besitzer; es gibt keinen Dritten, der nur verifizieren soll. Aufwand ohne Gegenwert. |
| **SHA-256 ohne Schlüssel** | Keine Geheimhaltung — jeder könnte Zustellungen fälschen. |
| **HMAC-SHA512** | Kein Sicherheitsgewinn, nur größerer Header. SHA-256 ist der Standard. |
| **Signatur über ein JSON-Envelope statt über die Nutzlastbytes** | AC-007-3 verlangt, dass die zugestellten Bytes den eingelieferten entsprechen; ein Envelope würde das brechen. |
| **Zeitstempel als Query-Parameter** | Header sind der kanonische Ort; Query-Parameter landen in Proxy-Logs. |
| **Signatur über die Chiffre bei vorverschlüsselten Ereignissen weglassen** | Dann wäre die Zustellung nicht authentifizierbar — das widerspricht FR-020. |

## Status

**Angenommen** — der Vertrag `docs/api/openapi.yaml` (`x-delivery-protocol`)
ist ab hier **eingefroren**. Bei Gate 1 am 2026-10-21 bestätigt.
