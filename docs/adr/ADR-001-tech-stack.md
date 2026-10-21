# ADR-001: Tech-Stack

## Kontext

Die Anforderung (Abschnitt 11) legt als Rahmenbedingung aus der Technik fest:
Backend **Java 21, Spring Boot, PostgreSQL**; Frontend **React, TypeScript**;
Zustellung im Hintergrundprozess; Betrieb über Container mit getrenntem Staging
und Produktion.

Offen ist die Frage, ob **Next.js** (Meta-Framework) oder ein **reines React**
mit eigenem Build verwendet wird. Das PRD (Abschnitt 12) nennt in den geplanten
Modulen `src/app/endpoints/page.tsx`, `src/app/deliveries/page.tsx`,
`src/app/dead-deliveries/page.tsx` — die `src/app`-Struktur und der Dateiname
`page.tsx` sind Next.js-Konventionen (App Router). Die Traceability-Matrix
übernimmt diese Pfade unverändert.

## Entscheidung

| Bereich | Entscheidung |
|---|---|
| Sprache / Laufzeit | **Java 21 (LTS)** |
| Framework | **Spring Boot 3.x** (Web, Validation, Security, JDBC, Actuator) |
| Datenbank | **PostgreSQL 16** |
| Persistenz | **Spring Data JDBC (JdbcTemplate)** — kein JPA/Hibernate |
| Auth | **Custom JWT** (`organisation_id`-Scope), Ressourcen-Server gegen den Identity Provider |
| HTTP-Client | Java `HttpClient` (Adapter hinter Port `DeliveryPort`) |
| Frontend | **Next.js 15 (App Router) auf React 19, TypeScript strict** |
| Oberflächen-Tests | Vitest (Unit), Playwright (E2E), axe (Barrierefreiheit) |
| Backend-Tests | JUnit 5, Testcontainers (PostgreSQL 16), WireMock (Empfängersimulation) |
| Build | Maven, Multi-Modul ist **nicht** nötig — ein Backend-Modul, ein Frontend-Modul |
| Container | Docker (Multi-Stage, non-root, gepinnte Basis-Images) |
| LLM | **kein** — der Dienst hat keine KI-Funktion |

Diese Entscheidung ist die **Quelle** für `.agent/knowledge/stack.md`.

## Konsequenzen

- Die geplanten Codepfade der Traceability-Matrix (`src/main/java/com/echobox/…`,
  `src/main/frontend/src/app/…/page.tsx`) bleiben unverändert gültig.
- Der Fremdkörper Next.js bringt Server-Rendering mit; die Oberfläche bleibt
  trotzdem eine reine API-Konsumentin — keine fachliche Logik im Frontend.
- Spring Data JDBC statt JPA: der Mandantenkontext ist eine transaktionslokale
  Sitzungsvariable; mit JPA müssten Flush und Clear zusätzlich mit der
  Mandantenregel verträglich bleiben. JdbcTemplate macht den abgesetzten SQL-Satz
  sichtbar — und der Satz lautet immer „Mandant + Filter“.
- Java 21 bringt `record`, Pattern Matching und virtuelle Threads mit — Letzteres
  ist für den nebenläufigen Worker nützlich, aber kein Grund, die Zustellung in
  Threads zu erfinden: die Zustellung ist ein **Hintergrundprozess** (ADR-006).
- **Kein LLM:** Der Dienst hat keine KI-Funktion. Der Stack-Schicht bleibt die
  Möglichkeit erhalten, sie in einer anderen Stack-Ebene zu ergänzen.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **FastAPI / Python** | Widerspricht der Rahmenbedingung aus Abschnitt 11 der Anforderung. |
| **Spring Boot 2.x** | End of Life, kein Java-21-Support. |
| **JPA / Hibernate** | Siehe oben — der Mandantenkontext und die Sichtbarkeit der Queries wiegen schwerer als die Bequemlichkeit des ORM. |
| **Reines React + Vite** (ohne Next.js) | Die `src/app/…/page.tsx`-Pfade der Traceability-Matrix sprechen für den App Router; der Next.js-App-Router liefert außerdem die serverseitige Auslieferung ohne zusätzliche Betriebsstufe. |
| **MySQL** | RLS (NFR-003) ist in PostgreSQL nativ vorhanden; in MySQL müsste die Trennung nachgebaut werden — das widerspricht der Anforderung, sie **auf Datenbankebene** zu erzwingen. |
| **Kafka / RabbitMQ** | Ausdrücklich ausgeschlossen (Abschnitt 2 der Anforderung). |
| **Node.js / NestJS für das Backend** | Widerspricht Abschnitt 11. |

## Status

**Angenommen** — bei Gate 1 am 2026-10-21 bestätigt.

> Hinweis: Die Festlegungen D-01 bis D-15 aus dem PRD sind Vorschläge der
> Anforderungserhebung, keine Fachbereichs-Festlegungen. Sie werden bei
> Gate 1 bestätigt.
