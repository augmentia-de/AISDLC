# ADR-001 — Tech-Stack: Spring Boot 3 / Java 21, React 19, PostgreSQL 16

| Feld | Wert |
|---|---|
| **ID** | ADR-001 |
| **Titel** | Tech-Stack: Spring Boot 3 / Java 21, React 19, PostgreSQL 16 |
| **Status** | **Angenommen** |
| **Datum** | 2026-10-20 |
| **Entscheider** | Architecture Orchestrator (`aisdlc-architecture`), bestätigt durch F10 |
| **Betrifft** | CON-001, CON-002, CON-003, CON-004, CON-005, CON-007, F10, A-11, NFR-016, NFR-017 |
| **Supersediert** | — |

## Kontext

`.agent/knowledge/stack.md` nennt als Backend-Default „FastAPI (Python 3.11+) **oder**
Spring Boot (Java 21+)". Die Kund-Mail fordert ausdrücklich **Langchain4j** — eine
JVM-Bibliothek — und **React**.

Damit steht das Framework nicht mehr zur freien Wahl: Langchain4j erzwingt eine JVM-Laufzeit
und damit Spring Boot. Die Stack-Vorgabe löst sich also faktisch auf (CON-001, Zielkonflikt
K-7). Offen bleiben die konkreten Versionen, der Build und die Betriebsform.

Weitere Randbedingungen:

* **NFR-016** — Typsicherheit strikt (TypeScript strict, Java-Compiler-Warnungen als Fehler),
  Formatierung/Linting in CI.
* **NFR-009 / FR-073** — Coverage-Gate ≥ 80 % über Backend **und** Frontend, harter CI-Blocker (F9).
* **CON-007 / NFR-017** — Container-Images, non-root, gepinnte Tags, Health-Check,
  docker-compose lokal, Cloud Run (`europe-west3`) in Produktion.
* **K-8** — die asynchrone KI-Prüfung muss testbar sein; KI-Antworten gehören hinter ein
  Adapter-Interface, damit der Coverage-Nachweis für die Orchestrierung gilt, nicht für das
  Modell.
* **F7** — kein SSO/OIDC und kein MFA im MVP, aber ein **Einbaupunkt** wird in Phase 2
  festgeschrieben.

## Entscheidung

### Backend

| Aspekt | Entscheidung |
|---|---|
| Laufzeit / Sprache | Java 21 (LTS) |
| Framework | Spring Boot 3.x (Web, Security, Validation, Data JPA, Actuator) |
| KI-Anbindung | **Langchain4j** (ChatModel + EmbeddingModel), Provider über Konfiguration |
| Datenbank | PostgreSQL 16, `organisation_id`-Scoping, Row-Level-Security (CON-003) |
| Migration | Flyway |
| Persistenz | Spring Data JPA / JDBC, **niemals** dynamisch zusammengesetztes SQL |
| Build | Maven Wrapper, `mvnw verify` als Gate |
| Tests | JUnit 5, Testcontainers (echte PostgreSQL-Instanz für die Isolationstests) |
| Coverage | JaCoCo, Gate bei 80 % Line Coverage, generierter Code ausgeschlossen |

### Frontend

| Aspekt | Entscheidung |
|---|---|
| Framework | **React 19** in **Next.js 15 (App Router)**, TypeScript mit `strict: true` |
| Rendering | **Client Components** für alle datenabhängigen Ansichten; der App Router liefert Routing, Layout und Fehlergrenzen. **Kein** SSR dynamischer Fachdaten — CON-002: internes Tool, kein SEO-Treiber |
| Build | `next build` (statische Optimierung), ein Container-Artifact für Cloud Run |
| Routing / Daten | App Router (`app/`-Segment) + TanStack Query für Server-State, SSE-Client für den Live-Status (ADR-008) |
| API-Typen | **aus dem Vertrag generiert** (`openapi-typescript`) — die Typen sind die Vertragsquelle, nicht eine zweite Wahrheit |
| Tests | Vitest + React Testing Library, `axe-core` für die Barrierefreiheit, Playwright für E2E |
| Coverage | Vitest-Coverage, Gate bei 80 %, Teil des CI-Gates (F9) |

### Betrieb

| Aspekt | Entscheidung |
|---|---|
| Container | Docker, non-root, gepinnte Tags, `HEALTHCHECK` (NFR-017) |
| Produktion | Cloud Run, Region `europe-west3` (EU-Hosting, FR-065) |
| CI/CD | GitHub Actions: Build → Test → Coverage-Gate → Security-Scan → Image-Scan → Deploy |
| Secrets | ausschließlich Umgebungsvariablen / gemountete Secrets, `.env` in `.gitignore`, Secret-Scan (CON-005, SEC-012) |

### Einbaupunkt für MFA (F7)

Der Authentifizierungs-Stack wird hinter einem schmalen Port `AuthenticationPort`
gekapselt (`verify(credentials) -> Principal`, `issueTokens`, `rotateRefreshToken`,
`revokeSession`). Eine spätere MFA-Erweiterung (TOTP/WebAuthn für `SECURITY_ADMIN` und
`ORG_ADMIN`) und eine spätere OIDC-Anbindung kommen hinter demselben Port hinzu, ohne dass
Controller, Service- oder Datenbankschicht berührt werden müssen. Der Port wird in Phase 6
als eigenes Interface angelegt, damit die Nachrüstung nicht in die Tiefe der Fachlogik
eindringt.

### Adapter für den KI-Dienst (K-8)

Der Zugriff auf das Modell läuft über zwei Ports:

* `ClassificationPort` — Prompt-Injection/Jailbreak-Klassifikation (ChatModel)
* `EmbeddingPort` — Duplikatvergleich (EmbeddingModel)

Beide Ports liefern **Datenobjekte, kein Modell**. Im Test werden sie durch Stubs/Fakes
ersetzt. Dadurch ist die Orchestrierungslogik vollständig testbar und der 80-%-Nachweis
gilt für die steuerende Logik, nicht für das Modell (K-8, NFR-009).

## Konsequenzen

### Positiv

* **Eine Sprache im Backend** — Langchain4j, Spring Data, Testcontainers und Jakarta EE
  arbeiten ohne Bruch zusammen; kein Adapter zwischen JVM- und Python-Bibliothek.
* **Testbarkeit** — die KI-Teile sind hinter Ports austauschbar, die 80-%-Regel ist
  belastbar erfüllbar.
* **Betrieb** — Spring Boot bringt Actuator (Health/Metriken), Security-Header und
  Validierung mit; wenig Eigenbau.
* **TypeScript strict** auf dem Frontend deckt NFR-016 direkt ab.

### Negativ / Kosten

* **Schwerer Start und mehr Build-Zeit** als FastAPI: JVM-Start, Maven-Auflösung,
  Container-Build. Für ein internes Tool akzeptabel (kein Serverless-Edge-Bedarf).
* **Wärmeaufnahme** des JVM-Containers (Cloud Run) — Mitigierung: moderate Heap-Größe,
  `JAVA_TOOL_OPTIONS` mit `-XX:MaxRAMPercentage`.
* **SSE auf Cloud Run** hat eine Request-Dauergrenze; der Live-Status muss das abfedern
  (→ ADR-008).
* **Zwei Coverage-Gates** (Backend, Frontend) in einer Pipeline — die Schwelle von 80 %
  gilt je Komponente, nicht im Mittel.

### Neutral

* FastAPI bleibt im Stack-Default von `AGENTS.md` / `stack.md` als Alternative für
  Greenfield-Projekte ohne JVM-Zwang stehen; für **PromptGuard** ist es nicht relevant.
* `stack.md` bleibt unverändert — die Stack-Entscheidung ist in diesem ADR dokumentiert,
  die Knowledge-Datei bleibt die generische Vorlage.

## Alternativen, die verworfen wurden

| Alternative | Warum verworfen |
|---|---|
| FastAPI (Python) | Widerspricht CON-001/der Kund-Mail: Langchain4j ist eine JVM-Bibliothek. Zwei Laufzeitwelten, ein Provider-SDK, zusätzliche Bridge. |
| Node.js/NestJS | Widerspricht CON-001 (Langchain4j) und F10; außerdem kein Vorteil bei KI-Integration. |
| Spring Boot 2 / Java 17 | Java 21 ist LTS, bringt Records/Pattern Matching und ist die Basis der Kunden-Mail („Java 21+“). |
| Next.js mit **SSR dynamischer Fachdaten** | CON-002: internes Tool, kein SEO-Treiber. Next.js wird **gewählt** — aber ohne SSR der Fachdaten: alle datenabhängigen Ansichten sind Client Components. So bleibt der Betriebsaufwand gering und der Vertrag bleibt die einzige Datenquelle. |
| React-SPA (Vite) ohne Framework-Routing | Technisch möglich und minimal, aber Routing, Fehlergrenzen, Layout und Deployment-Artefakt müssten selbst gebaut werden. Der Stack-Default (`AGENTS.md`) nennt Next.js 15 zuerst. |
| PostgreSQL-Volltextsuche vs. Elasticsearch | Für 1 000 000 Prompts (A-10) reicht `tsvector` + GIN-Index in PostgreSQL; ein zweiter Speicher wäre eine zusätzliche Mandanten-Isolationsfläche (K-2). Bewertung in Phase 2 abgeschlossen, Nachszenario in §6.3 des PRD. |

## Verweise

* `docs/requirements/PRD.md` §12 (CON-001…CON-010), §14 (F9, F10), §15 (K-7, K-8)
* `docs/api/openapi.yaml` — Target-Stack des Vertrags
* `docs/architecture/diagrams/01-context.mmd`, `02-container.mmd`
