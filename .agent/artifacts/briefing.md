# Steckbrief — Schritt 2 (Korrektur: Uebergangstabelle des Domänenmodells, A-6/B-17)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Ruecksprung Schritt 6/7 abgeschlossen — Commit `d135cda` `fix(sdlc-6): Security-Befunde S-19 bis S-27 und Coverage-Gate behoben`; davor Schritt 8 `1ba4160`, Schritt 1 (Nachzug) `825656a`, Schritt 7 `bd15f19`, Schritt 6 `e9e1bc1` |
| Dieser Schritt | 2 (Konzept & Architektur) — **eine** Korrektur: die Uebergangstabelle des Domänenmodells |
| Danach | Schritt 8 (erneute Pruefung) → **GATE 2** → Schritt 9 |
| **Vertrag** | `docs/api/openapi.yaml` — **EINGEFROREN seit `8627bb6`**, im Ruecksprung **nicht** angefasst. Diese Korrektur fasst den Vertrag **nicht** an. |
| Schema | `.agent/knowledge/schema.sql` + Spiegel + Migration — alle drei byte-identisch; **nicht** anfassen |
| Code | `src/`, `tests/` — 9/9 Security-Befunde behoben, alle Tests gruen, Coverage-Gate erzwungen |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/02-architecture.md` | dieser Schritt, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/02_orchestrator_architecture.md` | dein Systemprompt |
| 5 | `docs/architecture/domain_model.md` | **§3.3 — die zu korrigierende Uebergangstabelle** |
| 6 | `docs/execution/step8-nacharbeit-security.md` **§4.2** | der Beleg: die Tabelle, die zu dokumentieren ist, und die Begruendung |
| 7 | `src/main/java/com/echobox/delivery/DeliveryStateMachine.java` | die **Code**-Wahrheit (`TRANSITIONS`, `onRateLimited`) |
| 8 | `docs/adr/ADR-010-rate-limit.md` | der Rate-Limit (FR-033) — der Grund fuer `-> delayed` aus jedem offenen Zustand |

## Die Korrektur (A-6 / B-17)
Der Befund: `DeliveryStateMachine.TRANSITIONS` erlaubte `delayed` nur aus `failed`; der
Ratenbegrenzungs-Pfad setzt **jeden** offenen Zustand auf `delayed`. Der **Code** ist im Ruecksprung
angeglichen (`pending -> delayed`, `scheduled -> delayed`; `onRateLimited` wird aus der Tabelle
**abgeleitet**). Die **Dokumentation** (dein Bereich) ist nachzuziehen.

Die zu dokumentierende Tabelle (aus §4.2 des Belegs):
```
pending   -> scheduled, cancelled, no_subscriber, delayed
scheduled -> sent, rejected, failed, cancelled, delayed
failed    -> scheduled, delayed, abandoned, cancelled
delayed   -> scheduled, abandoned, cancelled
```
- `delayed -> delayed` ist **kein** Uebergang (der Zustand aendert sich nicht).
- Begruendung fuer `pending -> delayed` und `scheduled -> delayed`: das Endpunkt-Limit (FR-033)
  wirkt auch auf den **ersten** Versuch eines frischen Laufs — sonst kaeme ein Endpunkt in der
  ersten Minute ueber das Limit hinaus. Vertrag und ADR-010 nennen den Zustand `delayed` ohne
  Einschraenkung des Ausgangszustands.

## Was du tust
1. `docs/architecture/domain_model.md` **§3.3**: die Uebergangstabelle auf den Stand bringen
   (inkl. der Begruendung und des Hinweises, dass `delayed -> delayed` kein Uebergang ist).
2. Pruefe, ob **weitere** Stellen im `docs/architecture/` (oder in `docs/adr/`) die alte Tabelle oder
   eine alte Zustandsbeschreibung wiedergeben — und ziehe sie nach. **Nur** soweit die Korrektur
   es erfordert.
3. **Pruefe** die Konsistenz mit dem Code: `DeliveryStateMachine.TRANSITIONS` (Quelle der Wahrheit),
   die `DeliveryStatus`-Werte und die `CHECK`-Constraints im kanonischen Schema. Stimmen sie mit der
   Tabelle ueberein? Ein Widerspruch ist ein **Befund**, den du meldest — **nicht** eine
   Codeaenderung.
4. Wenn die Korrektur eine **ADR** beruehrt (z. B. ADR-006 Zustellprozess oder ADR-010 Rate-Limit
   nennen die Uebergaenge), ziehe **die** nach — im selben Commit.

## Was du **nicht** tust
- **Keinen Anwendungscode.** `src/` und `tests/` bleiben unberuehrt.
- **Den Vertrag nicht aendern.** Er ist eingefroren und wurde im Ruecksprung nicht angefasst.
- **Kein Schema-Anfassen** (beide Diffs leer halten).
- **Keine Aenderung** an `docs/requirements/`, `docs/api/`, `.agent/knowledge/`, `docs/Anforderung.md`.
- **Keine neue Festlegung** der bestaetigten Entscheidungen (D-01…D-15, ADR-001…ADR-018, F-01…F-04,
  E-01…E-11, E-13, V-26…V-27, V-39…V-44, V-48…V-49).
- Kein `git push`. Keine Ausgabe-Kommandos (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Commit — genau einer, Befehl aus der Workflow-Datei
```bash
mkdir -p docs/architecture/ docs/adr/ .agent/artifacts/
git reset -q
git add -A -- docs/architecture/ docs/adr/ .agent/artifacts/
git commit -m "docs(sdlc-2): Uebergangstabelle des Domänenmodells angeglichen" \
  -m "Schritt 2 - Konzept & Architektur (Korrektur A-6/B-17) | Agent: aisdlc-architecture | Artefakte: docs/architecture/, docs/adr/, .agent/artifacts/"
```

## Antwortformat — genau so, die drei Zeilen ZUERST
Danach hoechstens zwei optionalen Zeilen (Pruefergebnis). Keine Herleitung, keine Auszuege.

```
STATUS: done | done_with_open_points | blocked
COMMIT: <hash> <subject>
OFFENE PUNKTE: <kurz, mit A-xx/S-xx> | -
```
