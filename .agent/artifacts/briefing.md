# Steckbrief — Schritt 2 (Konzept & Architektur)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, offene Punkte.
> Keine Auszüge, keine Herleitungen. **Die Dateien sind die Wahrheit, nicht dieser
> Steckbrief.** Bei Widerspruch gewinnen die Dateien; der Steckbrief wird im nächsten
> Commit korrigiert.

| Feld | Wert |
|---|---|
| Stand | Schritt 1 abgeschlossen — Commit `6538d9e` `docs(sdlc-1): PRD, User Stories und Traceability-Matrix` |
| Nächster Schritt | 2 (Konzept & Architektur), Agent `aisdlc-architecture` |
| Gate | **GATE 1** nach Schritt 2 — kein Anwendungscode vor menschlicher Freigabe |
| Vertrag | `docs/api/openapi.yaml` — existiert noch nicht; wird in Schritt 2 erstellt und **erst nach Schritt 4 eingefroren** |
| Schema | `.agent/knowledge/schema.sql` (kanonisch) — noch Template |
| Stack | `.agent/knowledge/stack.md` — noch Template; laut Anforderung Abschnitt 11: Java 21, Spring Boot, PostgreSQL, React/TypeScript |
| Codebasis | `src/` existiert nicht — Neuaufbau, keine Altlast |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/02-architecture.md` | dieser Schritt, mit Agent-&-Skill-Tabelle und Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/02_orchestrator_architecture.md` | dein Systemprompt |
| 5 | `docs/Anforderung.md` | der Roh-Input (18 FR + 6 NFR, unverändert) |
| 6 | `docs/requirements/PRD.md` | Vision, Rollen, Scope, MoSCoW, Festlegungen D-01…D-15, offene Punkte |
| 7 | `docs/requirements/traceability_matrix.json` | Anforderung → Story → Test → Code; offene Punkte O-01…O-07 |
| 8 | `.agent/artifacts/risk-register.md` | Risiken und Gegenmaßnahmen |
| 9 | `.agent/artifacts/requirements.md` | kompakter Artefaktkatalog |

Bei Bedarf, nicht zwingend: `docs/requirements/stories/US-0xx.json` (23),
`docs/requirements/features/US-0xx.feature` (23), `.agent/artifacts/user-stories.md`.

## Entscheidungen, die gelten

| ID | Entscheidung | Bindet |
|---|---|---|
| D-01 | Mandant stammt ausschließlich aus der Identität; ein Mandantenparameter wird mit HTTP 422 abgelehnt | Vertrag, Datenmodell |
| D-02 | Fremde Ressourcen werden mit HTTP 404 beantwortet (Existenz bleibt verborgen) | Vertrag, Fehlersemantik |
| D-03 | Pause: keine Zustellung, kein Zwischenspeichern, kein Nachliefern; offene Wiederholung wird abgebrochen | Zustellzustandsmodell |
| D-04 | Ereignis ohne Abnehmer wird angenommen und sichtbar als „kein Abnehmer“ protokolliert | Zustellzustandsmodell, Protokoll |
| D-05 | Idempotenz je (Mandant, Idempotenzschlüssel); abweichende Nutzlast unter gleichem Schlüssel → HTTP 409 | Datenmodell, Vertrag |
| D-06 | Drei Kennungen: Ereignis-ID, Zustelllauf-ID, Versuchs-ID | Vertrag, Zustellmodell |
| D-07 | Signatur über Zeitstempel (UTC) + Nutzlastbytes; Verfahren und Header-Name werden im Vertrag eingefroren | Vertrag, Krypto |
| D-08 | Wiederholung bei 5xx, Netzwerkfehler, 408, 429; kein Wiederholen bei 2xx; Ablehnungssignal + übrige 4xx → sofort aufgeben | Zustellzustandsmodell |
| D-09 | Backoff konfigurierbar, Verdopplung, mit Streuung | Worker-Konfiguration |
| D-10 | Annahme und Zustellung getrennt; Zustellung im Hintergrundprozess | Architektur, NFR-002 |
| D-11 | Protokollsuche nur über Metadaten, keine Suche nach Nutzlastinhalt | Protokoll, Suchindex |
| D-12 | Aufbewahrungsfrist konfigurierbar; ohne O-02 kein Wert vorbelegt | Betrieb |
| D-13 | Die 1-MB-Grenze gilt für die eingelieferten Bytes (bei vorverschlüsselter Einlieferung für die Chiffre) | Vertrag |
| D-14 | Rate-Limit-Mechanismus gebaut und konfigurierbar; ohne O-01 kein Grenzwert vorbelegt, nur Zähler erhoben | Betrieb |
| D-15 | Replay bis zur Entscheidung nur durch den Empfänger-Admin des Endpunkts | Rollenmatrix, Vertrag |

Alle 15 Festlegungen sind **Vorschläge der Anforderungserhebung**, keine Fachbereichs-Festlegungen.
Bei Gate 1 brauchen sie die Bestätigung des Menschen.

## Offene Punkte

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-01 | Rate-Limit je Endpunkt: welche Rate, blockieren oder verschieben? | Fachbereich | Gate 1 |
| O-02 | Aufbewahrung: welche Daten, welche Frist, ab welchem Zeitpunkt? | Fachbereich + Datenschutz | Gate 1 |
| O-03 | Wer darf ein totes Ereignis erneut auslösen? (Widerspruch FR-024 vs. Rollentabelle) | Fachbereich | Gate 1 |
| O-04 | Verschlüsselung im Ruhezustand: Verfahren und Durchsuchbarkeit des Protokolls | Fachbereich + Datenschutz (Verfahren: Architektur) | Gate 1 |
| O-05 | Vertrag über die Auftragsverarbeitung (AVV bei Fremddaten) | Fachbereich + Recht | Gate 2 |
| O-06 | Anzahl der Zustellversuche und Backoff-Parameter (Zahlenlücke in FR-022/FR-023) | Fachbereich | Gate 1 |
| O-07 | Zeitlimit je Zustellversuch | Architektur | Gate 1 |

**ARCHITECTURAL_BLOCKER-Kandidaten für diesen Schritt** (hier zu entscheiden, blockieren
Schritt 2 nicht): Verschlüsselungsverfahren und Schlüsselverwaltung (O-04), Durchsetzung
der Mandantentrennung auf Datenbankebene (NFR-003), Zeitlimit je Zustellversuch (O-07),
Zustandsmodell der Zustellung, Übernahme des Zustellprozesses nach Absturz.

## Was du in diesem Schritt **nicht** tust

- **Keine Festlegung der offenen Punkte O-01…O-07.** Sie gehören dem Fachbereich. Trage sie
  als Vorschlag in die Architekturentscheidungen ein, entscheide sie nicht.
- **Keine Anwendungscode-Dateien.** `src/` bleibt unberührt; Code entsteht in Schritt 6.
- **Keine Änderung am Roh-Input** `docs/Anforderung.md`.
- **Noch kein Vertrags-Freeze.** Der Vertrag wird in Schritt 2 erstellt und erst nach
  Schritt 4 eingefroren.
- **Keine Änderung an `.agent/knowledge/schema.sql` oder `docs/architecture/schema.sql`**
  ohne den jeweils anderen Pfad im selben Schritt (Spiegel-Regel, ADR-019).
- **Keine Änderung an `docs/requirements/`** — das ist der eingefrorene Ergebnisbereich
  von Schritt 1.

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Story-Übersicht (menschlich lesbar) | `.agent/artifacts/user-stories.md` |
