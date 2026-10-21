# Anforderungen — Ereignis-Briefkasten mit Zustellgarantie (EchoBox)

> Kompakter Katalog des Schritts 1 (Anforderungserhebung).
> Quelle: `docs/Anforderung.md` (Roh-Input). Volltext: `docs/requirements/PRD.md`.
> Agent: `aisdlc-requirements` · Stand: 2026-10-21

## Vision

Ein zentraler Dienst nimmt Ereignisse entgegen und liefert sie an registrierte Empfänger-URLs weiter — mit Protokoll, mit Wiederholung und mit einer nachvollziehbaren Antwort auf die Frage „ist das angekommen?“. Kein Broker, kein Event-Store, keine zweite Zustandshaltung. Leitsatz: Nichts verschwindet still.

## Rollen

| Rolle | Darf | Stories |
|---|---|---|
| Entwickler | Ereignisse einliefern, Zustellprotokoll einsehen | US-005, US-006, US-007, US-008, US-015, US-020 |
| Empfänger-Admin | Endpunkte registrieren, pausieren, reaktivieren, Secret rotieren, tote Zustellung erneut auslösen | US-001, US-002, US-003, US-004, US-013, US-019 |
| Service-Admin | Endpunkte aller Mandanten sehen, Tote-Zustellungen abholen, Aufbewahrung erzwingen | US-004, US-016, US-017, US-018, US-022, US-023 |

## Anforderungen (IDs unverändert aus dem Roh-Input)

| ID | Anforderung | Abschnitt | Story | Priorität | Offener Punkt |
|---|---|---|---|---|---|
| FR-001 | Endpunkt mit Ziel-URL, Secret und Ereignistypen registrieren; URL muss https sein | 6 | US-001, US-019 | Must-have | — |
| FR-002 | Endpunkt pausieren und reaktivieren; kein Zwischenspeichern, kein Nachliefern | 6 | US-002, US-019 | Must-have | — |
| FR-003 | Secret rotieren; nach der Rotation gilt nur das neue Secret | 6 | US-003, US-019 | Must-have | — |
| FR-004 | Service-Admin sieht alle Endpunkte, Empfänger-Admin nur die eigenen | 6 | US-004 | Must-have | — |
| FR-010 | Entwickler liefert Ereignis ein: Typ, Nutzlast, Idempotenzschlüssel | 7 | US-005 | Must-have | — |
| FR-011 | Gleicher Idempotenzschlüssel erzeugt kein zweites Ereignis; ID des ersten wird zurückgeliefert | 7 | US-006 | Must-have | — |
| FR-012 | Nutzlast wird verschlüsselt gespeichert, nur zur Zustellung entschlüsselt | 7 | US-007 | Must-have | O-04 |
| FR-013 | Ereignisse können bereits verschlüsselt eingeliefert werden | 7 | US-008 | Must-have | — |
| FR-020 | Signatur über Zeitstempel und Nutzlast; Verfahren und Header-Name sind Teil des Vertrags | 8 | US-009 | Must-have | — |
| FR-021 | Zustellung ist at-least-once; jede Zustellung trägt eine eigene Zustell-Kennung | 8 | US-010 | Must-have | — |
| FR-022 | Fehlgeschlagene Zustellung wird mit wachsendem Abstand erneut versucht, mit Streuung | 8 | US-011 | Must-have | O-06, O-07 |
| FR-023 | Nach fester Anzahl erfolgloser Versuche wird die Zustellung aufgegeben (tote Zustellung) | 8 | US-012 | Must-have | O-06 |
| FR-024 | Empfänger-Admin kann aufgegebenes Ereignis erneut auslösen; Versuchszahl beginnt neu | 8 | US-013 | Must-have | O-03 |
| FR-025 | Empfänger kann dauerhafte Ablehnung angeben; Zustellung sofort aufgeben | 8 | US-014 | Must-have | — |
| FR-030 | Zustellprotokoll zeigt Zustand, Versuche, letzter Versuch, Antwortcode, nächster Versuch | 9 | US-015, US-020 | Must-have | — |
| FR-031 | Service-Admin kann tote Zustellungen als Liste abholen | 9 | US-016, US-020 | Must-have | O-02 |
| FR-032 | Je Endpunkt werden Erfolgsquote und Zustelllatenz ausgewiesen | 9 | US-017 | Must-have | — |
| FR-033 | Endpunkt ist gegen zu viele Zustellungen in kurzer Zeit geschützt (Rate steht aus) | 9 | US-018 | Must-have | O-01 |
| NFR-001 | Nutzlast eines Ereignisses ist auf 1 MB begrenzt | 10 | US-005 | Must-have | — |
| NFR-002 | Einliefern antwortet in unter 200 ms (p95); Zustellung ist asynchron | 10 | US-005 | Must-have | — |
| NFR-003 | Mandantentrennung wird auf Datenbankebene erzwiesen | 10 | US-022 | Must-have | — |
| NFR-004 | Verschlüsselung im Ruhezustand; Schlüssel liegt außerhalb der Datenbank | 10 | US-007 | Must-have | O-04 |
| NFR-005 | Oberfläche ohne Maus bedienbar, mit Screenreader nutzbar, WCAG 2.1 AA | 10 | US-021 | Must-have | — |
| NFR-006 | Dienst läuft in Containern, auf eine Instanz je Mandant vorbereitet, skaliert horizontal | 10 | US-023 | Must-have | — |

## Epics

| Epic | Ziel | Stories |
|---|---|---|
| E1 — Endpunkte verwalten | Endpunkte sicher registrieren und steuern — inklusive Pause, Rotation und Mandantensicht | US-001, US-002, US-003, US-004 |
| E2 — Ereignisse einliefern | Ereignisse zuverlässig, idempotent und verschlüsselt annehmen — ohne den Aufrufer zu blockieren | US-005, US-006, US-007, US-008 |
| E3 — Zustellung und Zustellgarantie | Zustellgarantie: signiert, wiederholt, gestreut, nachvollziehbar aufgegeben | US-009, US-010, US-011, US-012, US-013, US-014 |
| E4 — Protokoll und Betrieb | Protokoll, Tote-Zustellungen, Kennzahlen und Schutz gegen Überlastung im Betrieb | US-015, US-016, US-017, US-018 |
| E5 — Oberfläche | Bedienbare, barrierefreie Oberfläche für Endpunkte, Protokoll und Tote Zustellungen | US-019, US-020, US-021 |
| E6 — Mandantentrennung und Betrieb | Mandantentrennung auf Datenbankebene, Containerbetrieb und horizontale Skalierung | US-022, US-023 |

## Festlegungen aus dem Grill-Me (Schritt 1a)

| ID | Festlegung | Bestätigung |
|---|---|---|
| D-01 | Mandant stammt ausschließlich aus der Identität; ein Mandantenparameter wird mit HTTP 422 abgelehnt | Gate 1 |
| D-02 | Fremde Ressourcen werden mit HTTP 404 beantwortet (Existenz bleibt verborgen) | Gate 1 |
| D-03 | Pause: keine Zustellung, kein Zwischenspeichern, kein Nachliefern; offene Wiederholung wird abgebrochen | Gate 1 |
| D-04 | Ereignis ohne Abnehmer wird angenommen und sichtbar als „kein Abnehmer“ protokolliert | Gate 1 |
| D-05 | Idempotenz je (Mandant, Schlüssel); Wiederholung liefert die erste Kennung; abweichende Nutzlast → HTTP 409 | Gate 1 |
| D-06 | Drei Kennungen: Ereignis-ID, Zustelllauf-ID, Versuchs-ID (löst den Glossar-Widerspruch aus Abschnitt 4 vs. FR-021) | Gate 1 |
| D-07 | Signatur über Zeitstempel (UTC) und Nutzlastbytes; Verfahren und Header-Name werden im Vertrag eingefroren | Gate 1 |
| D-08 | Wiederholung bei 5xx, Netzwerkfehler, 408, 429; kein Wiederholen bei 2xx; Ablehnungssignal und übrige 4xx → sofort aufgeben | Gate 1 |
| D-09 | Backoff konfigurierbar, Verdopplung, mit Streuung; konkrete Werte aus O-06 | Gate 1 |
| D-10 | Annahme und Zustellung getrennt; Zustellung im Hintergrundprozess | Gate 1 |
| D-11 | Protokollsuche nur über Metadaten, keine Suche nach Nutzlastinhalt | Gate 1 |
| D-12 | Aufbewahrungsfrist ist konfigurierbar; ohne O-02 wird kein Wert vorbelegt | Gate 1 |
| D-13 | Die 1-MB-Grenze gilt für die eingelieferten Bytes (bei vorverschlüsselter Einlieferung für die Chiffre) | Gate 1 |
| D-14 | Rate-Limit-Mechanismus wird gebaut und konfiguriert; ohne O-01 kein Grenzwert vorbelegt, nur Zähler erhoben | Gate 1 |
| D-15 | Replay: bis zur Entscheidung nur der Empfänger-Admin des Endpunkts (Auflösung des Widerspruchs FR-024 vs. Rollentabelle) | Gate 1 |

## Offene Punkte (bewusst offen — vom Fachbereich so gewollt)

| ID | Thema | Owner | Empfehlung | Blocker-Kandidat Schritt 2 |
|---|---|---|---|---|
| O-01 | Rate-Limit je Endpunkt: welche Rate, und bei Überschreitung blockieren oder verschieben? | Fachbereich / Gate 1 | Startwert 60 Zustellversuche je Endpunkt und Minute; bei Überschreitung verschieben (nicht blockieren, nicht verwerfen). | nein |
| O-02 | Aufbewahrung: für welche Daten, welche Frist, ab welchem Zeitpunkt? | Fachbereich + Datenschutz / Gate 1 | 30 Tage ab dem letzten Zustellversuch, getrennt konfigurierbar für Ereignisse und Zustellprotokolle. | nein |
| O-03 | Wer darf ein totes Ereignis erneut auslösen: Empfänger-Admin, Entwickler oder Plattform-Betrieb? | Fachbereich / Gate 1 | Empfänger-Admin des betroffenen Endpunkts (entspricht FR-024); Entwickler nur mit Mandantenfreigabe; Plattform-Betrieb nicht. | nein |
| O-04 | Verschlüsselung im Ruhezustand: Muss? Und ist das Zustellprotokoll dann noch durchsuchbar? | Fachbereich + Datenschutz (Verfahren: Architektur) / Gate 1 | Verschlüsselung bleibt Muss (FR-012, NFR-004); Verfahren = anerkanntes AEAD mit Schlüssel aus einem Secret-Store; Protokollsuche nur über Metadaten, keine Volltextsuche über Nutzlasten. | ja |
| O-05 | Vertrag über die Auftragsverarbeitung: Vertragspartner, Verantwortlicher, AVV vor Livegang? | Fachbereich + Recht/Datenschutz / Gate 2 | Kein Software-Blocker, aber Livegang-Bedingung: AVV vor dem produktiven Betrieb klären; technisch Datenminimierung, Mandantentrennung, konfigurierbare Löschfristen. | nein |
| O-06 | Wie viele Zustellversuche und welcher Backoff? FR-023 nennt keine Zahl. | Fachbereich / Gate 1 | 8 Versuche, Basisabstand 1 Minute, Verdopplung, Deckel 60 Minuten, Streuung 20 Prozent (Vorschlag - im Code konfigurierbar). | nein |
| O-07 | Zeitlimit je Zustellversuch: wie lange wartet der Dienst auf einen Empfänger? | Architektur / Gate 1 | Verbindungs-Timeout 5 s, Lese-Timeout 10 s (Summe unter 30 s wie in Abschnitt 11 gefordert). | ja |

## Won't have (ausdrücklich ausgeschlossen)

Message-Broker, Event-Store, Batch-Ersatz · Zustellung an Nicht-HTTP-Ziele · eigene Retry-Bibliotheken für Teams · Zustellung an Ziele fremder Mandanten · Volltextsuche über Nutzlasten · feste Instanz je Mandant im Regelbetrieb · Benutzer-/Mandantenverwaltung · Betriebshandbuch (Schritt 9) · Multi-Region-Active-Active

## Abnahmekriterien (aus Abschnitt 12 der Anforderung)

1. Endpunkt registrieren, pausieren, Secret rotieren — ohne dass ein Mandant fremde Daten sieht (US-001, US-002, US-003, US-019, US-022).
2. Ereignis wird angenommen, signiert, zugestellt; bei Fehlschlag erneut versucht, Wiederholung im Protokoll sichtbar (US-005, US-009, US-010, US-011, US-015).
3. Ein totes Ereignis lässt sich erneut auslösen (US-013, US-012).
4. Verschlüsselung im Ruhezustand nachweisbar in Datenbank und Logs (US-007).
5. Oberfläche zeigt Endpunkte, Protokoll und tote Zustellungen, bedienbar mit Tastatur und Screenreader (US-019, US-020, US-021).
6. Zu jeder Anforderung ein Testfall; Nachverfolgung weist Anforderung, Test und Code aus (Traceability-Matrix).

## Codebase-Prüfung (OpenCode, Read-Only)

`src/` existiert nicht — der Dienst ist ein Neuaufbau. Es gibt keine bestehenden Module, keine Altlast und keine Migrationspflicht. Die Zielmodule sind in `docs/requirements/PRD.md` (Abschnitt 12) vorgeschlagen und in Schritt 2 zu bestätigen.

## Artefakte

- `docs/requirements/PRD.md` — PRD, Festlegungen, offene Punkte, Won't-have
- `docs/requirements/stories/US-0xx.json` — 23 User Stories
- `docs/requirements/features/US-0xx.feature` — 23 Gherkin-Features (je Normalfall, Verweigerungsfall, Grenzfall)
- `docs/requirements/traceability_matrix.json` — Anforderung → Story → Test → Code
- `.agent/artifacts/user-stories.md` — lesbare Fassung der Stories
- `.agent/artifacts/risk-register.md` — Risiken und Gegenmaßnahmen
- `.agent/artifacts/traceability.md` — Lückenliste der Traceability-Kette
