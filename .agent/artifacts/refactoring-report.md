# Refactoring-Bericht — Schritt 7 (Refactoring & Clean-up)

> Agent: `aisdlc-execution` · Skill: `aisdlc-simplify` (zweiter Durchlauf) · Stand: 2026-10-22
> Vorgang: **verhaltenserhaltend**. Der Vertrag `docs/api/openapi.yaml` ist seit
> `8627bb6` eingefroren und wurde **nicht** angefasst; das Schema blieb
> unveraendert (beide Diffs leer).
>
> Umfang: **48 Dateien** in `src/` und `tests/` geaendert (46 bearbeitet, 2 neu),
> **+290 / −486 Zeilen**. Kein Verhaltensaenderung, kein API-Bruch.

---

## 1. Das Schulden-Inventar

Der Bestand wurde **systematisch** durchgegangen: alle 109 Java-Dateien in
`src/main` (dazu 59 in `tests/`) und alle 32 TypeScript-Dateien wurden auf
ungenutzte Symbole, ungenutzte Importe, unbenutzte Konfigurationsschluessel,
mehrfach vorhandene Logik, flache Module und ueberdimensionierte Stellen
geprueft. Das Ergebnis:

| # | Stelle | Befund | Risiko | Prioritaet | Aufwand |
|---|---|---|---|---|---|
| S-01 | `common/Problem.java` | `withInstance(String)` — nirgends aufgerufen | SAFE | **Hoch** | S |
| S-02 | `delivery/DeliveryOutcome.java` | `isRetryable()` — nirgends aufgerufen (nur `isFinal()` wird benutzt) | SAFE | **Hoch** | S |
| S-03 | `endpoint/api/EndpointResponse.java` | `statusValue()` — nirgends aufgerufen | SAFE | **Hoch** | S |
| S-04 | `tenant/TenantContext.java` | `requireOrganisationId()` — nirgends aufgerufen | SAFE | **Hoch** | S |
| S-05 | `delivery/RateLimitGuard.java` | `effectiveLimit(String, UUID)` — die Methode wird nie benutzt; `EndpointController` hat eine **gleichnamige lokale Variable** (Verwechslungsgefahr) | SAFE | **Hoch** | S |
| S-06 | `common/AuditLogRepository.java` | `ROLE_RECEIVER_ADMIN`, `ROLE_SERVICE_ADMIN`, `ROLE_DEVELOPER`, `ROLE_SYSTEM` — vier Konstanten ohne jeden Verwendungszweck | SAFE | **Hoch** | S |
| S-07 | `tenant/TenantTransactionRunner.java` | `tenantParameter(String)` — nirgends aufgerufen | SAFE | **Hoch** | S |
| S-08 | `src/` (3) + `tests/` (26 Dateien) | **29 ungenutzte Importe** (Java) | SAFE | **Hoch** | S |
| S-09 | `src/main/resources/application.yml` | `echobox.crypto.fingerprint-key` / `ECHobox_CRYPTO_FINGERPRINT_KEY` — **nie gelesen**; `PayloadCipher` leitet den Fingerprintschluessel immer aus dem Hauptschluessel ab (ADR-003 §5). Spring bindet die Property stillschweigend, sie wirkt **nicht**. Ein Schluessel, der etwas verspricht, aber nichts tut — und der ADR widerspricht | SAFE | **Hoch** | S |
| S-10 | `endpoint/EndpointQueryService`, `protocol/ProtocolQueryService`, `protocol/DeadDeliveryQueryService` | Die **Cursor-Logik existiert dreimal**: `encodeCursor`, `decodeCursor` und der Record `CursorPosition` — Zeichen fuer Zeichen gleich, mit derselben Fehlermeldung und demselben Format `<millis>:<uuid>` | SAFE | **Hoch** | M |
| S-11 | `endpoint/api/EndpointListResponse`, `protocol/api/DeliveryListResponse`, `protocol/api/DeadDeliveryListResponse`, `common/PageResponse` | Die Seitennavigation existiert **viermal**: `common/PageResponse` als Traeger plus drei strukturgleiche `Page`-Records als JSON-Projektion, dazwischen eine reine Umrechnung `Page.of(PageResponse)` | SAFE | **Hoch** | M |
| S-12 | `common/GlobalExceptionHandler.java` | Die Abbildung auf HTTP 422 `validation_failed` steht **viermal** wortgleich im Code (Fachverletzung, Bean-Validierung, Parametervalidierung, unbrauchbarer Parameter) | SAFE | **Mittel** | S |
| S-13 | `config/SecurityConfiguration.java`, `tenant/TenantParameterFilter.java` | `writeProblem(...)` — **zweimal** dieselbe Implementierung (Status, Content-Type, Zeichenkodierung, JSON) | SAFE | **Mittel** | S |
| S-14 | `delivery/DeliveryHttpClient.java` + `config/EchoBoxProperties.java` | **Zwei Wege fuer dieselben Zahlenwerte**: `EchoBoxProperties.Delivery.Http.getConnectTimeout()/getReadTimeout()` werden **nie gelesen**; die Werte kommen stattdessen aus `@Value("${…:5s}")` — mit **im Code verdrahteten Vorgabewerten** (D-09: „kein Zahlwert ist im Code verdrahtet") | SAFE | **Mittel** | S |
| S-15 | `app/actions.ts` (Oberflaeche) | Fuenf Aktionen tragen **denselben** Block `let code = null; try { await api.post(…) } catch { code = codeForProblem(…) }; if (code !== null) redirect(…)` — fuenfmal kopiert | SAFE | **Hoch** | S |
| S-16 | `features/endpoints/statusPresentation.ts`, `features/protocol/statusPresentation.ts`, `components/StatusBadge.tsx` | `StatusPresentation` existiert **dreimal** mit identischer Gestalt — und `StatusBadgeProps` ist dieselbe Gestalt ein viertes Mal | SAFE | **Hoch** | S |
| S-17 | `features/protocol/deliveryQueries.ts` | `filterFromParams` bringt eine eigene `single(…)`-Hilfe mit — dieselbe Logik wie `param(…)` in `lib/searchParams.ts` | SAFE | **Mittel** | S |
| S-18 | `delivery/DeliveryRepository.java` (867 Z.), `endpoint/EndpointRepository.java` (566 Z.), `config/EchoBoxProperties.java` (664 Z.) | **Ueberdimensionierte Klassen**: ein Adapter traegt viele Statementgruppen | RISKY | **Mittel** | **L** |
| S-19 | `common/ServiceUnavailableException.java`, `common/TenantParameterException.java`, `common/UnauthenticatedException.java` | Drei **Fehlerklassen ohne Aufrufer** — die Aufrufstellen bauen das Fehlerbild direkt (`HealthController`, `TenantParameterFilter`, `SecurityConfiguration`) | RISKY | Niedrig | S |
| S-20 | `delivery/DeliveryDispatcher.record(…)` (8 Parameter), `protocol/ProtocolQueryService.list(…)` (9 Parameter) | **Viele Parameter** an einer Stelle | RISKY | Niedrig | M |
| S-21 | `common/Problem.java` + die drei `Page`-Records | `Problem` ohne `@JsonProperty` war auf die globale Namensstrategie angewiesen; die `Page`-Records setzten die Feldnamen explizit | RISKY | Niedrig | S |
| S-22 | `docker-compose.yml` | Von `docs/architecture/folder-structure.md` §1 und `.agent/knowledge/stack.md` („plus docker-compose fuer die lokale Entwicklung") vorgesehen — **die Datei existiert nicht** | RISKY | Niedrig | M |
| S-23 | `.gitignore` (Projektwurzel) | `.gitignore` schliesst **nur** `/src/main/frontend/test-results/` und `/src/main/frontend/playwright-report/` aus. Ein Playwright-Lauf **aus der Projektwurzel** legt dagegen `/test-results/` im Wurzelverzeichnis an — das **nicht** ausgeschlossen ist und bei einem unbedachten `git add -A` mitcommitet wuerde | SAFE | Niedrig | S |

**Zaehlung:** 23 Befunde — 13 **Hoch**, 5 **Mittel**, 5 **Niedrig**;
Aufwand: 18 × S, 4 × M, 1 × L.

Die Befunde S-18 bis S-22 sind **RISKY** (oeffentliche API, Vertrag, Schema,
Architekturdokument) und wurden **nicht** angefasst — sie sind in Abschnitt 5
dokumentiert.

---

## 2. Was bereinigt wurde — und der Nachweis

Die Bereinigung lief in **drei Chargen**. Nach jeder Charge wurden die
betroffenen Tests laufen gelassen; das Ergebnis steht bei jeder Charge.

### 2.1 Charge 1 — toter Code (S-01 … S-09)

**Beleg fuer jede Entfernung** (`git grep -w <Symbol> HEAD` ueber den ganzen
Repo — `src/`, `tests/`, `docs/`, `.agent/`, `pom.xml`, `package.json`,
`next.config`; jeweils **nur** die Definition):

| Symbol | Datei:Zeile (Stand `e9e1bc1`) | Treffer | Entfernt |
|---|---|---|---|
| `withInstance` | `common/Problem.java:34` | 1 (nur die Definition) | ja |
| `isRetryable` | `delivery/DeliveryOutcome.java:103` | 1 | ja |
| `statusValue` | `endpoint/api/EndpointResponse.java:66` | 1 | ja |
| `requireOrganisationId` | `tenant/TenantContext.java:63` | 1 | ja |
| `effectiveLimit` (Methode) | `delivery/RateLimitGuard.java:93` | 1 | ja |
| `tenantParameter` | `tenant/TenantTransactionRunner.java:110` | 1 | ja |
| `ROLE_RECEIVER_ADMIN`/`ROLE_SERVICE_ADMIN`/`ROLE_DEVELOPER`/`ROLE_SYSTEM` | `common/AuditLogRepository.java:29,32,35,38` | je 1 | ja |
| `fingerprint-key` | `src/main/resources/application.yml:90` | 1 (nie gelesen) | ja |

Mit entfernt: die dadurch **verwaisten Importe** (`EndpointStatus` in
`EndpointResponse`, `UnauthenticatedException` in `TenantContext`,
`java.util.Map` in `TenantTransactionRunner`).

**29 ungenutzte Importe** entfernt: 3 in `src/` (`EventRepository`,
`EndpointMetricsResponse`, `RetentionService`), 26 in `tests/`. Jeder wurde
einzeln geprueft (nur der Import-Treffer, kein Verwendungs-Treffer).

**Nachweis:** `mvn -q verify` → **Exit 0** (89 Unit, 258 Integration, 19
Vertrag, 2 Last — alle gruen). `tsc --noEmit` 0, `eslint` 0, `vitest` **30/30**,
`next build` ok.

### 2.2 Charge 2 — Duplikation im Backend (S-10 … S-14)

| # | Aenderung | Warum verhaltenserhaltend |
|---|---|---|
| S-10 | `common/CursorPosition` (neu) + `CursorCodec.encodePosition(…)`/`decodePosition(…)`; die drei Listenabfragen nutzen sie | Das **Nutzformat bleibt `<millis>:<uuid>`** — ein bestehender Cursor bleibt gueltig. Die Fehlerunterscheidung bleibt: `invalid_cursor` (kaputte Signatur) aus `decode(…)`, `cursor_invalid` (Signatur ok, Inhalt unlesbar) aus `decodePosition(…)`. Die Sortierschluessel bleiben, was sie waren: `updated_at`/`endpoint_id` (Endpunkte), `created_at`/`delivery_run_id` (Protokoll, Totenliste) |
| S-11 | `common/PageResponse` traegt jetzt `@JsonProperty("limit"/"has_more"/"next_cursor")` und wird **direkt** in den drei Listen benutzt; die drei `Page`-Records und die `Page.of(…)`-Umrechnung entfallen | Die **JSON-Ausgabe ist byteweise gleich** — nachgewiesen an der serialisierten Antwort: `{"items":[],"page":{"limit":50,"has_more":false,"next_cursor":null}}` (vorher und nachher identisch; `next_cursor: null` wird weiterhin geschrieben) |
| S-12 | `GlobalExceptionHandler.validationFailed(request, violations)` — **eine** Abbildung statt vier | Wortlaut, Status (422), Code (`validation_failed`), Titel und Detail bleiben unveraendert; nur die Verstoesse unterscheiden sich — wie vorher |
| S-13 | `common/ProblemWriter` (neu) — **eine** Schreibweise fuer die Fehlerantwort | Beide Stellen schrieben Status, `application/problem+json`, UTF-8 und dasselbe JSON. Unveraendert |
| S-14 | `DeliveryHttpClient` ist **keine** `@Component` mehr; die Bean entsteht in `DeliveryConfiguration` aus `EchoBoxProperties` | Der **Konstruktor `(Duration, Duration)` bleibt unveraendert** (vom Testvertrag §6.2 gebunden). Der Wert kommt aus derselben Property (`echobox.delivery.http.connect-timeout`) — nur aus **einer** Quelle statt aus zwei. Der im Code verdrahtete Vorgabewert `5s`/`10s` ist entfallen; der Vorgabewert der `EchoBoxProperties` ist derselbe |

**Nachweis:** `mvn -q verify` → **Exit 0**. `tsc --noEmit` 0, `eslint` 0,
`vitest` 30/30, `next build` ok, `npm run contract:check` ok, beide
Schema-Diffs **leer**, `traceability_check.py` gruen.

### 2.3 Charge 3 — Duplikation in der Oberflaeche (S-15 … S-17)

| # | Aenderung | Warum verhaltenserhaltend |
|---|---|---|
| S-15 | `actions.ts`: `runAction(call, secretCode)` kapselt den fuenfmal kopierten Block; `requireIdentifier(value, path)` die viermal kopierte Kennungspruefung | Die **Umleitung bleibt ausserhalb** des `try` — sonst wuerde Next.js' `redirect` (das wirft) als Fehler verschluckt. Die ErgebnisCodes (`unautorisiert`, `verboten`, `nicht-gefunden`, `konflikt`, `typen-fehlen`, `url-ungueltig`, `dienst-fehler`, `secret-zu-kurz`, `rotation-secret-zu-kurz`) sind unveraendert |
| S-16 | `StatusPresentation` steht **einmal** in `components/StatusBadge.tsx`; `StatusBadgeProps` ist ein Alias darauf; die beiden Fachmodule importieren den Typ | Die beiden Fachmodule zeigen **weiterhin unterschiedliche Texte** („aktiv“/„pausiert“ gegenueber den Vertragswerten) — nur der **Typ** ist jetzt einer |
| S-17 | `filterFromParams` nutzt `param(…)` aus `lib/searchParams.ts` statt einer eigenen `single(…)`-Kopie | `param` liefert `string \| null`, die Kopie `string`; `?? ''` macht die Werte identisch (String, erstes Array-Element, sonst leer) |

**Nachweis:** `tsc --noEmit` **0 Fehler**, `eslint .` **0 Befunde**,
`npx vitest run` **30/30 gruen**, `npx next build` **0 Fehler**,
`npx playwright test` **31/31 gruen** (3 davon ohne JavaScript),
`npm run contract:check` **ok** (11 Schemas, 3 Aufzaehlungen).

### 2.4 Was **nicht** geaendert wurde

- **Der Vertrag** `docs/api/openapi.yaml` — unberuehrt.
- **Das Schema** — `.agent/knowledge/schema.sql`, `docs/architecture/schema.sql`
  und `src/main/resources/db/migration/V1__initial_schema.sql` sind
  **byte-identisch** (beide Diffs leer, `SchemaMirrorIT` gruen).
- **Die Produktions-API, die der Testvertrag bindet** (`.agent/artifacts/
  test-strategy.md` §6) — unveraendert. Das gilt fuer
  `DeliveryHttpClient` (Konstruktor `(Duration, Duration)`), `DeliveryPort`,
  `DeliveryWorker`, `ClockPort`, `DeliveryStateMachine`, `DeliveryOutcome`
  (`classify`, `isFinal`), `PayloadCipher`, `SignatureService`,
  `EndpointTargetUrl`, `PayloadSize`, `BackoffPolicy`, `AttemptLimit`,
  `EndpointRateLimiter`, `RateLimitDecision`, `DeliveryHeaders` und die drei
  Frontend-Seiten.
  **Einzige Ausnahme:** `DeliveryOutcome.isRetryable()` wurde entfernt — sie
  ist **nicht** Teil des Testvertrags (§6.1 nennt `classify` und `isFinal`)
  und wurde von keinem Test benutzt. `isFinal()` bleibt, damit die
  Gegenrichtung erhalten bleibt.
- **Mandantentrennung, Krypto, Signatur, die Zustaende der Zustellung** —
  unberuehrt. `TenantTransactionRunner.setTenant(…)`, `CrossTenantScope`,
  `PayloadCipher`, `SignatureService` und `DeliveryStateMachine` sind
  unveraendert.

---

## 3. Was bewusst bleibt — und warum

| Stelle | Warum sie bleibt |
|---|---|
| `common/ServiceUnavailableException.java`, `common/TenantParameterException.java`, `common/UnauthenticatedException.java` | **RISKY.** `docs/architecture/folder-structure.md` §4 nennt `TenantParameterException` **ausdruecklich** als Teil des Fehlerkatalogs von `common`; die drei Klassen sind das dokumentierte Vokabular des Fehlerbilds (401/422/503). Zwei ihrer Aufrufstellen liegen **ausserhalb** der Controller-Kette (Servlet-Filter, Security-Kette) — dort greift der `GlobalExceptionHandler` nicht, deshalb wird das Fehlerbild dort direkt gebaut. `UnauthenticatedException` wurde nach dem Entfernen von `requireOrganisationId()` (S-04) ebenfalls ohne Aufrufer. Ein Loeschen wuerde das read-only Architekturdokument ins Leere laufen lassen |
| `delivery/DeliveryRepository.java` (867 Z.), `endpoint/EndpointRepository.java` (566 Z.) | **RISKY, Aufwand L.** Das Aufteilen eines JDBC-Adapters in mehrere Klassen aendert die Transaktionsgrenzen — das ist ein Verhaltensrisiko in der Mandantentrennung, kein Formatierungsproblem. **Befund, nicht Aenderung in diesem Schritt** (siehe Abschnitt 5) |
| `config/EchoBoxProperties.java` (664 Z.) | Die Datei ist gross, aber **jede** Konstante ist entweder gebunden (`@ConfigurationProperties`) oder gelesen. Die Setter sind Spring-Bindings, kein toter Code |
| `delivery/DeliveryDispatcher.record(…)` (8 Parameter) | **RISKY.** Eine Aenderung der Parameterliste beruehrt den Vertrag der Zustaelllaeufe. Der Rueckgabewert ist an einer Stelle — die Klarheit ist vertretbar |
| `docs/execution/step6-*.md` — die Erklaerungen T-1…T-27 | Sie sind das **Protokoll** des Nachzugs aus Schritt 6; sie werden in Schritt 7 nur **nummeriert**, nicht umgeschrieben |
| `common/Problem` ohne `@JsonProperty` an den Feldern | Das Feld `type`/`title`/… wird ueber `spring.jackson.property-naming-strategy: SNAKE_CASE` projiziert. Eine Annotation je Feld waere eine **zweite** Wahrheit — die globale Strategie ist die eine |

---

## 4. B-16 — die doppelten Nummern

**Befund erledigt.** Die Nummern **T-8…T-10** (Teil B und Teil C) sowie
**B-12** und **B-13** (Teil B und Teil C) waren doppelt vergeben.

**Die Nummerierung folgt jetzt der Reihenfolge der Teile A → B → C → D:**

| Bereich | Nummern | vorher |
|---|---|---|
| Teil A | **T-1 … T-7** | unveraendert |
| Teil B | **T-8 … T-16** | unveraendert |
| Teil C | **T-17 … T-19** | T-8 … T-10 (Teil C) |
| Teil D | **T-20 … T-27** | T-17 … T-24 |

**Befunde:** die beiden Befunde aus Teil B heissen jetzt **B-17**
(Uebergangstabelle des Domänenmodells) und **B-18** (Endpunkt-Idempotenz-
schluessel). **B-12** (`next lint`) und **B-13** (npm-Workspace-Wurzel) bleiben
die Befunde aus Teil C — damit bleiben die Verweise im Steckbrief und in
Schritt 9 gueltig.

**Die Reihenfolge der Erklaerungen ist unveraendert** — nur die Nummern sind
eindeutig. Korrigiert wurden: `docs/execution/step6-umsetzungsbericht.md`
(Abschnitte 5.2, 5.3 und 7), `step6-teil-b-zustellung-protokoll.md`
(Abschnitt 5), `step6-teil-c-oberflaeche.md` (Abschnitte 3 und 5).

Nachkontrolle: `B-01 … B-18` bezeichnen in `docs/execution/` jetzt **je genau
einen** Befund; `T-1 … T-27` **je genau eine** Nachzug-Begruendung.

---

## 5. Verbleibende technische Schulden

| ID | Befund | Prioritaet | Aufwand | Owner |
|---|---|---|---|---|
| S-18 | `DeliveryRepository` (867 Z.) und `EndpointRepository` (566 Z.) tragen viele Statementgruppen; ein Aufteilen beruehrt die Transaktionsgrenzen | Mittel | **L** | Schritt 8/9 (mit Testnachweis) |
| S-19 | `ServiceUnavailableException`, `TenantParameterException`, `UnauthenticatedException` ohne Aufrufer — das Fehlerbild wird an drei Stellen ausserhalb der Controller-Kette direkt gebaut | Niedrig | S | Architektur (Gate 2) |
| S-20 | `DeliveryDispatcher.record(…)` (8) und `ProtocolQueryService.list(…)` (9) Parameter | Niedrig | M | nach Bedarf |
| S-22 | `docker-compose.yml` fehlt (in `folder-structure.md` §1 und `stack.md` vorgesehen) — die **lokale Entwicklung** startet den Dienst heute ohne Compose | Niedrig | M | Schritt 9 |
| **B-17** | Die Uebergangstabelle des Domänenmodells nennt nur `failed → delayed`; die Implementierung erlaubt den Uebergang von jedem offenen Zustand | — | — | Architektur (Schritt 8) |
| **B-18** | `endpoint_event_types` hat keinen eindeutigen Schluessel ueber `(endpoint_id, event_type)` **je Mandant** | — | — | Gate 2 |

**Kein Vertragsdefekt** wurde gefunden. `docs/api/openapi.yaml` wurde nicht
geaendert.

---

## 6. Der Testnachweis

| # | Pruefung | vor der Aenderung | nach der Aenderung |
|---|---|---|---|
| 1 | `mvn -q verify` (89 Unit, 258 Integration, 19 Vertrag, 2 Last) | gruen (Exit 0) | **gruen (Exit 0)** — nach Charge 1, Charge 2 und zum Schluss |
| 2 | `npx tsc --noEmit` (strict, Oberflaeche **und** `tests/`) | 0 Fehler | **0 Fehler** |
| 3 | `npx eslint .` (ESLint 9, Flat Config) | 0 Befunde | **0 Befunde** |
| 4 | `npx next build` (Next 15.5.28) | ok, 0 Fehler | **ok, 0 Fehler** |
| 5 | `npx vitest run` (`tests/unit/frontend`) | 30/30 gruen | **30/30 gruen** |
| 6 | `npx playwright test` (`tests/e2e`) | 31/31 gruen | **31/31 gruen** (3 davon ohne JavaScript) |
| 7 | `npm run contract:check` | ok (11 Schemas, 3 Aufzaehlungen) | **ok** |
| 8 | `diff .agent/knowledge/schema.sql src/main/resources/db/migration/V1__initial_schema.sql` | leer | **leer** |
| 9 | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | leer | **leer** |
| 10 | `python3 .agent/tools/traceability_check.py` | gruen | **gruen** |
| 11 | JSON-Projektion der Seitennavigation (Probe gegen die gebaute Klasse) | `{"items":[],"page":{"limit":50,"has_more":false,"next_cursor":null}}` | **identisch** |

> **Wegweiser fuer Schritt 8/9:** `npx playwright test` laeuft **im
> Frontend-Verzeichnis** (`src/main/frontend`) — aus der Projektwurzel findet
> Playwright keine Konfiguration und zieht die Vitest-Dateien unter
> `tests/unit/frontend` mit. Der Wegweiser aus der Projektwurzel ist
> `npm run test:e2e` (delegiert in das Workspace-Mitglied).

---

## 7. Sicherheitshinweise

- **Keine Secrets in Code, Konfiguration oder Image.** Die Bereinigung hat an
  den Schluesselwegen nichts geaendert: `ECHobox_CRYPTO_KEY` → `PayloadCipher`,
  `ECHobox_CURSOR_KEY` → `CursorCodec`, `ECHobox_SECURITY_JWK_SET_URI` →
  `JwtDecoder`.
- Die entfernte Property `echobox.crypto.fingerprint-key` war **wirkungslos** —
  ihre Entfernung kann **keinen** Schluesselweg veraendern (ADR-003 §5 leitet
  den Fingerprintschluessel aus dem Hauptschluessel ab).
- Die Mandantentrennung, die RLS-Policies und die transaktionsgebundene
  Mandantenbindung (`TenantTransactionRunner`) sind unveraendert.
- Die Fehlerantworten tragen unveraendert **ein** Schema (`Problem`) und
  **eine** Fehlerabbildung (`GlobalExceptionHandler`).
