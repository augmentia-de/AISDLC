# ADR-004 — Storage, Retention und Datenschutz: Trennung von Telemetrie und Registry-Inhalt

| Feld | Wert |
|---|---|
| **ID** | ADR-004 |
| **Titel** | Storage, Retention und Datenschutz |
| **Status** | **Angenommen** |
| **Datum** | 2026-10-20 |
| **Entscheider** | Architecture Orchestrator (`aisdlc-architecture`), bestätigt durch F5 |
| **Betrifft** | FR-060, FR-061, FR-062, FR-063, FR-064, FR-065, DP-001…DP-008, NFR-013, SEC-012, A-03, F5, K-3, R-06 |
| **Supersediert** | — |

## Kontext

Die Kunden-Mail fordert „DSGVO mit 90-Tage-Löschung". Das PRD (F5, A-03) präzisiert: die
90-Tage-Löschung gilt für **Logs, Rohtelemetrie und KI-Rohpayloads** (letztere nach 24 h) —
**nicht** für Prompts und Versionen. Diese bleiben bis zur expliziten Löschung.

Damit stehen zwei Ziele in Spannung:

* **K-3** — das zentrale Register soll einen Verlauf über Monate behalten (sonst ist das
  Register nach 90 Tagen leer und der Zweck des Produkts — Nachvollziehbarkeit — verloren).
* **DP-003 / K8** — Telemetrie, die Inhalte berühren kann, muss nach 90 Tagen weg sein.

Zusätzlich zu entscheiden: **Wo** liegen die Daten, **wie** werden sie geschützt, und
**wie** wird das Recht auf Löschung technisch durchgesetzt (FR-063, DP-004, DP-007, DP-008)?

## Entscheidung

### 1. Aufbewahrungsmatrix — drei Klassen, drei Fristen

| Klasse | Objekte | Frist | Löschung |
|---|---|---|---|
| **Fachinhalt** | `prompt`, `prompt_version`, `scan_finding.redacted_excerpt`, `waiver.reason` | **unbegrenzt**, bis explizite Löschung | `DELETE /api/v1/prompts/{id}` bzw. Recht auf Löschung |
| **Verdichtetes Ergebnis** | `scan_run` (ohne Rohpayload), Status, Schwellen, `finding` (maskiert) | unbegrenzt, solange der Prompt existiert | Kaskade mit dem Prompt |
| **Telemetrie** | Request-/Access-Logs, `scan_raw_payload` (KI-Rohdaten), `notification_delivery`-Protokolle, `audit_event` mit Inhaltsbezug | **90 Tage** | täglicher Retention-Job |
| **KI-Rohpayload** | rohe Anfrage-/Antwort-Payloads des Providers | **24 Stunden** | separater, häufigerer Job |
| **Audit ohne Inhaltsbezug** | `audit_event` (Aktion, Actor, Zeitpunkt) | 90 Tage | Teil des Retention-Jobs |

> **Hinweis zur Abgrenzung:** „Audit-Einträge mit Inhaltsbezug" (FR-060) — der Job löscht
> alle Audit-Einträge, die Inhaltsbezug haben oder deren Aufbewahrung mit dem Inhalt
> verknüpft ist. Da die Audit-Einträge **per Konstruktion** inhaltsfrei sind (FR-062), ist
> die Menge praktisch die gesamte Audit-Tabelle. Diese Auslegung wird in
> `.agent/artifacts/api-review.md` als Befund B-08 festgehalten und in der zweiten
> Abgleich-Runde gegen das Schema bestätigt.

### 2. Drei Jobs, ein Zeitplan

| Job | Takt | Wirkung | Idempotenz |
|---|---|---|---|
| `RetentionJob` | täglich 03:00 UTC | löscht Telemetrie > 90 Tage, `scan_raw_payload` > 24 h, Audit > 90 Tage |ja — ein zweiter Lauf löscht 0 und meldet 0 ohne Fehler (AC-028-2) |
| `RawPayloadJob` | stündlich | löscht KI-Rohpayloads > 24 h | ja |
| `OrphanCleanup` | täglich | findet verwaiste Jobs/Versionen ohne Mandanten-Kontext und verwirft sie (SEC-003) | ja |

* **Fehlerverhalten:** Schlägt ein Löschvorgang fehl (DB-Fehler), endet der Job **mit
  Fehler**, wird erneut versucht und löst einen Alarm aus. Es bleibt **kein** teilweise
  oder inkonsistenter Zustand zurück (AC-028-3) — die Löschung läuft in einer Transaktion
  je Objektgruppe.
* **Nachweis:** Jeder Lauf schreibt eine Zusammenfassung der gelöschten Mengen (Anzahl je
  Klasse, Zeitfenster) in die Logs und in `audit_event` (`RETENTION_RUN`) — ohne Inhalt.

### 3. Verschlüsselung und Zugriff

* **Ruhende Daten:** Verschlüsselung über den Datenbank-/Storage-Dienst (SEC-012).
  PostgreSQL-Volume-Encryption plus Backend-Verschlüsselung für die als sensibel
  markierten Spalten (`user.password_hash`, `invitation.token_hash`,
  `refresh_token`).
* **Refresh-Token:** werden **nur als Hash** gespeichert (SEC-012, AC-034-6) — ein
  Datenbank-Dump erlaubt kein Replay.
* **Passwörter:** bcrypt/argon2id, Cost ≥ 12 (FR-008). Das Klartextpasswort verlässt den
  Registrierungs-/Login-Fluss nie.
* **Kein Inhalt in Logs (FR-062):** Log-Zeilen enthalten `correlationId`, `organisationId`
  und technische Kennzahlen — **niemals** Prompt-Inhalt, Secrets, Tokens oder
  personenbezogene Daten. Die Durchsetzung erfolgt über eine zentrale Logback-/SLF4J-
  Konfiguration, die keine beliebigen Objekt-`toString()`-Werte durchreicht, plus ein
  automatischer Test, der die Logzeilen eines Requests mit Secret-haltigem Inhalt prüft
  (AC-032-2).
* **Suchindex:** Der Volltextindex enthält denselben Inhalt wie die Fachtabelle. Er wird
  **gemeinsam** mit ihr gelöscht (kein zweiter, unabhängiger Lebenszyklus).

### 4. Recht auf Löschung (FR-063, DP-004, DP-007, DP-008)

Zwei Wege, beide im Vertrag:

| Weg | Operation | Wirkung |
|---|---|---|
| Betroffener selbst | `DELETE /api/v1/me` | Konto, Tokens, Sitzungen gelöscht; Autorschaft in Versionen **anonymisiert**; Prompt-Bestand bleibt; betroffene Versionen werden erneut zur Prüfung eingereiht |
| `ORG_ADMIN` | `DELETE /api/v1/users/{userId}` | identisch, ausgelöst durch die Organisation |

* **Anonymisierung** statt Löschung der Fremdinhalte: `prompt_version.created_by` und
  `prompt.created_by` werden `NULL`; `audit_event.actor_id` wird `NULL`, Zeitpunkt und
  Aktion bleiben (AC-029-3). Der fachliche Inhalt gehört der Organisation, nicht der
  Person.
* **Re-Scan nach Anonymisierung:** Die betroffenen Versionen werden erneut eingereiht, weil
  eine erneute Prüfung ohne personenbezogenen Autor sinnvoll ist. Der Status wechselt
  `APPROVED|FLAGGED|WAIVED → PENDING`; die alte Version bleibt als Historie bestehen — die
  Freigabe der neuen Version entsteht aus dem unveränderten Inhalt.
* **Auskunft/Berichtigung:** `GET /api/v1/me/export` (maschinenlesbar, DP-008),
  `PATCH /api/v1/me` (Berichtigung von Anzeigename und E-Mail, DP-007).
* **Widerspruch:** organisatorisch geregelt (DP-007) und über den Widerruf der
  Einladung sowie das Entziehen des Zugangs abbildbar; im MVP ohne eigene Operation.

### 5. Datenschutzhinweis DE/EN (FR-064, DP-001, DP-006)

* `GET /api/v1/privacy-notice` — **öffentlich**, beide Sprachen in einer Antwort, mit
  Zweck, Rechtsgrundlage, Aufbewahrungsfristen und Rechten.
* `GET /api/v1/privacy-notice/versions` — archivierte Fassungen mit Versionskennung und
  Gültigkeitsbeginn (Nachweis der Historie, AC-030-2).
* Der Text wird **versioniert im Repository** gehalten; jede Änderung erzeugt eine neue
  Fassung, ältere Fassungen bleiben archiviert.

### 6. EU-Hosting und Provider-Auflagen (FR-065, DP-005)

* Produktion ausschließlich `europe-west3` (Cloud Run + Cloud SQL/Supabase EU).
* Der KI-Provider wird ausschließlich mit Auftragsverarbeitungsvertrag, EU-Region,
  Trainingsverbot und Löschgarantie betrieben. Die Plattform **verifiziert diese
  Konfiguration vor dem ersten Aufruf** und bricht den Scan bei Verstoß ab, ohne dass
  Inhalt das System verlässt (→ ADR-006).

## Konsequenzen

### Positiv

* Der Zweck des Produkts (Nachvollziehbarkeit über Monate) bleibt erhalten, während die
  Telemetrie sauber begrenzt ist — K-3 sauber aufgelöst.
* Löschung ist **nachweisbar**: jeder Joblauf erzeugt eine Zusammenfassung, jederzeit
  reproduzierbar.
* Passwörter, Refresh-Token und Einladungstoken sind im Datenbank-Dump wertlos.
* Der Export macht DP-007/DP-008 im MVP erfüllbar, ohne eine separate „Datenkonto"-Seite.

### Negativ / Kosten

* **Drei Jobs** mit je eigener Transaktionslogik — Aufwand im Betrieb, dafür klare
  Verantwortung.
* **Anonymisierung statt Löschung** der Autorschaft ist rechtlich eine Abwägung: Der Inhalt
  bleibt, die Person verschwindet. Das ist mit dem Product Owner und der DPO zu
  bestätigen (Befund B-09 in `api-review.md`).
* **Re-Scan nach Anonymisierung** erzeugt zusätzliche Provider-Aufrufe und damit Kosten —
  bewusst, weil sonst eine Person als verantwortlich im Register stehen bliebe.
* **Suchindex und Fachtabelle** müssen gemeinsam gelöscht werden — die Löschlogik ist
  damit an zwei Stellen zu pflegen (im MVP eine Tabelle, keine separate Suchengine — ADR-001).

### Neutral

* Die 90-Tage-Frist ist **keine** Frist für Prompt-Inhalte. Diese Interpretation ist
  F5/A-03 geschuldet und im Datenschutzhinweis explizit zu benennen (DP-001).

## Alternativen, die verworfen wurden

| Alternative | Warum verworfen |
|---|---|
| 90 Tage für **alle** Inhalte | Widerspricht F5/A-03; das Register wäre nach 90 Tagen leer (K-3). |
| Keine Rohpayload speichern (gar nicht) | Der Provider-Vertrag verlangt „keine dauerhafte Speicherung" — die Plattform muss die Rohantwort für den Debugging-Nachweis kurz halten können, sonst fehlt der Nachweis. 24 h sind der Kompromiss. |
| Unbegrenzte Aufbewahrung der Telemetrie | Widerspricht DP-003/NFR-013. |
| Physisches Löschen der Versionen bei Anonymisierung | Zerstört die Nachvollziehbarkeit des Registers und widerspricht der Aussage „Inhalt gehört der Organisation“. |
| Löschung als manueller Prozess ohne API | Widerspricht DP-004 („technisch über einen auslösbaren Vorgang umgesetzt“). |

## Verweise

* `docs/requirements/PRD.md` §8 (FR-060…FR-065), §11 (DP-001…DP-008), §13 (A-03), §14 (F5)
* `docs/architecture/diagrams/02-container.mmd` — Container `Retention-Job`
* `docs/api/openapi.yaml` — `eraseOwnAccount`, `eraseUserAccount`, `exportOwnData`, `getPrivacyNotice`
* `.agent/artifacts/api-review.md` — Befunde B-08, B-09
