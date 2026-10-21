# Glossar — EchoBox

> Fachsprache für Code, Doku und Tests. Eine Stelle je Begriff, immer mit der
> Negation und mit der Quelle. Format nach Skill `aisdlc-glossary-adr`.
> Stand: Schritt 2 (Konzept & Architektur), korrigiert in Schritt 4 (Grill-Me-Stresstest)
> · Agent: `aisdlc-architecture`

---

## Endpunkt

Eine Registrierung aus Ziel-URL, Secret, abonnierten Ereignistypen und Zustand.

- **Ausprägung:** genau eine `https`-Ziel-URL, genau ein verschlüsselt
  abgelegtes Secret, mindestens ein Ereignistyp, Status `active` oder `paused`
  (im Deutschen „aktiv“/„pausiert“ — die **Werte** sind englisch, wie im
  Vertrag und im Schema).
- **Nicht:** ein Nachrichtenziel, kein Empfänger-*Nutzer*, kein Mandant. Der
  Endpunkt gehört genau einem Mandanten.
- **Quelle:** FR-001…FR-003, `docs/Anforderung.md` Abschnitt 4

## Ereignis

Eine eingelieferte Nutzlast mit Typ, Zeitpunkt und Idempotenzschlüssel.

- **Ausprägung:** Typ, verschlüsselte Nutzlast, Idempotenzschlüssel je
  (Mandant, Schlüssel), Annahmezeitpunkt, Status `accepted` oder `no_subscriber`.
- **Nicht:** ein dauerhaftes Ereignisarchiv. EchoBox ist kein Event-Store.
- **Quelle:** FR-010…FR-013

## Zustellung

Ein **einzelner** Versuch, ein Ereignis an einen Endpunkt zu senden.

- **Ausprägung:** ein HTTP-Versuch mit Start, Ende, Dauer, Antwortcode oder
  Fehlercode und Ergebnis (`outcome`).
- **Nicht:** die Kette über alle Versuche — das ist der Zustelllauf. Der
  Roh-Input verwendet „Zustellung“ für den Versuch und „Lieferung“ für die
  Kette; FR-021 macht eine eindeutige Kennung je Zustellung nötig, deshalb
  drei Kennungen (D-06).
- **Quelle:** `docs/Anforderung.md` Abschnitt 4, FR-021, D-06

## Lieferung / Zustelllauf

Das Protokoll einer Zustellung über alle Versuche.

- **Ausprägung:** ein Zustelllauf zu **einem** Ereignis an **einem** Endpunkt
  mit Zustand, Versuchsanzahl, letztem Versuch, Antwortcode, nächstem
  Versuchszeitpunkt und der Liste der Versuche.
- **Nicht:** ein Ereignis. Ein Ereignis an drei Endpunkte erzeugt drei
  Zustellläufe mit derselben Ereignis-ID und verschiedenen Zustelllauf-IDs.
- **Quelle:** `docs/Anforderung.md` Abschnitt 4, D-06

## Tote Zustellung

Eine Zustellung, die nach erschöpften Versuchen aufgegeben wurde.

- **Ausprägung:** ein Zustelllauf mit Status `abandoned`, gesetztem
  `abandoned_at`; steht in der Totenliste; replaybar.
- **Nicht:** ein abgelehnter Lauf (`rejected`) und kein pausiert abgebrochener
  Lauf (`cancelled`). Beides ist endgültig, aber nicht replaybar.
- **Quelle:** FR-023, FR-031

## Ereignis-ID

Stabile Kennung eines Ereignisses — über alle Zustellläufe und Endpunkte
hinweg gleich.

- **Ausprägung:** UUID, im ausgehenden Request als `X-EchoBox-Event-Id`.
- **Nicht:** der Deduplizierungsschlüssel des *Versuchs*. Er ist der
  fachliche Schlüssel: dieselbe Ereignis-ID heißt dasselbe Geschäftsvorfall.
- **Quelle:** D-06, FR-021

## Zustelllauf-ID

Stabile Kennung eines Zustelllaufs — über alle Versuche dieses Laufs gleich.

- **Ausprägung:** UUID, im ausgehenden Request als `X-EchoBox-Delivery-Run-Id`.
- **Nicht:** dauerhaft. Ein Replay erzeugt einen neuen Zustelllauf mit neuer
  ID; die alte bleibt als Historie erhalten.
- **Quelle:** D-06, FR-024

## Versuchs-ID

Eindeutige Kennung eines einzelnen HTTP-Versuchs.

- **Ausprägung:** UUID, im ausgehenden Request als `X-EchoBox-Attempt-Id`.
- **Nicht:** stabil. Jeder Wiederholungsversuch hat eine eigene.
- **Quelle:** D-06, FR-021

## Aufgegeben / tot

Zustellung endet ohne Erfolg; keine weiteren Versuche.

- **Ausprägung:** Status `abandoned` nach Erreichen der konfigurierten
  Versuchsgrenze; `abandoned_at` gesetzt.
- **Nicht:** `rejected` — dort wollte der Empfänger das Ereignis nicht.
  `cancelled` — dort hat der Endpunkt pausiert. Beides ist kein „tot“, sondern
  ein bewusstes Ende.
- **Quelle:** FR-023, FR-024

## Abgelehnt

Der Empfänger hat dauerhaft signalisiert, dass er das Ereignis nicht möchte.

- **Ausprägung:** Status `rejected` mit `rejection_reason` (`permanent_rejection`
  oder `client_error`); keine weiteren Versuche.
- **Nicht:** ein Fehler des Dienstes. Der Empfänger hat recht — die Zustellung
  wird ohne Schuldzuweisung beendet.
- **Quelle:** FR-025, D-08

## Kein Abnehmer

Ein Ereignis, für das kein Endpunkt den Ereignistyp abonniert hat.

- **Ausprägung:** Ereignis-Status `no_subscriber` und ein Zustelllauf mit
  Status `no_subscriber` und `endpoint_id = null`.
- **Nicht:** ein Fehler und kein verworfenes Ereignis. Das Ereignis wird
  angenommen und gespeichert; es ist lediglich sichtbar unzustellbar.
- **Quelle:** D-04

## Signatur

Nachrechenbare Kennung der Zustellung: `HMAC-SHA256` über Zeitstempel und
Nutzlastbytes.

- **Ausprägung:** Header `X-EchoBox-Signature` mit dem Wert `v1=<hex>`; die
  Signier-Eingabe ist `<timestamp>.<payload_bytes>`.
- **Nicht:** eine Prüfsumme der Nutzlast allein und **nicht** über die Chiffre
  hinweg veränderlich — der Zeitstempel ist Teil der Signatur.
- **Quelle:** FR-020, D-07, ADR-004

## Ablehnungssignal

Das dokumentierte Signal, mit dem der Empfänger ein Ereignis dauerhaft
ablehnt.

- **Ausprägung:** HTTP **410 Gone**, Header `X-EchoBox-Rejection: permanent`,
  Body `{rejection, reason, message?, rejected_event_types?}`.
- **Nicht:** ein beliebiger 4xx. Ein solcher führt ebenfalls zum Ende, aber
  **ohne** das ausdrückliche Signal; `rejection_reason` ist dann
  `client_error`.
- **Quelle:** FR-025, D-08

## Idempotenzschlüssel

Der Schlüssel, mit dem eine erneute Einlieferung desselben Ereignisses
erkannt wird.

- **Ausprägung:** Pflichtfeld beim Einliefern; Gültigkeitsbereich ist das Paar
  (Mandant, Schlüssel); ein zweites Ereignis mit demselben Schlüssel entsteht
  nicht.
- **Nicht:** global und **nicht** frei wählbar durch den Aufrufer über den
  Mandanten hinweg. Der Schlüssel allein identifiziert nichts.
- **Quelle:** FR-011, D-05

## Vorverschlüsselte Einlieferung

Ein Ereignis, dessen Nutzlast bereits verschlüsselt eingeliefert wurde.

- **Ausprägung:** `payload_encoding = pre_encrypted`; die Bytes werden
  unverändert gespeichert und zugestellt; die Signatur wird über die Chiffre
  gebildet.
- **Nicht:** ein Hinweis, dass EchoBox den Klartext kennt. Vor der Zustellung
  findet **kein** Entschlüsseln statt.
- **Quelle:** FR-013, ADR-003

## Mandant

Die organisatorische Grenze des Dienstes — Träger von Endpunkten, Ereignissen
und Zustellungen.

- **Ausprägung:** der JWT-Claim `organisation_id`; zusätzlich transaktionslokal
  in der Datenbank gesetzt und dort per Row-Level-Security erzwungen.
- **Nicht:** ein Filterparameter. Ein mitgelieferter Mandantenparameter wird
  mit HTTP 422 abgelehnt.
- **Quelle:** `docs/Anforderung.md` Abschnitt 3, D-01, ADR-002

## Rolle

Die Berechtigungsklasse eines Aufrufers.

- **Ausprägung:** `developer`, `receiver_admin`, `service_admin` — aus dem
  Token gelesen, nicht aus dem Request.
- **Nicht:** ein vom Aufrufer wählbares Feld. Wer sich als
  `service_admin` ausgibt, hat das nicht gesagt — der Identity Provider hat
  es bestätigt.
- **Quelle:** `docs/Anforderung.md` Abschnitt 3, D-15, ADR-015

## Replay

Das erneute Auslösen einer aufgegebenen Zustellung.

- **Ausprägung:** ein **neuer** Zustelllauf mit neuer Zustelllauf-ID, gleicher
  Ereignis-ID und zurückgesetzter Versuchszahl; der alte Datensatz bleibt
  unverändert.
- **Nicht:** ein erneutes Senden desselben Laufs und **nicht** ein Neuversand
  des Ereignisses an alle Endpunkte.
- **Quelle:** FR-024, D-06

## Aufbewahrung

Das Löschen von Ereignissen, Zustellläufen und Versuchen nach einer Frist.

- **Ausprägung:** `deleted_at` markiert die betroffenen Zeilen (sie sind
  sofort unsichtbar); ein Purge-Vorgang entfernt sie endgültig. Die Frist ist
  je Mandant und **getrennt** für Ereignisse und Zustellprotokolle
  konfigurierbar; Vorgabewert **je 30 Tage**, Fristbeginn ist der **letzte
  Zustellversuch** (bestätigt am 2026-10-21, F-02). Die **Versuche** haben
  keine eigene Markierung: sie verlieren die Sichtbarkeit **mit ihrem
  Zustelllauf** — und zählen danach auch nicht mehr in den Kennzahlen.
- **Nicht:** ein fest verdrahteter Wert. Der Vorgabewert steht in der
  Konfiguration und ist je Mandant überschreibbar.
- **Quelle:** FR-031, D-12, ADR-012

## Endpunkt-Begrenzung

Der Schutz eines Endpunkts gegen zu viele Zustellversuche in kurzer Zeit.

- **Ausprägung:** Zähler je Endpunkt und Zeitfenster; die Strategie ist
  `delay` (verschieben) oder `block`; der Grenzwert ist konfigurierbar —
  Vorgabewert **60 Versuche je Endpunkt und Minute** mit Strategie `delay`
  (bestätigt am 2026-10-21, F-01).
- **Nicht:** ein API-Anfragelimit und **nicht** eine Blockade, die Ereignisse
  verwirft. Nichts geht verloren (FR-033).
- **Quelle:** FR-033, D-14, ADR-010
