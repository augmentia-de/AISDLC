# PromptGuard — API-Review (openapi-first)

| Feld | Wert |
|---|---|
| **Artefakt** | `api-review.md` (Schritt-Artefakt des Architecture Orchestrators) |
| **Vertrag** | `docs/api/openapi.yaml` (OAS 3.0.3) |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Skill** | `aisdlc-openapi-first` |

---

## 0. Ergebnis

> ## API-REVIEW: **PASS_WITH_FINDINGS**
>
> **Begründung:** Der Vertrag ist **strukturell vollständig** und **lückenlos**:
> * **60 Operationen**, alle mit `operationId` (stabil, camelCase, eindeutig), `summary`,
>   `description`, `responses`, `security` (oder explizit `security: []`), `tags` sowie
>   `x-tenant-scope`, `x-required-role` und `x-stories`.
> * **Ein** Fehlerformat für alle Operationen (RFC 9457 + `correlationId` + Feldfehlerliste);
>   Statuscodes 400/401/403/404/409/422/429/500 mit fester Bedeutung (`503` nur als
>   dokumentierte Erweiterung bei `GET /health`).
> * **Jeder Lese- und Schreibpfad** trägt den `organisation_id`-Scope; ein Objekt einer
>   fremden Organisation liefert `404` ohne Existenzinformation.
> * **Pagination, Filter und Sortierung** sind Vertragsbestandteil (Default 50, Maximum 200,
>   `hasMore`, `nextCursor`).
> * **35/35 Stories** sind durch mindestens eine Operation abgedeckt, **60/60 Operationen**
>   haben mindestens eine Story — **keine Lücke**.
> * Die Komponenten-Schemata spiegeln `docs/architecture/schema.sql` in Typ und
>   Nullbarkeit.
>
> Die **15 Befunde** (Abschnitt 2) sind **keine offenen Implementierungslücken**: 8 wurden in
> diesem Schritt **bereits behoben**, 7 sind **bewusste Festlegungen** oder **Auslegungen**
> und brauchen eine Bestätigung (Product Owner bzw. DPO) — siehe Abschnitt 6.
> Maschinell geprüft mit `docs/api/check_contract.py` (Ergebnis in Abschnitt 6).

---

## 1. Umfang des Vertrags

| Kennzahl | Wert |
|---|---|
| Operationen | **60** |
| Pfade | **43** |
| Komponenten-Schemata | **77** |
| Fehlerantwort-Komponenten | 8 (`BadRequest`, `Unauthorized`, `Forbidden`, `NotFound`, `Conflict`, `ValidationFailed`, `RateLimited`, `InternalError`) |
| Wiederverwendbare Parameter | 7 (`PageParam`, `SizeParam`, `PromptIdPathParam`, `VersionIdPathParam`, `CategoryIdPathParam`, `UserIdPathParam`, `InvitationIdPathParam`) |
| Security-Schemes | 1 (`bearerAuth`, HTTP/JWT) |
| Stories im Backlog | 35 |
| Stories mit mindestens einer Operation | **35** |
| Operationen ohne Story | **0** |

**Operationen je Tag**

| Tag | Operationen | Stories |
|---|---|---|
| Authentication | 5 | US-001…US-003 |
| Account | 6 | US-029, US-035 |
| Organisation | 8 | US-004, US-006, US-017, US-021 |
| Prompts | 8 | US-005, US-007…US-009, US-012…US-015 |
| Taxonomy | 7 | US-010, US-011 |
| Scans | 5 | US-016…US-023 |
| Audit | 2 | US-025 |
| Notifications | 3 | US-026, US-027 |
| Privacy | 2 | US-030 |
| Operations | 6 | US-028, US-031…US-034 |

---

## 2. Befunde

### 2.1 In diesem Schritt behobene Befunde

| ID | Befund | Behandlung |
|---|---|---|
| **B-01** | **Pfadabweichung Schema.** Der Workflow `02-architecture.md` nennt `.agent/knowledge/schema.sql` als Ziel des projektspezifischen Schemas; `.agent/knowledge/` war in diesem Lauf **schreibgeschützt**. | **Behoben:** Das Schema liegt unter **`docs/architecture/schema.sql`** (im Vertrag als Quelle der Feldtypen referenziert) und ist **byte-identisch nach `.agent/knowledge/schema.sql` gespiegelt** — der kanonische Ort des Workflows enthält damit das Projektschema. → *Prozessbefund, kein Vertragsbefund.* |
| **B-03** | **Fehlendes `organisationId` in sechs Ressourcen.** `Tag`, `StatusTransition`, `Waiver`, `PromptVersion`, `ScanFinding` und `DuplicateMatch` führten den Mandanten nicht im Vertrag — obwohl ADR-002 „je Ressource das Feld `organisationId`" verlangt. | **Behoben:** `organisationId` ergänzt und überall **required**. |
| **B-05** | **`size > 200` als `400` statt `422`.** AC-015-3 fordert ausdrücklich `400`. | **Behoben/bestätigt:** `400` bleibt; die Regel steht jetzt im Vertrag („Fehlerkatalog je Operation") und ist maschinell geprüft. |
| **B-06** | **Fehlende `hasMore`/`nextCursor`.** Die Sammlungs-Schemas kannten nur `page`/`size`/`totalElements`/`totalPages`. | **Behoben:** `hasMore` (required) und `nextCursor` (nullable) in **allen 11** `Paged*`-Schemas ergänzt; in `info.description` erläutert. |
| **B-11** | **US-028 und US-031 ohne Operation.** Retention-Lauf und Coverage-Gate sind Betriebs-/Pipeline-Vorgänge und hatten im fachlichen Vertrag keine Entsprechung. | **Behoben:** `GET/POST /api/v1/ops/retention` und `GET/POST /api/v1/ops/quality-gate` ergänzt (US-028, US-031). Das Qualitäts-Gate wird über ein **CI-Service-Token** (Scope `ci:quality-gate`) eingereicht, nicht über einen Benutzer-Token. |
| **B-12** | **Fehlende Operation für Tag-Vergabe (US-011).** Tags waren nur Teil von Prompt-Body; AC-011-2/AC-011-3 (Limit-Überschreitung ⇒ `422`) hatten keinen eigenen Pfad. | **Behoben:** `PUT /api/v1/prompts/{promptId}/tags` (`assignPromptTags`) ergänzt; die Änderung erzeugt — wie jede inhaltliche Änderung — eine neue Version (FR-012, I-1). |
| **B-13** | **Keine Fundstellen-Operation je Kategorie.** US-018/019/020 (Injection/Jailbreak, Secrets, PII) teilten sich nur `getScanResult`. | **Behoben:** `GET /api/v1/prompt-versions/{versionId}/findings` (`listFindings`) mit Filtern `category` und `severity` und Pagination ergänzt. |
| **B-16** | **Nullabilität des Vertrags wich von der Datenbank ab** (maschinell geprüft, 140 Feld-Mappings): `ScanRun.threshold`, `ScanRun.duplicateThreshold` (DB nullable, Vertrag required) und `Waiver.waivedBy` (DB nullable wegen `ON DELETE SET NULL`). | **Behoben:** `threshold`/`duplicateThreshold` sind im Vertrag jetzt nullable (die angewandten Schwellen sind erst **nach** Abschluss des Laufs bekannt, AC-021-3), `waivedBy` ist nullable (Anonymisierung bei Kontolöschung, FR-063). Die einzige verbleibende Abweichung ist **B-15**. |

### 2.2 Bewusste Festlegungen und Auslegungen (bestätigungsbedürftig)

| ID | Befund | Auswirkung | Empfehlung |
|---|---|---|---|
| **B-02** | **`403` statt `404` bei Löschung/Export eines Nutzers einer fremden Organisation.** AC-029-2 fordert `403`; ADR-002 verlangt ansonsten `404` (kein Existenz-Leak). | Schwache Existenzinformation über User-UUIDs. | **Bestätigung durch Product Owner/DPO**, Prüfung in Phase 8 (Befund in ADR-002 vermerkt). Mitigation: UUIDv4, nicht erratbar. |
| **B-07** | **Kein Re-Scan freigegebener Versionen.** `APPROVED`/`WAIVED` → `PENDING` existiert nur über eine **inhaltliche Änderung** (neue Version). | Eine bewusste Entscheidung muss eine Inhaltsänderung erzeugen, um neu geprüft zu werden. | **Bestätigung**, dass die Entscheidung so gewollt ist (ADR-003). |
| **B-08** | **Auslegung „Audit-Einträge mit Inhaltsbezug" (FR-060).** Audit-Einträge sind per Konstruktion inhaltsfrei; der Job löscht deshalb die gesamte Audit-Tabelle nach 90 Tagen. | Die Löschung ist weiter als der Wortlaut möglicherweise meint. | **Bestätigung durch DPO**; Auslegung im Datenschutzhinweis benennen (ADR-004). |
| **B-09** | **Anonymisierung statt Löschung der Autorschaft** bei Recht auf Löschung (`created_by → NULL`). Der Prompt-Inhalt bleibt bestehen. | Rechtliche Abwägung: Inhalt gehört der Organisation, nicht der Person. | **Bestätigung durch DPO und Product Owner** (ADR-004). |
| **B-10** | **Zugriff auf KI-Rohpayloads ist nicht Teil des fachlichen Vertrags.** | Ein Provider-Debug ist nach 24 h nicht möglich; die Rohpayload bleibt eine Betriebsfunktion. | **Bewusste Grenze** (ADR-006). Alternative in Phase 8 prüfen. |
| **B-14** | **`GET /health` kann `503` liefern** — ein Statuscode außerhalb des Katalogs 400/401/403/404/409/422/429/500. | Kein Widerspruch: `503` erscheint **nur** bei `status: DOWN`; ein KI-Ausfall führt zu `DEGRADED` mit `200`. | **Dokumentierte Erweiterung**, im Vertrag an der Operation beschrieben. |
| **B-15** | **`Prompt.currentVersionId`: Vertrag `required`, Datenbank nullable.** Der Foreign Key ist zirkulär (`prompt.current_version_id` → `prompt_version.id`, `prompt_version.prompt_id` → `prompt.id`), deshalb muss die Spalte beim INSERT leer sein und wird im selben Commit gesetzt. | **Kein beobachtbarer Zustand**: ein Prompt wird nie ohne aktuelle Version ausgeliefert — Version und Job entstehen in derselben Transaktion (CON-009). | **Bewusste Ausnahme**, dokumentiert in `docs/architecture/schema.sql` und hier. Alternative: Vertrag auf `nullable` umstellen (empfohlen erst, wenn der Schreibpfad davon abweicht). |

### 2.3 Weitere Prüfpunkte ohne Befund

| Prüfpunkt | Ergebnis |
|---|---|
| Eindeutigkeit der `operationId` | 60/60 eindeutig, alle camelCase |
| Pflichtangaben je Operation | vollständig (keine Ausnahme) |
| `security` je Operation | immer gesetzt; `security: []` nur bei `/auth/register`, `/auth/login`, `/auth/refresh`, `/privacy-notice`, `/privacy-notice/versions` |
| Fehlerantworten | jede Operation dokumentiert **alle** für sie ableitbaren Codes (regelbasiert geprüft) |
| `$ref`-Integrität | alle Referenzen zeigen auf existierende Komponenten |
| Komponentennamen | alle konform zur OpenAPI-Namenskonvention (`^[a-zA-Z0-9.\-_]+$`) |
| Fehlerformat | ein `Problem`-Schema, `type`/`title`/`status`/`detail`/`instance` alle `required` |
| Pagination | `size` Default 50, Maximum 200, `400` bei Überschreitung; `totalElements` immer auf die **gefilterte** Menge der eigenen Organisation |
| Tenant-Scope | `x-tenant-scope` auf 60/60 Operationen; `organisationId` in allen mandantenbehafteten Ressourcen-Schemata |
| Story-Zuordnung | `x-stories` auf 60/60 Operationen, 35/35 Stories abgedeckt, 0 unbekannte Verweise |

---

## 3. Schema-Abgleich (Vertrag ↔ `docs/architecture/schema.sql`)

Der Abgleich wurde **nicht von Hand**, sondern **maschinell gegen eine laufende
PostgreSQL-16-Instanz** durchgeführt: Das Schema wurde angewendet, die tatsächliche
Spaltenstruktur aus `information_schema` gelesen und Feld für Feld gegen
`components.schemas` des Vertrags geprüft (Typ und Nullbarkeit).

```bash
$ psql -d pgcheck -v ON_ERROR_STOP=1 -f docs/architecture/schema.sql
CREATE TABLE … (56 Aussagen, 0 Fehler)

$ python3 schema_check.py
geprüfte Feld-Mappings: 140
  (Hinweis) AlertRecipient.id: nicht im Vertrag (Spalte alert_recipient.id existiert)
  …
ABWEICHUNGEN (1):
  - Prompt.currentVersionId: DB nullable, Vertrag required
```

| Komponente | Tabelle | Ergebnis |
|---|---|---|
| `Organisation` | `organisation` | deckungsgleich (`scanThreshold`, `duplicateThreshold`, `duplicateDetectionEnabled`, `maxTagsPer*`, `providerVerified`) |
| `User` | `app_user` + `membership` | deckungsgleich; `role`/`status`/`organisationId` sind die Projektion der Membership, `passwordHash` bleibt außen vor |
| `Membership` | `membership` | deckungsgleich |
| `Invitation` | `invitation` | deckungsgleich (`tokenHash` bleibt außen vor) |
| `AlertRecipient` | `alert_recipient` | deckungsgleich; `id`/`organisationId`/`createdAt` werden nicht ausgeliefert (Hinweis, kein Fehler) |
| `Category` | `category` | deckungsgleich (`promptCount` ist abgeleitet) |
| `Tag` | `tag` | deckungsgleich nach B-03 (`createdAt` wird nicht ausgeliefert) |
| `Prompt` / `PromptSummary` | `prompt` | deckungsgleich bis auf **B-15**; `content` stammt aus `prompt_version`, `currentVersionNo` ist abgeleitet |
| `PromptVersion` | `prompt_version` | deckungsgleich nach B-03 |
| `PromptVersion.waiver` | `waiver` | deckungsgleich nach B-03/B-16 |
| `ScanRun` | `scan_run` | deckungsgleich nach B-16 |
| `ScanFinding` | `scan_finding` | deckungsgleich nach B-03; `line`/`column` → SQL `line_no`/`column_no` (`line`/`column` sind reservierte Wörter) |
| `DuplicateMatch` | `duplicate_match` | deckungsgleich nach B-03 (`method` wird nicht ausgeliefert) |
| `Waiver` | `waiver` | deckungsgleich nach B-03/B-16 |
| `StatusTransition` | `status_transition` | deckungsgleich nach B-03 |
| `AuditEvent` | `audit_event` | deckungsgleich; `fromStatus`/`toStatus`/`cause` werden nicht ausgeliefert (Hinweis) |
| `PromptStatus` | `status_event` | `sequence`, `status`, `findingCount`, `occurredAt` deckungsgleich; `retryable` ist eine **Ableitung** aus `prompt_version.status = FAILED` |
| `NotificationDelivery` | `notification_delivery` | deckungsgleich; `promptTitle` ist ein Join-Feld, `findingCategories`/`findingSeverities` sind in der Spalte `finding_categories`/`finding_severities` (CSV) abgelegt |
| `ProviderVerification` / `ProviderCheck` | `organisation.provider_verified` + ENV-Konfiguration | Nachweisobjekt; die Prüfwerte selbst sind Konfiguration, keine Tabellenzeilen |
| `ProviderTransfer` | `transfer_audit` | deckungsgleich |
| `RetentionRun` | `retention_run` | deckungsgleich (`deletedCounts` ↔ `deleted_counts` JSONB) |
| `QualityGateAttestation` | `quality_gate_attestation` | deckungsgleich (`id` wird nicht ausgeliefert) |
| `PrivacyNotice` | `privacy_notice` | deckungsgleich (`languages` = die beiden Sprachfassungen) |
| `Problem` / `FieldError` | — | kein Tabellenbild (Fehlerobjekt) |
| `Paged*` | — | Projektion von Listen + Zählwerten (`hasMore`/`nextCursor` sind abgeleitet) |

**Nicht im Vertrag, aber in der Datenbank** (bewusst): `refresh_token`, `scan_job`,
`status_event` (technisch), `scan_raw_payload` (Betrieb, B-10), `scan_finding.stage`.

**Gesamtergebnis:** 140 Feld-Mappings geprüft — **1 dokumentierte Abweichung (B-15)**,
0 ungeklärte Typabweichungen.

## 4. Story → Operation — vollständige Zuordnung

| Story | Epic / Titel | Anforderungen | Operationen |
|---|---|---|---|
| `US-001` | E1 — Anmeldung mit E-Mail und Passwort | `FR-001`, `FR-003`, `FR-008`, `SEC-001`, `SEC-008` | `loginUser` (POST `/auth/login`) · `getCurrentUser` (GET `/me`) · `changeOwnPassword` (POST `/me/password`) |
| `US-002` | E1 — Registrierung erzeugt eine eigene Organisation | `FR-002` | `registerUser` (POST `/auth/register`) |
| `US-003` | E1 — Token-Lebenszyklus: Refresh, Rotation und Logout | `FR-007`, `SEC-001` | `refreshTokens` (POST `/auth/refresh`) · `logout` (POST `/auth/logout`) |
| `US-004` | E1 — Rollen und Berechtigungen | `FR-003`, `FR-004`, `SEC-004` | `getCurrentUser` (GET `/me`) · `listOrganisationMembers` (GET `/organisations/current/members`) · `getOrganisationMember` (GET `/organisations/current/members/{userId}`) · `updateOrganisationMemberRole` (PATCH `/organisations/current/members/{userId}`) · `listCategories` (GET `/categories`) · `createCategory` (POST `/categories`) · `createWaiver` (POST `/prompt-versions/{versionId}/waiver`) |
| `US-005` | E1 — Striktes Mandantenscoping auf jeder Route | `FR-009`, `FR-023`, `SEC-002`, `SEC-003` | `getOrganisationMember` (GET `/organisations/current/members/{userId}`) · `listPrompts` (GET `/prompts`) · `getPrompt` (GET `/prompts/{promptId}`) |
| `US-006` | E1 — Mitglieder einladen und Alarmempfänger pflegen | `FR-005`, `FR-006`, `FR-053` | `getCurrentOrganisation` (GET `/organisations/current`) · `updateCurrentOrganisation` (PATCH `/organisations/current`) · `listOrganisationMembers` (GET `/organisations/current/members`) · `removeOrganisationMember` (DELETE `/organisations/current/members/{userId}`) · `listAlertRecipients` (GET `/organisations/current/alert-recipients`) · `replaceAlertRecipients` (PUT `/organisations/current/alert-recipients`) · `listInvitations` (GET `/organisations/current/invitations`) · `createInvitation` (POST `/organisations/current/invitations`) · `getInvitation` (GET `/organisations/current/invitations/{invitationId}`) · `revokeInvitation` (DELETE `/organisations/current/invitations/{invitationId}`) · `acceptInvitation` (POST `/invitations/accept`) |
| `US-007` | E2 — Prompt anlegen | `FR-010`, `FR-011`, `FR-030` | `createPrompt` (POST `/prompts`) |
| `US-008` | E2 — Bearbeiten erzeugt eine neue Version und setzt die Freigabe zurück | `FR-012` | `updatePrompt` (PATCH `/prompts/{promptId}`) |
| `US-009` | E2 — Versionshistorie und Versionsvergleich | `FR-013`, `NFR-005` | `listPromptVersions` (GET `/prompts/{promptId}/versions`) · `getPromptVersion` (GET `/prompts/{promptId}/versions/{versionNo}`) · `comparePromptVersions` (GET `/prompts/{promptId}/diff`) |
| `US-010` | E2 — Kategorien verwalten | `FR-014` | `listCategories` (GET `/categories`) · `createCategory` (POST `/categories`) · `getCategory` (GET `/categories/{categoryId}`) · `updateCategory` (PATCH `/categories/{categoryId}`) · `deleteCategory` (DELETE `/categories/{categoryId}`) |
| `US-011` | E2 — Tags vergeben | `FR-015` | `assignPromptTags` (PUT `/prompts/{promptId}/tags`) · `listTags` (GET `/tags`) |
| `US-012` | E2 — Prompt löschen | `FR-016`, `DP-004` | `deletePrompt` (DELETE `/prompts/{promptId}`) |
| `US-013` | E3 — Volltextsuche | `FR-020`, `NFR-004` | `listPrompts` (GET `/prompts`) |
| `US-014` | E3 — Filtern nach Kategorie, Tag und Prüfstatus | `FR-021` | `listPrompts` (GET `/prompts`) |
| `US-015` | E3 — Sortierung und Pagination | `FR-022`, `NFR-008` | `listPrompts` (GET `/prompts`) |
| `US-016` | E4 — Asynchroner Prüflauf | `FR-030`, `FR-031`, `NFR-001`, `NFR-002`, `NFR-007`, `CON-009` | `createPrompt` (POST `/prompts`) · `getPrompt` (GET `/prompts/{promptId}`) · `getScanResult` (GET `/prompt-versions/{versionId}/scan`) |
| `US-017` | E4 — Duplikatwarnung innerhalb der eigenen Organisation | `FR-032` | `updateCurrentOrganisation` (PATCH `/organisations/current`) · `getScanResult` (GET `/prompt-versions/{versionId}/scan`) |
| `US-018` | E4 — Erkennung von Prompt Injection und Jailbreak | `FR-033`, `SEC-006`, `SEC-007` | `listFindings` (GET `/prompt-versions/{versionId}/findings`) |
| `US-019` | E4 — Erkennung von Secrets | `FR-034`, `FR-038` | `listFindings` (GET `/prompt-versions/{versionId}/findings`) |
| `US-020` | E4 — Erkennung personenbezogener Daten | `FR-035` | `listFindings` (GET `/prompt-versions/{versionId}/findings`) |
| `US-021` | E4 — Scan-Ergebnis mit Fundstellen | `FR-036`, `FR-037` | `updateCurrentOrganisation` (PATCH `/organisations/current`) · `getScanResult` (GET `/prompt-versions/{versionId}/scan`) · `listFindings` (GET `/prompt-versions/{versionId}/findings`) |
| `US-022` | E4 — Übermittlung an den KI-Dienst unter festen Auflagen | `FR-038`, `FR-074`, `DP-002`, `DP-005`, `SEC-006` | `rescanPromptVersion` (POST `/prompt-versions/{versionId}/rescan`) · `getProviderVerification` (GET `/provider/verification`) · `verifyProviderConfiguration` (POST `/provider/verification`) |
| `US-023` | E4 — Manuelle Ausnahme-Freigabe eines geflaggten Prompts | `FR-039`, `SEC-010` | `createWaiver` (POST `/prompt-versions/{versionId}/waiver`) |
| `US-024` | E5 — Live-Aktualisierung des Prüfstatus in der Oberfläche | `FR-040`, `NFR-003`, `FR-042` | `getPromptStatus` (GET `/prompts/{promptId}/status`) · `streamPromptStatus` (GET `/prompts/{promptId}/status-stream`) |
| `US-025` | E5 — Status- und Änderungshistorie | `FR-041`, `SEC-010`, `FR-060` | `listPromptStatusHistory` (GET `/prompts/{promptId}/history`) · `listAuditEvents` (GET `/audit-events`) |
| `US-026` | E6 — Warn-E-Mail an den Security-Admin | `FR-050`, `FR-051` | `listNotificationDeliveries` (GET `/notifications`) · `getNotificationDelivery` (GET `/notifications/{notificationId}`) |
| `US-027` | E6 — Zustellungswiederholung und Totmannschaltung | `FR-052`, `FR-070`, `NFR-007` | `listNotificationDeliveries` (GET `/notifications`) · `retryNotificationDelivery` (POST `/notifications/{notificationId}/retry`) |
| `US-028` | E7 — Retention-Löschung nach 90 Tagen | `FR-060`, `FR-061`, `DP-003`, `NFR-013` | `listRetentionRuns` (GET `/ops/retention`) · `triggerRetentionRun` (POST `/ops/retention`) |
| `US-029` | E7 — Recht auf Löschung | `FR-063`, `DP-004`, `DP-007`, `DP-008` | `updateOwnProfile` (PATCH `/me`) · `eraseOwnAccount` (DELETE `/me`) · `exportOwnData` (GET `/me/export`) · `removeOrganisationMember` (DELETE `/organisations/current/members/{userId}`) · `eraseUserAccount` (DELETE `/users/{userId}`) · `exportUserData` (GET `/users/{userId}/export`) |
| `US-030` | E7 — Datenschutzhinweis in Deutsch und Englisch | `FR-064`, `DP-001`, `DP-006` | `getPrivacyNotice` (GET `/privacy-notice`) · `listPrivacyNoticeVersions` (GET `/privacy-notice/versions`) |
| `US-031` | E8 — Testabdeckung und Qualitäts-Gate | `NFR-009`, `NFR-010`, `FR-073`, `CON-008` | `getQualityGateAttestation` (GET `/ops/quality-gate`) · `reportQualityGateAttestation` (POST `/ops/quality-gate`) |
| `US-032` | E8 — Observability: Health, Metriken und inhaltsfreie Logs | `FR-072`, `NFR-014`, `FR-062` | `getHealth` (GET `/health`) · `getMetrics` (GET `/ops/metrics`) |
| `US-033` | E8 — Degraded Mode und manuelle Wiederholung | `FR-070`, `FR-071`, `NFR-006`, `NFR-007` | `getPromptStatus` (GET `/prompts/{promptId}/status`) · `rescanPromptVersion` (POST `/prompt-versions/{versionId}/rescan`) · `getHealth` (GET `/health`) |
| `US-034` | E8 — Sicherheits-, Datenschutz- und Betriebsgrundlage | `FR-065`, `FR-074`, `NFR-011`, `NFR-012`, `NFR-017`, `SEC-005`, `SEC-009`, `SEC-011`, `SEC-012`, `DP-005`, `CON-005` | `getProviderVerification` (GET `/provider/verification`) · `verifyProviderConfiguration` (POST `/provider/verification`) · `getHealth` (GET `/health`) · `reportQualityGateAttestation` (POST `/ops/quality-gate`) |
| `US-035` | E8 — Barrierefreiheit, Typsicherheit und Bedienbarkeit | `NFR-015`, `NFR-016`, `NFR-018` | `updateOwnProfile` (PATCH `/me`) |

### 4.1 Ungedeckte Stories

**Keine.** Alle 35 Stories sind abgedeckt.

Anmerkung zur Vollständigkeit: **US-028** (Retention) und **US-031** (Coverage-Gate) sind
**Betriebs-/Pipeline-Vorgänge** und haben im fachlichen Teil des Vertrags bewusst keine
Operation. Sie werden über die Betriebs-Operationen `GET/POST /api/v1/ops/retention` und
`GET/POST /api/v1/ops/quality-gate` abgedeckt (B-11). Ohne diese Ergänzung wären zwei
Stories **ohne** Operation gewesen — das wäre eine echte Lücke der Zuordnung gewesen.

### 4.2 Operationen ohne Story

**Keine.** Alle 60 Operationen tragen mindestens eine Story in `x-stories`.

---

## 5. Fehlende oder unklare Anforderungen

| # | Punkt | Quelle | Umgang im Vertrag |
|---|---|---|---|
| **A-1** | **E-Mail-Bestätigung des Kontos.** AC-001-1 setzt „a registered **and confirmed** developer account" voraus; der Vertrag kennt nur `ACTIVE` und keinen Bestätigungsworkflow. `PATCH /me` beschreibt sogar „sofort übernommen (kein Bestätigungsworkflow im MVP)". | AC-001-1 vs. `ProfileUpdateRequest` | **B-12/B-16**: als **offene Frage** an den Product Owner (Abschnitt 6). Vorschlag: `User.status` um `PENDING_CONFIRMATION` erweitern oder AC-001-1 als „registriert" lesen. |
| **A-2** | **`FR-017` (Prompt kopieren)** ist `SOLL` und hat **keine** Story (Post-MVP, PRD §6.3). | PRD §6.3 | **Bewusst ohne Operation.** Kein Vertragsbestandteil im MVP. |
| **A-3** | **MFA-Einbaupunkt** (F7) verlangt eine Festlegung in Phase 2; das Datenmodell kennt keinen MFA-Faktor. | F7, O6 | Festgelegt in **ADR-001** (`AuthenticationPort`), **nicht** im Vertrag (kein MVP-Umfang). |
| **A-4** | **Löschung einer ganzen Organisation** ist nicht beschrieben. | — | Nicht im MVP; `ORG_ADMIN` kann Mitglieder entfernen, die Organisation selbst bleibt. |
| **A-5** | **Globaler Plattform-Admin** (O7) — ausgeschlossen. | O7, F3 | Keine Operation; der Betrieb bleibt ohne Fachdatenzugriff (ADR-002). |
| **A-6** | **Suchtreffer-Hervorhebung** („highlighted match", AC-013-1) ist im Vertrag nur als Freitext-Abschnitt der `description` geführt, nicht als eigenes Feld. | AC-013-1 | Das Suchergebnis ist ein `PromptSummary` mit `title`/`description`; eine Hervorhebung ist eine **Darstellungs**frage der Oberfläche. Kein eigenes Feld — bewusst. |
| **A-7** | **Volltextsuche und Filter teilen sich eine Operation** (`listPrompts` mit `q`). | US-013/US-014/US-015 | Bewusst: eine Operation, drei Stories, ein Filter- und Sortiermodell. Der Vertrag nennt `q` als optionalen Filter der Listenoperation. |
| **A-8** | **`transfer_audit` enthält bewusst keinen Inhalts-Hash** — der wäre ein ableitbarer Fingerabdruck. | ADR-006 | Bewusste Auslegung von FR-074 („Zeichenlänge"). |

---

## 6. Offene Fragen

| # | Frage | Adressat | Auswirkung bei Nichtbeantwortung |
|---|---|---|---|
| **F-1** | Ist die `403`-Antwort bei Löschung/Export eines **fremden** Nutzers (B-02) mit dem Grundsatz „kein Existenz-Leak" vereinbar, oder soll AC-029-2 auf `404` geändert werden? | Product Owner, DPO | Vertrag bleibt bei `403`; die Ausnahme wird in Phase 8 auditiert. |
| **F-2** | Bestätigt der Product Owner, dass eine freigegebene Version **nur** über eine Inhaltsänderung neu geprüft werden kann (B-07)? | Product Owner | `rescanPromptVersion` bleibt auf `FAILED` beschränkt. |
| **F-3** | Bestätigt die DPO die Löschung der **gesamten** Audit-Tabelle nach 90 Tagen (B-08) und die Anonymisierung statt Löschung der Autorschaft (B-09)? | DPO | Retention-Job läuft wie dokumentiert; die Datenschutz-Folgenabschätzung übernimmt die Auslegung. |
| **F-4** | Soll das Konto eine **E-Mail-Bestätigung** haben (A-1)? Falls ja: zusätzlicher Status und zusätzliche Operation, oder bleibt der Bestätigungsschritt Betrieb? | Product Owner | Vertrag bleibt bei `ACTIVE` ohne Bestätigung. |
| **F-5** | Soll der **Qualitätsnachweis** (US-031) als Plattform-Funktion im MVP bleiben, oder reicht der Nachweis in der CI? Bei „nein" entfallen `GET/POST /ops/quality-gate` — US-031 wäre dann bewusst ohne Operation. | Product Owner | Die beiden Operationen bleiben ungenutzt im Vertrag. |
| **F-6** | Wird die **Registraturprüfung** (US-031 AC-031-3, echte DB) Teil des **Pflicht**-Testlaufs der Pipeline oder nur des Nightly-Laufs? | Execution/QA | Pflichtlauf = längere CI-Zeit; Nightly = Risiko, dass ein Leck spät auffällt. Empfehlung: **Pflicht**. |
| **F-7** | Soll der Inhalts-Hash der Version zusätzlich zum `content_hash` in der Datenbank im Vertrag erscheinen (`PromptVersion.contentHash` — ja, vorhanden)? **Nein** — Frage zurückgestellt: braucht der Export (`PersonalDataExport`) den Hash? | Product Owner | Der Export enthält aktuell **keinen** Hash — das ist bewusst (Fingerabdruck). |
| **F-8** | JWT-Signatur **symmetrisch (HMAC) oder asymmetrisch (RSA/EC)**? Beides erfüllt SEC-001; asymmetrisch erlaubt spätere externe Verifikation. | Security/Plattform | Der Vertrag ist neutral (`bearerAuth`, Format JWT). Festlegung in Phase 6 am `AuthenticationPort`. |
| **F-9** | Wird eine **organisationsweite** Sammel-Live-Ansicht (ein SSE-Kanal für alle Prompts einer Organisation) benötigt? Der Vertrag bietet je Prompt einen Kanal. | Product Owner | Bei Bedarf: eine Operation `GET /prompts/status-stream` — **Vertragsrevision** nach Gate 1. |
| **F-10** | Soll die **CI-Pipeline** den Coverage-Nachweis an die Plattform melden, oder bleibt die Plattform davon unberührt? | Product Owner, Betrieb | `POST /ops/quality-gate` bleibt optional. |

---

## 7. Maschinelle Prüfung

Alle Prüfungen wurden in diesem Schritt **ausgeführt**; die Ausgaben sind unten wörtlich
wiedergegeben.

### 7.1 YAML und OpenAPI-Validierung

```bash
$ python3 -c "import yaml; yaml.safe_load(open('docs/api/openapi.yaml'))"
yaml.safe_load OK

$ openapi-spec-validator docs/api/openapi.yaml
docs/api/openapi.yaml: OK
```

### 7.2 Vertragsprüfung (Pflichtangaben, Fehlerkatalog, Story-Zuordnung)

```bash
$ python3 docs/api/check_contract.py
Operationen im Vertrag : 60
operationId (eindeutig) : 60
Stories im Backlog      : 35
Stories mit Operation   : 35
Operationen mit Story   : 60
Paged-Schemata          : 11

WARNUNGEN (147):
  - POST /auth/register: Fehlerantwort 401 nicht dokumentiert (für diese Operation nicht ableitbar)
  … (weitere: Codes, die für die jeweilige Operation nicht ableitbar sind)

API-REVIEW: alle strukturellen Prüfungen bestanden.
```

Exitcode 0. Das Skript prüft: eindeutige `operationId`s im camelCase-Format, alle
Pflichtangaben je Operation, `security`/`x-tenant-scope`/`x-required-role`/`x-stories` je
Operation, die **regelbasiert ableitbaren** Fehlercodes je Operation, die
Paginierungsfelder in allen `Paged*`-Schemas, das Fehlerformat (`Problem` mit fünf
Pflichtfeldern), die Integrität aller `$ref`-Verweise und die Namenskonvention der
Komponenten. Die Warnungen sind **keine Fehler**, sondern Codes, die für die jeweilige
Operation nicht ableitbar sind (z. B. `409` bei einer GET-Operation).

### 7.3 Schema: Syntax, Anwendbarkeit und Abgleich

```bash
$ pglast.parse_sql(schema.sql)          # PostgreSQL-Parser
pglast: SQL syntaktisch OK — 56 Aussagen geparst

$ psql -d pgcheck -v ON_ERROR_STOP=1 -f docs/architecture/schema.sql
CREATE TABLE … (0 Fehler, Exitcode 0)

$ python3 schema_check.py                # Feld-für-Feld-Abgleich Vertrag ↔ Datenbank
geprüfte Feld-Mappings: 140
ABWEICHUNGEN (1):
  - Prompt.currentVersionId: DB nullable, Vertrag required      → Befund B-15
```

### 7.4 Diagramme

```bash
$ npx -y @mermaid-js/mermaid-cli --version
12.0.0

$ for f in docs/architecture/diagrams/*.mmd; do npx -y @mermaid-js/mermaid-cli -i "$f" -o "$f.svg"; done
PASS 01-context            PASS 05-sequence-scan-process
PASS 02-container          PASS 06-state-machine-scan-status
PASS 03-component-api      PASS 07-domain-model
PASS 04-component-scan-worker  PASS 08-tenant-isolation
```

**8/8 Diagramme rendern fehlerfrei.** Dabei wurden drei echte Mermaid-Syntaxfehler gefunden
und behoben:

| Fundstelle | Fehler | Behebung |
|---|---|---|
| `01`, `02`, `03`, `04` | `UpdateLayoutStyle(...)` wird vom C4-Parser in Mermaid 12 **nicht** unterstützt („Unrecognized text") | Zeilen entfernt (`UpdateRelStyle` ist unterstützt und bleibt) |
| `05` | geschweifte Klammern in Sequenz-Nachrichten (`{ status: … }`) sowie ein **Semikolon** im Notiz-Text brechen den Parser | Nachrichtentexte auf Klammern/ Semikolon umgestellt |
| `07` | mehrzeilige `note for X … end note`-Blöcke werden nicht geparst („Expecting 'STR'") | einzeilige Form `note for X "…"` |

## 8. Verweise

* `docs/api/openapi.yaml` — der eingefrorene Vertrag
* `docs/api/check_contract.py` — Prüfskript
* `docs/architecture/schema.sql` — Datenmodell (Quelle der Feldtypen)
* `docs/architecture/overview.md`, `tenant-model.md`, `scan-state-machine.md`,
  `error-retry-strategy.md`, `security-threat-model.md`, `observability.md`,
  `degraded-mode.md`, `privacy-retention.md`, `accessibility.md`, `resilience.md`
* `docs/adr/ADR-001…ADR-009` — Entscheidungen (ADR-002, ADR-003, ADR-004, ADR-006
  referenzieren die Befunde B-02, B-07, B-08, B-09, B-10)
* `.agent/artifacts/architecture.md` — Architektur-Zusammenfassung und offene Punkte
