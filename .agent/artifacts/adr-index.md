# ADR-Index — EchoBox

> Schritt 3 (Simplify & Reduktion) · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Eine Zeile je ADR. Volltext: `docs/adr/`. Format: Nygard.
> **Gate 1 ist freigegeben (2026-10-21)** — alle 17 Entscheidungen sind
> bestätigt; F-01…F-04 sind als Vorgabewerte festgeschrieben.

| ADR | Titel | Status | Gate-Hinweis |
|---|---|---|---|
| ADR-001 | Tech-Stack | Angenommen | Java 21 / Spring Boot 3 / PostgreSQL 16 / Next.js 15 — bestätigt |
| ADR-002 | Mandantentrennung durch Row-Level-Security | Angenommen | RLS, `app.set_tenant()`, kein BYPASSRLS — bestätigt |
| ADR-003 | Verschlüsselung im Ruhezustand und Schlüsselverwaltung | Angenommen | **Architekturseite von O-04** — bestätigt; fachliche Pflichtfrage offen (Gate 2) |
| ADR-004 | Signaturverfahren und Header-Namen | Angenommen | **eingefroren** — bestätigt |
| ADR-005 | Zustellkennungen und Empfänger-Header | Angenommen | **eingefroren** — bestätigt |
| ADR-006 | Zustellprozess — Zustandsmodell und Hintergrundprozess | Angenommen | Warteschlange = `delivery_runs`, Lease — bestätigt |
| ADR-007 | Idempotenz der Einlieferung | Angenommen | 409 bei abweichender Nutzlast — bestätigt |
| ADR-008 | Wiederholung, Backoff, Streuung **und Zeitlimit je Versuch** | Angenommen | **bestätigt: F-04 (O-06) + O-07** — enthält die frühere ADR-014 |
| ADR-009 | Ablehnungssignal des Empfängers | Angenommen | **eingefroren** (410 + Header + Body) — bestätigt |
| ADR-010 | Rate-Limit je Endpunkt | Angenommen | **bestätigt: F-01 (O-01)** — 60/Endpunkt/Minute, `delay` |
| ADR-011 | Kennzahlen, Erfolgsquote und Zustelllatenz | Angenommen | Definitionen bestätigt |
| ADR-012 | Aufbewahrung | Angenommen | **bestätigt: F-02 (O-02)** — je 30 Tage, getrennt; O-05 vor Gate 2 |
| ADR-013 | Mandanten-Isolation pro Instanz und Containerbetrieb | Angenommen | geteilter Betrieb als Standard — bestätigt |
| ADR-014 | ~~Zeitlimit je Zustellversuch~~ | **Zusammengeführt in ADR-008** | Verweisdatei — keine eigene Entscheidung mehr |
| ADR-015 | Fehlersemantik, Mandantenherkunft und Rollenmatrix | Angenommen | **bestätigt: F-03 (O-03)** — Replay nur Empfänger-Admin |
| ADR-016 | Kanonische Artefakte — ein Ort pro Wahrheit | Angenommen | Vertrag einmal, Schema kanonisch + Spiegel — bestätigt |
| ADR-017 | Protokollsuche ausschließlich über Metadaten | Angenommen | D-11 — bestätigt; O-04 bleibt Produktfrage (Gate 2) |
| ADR-018 | Oberfläche — Framework und Barrierefreiheit | Angenommen | axe als Pflicht-Gate — bestätigt |

**17 Entscheidungen** (ADR-014 ist als Verweis in ADR-008 aufgegangen).

## Bestätigte Festlegungen (Gate 1, 2026-10-21)

| ID | Festlegung | Wert | ADR |
|---|---|---|---|
| **F-01** | Rate-Limit je Endpunkt | **60 Versuche/Endpunkt/Minute**, Strategie `delay` | ADR-010 |
| **F-02** | Aufbewahrung | **30 Tage**, getrennt für Ereignisse und Zustellprotokolle, Fristbeginn = letzter Versuch | ADR-012 |
| **F-03** | Replay-Berechtigung | **nur** Empfänger-Admin des Endpunkts (Entwickler und Betrieb: 403) | ADR-015 |
| **F-04** | Versuche und Backoff | **8 Versuche**, Basis **1 min**, ×2, Deckel **60 min**, Streuung **± 20 %** | ADR-008 |

Alle vier Werte sind **Vorgaben in der Konfiguration** — jeder Wert ist je
Umgebung änderbar, keiner ist im Code verdrahtet.

## Offen für Gate 2

| ID | Thema | Owner |
|---|---|---|
| O-04 | Verschlüsselung im Ruhezustand: **fachliche** Pflicht und Durchsuchbarkeit | Fachbereich + Datenschutz |
| O-05 | Vertrag über die Auftragsverarbeitung (AVV) | Fachbereich + Recht |
