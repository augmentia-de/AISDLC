# ADR-009 — Fehlermodell: einheitliches Problem-Details-Format mit Korrelations-ID

| Feld | Wert |
|---|---|
| **ID** | ADR-009 |
| **Titel** | Fehlermodell: einheitliches Problem-Details-Format mit Korrelations-ID |
| **Status** | **Angenommen** |
| **Datum** | 2026-10-20 |
| **Entscheider** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Betrifft** | FR-003, FR-023, FR-062, SEC-005, SEC-009, NFR-014, AC-032-3, R-01 |
| **Supersediert** | — |

## Kontext

Die 60 Operationen des Vertrags `docs/api/openapi.yaml` müssen **ein** Fehlerformat
produzieren. Das ist keine Formalie:

* **AC-032-3** — bei einem unerwarteten internen Fehler enthält die Antwort eine generische
  Meldung und eine **Korrelation-ID**, aber **keinen** Stacktrace und kein internes Detail.
* **FR-062 / SEC-009** — Antworten enthalten niemals Prompt-Inhalt, Secrets, Tokens oder
  personenbezogene Daten.
* **FR-003** — `401` und `403` enthalten **keine** Datenexistenzinformation.
* **SEC-005** — Eingabevalidierung an der Grenze; ein unvalidiertes Wert darf die
  Fachlogik nicht erreichen (AC-034-3).
* **FR-023 / R-01** — Suchergebnisse geben niemals Daten einer anderen Organisation preis;
  das gilt auch für **Fehlerantworten**.

Gleichzeitig muss der Vertrag **maschinell prüfbar** sein: Der openapi-first-Skill verlangt
eine feste Bedeutung der Statuscodes und ein einheitliches Format — sonst ist die Antwort
eines Clients nicht programmatisch auswertbar.

## Entscheidung

### 1. Format: RFC 9457 (Problem Details), erweitert um zwei Felder

```json
{
  "type": "https://api.promptguard.example/errors/validation-failed",
  "title": "Validation failed",
  "status": 422,
  "detail": "The field 'title' must not be empty. Allowed length: 1-200 characters.",
  "instance": "/api/v1/prompts",
  "correlationId": "01J8Z6M2QK9Y3F7V0C4T5N6P7R",
  "errors": [ { "field": "title", "code": "must_not_be_empty", "message": "..." } ]
}
```

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `type` | ja | URI-Referenz auf die Fehlerklasse — stabil, versionierbar |
| `title` | ja | Kurze, **englische** Fehlerklasse, maschinenlesbar |
| `status` | ja | HTTP-Statuscode, identisch mit der Antwort |
| `detail` | ja | Was zu tun ist — **niemals** nur, was schiefging |
| `instance` | ja | Pfad der auslösenden Anfrage |
| `correlationId` | ja (Erweiterung) | Rückverfolgung zu den strukturierten Logs |
| `errors` | nur bei `400`/`422` (Erweiterung) | Feldbezogene Fehler mit stabilem Code |

* **Warum RFC 9457 statt eines eigenen Formats:** Das Format ist ein Standard — Clients
  können generisch darauf aufbauen, ohne PromptGuard-spezifisches Wissen zu brauchen.
  RFC 9457 ist die Nachfolgerin von RFC 7807 und trägt dieselben Feldnamen.
* **Warum die zwei Erweiterungen:** `correlationId` ist für **US-032 / AC-032-3**
  zwingend (der Client muss einen Support-Fall mit einem Log befüllen können, ohne
  Inhalt zu sehen). `errors` ist für **AC-007-2 / AC-011-2 / AC-034-3** zwingend
  („nennt das betroffene Feld", „names the limit").
* **Sprache:** Fehlertexte sind **englisch** (NFR-018: Oberfläche deutsch, API englisch).

### 2. Statuscodes: feste Bedeutung, kein Code-Salat

| Code | Bedeutung in PromptGuard | Auslöser |
|---|---|---|
| `400` | syntaktisch fehlerhafte Anfrage | `size > 200`, unzulässiger Parameterwert |
| `401` | nicht angemeldet | Token fehlt, abgelaufen, ungültig, Session-Kette invalidiert |
| `403` | angemeldet, aber nicht berechtigt | Rolle fehlt für die Operation |
| `404` | nicht existent **oder** nicht sichtbar | fremde Organisation, gelöschtes Objekt |
| `409` | Konflikt mit dem aktuellen Zustand | E-Mail belegt, Kategorie referenziert, Status nicht `FLAGGED` |
| `422` | semantisch ungültig | Pflichtfeld fehlt, Länge/Limit überschritten, unbekannter Aufzählungswert |
| `429` | Rate-Limit überschritten | 10 Logins/Min/IP, 1 000 API-Aufrufe/Min/Nutzer |
| `500` | unerwarteter Fehler | **generisch**, mit `correlationId`, ohne internes Detail |

* **`403` vs. `404`:** `403` entsteht **nur** bei fehlender Rolle, `404` immer dann, wenn
  die Ressource nicht existiert **oder** einer fremden Organisation gehört (ADR-002). Damit
  wird `403`/`404` **nicht** zum Existenzorakel.
* **Dokumentierte Ausnahme:** `DELETE /api/v1/users/{userId}` und
  `GET /api/v1/users/{userId}/export` liefern bei einem Nutzer einer fremden Organisation
  `403`, weil AC-029-2 das so fordert (ADR-002, Befund B-02).
* **Welche Codes eine Operation dokumentiert,** ist **regelbasiert** und wird
  maschinell geprüft (`docs/api/check_contract.py`); die Regel steht im Vertrag unter
  „Fehlerkatalog je Operation".

### 3. Abbildung im Code

| Schicht | Aufgabe |
|---|---|
| **Domäne** | Wirft typisierte Ausnahmen (`ValidationException`, `ConflictException`, `NotFoundException`, `ForbiddenException`, `RateLimitExceededException`, `ProviderPolicyViolationException`) — **ohne** HTTP-Semantik. |
| **Service** | Übersetzt Domänenfehler in Fehlerklassen; trägt den `correlationId` aus dem Request-Kontext. |
| **Boundary (`@RestControllerAdvice`)** | Einziger Ort, der Statuscode und Problem-Body erzeugt. Kein Controller baut Fehlerantworten selbst. |

* **Warum eine Exception-Mapping-Schicht:** Fehlerantworten an fünf Stellen zu
  konstruieren führt zu Drift mit fünf Formaten. Eine einzige Abbildung erzwingt das Format
  strukturell — die Prüfung „alle Fehlerantworten haben dieselbe Form" ist damit eine
  Codefrage, nicht eine Disziplinfrage.
* **`errorCode` statt Exception-Klassen im Vertrag:** Der stabile englische Code
  (`must_not_be_empty`, `resource_not_found`, `provider_policy_violation`) ist Teil des
  Vertrags und darf sich nicht mit dem Klassennamen der Exception ändern.

### 4. Inhaltsfreiheit — die harte Regel

* **Kein Prompt-Inhalt, kein Secret, keine PII, kein Stacktrace, kein SQL-Fragment, kein
  Mandantenname eines fremden Mandanten** in `detail`, `title`, `errors` oder `type`.
* **Keine Existenzinformation** in `401`/`403`/`404` (FR-003, FR-023).
* Der einzige zulässige Bezug auf ein Objekt ist dessen **eigene** ID im eigenen Mandanten.
* **Technische Fehlertexte** (Timeout, Provider nicht erreichbar) werden **intern**
  protokolliert und in `detail` nur als **Klasse** genannt (`provider_unavailable`), nicht
  mit Rohdetails.

### 5. Korrelations-ID

* Wird **am Anfang** der Request-Verarbeitung erzeugt (oder aus dem eingehenden
  `X-Correlation-Id` übernommen, wenn er dem erlaubten Format entspricht) und **über die
  gesamte Verarbeitung** durchgereicht — Request, Worker-Job, Provider-Aufruf, Audit,
  Log, Fehlerantwort, SSE-Ereignis.
* Sie verbindet damit genau die Stellen, die FR-074 (Metadaten-Audit der Übermittlung)
  und NFR-014 (strukturierte Logs) verlangen — ohne Inhalt zu übertragen.
* Sie ist **kein** Geheimnis und **kein** personenbezogener Identifier: sie enthält keine
  E-Mail, keine Organisation, keinen Prompt-Inhalt.

## Konsequenzen

### Positiv

* **Ein Format für alle 60 Operationen** — Clients können Fehler generisch behandeln.
* **Maschinell prüfbar:** `docs/api/check_contract.py` prüft Format, Pflichtfelder und
  Code-Abdeckung; die Prüfung ist Teil des CI-Gates.
* **Fehlersuche ohne Inhalt:** Die `correlationId` erlaubt die Verknüpfung von
  Nutzerfehler und Logzeile, ohne dass Inhalt im Log oder in der Antwort liegt.
* **Feldfehler maschinell nutzbar:** `errors[].field` + `errors[].code` erlauben eine
  Formularvalidierung ohne String-Matching auf `detail`.

### Negativ / Kosten

* **Ein Mapping-Layer** ist zusätzlicher Code — dafür entfällt die Fehlerformatierung an
  jeder einzelnen Stelle.
* **`detail` muss bewusst formuliert werden:** „Was zu tun ist" ist anspruchsvoller als
  „was schiefging" und wird beim Review gerne zu technisch. Das ist eine Review-Aufgabe.
* **Fehlercodes sind Vertragsinhalt:** Eine Umbenennung eines `errorCode` ist eine
  Vertragsänderung und braucht eine Revision.

### Neutral

* `title` bleibt englisch, die Oberfläche übersetzt über die Fehlerklasse (NFR-018).

## Alternativen, die verworfen wurden

| Alternative | Warum verworfen |
|---|---|
| Einfache Fehlerobjekte je Controller | Format driftet; die Prüfung „ein Format für alle" wird unprüfbar. |
| Spring-Standard-Fehlerausgabe (`ProblemDetail`) allein | Deckt `correlationId` und Feldfehlerliste nicht ab — beides ist für AC-032-3 und AC-007-2 nötig. |
| RFC 9457 ohne Erweiterungen | `correlationId` und Feldfehlerliste fehlen; die genannten AKs sind nicht erfüllbar. |
| Fehlerdetails mit Stacktrace für Debugging | Verstoß gegen SEC-009/FR-062; Stacktraces gehören ins Log, nicht in die Antwort. |
| Mandantenname oder Nutzer-E-Mail in `detail` | Existenz- und PII-Leak (FR-023, R-01). |

## Verweise

* `docs/requirements/PRD.md` §8 (FR-003, FR-023, FR-062), §10 (SEC-005, SEC-009), §9 (NFR-014, NFR-018)
* `docs/api/openapi.yaml` — `components.schemas.Problem`, `components.schemas.FieldError`,
  `components.responses.*`, Abschnitt „Fehlerkatalog je Operation"
* `docs/api/check_contract.py` — maschinelle Prüfung des Fehlermodells
* `docs/adr/ADR-002-multi-tenancy-and-authorisation.md` — `403`/`404`-Regel
* `docs/architecture/diagrams/03-component-api.mmd` — Komponente „Fehlerabbildung"
