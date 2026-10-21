# User Stories — Ereignis-Briefkasten mit Zustellgarantie (EchoBox)

> Lesbare Fassung der Stories aus `docs/requirements/stories/US-0xx.json`.
> Je Story: Rolle, Nutzen, abgedeckte Anforderungen, Akzeptanzkriterien und die drei BDD-Szenarien.
> **Eiserne Regel:** Keine Story ohne beobachtbares Akzeptanzkriterium.

**Kurzstatistik:** 6 Epics · 23 Stories · 101 Akzeptanzkriterien · 69 BDD-Szenarien · 24 Anforderungen (18 FR + 6 NFR) · 7 offene Punkte

## Epic E1 — Endpunkte verwalten

### US-001 — Endpunkt registrieren

**Rolle:** Empfänger-Admin  
**Story:** Als Empfänger-Admin möchte ich einen Endpunkt mit Ziel-URL, Secret und gewünschten Ereignistypen registrieren, damit Ereignisse meines Systems an eine kontrollierte, mandanteneigene URL zugestellt werden.  
**Anforderungen:** FR-001 · verwandt: NFR-004  
**Priorität:** must-have · **Quelle:** Abschnitt 6 (FR-001) · **Feature:** `docs/requirements/features/US-001.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-001-1 | ein angemeldeter Empfänger-Admin des Mandanten M1 | er einen Endpunkt mit https-Ziel-URL, einem Secret und der Liste der gewünschten Ereignistypen registriert | legt der Dienst den Endpunkt mit dem Status „aktiv“ an, weist ihm M1 aus der Identität zu und liefert die Endpunkt-ID zurück |
| AC-001-2 | dieselbe Anmeldung | die Ziel-URL kein https-Schema trägt (http, ftp oder file) | lehnt der Dienst die Registrierung mit HTTP 422 ab und legt keinen Endpunkt an |
| AC-001-3 | eine Ziel-URL mit eingebetteten Zugangsdaten (https://user:pass@host/...) | der Empfänger-Admin den Endpunkt registriert | lehnt der Dienst mit HTTP 422 ab, weil Zugangsdaten ausschließlich über das Secret laufen |
| AC-001-4 | ein neu registrierter Endpunkt | die Registrierungsantwort ausgeliefert wird | enthält die Antwort das Secret nicht im Klartext; das Secret wird verschlüsselt abgelegt und nur einmalig erfasst |
| AC-001-5 | eine Registrierungsanfrage | ein Mandantenparameter (z. B. organisation_id) mitgeschickt wird | lehnt der Dienst mit HTTP 422 ab, weil der Mandant ausschließlich aus der Identität stammt |

Szenarien:

- **Normalfall** — Endpunkt wird angelegt
  - Given ein Empfänger-Admin des Mandanten M1
  - When er registriert einen Endpunkt mit https-URL, Secret und Ereignistypen
  - Then der Endpunkt existiert mit dem Status „aktiv“
  - And der Mandant M1 stammt aus der Identität, nicht aus dem Request
  - And die Antwort enthält die Endpunkt-ID, aber kein Secret im Klartext
- **Verweigerungsfall** — Ziel-URL ohne https
  - Given ein Empfänger-Admin des Mandanten M1
  - When er registriert einen Endpunkt mit einer http-URL
  - Then der Dienst antwortet mit HTTP 422
  - And es wird kein Endpunkt angelegt
- **Grenzfall** — URL mit eingebetteten Zugangsdaten
  - Given ein Empfänger-Admin des Mandanten M1
  - When er registriert einen Endpunkt mit https://user:pass@host/path
  - Then der Dienst antwortet mit HTTP 422
  - And Zugangsdaten im URL werden abgelehnt; nur das Secret trägt die Authentifizierung

### US-002 — Endpunkt pausieren und reaktivieren

**Rolle:** Empfänger-Admin  
**Story:** Als Empfänger-Admin möchte ich einen Endpunkt pausieren und wieder aktivieren, damit ich die Zustellung kontrolliert aussetze, ohne dass Ereignisse nachgeliefert werden.  
**Anforderungen:** FR-002  
**Priorität:** must-have · **Quelle:** Abschnitt 6 (FR-002) · **Feature:** `docs/requirements/features/US-002.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-002-1 | ein aktiver Endpunkt und ein eingeliefertes Ereignis eines seiner Ereignistypen | der Empfänger-Admin den Endpunkt pausiert | entsteht für dieses Ereignis kein Zustellversuch und wird für diesen Endpunkt kein Zustellvorgang in eine Warteschlange eingestellt |
| AC-002-2 | ein während der Pause eingeliefertes Ereignis | der Empfänger-Admin den Endpunkt reaktiviert | wird dieses Ereignis nicht nachgeliefert - es gibt keinen Zustellversuch dafür und keinen nachzutragenden Eintrag |
| AC-002-3 | ein Endpunkt des Mandanten M2 | ein Empfänger-Admin von M1 ihn pausiert | antwortet der Dienst mit HTTP 404 und der Zustand des Endpunkts bleibt unverändert |
| AC-002-4 | ein Endpunkt mit einem offenen Wiederholungsvorgang | der Endpunkt pausiert wird | wird der offene Wiederholungsvorgang abgebrochen (kein weiterer Versuch) und die Zustellung endet mit dem Status „abgebrochen (pausiert)“ |
| AC-002-5 | ein pausierter Endpunkt | die Endpunktliste gelesen wird | zeigt der Endpunkt den Status „pausiert“, und die Anzahl der Zustellversuche bleibt unverändert |

Szenarien:

- **Normalfall** — Endpunkt wird pausiert
  - Given ein aktiver Endpunkt mit einem offenen Zustellvorgang
  - When der Empfänger-Admin pausiert den Endpunkt
  - Then der Endpunkt steht auf „pausiert“
  - And für den Endpunkt wird kein neuer Zustellversuch gestartet
  - And ein bereits geplanter Wiederholungsvorgang wird abgebrochen
- **Verweigerungsfall** — fremder Endpunkt
  - Given ein Empfänger-Admin des Mandanten M1
  - When er pausiert einen Endpunkt des Mandanten M2
  - Then der Dienst antwortet mit HTTP 404 und der fremde Endpunkt bleibt unverändert
- **Grenzfall** — Ereignisse während der Pause
  - Given ein pausierter Endpunkt
  - When während der Pause wird ein Ereignis eingeliefert und danach wird der Endpunkt reaktiviert
  - Then das Ereignis wird nicht zugestellt und nicht nachgeliefert
  - And nach der Reaktivierung existiert für dieses Ereignis kein Zustellversuch

### US-003 — Secret rotieren

**Rolle:** Empfänger-Admin  
**Story:** Als Empfänger-Admin möchte ich das Secret meines Endpunkts rotieren, damit ein verdächtiges Secret ausgetauscht werden kann, ohne den Endpunkt neu anzulegen.  
**Anforderungen:** FR-003 · verwandt: NFR-004  
**Priorität:** must-have · **Quelle:** Abschnitt 6 (FR-003) · **Feature:** `docs/requirements/features/US-003.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-003-1 | ein Endpunkt mit Secret S1 | der Empfänger-Admin auf Secret S2 rotiert | verwendet der Dienst ab dem nächsten Zustellversuch ausschließlich S2; eine Zustellung mit einer Signatur über S1 wird nicht mehr erzeugt |
| AC-003-2 | eine durchgeführte Rotation | die Endpunktverlaufshistorie gelesen wird | ist der Rotationszeitpunkt mit der auslösenden Identität vermerkt; das alte Secret wird nicht im Klartext ausgegeben |
| AC-003-3 | ein Entwickler desselben Mandanten | er die Rotation auslöst | antwortet der Dienst mit HTTP 403 und das Secret bleibt unverändert |
| AC-003-4 | ein Endpunkt, dessen Secret S1 gerade rotiert wurde | eine Zustellung erfolgt | prüft der Empfänger die Signatur mit S2, so ist die Prüfung erfolgreich; mit S1 schlägt sie fehl |

Szenarien:

- **Normalfall** — Rotation
  - Given ein Endpunkt mit Secret S1
  - When der Empfänger-Admin rotiert auf Secret S2
  - Then die nächste Zustellung trägt eine Signatur, die mit S2 prüfbar ist
  - And das alte Secret wird nicht mehr verwendet
- **Verweigerungsfall** — unberechtigte Rolle
  - Given ein Entwickler desselben Mandanten
  - When er die Rotation auslöst
  - Then der Dienst antwortet mit HTTP 403 und das Secret bleibt unverändert
- **Grenzfall** — laufende Zustellung
  - Given eine Zustellung, die bereits mit S1 signiert wurde
  - When im Anschluss wird auf S2 rotiert
  - Then die bereits signierte Zustellung bleibt mit S1 signiert, alle folgenden mit S2

### US-004 — Endpunkte nach Rolle und Mandant sehen

**Rolle:** Empfänger-Admin / Service-Admin  
**Story:** Als Empfänger-Admin möchte ich nur die Endpunkte meines Mandanten sehen und als Service-Admin alle Endpunkte aller Mandanten, damit die Mandantentrennung in der Oberfläche genauso gilt wie in der Schnittstelle.  
**Anforderungen:** FR-004 · verwandt: NFR-003  
**Priorität:** must-have · **Quelle:** Abschnitt 6 (FR-004), Abschnitt 3 (Rollen) · **Feature:** `docs/requirements/features/US-004.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-004-1 | die Mandanten M1 und M2 mit je einem Endpunkt | ein Empfänger-Admin von M1 die Endpunktliste abruft | enthält die Liste ausschließlich den Endpunkt von M1 |
| AC-004-2 | ein Service-Admin | er die Endpunktliste aller Mandanten abruft | erhält er die Endpunkte aller Mandanten, jeweils mit dem Mandantenkennzeichen |
| AC-004-3 | ein Empfänger-Admin von M1 | er die Endpunkt-ID eines Endpunkts von M2 direkt abruft | antwortet der Dienst mit HTTP 404 und es werden keine Daten des fremden Endpunkts ausgegeben |
| AC-004-4 | eine Anfrage mit einem Mandantenparameter | die Identität einen anderen Mandanten trägt | wird der Parameter mit HTTP 422 abgelehnt; der Mandant stammt ausschließlich aus der Identität |

Szenarien:

- **Normalfall** — Empfänger-Admin sieht eigene Endpunkte
  - Given zwei Mandanten mit je einem Endpunkt
  - When ein Empfänger-Admin von M1 ruft die Endpunktliste ab
  - Then die Liste enthält nur den Endpunkt von M1
- **Verweigerungsfall** — mandantenübergreifende Liste
  - Given ein Empfänger-Admin von M1
  - When er die mandantenübergreifende Endpunktliste anfordert
  - Then der Dienst antwortet mit HTTP 403
- **Grenzfall** — direkter Zugriff auf fremde ID
  - Given ein Empfänger-Admin von M1
  - When er die Endpunkt-ID von M2 direkt abruft
  - Then der Dienst antwortet mit HTTP 404 ohne Daten des fremden Endpunkts

## Epic E2 — Ereignisse einliefern

### US-005 — Ereignis einliefern

**Rolle:** Entwickler  
**Story:** Als Entwickler möchte ich ein Ereignis mit Typ, Nutzlast und Idempotenzschlüssel einliefern, damit es von unserem zentralen Dienst an die abonnierten Endpunkte zugestellt wird, ohne dass mein Aufruf wartet.  
**Anforderungen:** FR-010, NFR-001, NFR-002  
**Priorität:** must-have · **Quelle:** Abschnitt 7 (FR-010), Abschnitt 10 (NFR-001, NFR-002), Abschnitt 11 · **Feature:** `docs/requirements/features/US-005.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-005-1 | ein berechtigter Entwickler des Mandanten M1 | er ein Ereignis mit Typ, Nutzlast und Idempotenzschlüssel einliefert | nimmt der Dienst das Ereignis an, liefert die Ereignis-ID zurück und startet die Zustellung erst nach der Antwort (asynchron) |
| AC-005-2 | eine Nutzlast von exakt 1.048.576 Byte (1 MB) | sie eingeliefert wird | wird das Ereignis angenommen |
| AC-005-3 | eine Nutzlast von 1.048.577 Byte | sie eingeliefert wird | lehnt der Dienst mit HTTP 413 ab und legt kein Ereignis an |
| AC-005-4 | ein Empfänger, der 30 Sekunden für die Antwort braucht | das Ereignis eingeliefert wird | ist die Annahmeantwort bereits gesendet und wird der Annahme-Request nicht blockiert |
| AC-005-5 | 1.000 aufeinanderfolgende Einlieferungen im Betrieb | die Antwortzeiten gemessen werden | liegt die Annahmeantwort in unter 200 ms (p95) |
| AC-005-6 | eine Einlieferung ohne Ereignistyp oder ohne Idempotenzschlüssel | sie eingeliefert wird | antwortet der Dienst mit HTTP 422 und legt kein Ereignis an |

Szenarien:

- **Normalfall** — Normalfall — Annahme und asynchrone Zustellung
  - Given ein berechtigter Entwickler des Mandanten M1
  - When er ein Ereignis mit Typ, Nutzlast und Idempotenzschlüssel einliefert
  - Then der Dienst nimmt das Ereignis an und liefert die Ereignis-ID zurück
  - And die Zustellung startet im Hintergrundprozess, nicht im Request-Thread
  - And die Annahmeantwort liegt unter 200 ms (p95)
- **Verweigerungsfall** — Verweigerungsfall — unvollständige Einlieferung
  - Given ein berechtigter Entwickler
  - When er ein Ereignis ohne Idempotenzschlüssel einliefert
  - Then der Dienst antwortet mit HTTP 422 und legt kein Ereignis an
- **Grenzfall** — Grenzfall — Nutzlast an der 1-MB-Grenze
  - Given ein berechtigter Entwickler
  - When er eine Nutzlast von genau 1.048.576 Byte einliefert
  - Then der Dienst nimmt das Ereignis an
  - And eine Nutzlast von 1.048.577 Byte wird mit HTTP 413 abgelehnt

### US-006 — Idempotente Einlieferung

**Rolle:** Entwickler  
**Story:** Als Entwickler möchte ich, dass eine wiederholte Einlieferung mit demselben Idempotenzschlüssel kein zweites Ereignis erzeugt, damit ein Retry meines Clients keine Doppelzustellung auslöst.  
**Anforderungen:** FR-011  
**Priorität:** must-have · **Quelle:** Abschnitt 7 (FR-011) · **Feature:** `docs/requirements/features/US-006.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-006-1 | ein Ereignis E mit Idempotenzschlüssel K im Mandanten M1 | dasselbe Ereignis erneut mit K eingeliefert wird | entsteht kein zweites Ereignis; die Antwort ist erfolgreich und liefert die ID von E zurück |
| AC-006-2 | das Ereignis E | die Wiederholung mit K erfolgt | bleiben Typ und Nutzlast von E unverändert und die Anzahl der Ereignisse von M1 mit K bleibt bei 1 |
| AC-006-3 | ein Ereignis mit K im Mandanten M1 | im Mandanten M2 dasselbe K verwendet wird | entsteht in M2 ein eigenes Ereignis; die Wiederholung in M1 wird davon nicht berührt |
| AC-006-4 | ein Ereignis E mit K | E mit K, aber abweichender Nutzlast eingeliefert wird | antwortet der Dienst mit HTTP 409 und E bleibt unverändert |

Szenarien:

- **Normalfall** — Normalfall — Wiederholung liefert die erste Kennung
  - Given ein bereits eingeliefertes Ereignis E mit Idempotenzschlüssel K
  - When der Entwickler liefert dasselbe Ereignis erneut mit K ein
  - Then die Antwort ist erfolgreich und nennt die ID von E
  - And es existiert genau ein Ereignis mit K
- **Verweigerungsfall** — Verweigerungsfall — gleicher Schlüssel, andere Nutzlast
  - Given ein Ereignis E mit Idempotenzschlüssel K
  - When der Entwickler liefert mit K eine abweichende Nutzlast ein
  - Then der Dienst antwortet mit HTTP 409 und E bleibt unverändert
- **Grenzfall** — Grenzfall — gleicher Schlüssel in einem anderen Mandanten
  - Given ein Ereignis mit K im Mandanten M1
  - When der Mandant M2 liefert mit demselben K ein Ereignis ein
  - Then M2 erhält ein eigenes Ereignis mit eigener Kennung
  - And die Idempotenz ist je Mandant abgegrenzt

### US-007 — Verschlüsselung im Ruhezustand

**Rolle:** Entwickler / Service-Admin  
**Story:** Als Entwickler möchte ich, dass meine Nutzlast verschlüsselt gespeichert wird, damit sie weder in der Datenbank noch in einem Log im Klartext lesbar ist.  
**Anforderungen:** FR-012, NFR-004 · offene Punkte: O-04  
**Priorität:** must-have · **Quelle:** Abschnitt 7 (FR-012), Abschnitt 10 (NFR-004) · **Feature:** `docs/requirements/features/US-007.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-007-1 | ein eingeliefertes Ereignis mit der Klartextmarkierung „TOP-SECRET-PAYLOAD“ | die Datenbank (Tabellen, Indizes, Journal) nach dieser Markierung durchsucht wird | wird kein Treffer erzielt — die Nutzlast ist im Ruhezustand nicht im Klartext lesbar |
| AC-007-2 | dasselbe Ereignis | die Anwendungs- und Zugriffsprotokolle nach der Markierung durchsucht werden | wird kein Treffer erzielt |
| AC-007-3 | ein verschlüsselt gespeichertes Ereignis | der Zustellprozess es entschlüsselt und zustellt | sind die zugestellten Bytes identisch mit den eingelieferten Klartextbytes |
| AC-007-4 | der Verschlüsselungsschlüssel | Datenbankschema, Container-Image und Konfigurationsdateien des Projekts geprüft werden | ist der Schlüssel in keinem dieser Artefakte enthalten; er wird aus einer Secret-Verwaltung injiziert |
| AC-007-5 | ein verschlüsselt gespeichertes Ereignis | im Zustellprotokoll nach dem Ereignis gesucht wird | ist die Suche über Metadaten (Ereignis-ID, Typ, Endpunkt, Status, Zeitraum) möglich; eine Suche nach Nutzlastinhalt existiert nicht |

Szenarien:

- **Normalfall** — Normalfall — Nutzlast im Ruhezustand verschlüsselt
  - Given ein eingeliefertes Ereignis mit der Klartextmarkierung „TOP-SECRET-PAYLOAD“
  - When die Datenbank und die Protokolle nach dieser Markierung durchsucht werden
  - Then es gibt keinen Treffer im Klartext
  - And beim Zustellen werden die ursprünglichen Klartextbytes geliefert
- **Verweigerungsfall** — Verweigerungsfall — Schlüssel darf nicht im Projekt liegen
  - Given der Verschlüsselungsschlüssel
  - When Schema, Image und Konfiguration werden geprüft
  - Then ist der Schlüssel in keinem Artefakt enthalten
  - And der Schlüssel kommt aus einer Secret-Verwaltung
- **Grenzfall** — Grenzfall — Suche im Zustellprotokoll
  - Given ein verschlüsselt gespeichertes Ereignis
  - When im Zustellprotokoll nach Ereignis-ID und Typ gefiltert wird
  - Then der Treffer wird gefunden
  - And eine Suche nach Nutzlastinhalt ist nicht vorhanden

### US-008 — Bereits verschlüsselte Einlieferung

**Rolle:** Entwickler  
**Story:** Als Entwickler möchte ich ein bereits verschlüsseltes Ereignis einliefern können, damit der Dienst die Nutzlast nicht entschlüsseln muss, bevor er zustellt.  
**Anforderungen:** FR-013  
**Priorität:** must-have · **Quelle:** Abschnitt 7 (FR-013) · **Feature:** `docs/requirements/features/US-008.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-008-1 | ein Ereignis, das als „bereits verschlüsselt“ eingeliefert wird | es gespeichert und zugestellt wird | werden die gelieferten Bytes unverändert gespeichert und beim Zustellen ohne vorheriges Entschlüsseln weitergegeben |
| AC-008-2 | eine Einlieferung mit dem Kennzeichen „bereits verschlüsselt“ und erkennbarer Klartextnutzlast | sie eingeliefert wird | antwortet der Dienst mit HTTP 422 und legt kein Ereignis an |
| AC-008-3 | ein als verschlüsselt eingeliefertes Ereignis | die eingelieferten Bytes die Grenze von 1 MB überschreiten | gilt dieselbe Grenze wie bei Klartext; der Dienst lehnt mit HTTP 413 ab |

Szenarien:

- **Normalfall** — Normalfall — Durchreichen ohne Entschlüsseln
  - Given ein Entwickler mit einem bereits verschlüsselten Ereignis
  - When er es mit dem Kennzeichen „bereits verschlüsselt“ einliefert
  - Then der Dienst speichert und liefert die Bytes unverändert aus
  - And vor der Zustellung wird nicht entschlüsselt
- **Verweigerungsfall** — Verweigerungsfall — Kennzeichen passt nicht zur Nutzlast
  - Given ein Entwickler
  - When er das Kennzeichen „bereits verschlüsselt“ mit einer Klartextnutzlast kombiniert
  - Then der Dienst antwortet mit HTTP 422
- **Grenzfall** — Grenzfall — Größengrenze bei verschlüsselter Nutzlast
  - Given ein Entwickler
  - When er ein verschlüsseltes Ereignis mit mehr als 1 MB einliefert
  - Then der Dienst antwortet mit HTTP 413

## Epic E3 — Zustellung und Zustellgarantie

### US-009 — Signierte Zustellung

**Rolle:** Empfänger-Admin  
**Story:** Als Empfänger-Admin möchte ich, dass jede Zustellung über Zeitstempel und Nutzlast signiert ist, damit mein Empfänger die Echtheit des Ereignisses prüfen kann.  
**Anforderungen:** FR-020  
**Priorität:** must-have · **Quelle:** Abschnitt 8 (FR-020) · **Feature:** `docs/requirements/features/US-009.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-009-1 | ein Endpunkt mit Secret S | eine Zustellung erfolgt | trägt der HTTP-Request eine Signatur, die der Empfänger über Zeitstempel und Nutzlast mit S nachrechnen und bestätigen kann |
| AC-009-2 | eine zugestellte Zustellung | der Empfänger die Signatur mit einem falschen Secret oder mit veränderter Nutzlast prüft | schlägt die Prüfung fehl und die Zustellung gilt als nicht erfolgreich zugestellt |
| AC-009-3 | der öffentliche Vertrag des Dienstes | er geprüft wird | sind Verfahren und Header-Name der Signatur dort benannt; ein Vertragstest schlägt fehl, wenn eines von beiden fehlt |
| AC-009-4 | zwei Endpunkte, die dieselbe Ereignisnutzlast erhalten | die Signaturen der beiden Zustellungen verglichen werden | sind sie verschieden, weil das Secret des jeweiligen Endpunkts in die Signatur eingeht |
| AC-009-5 | der Zeitstempel einer Zustellung | die Signatur nachgerechnet wird | ist der Zeitstempel UTC-basiert und die Signatur über genau die gesendeten Nutzlastbytes berechnet |

Szenarien:

- **Normalfall** — Normalfall — Signatur prüft beim Empfänger
  - Given ein Endpunkt mit Secret S
  - When eine Zustellung mit Nutzlast und Zeitstempel erfolgt
  - Then der Empfänger kann die Signatur mit S bestätigen
- **Verweigerungsfall** — Verweigerungsfall — manipulierte Nutzlast
  - Given eine signierte Zustellung
  - When die Nutzlast vor der Prüfung verändert wird
  - Then schlägt die Signaturprüfung fehl
  - And die Zustellung gilt nicht als erfolgreich zugestellt
- **Grenzfall** — Grenzfall — gleiche Nutzlast, verschiedene Endpunkte
  - Given zwei Endpunkte mit unterschiedlichen Secrets
  - When beide erhalten dasselbe Ereignis
  - Then tragen die beiden Zustellungen verschiedene Signaturen

### US-010 — Zustellkennungen und at-least-once

**Rolle:** Empfänger-Admin  
**Story:** Als Empfänger-Admin möchte ich, dass jede Zustellung eigene Kennungen trägt, damit mein Empfänger doppelte Zustellungen erkennen und selbst entscheiden kann.  
**Anforderungen:** FR-021  
**Priorität:** must-have · **Quelle:** Abschnitt 8 (FR-021), Abschnitt 4 (Fachbegriffe) · **Feature:** `docs/requirements/features/US-010.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-010-1 | ein Ereignis E an einen Endpunkt | die Zustellung erfolgt | trägt jeder Zustellversuch (a) die Ereignis-ID, (b) eine Zustelllauf-ID, die über alle Versuche desselben Laufs gleich bleibt, und (c) eine eigene Versuchs-ID |
| AC-010-2 | ein Empfänger, der wegen eines Timeouts nicht geantwortet hat und deshalb eine Wiederholung erhält | er beide Anfragen vergleicht | tragen sie dieselbe Zustelllauf-ID und dieselbe Ereignis-ID, aber unterschiedliche Versuchs-IDs — er kann die Dublette erkennen |
| AC-010-3 | ein Empfänger, der mit 2xx antwortet | der Zustellvorgang abgeschlossen ist | erfolgt kein weiterer Versuch für dieses Ereignis an diesen Endpunkt |
| AC-010-4 | ein Ereignis an zwei Endpunkte | beide zugestellt werden | tragen die Zustellläufe verschiedene Zustelllauf-IDs, aber dieselbe Ereignis-ID |

Szenarien:

- **Normalfall** — Normalfall — Kennungen am Zustelllauf
  - Given ein Ereignis E an einen Endpunkt
  - When die Zustellung erfolgt
  - Then trägt der Request die Ereignis-ID, eine Zustelllauf-ID und eine Versuchs-ID
- **Verweigerungsfall** — Verweigerungsfall — Erfolg beendet die Kette
  - Given ein Empfänger, der mit 2xx antwortet
  - When der Zustellvorgang abgeschlossen wird
  - Then erfolgt kein weiterer Versuch für dieses Ereignis an diesen Endpunkt
- **Grenzfall** — Grenzfall — Dublette nach Timeout
  - Given ein Empfänger, der wegen eines Timeouts nicht geantwortet hat
  - When eine Wiederholung eingeht
  - Then tragen beide Anfragen dieselbe Zustelllauf-ID und unterschiedliche Versuchs-IDs

### US-011 — Wiederholung mit Backoff und Streuung

**Rolle:** Service-Admin  
**Story:** Als Service-Admin möchte ich, dass fehlgeschlagene Zustellungen mit wachsendem Abstand erneut versucht werden und die Versuche über die Endpunkte gestreut sind, damit ein Ausfall nicht zu einem Laststurm wird.  
**Anforderungen:** FR-022 · offene Punkte: O-06, O-07  
**Priorität:** must-have · **Quelle:** Abschnitt 8 (FR-022) · **Feature:** `docs/requirements/features/US-011.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-011-1 | ein Zustellversuch, der mit einem 5xx oder einem Netzwerkfehler fehlschlägt | der nächste Versuch geplant wird | liegt der Abstand zum vorherigen Versuch mindestens doppelt so hoch wie der vorherige Abstand (Backoff mit Basisabstand; Werte konfigurierbar) |
| AC-011-2 | 100 Endpunkte, die gleichzeitig fehlschlagen | die Zeitpunkte der nächsten Versuche berechnet werden | sind sie nicht alle identisch — die Streuung (Jitter) wird angewendet |
| AC-011-3 | ein fehlgeschlagener Versuch | die Wiederholung geplant wird | läuft der Versuch im Hintergrundprozess und nicht im Request-Thread der Einlieferung |
| AC-011-4 | ein Empfänger, der mit 2xx antwortet | die Wiederholungsplanung läuft | wird kein weiterer Versuch geplant |
| AC-011-5 | die konfigurierten Basiswerte des Backoffs | die geplanten Wartezeiten der ersten fünf Versuche ausgegeben werden | steigen sie monoton an und kein geplanter Zeitpunkt liegt in der Vergangenheit |

Szenarien:

- **Normalfall** — Normalfall — Backoff mit wachsendem Abstand
  - Given ein Endpunkt, der mit 5xx antwortet
  - When mehrere Zustellversuche geplant werden
  - Then wächst der Abstand zwischen den Versuchen von Versuch zu Versuch
  - And die Versuche laufen im Hintergrundprozess
- **Verweigerungsfall** — Verweigerungsfall — Erfolg beendet die Wiederholungen
  - Given ein Empfänger, der mit 2xx antwortet
  - When die Wiederholungsplanung läuft
  - Then wird kein weiterer Versuch geplant
- **Grenzfall** — Grenzfall — Streuung über viele Endpunkte
  - Given 100 Endpunkte, die gleichzeitig fehlschlagen
  - When die Zeitpunkte der nächsten Versuche verglichen werden
  - Then sind die Zeitpunkte nicht alle identisch

### US-012 — Aufgabe nach erschöpften Versuchen

**Rolle:** Entwickler / Service-Admin  
**Story:** Als Service-Admin möchte ich, dass eine Zustellung nach einer festen Anzahl erfolgloser Versuche aufgegeben wird und sichtbar in der Liste der toten Zustellungen landet, damit kein Ereignis still verschwindet.  
**Anforderungen:** FR-023 · offene Punkte: O-06  
**Priorität:** must-have · **Quelle:** Abschnitt 8 (FR-023) · **Feature:** `docs/requirements/features/US-012.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-012-1 | ein Endpunkt, der dauerhaft mit 5xx antwortet | die konfigurierte Anzahl der Versuche (Vorgabewert 8, siehe O-06) erreicht ist | wird die Zustellung aufgegeben, erscheint sie in der Liste der toten Zustellungen und es erfolgt kein weiterer Versuch |
| AC-012-2 | eine aufgegebene Zustellung | das Zustellprotokoll gelesen wird | stehen dort die Anzahl der Versuche, der Zeitpunkt des letzten Versuchs und der Antwortcode des Empfängers |
| AC-012-3 | eine aufgegebene Zustellung | das Zeitfenster des nächsten Versuchs verstreicht | bleibt die Versuchsanzahl unverändert — es findet kein automatischer Neuversuch statt |
| AC-012-4 | eine Konfiguration mit der Versuchsgrenze 1 | ein Zustellversuch fehlschlägt | endet die Zustellung nach genau einem Versuch als „aufgegeben“ — die konfigurierte Grenze wird nicht unterlaufen |

Szenarien:

- **Normalfall** — Normalfall — Zustellung wird aufgegeben
  - Given ein Endpunkt, der dauerhaft mit 5xx antwortet
  - When die konfigurierte Anzahl der Versuche erreicht ist
  - Then die Zustellung erscheint in der Liste der toten Zustellungen
  - And ein weiterer Versuch findet nicht statt
- **Verweigerungsfall** — Verweigerungsfall — kein automatischer Neuversuch
  - Given eine aufgegebene Zustellung
  - When das Zeitfenster des nächsten Versuchs verstreicht
  - Then bleibt die Versuchsanzahl unverändert
- **Grenzfall** — Grenzfall — Versuchsgrenze 1
  - Given eine Konfiguration mit der Versuchsgrenze 1
  - When ein Zustellversuch fehlschlägt
  - Then die Zustellung endet nach genau einem Versuch als „aufgegeben“

### US-013 — Tote Zustellung erneut auslösen

**Rolle:** Empfänger-Admin  
**Story:** Als Empfänger-Admin möchte ich eine aufgegebene Zustellung erneut auslösen können, damit ich ein nach einem Ausfall verpasstes Ereignis doch noch verarbeiten kann.  
**Anforderungen:** FR-024 · offene Punkte: O-03  
**Priorität:** must-have · **Quelle:** Abschnitt 8 (FR-024), Abschnitt 13 (Replay-Berechtigung) · **Feature:** `docs/requirements/features/US-013.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-013-1 | eine tote Zustellung | der Empfänger-Admin des betroffenen Endpunkts sie erneut auslöst | startet ein neuer Zustelllauf mit der Versuchsanzahl 1; beide Läufe sind im Zustellprotokoll getrennt sichtbar |
| AC-013-2 | die tote Zustellung | sie erneut ausgelöst wird | bleibt der ursprüngliche Datensatz im Protokoll unverändert erhalten (Historie) |
| AC-013-3 | eine Rolle, die nach O-03 nicht zum erneuten Auslösen berechtigt ist (bis zur Entscheidung: alle außer dem Empfänger-Admin des Endpunkts) | sie die Auslösung versucht | antwortet der Dienst mit HTTP 403 und es startet kein Zustelllauf |
| AC-013-4 | eine tote Zustellung zu einem pausierten Endpunkt | sie erneut ausgelöst wird | wird kein Versuch gestartet und die Zustellung endet erneut ohne Zustellung — die Pause wirkt auch auf Replays |
| AC-013-5 | ein erfolgreicher Replay | das Protokoll gelesen wird | trägt der neue Lauf eine neue Zustelllauf-ID und dieselbe Ereignis-ID |

Szenarien:

- **Normalfall** — Normalfall — Replay startet neuen Zustelllauf
  - Given eine tote Zustellung zu einem aktiven Endpunkt
  - When der Empfänger-Admin löst sie erneut aus
  - Then ein neuer Zustelllauf startet mit Versuchsanzahl 1
  - And der ursprüngliche Datensatz bleibt im Protokoll erhalten
- **Verweigerungsfall** — Verweigerungsfall — unberechtigte Rolle
  - Given eine tote Zustellung
  - When eine nicht berechtigte Rolle sie erneut auslöst
  - Then der Dienst antwortet mit HTTP 403 und es startet kein Zustelllauf
- **Grenzfall** — Grenzfall — Replay bei pausiertem Endpunkt
  - Given eine tote Zustellung zu einem pausierten Endpunkt
  - When sie erneut ausgelöst wird
  - Then wird kein Versuch gestartet

### US-014 — Endgültige Ablehnung des Empfängers

**Rolle:** Empfänger-Admin  
**Story:** Als Empfänger-Admin möchte ich dem Dienst signalisieren können, dass er ein Ereignis dauerhaft nicht möchte, damit er sofort aufhört statt weiter zu versuchen.  
**Anforderungen:** FR-025  
**Priorität:** must-have · **Quelle:** Abschnitt 8 (FR-025) · **Feature:** `docs/requirements/features/US-014.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-014-1 | ein Empfänger, der ein Ereignis dauerhaft ablehnt (dokumentiertes Signal im Vertrag) | die Zustellung bearbeitet wird | wird die Zustellung sofort aufgegeben, ohne weitere Versuche; das Protokoll zeigt den Status „abgelehnt“ mit dem Antwortcode |
| AC-014-2 | das Ablehnungssignal | der öffentliche Vertrag geprüft wird | ist das Signal dort beschrieben und ein Vertragstest schlägt fehl, wenn es fehlt |
| AC-014-3 | ein Empfänger, der mit 5xx oder einem Netzwerkfehler antwortet | die Zustellung bearbeitet wird | wird sie nicht als dauerhafte Ablehnung behandelt, sondern nach dem Backoff erneut versucht |
| AC-014-4 | ein Empfänger, der mit einem 4xx ohne das dokumentierte Ablehnungssignal antwortet | die Zustellung bearbeitet wird | wird die Zustellung ohne weitere Versuche aufgegeben und als „abgelehnt“ protokolliert |

Szenarien:

- **Normalfall** — Normalfall — dauerhafte Ablehnung beendet die Kette
  - Given ein Empfänger, der ein Ereignis dauerhaft nicht möchte
  - When er dies im Antwortsignal angibt
  - Then wird die Zustellung sofort aufgegeben
  - And das Protokoll zeigt den Status „abgelehnt“
- **Verweigerungsfall** — Verweigerungsfall — Serverfehler ist keine Ablehnung
  - Given ein Empfänger, der mit 5xx antwortet
  - When die Zustellung bearbeitet wird
  - Then wird die Zustellung erneut versucht statt aufgegeben
- **Grenzfall** — Grenzfall — anderer 4xx
  - Given ein Empfänger, der mit 4xx ohne das dokumentierte Ablehnungssignal antwortet
  - When die Zustellung bearbeitet wird
  - Then wird die Zustellung ohne weitere Versuche aufgegeben

## Epic E4 — Protokoll und Betrieb

### US-015 — Zustellprotokoll einsehen

**Rolle:** Entwickler / Empfänger-Admin  
**Story:** Als Entwickler möchte ich je Zustellung Zustand, Versuche, Zeitpunkte und den Antwortcode des Empfängers einsehen, damit ich die Frage „ist das angekommen?“ selbst beantworten kann.  
**Anforderungen:** FR-030  
**Priorität:** must-have · **Quelle:** Abschnitt 9 (FR-030) · **Feature:** `docs/requirements/features/US-015.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-015-1 | ein Zustellvorgang | das Zustellprotokoll gelesen wird | zeigt der Eintrag Zustand, Anzahl der Versuche, Zeitpunkt des letzten Versuchs, Antwortcode des Empfängers und den nächsten Versuchszeitpunkt |
| AC-015-2 | eine tote oder abgebrochene Zustellung | das Protokoll gelesen wird | ist der nächste Versuchszeitpunkt leer — es wird kein Zeitpunkt in der Vergangenheit oder in der Zukunft angezeigt |
| AC-015-3 | ein Entwickler von M1 | er die Ereignis-ID eines Ereignisses von M2 abruft | antwortet der Dienst mit HTTP 404 und es werden keine fremden Daten ausgegeben |
| AC-015-4 | ein Zeitraum und ein Statusfilter | das Protokoll gefiltert gelesen wird | liefert die Liste nur die Zustellvorgänge des eigenen Mandanten im gewählten Zeitraum; die Filter sind kombinierbar |

Szenarien:

- **Normalfall** — Normalfall — Protokolleintrag mit allen Feldern
  - Given ein Zustellvorgang mit zwei Versuchen
  - When das Zustellprotokoll gelesen wird
  - Then zeigt der Eintrag Zustand, Versuchsanzahl, letzten Versuchszeitpunkt, Antwortcode und nächsten Versuchszeitpunkt
- **Verweigerungsfall** — Verweigerungsfall — fremdes Ereignis
  - Given ein Entwickler von M1
  - When er ein Protokoll eines Ereignisses von M2 abruft
  - Then der Dienst antwortet mit HTTP 404 ohne fremde Daten
- **Grenzfall** — Grenzfall — tote Zustellung ohne nächsten Termin
  - Given eine tote Zustellung
  - When das Protokoll gelesen wird
  - Then ist der nächste Versuchszeitpunkt leer

### US-016 — Tote Zustellungen abholen und Aufbewahrung durchsetzen

**Rolle:** Service-Admin  
**Story:** Als Service-Admin möchte ich die toten Zustellungen aller Mandanten abholen und die Aufbewahrung der Daten durchsetzen, damit ich den Betrieb überblicke und die Daten nicht unbegrenzt wachsen.  
**Anforderungen:** FR-031 · offene Punkte: O-02  
**Priorität:** must-have · **Quelle:** Abschnitt 9 (FR-031), Abschnitt 3 (Rollen), Abschnitt 13 (Aufbewahrung) · **Feature:** `docs/requirements/features/US-016.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-016-1 | ein Service-Admin | er die Liste der toten Zustellungen abruft | erhält er die toten Zustellungen aller Mandanten mit Ereignis-ID, Endpunkt, Ereignistyp, Anzahl der Versuche, letztem Antwortcode und dem Zeitpunkt der Aufgabe |
| AC-016-2 | ein Empfänger-Admin | er die mandantenübergreifende Liste der toten Zustellungen abruft | antwortet der Dienst mit HTTP 403 |
| AC-016-3 | eine leere Trefferliste | die Liste abgerufen wird | wird eine leere Liste (HTTP 200) geliefert und kein Fehler |
| AC-016-4 | die Liste der toten Zustellungen | sie mit Endpunkt, Status und Zeitraum gefiltert wird | sind die Filter kombinierbar und die Liste ist seitenweise abrufbar |
| AC-016-5 | eine konfigurierte Aufbewahrungsfrist (im Test 30 Tage) | der Dienst die Aufbewahrung durchsetzt | werden Ereignisse, Zustellvorgänge und Protokolleinträge, die älter als die Frist sind, gelöscht, und jüngere bleiben erhalten; die Frist ist konfigurierbar |

Szenarien:

- **Normalfall** — Normalfall — Service-Admin holt die Totenliste
  - Given eine tote Zustellung in zwei Mandanten
  - When ein Service-Admin die Liste abruft
  - Then erhält er die toten Zustellungen beider Mandanten mit allen geforderten Feldern
- **Verweigerungsfall** — Verweigerungsfall — Empfänger-Admin
  - Given ein Empfänger-Admin
  - When er die mandantenübergreifende Totenliste abruft
  - Then der Dienst antwortet mit HTTP 403
- **Grenzfall** — Grenzfall — leere Trefferliste
  - Given ein Mandant ohne tote Zustellungen
  - When die Liste abgerufen wird
  - Then liefert der Dienst eine leere Liste ohne Fehler

### US-017 — Erfolgsquote und Zustelllatenz je Endpunkt

**Rolle:** Service-Admin  
**Story:** Als Service-Admin möchte ich je Endpunkt Erfolgsquote und Zustelllatenz ausgewiesen bekommen, damit ich im Betrieb erkennen kann, wo die Zustellung schwächelt.  
**Anforderungen:** FR-032  
**Priorität:** must-have · **Quelle:** Abschnitt 9 (FR-032) · **Feature:** `docs/requirements/features/US-017.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-017-1 | ein Endpunkt mit Zustellvorgängen im Auswertungsfenster | die Kennzahlen abgerufen werden | wird die Erfolgsquote (erfolgreiche Versuche / alle Versuche) und die Zustelllatenz (p95 der Dauer von der Annahme bis zur erfolgreichen Antwort) je Endpunkt ausgewiesen |
| AC-017-2 | die Betriebsüberwachung | die Kennzahlen abgerufen werden | sind sie über eine dokumentierte Schnittstelle (Metrikendefinition) abrufbar |
| AC-017-3 | ein Endpunkt ohne Zustellvorgänge im Auswertungsfenster | die Kennzahlen abgerufen werden | wird „keine Daten“ ausgewiesen — nicht 0 % und nicht 100 % |
| AC-017-4 | ein Empfänger-Admin | er die Kennzahlen eines Endpunkts eines anderen Mandanten abruft | antwortet der Dienst mit HTTP 404 |

Szenarien:

- **Normalfall** — Normalfall — Kennzahlen je Endpunkt
  - Given ein Endpunkt mit gemischten Versuchsergebnissen
  - When die Kennzahlen für das Auswertungsfenster abgerufen werden
  - Then werden Erfolgsquote und Zustelllatenz (p95) ausgewiesen
- **Verweigerungsfall** — Verweigerungsfall — fremder Endpunkt
  - Given ein Empfänger-Admin von M1
  - When er die Kennzahlen eines Endpunkts von M2 abruft
  - Then der Dienst antwortet mit HTTP 404
- **Grenzfall** — Grenzfall — Endpunkt ohne Zustellvorgänge
  - Given ein Endpunkt ohne Zustellvorgänge im Fenster
  - When die Kennzahlen abgerufen werden
  - Then wird „keine Daten“ ausgewiesen

### US-018 — Rate-Limit je Endpunkt

**Rolle:** Service-Admin  
**Story:** Als Service-Admin möchte ich, dass ein Endpunkt gegen zu viele Zustellungen in kurzer Zeit geschützt ist, damit ein schwacher Empfänger nicht die Zustellung für alle anderen blockiert.  
**Anforderungen:** FR-033 · offene Punkte: O-01  
**Priorität:** must-have · **Quelle:** Abschnitt 9 (FR-033), Abschnitt 13 (Rate-Limit) · **Feature:** `docs/requirements/features/US-018.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-018-1 | ein Endpunkt | in einem Zeitfenster mehr Zustellversuche anfallen als der konfigurierte Grenzwert erlaubt | greift die konfigurierte Strategie (verzögern oder blockieren) und kein Ereignis geht verloren |
| AC-018-2 | eine durch das Limit betroffene Zustellung | das Zustellprotokoll gelesen wird | ist die Verzögerung beziehungsweise Blockierung mit Status und nächstem Versuchszeitpunkt erkennbar |
| AC-018-3 | zwei Endpunkte, von denen nur einer das Limit überschreitet | die Zustellvorgänge beider Endpunkte laufen | wird die Zustellung des anderen Endpunkts nicht verzögert — die Begrenzung wirkt je Endpunkt |
| AC-018-4 | die offene Festlegung O-01 | der Dienst konfiguriert wird | ist kein Grenzwert vorbelegt; der Dienst erhebt lediglich die Zählung der Zustellversuche je Endpunkt und Zeitfenster als Datenbasis für die Festlegung |

Szenarien:

- **Normalfall** — Normalfall — Limit greift je Endpunkt
  - Given ein Endpunkt mit konfiguriertem Grenzwert
  - When die Versuche im Zeitfenster den Grenzwert überschreiten
  - Then greift die konfigurierte Strategie und es geht kein Ereignis verloren
  - And die betroffene Zustellung ist im Protokoll erkennbar
- **Verweigerungsfall** — Verweigerungsfall — kein Ereignisverlust
  - Given ein Endpunkt mit aktivem Limit
  - When mehrere Zustellungen laufen in das Limit hinein
  - Then wird kein Ereignis verworfen und kein Zustellvorgang endet unprotokolliert
- **Grenzfall** — Grenzfall — Grenze wirkt nicht mandantenübergreifend
  - Given zwei Endpunkte, von denen nur einer das Limit überschreitet
  - When beide Endpunkte werden zugestellt
  - Then bleibt der andere Endpunkt unverzögert

## Epic E5 — Oberfläche

### US-019 — Endpunktverwaltung in der Oberfläche

**Rolle:** Empfänger-Admin  
**Story:** Als Empfänger-Admin möchte ich Endpunkte in einer Oberfläche registrieren, pausieren und rotieren, damit ich die Verwaltung ohne API-Aufrufe erledigen kann.  
**Anforderungen:** FR-001, FR-002, FR-003 · verwandt: FR-004  
**Priorität:** must-have · **Quelle:** Abschnitt 12 (Nr. 1 und 5) · **Feature:** `docs/requirements/features/US-019.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-019-1 | ein angemeldeter Empfänger-Admin | er die Endpunktseite öffnet | sieht er seine Endpunkte mit Status, Ziel-URL (ohne Secret) und den Ereignistypen |
| AC-019-2 | eine ungültige Ziel-URL | er das Registrierungsformular abschickt | erscheint eine verständliche Fehlermeldung am Feld und es wird kein Endpunkt angelegt |
| AC-019-3 | ein aktiver Endpunkt | er ihn pausiert | wechselt der Status in der Liste sofort auf „pausiert“ und die Aktion „reaktivieren“ wird angeboten |
| AC-019-4 | ein Endpunkt | er das Secret rotiert | wird die Änderung übernommen, das neue Secret wird nicht im Klartext angezeigt und die Rotation wird bestätigt |

Szenarien:

- **Normalfall** — Normalfall — Endpunktliste
  - Given ein Empfänger-Admin mit zwei Endpunkten
  - When er die Endpunktseite öffnet
  - Then sieht er beide Endpunkte mit Status, URL und Ereignistypen
- **Verweigerungsfall** — Verweigerungsfall — ungültige URL
  - Given ein Empfänger-Admin
  - When er das Registrierungsformular mit einer http-URL abschickt
  - Then erscheint eine Fehlermeldung am Feld und es wird kein Endpunkt angelegt
- **Grenzfall** — Grenzfall — Statuswechsel beim Pausieren
  - Given ein aktiver Endpunkt
  - When er ihn pausiert
  - Then zeigt die Liste sofort den Status „pausiert“ und bietet „reaktivieren“ an

### US-020 — Oberfläche für Zustellprotokoll und tote Zustellungen

**Rolle:** Entwickler / Service-Admin  
**Story:** Als Entwickler und als Service-Admin möchte ich Zustellprotokoll und tote Zustellungen in der Oberfläche sehen, damit ich Zustellfragen ohne Datenbankzugriff klären kann.  
**Anforderungen:** FR-030, FR-031  
**Priorität:** must-have · **Quelle:** Abschnitt 12 (Nr. 5) · **Feature:** `docs/requirements/features/US-020.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-020-1 | ein Entwickler | er die Protokollseite öffnet | sieht er je Zustellvorgang Zustand, Versuche, letzten Versuch, Antwortcode und nächsten Versuchszeitpunkt und kann nach Endpunkt, Status und Zeitraum filtern |
| AC-020-2 | ein Service-Admin | er die Seite der toten Zustellungen öffnet | sieht er die toten Zustellungen aller Mandanten mit den geforderten Feldern |
| AC-020-3 | keine Treffer | die Liste geladen wird | wird ein verständlicher Leerzustand angezeigt und keine leere Tabelle ohne Erklärung |
| AC-020-4 | die URL eines fremden Mandanten | sie direkt aufgerufen wird | werden keine fremden Daten angezeigt (HTTP 404 beziehungsweise Leerzustand) |

Szenarien:

- **Normalfall** — Normalfall — Protokollansicht
  - Given ein Entwickler mit Zustellvorgängen
  - When er die Protokollseite öffnet
  - Then sieht er die Zustellvorgänge mit allen geforderten Feldern und Filtern
- **Verweigerungsfall** — Verweigerungsfall — fremder Mandant per Direktaufruf
  - Given ein Entwickler von M1
  - When er die Detailseite eines Ereignisses von M2 direkt aufruft
  - Then werden keine fremden Daten angezeigt
- **Grenzfall** — Grenzfall — Leerzustand
  - Given ein Entwickler ohne Zustellvorgänge
  - When er die Protokollseite öffnet
  - Then wird ein verständlicher Leerzustand angezeigt

### US-021 — Barrierefreiheit der Oberfläche

**Rolle:** Entwickler / Empfänger-Admin / Service-Admin  
**Story:** Als Nutzer mit Hilfsmitteln möchte ich die Oberfläche ohne Maus und mit Screenreader bedienen können, damit ich Endpunkte, Protokoll und Totenliste gleichwertig nutzen kann.  
**Anforderungen:** NFR-005  
**Priorität:** must-have · **Quelle:** Abschnitt 10 (NFR-005), Abschnitt 12 (Nr. 5) · **Feature:** `docs/requirements/features/US-021.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-021-1 | die Seiten Endpunkte, Protokoll und tote Zustellungen | sie ausschließlich mit der Tastatur bedient werden | sind alle Funktionen erreichbar und auslösbar, ist die Fokussierung sichtbar und ist die Reihenfolge logisch |
| AC-021-2 | ein automatischer WCAG-2.1-AA-Scan (axe) über alle Seiten | der Scan läuft | werden keine Verstöße der Schweregrad-Klassen „kritisch“ oder „schwer“ gemeldet |
| AC-021-3 | Text und Bedienelemente der Oberfläche | die Kontraste geprüft werden | erreichen Texte mindestens 4,5:1 und Bedienelemente mindestens 3:1 |
| AC-021-4 | ein Screenreader-Test der drei Seiten | der Test durchläuft | werden Liste, Zeileninhalte und Statusmeldungen mit Rolle und Inhalt ausgegeben |

Szenarien:

- **Normalfall** — Normalfall — Bedienung mit der Tastatur
  - Given die Seiten Endpunkte, Protokoll und tote Zustellungen
  - When sie ausschließlich mit der Tastatur bedient werden
  - Then sind alle Funktionen erreichbar und auslösbar
  - And die Fokussierung ist durchgehend sichtbar
- **Verweigerungsfall** — Verweigerungsfall — automatischer WCAG-Scan
  - Given alle Seiten der Oberfläche
  - When ein axe-Scan in der Qualitätssicherung läuft
  - Then meldet der Scan keinen Verstoß der Klasse „kritisch“ oder „schwer“
- **Grenzfall** — Grenzfall — Kontrast der Bedienelemente
  - Given Bedienelemente der Oberfläche
  - When die Kontrastverhältnisse gemessen werden
  - Then erreichen die Bedienelemente mindestens 3:1 und die Texte mindestens 4,5:1

## Epic E6 — Mandantentrennung und Betrieb

### US-022 — Mandanttrennung auf Datenbankebene

**Rolle:** Service-Admin  
**Story:** Als Service-Admin möchte ich, dass die Mandantentrennung auf Datenbankebene erzwungen wird, damit ein Fehler in der Anwendung niemals ein fremdes Ereignis zeigt.  
**Anforderungen:** NFR-003 · verwandt: FR-004  
**Priorität:** must-have · **Quelle:** Abschnitt 10 (NFR-003), Abschnitt 3 · **Feature:** `docs/requirements/features/US-022.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-022-1 | eine PostgreSQL-Instanz mit dem produktiven Schema | alle mandantenführenden Tabellen geprüft werden | erzwingt jede von ihnen die Mandantentrennung auf Datenbankebene (zum Beispiel Row-Level-Security mit zusätzlicher Policy) |
| AC-022-2 | zwei Mandanten mit je einem Ereignis | eine Abfrage ohne Mandantenfilter läuft | liefert sie nur die Zeilen des in der Verbindung gesetzten Mandanten — ein Anwendungsfehler, der den Filter vergisst, zeigt kein fremdes Ereignis |
| AC-022-3 | die Verbindung eines Requests | sie geöffnet wird | trägt sie den Mandanten aus der Identität (zum Beispiel als Sitzungsvariable), und ein Mandant aus dem Request-Body wird nicht übernommen |
| AC-022-4 | ein Benutzer ohne Zugriff auf Mandant M2 | er eine Ressource von M2 per ID abruft | antwortet der Dienst mit HTTP 404 |

Szenarien:

- **Normalfall** — Normalfall — Trennung wird erzwungen
  - Given zwei Mandanten mit je einem Ereignis in einer echten PostgreSQL-Instanz
  - When eine Abfrage ohne Mandantenfilter läuft
  - Then liefert die Abfrage nur die Zeilen des gesetzten Mandanten
- **Verweigerungsfall** — Verweigerungsfall — Zugriff auf fremde Ressource
  - Given ein Benutzer des Mandanten M1
  - When er eine Ressource von M2 per ID abruft
  - Then antwortet der Dienst mit HTTP 404
- **Grenzfall** — Grenzfall — Anwendungsfehler ohne Filter
  - Given zwei Mandanten mit Daten
  - When der Anwendungscode setzt versehentlich keinen Mandantenfilter
  - Then bleibt die Datenmenge auf den Mandanten der Verbindung begrenzt

### US-023 — Containerbetrieb, Mandanteninstanzen und horizontale Skalierung

**Rolle:** Service-Admin  
**Story:** Als Service-Admin möchte ich den Dienst als Container betreiben, pro Mandanten konfigurierbar und horizontal skalierbar, damit Ausfälle und Lastspitzen beherrschbar sind.  
**Anforderungen:** NFR-006  
**Priorität:** must-have · **Quelle:** Abschnitt 10 (NFR-006), Abschnitt 11 (Rahmenbedingungen) · **Feature:** `docs/requirements/features/US-023.feature`

| AC | Given | When | Then |
|---|---|---|---|
| AC-023-1 | das Container-Image | es gebaut, gestartet und geprüft wird | startet der Dienst daraus mit einem Gesundheitsendpunkt, enthält es keine Secrets und keine fest verdrahteten Zugangsdaten und läuft er als nicht privilegierter Benutzer |
| AC-023-2 | eine Konfiguration | sie einem Mandanten zugeordnet wird | bedient die Instanz nur diesen Mandanten — das Mandanten-Sharding ist vorbereitet |
| AC-023-3 | zwei Instanzen | beide parallel laufen | wird ein Zustellversuch für ein Ereignis von genau einer Instanz ausgeführt (keine Doppelzustellung durch konkurrierende Worker) |
| AC-023-4 | eine Instanz, die während eines Zustellversuchs abstürzt | sie neu startet | geht das Ereignis nicht verloren — der Zustellvorgang wird fortgesetzt und die Versuchsanzahl steigt nicht sprunghaft |
| AC-023-5 | der Betrieb | die Pipeline das Image baut und bereitstellt | werden Staging und Produktion getrennt bereitgestellt und erhalten getrennte Konfigurationen und Secrets |

Szenarien:

- **Normalfall** — Normalfall — Betrieb im Container
  - Given das gebaute Image
  - When der Dienst daraus gestartet wird
  - Then startet er, meldet der Gesundheitsendpunkt „gesund“, und das Image enthält keine Secrets
- **Verweigerungsfall** — Verweigerungsfall — keine fest verdrahteten Zugangsdaten
  - Given das Image und die Konfigurationsdateien
  - When sie nach Zugangsdaten und fest verdrahteten Passwörtern durchsucht werden
  - Then wird kein Treffer gefunden
- **Grenzfall** — Grenzfall — Absturz während der Zustellung
  - Given eine Instanz mit einem laufenden Zustellversuch
  - When die Instanz abstürzt und neu startet
  - Then wird der Zustellvorgang fortgesetzt, ohne das Ereignis zu verlieren
