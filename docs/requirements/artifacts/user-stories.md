# PromptGuard — User Stories und Akzeptanzkriterien

Alle 35 Stories in 8 Epics. Maschinenlesbare Fassung: `docs/requirements/stories/US-*.json`.
BDD: `docs/requirements/features/US-*.feature`

## E1 — Identität & Zugriff

### US-001 — Anmeldung mit E-Mail und Passwort

As a developer, I want to log in with my e-mail and password, so that I can manage the prompts of my organisation.

- **Anforderungen:** `FR-001`, `FR-003`, `FR-008`, `SEC-001`, `SEC-008`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-001-login.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-001-1` | a registered and confirmed developer account exists | the developer submits valid credentials | the API responds with 200 and a signed JWT access token · the token contains the claims sub, organisation_id, role and exp · the password is never part of the response or of any log line | integration |
| `AC-001-2` | a registered developer account exists | the developer submits a wrong password | the API responds with 401 and a generic error message · the response and the timing are indistinguishable from the case of an unknown e-mail address | integration |
| `AC-001-3` | an account has already failed to authenticate 9 times within a minute | the developer submits a further invalid login attempt | the API responds with 429 and the account is temporarily locked · the lock is released automatically after the configured lockout period | integration |

### US-002 — Registrierung erzeugt eine eigene Organisation

As a new user, I want to register and create my own organisation, so that my team and I can start managing prompts.

- **Anforderungen:** `FR-002`
- **Randbedingungen:** `A-07`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-002-registration.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-002-1` | no account exists for the given e-mail address | the user registers with e-mail and password | the API responds with 201, creates a new organisation and assigns the role ORG_ADMIN to the user · the access token contains the id of the newly created organisation | integration |
| `AC-002-2` | an account already exists for the given e-mail address | the user tries to register again | the API responds with 409 and no organisation is created | integration |
| `AC-002-3` | an existing organisation with developers already exists | a new user registers and tries to select that organisation | the API ignores the attempted organisation selection · the new user is a member of a separate new organisation only | integration |

### US-003 — Token-Lebenszyklus: Refresh, Rotation und Logout

As a developer, I want my session to refresh automatically and to end it explicitly, so that I stay signed in without leaving an open session behind.

- **Anforderungen:** `FR-007`, `SEC-001`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-003-token-lifecycle.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-003-1` | a client holds a valid access token and a valid refresh token | the client calls the refresh endpoint with the refresh token | the API responds with 200 and a new token pair · the previously used refresh token is no longer valid | integration |
| `AC-003-2` | a refresh token has already been rotated once | an attacker replays the old refresh token | the API responds with 401 and invalidates every token of that session chain | integration |
| `AC-003-3` | the access token has expired but the refresh token is still valid | the client calls a protected resource with the expired access token | the API responds with 401 and instructs the client to use the refresh endpoint · no protected data is returned | integration |

### US-004 — Rollen und Berechtigungen

As an organisation admin, I want three distinct roles, so that only the right people can administer members, categories and flagged prompts.

- **Anforderungen:** `FR-003`, `FR-004`, `SEC-004`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-004-roles.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-004-1` | the acting user has the role ORG_ADMIN | the user creates a new category | the API responds with 201 and the category belongs to the acting organisation | integration |
| `AC-004-2` | the acting user has the role DEVELOPER | the user attempts to create a category | the API responds with 403 and no category is created | integration |
| `AC-004-3` | a prompt version is flagged with severity HIGH | a developer attempts to release it while the security admin succeeds | the developer receives 403 and the security admin receives 200 with status WAIVED · both attempts are recorded in the audit trail | integration |

### US-005 — Striktes Mandantenscoping auf jeder Route

As a security admin, I want a hard separation between organisations, so that no developer can ever see the prompts of another department.

- **Anforderungen:** `FR-009`, `FR-023`, `SEC-002`, `SEC-003`
- **Randbedingungen:** `I-2`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-005-tenant-isolation.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-005-1` | the acting user is a developer of organisation A which owns three prompts | the user lists the prompts | the API responds with exactly the three prompts of organisation A · no field of any other organisation is present in the response | integration |
| `AC-005-2` | a prompt with a known id exists in organisation B | a developer of organisation A requests that prompt by id | the API responds with 404 and an empty body · neither the title nor the owner nor the scan status leaks | integration |
| `AC-005-3` | a distinctive term occurs only in prompts of organisation B | a developer of organisation A searches for that term | the API responds with an empty result list and total count 0 · the response contains no suggestion, no facet and no hint that a foreign match exists | integration |

### US-006 — Mitglieder einladen und Alarmempfänger pflegen

As an organisation admin, I want to invite colleagues and configure who receives security alerts, so that the right people are notified.

- **Anforderungen:** `FR-005`, `FR-006`, `FR-053`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-006-membership-and-alert-recipients.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-006-1` | the acting user has the role ORG_ADMIN | the admin invites a colleague with the role DEVELOPER | the API responds with 202 and creates a single-use, time-limited invitation · the invitation is bound to the acting organisation and to the role DEVELOPER | integration |
| `AC-006-2` | the acting user has the role DEVELOPER | the user attempts to send an invitation | the API responds with 403 and no invitation is created | integration |
| `AC-006-3` | an organisation has no security admin recipient configured | a prompt version of that organisation becomes flagged | the alert is delivered to the organisation admins · the delivery attempt is recorded with the actual recipients | integration |

## E2 — Prompt-Verwaltung

### US-007 — Prompt anlegen

As a developer, I want to create a prompt with title, content, category and tags, so that it becomes part of the central registry.

- **Anforderungen:** `FR-010`, `FR-011`, `FR-030`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-007-prompt-create.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-007-1` | an authenticated developer belongs to an organisation with a default category list | the developer submits a valid prompt with title, content, category and two tags | the API responds with 201 within the latency budget and creates version 1 with status PENDING · the response contains no scan result yet | integration |
| `AC-007-2` | an authenticated developer is present | the developer submits a prompt without a title | the API responds with 422 and names the offending field · no prompt and no version is created | integration |
| `AC-007-3` | an authenticated developer is present | the developer submits a prompt whose content exceeds the configured maximum length | the API responds with 422 and states the limit · neither a prompt nor a partial version is persisted | integration |

### US-008 — Bearbeiten erzeugt eine neue Version und setzt die Freigabe zurück

As a security admin, I want every content change to create a new version that must be scanned again, so that an approved prompt can never carry unapproved content.

- **Anforderungen:** `FR-012`
- **Randbedingungen:** `I-1`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-008-prompt-edit-new-version.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-008-1` | version 1 of a prompt is APPROVED | the author changes the content and saves | the API responds with 200 and creates version 2 with status PENDING · version 1 remains APPROVED and unchanged | integration |
| `AC-008-2` | a prompt exists in organisation B | a developer of organisation A submits an edit for that prompt | the API responds with 404 and no version is created | integration |
| `AC-008-3` | version 1 of a prompt is APPROVED | the author saves without changing title, content, category or tags | the API responds with 200 and returns version 1 unchanged · the number of versions stays at one and no scan is triggered | integration |

### US-009 — Versionshistorie und Versionsvergleich

As a developer, I want to see how a prompt evolved, so that I understand what changed and which version is in use.

- **Anforderungen:** `FR-013`, `NFR-005`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-009-version-history.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-009-1` | a prompt has three versions created by two different authors | the author opens the version history | the API responds with 200 and lists version number, author, timestamp, status and finding count per version | integration |
| `AC-009-2` | a version belongs to a prompt of organisation B | a developer of organisation A requests that version | the API responds with 404 and no version data is returned | integration |
| `AC-009-3` | a prompt has two versions with different content | the author compares version 1 with version 2 | the API responds with 200 and returns a diff of the two contents · neither version is modified by the comparison | integration |

### US-010 — Kategorien verwalten

As an organisation admin, I want to maintain the category list, so that prompts are classified consistently.

- **Anforderungen:** `FR-014`
- **Randbedingungen:** `A-06`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-010-categories.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-010-1` | an organisation has just been created | the developer lists the categories | the API responds with 200 and the seed categories Customer Support, Code Gen, Data Analysis, Documentation and Other | integration |
| `AC-010-2` | the acting user has the role ORG_ADMIN | the admin creates the category Support Bot and later renames it | the API responds with 201 and later 200 and the category keeps its id | integration |
| `AC-010-3` | a category is referenced by at least one prompt | the admin attempts to delete it | the API responds with 409 and the category remains usable but inactive · existing prompts keep their reference | integration |

### US-011 — Tags vergeben

As a developer, I want to label prompts with free tags, so that I can find them later.

- **Anforderungen:** `FR-015`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-011-tags.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-011-1` | an authenticated developer is present | the developer assigns the tags Support, deutsch and support | the API responds with 200 and stores the two distinct normalised tags Support and deutsch | integration |
| `AC-011-2` | an authenticated developer is present | the developer assigns eleven tags to one prompt | the API responds with 422 and names the limit · the prompt keeps its previous tag set | integration |
| `AC-011-3` | the organisation already uses the maximum number of distinct tags | the developer assigns a completely new tag | the API responds with 422 and the tag limit is reported · an already existing tag can still be assigned | integration |

### US-012 — Prompt löschen

As an organisation admin, I want to delete obsolete prompts including their history, so that the registry only contains relevant material.

- **Anforderungen:** `FR-016`, `DP-004`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-012-prompt-delete.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-012-1` | a prompt has two versions with scans and findings | the author deletes the prompt | the API responds with 204 and prompt, versions, scans and findings no longer exist · an audit entry without prompt content remains | integration |
| `AC-012-2` | a prompt exists in organisation B | a developer of organisation A attempts to delete it | the API responds with 404 and the prompt is untouched | integration |
| `AC-012-3` | a prompt was created by developer X and is being deleted by the organisation admin | the admin deletes the prompt | the API responds with 204 and the audit entry names the deleting actor, not only the author · the audit entry contains no prompt content | integration |

## E3 — Suche & Filterung

### US-013 — Volltextsuche

As a developer, I want to search prompts by title and content, so that I find an existing prompt instead of writing a new one.

- **Anforderungen:** `FR-020`, `NFR-004`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-013-fulltext-search.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-013-1` | the own organisation owns three prompts, one of them containing the term Refundierungsrichtlinie | the developer searches for Refundierungsrichtlinie | the API responds with 200 and returns exactly that prompt with a highlighted match | integration |
| `AC-013-2` | no prompt of the own organisation contains the search term | the developer searches for a term that only exists in another organisation | the API responds with 200 and an empty result list with total count 0 | integration |
| `AC-013-3` | an authenticated developer is present | the developer searches for a term containing SQL metacharacters and a prompt injection phrase | the API responds with 200 or 422 and never returns database errors or prompt contents · no injected statement is executed | integration |

### US-014 — Filtern nach Kategorie, Tag und Prüfstatus

As a developer, I want to combine filters, so that I can narrow down a large registry.

- **Anforderungen:** `FR-021`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-014-filtering.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-014-1` | the own organisation owns prompts in several categories, tags and scan statuses | the developer filters by category Code Gen, tag python and status FLAGGED | the API responds with 200 and only prompts matching all three criteria | integration |
| `AC-014-2` | a category belongs to another organisation | the developer filters by that category id | the API responds with 200 and an empty list without any hint that the category exists | integration |
| `AC-014-3` | the filtered result set spans several pages | the developer requests the second page with the same filters | the API responds with 200 and returns the next page of the same filtered set · the total count matches the filtered set, not the unfiltered one | integration |

### US-015 — Sortierung und Pagination

As a developer, I want to sort and page through the registry, so that large lists stay usable.

- **Anforderungen:** `FR-022`, `NFR-008`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-015-sorting-pagination.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-015-1` | the own organisation owns prompts with different update timestamps | the developer sorts by updated_at descending | the API responds with 200 and the prompts are ordered from newest to oldest | integration |
| `AC-015-2` | the filtered result set contains three pages | the developer requests page 99 | the API responds with 200 and an empty page with the correct total count | integration |
| `AC-015-3` | an authenticated developer is present | the developer requests a page size of 5000 | the API responds with 400 and states the maximum page size · the response contains at most the maximum number of items | integration |

## E4 — Asynchrone KI-Prüfung

### US-016 — Asynchroner Prüflauf

As a developer, I want my prompt to be saved immediately while the check runs in the background, so that the interface never blocks.

- **Anforderungen:** `FR-030`, `FR-031`, `NFR-001`, `NFR-002`, `NFR-007`, `CON-009`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-016-async-scan.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-016-1` | an authenticated developer creates a new prompt | the developer submits the prompt and the scan worker picks the job up | the API responds with 201 within the latency budget and the version later reaches status APPROVED · the status change is recorded in the history | e2e |
| `AC-016-2` | the AI service is unreachable | the developer submits a new prompt | the API responds with 201 and the prompt is stored · the version stays PENDING or moves to FAILED and the developer sees a retry option | integration |
| `AC-016-3` | several scan jobs are queued and the worker is restarted | the worker comes back up | all queued jobs are processed in their original order · no job is lost and no version stays in SCANNING forever | unit |

### US-017 — Duplikatwarnung innerhalb der eigenen Organisation

As a developer, I want to be warned when a similar prompt already exists in my organisation, so that I reuse instead of duplicating work.

- **Anforderungen:** `FR-032`
- **Randbedingungen:** `A-08`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-017-duplicate-detection.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-017-1` | the own organisation already owns a prompt with almost identical content | the developer creates a prompt with very similar content | the scan result contains a duplicate warning listing up to five similar prompts with title and similarity score · the creation itself is not blocked | integration |
| `AC-017-2` | an identical prompt exists in another organisation only | the developer creates the prompt in their own organisation | the scan result contains no duplicate warning and no hint about the foreign prompt | integration |
| `AC-017-3` | a prompt of the own organisation is only loosely related | the developer creates the prompt | the scan result contains no duplicate warning · the threshold is configurable per organisation | integration |

### US-018 — Erkennung von Prompt Injection und Jailbreak

As a security admin, I want attempts to manipulate the model or the checker to be detected, so that malicious prompts do not enter the registry.

- **Anforderungen:** `FR-033`, `SEC-006`, `SEC-007`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-018-injection-jailbreak-detection.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-018-1` | a developer creates a prompt whose content tries to override previous instructions | the scan runs | the version reaches status FLAGGED with a finding of category PROMPT_INJECTION · the finding contains a redacted excerpt, the position and the reasoning | integration |
| `AC-018-2` | a developer creates an ordinary customer support prompt | the scan runs | the version reaches status APPROVED and no finding is reported | integration |
| `AC-018-3` | a prompt contains text that instructs the classifier to report APPROVED | the scan runs | the version is classified by the content itself and the injected instruction has no effect · the checker prompt keeps its system instruction and the finding is recorded | integration |

### US-019 — Erkennung von Secrets

As a security admin, I want credentials that slipped into a prompt to be found, so that no API key ends up in the registry.

- **Anforderungen:** `FR-034`, `FR-038`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-019-secret-detection.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-019-1` | a developer creates a prompt that contains a live-looking API key | the scan runs | the version reaches status FLAGGED with a finding of category SECRET · the excerpt shows only a masked fragment of the key | integration |
| `AC-019-2` | a developer creates a prompt that contains the placeholder YOUR_API_KEY | the scan runs | no SECRET finding is raised and the version is not flagged for that reason | unit |
| `AC-019-3` | a prompt contains a secret and the scan runs | the provider is called for the classification | the secret is reported as a finding and the version is FLAGGED · the content is transmitted unchanged to the AI service - this exposure is accepted per decision F1 and is recorded in the audit trail as metadata without content | integration |

### US-020 — Erkennung personenbezogener Daten

As a data protection officer, I want personal data inside prompts to be detected, so that no customer data stays in the registry unnoticed.

- **Anforderungen:** `FR-035`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-020-pii-detection.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-020-1` | a developer creates a prompt that contains a customer e-mail address and a customer name | the scan runs | the version reaches status FLAGGED with findings of category PII · the excerpts are masked and the positions are reported | integration |
| `AC-020-2` | a developer creates a prompt that contains the address support@example.com | the scan runs | a finding of category PII with severity LOW is reported · the version is not flagged because the configured threshold is MEDIUM | unit |
| `AC-020-3` | a developer creates a prompt that mentions a customer with a full name in running text | the scan runs | a PII finding is reported with the semantic reasoning of the classifier | integration |

### US-021 — Scan-Ergebnis mit Fundstellen

As a developer, I want to understand why my prompt was flagged, so that I can fix it instead of guessing.

- **Anforderungen:** `FR-036`, `FR-037`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-021-scan-result-detail.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-021-1` | a scan produced three findings | the author opens the scan result | the API responds with 200 and lists category, severity, redacted excerpt, position and reasoning per finding · the raw prompt content is not reproduced beyond the redacted excerpts | integration |
| `AC-021-2` | a scan result belongs to a prompt of organisation B | a developer of organisation A requests it | the API responds with 404 and no finding data is returned | integration |
| `AC-021-3` | a scan completed without any finding | the author opens the scan result | the API responds with 200 and shows that no finding was raised and which thresholds were applied | integration |

### US-022 — Übermittlung an den KI-Dienst unter festen Auflagen

As a data protection officer, I want every transfer to the AI service to run only against a verified provider configuration, so that the data transfer is provably compliant.

- **Anforderungen:** `FR-038`, `FR-074`, `DP-002`, `DP-005`, `SEC-006`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-022-external-provider-transfer.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-022-1` | the provider configuration has been verified and a prompt is ready to be classified | the scanner calls the AI service | the provider receives the original prompt text without redaction · the transmission is recorded in the audit trail as metadata without content | unit |
| `AC-022-2` | the configured provider does not guarantee retention free processing in the EU | the scanner would call the AI service | the call is not executed and the version moves to FAILED · no prompt content leaves the system | unit |
| `AC-022-3` | the provider is reachable but the call fails with a timeout | the scan runs | the version moves to FAILED and can be retried manually · no partial scan result is stored | integration |

### US-023 — Manuelle Ausnahme-Freigabe eines geflaggten Prompts

As a security admin, I want to release a flagged prompt with a documented reason, so that false positives do not block my teams.

- **Anforderungen:** `FR-039`, `SEC-010`
- **Randbedingungen:** `I-3`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-023-waiver.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-023-1` | a version is FLAGGED with a finding of severity HIGH | the security admin releases it with a reason | the API responds with 200 and the version reaches status WAIVED · the audit trail records actor, timestamp and reason | integration |
| `AC-023-2` | a version is FLAGGED | a developer attempts to release it | the API responds with 403 and the status stays FLAGGED | integration |
| `AC-023-3` | a version is FLAGGED | the security admin submits a release without a reason | the API responds with 422 and the status stays FLAGGED · no audit entry is written | integration |

## E5 — Status & Live-Aktualisierung

### US-024 — Live-Aktualisierung des Prüfstatus in der Oberfläche

As a developer, I want the interface to show the status change without a reload, so that I know immediately whether my prompt is approved.

- **Anforderungen:** `FR-040`, `NFR-003`, `FR-042`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-024-live-status.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-024-1` | a prompt version is in status SCANNING and the user has the list open | the scan finishes and the status changes to APPROVED | the interface shows APPROVED within two seconds without a manual reload | e2e |
| `AC-024-2` | no valid token is presented | a client opens the live status channel | the server refuses the connection and no status data is streamed | e2e |
| `AC-024-3` | the live channel drops while a scan is running | the client reconnects | the client receives the current status of the prompt and continues to receive updates · no intermediate update is delivered twice with a different state | e2e |

### US-025 — Status- und Änderungshistorie

As a security admin, I want an audit trail of status changes, so that I can reconstruct what happened with a prompt.

- **Anforderungen:** `FR-041`, `SEC-010`, `FR-060`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-025-audit-trail.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-025-1` | a version went from PENDING to SCANNING to APPROVED | the author opens the history | the API responds with 200 and lists all three transitions with timestamp and cause | integration |
| `AC-025-2` | a history belongs to a prompt of organisation B | a developer of organisation A requests it | the API responds with 404 and no history entry is returned | integration |
| `AC-025-3` | audit entries older than 90 days exist | the retention job runs | those entries are deleted and no prompt content is left behind · the remaining entries are unaffected | integration |

## E6 — Alarmierung

### US-026 — Warn-E-Mail an den Security-Admin

As a security admin, I want to be alerted by e-mail when a prompt is flagged, so that I can react without watching the interface.

- **Anforderungen:** `FR-050`, `FR-051`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-026-security-alert-email.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-026-1` | an organisation has two security admin recipients configured | a version of that organisation becomes FLAGGED | one alert e-mail is delivered to each of the two recipients · the delivery attempt is recorded with timestamp and recipients | manual |
| `AC-026-2` | a version containing a secret was flagged | the alert e-mail is inspected | the body contains only organisation, prompt id and title, version, finding categories and severities, timestamp and a deep link · neither the prompt text nor the secret nor the finding excerpts appear in the mail body | integration |
| `AC-026-3` | an organisation has no security admin recipient configured | a version of that organisation becomes FLAGGED | the alert is delivered to the organisation admins and the fallback is recorded | integration |

### US-027 — Zustellungswiederholung und Totmannschaltung

As an operator, I want failed alert deliveries to be retried and finally surfaced, so that no warning is silently lost.

- **Anforderungen:** `FR-052`, `FR-070`, `NFR-007`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-027-notification-retry.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-027-1` | the mail server is temporarily unreachable and a version becomes FLAGGED | the notification worker retries the delivery | the delivery is retried with increasing backoff and the attempt count is recorded | integration |
| `AC-027-2` | the mail server stays unreachable and all configured attempts are used up | the notification worker gives up | the delivery is moved to the dead letter queue and is visible in the interface · the dead letter entry can be retried manually | integration |
| `AC-027-3` | the mail server is unreachable while a scan completes | the scan finishes | the version reaches APPROVED or FLAGGED as the scan determined · the delivery failure is recorded separately and does not block the scan | integration |

## E7 — Datenschutz & Aufbewahrung

### US-028 — Retention-Löschung nach 90 Tagen

As a data protection officer, I want old telemetry deleted automatically, so that the platform keeps only what it may keep.

- **Anforderungen:** `FR-060`, `FR-061`, `DP-003`, `NFR-013`
- **Randbedingungen:** `A-03`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-028-retention-job.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-028-1` | log entries, scan raw data and notification records older than 90 days exist and prompts of any age exist | the retention job runs | the telemetry is deleted and the prompts and versions remain · the job writes a summary of the deleted volume | integration |
| `AC-028-2` | the retention job has already run successfully | the job is triggered again | the second run deletes nothing and reports zero deleted records without an error | integration |
| `AC-028-3` | the database rejects the deletion statement | the retention job runs | the job ends with an error, is retried and raises an alert · no partial or corrupted state remains | integration |

### US-029 — Recht auf Löschung

As a user, I want my account and personal references to be deletable, so that my personal data is removed when I request it.

- **Anforderungen:** `FR-063`, `DP-004`, `DP-007`, `DP-008`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-029-right-to-erasure.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-029-1` | a user authored three prompts and holds an active session | the organisation admin triggers the erasure | the account, its tokens and its sessions are deleted and the authorship in the three prompts is anonymised · the prompts themselves remain and are queued for a new scan | integration |
| `AC-029-2` | a user belongs to organisation B | an admin of organisation A attempts the erasure | the API responds with 403 and nothing is deleted | integration |
| `AC-029-3` | the erased user performed security relevant actions | the erasure is executed | the audit entries remain with actor and timestamp but without the personal reference and without content | integration |

### US-030 — Datenschutzhinweis in Deutsch und Englisch

As a user, I want a clear privacy notice in my language, so that I know what happens with my data.

- **Anforderungen:** `FR-064`, `DP-001`, `DP-006`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-030-privacy-notice.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-030-1` | the application is running | a user opens the privacy notice | the API responds with the notice in German and in English including purpose, legal basis, retention periods and rights | manual |
| `AC-030-2` | the notice text has been changed in a new release | a user opens the notice | the API returns the current version and the version identifier is exposed · the previous version is archived for proof | manual |

## E8 — Qualität & Betrieb

### US-031 — Testabdeckung und Qualitäts-Gate

As a reviewer, I want the build to fail below 80 percent coverage, so that quality is enforced and not just intended.

- **Anforderungen:** `NFR-009`, `NFR-010`, `FR-073`, `CON-008`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-031-coverage-gate.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-031-1` | a change is pushed to a branch | the pipeline runs | the pipeline executes unit, integration and end to end tests and reports the line coverage of backend and frontend | integration |
| `AC-031-2` | the measured line coverage is 74 percent | the pipeline evaluates the coverage gate | the pipeline fails with the missing percentage and the change cannot be merged | integration |
| `AC-031-3` | a developer changes a repository query | the pipeline runs the integration tests | the cross tenant test runs against a real database and fails when a query loses its organisation filter | integration |

### US-032 — Observability: Health, Metriken und inhaltsfreie Logs

As an operator, I want health, metrics and content free logs, so that I can operate the platform without reading user data.

- **Anforderungen:** `FR-072`, `NFR-014`, `FR-062`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-032-observability.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-032-1` | the application is running | the operator queries the health endpoint and the metrics endpoint | both respond with 200 and report queue depth, scan duration, error rate, delivery rate and status distribution · every log line carries a correlation id | integration |
| `AC-032-2` | a prompt with a secret and a customer name is created and later flagged | all log lines of that request are inspected | no log line contains the prompt text, the secret, the customer name or the finding excerpts | integration |
| `AC-032-3` | an unexpected internal error occurs in a request handler | the client receives the error response | the response contains a generic error and a correlation id and no stack trace or internal detail | integration |

### US-033 — Degraded Mode und manuelle Wiederholung

As a developer, I want the platform to keep working while the AI service is down and to let me retry later, so that an outage does not stop my team.

- **Anforderungen:** `FR-070`, `FR-071`, `NFR-006`, `NFR-007`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-033-degraded-mode.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-033-1` | the AI service is down and the queue fills up | the developer creates and reads prompts | the API responds normally and the queued scans wait · no request fails with an AI related error and no data is lost | integration |
| `AC-033-2` | several scan jobs are queued while the AI service is down | the AI service becomes available again | the jobs are processed in their original order · each version ends in a final status | integration |
| `AC-033-3` | a version is in status FAILED | the author triggers a retry | a new scan job is created and the version moves to PENDING and later to a final status · the manual retry is recorded in the audit trail | integration |

### US-034 — Sicherheits-, Datenschutz- und Betriebsgrundlage

As a reviewer, I want the security and privacy baseline to be enforced automatically, so that no release can weaken it.

- **Anforderungen:** `FR-065`, `FR-074`, `NFR-011`, `NFR-012`, `NFR-017`, `SEC-005`, `SEC-009`, `SEC-011`, `SEC-012`, `DP-005`, `CON-005`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-034-security-baseline.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-034-1` | the application is built and deployed into the review environment | the security scan runs | the report lists no finding rated Critical or High · the scan covers authentication, authorisation, injection, XSS, secrets, dependencies and the container image | manual |
| `AC-034-2` | the repository and the built image are present | the secret scan runs over both | the scan finds no credential, token or private key · the scan is part of the pipeline and fails the build on a hit | integration |
| `AC-034-3` | the application is running | a client sends a request with an invalid field type, an unknown enumeration value and an oversized body | the API responds with 400 or 422 and a field level error · no unvalidated value reaches the domain logic | integration |
| `AC-034-4` | the application is reachable over the public endpoint | a client performs a request | the response carries HSTS, X-Content-Type-Options and X-Frame-Options DENY plus a restrictive content security policy · plain HTTP is redirected to HTTPS and the certificate is valid | integration |
| `AC-034-5` | the application starts with a configured environment | the configuration is inspected at runtime | every secret is resolved from an environment variable or a mounted secret · no secret value appears in the source tree, the image layers or the effective configuration dump | integration |
| `AC-034-6` | a refresh token has been issued and the platform runs in the target region | the token storage and the deployment configuration are inspected | the stored value is a hash that cannot be replayed and the deployment region is europe-west3 · the AI provider is configured without training and without durable storage | integration |

### US-035 — Barrierefreiheit, Typsicherheit und Bedienbarkeit

As a developer, I want an accessible and strictly typed interface, so that the platform can be used by everyone and stays maintainable.

- **Anforderungen:** `NFR-015`, `NFR-016`, `NFR-018`
- **Priorität:** must-have · **Feature:** `docs/requirements/features/US-035-accessibility-and-typing.feature`

| AC | Given | When | Then | Test-Level |
|---|---|---|---|---|
| `AC-035-1` | the application is running | a user navigates prompt list, detail view, editor and version history using the keyboard only | every interactive element is reachable in a logical order and operable without a mouse · focus is visible at all times | manual |
| `AC-035-2` | the application is running | a screen reader user opens the prompt list and a flagged prompt | the scan status is announced with its text label and not only by colour · form fields have associated labels and the status change is announced | manual |
| `AC-035-3` | a change with a type error is pushed | the pipeline runs | the pipeline fails with the type error and the change cannot be merged · no implicit any, no unhandled promise and no unresolved lint warning is tolerated | integration |
| `AC-035-4` | the application is running | a user reads the interface and an API client reads an error response | the interface texts are German and the API error codes and field names are English · the language of the interface can be switched and the choice is remembered | integration |
