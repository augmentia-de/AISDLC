# Tech Stack — <Projektname>

> **Diese Datei ist ein Template.** Sie wird in Phase 2 (Konzept & Architektur)
> mit der **Entscheidung** dieses Projekts gefüllt. Die Entscheidung wird in
> `docs/adr/ADR-001-tech-stack.md` dokumentiert; diese Datei ist der Einstiegspunkt
> für Agenten, die den Stack des Projekts brauchen.
>
> **Nicht** einfach die Default-Liste aus `AGENTS.md` stehen lassen — genau das war
> im Referenzlauf die Ursache dafür, dass der Architektur-Agent FastAPI baute, obwohl
> der Kunde eine JVM-Bibliothek verlangt hatte.

## Runtime und Sprache

**Entschieden:** <z. B. Java 21 (LTS)>
**Warum:** <die entscheidende Begründung — meist eine Anforderung aus dem Input>

**Ausgeschlossen:**
- <Stack, der nicht in Frage kam, und warum>

## Framework und Bibliotheken

**Entschieden:** <z. B. Spring Boot 3.x (Web, Security, Validation, Persistenz)>
**Warum:** <Begründung>

## Datenbank

**Entschieden:** <z. B. PostgreSQL 16>
**Warum:** <Begründung — Relationalität, RLS, DSGVO>

## Persistenz

**Entschieden:** <z. B. Spring Data JPA / JDBC, niemals dynamisch zusammengesetztes SQL>

## Auth

**Entschieden:** <z. B. Custom JWT, `organisation_id`-Scope>
**Warum:** <Begründung>

## Frontend

**Entschieden:** <z. B. React 19 in Next.js 15 (App Router), TypeScript strict>
**Warum:** <Begründung>

## Tests

**Entschieden:** <z. B. JUnit 5, Testcontainers, Vitest, Playwright>
**Warum:** <Begründung — oft: die Mandantentrennung lässt sich nur gegen eine echte
Datenbank belegen, nicht gegen einen Mock>

## Deployment

**Entschieden:** <z. B. Docker + GitHub Actions → Cloud Run (europe-west3)>
**Warum:** <Begründung — reproduzierbare Builds, EU-Hosting>

## LLM (optional)

**Entschieden:** <Modell(e) und Konfiguration>

## Datenschutz

- EU-Hosting (<Region>)
- Aufbewahrungsfristen: <Angabe>
- Recht auf Löschung: <Mechanismus>
- Keine Daten verlassen die EU

## Schema

`schema.sql` in diesem Verzeichnis ist **kanonisch**. Eine Kopie im Commit-Scope der
Architekturphase ist mit dem kanonischen Pfad byte-identisch — ein Mirror-Test erzwingt
das. Siehe die Schema-Entscheidung im zugehörigen ADR.

## Vertrag

`openapi.yaml` in diesem Verzeichnis ist ein **Verweis** auf `docs/api/openapi.yaml`,
keine Kopie. Der Vertrag wird an genau einer Stelle gepflegt.
