---
name: aisdlc-openapi-first
description: Contract-First-Arbeit - der API-Vertrag ist die Single Source of Truth. Steuert, WANN und WELCHE Artefakte entstehen; das Handwerk liefert der Stack-Skill (api-design). Nutze diesen Skill in Phase 2 (Konzept & Architektur) beim Entwurf des Vertrags und in Phase 6 bei der Umsetzung gegen ihn.
metadata:
  origin: eigenes Framework-Verfahren; Handwerk aus ECC/api-design + ECC/api-design-principles
---

## Wann

- **Phase 2**, beim Entwurf des Vertrags
- **Phase 6**, beim Bauen von Routen, DTOs und Handlern gegen den Vertrag

Nicht in Phase 6 den Vertrag ändern. Was sich nicht ausdrücken lässt, geht zurück
nach Phase 2.

## Zwei Schichten — nicht verwechseln

| Schicht | Zuständig | Liefert |
|---|---|---|
| **Verfahren** (dieser Skill) | das Framework | **wann**, **welche Artefakte**, **welche Prüfungen** |
| **Handwerk** | `api-design`, `api-design-principles` (vendoriert) | **wie** man REST-Entwürfe, Statuscodes, Fehlerbodies und Versionierung baut |

Dieser Skill ersetzt das Handwerk nicht. Er sagt dem Agenten, **welchen**
Stack-Skill er für **welchen** Teil des Vertrags lädt, und hält fest, was am Ende
als Artefakt vorliegen muss.

## Das eiserne Gesetz

```text
KEIN ENDPOINT OHNE VOLLSTAENDIGE OPERATION IM VERTRAG
```

Ein Endpoint ohne dokumentierte Fehlerantwort, ohne Autorisierungsangabe und ohne
Beschreibung ist kein Vertrag, sondern eine Vermutung.

## Der Vertrag

`docs/api/openapi.yaml` ist die Single Source of Truth. Alles andere folgt daraus:
Schemas, DTOs, Routen, Client-Aufrufe, Tests.

| Feld | Regel |
|---|---|
| `operationId` | stabil, camelCase, eindeutig im gesamten Vertrag |
| `summary` | eine Zeile, was die Operation tut |
| `description` | Randbedingungen, Seiteneffekte, Idempotenz |
| `parameters` | inkl. Validierung (min/max/format/enum) und Herkunft (path/query/header) |
| `requestBody` | Pflichtfelder, Formate, Beispiele |
| `responses` | mindestens Erfolg **und** jede Fehlerantwort |
| `security` | welche Rolle das braucht — oder ausdrücklich `none` |
| `tags` | Feature-Bereich |

**Das Handwerk dafür:** `api-design` (Resource-Naming, Statuscodes, Pagination,
Fehlerbodies, Versionierung, Rate-Limiting) und `api-design-principles` (die
allgemeinen REST-Prinzipien).

## Vorgehen

### 1. Anforderungen extrahieren

Aus `docs/requirements/PRD.md`, `.agent/artifacts/user-stories.md` und
`docs/requirements/features/*.feature` jede Story auf ihre Operation abbilden. Eine
Story ohne Operation und eine Operation ohne Story sind beides Befunde.

### 2. Gegen das Schema prüfen

Jedes Feld in Request und Response muss im Datenbankschema existieren, mit gleichem
Typ und gleicher Nullbarkeit. **Abweichungen melden, das Schema nicht stillschweigend
ändern.** Wenn das Schema falsch ist, ist das ein eigener Befund mit eigenem ADR.

**Das Handwerk dafür:** `database-migrations`, `postgresql-table-design`.

### 3. Reihenfolge bestimmen

Nach Abhängigkeit sortieren: geteilte Schemas zuerst, dann Endpunkte ohne
Abhängigkeit, dann die aggregierenden. So entsteht kein halbfertiger Zwischenstand.

### 4. Contract-Tests schreiben

Je Operation: Statuscode, Pflichtfelder, Fehlerformat. **Antwort mit
undokumentierten Feldern ist ein Fehler** — der Test schlägt dann fehl, bis Vertrag
und Implementierung übereinstimmen.

### 5. Implementieren

Route, Validierung, Service-Aufruf, Fehlermapping, dann der Test. In dieser
Reihenfolge. Validierung am Rand (Request), Geschäftsregeln im Service, Invarianten
im Modell.

## Fehlerformat — eine Form für alle

```json
{
  "type": "https://errors.example.com/validation-failed",
  "title": "Validation failed",
  "status": 422,
  "detail": "title must not be empty",
  "instance": "/tasks"
}
```

| Status | Wann |
|---|---|
| 400 | syntaktisch fehlerhafte Anfrage |
| 401 | nicht angemeldet |
| 403 | angemeldet, aber nicht berechtigt |
| 404 | Ressource existiert nicht **oder** ist für diese Organisation nicht sichtbar |
| 409 | Konflikt mit dem aktuellen Zustand |
| 422 | semantisch ungültig (Validierung) |
| 429 | Rate-Limit überschritten |
| 500 | unerwarteter Fehler — kein Stacktrace, kein internes Detail |

Die Detailmeldung sagt, **was** zu tun ist, nicht nur, was schiefging.

## Abgleich mit der Traceability

Nach dem Schreiben des Vertrags und nach dem Schreiben der Tests gehört der Abgleich
der Kette Anforderung → Story → Feature → Test → Code:

```bash
python3 .agent/tools/traceability_check.py
```

**Eine gefüllte Matrix ist kein Beleg** — ein Eintrag, der auf einen Test verweist,
den niemand geschrieben hat, zählt als Lücke. Siehe `aisdlc-traceability-check`.

## Regeln

- Pagination, Filterung und Sortierung sind **Teil des Vertrags**, keine
  Implementierungsdetails. Ohne sie in der Liste ist die Liste nicht reparierbar.
- Mandantentrennung gehört in den Vertrag: jeder Lese- und Schreibpfad trägt
  `organisation_id`-Scope. Fehlt er, ist das ein Befund, kein Detail.
- Der Vertrag wird **nach Schritt 4** eingefroren (Ende des Stresstests), nicht nach
  Gate 1. Gate 1 nach Schritt 2 prüft die *Richtung* — der Vertrag ist dort noch ein
  Entwurf. Im ersten Volllauf fand der Stresstest 13 Vertragsdefekte, alle **nach**
  der Einfrierung, und jeder davon hat Schema, Code und Tests invalidiert.
  Eine Abweichung nach dem Freeze braucht einen **Rücksprung zu Schritt 2** — keine
  stille Änderung im Code, keine neue Revision im Vorbeigehen.
- Ein Feld ohne Zweck und eine Operation ohne Zweck werden entfernt, nicht dokumentiert.
- Fehlerantworten sind Pflicht. Ein Vertrag ohne Fehlerformat ist unvollständig.

## Output

- `docs/api/openapi.yaml` — der eingefrorene Vertrag
- `.agent/artifacts/api-review.md` — Schema-Abweichungen und offene Fragen
- Zuordnung Story → Operation, mit Lücken als Liste (nicht als Prozentzahl)

## Stack-Skills

> Die Namen sind die der **Stack-Schicht** des Projekts (`opencode.jsonc`,
> `skills/`). Der Workflow dieses Skills gilt auch ohne sie — sie liefern nur
> das Handwerk, nicht das Verfahren.
| Wofür | Skill |
|---|---|
| REST-Entwurf, Statuscodes, Fehlerbodies, Versionierung | `api-design` |
| REST-Prinzipien, GraphQL-vs-REST | `api-design-principles` |
| Datenbank-Typen, Migrationen | `database-migrations`, `postgresql-table-design` |
| Vertrag als Quelle für beide Seiten | `contract-first` |
