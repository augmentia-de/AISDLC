# Architecture Decision Records — EchoBox

> Schritt 2 (Konzept & Architektur) · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Format: Nygard (Kontext · Entscheidung · Konsequenzen · Alternativen · Status).
> Ein angenommener ADR wird **nie** überschrieben — er wird durch einen neuen
> ersetzt.

## Index

| ADR | Titel | Status | Gate-Hinweis |
|---|---|---|---|
| [ADR-001](ADR-001-tech-stack.md) | Tech-Stack | Angenommen | Quelle für `.agent/knowledge/stack.md` |
| [ADR-002](ADR-002-mandantentrennung-rls.md) | Mandantentrennung durch Row-Level-Security | Angenommen | — |
| [ADR-003](ADR-003-verschluesselung-ruhezustand.md) | Verschlüsselung im Ruhezustand und Schlüsselverwaltung | Angenommen | **Architekturseite von O-04** |
| [ADR-004](ADR-004-signaturverfahren.md) | Signaturverfahren und Header-Namen | Angenommen | **eingefroren** |
| [ADR-005](ADR-005-zustellkennungen.md) | Zustellkennungen und Empfänger-Header | Angenommen | **eingefroren** |
| [ADR-006](ADR-006-zustellprozess-hintergrundprozess.md) | Zustellprozess — Zustandsmodell und Hintergrundprozess | Angenommen | — |
| [ADR-007](ADR-007-idempotenz.md) | Idempotenz der Einlieferung | Angenommen | D-05 |
| [ADR-008](ADR-008-retry-backoff-streuung.md) | Wiederholung, Backoff und Streuung | Angenommen | **Werte offen: O-06** |
| [ADR-009](ADR-009-ablehnungssignal.md) | Ablehnungssignal des Empfängers | Angenommen | **eingefroren** |
| [ADR-010](ADR-010-rate-limit.md) | Rate-Limit je Endpunkt | Angenommen | **Wert offen: O-01** |
| [ADR-011](ADR-011-metriken-slo.md) | Kennzahlen, Erfolgsquote und Zustelllatenz | Angenommen | — |
| [ADR-012](ADR-012-aufbewahrung.md) | Aufbewahrung | Angenommen | **Wert offen: O-02**; O-05 vor Gate 2 |
| [ADR-013](ADR-013-mandanten-isolation-instanz.md) | Mandanten-Isolation pro Instanz und Containerbetrieb | Angenommen | — |
| [ADR-014](ADR-014-zeitlimit-versuch.md) | Zeitlimit je Zustellversuch | Angenommen | **entscheidet O-07** |
| [ADR-015](ADR-015-fehlersemantik-tenant-rollen.md) | Fehlersemantik, Mandantenherkunft und Rollenmatrix | Angenommen | **Berechtigung offen: O-03** |
| [ADR-016](ADR-016-kanonische-artefakte.md) | Kanonische Artefakte — ein Ort pro Wahrheit | Angenommen | — |
| [ADR-017](ADR-017-protokollsuche-metadaten.md) | Protokollsuche ausschließlich über Metadaten | Angenommen | D-11 |
| [ADR-018](ADR-018-barrierefreiheit-frontend.md) | Oberfläche — Framework und Barrierefreiheit | Angenommen | — |

## Offene Punkte — wo sie entschieden werden

| ID | Thema | ADR | Wer entscheidet |
|---|---|---|---|
| O-01 | Rate-Limit: welche Rate, blockieren oder verschieben | ADR-010 | Fachbereich |
| O-02 | Aufbewahrung: welche Daten, welche Frist, ab wann | ADR-012 | Fachbereich + Datenschutz |
| O-03 | Wer darf eine tote Zustellung erneut auslösen | ADR-015 | Fachbereich |
| O-04 | Verschlüsselung: Muss? Durchsuchbarkeit? | ADR-003 (Architekturseite entschieden) | Fachbereich + Datenschutz |
| O-05 | Auftragsverarbeitung / AVV | ADR-012 | Fachbereich + Recht (vor Gate 2) |
| O-06 | Versuchsanzahl und Backoff-Werte | ADR-008 | Fachbereich |
| O-07 | Zeitlimit je Zustellversuch | ADR-014 | **Architektur — entschieden** |

## Hinweis zur Nummerierung

`AGENTS.md` verweist auf „ADR-019 §6“ für die Spiegel-Regel. Das ist die
Nummerierung des **Vorlaufs** dieses Projekts, nicht dieses Schritts. Hier
realisiert **ADR-016** die Spiegel-Regel (kanonisches Schema plus
byte-identische Kopie). Der Verweis in `AGENTS.md` ist bei der nächsten
Überarbeitung des Frameworks anzupassen — die Dateien sind die Wahrheit.
