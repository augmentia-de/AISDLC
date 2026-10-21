# ADR-Gate-Vorlage — EchoBox

> Schritt 3 (Simplify & Reduktion, nach dem Rücksprung) · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> **Der Index aller 17 ADRs** (Titel, Status, Gate-Hinweis) steht in
> `docs/adr/README.md` — dort ist er die **einzige** Kopie; die Volltexte in
> `docs/adr/ADR-*.md`. Format: Nygard.
> Diese Datei ist die **Gate-Vorlage**: was der menschliche Reviewer bestätigt
> bzw. entscheidet. Vor dem 2026-10-21 enthielt sie eine **zweite**
> ADR-Tabelle — die war nach dem Rücksprung **veraltet** (ADR-002 §7 fehlte) und
> ist durch den Verweis ersetzt.

## Was bestätigt ist (Gate 1, 2026-10-21)

| ID | Festlegung | Wert | ADR |
|---|---|---|---|
| **F-01** | Rate-Limit je Endpunkt | **60 Versuche/Endpunkt/Minute**, Strategie `delay` | ADR-010 |
| **F-02** | Aufbewahrung | **30 Tage**, getrennt für Ereignisse und Zustellprotokolle, Fristbeginn = letzter Versuch | ADR-012 |
| **F-03** | Replay-Berechtigung | **nur** Empfänger-Admin des Endpunkts (Entwickler und Betrieb: 403) | ADR-015 |
| **F-04** | Versuche und Backoff | **8 Versuche**, Basis **1 min**, ×2, Deckel **60 min**, Streuung **± 20 %** | ADR-008 |

Alle vier Werte sind **Vorgaben in der Konfiguration** — jeder Wert ist je
Umgebung und (wo sinnvoll) je Endpunkt bzw. Mandant änderbar. Keiner ist im
Code verdrahtet.

## Was offen ist

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-04 | Verschlüsselung im Ruhezustand: **fachliche** Pflicht und Durchsuchbarkeit | Fachbereich + Datenschutz | Gate 2 |
| O-05 | Vertrag über die Auftragsverarbeitung (AVV) | Fachbereich + Recht | Gate 2 |
| **R-01** | **Karenzzeit** der Aufbewahrung („markiert“ → „gelöscht“) — **kein** beschlossener Vorgabewert; der Vertrag behauptet **keinen** (F-02 bleibt unverändert) | Fachbereich | Gate 2 |
