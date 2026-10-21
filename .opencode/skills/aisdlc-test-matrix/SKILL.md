---
name: aisdlc-test-matrix
description: Steuert Phase 5: bildet jedes Akzeptanzkriterium auf Testebene, Testdatei und Testfall ab und schliesst die Luecken. Sagt, WANN welche Tests entstehen muessen. Nutze diesen Skill in Phase 5 (Test-Driven Setup) vor der Implementierung.
metadata:
  origin: eigenes Framework-Verfahren; Handwerk aus ECC/springboot-tdd + ECC/e2e-testing
---

## Wann

**Phase 5**, nach dem Vertrag (Schritt 2–4) und **vor** der ersten
Implementierungszeile.

## Das eiserne Gesetz

```text
KEIN KRITERIUM OHNE TESTFALL
```

Ein Kriterium ohne Testfall ist eine Luecke und blockiert Gate 1.

## Vorgehen

1. **Kriterien sammeln** — je Akzeptanzkriterium aus den Stories.
2. **Ebene zuweisen** —

   | Ebene | Fuer | Kontext |
   |---|---|---|
   | unit | eine Regel, keine Infrastruktur, keine Datenbank | ohne Framework-Kontext |
   | integration | Persistenz, Messaging, HTTP-Vertrag, Transaktionen | Testcontainer / echte Abhaengigkeit |
   | e2e | eine User-Journey ueber mehrere Komponenten | volle Kette |

3. **Datei benennen** — nach dem **Verhalten**, nicht nach der Implementierung:
   `test_task_status_transition`, nicht `test_service`.
4. **Negative Faelle ergaenzen** — zu jedem "das System muss verweigern" ein Test, der
   die Verweigerung belegt. Das wird vergessen.
5. **Invarianten** — Zustandsautomat, Mandantentrennung, Aufbewahrungsregeln bekommen
   eigene Faelle.
6. **Manuelle markieren** — was nicht automatisierbar ist, wird `manual` mit Grund.

## Output

`.agent/artifacts/test-matrix.md`:

| Kriterium | Ebene | Testdatei | Fall | Status |
|---|---|---|---|---|
| AC-003-1 Task erscheint in der Liste | integration | `tests/integration/test_task_create` | `test_create_task` | geplant |

Ausserdem maschinenlesbar in `docs/requirements/traceability_matrix.json`.

## Regeln

- Ein Kriterium, mindestens ein Fall. Ohne Fall ist es eine Luecke.
- Pflicht-Negativfaelle fuer jede Verweigerungsanforderung.
- Ein Test, der nichts ueber das Verhalten behauptet, ist keine Abdeckung.
- Ein Test, der nicht ausgefuehrt wurde, gilt als nicht bestanden.
- Danach `aisdlc-traceability-check` — der Check vergleicht die Matrix gegen die
  Dateisysteme, nicht gegen die Absicht.

## Stack-Skills

> Die Namen sind die der **Stack-Schicht** des Projekts (`opencode.jsonc`,
> `skills/`). Der Workflow dieses Skills gilt auch ohne sie — sie liefern nur
> das Handwerk, nicht das Verfahren.
| Wofuer | Skill |
|---|---|
| Spring-Boot-Testaufbau, Testcontainers, Slice-Tests | `springboot-tdd` |
| React-Komponententests | `react-testing` |
| JavaScript-/TypeScript-Teststrategien | `javascript-testing-patterns` |
| E2E mit Playwright | `e2e-testing` |
| Testinventar, Governance | `test-collaboration` |
