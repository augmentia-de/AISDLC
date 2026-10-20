---
name: aisdlc-openapi-first
description: Contract-First-Arbeit - OpenAPI-Spezifikation als Single Source of Truth. Operationen vollstaendig spezifizieren, Feldtypen gegen das Schema pruefen, Stubs/DTOs/Routen daraus ableiten, Contract-Tests schreiben und den Vertrag waehrend der Implementierung nicht mehr aendern. Nutze diesen Skill in Phase 2 (Konzept & Architektur) beim Entwurf des API-Contracts und in Phase 6 (Code-Generation) bei der Umsetzung gegen den Vertrag.
metadata:
  origin: Entwurf aus example/skills-to-create/aisdlc-openapi-first, uebersetzt ins Skill-Format der installierten Skills
---

## Wann

- Phase 2: wenn der API-Contract entworfen oder geprueft wird
- Phase 6: wenn Routen, DTOs und Handler gegen den eingefrorenen Vertrag gebaut werden

Nicht in Phase 6 den Vertrag aendern. Was sich nicht ausdruecken laesst, geht zurueck
nach Phase 2.

## Das eiserne Gesetz

```text
KEIN ENDPOINT OHNE VOLLSTAENDIGE OPERATION IM VERTRAG
```

Ein Endpoint ohne dokumentierte Fehlerantwort, ohne Autorisierungsangabe und ohne
Beschreibung ist kein Vertrag, sondern eine Vermutung.

## Der Vertrag

`docs/api/openapi.yaml` ist die Single Source of Truth. Alles andere folgt daraus:
Schemas, DTOs, Routen, Client-Aufrufe, Tests.

Jede Operation braucht **alle** diese Angaben:

| Feld | Regel |
|---|---|
| `operationId` | stabil, camelCase, eindeutig im gesamten Vertrag |
| `summary` | eine Zeile, was die Operation tut |
| `description` | Randbedingungen, Seiteneffekte, Idempotenz |
| `parameters` | inkl. Validierung (min/max/format/enum) und Herkunft (path/query/header) |
| `requestBody` | Pflichtfelder, Formate, Beispiele |
| `responses` | mindestens Erfolg **und** jede Fehlerantwort |
| `security` | welche Rolle das braucht - oder ausdruecklich `none` |
| `tags` | Feature-Bereich |

## Fehlerformat - eine Form fuer alle

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
| 404 | Ressource existiert nicht **oder** ist fuer diese Organisation nicht sichtbar |
| 409 | Konflikt mit dem aktuellen Zustand |
| 422 | semantisch ungueltig (Validierung) |
| 429 | Rate-Limit ueberschritten |
| 500 | unerwarteter Fehler - kein Stacktrace, kein internes Detail |

Die Detailmeldung sagt, **was** zu tun ist, nicht nur, was schiefging.

## Vorgehen

### 1. Anforderungen extrahieren

Aus `docs/requirements/PRD.md`, `.agent/artifacts/user-stories.md` und
`docs/requirements/features/*.feature` jede Story auf ihre Operation abbilden. Eine
Story ohne Operation und eine Operation ohne Story sind beides Befunde.

### 2. Gegen das Schema pruefen

Jedes Feld in Request und Response muss im Datenbankschema existieren, mit gleichem
Typ und gleicher Nullbarkeit. **Abweichungen melden, das Schema nicht stillschweigend
aendern.** Wenn das Schema falsch ist, ist das ein eigener Befund mit eigenem ADR.

### 3. Reihenfolge bestimmen

Nach Abhaengigkeit sortieren: geteilte Schemas zuerst, dann Endpunkte ohne Abhaengigkeit,
dann die aggregierenden. So entsteht kein halbfertiger Zwischenstand.

### 4. Contract-Tests schreiben

Je Operation: Statuscode, Pflichtfelder, Fehlerformat. **Antwort mit undokumentierten
Feldern ist ein Fehler** - der Test schlaegt dann fehl, bis der Vertrag und die
Implementierung uebereinstimmen.

### 5. Implementieren

Route, Validierung, Service-Aufruf, Fehlermapping, dann der Test. In dieser Reihenfolge.
Validierung am Rand (Request), Geschaeftsregeln im Service, Invarianten im Modell.

## Regeln

- Pagination, Filterung und Sortierung sind **Teil des Vertrags**, keine
  Implementierungsdetails. Ohne sie in der Liste ist die Liste nicht reparierbar.
- Mandantentrennung gehoert in den Vertrag: jeder Lese- und Schreibpfad traegt
  `organisation_id`-Scope. Fehlt er, ist das ein Befund, kein Detail.
- Der Vertrag wird nach der Freigabe (Gate 1) eingefroren. Abweichungen brauchen eine
  neue Revision der Datei, keine stille Aenderung im Code.
- Ein Feld ohne Zweck und eine Operation ohne Zweck werden entfernt, nicht dokumentiert.
- Fehlerantworten sind Pflicht. Ein Vertrag ohne Fehlerformat ist unvollstaendig.

## Output

- `docs/api/openapi.yaml` - der eingefrorene Vertrag
- `.agent/artifacts/api-review.md` - Schema-Abweichungen und offene Fragen
- Zuordnung Story -> Operation, mit Luecken als Liste (nicht als Prozentzahl)

## Offene Punkte, die nach Phase 2 gehoeren

- Datenbankschema unter `.agent/knowledge/schema.sql` - von dort kommen Typen und
  Indizes, die der Vertrag spiegelt. Wird der Vertrag vor dem Schema geschrieben,
  braucht es eine zweite Abgleich-Runde.
- Glossar und ADR fuer Begriffe und Entscheidungen, die im Vertrag stillschweigend
  fallen (z. B. `aisdlc-glossary-adr`).
