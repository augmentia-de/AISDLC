---
name: aisdlc-glossary-adr
description: Schreibt das Fachglossar und die Architecture Decision Records. Sagt, WANN eine Entscheidung festgeschrieben wird und in welchem Format. Nutze diesen Skill in Phase 2 fuer jede Entscheidung mit Folgen und danach, wenn ein Begriff geschaerft wird.
metadata:
  origin: eigenes Framework-Verfahren; Handwerk aus ECC/architecture-decision-records
---

## Wann

- **Phase 2**, fuer jede Entscheidung, die Folgen hat.
- Jedes Mal, wenn ein Begriff eingefuehrt, eingegrenzt oder umbenannt wird.
- Nach einem Grill-Me, das eine Entscheidung geklaert hat.

## Zwei Artefakte

**GLOSSARY.md** — die Fachsprache, damit Code, Doku und Tests dieselben Woerter
benutzen. Je Begriff: was er **ist**, was er **nicht** ist, woher er kommt.

**ADRs** — warum entschieden wurde, damit kein spaeterer Agent die Entscheidung neu
aufrollt.

## Glossar

Je Begriff:

```markdown
## <Begriff>

<Definition in einem Satz>

- **Auspraegung**: <was genau dazugehoert>
- **Nicht**: <was ihm aehnlich ist, aber etwas anderes ist>
- **Quelle**: <Anforderung oder ADR>
```

Regeln: klein und praezise; ein Begriff pro Eintrag; **immer** die Negation
mitfuehren; auf die Quelle verweisen; Begriffe loeschen, die weder im Code noch in
den Anforderungen vorkommen.

## ADR

```markdown
# ADR-<ID>: <Titel>

## Kontext
<die Lage, ohne die Entscheidung>
## Entscheidung
<was entschieden wurde>
## Konsequenzen
<was das nach sich zieht - auch das Unangenehme>
## Alternativen
<die verworfenen Wege, mit dem Grund>
## Status
Angenommen | Vorgeschlagen | Ersetzt durch ADR-<ID>
```

Regeln: die **Entscheidung** festhalten, nicht die Diskussion; immer die
verworfenen Alternativen nennen; einen angenommenen ADR **nie** ueberschreiben —
sondern durch einen neuen ersetzen.

## Output

- `docs/architecture/GLOSSARY.md`
- `docs/adr/ADR-<ID>-<titel>.md`
- `.agent/artifacts/adr-index.md` — eine Zeile je ADR fuer die Gate-Vorlage

## Regeln

- Eine Entscheidung je ADR.
- Findet ein Agent die Begruendung einer Entscheidung nicht, fehlt der ADR.
- Begriffe, die im Code vorkommen, stehen im Glossar; sonst ist das ein Review-Befund.
- Nach dem Schreiben gilt `aisdlc-traceability-check` fuer die Zuordnung.

## Stack-Skills

> Die Namen sind die der **Stack-Schicht** des Projekts (`opencode.jsonc`,
> `skills/`). Der Workflow dieses Skills gilt auch ohne sie — sie liefern nur
> das Handwerk, nicht das Verfahren.
| Wofuer | Skill |
|---|---|
| ADR-Format, Index, Alternativen | `architecture-decision-records` |
| Abgrenzung Fach- vs. Technikbegriffe | `hexagonal-architecture` |
