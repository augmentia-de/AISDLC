# Steckbrief — Schritt 6 (KI-Code-Generation)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 5 (Test-Driven Setup) abgeschlossen — Commit `test(sdlc-5): Teststrategie und fehlschlagende Tests` (Hash: `git log -1`); davor Schritt 2 (Ruecksprung 6) `8627bb6`, Schritt 4 `68ffa4e`, Schritt 3 `05bceca`, Schritt 1 `cc67445` |
| Dieser Schritt | 6 (KI-Code-Generation) — die Tests werden **gruen** |
| Nächster Schritt | 7 (Simplify, zweiter Durchlauf) → 8 (Security & Compliance) |
| **Vertrag** | `docs/api/openapi.yaml` — **EINGEFROREN seit Commit `8627bb6`**. Ein Vertragsdefekt ist ein Befund fuer einen Ruescksprung zu Schritt 2, **keine** Aenderung im Vorbeigehen. |
| Schema | `.agent/knowledge/schema.sql` (kanonisch) + Spiegel `docs/architecture/schema.sql` — byte-identisch; **nicht** anfassen |
| Stack | `.agent/knowledge/stack.md` — Java 21, Spring Boot 3, JdbcTemplate, PostgreSQL 16, Next.js 15 / React 19, JUnit 5, Testcontainers, WireMock, Vitest, Playwright, axe-core |
| Codebasis | `src/` existiert **noch nicht** — die 305 Tests in `tests/` laufen **rot** |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/06-implementation.md` (bzw. die Workflow-Datei dieses Schritts) | dieser Schritt, Output-Liste, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/03_orchestrator_implementation.md` | dein Systemprompt |
| 5 | `.agent/knowledge/stack.md` | Stack und Test-Stack (Entscheidungen, keine Vorschlaege) |
| 6 | `docs/api/openapi.yaml` | **der eingefrorene Vertrag** — die Testorakel |
| 7 | `.agent/artifacts/test-strategy.md` | **der Testvertrag**: Ebenen, Testdaten, Ports, Konfigurationsschluessel, Build-Anschluss, Abbruchkriterien A-1…A-8 |
| 8 | `.agent/artifacts/test-matrix.md` | die Matrix aller 101 AKs → Testfall; die Befunde B-01…B-06 |
| 9 | `docs/architecture/folder-structure.md` | Zielstruktur fuer `src/` — Paketnamen, Modulzuordnung, Dateinamen |
| 10 | `docs/architecture/domain_model.md` | Fachbegriffe und **Zustandsautomat der Zustellung** |
| 11 | `.agent/knowledge/schema.sql` | das kanonische Schema (Tabellen, Spalten, Constraints, RLS) |
| 12 | `docs/requirements/stories/US-0xx.json` | die 101 Akzeptanzkriterien — was der Code erfuellen muss |

Bei Bedarf: `docs/adr/ADR-*.md`, `docs/architecture/architecture.md`, `.agent/artifacts/architecture.md`.

## Entscheidungen, die gelten — der Testorakel

D-01…D-15 · ADR-001…ADR-018 · F-01…F-04 · E-01…E-11 · E-13 · V-26…V-27 · V-39…V-44 · V-48…V-49
Der Vertrag, das Schema und die ADRs sind die **Testorakel**. Wo eine Story, ein Feature und der
Vertrag unterschiedlich formulieren, gilt die **entschiedene Semantik** aus Vertrag + ADR.

| ID | Bedeutung fuer deine Implementierung |
|---|---|
| D-01 | Mandant aus dem JWT; ein Mandantenparameter ⇒ **422** (`tenant_parameter_rejected`) — im Body, im Query **und** im Header |
| D-02 | Fremde Ressource ⇒ **404** (Existenz bleibt verborgen) — auch fuer `service_admin` |
| V-39 | **Mandant vor Rolle**: fremder Mandant ⇒ **404**; eigener Mandant, falsche Rolle ⇒ **403** |
| V-26 | `organisation_id` **immer** gefuellt (auch in den mandantenuebergreifenden Antworten) |
| V-27 | Mandantenuebergreifende Sicht **nur** fuer Endpunktliste, Totenliste, Aufbewahrung; Einzelabruf ⇒ **404** auch fuer `service_admin` |
| E-09 / I-9 | Replay **einmal** je Lauf (UNIQUE `replay_of`), danach **409** `delivery_state_conflict` |
| F-01 | 60 Versuche/Endpunkt/Minute, Strategie `delay` |
| F-02 | Aufbewahrung 30 Tage, getrennt, ab letztem Versuch; Kaskade Ereignis → Läufe → Versuche |
| F-03 | Replay nur fuer den Empfaenger-Admin des Endpunkts; `developer`/`service_admin` ⇒ **403** |
| F-04 | 8 Versuche, 1 min Basis, ×2, Deckel 60 min, ± 20 % |
| ADR-002 §3 | `app.set_tenant(...)` **innerhalb** der Transaktion, die auch liest — transaktionsgebunden, nicht verbindungsgebunden |

## Der Testvertrag — was der Code erfuellen muss

Der vollstaendige Vertrag steht in `.agent/artifacts/test-strategy.md`, Abschnitt 6. Die
tragenden Punkte in Kuerze:

- **Ports:** der ausgehende Zustellzugriff laeuft ueber das Interface
  `com.echobox.delivery.DeliveryPort`; `DeliveryHttpClient` ist der Adapter. Ohne dieses
  Port-Interface ist die Zustelllogik nicht testbar (Abbruchkriterium **A-6**).
- **Worker:** `com.echobox.delivery.DeliveryWorker` mit `runOnce()` und `runOnce(instanceId)`;
  der Bean muss **auch bei deaktiviertem Zeitplan** existieren.
- **Uhr:** `com.echobox.common.ClockPort` — testbare Uhr, im Betrieb die Systemuhr.
- **Anwendung:** `com.echobox.EchoBoxApplication` (`@SpringBootApplication`).
- **Token:** Claim `organisation_id`; Rollen als Claim `roles` **und** als Autoritaet
  `ROLE_<rolle>`.
- **Migration:** `src/main/resources/db/migration/V1__initial_schema.sql` — byte-identisch
  mit `.agent/knowledge/schema.sql` (`SchemaMirrorIT`).
- **Frontend:** `src/main/frontend/src/app/{endpoints,deliveries,dead-deliveries}/page.tsx`
  als **Default-Export**, eine Komponente je Seite.
- **Build:** `tests/unit`, `tests/integration`, `tests/contract`, `tests/performance` als
  Test-Quellverzeichnisse; Surefire `*Test`, Failsafe `*IT`; der Lasttest in einem eigenen
  Profil.

## Offene Punkte — aus Schritt 5

| ID | Thema | Owner |
|---|---|---|
| **B-01** | AC-011-1 („mindestens doppelt") vs. ± 20 % Streuung (F-04) — der Test prueft Nennwartezeit und Streuung getrennt | Schritt 1 (Formulierung) — **kein Blocker** |
| **B-02** | Acht Rollenrechte der Vertragsrollenmatrix ohne eigenes AK (V-52) — je einem Testfall zugewiesen | Schritt 1 (AK-Nachpflege) — **kein Blocker** |
| **B-03** | Die Migration enthaelt ein eigenes `BEGIN; … COMMIT;` — Flyway muss sie transaktionsneutral ausfuehren (`execute-in-transaction=false`) | **du** — Abbruchkriterium **A-1** |
| **B-04** | Der Lasttest misst die Verarbeitungszeit (MockMvc), nicht die Transportzeit | du: Lasttest gegen die laufende Instanz bestaetigen |
| **B-05** | Der WAL des Testcontainers ist nicht lesbar; der Journal-Nachweis stuetzt sich auf `pg_dump` und das Server-Protokoll | dokumentiert — kein Handlungsbedarf |
| **B-06** | Der Kontrast (AC-021-3) ist nur im echten Browser pruefbar | dokumentiert — kein Handlungsbedarf |
| O-04, O-05, E-12, R-01/E-13 | fachliche Fragen | Fachbereich/Recht, Gate 2 |

## Was du in diesem Schritt **nicht** tust

- **Den Vertrag nicht aendern.** Er ist eingefroren (`8627bb6`). Vertragsdefekte ⇒ **Befund**
  melden (`ARCHITECTURAL_BLOCKER` mit Fundstelle, erwartet vs. tatsaechlich, Auswirkung,
  Korrekturvorschlag), **nicht** beheben.
- **Keine Aenderung** an `docs/requirements/`, `docs/api/`, `docs/adr/`, `docs/architecture/`,
  `.agent/knowledge/`, `docs/Anforderung.md`.
- **Keine Testfaelle loeschen oder auskommentieren**, um gruen zu werden. Ein Test, der ohne
  Codeaenderung unterschiedlich ausgeht, ist ein Fehler — dann wird der **Code** geaendert.
- **Keine Testfaelle ohne Akzeptanzkriterium.** Jeder Testfall verweist auf eine **AC-ID**
  (z. B. `AC-007-1`) — und umgekehrt: **jede** der 101 ACs hat mindestens einen Testfall.
- **Keine erfundenen Anforderungen.** Was nicht pruefbar ist, wird als Luecke gemeldet.
- Kein `git push`. Keine Ausgabe-Kommandos (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Ergebnisse

### 1 — `src/` — der Anwendungscode, der die 305 Tests gruen macht

Struktur gemaess `docs/architecture/folder-structure.md`: Paket `com.echobox.<modul>`,
`endpoint` · `event` · `delivery` · `crypto` · `protocol` · `tenant` · `config` · `common`,
dazu `src/main/resources/` (Konfiguration, Flyway-Migration) und `src/main/frontend/`
(Next.js 15 / React 19, TypeScript strict).

### 2 — `.agent/artifacts/implementation-report.md` (dieser Schritt)

Was implementiert wurde, welche Tests von rot auf gruen gingen, welche **nicht** gruen wurden
und warum, und welche Befunde an Schritt 2 zurueckgehen.

### 3 — Build-Anschluss

`pom.xml` (Java) und `package.json` (TypeScript) entstehen **hier**, nicht in Schritt 5:
Test-Quellverzeichnisse, Surefire/Failsafe, Vitest, Playwright, Failsafe-Profil fuer den
Lasttest.

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Roter Zustand (vor der Implementierung) | `mvn -q test` · `npm test` |
| Schema-Spiegel (unveraendert halten) | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` |

## Commit — genau einer, Befehl aus der Workflow-Datei

```bash
mkdir -p src/
git reset -q
git add -A -- src/ .agent/artifacts/
git commit -m "feat(sdlc-6): Implementierung nach der Testmatrix" \
  -m "Schritt 6 - KI-Code-Generation | Agent: aisdlc-execution | Artefakte: src/, .agent/artifacts/"
```
Wenn sich nichts geaendert hat: kein Commit.
