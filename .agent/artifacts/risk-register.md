# Risikoregister — Ereignis-Briefkasten mit Zustellgarantie (EchoBox)

> Schritt 1 (Anforderungserhebung) · Agent: `aisdlc-requirements` · Stand: 2026-10-21
> Quelle: `docs/Anforderung.md` · Katalog: `docs/requirements/PRD.md`
> Regel dieses Registers: **Nur Risiken, die eine Entscheidung ändern.** Jedes Risiko hat
> eine Gegenmaßnahme, die in einer Anforderung verankert ist.

---

## Top 5 Risiken

### R-01 — Zustellgarantie ohne erkennbare Dubletten (Hoch)

**Befund:** Die Anforderung verlangt at-least-once (FR-021) und gleichzeitig eine
eindeutige Zustell-Kennung. Das Glossar (Abschnitt 4) nennt aber den *einzelnen Versuch*
„Zustellung“ und die Kette „Lieferung“. Nimmt man das wörtlich, trägt jeder Versuch eine
andere Kennung — dann kann der Empfänger eine Dublette **nicht** erkennen, und genau
daran scheitert die Zustellgarantie in der Praxis.

**Auswirkung:** Empfänger verarbeiten dasselbe Ereignis doppelt (Bestellungen!), oder sie
bauen doch wieder eigene Duplikaterkennungen — das Kernproblem der Anforderung bleibt
ungelöst.

**Gegenmaßnahme:** D-06 (drei Kennungen: Ereignis-ID, Zustelllauf-ID, Versuchs-ID) in
US-010 und US-013; der Vertrag in Schritt 2 legt fest, welche Kennung der Empfänger als
Deduplizierungsschlüssel nutzt. Verankert in FR-021.

**Empfehlungsstärke:** hoch — die Festlegung ist im PRD dokumentiert und braucht nur die
Bestätigung bei Gate 1.

### R-02 — Mandantenleck trotz sauberer Anwendung (Hoch)

**Befund:** Die Anforderung verlangt Trennung „in der Oberfläche genauso wie in der
Schnittstelle“ (Abschnitt 3) und auf Datenbankebene (NFR-003). Ein Leck entsteht typisch
nicht im Filter des Controllers, sondern dort, wo ein Entwickler einmal den `WHERE`-Zweig
vergisst — genau deshalb fordert NFR-003 die Durchsetzung in der Datenbank.

**Auswirkung:** Ein fremdes Ereignis (Nutzlast, eventuell personenbezogen) wird sichtbar.
Datenschutz- und Vertrauensverlust, möglicher Vorfall mit Meldepflicht.

**Gegenmaßnahme:** US-022 erzwingt die Trennung in der Datenbank (RLS) und beweist sie
gegen eine echte PostgreSQL-Instanz — auch mit absichtlich fehlendem Anwendungsfilter;
D-01 und D-02 nehmen den Mandanten aus dem Request heraus. Verankert in NFR-003, FR-004.

**Empfehlungsstärke:** hoch — die Umsetzungsform (RLS o. ä.) ist eine Architekturentscheidung
in Schritt 2, die Anforderung ist eindeutig.

### R-03 — Verschlüsselung im Ruhezustand nicht nachweisbar oder nicht durchsetzbar (Hoch)

**Befund:** FR-012 und NFR-004 verlangen Verschlüsselung im Ruhezustand und einen
Schlüssel außerhalb der Datenbank. Abschnitt 13 stellt die Frage, ob das überhaupt ein
Muss ist — und ob dann noch im Zustellprotokoll gesucht werden kann. Solange das Verfahren
und die Schlüsselverwaltung offen sind (O-04), kann ein Team den Dienst nicht belastbar
betreiben: Der Nachweis „im Klartext nicht lesbar“ (Abnahmekriterium 4) hängt am
konkreten Verfahren.

**Auswirkung:** Der Datenschutz-Zusagen des Fachbereichs kann nicht nachgewiesen werden;
die Abnahmekriterien 4 und 7 bleiben unerfüllt.

**Gegenmaßnahme:** US-007 formuliert den Nachweis verfahrensneutral (Suche nach einer
Klartextmarkierung in Datenbank, Index, Journal und Logs), D-11 grenzt die Protokollsuche
auf Metadaten ein, O-04 trägt die Verfahrensfrage mit Empfehlung in Gate 1.
**ARCHITECTURAL_BLOCKER-Kandidat** für Schritt 2.

**Empfehlungsstärke:** mittel — die Verschlüsselungspflicht selbst ist durch die
nummerierten Anforderungen gesetzt; offen sind nur Verfahren und Durchsuchbarkeit.

### R-04 — Daten wachsen unbegrenzt (Aufbewahrung ungeklärt) (Mittel)

**Befund:** Abschnitt 13 stellt Aufbewahrung offen: 30 Tage — für welche Daten, und ab
welchem Zeitpunkt? Der Dienst speichert Ereignisse **und** Zustellprotokolle; beides
wächst mit dem Volumen. Ohne Frist wächst die Datenbank unbegrenzt (Kosten), und die
Löschpflicht wird verfehlt (Datenschutz). Der Service-Admin soll „Aufbewahrung
erzwingen“ können — ohne Frist hat er nichts zu erzwingen.

**Auswirkung:** Steigende Betriebskosten, DSGVO-Verstoß (Speicherbegrenzung), und
uneinheitliche Daten in der Auswertung.

**Gegenmaßnahme:** US-016 baut die Durchsetzung mit **konfigurierbarer** Frist (AC-016-5
prüft das Löschen mit einer im Test gesetzten Frist); D-12 legt fest, dass ohne O-02 kein
Wert vorbelegt wird; O-02 trägt die Empfehlung (30 Tage ab dem letzten Versuch, getrennt
für Ereignisse und Protokolle). Verankert in FR-031.

**Empfehlungsstärke:** mittel — der Mechanismus ist unabhängig vom Wert umsetzbar.

### R-05 — Ereignisse verschwinden still (Hoch)

**Befund:** Das Hauptproblem der Anforderung ist der stille Verlust („der Batch still
fehlschlägt“, „niemand merkt es“). Drei Wege führen dorthin: (a) ein Ereignis ohne
passenden Endpunkt, (b) ein pausierter Endpunkt, (c) eine Zustellung, die nach der
Versuchsgrenze aufgegeben wird. Keiner dieser drei Fälle darf im Protokoll wie ein Erfolg
aussehen.

**Auswirkung:** Dasselbe Symptom wie heute — Daten fehlen, niemand merkt es.

**Gegenmaßnahme:** D-04 (Ereignis ohne Abnehmer wird sichtbar protokolliert), D-03 (Pause
erzeugt keine Warteschlangeneintragung, offene Wiederholungen werden abgebrochen), FR-023
mit US-012 (tote Zustellung erscheint in der Totenliste) und US-015 (Protokoll zeigt
Status, Versuche, Antwortcode). Verankert in FR-023, FR-030, FR-031.

**Empfehlungsstärke:** hoch — die drei Fälle sind bereits als Story und Festlegung
geführt.

---

## Weitere Befunde

| ID | Risiko | Auswirkung | Gegenmaßnahme | Anker | Stärke |
|---|---|---|---|---|---|
| R-06 | **Signaturverfahren bricht bestehende Empfänger.** FR-020 verbietet Änderungen an Verfahren und Header-Namen — wer sie falsch wählt, muss alle Empfänger neu ausrollen. | Totalausfall der Zustellung bei allen Empfängern. | D-07 (Verfahren und Header-Name werden im Vertrag festgeschrieben und versioniert; ein Vertragstest schlägt fehl, wenn eines fehlt — US-009, US-014) | FR-020 | mittel (Verfahrensentscheidung in Schritt 2) |
| R-07 | **AVV/Vertrag über die Auftragsverarbeitung fehlt (O-05).** Fremde Ereignis-Nutzlasten werden an Dritte weitergegeben. | Livegang ohne Rechtsgrundlage; Verstoß gegen die Vertragslage des Fachbereichs. | O-05 wird mit Empfehlung und Owner (Fachbereich + Recht) geführt und ist **Freigabebedingung vor Gate 2**; technisch vorbereitet durch Datenminimierung, Mandantentrennung und Löschfristen | — (Abschnitt 13) | hoch (Bedingung, kein Blocker für Schritt 1) |
| R-08 | **Rate-Limit ohne Zahl (O-01).** FR-033 verlangt Schutz, nennt aber keinen Wert. | Entweder Überlastung des Empfängers oder eine Schutzfunktion, die in der Praxis stört. | D-14 (Mechanismus gebaut und konfigurierbar, ohne Wert nur Zähler erhoben) + O-01 mit Empfehlung (60 Versuche/Endpoint/Minute, verschieben statt blockieren) | FR-033 | mittel |
| R-09 | **Replay-Berechtigung widersprüchlich (O-03).** Die Rollentabelle nennt den Entwickler, FR-024 den Empfänger-Admin. | Eine zu weite Berechtigung erlaubt Replays gegen den Willen des Empfängers; eine zu enge verhindert die Wiederaufnahme nach einem Ausfall. | D-15 (bis zur Entscheidung nur Empfänger-Admin) + O-03 mit Empfehlung | FR-024 | hoch |
| R-10 | **Zustellprozess und Datenbank wachsen zusammen.** Der Dienst ist bewusst kein Event-Store — bei hoher Last wird daraus ungewollt einer. | Betriebskosten, unbegrenztes Wachstum, Verlust der zugesagten Kurzzeitigkeit. | D-12 (konfigurierbare Frist, Durchsetzung in US-016), D-04, US-023 (Mandanten-Sharding, horizontale Skalierung) | NFR-006, FR-031 | mittel |
| R-11 | **Doppelzustellung durch konkurrierende Worker.** Mehrere Instanzen bearbeiten dieselbe Warteschlange. | Verletzte at-least-once-Kontrolle, Empfänger bekommt mehr Dubletten als erwartet. | US-023 AC-023-3 (genau eine Instanz führt den Versuch aus), AC-023-4 (Wiederaufnahme nach Absturz ohne Verlust) | NFR-006, FR-021 | mittel (Mechanismus ist Architektur-Sache) |
| R-12 | **Latenzversprechen wird gebrochen.** NFR-002 (p95 < 200 ms) steht neben einer synchronen Zustellung und einem synchronen Schreibpfad. | Der Entwickler wartet auf Empfänger — der Nutzen des Dienstes kippt. | D-10 (Zustellung im Hintergrundprozess), US-005 (AC-005-4, AC-005-5), US-011 (AC-011-3) | NFR-002 | hoch |

---

## Offene Punkte als Risiko

Die sieben offenen Punkte sind bewusst offen (Abschnitt 13 der Anforderung plus die
Zahlenlücke in FR-022/FR-023 und das Zeitlimit aus Abschnitt 11). Sie sind **kein**
Blocker für Schritt 1, aber sie tragen jeweils ein Risiko:

| ID | Risiko, solange offen | Owner | Empfehlung | Gate |
|---|---|---|---|---|
| O-01 | Schutz gegen Überlastung nicht justierbar | Fachbereich | 60 Versuche/Endpunkt/Minute, verschieben statt blockieren | Gate 1 |
| O-02 | Daten wachsen unbegrenzt, Löschpflicht verfehlt | Fachbereich + Datenschutz | 30 Tage ab letztem Versuch, getrennt für Ereignisse und Protokolle | Gate 1 |
| O-03 | Replay-Berechtigung widersprüchlich | Fachbereich | Empfänger-Admin des Endpunkts | Gate 1 |
| O-04 | Verschlüsselungsverfahren und Durchsuchbarkeit ungeklärt | Fachbereich + Datenschutz (Verfahren: Architektur) | Verschlüsselung bleibt Muss; AEAD mit Secret-Store; Suche nur über Metadaten | Gate 1 |
| O-05 | Livegang ohne AVV | Fachbereich + Recht | AVV vor produktivem Betrieb | Gate 2 |
| O-06 | „Feste Anzahl“ Versuche ohne Wert — tote Zustellungen liegen zu kurz oder zu lange | Fachbereich | 8 Versuche, 1 min Basis, ×2, Deckel 60 min, ± 20 % Streuung | Gate 1 |
| O-07 | Worker-Rechner nicht dimensionierbar, Ausfälle zu spät erkannt | Architektur | 5 s Verbindungs-, 10 s Lese-Timeout | Gate 1 |

---

## Kein Risiko, aber eine Grenze

Was **nicht** in diesem Schritt entschieden wurde und auch nicht entschieden werden
sollte: die 18 FR und 6 NFR sind vollständig abgedeckt, aber **die Zahlenwerte** (Versuche,
Fristen, Raten, Timeouts) sind Parameter, keine Festlegungen des Fachbereichs. Sie sind
bewusst als offene Punkte (O-01, O-02, O-06, O-07) und als Vorschlag in den
Akzeptanzkriterien geführt — ein Akzeptanzkriterium, das eine Zahl fest verdrahtet,
wäre eine erfundene Anforderung.
