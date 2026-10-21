# Provenienz der vendorierten Skills

Spalte **KB** = Groesse der `SKILL.md` (das, was in den Kontext geladen wird).

Erzeugt von `.agent/tools/vendor_skills.py`. Jeder Skill stammt aus einer der
Agent-Bibliotheken; der Inhalt ist unverändert. Die Spalte **Schritt** sagt,
für welchen SDLC-Schritt der Skill gedacht ist — sie bestimmt, welche Skills
der Orchestrator einem Agenten freigibt.

| Skill | Schritt | Quelle | KB | Herkunft |
|---|---|---|---:|---|
| `api-design` | 2 | `ECC/skills` | 12 | 1 Quelle |
| `api-design-principles` | 2 | `wshobson-agents/plugins/backend-development/skills` | 3 | 1 Quelle |
| `openapi-spec-generation` | 2 | `wshobson-agents/plugins/documentation-generation/skills` | 1 | 1 Quelle |
| `contract-first` | 2 | `ECC/skills` | 9 | 1 Quelle |
| `database-migrations` | 2 | `ECC/skills` | 11 | 1 Quelle |
| `postgresql-table-design` | 2 | `wshobson-agents/plugins/database-design/skills` | 7 | 1 Quelle |
| `postgres-patterns` | 2 | `ECC/skills` | 3 | 1 Quelle |
| `architecture-decision-records` | 2 | `ECC/skills` | 7 | 2 Quellen, erste gewaehlt |
| `hexagonal-architecture` | 2 | `ECC/skills` | 11 | 1 Quelle |
| `security-requirement-extraction` | 2 | `wshobson-agents/plugins/security-scanning/skills` | 2 | 1 Quelle |
| `stride-analysis-patterns` | 2 | `wshobson-agents/plugins/security-scanning/skills` | 2 | 1 Quelle |
| `threat-mitigation-mapping` | 2 | `wshobson-agents/plugins/security-scanning/skills` | 3 | 1 Quelle |
| `code-review-excellence` | 4 | `wshobson-agents/plugins/developer-essentials/skills` | 6 | 1 Quelle |
| `springboot-tdd` | 5 | `ECC/skills` | 3 | 1 Quelle |
| `e2e-testing` | 5 | `ECC/skills` | 7 | 1 Quelle |
| `react-testing` | 5 | `ECC/skills` | 12 | 1 Quelle |
| `javascript-testing-patterns` | 5 | `wshobson-agents/plugins/javascript-typescript/skills` | 14 | 1 Quelle |
| `test-collaboration` | 5 | `ECC/skills` | 10 | 1 Quelle |
| `verification-loop` | 5 | `ECC/skills` | 2 | 1 Quelle |
| `springboot-patterns` | 6 | `ECC/skills` | 9 | 1 Quelle |
| `springboot-security` | 6 | `ECC/skills` | 7 | 1 Quelle |
| `java-coding-standards` | 6 | `ECC/skills` | 11 | 1 Quelle |
| `jpa-patterns` | 6 | `ECC/skills` | 4 | 1 Quelle |
| `error-handling` | 6 | `ECC/skills` | 10 | 1 Quelle |
| `sql-optimization-patterns` | 6 | `wshobson-agents/plugins/developer-essentials/skills` | 5 | 1 Quelle |
| `react-patterns` | 6 | `ECC/skills` | 11 | 1 Quelle |
| `react-state-management` | 6 | `wshobson-agents/plugins/frontend-mobile-development/skills` | 3 | 1 Quelle |
| `nextjs-app-router-patterns` | 6 | `wshobson-agents/plugins/frontend-mobile-development/skills` | 3 | 1 Quelle |
| `typescript-advanced-types` | 6 | `wshobson-agents/plugins/javascript-typescript/skills` | 7 | 1 Quelle |
| `tailwind-design-system` | 6 | `wshobson-agents/plugins/frontend-mobile-development/skills` | 5 | 1 Quelle |
| `frontend-a11y` | 6 | `ECC/skills` | 12 | 1 Quelle |
| `frontend-patterns` | 6 | `ECC/skills` | 15 | 1 Quelle |
| `modern-javascript-patterns` | 6 | `wshobson-agents/plugins/javascript-typescript/skills` | 2 | 1 Quelle |
| `langchain-architecture` | 6 | `wshobson-agents/plugins/llm-application-dev/skills` | 7 | 1 Quelle |
| `prompt-engineering-patterns` | 6 | `wshobson-agents/plugins/llm-application-dev/skills` | 5 | 1 Quelle |
| `llm-evaluation` | 6 | `wshobson-agents/plugins/llm-application-dev/skills` | 3 | 1 Quelle |
| `deployment-patterns` | 9 | `ECC/skills` | 10 | 1 Quelle |
| `github-actions-templates` | 9 | `wshobson-agents/plugins/cicd-automation/skills` | 6 | 1 Quelle |
| `incident-runbook-templates` | 9 | `wshobson-agents/plugins/incident-response/skills` | 5 | 1 Quelle |
| `slo-implementation` | 9 | `wshobson-agents/plugins/observability-monitoring/skills` | 6 | 1 Quelle |
| `canary-watch` | 9 | `ECC/skills` | 3 | 1 Quelle |
| `prometheus-configuration` | 9 | `wshobson-agents/plugins/observability-monitoring/skills` | 1 | 1 Quelle |
| `security-review` | * | `ECC/skills` | 12 | 1 Quelle |
| `secrets-management` | * | `wshobson-agents/plugins/cicd-automation/skills` | 7 | 1 Quelle |
| `conventional-commit-review` | * | `ECC/skills` | 4 | 1 Quelle |
| `git-workflow` | * | `ECC/skills` | 14 | 1 Quelle |
