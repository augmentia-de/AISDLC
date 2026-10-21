# Architecture Decision Records — EchoBox

> Schritt 3 (Simplify & Reduktion) · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> **Rücksprung aus Schritt 4 (2026-10-21):** ADR-002, ADR-006, ADR-008,
> ADR-009, ADR-010, ADR-012 und ADR-015 trugen die **alte, falsche** Semantik
> und wurden im Rücksprung **im Schritt-2-Eigentum** korrigiert (der
> Stresstest hat sie als Eigentum von Schritt 2 ausgewiesen). Es wurde **keine**
> bestätigte Entscheidung neu aufgerollt.
> Format: Nygard (Kontext · Entscheidung · Konsequenzen · Alternativen · Status).
> Ein angenommener ADR wird **nie** überschrieben — er wird durch einen neuen
> ersetzt. *Ausnahme:* die Korrektur einer **fehlerhaften** Semantik im
> Rücksprung in das Eigentum desselben Schritts (§ „Korrekturen aus dem
> Rücksprung“).

## Index

| ADR | Titel | Status | Gate-Hinweis |
|---|---|---|---|
| [ADR-001](ADR-001-tech-stack.md) | Tech-Stack | Angenommen | Quelle für `.agent/knowledge/stack.md` |
| [ADR-002](ADR-002-mandantentrennung-rls.md) | Mandantentrennung durch Row-Level-Security | Angenommen | **§7:** mandantenübergreifende Sicht des Service-Admins über `SECURITY DEFINER`-Eigentümerfunktionen |
| [ADR-003](ADR-003-verschluesselung-ruhezustand.md) | Verschlüsselung im Ruhezustand und Schlüsselverwaltung | Angenommen | **Architekturseite von O-04** |
| [ADR-004](ADR-004-signaturverfahren.md) | Signaturverfahren und Header-Namen | Angenommen | **eingefroren** |
| [ADR-005](ADR-005-zustellkennungen.md) | Zustellkennungen und Empfänger-Header | Angenommen | **eingefroren** |
| [ADR-006](ADR-006-zustellprozess-hintergrundprozess.md) | Zustellprozess — Zustandsmodell und Hintergrundprozess | Angenommen | **§3:** Worker arbeitet mandantenweise · **§6:** Replay **einmal** |
| [ADR-007](ADR-007-idempotenz.md) | Idempotenz der Einlieferung | Angenommen | D-05 |
| [ADR-008](ADR-008-retry-backoff-streuung.md) | Wiederholung, Backoff, Streuung **und Zeitlimit je Versuch** | Angenommen | **bestätigt: F-04 (O-06), O-07** — enthält die frühere ADR-014; Gesamtfenster **≈ 2 h** (E-03) |
| [ADR-009](ADR-009-ablehnungssignal.md) | Ablehnungssignal des Empfängers | Angenommen | **eingefroren** — 3xx: kein Wiederholen, `client_error` (E-08) |
| [ADR-010](ADR-010-rate-limit.md) | Rate-Limit je Endpunkt | Angenommen | **bestätigt: F-01 (O-01)** — 60/ Minute, `delay`; **zwei** Zustände (E-02) |
| [ADR-011](ADR-011-metriken-slo.md) | Kennzahlen, Erfolgsquote und Zustelllatenz | Angenommen | — |
| [ADR-012](ADR-012-aufbewahrung.md) | Aufbewahrung | Angenommen | **bestätigt: F-02 (O-02)** — 30 Tage, getrennt; Kaskade benannt (E-04); O-05 vor Gate 2 |
| [ADR-013](ADR-013-mandanten-isolation-instanz.md) | Mandanten-Isolation pro Instanz und Containerbetrieb | Angenommen | — |
| [ADR-014](ADR-014-zeitlimit-versuch.md) | ~~Zeitlimit je Zustellversuch~~ | **Zusammengeführt in ADR-008** | Verweisdatei, keine eigene Entscheidung |
| [ADR-015](ADR-015-fehlersemantik-tenant-rollen.md) | Fehlersemantik, Mandantenherkunft und Rollenmatrix | Angenommen | **bestätigt: F-03 (O-03)** — nur Empfänger-Admin; Replay **einmal** (E-09) · **§3:** erst der Mandant, dann die Rolle (V-39) |
| [ADR-016](ADR-016-kanonische-artefakte.md) | Kanonische Artefakte — ein Ort pro Wahrheit | Angenommen | — |
| [ADR-017](ADR-017-protokollsuche-metadaten.md) | Protokollsuche ausschließlich über Metadaten | Angenommen | D-11; O-04 bleibt Produktfrage |
| [ADR-018](ADR-018-barrierefreiheit-frontend.md) | Oberfläche — Framework und Barrierefreiheit | Angenommen | — |

**17 Entscheidungen** in 17 ADRs; ADR-014 ist als Verweis auf ADR-008 erhalten
(siehe „Hinweis zur Nummerierung“).

## Bestätigte Festlegungen (Gate 1, 2026-10-21)

| ID | Festlegung | Wert | ADR |
|---|---|---|---|
| **F-01** | Rate-Limit je Endpunkt | **60 Versuche/Endpunkt/Minute**, Strategie `delay` (verschieben); **zwei** Zustände: `NULL` = kein Override (⇒ 60), `0` = abgeschaltet | ADR-010 |
| **F-02** | Aufbewahrung | **30 Tage**, getrennt für Ereignisse und Zustellprotokolle, Fristbeginn **je Datensatz** = letzter Versuch; Anker, zwei Stufen, Karenzzeit, Kaskade | ADR-012 |
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
| **R-01** | **Karenzzeit** der Aufbewahrung (Abstand „markiert“ → „gelöscht“) | ADR-012 §6 | **offen** — konfigurierbar, **kein** festgelegter Vorgabewert; der Vertrag behauptet **keinen**. **F-02 bleibt unverändert** | Fachbereich (Gate 2) |

## Korrekturen aus dem Rücksprung (Schritt 4 → Schritt 2, 2026-10-21)

Der Stresstest in Schritt 4 hat **22 Vertragsdefekte**, einen
**Architekturbruch** (S-01) und zwei Schema-Befunde gefunden. Der Rücksprung in
Schritt 2 (Eigentum des Vertrags und des Schemas) hat die betroffenen ADRs
korrigiert — sie trugen die **alte** Semantik:

| ADR | Korrektur | Befund |
|---|---|---|
| ADR-002 | §3 transaktionsgebundener Kontext · **§7** mandantenübergreifende Sicht über `SECURITY DEFINER`-Eigentümerfunktionen · Verifikation erweitert | N-01, S-01, E-01 |
| ADR-006 | §3 Worker arbeitet **mandantenweise** · **§6** Replay **einmal** (`UNIQUE (replay_of)`) | N-02, E-09 |
| ADR-008 | Gesamtfenster **≈ 2 Stunden** statt 4 (7 Wartezeiten = 123 min) · Zeile 3xx | S-17, E-03, E-08 |
| ADR-009 | **3xx**: kein Wiederholen, `rejected` / `client_error`, Redirects werden **nicht** gefolgt | S-13, E-08 |
| ADR-010 | **Zwei** Zustände statt drei; die Spalte `enabled` ist entfallen | S-04, N-08, E-02 |
| ADR-012 | **Kaskade** benannt (Ereignis nimmt Läufe und Versuche mit) · Fristbeginn je Datensatz | S-03, N-08, E-04 |
| ADR-015 | Rollenmatrix mit dem Mechanismus der mandantenübergreifenden Sicht · **§6** Replay **einmal** | S-01, S-19, E-01, E-09 |

**Keine** bestätigte Festlegung (F-01…F-04, D-01…D-15, ADR-001…ADR-018) wurde
neu aufgerollt. Der vollständige Nachweis je Defekt steht in
`.agent/artifacts/ruecksprung-bilanz.md`.

## Korrekturen aus dem zweiten Rücksprung (Schritt 4 → Schritt 2, 2026-10-21)

Der **zweite** Stresstest (Commit `c9c8038`) hat drei Vertragssätze, drei
Schema-Befunde und sechs „sollte“-Befunde gefunden. Der Rücksprung in Schritt 2
hat die betroffenen ADRs nachgezogen:

| ADR | Korrektur | Befund |
|---|---|---|
| ADR-002 | **Grenze (ehrlich benannt)**: die Applikationsrolle kann `app.cross_tenant` setzen und damit die mandantenübergreifende Sicht öffnen — die einzige verbleibende Lücke zwischen RLS und Anwendung; Härtung als **offene Entscheidung** (E-12, Gate 2) · §7: Totenlisten-Funktion ohne Statusparameter (`abandoned` **fest verdrahtet**) · §7: logische Löschung ist der Applikationsrolle **nicht** möglich | N-18, N-14, N-16 |
| ADR-006 | §6: `replay_of` ist ein **historischer Verweis**, kein Fremdschlüssel · §6: **23505 ⇒ 409** und `lock_timeout`/`statement_timeout` **kürzer** als das HTTP-Timeout (Vorgabe für Schritt 6) | N-15, N-20 |
| ADR-012 | §3: die **Versuche** verlieren die Sichtbarkeit **mit ihrem Lauf** · §6: ohne gesetzten Wert Purge im **nächsten** Durchlauf — **kein** erfundener Vorgabewert | N-13, E-13 |
| ADR-015 | Rollenmatrix korrigiert: die mandantenübergreifende Sicht gilt **nur** für Endpunktliste, Totenliste und Aufbewahrung — das **Zustellprotokoll bleibt mandantengefiltert** · §6: 23505 ⇒ `delivery_state_conflict`, Sperrenwartezeit kürzer als das HTTP-Timeout | N-19, N-20, E-10 |

Der vollständige Nachweis je Befund steht in
`.agent/artifacts/ruecksprung-bilanz.md`.

## Korrekturen aus dem dritten Rücksprung (Rücksprung 5, 2026-10-21)

Der **dritte** Rücksprung (letzter Korrekturdurchgang vor dem Einfrieren des
Vertrags) hat die sechs bestätigten Befunde **V-39…V-44** umgesetzt. Betroffen
ist **eine** ADR:

| ADR | Korrektur | Befund |
|---|---|---|
| ADR-015 | **§1** Rollenmatrix: die mandantenübergreifende Sicht des `service_admin` gilt **nur für die drei Listen-/Betriebsoperationen** — der **Einzelabruf** einer fremden Ressource ergibt **404** (auch für `service_admin`) · **§2** `organisation_id` ist **immer** gefüllt und benennt den **besitzenden Mandanten** (im Zustellprotokoll: der Mandant aus der Identität) · **§3** **Reihenfolge der Prüfungen: erst der Mandant, dann die Rolle** — fremde Ressource ⇒ 404 (auch für `developer`/`service_admin`), eigene Ressource ohne Rolle ⇒ 403 | V-44, V-26, V-27, V-39 |

**Keine** bestätigte Festlegung (F-01…F-04, D-01…D-15, ADR-001…ADR-018) wurde
neu aufgerollt; die drei Sätze waren **überholt** und wurden an den entschiedenen
Stand gebracht. Der vollständige Nachweis steht in
`.agent/artifacts/ruecksprung-bilanz.md` § 11.

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
