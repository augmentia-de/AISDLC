# Architecture Decision Records — EchoBox

> Schritt 3 (Simplify & Reduktion) · Agent: `aisdlc-architecture` · Stand: 2026-10-21
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
| [ADR-008](ADR-008-retry-backoff-streuung.md) | Wiederholung, Backoff, Streuung **und Zeitlimit je Versuch** | Angenommen | **bestätigt: F-04 (O-06), O-07** — enthält die frühere ADR-014 |
| [ADR-009](ADR-009-ablehnungssignal.md) | Ablehnungssignal des Empfängers | Angenommen | **eingefroren** |
| [ADR-010](ADR-010-rate-limit.md) | Rate-Limit je Endpunkt | Angenommen | **bestätigt: F-01 (O-01)** — 60/ Minute, `delay` |
| [ADR-011](ADR-011-metriken-slo.md) | Kennzahlen, Erfolgsquote und Zustelllatenz | Angenommen | — |
| [ADR-012](ADR-012-aufbewahrung.md) | Aufbewahrung | Angenommen | **bestätigt: F-02 (O-02)** — 30 Tage, getrennt; O-05 vor Gate 2 |
| [ADR-013](ADR-013-mandanten-isolation-instanz.md) | Mandanten-Isolation pro Instanz und Containerbetrieb | Angenommen | — |
| [ADR-014](ADR-014-zeitlimit-versuch.md) | ~~Zeitlimit je Zustellversuch~~ | **Zusammengeführt in ADR-008** | Verweisdatei, keine eigene Entscheidung |
| [ADR-015](ADR-015-fehlersemantik-tenant-rollen.md) | Fehlersemantik, Mandantenherkunft und Rollenmatrix | Angenommen | **bestätigt: F-03 (O-03)** — nur Empfänger-Admin |
| [ADR-016](ADR-016-kanonische-artefakte.md) | Kanonische Artefakte — ein Ort pro Wahrheit | Angenommen | — |
| [ADR-017](ADR-017-protokollsuche-metadaten.md) | Protokollsuche ausschließlich über Metadaten | Angenommen | D-11; O-04 bleibt Produktfrage |
| [ADR-018](ADR-018-barrierefreiheit-frontend.md) | Oberfläche — Framework und Barrierefreiheit | Angenommen | — |

**17 Entscheidungen** in 17 ADRs; ADR-014 ist als Verweis auf ADR-008 erhalten
(siehe „Hinweis zur Nummerierung“).

## Bestätigte Festlegungen (Gate 1, 2026-10-21)

| ID | Festlegung | Wert | ADR |
|---|---|---|---|
| **F-01** | Rate-Limit je Endpunkt | **60 Versuche/Endpunkt/Minute**, Strategie `delay` (verschieben) | ADR-010 |
| **F-02** | Aufbewahrung | **30 Tage**, getrennt für Ereignisse und Zustellprotokolle, Fristbeginn = letzter Versuch | ADR-012 |
| **F-03** | Replay-Berechtigung | **nur** der Empfänger-Admin des Endpunkts; Entwickler und Betrieb ⇒ 403 | ADR-015 |
| **F-04** | Versuche und Backoff | **8 Versuche**, Basis **1 min**, ×2, Deckel **60 min**, Streuung **± 20 %** | ADR-008 |

Alle vier Werte sind **Vorgabewerte in der Konfiguration** — kein Wert ist im
Code verdrahtet, jeder ist je Umgebung und (wo sinnvoll) je Endpunkt bzw.
Mandant änderbar.

## Offene Punkte — wo sie entschieden werden

| ID | Thema | ADR | Stand | Wer entscheidet |
|---|---|---|---|---|
| O-01 | Rate-Limit: welche Rate, blockieren oder verschieben | ADR-010 | **entschieden (F-01)** | — |
| O-02 | Aufbewahrung: welche Daten, welche Frist, ab wann | ADR-012 | **entschieden (F-02)** | — |
| O-03 | Wer darf eine tote Zustellung erneut auslösen | ADR-015 | **entschieden (F-03)** | — |
| O-04 | Verschlüsselung: Muss? Durchsuchbarkeit? | ADR-003 (Architekturseite entschieden), ADR-017 | **offen — Produktfrage** | Fachbereich + Datenschutz (Gate 2) |
| O-05 | Auftragsverarbeitung / AVV | ADR-012 | **offen** | Fachbereich + Recht (vor Gate 2) |
| O-06 | Versuchsanzahl und Backoff-Werte | ADR-008 | **entschieden (F-04)** | — |
| O-07 | Zeitlimit je Zustellversuch | ADR-008 | **entschieden** | — |

## Hinweis zur Nummerierung

`AGENTS.md` verweist auf „ADR-019 §6" für die Spiegel-Regel. Das ist die
Nummerierung des **Vorlaufs** dieses Projekts, nicht dieses Schritts. Hier
realisiert **ADR-016** die Spiegel-Regel (kanonisches Schema plus
byte-identische Kopie). Der Verweis in `AGENTS.md` ist bei der nächsten
Überarbeitung des Frameworks anzupassen — die Dateien sind die Wahrheit.

**Lücke bei ADR-014 (Schritt 3):** Die Zeitlimit-Entscheidung wurde in
ADR-008 eingearbeitet; ADR-014 bleibt als Verweisdatei bestehen. Die
Nummerierung wird **nicht** umgestellt — eine Umnummerierung würde jeden
Verweis in Vertrag, Architektur und Artefakten brechen, ohne eine Entscheidung
zu gewinnen. Neue ADRs beginnen bei ADR-019.
