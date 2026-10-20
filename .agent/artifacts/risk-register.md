# PromptGuard — Risiko-Register (Top 5)

> Stand 2026-10-20 · Phase 1 (Anforderungserhebung) · Quelle: `docs/requirements/PRD.md` §16
> Aufnahmekriterium: nur Risiken, die **eine Entscheidung ändern** können. Jedes Risiko hat eine
> Gegenmaßnahme, die in einer konkreten Anforderung (ID) verankert ist.
>
> **R-06 ist ein bewusst akzeptiertes Restrisiko** (Entscheidung F1, Product Owner, 2026-10-20):
> Der Originalinhalt eines Prompts wird unredigiert an den externen KI-Dienst übermittelt. Es ist
> deshalb nicht vermieden, sondern **begrenzt und nachgewiesen** — die Gegenmaßnahmen sind
> verbindliche Anforderungen, keine Empfehlungen.

| ID | Risiko | Likelihood | Impact | Gegenmaßnahme | Verankert in |
|---|---|---|---|---|---|
| R-01 | **Mandantenleck** — ein Query oder Hintergrund-Job ohne `organisation_id`-Filter gibt Daten einer fremden Organisation preis. | mittel | **kritisch** | Row-Level-Security als zweite Verteidigungslinie zusätzlich zur Filterpflicht; verbindlicher Negativ-Integrationstest „Cross-Tenant-Zugriff liefert keine Daten" auf **jeder** Route (Liste, Detail, Suche, Fundstelle, Export, Cache-Key, Job-Kontext); kein Foreign-Key-Pfad ohne Tenant-Kontext. | SEC-002, SEC-003, FR-009, FR-023, US-005, US-031 |
| R-02 | **Fehlalarme** — Secret-/PII-Erkennung produziert viele Treffer, die Teams umgehen (Blind-Alarm-Effekt). | hoch | hoch | Konfigurierbare Schwelle je Organisation (Default `MEDIUM`); `WAIVED`-Pfad mit Pflicht-Begründung und Audit; nur Redaktionsausschnitt statt Volltext im Ergebnis; KPI „Fehlalarmquote" sichtbar machen. | FR-036, FR-037, FR-039, US-021, US-023 |
| R-03 | **Prüfstau** — der KI-Dienst ist langsam oder aus, die Warteschlange wächst, die Registry blockiert. | mittel | hoch | Persistente Warteschlange (kein In-Memory), Retry mit Backoff, Totmannschaltung, `FAILED` manuell wiederholbar, degradierter Betrieb (Anlegen bleibt möglich), Warteschlangentiefe als Metrik mit Alarm. | FR-070, FR-071, NFR-006, NFR-007, US-016, US-033 |
| R-04 | **Prüfer kompromittiert** — ein Prüfling enthält eine Prompt Injection, die den Prüfer dazu bringt, `APPROVED` zu melden. | mittel | **kritisch** | Zweistufige Prüfung: lokale deterministische Vorprüfung zuerst; Redaktion vor dem externen Aufruf; Prüfling wird ausschließlich als Datenblock übergeben, nie als Anweisung; Systemanweisung des Prüfers dominiert; keine Werkzeugaufrufe aus Prüflinginhalt. | FR-038, SEC-006, SEC-007, US-018, US-022 |
| R-05 | **Alarm wird ignoriert** — zu viele Security-Alarme ⇒ die Warnung verliert ihre Wirkung. | mittel | hoch | Priorisierung nach Schweregrad, Filterung des Alarmrahmens auf Metadaten, Zustellquote als KPI (K7), Nachjustierung der Schwelle je Organisation. | FR-050, FR-051, FR-052, US-026, US-027 |
| R-06 | **Unredigierte Übermittlung an den KI-Dienst (F1)** — erkannte Secrets und personenbezogene Daten verlassen das System und werden vom externen Dienst verarbeitet. Die Plattform verteilt damit genau die Inhalte, die sie als Risiko markiert. | **hoch** (bei secret-haltigen Prompts) | **hoch** | **Akzeptiertes Restrisiko** (Entscheidung F1, Product Owner, 2026-10-20). Pflichtmaßnahmen: AVV mit EU-Anbieter inkl. Trainingsverbot und Löschgarantie (FR-065, DP-005); Provider-Konfigurationsprüfung mit Abbruch bei Verstoß (FR-038, US-022); Metadaten-Audit jeder Übermittlung ohne Inhalt (FR-074); lokale Vorprüfung bleibt Pflicht und fließt in das Gesamturteil ein (FR-031); Aufnahme in die Datenschutz-Folgenabschätzung. | FR-031, FR-038, FR-065, FR-074, DP-002, DP-005, US-019, US-022, US-034 |

## Weitere Beobachtungspunkte (kein eigener Top-5-Eintrag)

| # | Beobachtung | Auswirkung | Treatment |
|---|---|---|---|
| B-01 | Scope-Konflikt „zentrales Register" ↔ „Fachbereiche arbeiten in Abteilungs-Workspaces" (Issue: **F2**) | Falsches Mandantenmodell erzwingt ein Datenmodell- und API-Rework in Phase 2 | Entscheidung F2 **vor** dem API-Vertrag |
| B-02 | Externe KI und DSGVO: Übermittlung von Prompt-Inhalten an einen Drittanbieter (Issue: **F1**) | Verstoß gegen Art. 28/32 DSGVO, wenn ungeklärt | Redaktionsstufe + Anbieter-Konfiguration als **MUSS** im PRD; F1 entscheiden |
| B-03 | Der Kunde nennt Langchain4j, der Framework-Stack nennt „FastAPI oder Spring Boot" | Umsetzung in zwei falschen Technologien | Als **CON-001** aufgelöst: Langchain4j ⇒ JVM ⇒ Spring Boot; Bestätigung einholen (F10) |
| B-04 | 80-%-Coverage-Gate bei KI- und asynchronen Pfaden | Gate blockiert Releases ohne belastbaren Test | KI-Antworten hinter einem Adapter-Interface (Stub im Test), NFR-009/FR-073 definieren die Messgröße (Line Coverage, ohne generierte Code) |
| B-05 | Fehlender Betrieb für Mandanten-Rechte (globaler Security-Admin) | Scope-Ausweitung um Monate | F3 — Empfehlung: organisationsintern, globaler Zugriff nur im Rahmenbetrieb ohne Fachdaten |

## Frühwarnindikatoren

| Indikator | Schwelle | Reaktion |
|---|---|---|
| Anteil `FLAGGED` an allen Scans | > 25 % über 7 Tage | Schwellen- und Detektor-Kalibrierung prüfen (R-02) |
| Anteil `FAILED` an allen Scans | > 5 % über 24 h | KI-Anbieter-/Warteschlangen-Incident öffnen (R-03) |
| Zustellquote der Warn-E-Mails | < 95 % über 24 h | SMTP-Routing und Totmannschaltung prüfen (R-05) |
| Warteschlangentiefe | > 1 000 Jobs oder Alter > 15 min | Skalierung / Provider-Limit prüfen (R-03) |
| Negativtests der Mandantentrennung | **jeder** Fehlschlag | Release-Stopp, Root Cause vor Gate 2 (R-01) |
