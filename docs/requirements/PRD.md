# PRD — Ereignis-Briefkasten mit Zustellgarantie

> **Produkt (Arbeitstitel):** EchoBox — Ereignis-Briefkasten mit Zustellgarantie
> **Quelle:** `docs/Anforderung.md` (Roh-Input, Commit `dea4f21`) — Anforderungs-IDs (FR/NFR) werden unverändert übernommen
> **Schritt:** 1 — Anforderungserhebung · **Agent:** `aisdlc-requirements` · **Stand:** 2026-10-21
> **Fachlicher Ansprechpartner:** Lena Hofmann (Platform Engineering)
> **Begleitartefakte:** `.agent/artifacts/requirements.md`, `.agent/artifacts/user-stories.md`, `.agent/artifacts/risk-register.md`

---

## 1. Vision und Problem

Wir produzieren Ereignisse — Bestellung geändert, Freigabe erteilt, Scan abgeschlossen,
Benutzer angelegt. Heute kommen diese Ereignisse auf zwei Wegen bei den abonnierenden
Systemen an: über einen selbst gebauten Message-Broker oder über einen nächtlichen Batch.
Beides verursacht seit zwei Quartalen wiederholt Ärger:

| Problem heute | Wirkung |
|---|---|
| Der Batch verliert Ereignisse (Ausfall um 02:00) | Daten des Vortags fehlen, der Fehler bleibt still |
| Der Broker kennt keine Zustellgarantie | Wir wissen nicht, ob ein Team die Nachricht verarbeitet hat |
| Jedes Team baut seine eigene Wiederholungslogik | drei Retry-/Backoff-Varianten, eine überschlägt still nach elf Fehlversuchen |
| Doppelte Zustellungen | Empfänger bauen jeder für sich eigene Duplikaterkennungen |

**Die Vision:** ein zentraler Dienst, der Ereignisse entgegennimmt und an registrierte
Empfänger-URLs weiterleitet — mit Protokoll, mit Wiederholung und mit einer
nachvollziehbaren Antwort auf die Frage „ist das angekommen?“. Die Zustellgarantie
wandert damit vom Empfänger zu uns, und das wird sichtbar.

**Was der Dienst ausdrücklich nicht ist:** kein Message-Broker, kein Event-Store, kein
Ersatz für den internen Bus. Es gibt bewusst keine zweite Zustandshaltung, die gegen die
erste konsistent gehalten werden müsste. Der Dienst hält nur, was er für die Zustellung
braucht — und das so kurz wie möglich.

**Leitsatz für alle Entscheidungen:** Nichts verschwindet still. Jedes eingelieferte
Ereignis ist über seinen gesamten Weg sichtbar — ob zugestellt, ob verworfen, ob tot.

---

## 2. Ziele und Erfolgskriterien

| # | Ziel | Messbares Erfolgskriterium | Quelle |
|---|---|---|---|
| Z1 | Kein stiller Ereignisverlust | Jedes eingelieferte Ereignis hat nach 24 h einen endgültigen Zustand (zugestellt, abgebrochen, tot) und ist im Protokoll auffindbar | Abschnitt 1, 12.2 |
| Z2 | Nachvollziehbare Zustellgarantie | Für jedes Ereignis sind Zustand, Versuchsanzahl, letzter Versuch, Antwortcode und nächster Versuch abrufbar | FR-030, 12.2 |
| Z3 | Kein fremder Mandanten-Blick | Ein Anwendungsfehler kann kein fremdes Ereignis zeigen — Trennung wird auf Datenbankebene erzwiesen | Abschnitt 3, NFR-003, 12.1 |
| Z4 | Empfänger braucht keine eigene Wiederholungslogik | Drei Teams bauen keine Retry-/Backoff-Logik mehr; alle nutzen den Dienst | Abschnitt 1, 5 |
| Z5 | Empfänger kann Doppeltzustellungen erkennen | Jeder Zustellversuch trägt Ereignis-ID, Zustelllauf-ID und Versuchs-ID; ein Timeout-Duplikat ist am stabilen Zustelllauf erkennbar | FR-021, 12.2 |
| Z6 | Betrieb überprüfbar | Je Endpunkt sind Erfolgsquote und Zustelllatenz ausgewiesen; tote Zustellungen sind mandantenübergreifend abholbar | FR-031, FR-032 |
| Z7 | Nachweisbare Vertraulichkeit | Nutzlast ist in Datenbank und Logs nicht im Klartext lesbar; Schlüssel liegt außerhalb der Datenbank | FR-012, NFR-004, 12.4 |
| Z8 | Bedienbar ohne Maus | Oberfläche vollständig per Tastatur bedienbar, WCAG 2.1 AA, Screenreader-tauglich | NFR-005, 12.5 |

### Nicht-Ziele (bewusst nicht angestrebt)

- Kein Latenzversprechen für die **Zustellung** — nur für die Annahme (NFR-002).
- Kein Ersatz für den internen Ereignisbus und keine Batch-Nachbildung.
- Kein Empfang von Ereignissen von außen (der Dienst nimmt nur an, was eingeliefert wird).

---

## 3. Nutzer und Rollen

Der Mandant steckt in der Identität. Er ist **kein** Filterparameter, den ein Aufrufer
frei wählen darf (Abschnitt 3 der Anforderung) — siehe Festlegung D-01.

| Rolle | Wer | Bedarf | Was er darf | Stories |
|---|---|---|---|---|
| **Entwickler** | Team, das das Ereignis erzeugt | Ereignisse zuverlässig abgeben und den Zustellverlauf nachvollziehen | Ereignisse einliefern, Zustellprotokoll einsehen, totes Endpunkt-Ziel neu auslösen (Berechtigung offen, O-03) | US-005, US-006, US-007, US-008, US-015, US-020 |
| **Empfänger-Admin** | Team des abonnierenden Systems | Kontrolle über die eigene Ziel-URL und ihre Empfangsbereitschaft | Endpunkte registrieren, pausieren, reaktivieren, Secret rotieren, tote Zustellung erneut auslösen | US-001, US-002, US-003, US-004, US-013, US-019 |
| **Service-Admin** | Plattform-Betrieb | Überblick über alle Mandanten, Aufräumen, Durchsetzen von Aufbewahrung | Endpunkte aller Mandanten sehen, Tote-Zustellungsliste abholen, Aufbewahrung erzwingen | US-004, US-016, US-017, US-018, US-022, US-023 |
| **Empfangendes System** (kein Mensch) | Zielsystem hinter der Empfänger-URL | Ereignisse einmalig verarbeiten | Antworten mit 2xx (Erfolg), mit dem Ablehnungssignal (dauerhaft nicht gewünscht) oder mit Fehlercodes | US-009, US-010, US-014 |

---

## 4. Fachbegriffe

| Begriff | Bedeutung in diesem Dienst | Abgrenzung |
|---|---|---|
| **Endpunkt** | Eine Registrierung: Ziel-URL, Secret, gewünschte Ereignistypen, Zustand | Gehört genau einem Mandanten |
| **Ereignis** | Eine eingelieferte Nutzlast mit Typ, Zeitpunkt und Idempotenzschlüssel | Wird verschlüsselt gespeichert (FR-012) |
| **Zustellung** | Ein Versuch, ein Ereignis an einen Endpunkt zu senden | Ein Versuch = eine Zustellung (Glossar der Anforderung) |
| **Lieferung / Zustelllauf** | Das Protokoll einer Zustellung über alle Versuche | Ein Zustelllauf umfasst alle Versuche zu einem Ereignis an einen Endpunkt |
| **Tote Zustellung** | Eine Zustellung, die nach erschöpften Versuchen aufgegeben wurde | Erscheint in der Totenliste (FR-031) |
| **Ereignis-ID** | Stabile Kennung des Ereignisses (über alle Zustellläufe und Mandanten hinweg eindeutig) | Vom Empfänger als Deduplizierungsschlüssel nutzbar |
| **Zustelllauf-ID** | Stabile Kennung eines Zustelllaufs — über alle Versuche dieses Laufs gleich | Ein Replay erzeugt einen neuen Zustelllauf |
| **Versuchs-ID** | Eindeutige Kennung eines einzelnen HTTP-Versuchs | Jeder Versuch hat eine eigene |
| **Aufgegeben / tot** | Zustellung endet ohne Erfolg; keine weiteren Versuche | Nur durch erneutes Auslösen (FR-024) oder durch das Erreichen der Aufbewahrungsfrist beendet |
| **Abgelehnt** | Der Empfänger hat dauerhaft signalisiert, dass er das Ereignis nicht möchte | Kein Fehler — der Empfänger hat recht (FR-025) |

> **Glossar-Konflikt (aus dem Grill-Me, siehe D-06):** Das Glossar der Anforderung nennt
> den einzelnen Versuch „Zustellung“ und die Kette über alle Versuche „Lieferung“.
> FR-021 verlangt aber, dass *jede* Zustellung eine eigene Kennung trägt, „damit der
> Empfänger doppelte Zustellungen erkennen kann“. Mit nur einer Kennung je Versuch kann
> der Empfänger eine Dublette nicht erkennen. Deshalb führt der Dienst drei Kennungen
> (Ereignis-ID, Zustelllauf-ID, Versuchs-ID), und der Vertrag (Schritt 2) legt fest,
> welche davon der Empfänger als Deduplizierungsschlüssel verwendet.

---

## 5. Scope

### 5.1 Funktionaler Scope (Epics)

| Epic | Ziel | Stories |
|---|---|---|
| **E1 — Endpunkte verwalten** | Registrierung, Pausierung, Secret-Rotation und rollenabhängige Sicht auf Endpunkte | US-001, US-002, US-003, US-004 |
| **E2 — Ereignisse einliefern** | Annahme mit Idempotenz, Größengrenze, Latenzversprechen und Verschlüsselung im Ruhezustand | US-005, US-006, US-007, US-008 |
| **E3 — Zustellung und Zustellgarantie** | Signatur, at-least-once mit Kennungen, Wiederholung mit Backoff und Streuung, Aufgabe, Replay, endgültige Ablehnung | US-009, US-010, US-011, US-012, US-013, US-014 |
| **E4 — Protokoll und Betrieb** | Zustellprotokoll, Tote-Zustellungen, Kennzahlen, Rate-Limit, Aufbewahrung | US-015, US-016, US-017, US-018 |
| **E5 — Oberfläche** | Bedienung von Endpunkten, Protokoll und Totenliste; Barrierefreiheit | US-019, US-020, US-021 |
| **E6 — Mandanttrennung und Betrieb** | Trennung auf Datenbankebene, Containerbetrieb, Mandanten-Sharding, horizontale Skalierung | US-022, US-023 |

### 5.2 Nicht-funktionaler Scope

| ID | Thema | Zielgröße (messbar) | Story |
|---|---|---|---|
| NFR-001 | Größe der Nutzlast | ≤ 1.048.576 Byte (1 MB), 1 Byte darüber → HTTP 413 | US-005 |
| NFR-002 | Antwortzeit der Annahme | p95 < 200 ms über 1.000 Einlieferungen; Zustellung asynchron (Hintergrundprozess) | US-005, US-011 |
| NFR-003 | Mandantentrennung | Datenbankebene erzwungen; ein Anwendungsfehler ohne Filter zeigt 0 fremde Zeilen | US-022 |
| NFR-004 | Verschlüsselung im Ruhezustand | Kein Klartexttreffer in DB, Index, Journal und Logs; Schlüssel außerhalb der DB | US-007 |
| NFR-005 | Barrierefreiheit | WCAG 2.1 AA: 0 Verstöße der Klasse „kritisch“/„schwer“; Kontrast ≥ 4,5:1 (Text) bzw. 3:1 (Elemente); volle Tastaturbedienung | US-021 |
| NFR-006 | Betrieb | Container ohne Secrets, Mandanteninstanz vorbereitet, horizontal skalierbar, keine Doppelzustellung bei Parallelbetrieb | US-023 |

### 5.3 Won't have — ausdrücklich ausgeschlossen

Diese Punkte stehen **nicht** im Backlog. Sie sind bewusst außerhalb des Scopes.

| Nicht enthalten | Begründung |
|---|---|
| Message-Broker, Event-Store, interner Bus-Ersatz | Ausdrucklich ausgeschlossen (Abschnitt 2) — der Dienst ist ein Zustell-Wrapper, keine zweite Zustandshaltung |
| Batch-Verarbeitung / nächtlicher Batch-Ersatz | Der Batch ist die Ursache des Problems, nicht die Lösung (Abschnitt 1) |
| Zustellung an Nicht-HTTP-Ziele (E-Mail, AMQP, Kafka, SQS) | Der Vertrag ist „Registrierung von Empfänger-URLs“ (FR-001) |
| Zustelllogik, Retry-Bibliotheken oder Empfänger-SDKs für Teams | Ausdrückliches Ziel: die Teams bauen diese Logik nicht mehr selbst (Z4) |
| Zustellung an Ziele eines anderen Mandanten | Der Mandant ist die Grenze des Dienstes (Abschnitt 3) |
| Volltextsuche / Suche nach Nutzlastinhalt | Konflikt mit FR-012 (Verschlüsselung im Ruhezustand) — Suche nur über Metadaten, siehe D-11 und O-04 |
| Feste Instanz je Mandant im Regelbetrieb | NFR-006 verlangt nur, dass der Dienst **vorbereitet** ist; geteilter Betrieb bleibt Standard |
| Benutzer-, Rollen- oder Mandantenverwaltung | Identität und Mandant kommen von außen (Abschnitt 3); der Dienst verwaltet sie nicht |
| Historisierung/Wiederholung von Ereignissen über den Dienst hinaus | Nur die Zustellung wird wiederholt (FR-024); ein Ereignis-Archiv mit Replay an Empfänger ist nicht Teil des Auftrags |
| Mandantenportal, Self-Service-Portal, Rechnungsstellung, Mandantenauswertung | Nicht Teil des Auftrags |
| Betriebshandbuch, Runbook, On-Call-Prozesse | gehören zu Schritt 9 (Deployment und Betrieb) |
| Länder-/Währungsspezifische Logik, Multi-Region-Active-Active | nicht Teil des Auftrags |

---

## 6. Anforderungen im Detail

Alle IDs stammen unverändert aus `docs/Anforderung.md`. Die Spalte „Story“ nennt die
User Story, die die Anforderung abdeckt; die vollständige Zuordnung steht in
`docs/requirements/traceability_matrix.json`.

| ID | Anforderung (Kurzfassung) | Quelle | Story | Priorität | Offener Punkt |
|---|---|---|---|---|---|
| FR-001 | Endpunkt mit Ziel-URL, Secret und Ereignistypen registrieren; URL muss `https` sein | Abschnitt 6 | US-001, US-019 | Must-have | — |
| FR-002 | Endpunkt pausieren und reaktivieren; Ereignisse werden nicht zwischengespeichert und nicht nachgeliefert | Abschnitt 6 | US-002, US-019 | Must-have | — |
| FR-003 | Secret rotieren; nach der Rotation gilt nur das neue Secret | Abschnitt 6 | US-003, US-019 | Must-have | — |
| FR-004 | Service-Admin sieht alle Endpunkte aller Mandanten, Empfänger-Admin nur die eigenen | Abschnitt 6 | US-004 | Must-have | — |
| FR-010 | Entwickler liefert ein Ereignis ein: Typ, Nutzlast, Idempotenzschlüssel | Abschnitt 7 | US-005 | Must-have | — |
| FR-011 | Derselbe Idempotenzschlüssel liefert kein zweites Ereignis; die Wiederholung ist erfolgreich und liefert die Kennung des ersten Ereignisses | Abschnitt 7 | US-006 | Must-have | — |
| FR-012 | Nutzlast wird verschlüsselt gespeichert und nur zur Zustellung entschlüsselt — im Ruhezustand weder in der Datenbank noch in einem Log im Klartext lesbar | Abschnitt 7 | US-007 | Must-have | O-04 |
| FR-013 | Ereignisse können optional bereits verschlüsselt eingeliefert werden; dann entfällt das Entschlüsseln vor der Zustellung | Abschnitt 7 | US-008 | Must-have | — |
| FR-020 | Jede Zustellung trägt eine Signatur über Zeitstempel und Nutzlast; Verfahren und Header-Name sind Teil der öffentlichen Schnittstelle und danach unveränderlich | Abschnitt 8 | US-009 | Must-have | — |
| FR-021 | Zustellung ist at-least-once; jede Zustellung trägt eine eigene Zustell-Kennung, damit der Empfänger doppelte Zustellungen erkennen kann | Abschnitt 8 | US-010 | Must-have | — |
| FR-022 | Fehlgeschlagene Zustellung wird erneut versucht, mit wachsendem Abstand zwischen den Versuchen und mit Streuung | Abschnitt 8 | US-011 | Must-have | O-06, O-07 |
| FR-023 | Nach einer festen Anzahl erfolgloser Versuche wird die Zustellung aufgegeben und landet in der Liste der toten Zustellungen | Abschnitt 8 | US-012 | Must-have | O-06 |
| FR-024 | Empfänger-Admin kann ein aufgegebenes Ereignis erneut auslösen; die erneute Zustellung beginnt mit der Versuchszahl neu | Abschnitt 8 | US-013 | Must-have | O-03 |
| FR-025 | Empfänger kann in der Antwort angeben, dass er das Ereignis dauerhaft nicht möchte; die Zustellung wird sofort aufgegeben | Abschnitt 8 | US-014 | Must-have | — |
| FR-030 | Zustellprotokoll zeigt je Zustellung: Zustand, Anzahl Versuche, Zeitpunkt des letzten Versuchs, Antwortcode des Empfängers, nächsten Versuchszeitpunkt | Abschnitt 9 | US-015, US-020 | Must-have | — |
| FR-031 | Service-Admin kann die toten Zustellungen als Liste abholen | Abschnitt 9 | US-016, US-020 | Must-have | O-02 |
| FR-032 | Je Endpunkt werden Erfolgsquote und Zustelllatenz ausgewiesen | Abschnitt 9 | US-017 | Must-have | — |
| FR-033 | Endpunkt ist gegen zu viele Zustellungen in kurzer Zeit geschützt; die zulässige Rate steht noch nicht fest | Abschnitt 9 | US-018 | Must-have | O-01 |
| NFR-001 | Nutzlast eines Ereignisses ist auf 1 MB begrenzt | Abschnitt 10 | US-005 | Must-have | — |
| NFR-002 | Das Einliefern antwortet in unter 200 ms (p95); die Zustellung ist asynchron | Abschnitt 10 | US-005, US-011 | Must-have | — |
| NFR-003 | Mandantentrennung wird auf Datenbankebene erzwiesen, nicht nur in der Anwendung | Abschnitt 10 | US-022 | Must-have | — |
| NFR-004 | Verschlüsselung im Ruhezustand mit anerkanntem Verfahren; der Schlüssel liegt außerhalb der Datenbank | Abschnitt 10 | US-007 | Must-have | O-04 |
| NFR-005 | Oberfläche ohne Maus bedienbar, mit Screenreader nutzbar, Kontraste mindestens WCAG 2.1 AA | Abschnitt 10 | US-021 | Must-have | — |
| NFR-006 | Dienst läuft in Containern, ist auf eine Instanz je Mandant vorbereitet und skaliert horizontal | Abschnitt 10 | US-023 | Must-have | — |

### MoSCoW-Zusammenfassung

| Priorität | Umfang |
|---|---|
| **Must-have** | Alle 18 FR und alle 6 NFR dieser Anforderung — der Auftrag enthält keine Anforderung, die entfallen kann. |
| **Should-have** | Nur die **Durchsetzung der Aufbewahrung** innerhalb US-016 (AC-016-5): der Mechanismus wird gebaut, die Frist folgt aus O-02. |
| **Could-have** | Mandantenweise eigene Instanz im Regelbetrieb; Export der Kennzahlen; Suchfunktion über Ereignistyp und Zeitraum über das Protokoll hinaus. **Nicht Bestandteil dieses Schritts.** |
| **Won't have** | Abschnitt 5.3 — ausdrücklich ausgeschlossen, nicht im Backlog. |

---

## 7. Festlegungen aus dem Grill-Me (Schritt 1a)

Das Grill-Me (Skill `aisdlc-grill-stress`) wurde gegen den Roh-Input geführt: Designbaum
abfragen, Zielkonflikte und Edge Cases suchen, Deepening-Opportunitäten und
Spezifikationslücken prüfen. Wo die Anforderung eine Frage offenließ, die für eine
testbare Story nicht beantwortet werden muss, wurde eine **Festlegung** getroffen —
sie ist im nächsten Schritt durch Gate 1 zu bestätigen. Die fünf bewusst offenen Fragen
aus Abschnitt 13 der Anforderung wurden **nicht** festgelegt, sondern als O-01 bis O-05
erfasst.

| ID | Festlegung | Grundlage | Wirkung | Zu bestätigen |
|---|---|---|---|---|
| D-01 | Der Mandant stammt ausschließlich aus der Identität. Ein mitgelieferter Mandantenparameter wird mit HTTP 422 abgelehnt, nie als Filter benutzt. | Abschnitt 3: „Der Mandant steckt in der Identität, er ist kein Filterparameter“ | API-Verträge (Schritt 2), alle Stories | Gate 1 |
| D-02 | Fremde Ressourcen werden mit HTTP 404 beantwortet, nicht mit 403 — so verrät die Antwort nicht, dass ein fremdes Objekt existiert. | Abschnitt 3 (Mandantentrennung), NFR-003 | einheitliche Fehlersemantik aller Stories | Gate 1 |
| D-03 | **Pause-Semantik:** Ein pausierter Endpunkt bekommt keine Zustellung. Für Ereignisse, die während der Pause eintreffen, entsteht kein Zustellvorgang und keine Warteschlangeneintragung; ein bereits geplanter Wiederholungsvorgang wird abgebrochen. Nach der Reaktivierung wird **nichts** nachgeliefert. | FR-002: „nicht zwischengespeichert und nicht nachgeliefert“ | US-002, US-013 (Replay bei pausiertem Endpunkt) | Gate 1 |
| D-04 | **Ereignis ohne Abnehmer:** Ein Ereignis, für das kein Endpunkt den Ereignistyp abonniert hat, wird angenommen und gespeichert und ist im Protokoll sichtbar — es entsteht keine Zustellung, aber der Zustand „kein Abnehmer“ ist sichtbar. Kein stilles Verschwinden. | Abschnitt 1 (stille Verluste sind das Hauptproblem), FR-030 | US-015, US-016 | Gate 1 |
| D-05 | **Idempotenz:** Der Gültigkeitsbereich des Idempotenzschlüssels ist das Paar (Mandant, Schlüssel). Eine Wiederholung ist erfolgreich und liefert die ID des ersten Ereignisses; die erste Nutzlast wird nie überschrieben. Derselbe Schlüssel mit abweichender Nutzlast wird mit HTTP 409 abgelehnt (statt still die erste Nutzlast zurückzugeben — sonst hielte der Aufrufer neue Daten für gespeichert). | FR-011, Abschnitt 12.2 | US-006, Vertragsentwurf in Schritt 2 | Gate 1 |
| D-06 | **Drei Kennungen:** Ereignis-ID (stabil), Zustelllauf-ID (stabil je Lauf, ein Replay erzeugt einen neuen Lauf), Versuchs-ID (eindeutig je HTTP-Versuch). Der Empfänger dedupliziert über Ereignis-ID bzw. Zustelllauf-ID. | Auflösung des Glossar-Widerspruchs zwischen Abschnitt 4 und FR-021 | US-010, US-013, Vertrag in Schritt 2 | Gate 1 |
| D-07 | **Signatur:** Signiert werden Zeitstempel (UTC) und die exakt gesendeten Nutzlastbytes; Schlüssel ist das Endpunkt-Secret. Verfahren und Header-Name werden im Vertrag (OpenAPI) festgeschrieben und sind danach versioniert — eine Änderung ist ein breaking change und braucht eine neue Hauptversion. | FR-020 („dürfen sich danach nicht mehr ändern“) | US-009, Vertrag in Schritt 2 | Gate 1 |
| D-08 | **Wiederholungsregel:** Wiederholung bei 5xx, Netzwerkfehler, HTTP 408 und HTTP 429. Keine Wiederholung bei 2xx. Das dokumentierte Ablehnungssignal (Vorschlag: HTTP 410) und alle übrigen 4xx bedeuten: sofort aufgeben, Status „abgelehnt“. | FR-022, FR-025 (das Ablehnungssignal selbst ist im Vertrag zu benennen) | US-011, US-014 | Gate 1 |
| D-09 | **Backoff:** konfigurierbar, mit Verdopplung des Abstands und Streuung; die konkreten Werte folgen aus O-06 (Vorschlag: 8 Versuche, Basis 1 min, Deckel 60 min, ± 20 % Streuung). | FR-022, FR-023 | US-011, US-012 | Gate 1 (Werte), O-06 |
| D-10 | **Asynchronität:** Annahme und Zustellung sind getrennt; die Zustellung läuft in einem Hintergrundprozess. Ein Empfänger, der 30 Sekunden braucht, blockiert den Annahme-Request nicht. | Abschnitt 11 (Rahmenbedingung), NFR-002 | US-005, US-011, US-023 | Gate 1 |
| D-11 | **Suche im Protokoll:** Das Zustellprotokoll ist über Metadaten durchsuchbar (Ereignis-ID, Typ, Endpunkt, Status, Zeitraum). Eine Suche nach Nutzlastinhalt gibt es nicht — das wäre mit FR-012 unvereinbar. | FR-012, NFR-004 | US-007, US-015 | Gate 1 (Zusammenhang mit O-04) |
| D-12 | **Aufbewahrung:** Die Frist ist ein konfigurierbarer Parameter; ohne die Festlegung aus O-02 wird kein Wert vorbelegt. | Abschnitt 13 (bewusst offen) | US-016 | Gate 1 (Wert), O-02 |
| D-13 | **Größengrenze:** Die 1-MB-Grenze gilt für die eingelieferten Bytes — bei bereits verschlüsselter Einlieferung also für die Chiffre, nicht für den Klartext. | NFR-001, FR-013 | US-005, US-008 | Gate 1 |
| D-14 | **Rate-Limit:** Der Begrenzungsmechanismus (Grenzwert je Endpunkt und Zeitfenster, konfigurierbare Strategie) wird gebaut und konfigurierbar gehalten. Bis O-01 entschieden ist, wird **kein** Grenzwert vorbelegt; erhoben werden nur die Zähler je Endpunkt und Zeitfenster als Datenbasis. | FR-033, Abschnitt 13 | US-018 | Gate 1 (Wert), O-01 |
| D-15 | **Rollenrechte:** Der Empfänger-Admin des betroffenen Endpunkts darf eine tote Zustellung erneut auslösen (entspricht FR-024). Bis zur Entscheidung aus O-03 erhalten alle anderen Rollen HTTP 403. Die Rollentabelle aus Abschnitt 3 nennt dagegen auch den Entwickler — genau das ist die offene Frage. | FR-024 gegen Abschnitt 3 | US-013 | Gate 1, O-03 |

> **Wichtiger Hinweis zu O-01 bis O-05:** Diese fünf Fragen stammen wörtlich aus
> Abschnitt 13 der Anforderung und sind **bewusst offen** — vom Fachbereich so gewollt.
> Sie sind kein Blocker für diesen Schritt und werden hier nicht festgelegt. Sie gehören
> an Gate 1.

---

## 8. Offene Punkte

Owner: **Fachbereich** = Plattform-Team Interne Systeme (Lena Hofmann). Gate 1 = menschliche
Freigabe nach Schritt 2. Gate 2 = menschliche Freigabe nach Schritt 8.

| ID | Thema | Owner | Empfehlung | Warum das der Fachbereich entscheidet | Wirkung |
|---|---|---|---|---|---|
| O-01 | Rate-Limit je Endpunkt: Welche Rate ist „angemessen“ — und was passiert bei Überschreitung: blockieren oder verschieben? | Fachbereich / Gate 1 | Startwert 60 Zustellversuche je Endpunkt und Minute; bei Überschreitung **verschieben** (nicht blockieren, nicht verwerfen), mit sichtbarem Status im Protokoll | Die erlaubte Rate richtet sich danach, was ein abonnierendes System an Last verträgt — das ist eine Vereinbarung über die Betriebslast, keine technische Grenze. Ohne Zahl ist die Schutzfunktion nicht justierbar. | US-018 (Mechanismus ist gebaut und konfigurierbar, Wert und Strategie offen) |
| O-02 | Aufbewahrung: 30 Tage — für Ereignisse, für Zustellungen oder für beides? Und beginnt die Frist mit dem Einliefern oder mit dem letzten Versuch? | Fachbereich + Datenschutz / Gate 1 | 30 Tage ab dem **letzten Zustellversuch**, getrennt konfigurierbar für Ereignisse und Zustellprotokolle; Durchsetzung automatisch, Erzwingung durch den Service-Admin zusätzlich möglich | Die Frist ist eine Abwägung zwischen Nachvollziehbarkeit (Betrieb, Support) und Löschpflicht (Datenschutz, Datenvolumen) — das ist eine Produkt- und Rechtsentscheidung. | US-016 (AC-016-5 ist mit konfigurierbarer Frist umgesetzt) |
| O-03 | Wer darf ein totes Ereignis erneut auslösen: Empfänger-Admin, Entwickler oder der Plattform-Betrieb? | Fachbereich / Gate 1 | Der **Empfänger-Admin des betroffenen Endpunkts** (entspricht FR-024); der Entwickler nur für eigene Ereignisse und nur mit ausdrücklicher Freigabe des Mandanten. Der Plattform-Betrieb darf **nicht** replayen — sonst verliert der Empfänger die Kontrolle über seinen eigenen Endpunkt. | Die Rollentabelle (Abschnitt 3) nennt den Entwickler, FR-024 den Empfänger-Admin — das ist ein echter Widerspruch in der Anforderung, den nur der Fachbereich auflösen kann. | US-013 (bis zur Entscheidung gilt D-15) |
| O-04 | Verschlüsselung im Ruhezustand: Ist das ein Muss? Und wenn ja — können wir dann im Zustellprotokoll überhaupt noch nach einem Ereignis suchen? | Fachbereich + Datenschutz (Verfahren: Architektur) / Gate 1 | Die Verschlüsselung bleibt **Muss** (FR-012 und NFR-004 sind nummerierte Anforderungen). Das Verfahren (anerkanntes AEAD, Schlüssel aus einem Secret-Store) ist eine Architekturentscheidung. Zur Suche: Volltextsuche über Nutzlasten wird **nicht** gebaut; die Protokollsuche bleibt über Metadaten (D-11). | Die Aussage „ist das ein Muss“ berührt die Datenschutz-Zusage gegenüber den Mandanten. Die Frage nach der Durchsuchbarkeit ist eine Produktentscheidung mit Kosten (Klartextindex vs. kein Index) — sie gehört zum Fachbereich, nicht zur Technik. | US-007, US-015; **ARCHITECTURAL_BLOCKER-Kandidat** (Verfahren, Mandantentrennung auf DB-Ebene, Verschlüsselungsschnittstelle) für Schritt 2 |
| O-05 | Vertrag über die Auftragsverarbeitung: Wir verarbeiten fremde Ereignis-Nutzlasten und geben sie an Dritte weiter. Wer ist Vertragspartner, wer Verantwortlicher, und brauchen wir eine Vereinbarung vor dem Livegang? | Fachbereich + Recht/Datenschutz / Gate 2 | Kein Software-Blocker, aber **Livegang-Bedingung**: vor dem produktiven Betrieb ist ein AVV-/Auftragsverarbeitungsvertrag mit dem Fachbereich als Vertragspartner zu klären. Technisch wird der Dienst darauf vorbereitet: Datenminimierung, Mandantentrennung, konfigurierbare Löschfristen, Protokollierbarkeit. | Ein Vertragsverhältnis mit Dritten kann die Fachbereich nicht durch Technik ersetzen lassen — der Dienst kann die Einhaltung belegen, nicht vereinbaren. | Kein Story-Blocker; Freigabebedingung vor dem Deploy (Gate 2) |
| O-06 | Wie viele Zustellversuche, welcher Backoff? FR-023 verlangt eine „feste Anzahl“, nennt sie aber nicht. | Fachbereich / Gate 1 | 8 Versuche, Basisabstand 1 Minute, Verdopplung, Deckel 60 Minuten, Streuung ± 20 % (Vorschlag zur Bestätigung — im Code konfigurierbar) | Die Zahl bestimmt, wie lange ein Empfängerausfall toleriert wird und wie lange tote Zustellungen liegen. Das ist eine Betriebs- und Zusagenfrage gegenüber den abonnierenden Teams, keine technische Größe. | US-011, US-012 (Vorgabewert im Code, jederzeit überschreibbar) |
| O-07 | Zeitlimit je Zustellversuch: Wie lange wartet der Dienst auf einen Empfänger? | Architektur / Gate 1 | Verbindungs-Timeout 5 s, Lese-Timeout 10 s (Summe < 30 s, wie in Abschnitt 11 gefordert); ein langsamer Empfänger wird als Fehlversuch gezählt | Technische Größe — sie bestimmt, wie der Worker-Rechner dimensioniert wird und wie schnell ein Ausfall erkannt wird. Der Fachbereich muss sie nur kennen, nicht setzen. | US-011, US-023; **ARCHITECTURAL_BLOCKER-Kandidat** für Schritt 2 |

### Nicht offene, sondern entschiedene Punkte

Die folgenden Fragen standen in der Anforderung nicht offen und wurden deshalb hier
**entschieden** (siehe D-01 bis D-15): Mandantenherkunft, Fehlerstatus für fremde
Ressourcen, Pause-Semantik, Umgang mit Ereignissen ohne Abnehmer, Idempotenzumfang und
Konfliktantwort, Kennungen, Wiederholungsregel je Antwortcode, Grenze der 1-MB-Regel.

---

## 9. Abnahmekriterien (Definition of Done)

Die sechs Punkte aus Abschnitt 12 der Anforderung, übersetzt in prüfbare Bedingungen:

| # | Anforderung (Abschnitt 12) | Nachweis |
|---|---|---|
| 1 | Endpunkt registrieren, pausieren, Secret rotieren — ohne dass ein Mandant fremde Daten sieht | US-001, US-002, US-003 (API) + US-019 (Oberfläche) + US-022 (Trennung auf DB-Ebene) |
| 2 | Ereignis wird angenommen, signiert, zugestellt; scheitert die Zustellung, wird erneut versucht, die Wiederholung ist im Protokoll sichtbar | US-005, US-009, US-010, US-011, US-015 |
| 3 | Ein totes Ereignis lässt sich erneut auslösen | US-013 (+ US-012 für die Totenliste) |
| 4 | Verschlüsselung im Ruhezustand ist nachweisbar — in der Datenbank und in den Logs | US-007 (Suche nach Klartextmarkierung in DB, Index, Journal und Logs) |
| 5 | Oberfläche zeigt Endpunkte, Zustellprotokoll und tote Zustellungen; bedienbar mit Tastatur und Screenreader | US-019, US-020, US-021 |
| 6 | Zu jeder Anforderung gibt es einen Testfall; die Nachverfolgung weist Anforderung, Test und Code aus | `docs/requirements/traceability_matrix.json` + `.agent/tools/traceability_check.py` (in Schritt 5/6 mit echten Test- und Codepfaden zu füllen) |

---

## 10. Traceability

| Artefakt | Ort |
|---|---|
| PRD (dieses Dokument) | `docs/requirements/PRD.md` |
| User Stories (JSON) | `docs/requirements/stories/US-001.json` … `US-023.json` |
| BDD-Features (Gherkin) | `docs/requirements/features/US-001.feature` … `US-023.feature` |
| Traceability-Matrix | `docs/requirements/traceability_matrix.json` |
| Kompakter Katalog | `.agent/artifacts/requirements.md`, `.agent/artifacts/user-stories.md` |
| Risiken | `.agent/artifacts/risk-register.md` |
| Lückenliste | `.agent/artifacts/traceability.md` |

**Kette:** Anforderung (FR/NFR) → Story (US) → Akzeptanzkriterium (AC) → Feature-Szenario
→ Test (Schritt 5) → Code (Schritt 6). In Schritt 1 sind Test- und Codebezüge
**Platzhalter** mit `coverage_state: "seeded"`; Schritt 5 füllt die Tests, Schritt 6 den
Code, und beide Schritte gleichen die Matrix gegen das Dateisystem ab.

Prüfung: `python3 .agent/tools/traceability_check.py` (Exit 0 = keine Lücke).

---

## 11. Risiken

Top-Risiken und Gegenmaßnahmen stehen in `.agent/artifacts/risk-register.md`. Die fünf
gewichtigsten: R-01 Zustellgarantie ohne nachvollziehbare Kennungen, R-02 Mandantenleck,
R-03 Verschlüsselung im Ruhezustand, R-04 Aufbewahrung, R-05 stiller Ereignisverlust.

---

## 12. Hinweise für Schritt 2 (Konzept & Architektur)

**Ergebnis der Codebase-Prüfung (OpenCode, Read-Only):** `src/` existiert in diesem
Projekt **nicht** — es gibt keinen Anwendungscode, keine bestehenden Module und keine
Altlast, an die der Dienst andocken müsste. Es handelt sich um einen Neuaufbau; die
betroffenen Module entstehen erst in Schritt 5/6. Vorgeschlagene Zielmodule (aus den
Anforderungen abgeleitet, in Schritt 2 zu bestätigen):

| Modul | Inhalt | Stories |
|---|---|---|
| `endpoint` | Endpunktregistrierung, Pause/Aktivierung, Secret-Rotation, rollenabhängige Sicht | US-001 … US-004, US-019 |
| `event` | Annahme, Idempotenz, Verschlüsselung im Ruhezustand, vorverschlüsselte Einlieferung | US-005 … US-008 |
| `delivery` | Zustelllauf, Versuch, Backoff, Streuung, Aufgabe, Replay, Ablehnung, Rate-Limit | US-009 … US-014, US-018 |
| `crypto` | Signatur, Payload-Verschlüsselung, Secret-Verwaltung | US-003, US-007, US-009 |
| `protocol` | Zustellprotokoll, Tote-Zustellungen, Kennzahlen, Aufbewahrung | US-015 … US-017 |
| `tenant` | Mandantenkontext, Durchsetzung auf Datenbankebene | US-004, US-022 |
| `frontend` | Oberfläche Endpunkte, Protokoll, Tote Zustellungen, Barrierefreiheit | US-019 … US-021 |

**Vertragspunkte, die Schritt 2 festlegen und danach einfrieren muss:** Signaturverfahren
und Header-Name (FR-020), das Ablehnungssignal des Empfängers (FR-025), die drei
Kennungen und ihre Header (FR-021, D-06), die Fehler- und Statusvokabular der
Zustellvorgänge (FR-030), die Rollenmatrix der Endpunkte (FR-004).

**ARCHITECTURAL_BLOCKER-Kandidaten** (blockieren Schritt 2 nicht, sind dort zu entscheiden):
Verschlüsselungsverfahren und Schlüsselverwaltung (O-04), Durchsetzung der
Mandantentrennung auf Datenbankebene (NFR-003), Zeitlimit je Zustellversuch (O-07),
Zustandsmodell der Zustellung (ausstehend → gesendet / fehlgeschlagen → aufgegeben /
abgelehnt / abgebrochen), Zustellmechanismus des Hintergrundprozesses (Worker,
Übernahme nach Absturz).

**Rahmenbedingungen aus Abschnitt 11 der Anforderung (keine Festlegungen des
Fachbereichs, aber bindend für die Architektur):** Java 21, Spring Boot, PostgreSQL,
React/TypeScript; Zustellung im Hintergrundprozess; Containerbetrieb mit getrenntem
Staging und Produktion.
