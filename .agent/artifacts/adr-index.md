# ADR-Index — Gate 1

> Schritt 2 (Konzept & Architektur) · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Eine Zeile je ADR. Volltext: `docs/adr/`. Format: Nygard.

| ADR | Titel | Status | Gate-Hinweis |
|---|---|---|---|
| ADR-001 | Tech-Stack | Angenommen | Java 21 / Spring Boot 3 / PostgreSQL 16 / Next.js 15 — bestätigen |
| ADR-002 | Mandantentrennung durch Row-Level-Security | Angenommen | RLS, `app.set_tenant()`, kein BYPASSRLS — bestätigen |
| ADR-003 | Verschlüsselung im Ruhezustand und Schlüsselverwaltung | Angenommen | **Architekturseite von O-04** — bestätigen |
| ADR-004 | Signaturverfahren und Header-Namen | Angenommen | **eingefroren** — bestätigen |
| ADR-005 | Zustellkennungen und Empfänger-Header | Angenommen | **eingefroren** — bestätigen |
| ADR-006 | Zustellprozess — Zustandsmodell und Hintergrundprozess | Angenommen | Warteschlange = `delivery_runs`, Lease — bestätigen |
| ADR-007 | Idempotenz der Einlieferung | Angenommen | 409 bei abweichender Nutzlast — bestätigen |
| ADR-008 | Wiederholung, Backoff und Streuung | Angenommen | **Werte offen (O-06)** |
| ADR-009 | Ablehnungssignal des Empfängers | Angenommen | **eingefroren** (410 + Header + Body) — bestätigen |
| ADR-010 | Rate-Limit je Endpunkt | Angenommen | **Wert offen (O-01)** |
| ADR-011 | Kennzahlen, Erfolgsquote und Zustelllatenz | Angenommen | Definitionen bestätigen |
| ADR-012 | Aufbewahrung | Angenommen | **Wert offen (O-02)**; O-05 vor Gate 2 |
| ADR-013 | Mandanten-Isolation pro Instanz und Containerbetrieb | Angenommen | geteilter Betrieb als Standard — bestätigen |
| ADR-014 | Zeitlimit je Zustellversuch | Angenommen | **entscheidet O-07** (5 s + 10 s) — Kenntnisnahme |
| ADR-015 | Fehlersemantik, Mandantenherkunft und Rollenmatrix | Angenommen | **Berechtigung offen (O-03)** |
| ADR-016 | Kanonische Artefakte — ein Ort pro Wahrheit | Angenommen | Vertrag einmal, Schema kanonisch + Spiegel — bestätigen |
| ADR-017 | Protokollsuche ausschließlich über Metadaten | Angenommen | D-11 — bestätigen |
| ADR-018 | Oberfläche — Framework und Barrierefreiheit | Angenommen | axe als Pflicht-Gate — bestätigen |

**18 ADRs**, alle im Status *Angenommen* (Vorschlag der Architektur — die
Bestätigung des Menschen steht bei Gate 1 aus).
