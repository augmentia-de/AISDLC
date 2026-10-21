---
name: aisdlc-feature-scaffold
description: Steuert Phase 6: baut eine Feature-Slice end-to-end (Modell, Repository, Service, Route, Validierung, Test) nach den Konventionen, die bereits im Code stehen. Sagt, WIE eine Story geschnitten wird. Nutze diesen Skill in Phase 6 bei der Umsetzung einer Story.
metadata:
  origin: eigenes Framework-Verfahren; Handwerk aus ECC/contract-first + Stack-Skills
---

## Wann

**Phase 6**, je Story. Ein Aufruf = eine Story.

## Das eiserne Gesetz

```text
LIES ZUERST DEN VORHANDENEN CODE, DANN SCHREIBE
```

Der Skill fuegt keine zweite Architektur ein. Er findet das aehnlichste vorhandene
Feature und spiegelt dessen Struktur.

## Vorgehen

1. **Lesen vor dem Schreiben** — das naechstgelegene Feature suchen und seine
   Struktur uebernehmen: Modullayout, Benennung, Fehlerbehandlung, Teststil, Logging.
2. **Vertikaler Schnitt, nicht schichtenweise** — Modell, Repository, Service, Route,
   Test fuer **diese** Story. Nicht die halbe Model-Schicht und dann aufhoeren.
3. **Vertrag zuerst** — die Route-Form kommt aus `docs/api/openapi.yaml`, nie aus der
   Vorstellungskraft.
4. **Validierung am Rand** — Eingabevalidierung in der Request-Schicht,
   Geschaeftsregeln im Service, Invarianten im Modell.
5. **Tests mit dem Code** — der Schnitt ist ohne seine Tests nicht fertig.
6. **Traceability** — Story-ID und Akzeptanzkriterien im Testnamen oder Commit-Body.
7. **Berichten** — geaenderte Dateien, abgedeckte Kriterien, Testergebnis.

## Output

- Code unter `src/`
- Tests unter `tests/`
- Kurzer Bericht: Story-ID, abgedeckte Kriterien, Dateien, Testergebnis
- Aktualisierung der Traceability-Matrix fuer die abgedeckten Kriterien

## Regeln

- Eine Story pro Aufruf. Keine zweite Story im selben Lauf.
- Keine Schema-Aenderung ohne Migrationsplan (`aisdlc-migration-plan`).
- Keine neue Abhaengigkeit, ohne zu pruefen, ob das Projekt sie schon nutzt.
- Braucht die Story eine Entscheidung, die weder in der PRD noch im Vertrag steht:
  **eskalieren**, nicht erfinden.
- Der Vertrag wird nicht angefasst — ein Vertragsdefekt ist ein Befund fuer
  Schritt 2.

## Stack-Skills

> Die Namen sind die der **Stack-Schicht** des Projekts (`opencode.jsonc`,
> `skills/`). Der Workflow dieses Skills gilt auch ohne sie — sie liefern nur
> das Handwerk, nicht das Verfahren.
| Wofuer | Skill |
|---|---|
| Frontend und Backend aus einem Vertrag | `contract-first` |
| Java/Spring-Backend-Struktur | `springboot-patterns` |
| Java-Konventionen | `java-coding-standards` |
| React-Komponenten | `react-patterns` |
| Next.js App Router | `nextjs-app-router-patterns` |
| Typen | `typescript-advanced-types` |
