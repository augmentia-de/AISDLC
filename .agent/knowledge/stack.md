# Tech Stack — EchoBox (Ereignis-Briefkasten mit Zustellgarantie)

> **Kein Template.** Diese Datei ist die Entscheidung dieses Projekts.
> Quelle: `docs/adr/ADR-001-tech-stack.md` (Nygard-Schema). Bei jeder Änderung
> sind **beide** Dateien im selben Schritt anzupassen.
> Stand: Schritt 2 (Konzept & Architektur) · Agent: `aisdlc-architecture` · 2026-10-21

## Runtime und Sprache

**Entschieden:** Java 21 (LTS) — Backend; TypeScript 5 (strict) — Oberfläche.

**Warum:** Abschnitt 11 der Anforderung legt Java 21, Spring Boot und
PostgreSQL als Rahmenbedingung aus der Technik fest. Java 21 ist die aktuelle
LTS-Version und bringt `record`, Pattern Matching und virtuelle Threads mit.

**Ausgeschlossen:**
- **FastAPI / Python** — widerspricht der Rahmenbedingung aus Abschnitt 11.
- **Node.js / NestJS** — widerspricht Abschnitt 11; die Zustellung braucht
  präzises Zeit- und Nebenläufigkeitsverhalten.
- **Kotlin** — nicht gefordert; der Sprachgewinn rechtfertigt die
  Abweichung von der Vorgabe nicht.

## Framework und Bibliotheken

**Entschieden:** Spring Boot 3.x — `spring-boot-starter-web`,
`spring-boot-starter-security`, `spring-boot-starter-validation`,
`spring-boot-starter-jdbc`, `spring-boot-starter-actuator`.
Persistenz über **Spring Data JDBC (JdbcTemplate)**, **nicht** JPA/Hibernate.

**Warum:** Spring Boot bringt die Laufzeit- und Konfigurationskonventionen mit,
die der Dienst braucht (Security-Filter, Validierung, Actuator). JdbcTemplate
statt JPA: der Mandantenkontext ist eine transaktionslokale Sitzungsvariable,
und mit JPA müssten Flush und Clear zusätzlich mit der Mandantenregel
verträglich bleiben. JdbcTemplate macht den abgesetzten SQL-Satz sichtbar — und
der Satz lautet immer „Mandant + Filter“.

**Ausgeschlossen:**
- **Hibernate / JPA** — siehe oben.
- **Quarkus / Micronaut** — nicht gefordert; der Wechsel bringt keinen
  Vorteil gegenüber der Vorgabe.
- **Netty direkt** — Spring Boot genügt.

## Datenbank

**Entschieden:** PostgreSQL 16.

**Warum:** NFR-003 verlangt die Durchsetzung der Mandantentrennung **auf
Datenbankebene**. PostgreSQL liefert Row-Level-Security nativ, mit
transaktionslokalen Sitzungsvariablen und fail-closed-Verhalten. Dazu passt der
Datentyp `bytea` für die verschlüsselte Nutzlast (ADR-003) und die
partiellen Indizes für die Totenliste und die Worker-Abfrage.

**Ausgeschlossen:**
- **MySQL / MariaDB** — RLS müsste nachgebaut werden; das widerspricht der
  Anforderung.
- **MongoDB** — kein RLS, keine transaktionale Fremdschlüssel-Kaskade, und die
  Zustelllogik braucht atomare Mehr-Tabellen-Operationen.
- **Supabase / Cloud-eigene Datenbank** — der Dienst läuft containerisiert; die
  Datenbank ist eine Betriebsabhängigkeit, keine Fachentscheidung.

## Persistenz

**Entschieden:** Spring Data JDBC (`JdbcTemplate`) mit **vorbereiteten
Statements** und benannten Parametern. **Kein** dynamisch zusammengesetztes
SQL, **kein** String-Konkatenieren. Schema- und Datenänderungen über
**Flyway**; die Migration `V1__initial_schema.sql` ist eine Kopie des
kanonischen Schemas.

**Warum:** Jede Abfrage muss den Mandanten tragen und unter der RLS-Policy
laufen. Vorbereitete Statements machen das überprüfbar; dynamisches SQL würde
die Tür für den Fehler öffnen, den NFR-003 ausschließt.

**Ausgeschlossen:**
- **JPA / Hibernate** — siehe Framework.
- **Spring Data JDBC Repositories mit automatischer Query-Ableitung** — die
  Abfragen sind mandanten- und filterabhängig; explizite Statements sind
  lesbarer und prüfbarer.
- **MyBatis** — funktioniert, bringt aber keine Vorteile gegenüber
  `JdbcTemplate` bei dieser Größe.

## Auth

**Entschieden:** **Custom JWT** als Ressourcen-Server. Der Token trägt
`organisation_id` (Mandant) und die Rollen `developer`, `receiver_admin`,
`service_admin`. Prüfung gegen den Identity Provider (JWKS). Der Mandant
stammt **ausschließlich** aus dem Token; ein Mandantenparameter im Request
wird mit HTTP 422 abgelehnt (D-01).

**Warum:** Abschnitt 3 der Anforderung: *„Der Mandant steckt in der Identität,
er ist kein Filterparameter“*. Benutzer-, Rollen- und Mandantenverwaltung sind
ausdrücklich **nicht** Teil des Auftrags — die Identität kommt von außen.

**Ausgeschlossen:**
- **OAuth2-Authorization-Server im Dienst selbst** — Benutzerverwaltung ist nicht
  im Scope.
- **Session-Cookies** — der Dienst ist eine Maschinen-zu-Maschinen-Schnittstelle
  und wird horizontal skaliert.
- **API-Keys je Mandant** — der Mandant steckt in der Identität; ein
  Mandantenschlüssel wäre eine zweite Identität.

## Frontend

**Entschieden:** **Next.js 15 (App Router) auf React 19, TypeScript `strict`.**
Kein UI-Framework, keine Komponentenbibliothek — semantisches HTML plus
eigene, zugängliche Bausteine (`DataTable`, `StatusBadge`, `FieldError`).

**Warum:** Die geplanten Codepfade der Traceability-Matrix
(`src/app/endpoints/page.tsx` usw.) sind Next.js-Konventionen; der App Router
liefert die Seiten ohne zusätzliche Betriebsstufe. Barrierefreiheit (NFR-005)
ist eine **Bauregel**, keine Auditfrage: native Bedienelemente, echte
`<table>`-Struktur, sichtbarer Fokus, Status nie allein über Farbe (ADR-018).

**Ausgeschlossen:**
- **Material UI / Ant Design** — die Komponentenbibliotheken bringen eigene
  Stile und Kontrastwerte mit; sie müssten für AC-021-3 durchgeprüft und
  teilweise überschrieben werden.
- **Angular / Vue** — widerspricht Abschnitt 11.
- **Reines React + Vite** — die `src/app/…/page.tsx`-Pfade der Matrix sprechen
  für den App Router.

## Tests

**Entschieden:**
- **JUnit 5 + AssertJ** — Unit-Tests der Fachlogik, ohne Datenbank und ohne
  Netz.
- **Testcontainers (PostgreSQL 16)** — Integrationstests. Die Mandantentrennung
  ist **nur** gegen eine echte Datenbank belegbar (AC-022-1, AC-022-2); ein
  Mock beweist nichts.
- **WireMock / MockWebServer** — simulierte Empfänger für die Zustell- und
  Signaturtests (AC-009, AC-010, AC-014).
- **Vitest + Testing Library** — Unit-Tests der Oberfläche.
- **Playwright** — E2E-Tests der drei Seiten.
- **axe-core** — automatischer WCAG-2.1-AA-Scan als Pflicht-Gate (AC-021-2).
- **Vertragstests** — gegen `docs/api/openapi.yaml`; die Signatur- und das
  Ablehnungssignal werden **vertraglich** geprüft (AC-009-3, AC-014-2).

**Warum:** Die Anforderung verlangt in Abschnitt 12, dass zu **jeder**
Anforderung ein Testfall existiert und die Nachverfolgung Anforderung, Test und
Code ausweist. Der Nachweis der Mandantentrennung und der Verschlüsselung im
Ruhezustand ist nur gegen das echte System führbar.

**Ausgeschlossen:**
- **H2 / SQLite für Integrationstests** — kennt weder RLS noch `bytea` noch
  die Partial-Indizes; die Tests würden genau das nicht prüfen, worum es geht.
- **Mocking der Datenbank für Mandantentests** — ein Mock kann die
  Durchsetzung in der Datenbank nicht nachweisen.

## Deployment

**Entschieden:** Docker (Multi-Stage, gepinnte Basis-Images, **nicht**
privilegierter Benutzer, keine Secrets im Image) plus **docker-compose** für die
lokale Entwicklung. Eine Pipeline baut das Image und stellt **Staging und
Produktion getrennt** bereit, mit getrennten Konfigurationen und getrennten
Secrets (AC-023-5).

Ein Image, **zwei Startprofile**: `api` (HTTP) und `worker` (Zustellung).
Beide sind horizontal skalierbar; die Zustellung ist ein Hintergrundprozess
(ADR-006, ADR-013).

**Warum:** Abschnitt 11 verlangt Containerbetrieb mit getrenntem Staging und
Produktion. Die Trennung von API und Worker erlaubt, die Zustellung unabhängig
vom API-Traffic zu skalieren.

**Ausgeschlossen:**
- **Kubernetes** — nicht gefordert; die Anforderung verlangt Container und
  horizontale Skalierbarkeit, keine Orchestrierung. Container-Ready für
  später bleibt möglich (Health-Endpunkte, Konfiguration über ENV).
- **Deployment in der Anwendung** — widerspricht Abschnitt 11.

## LLM (optional)

**Entschieden: kein LLM.** EchoBox hat **keine** KI-Funktion — es ist ein
Zustell-Wrapper. Es gibt kein Modell, keinen Prompt und keinen LLM-Vertrag.

**Warum:** Die Anforderung enthält keinen einzigen KI-Bezug. Ein LLM hier
einzusetzen, wäre ein Feature ohne Bedarf — und ein zusätzlicher
Datenabfluss in ein Drittsystem (Frage O-05, Auftragsverarbeitung).

**Ausgeschlossen:**
- **Gemini / OpenAI** — kein Anwendungsfall.
- **Lokale Modelle** — ebenfalls kein Anwendungsfall.

## Datenschutz

- **EU-Hosting** — der Dienst läuft in der EU; die Zieler-URLs der Endpunkte
  können im Einzelfall außerhalb liegen, das ist eine **Empfängerentscheidung**
  und wird protokolliert (`target_host`).
- **Verschlüsselung im Ruhezustand** — AES-256-GCM (AEAD) für die Nutzlast und
  das Endpunkt-Secret; der Schlüssel liegt **außerhalb** der Datenbank, aus dem
  Secret-Store (ADR-003, NFR-004).
- **Aufbewahrung** — konfigurierbar, **kein vorbelegter Wert**, solange O-02
  offen ist; `deleted_at` markiert, ein Purge löscht endgültig (ADR-012,
  D-12).
- **Recht auf Löschung** — umgesetzt über die Aufbewahrung (Löschung von
  Ereignissen, Zustellläufen und Versuchen) sowie über die Löschung des
  Endpunkts; das Secret wird beim Löschen des Endpunkts mitgelöscht.
- **Protokollierbarkeit** — `audit_log` mit Identität, Rolle, Aktion und
  Zeitpunkt; **ohne** Nutzlastinhalt.
- **Keine Daten verlassen die EU** — durch den Betrieb gesichert (Region der
  Container und der Datenbank); die Zustellung an die Empfänger-URL ist eine
  vom Mandanten selbst gewählte Zieladresse und wird protokolliert.
- **Datenminimierung** — gespeichert wird nur, was für die Zustellung nötig ist:
  Nutzlast, Typ, Kennungen, Protokollmetadaten. Der Dienst ist **kein**
  Event-Store (R-10).

## Schema

`schema.sql` in diesem Verzeichnis ist **kanonisch**. Die Kopie unter
`docs/architecture/schema.sql` ist **byte-identisch** — ein Mirror-Test
(`SchemaMirrorIT`) erzwingt das. Siehe ADR-016 und
`docs/architecture/architecture.md` Abschnitt 3.

## Vertrag

`openapi.yaml` in diesem Verzeichnis ist ein **Verweis** auf
`docs/api/openapi.yaml` — **keine Kopie**. Der Vertrag wird an genau einer
Stelle gepflegt und ist **nach Schritt 4** eingefroren (ADR-016).
