# Steckbrief — Schritt 1 (Anforderungserhebung)

> Dieser Steckbrief ist der Einstiegspunkt für den nächsten Schritt. Er ist ein Zeiger:
> Status, Commit, offene Punkte — keine Auszüge, keine Herleitungen. **Die Dateien sind
> die Wahrheit, nicht dieser Steckbrief.** Bei Widerspruch gewinnen die Dateien, und der
> Steckbrief wird im nächsten Commit korrigiert.

| Feld | Wert |
|---|---|
| Stand | Schritt 1 abgeschlossen — Commit `docs(sdlc-1): PRD, User Stories und Traceability-Matrix` (Hash = `HEAD` nach diesem Commit) |
| Nächster Schritt | 2 (Konzept & Architektur), Agent `aisdlc-architecture` |
| Gate | **GATE 1** nach Schritt 2 — kein Anwendungscode vor menschlicher Freigabe |
| Vertrag | `docs/api/openapi.yaml` — existiert noch nicht; wird in Schritt 2 erstellt und **nach Schritt 4 eingefroren** |
| Schema | `.agent/knowledge/schema.sql` (kanonisch) — noch Template |
| Stack | `.agent/knowledge/stack.md` — noch Template; Stack laut Anforderung Abschnitt 11: Java 21, Spring Boot, PostgreSQL, React/TypeScript |
| Codebasis | `src/` existiert nicht — Neuaufbau, keine Altlast |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate |
| 3 | `.agent/workflows/02-architecture.md` | dieser Schritt |
| 4 | `.agent/prompts/orchestrator/02_orchestrator_architecture.md` | dein Systemprompt |
| 5 | `docs/Anforderung.md` | der Roh-Input (18 FR + 6 NFR, unverändert) |
| 6 | `docs/requirements/PRD.md` | Vision, Rollen, Scope, MoSCoW, Festlegungen D-01…D-15, offene Punkte |
| 7 | `docs/requirements/traceability_matrix.json` | Anforderung → Story → Test → Code; offene Punkte O-01…O-07 |
| 8 | `.agent/artifacts/risk-register.md` | Risiken und Gegenmaßnahmen |
| 9 | `.agent/artifacts/traceability.md` | Lückenliste (L-01…L-04) und was die nächsten Schritte damit tun |

Bei Bedarf, nicht zwingend: `docs/requirements/stories/US-0xx.json` (23 Stories),
`docs/requirements/features/US-0xx.feature` (23 Features, je Normalfall/Verweigerungsfall/Grenzfall).

## Entscheidungen, die gelten

| ID | Entscheidung | Bindet |
|---|---|---|
| D-01 | Mandant stammt ausschließlich aus der Identität; ein Mandantenparameter wird mit HTTP 422 abgelehnt | Vertrag, Datenmodell, alle Stories |
| D-02 | Fremde Ressourcen werden mit HTTP 404 beantwortet (Existenz bleibt verborgen) | Vertrag, Fehlersemantik |
| D-03 | Pause: keine Zustellung, kein Zwischenspeichern, kein Nachliefern; offene Wiederholung wird abgebrochen | Zustellzustandsmodell |
| D-04 | Ereignis ohne Abnehmer wird angenommen und sichtbar als „kein Abnehmer“ protokolliert | Zustellzustandsmodell, Protokoll |
| D-05 | Idempotenz je (Mandant, Idempotenzschlüssel); abweichende Nutzlast unter gleichem Schlüssel → HTTP 409 | Datenmodell, Vertrag |
| D-06 | Drei Kennungen: Ereignis-ID, Zustelllauf-ID, Versuchs-ID — löst den Glossar-Widerspruch aus Abschnitt 4 vs. FR-021 | Vertrag, Zustellmodell |
| D-07 | Signatur über Zeitstempel (UTC) + Nutzlastbytes; Verfahren und Header-Name werden im Vertrag eingefroren (breaking change = neue Hauptversion) | Vertrag, Krypto |
| D-08 | Wiederholung bei 5xx, Netzwerkfehler, 408, 429; kein Wiederholen bei 2xx; Ablehnungssignal + übrige 4xx → sofort aufgeben | Zustellzustandsmodell |
| D-09 | Backoff konfigurierbar, Verdopplung, mit Streuung | Worker-Konfiguration |
| D-10 | Annahme und Zustellung getrennt; Zustellung im Hintergrundprozess | Architektur, NFR-002 |
| D-11 | Protokollsuche nur über Metadaten, keine Suche nach Nutzlastinhalt | Protokoll, Suchindex |
| D-12 | Aufbewahrungsfrist konfigurierbar; ohne O-02 kein Wert vorbelegt | Betrieb |
| D-13 | Die 1-MB-Grenze gilt für die eingelieferten Bytes (bei vorverschlüsselter Einlieferung für die Chiffre) | Vertrag |
| D-14 | Rate-Limit-Mechanismus gebaut und konfigurierbar; ohne O-01 kein Grenzwert vorbelegt, nur Zähler erhoben | Betrieb |
| D-15 | Replay bis zur Entscheidung nur durch den Empfänger-Admin des Endpunkts | Rollenmatrix, Vertrag |

Alle 15 Festlegungen sind **Vorschläge der Anforderungserhebung** und brauchen bei Gate 1
die Bestätigung des Menschen. Keine davon ist eine Fachbereichs-Festlegung.

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

**ARCHITECTURAL_BLOCKER-Kandidaten für Schritt 2** (blockieren Schritt 2 nicht, sind dort zu
entscheiden): Verschlüsselungsverfahren und Schlüsselverwaltung (O-04), Durchsetzung der
Mandantentrennung auf Datenbankebene (NFR-003), Zeitlimit je Zustellversuch (O-07),
Zustandsmodell der Zustellung, Übernahme des Zustellprozesses nach Absturz.

## Was du in diesem Schritt **nicht** tust

- **Keine Festlegung der offenen Punkte O-01…O-07.** Sie gehören dem Fachbereich. Trage
  sie als Vorschlag in die Architekturentscheidungen ein, entscheide sie nicht.
- **Keine Anwendungscode-Dateien.** `src/` bleibt unberührt; Code entsteht in Schritt 6.
- **Keine Änderung am Roh-Input** `docs/Anforderung.md` — er ist die Quelle und bleibt
  unverändert.
- **Noch kein Vertrags-Freeze.** `docs/api/openapi.yaml` wird in Schritt 2 erstellt und
  erst nach Schritt 4 eingefroren. Ein Vertragsdefekt nach dem Freeze ist ein Befund für
  einen Rücksprung zu Schritt 2, keine Änderung im Vorbeigehen.
- **Keine Änderung an `.agent/knowledge/schema.sql` oder `docs/architecture/schema.sql`**
  ohne den jeweils anderen Pfad im selben Schritt (Spiegel-Regel, ADR-019).

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Story-Übersicht (menschlich lesbar) | `.agent/artifacts/user-stories.md` |
| Lückenliste mit Entscheidungen | `.agent/artifacts/traceability.md` |
