# PromptGuard — Anforderungskatalog (kompakt)

| Feld | Wert |
|---|---|
| Quelle | `docs/requirements/PRD.md` (Abschnitte 8-12) |
| Stand | 2026-10-20T00:00:00Z |
| Anforderungen | 97 |
| User Stories | 35 |

| ID | Anforderung | Prio | Story | Status |
|---|---|---|---|---|
| `FR-001` | Der Dienst stellt ausschließlich JWT-basierte Anmeldung bereit (E-Mail + Passwort). Der Access-Token enthält `sub`, `organisation_id`, `role`, `exp`; das Signieren erfolgt mit einem ausschließlich aus der Umgebung gelesenen Schlüssel. | MUSS | US-001 | Story vorhanden, Tests offen |
| `FR-002` | Registrierung legt eine neue Organisation an und setzt den ersten Nutzer als `ORG_ADMIN`. Eine Registrierung kann keiner bestehenden Organisation beitreten. | MUSS | US-002 | Story vorhanden, Tests offen |
| `FR-003` | Ein Gast ohne gültiges Token erhält `401`; ein gültiges Token ohne erforderliche Rolle erhält `403`. Beide Antworten enthalten keine Datenexistenzinformation. | MUSS | US-001, US-004 | Story vorhanden, Tests offen |
| `FR-004` | Rollenmodell: `DEVELOPER` (eigene Organisation, Schreiben/Lesen), `SECURITY_ADMIN` (zusätzlich Review, Waiver, Alarm-Empfang), `ORG_ADMIN` (zusätzlich Mitglieder, Kategorien, Org-Einstellungen). Rollen sind organisationsgebunden. | MUSS | US-004 | Story vorhanden, Tests offen |
| `FR-005` | `ORG_ADMIN` lädt Mitglieder per E-Mail-Einladung ein und weist Rollen zu. Eine Einladung ist einmalig, zeitlich begrenzt (Default 7 Tage) und nur für die eigene Organisation gültig. | MUSS | US-006 | Story vorhanden, Tests offen |
| `FR-006` | `ORG_ADMIN` hinterlegt je Organisation eine Liste von Security-Admin-Empfängeradressen (1–n). Es sind nur Adressen innerhalb der Organisation hinterlegbar. | MUSS | US-006 | Story vorhanden, Tests offen |
| `FR-007` | Access-Token sind kurzlebig (Default 15 min); Refresh-Token werden rotiert (Default 30 Tage) und bei Nutzung invalidiert; Logout invalidiert die Session-Serienkette. | MUSS | US-003 | Story vorhanden, Tests offen |
| `FR-008` | Passwort-Hashing mit bcrypt/argon2id (Cost ≥ 12). Das Klartextpasswort verlässt den Registrierungs-/Login-Fluss nie. | MUSS | US-001 | Story vorhanden, Tests offen |
| `FR-009` | Die Anwendung stellt die Mandantentrennung auf jeder Route sicher (Authentifizierung, Autorisierung, Mandantenfilter, Hintergrund-Jobs, Suche, Export, Cache, Logs, Metriken). | MUSS | US-005 | Story vorhanden, Tests offen |
| `FR-010` | Ein Prompt besteht aus Titel (Pflicht, 1–200 Zeichen), Beschreibung (optional, ≤ 2 000 Zeichen), Inhalt (Pflicht, ≤ 50 000 Zeichen), genau einer Kategorie und 0–n Tags. | MUSS | US-007 | Story vorhanden, Tests offen |
| `FR-011` | Beim Anlegen wird automatisch Version 1 erzeugt mit Status `PENDING`; die API-Antwort bestätigt das Anlegen unabhängig vom Prüfergebnis (asynchron, siehe FR-030). | MUSS | US-007 | Story vorhanden, Tests offen |
| `FR-012` | Jede Änderung von Inhalt, Titel, Beschreibung, Kategorie oder Tags erzeugt eine neue Version mit Status `PENDING`. Die vorherige Version bleibt unverändert und behält ihren Status. | MUSS | US-008 | Story vorhanden, Tests offen |
| `FR-013` | Versionshistorie ist einsehbar (Version, Ersteller, Zeitpunkt, Status, Fundstellen-Zahl); die Versionsinhalte sind unveränderlich. Der Vergleich zweier Versionen wird angeboten. | MUSS | US-009 | Story vorhanden, Tests offen |
| `FR-014` | Kategorien sind organisationsspezifisch. Beim Anlegen der Organisation wird eine Seed-Liste angelegt (`Customer Support`, `Code Gen`, `Data Analysis`, `Documentation`, `Other`). `ORG_ADMIN` kann Kategorien anlegen, umbenennen, deaktivieren; Kategorien mit existierenden Prompts können nicht gelöscht, nur deaktiviert werden. | MUSS | US-010 | Story vorhanden, Tests offen |
| `FR-015` | Tags sind freitext, organisationsspezifisch, Groß-/Kleinschreibung wird normalisiert, Duplikate innerhalb einer Organisation sind unmöglich. Max. 10 Tags je Prompt, max. 200 Tags je Organisation. | MUSS | US-011 | Story vorhanden, Tests offen |
| `FR-016` | Ein `ORG_ADMIN` oder der Ersteller löscht einen Prompt. Dabei werden alle Versionen, Scans und Fundstellen gelöscht; der Audit-Eintrag bleibt ohne Prompt-Inhalt bestehen. | MUSS | US-012 | Story vorhanden, Tests offen |
| `FR-017` | Ein Prompt kann kopiert werden; die Kopie startet als neue, eigenständige Organisationseinheit mit Status `PENDING`. | SOLL | — | Post-MVP ohne Story |
| `FR-020` | Volltextsuche über Titel, Beschreibung und Inhalt innerhalb der eigenen Organisation. Mindestens-Präfix- und Substring-Treffer; die Suche berücksichtigt keine gelöschten Objekte. | MUSS | US-013 | Story vorhanden, Tests offen |
| `FR-021` | Filter: Kategorie (Mehrfachauswahl), Tag (Mehrfachauswahl), Scan-Status (Mehrfachauswahl). Filter sind kombinierbar. | MUSS | US-014 | Story vorhanden, Tests offen |
| `FR-022` | Sortierung nach Aktualisierungszeit, Erstellungszeit, Titel und Prüfstatus (jeweils auf-/absteigend). Pagination mit Seite und Seitengröße (Default 50, max 200). | MUSS | US-015 | Story vorhanden, Tests offen |
| `FR-023` | Suchergebnisse enthalten niemals Daten einer anderen Organisation — auch nicht als Anzahl, Vorschau, Fundstelle oder Existenzhinweis. | MUSS | US-005 | Story vorhanden, Tests offen |
| `FR-030` | Das Anlegen/Bearbeiten eines Prompts liefert sofort eine Bestätigung; die Prüfung startet asynchron über eine Warteschlange. Die UI wird durch die Prüfung nicht blockiert. | MUSS | US-007, US-016 | Story vorhanden, Tests offen |
| `FR-031` | Jede Version wird genau einem Prüflauf unterzogen. Der Prüflauf umfasst: (a) lokale deterministische Vorprüfung (Secrets/PII), (b) Duplikatvergleich, (c) LLM-Klassifikation, (d) Aggregation zum Gesamturteil. Die Stufen (a) und (b) laufen vor (c); ihre Fundstellen fließen in das Gesamturteil ein. In Stufe (c) wird der Originalinhalt unverändert übermittelt (FR-038). | MUSS | US-016 | Story vorhanden, Tests offen |
| `FR-032` | Duplikatwarnung: Der Vergleich erfolgt ausschließlich innerhalb derselben Organisation. Ähnlichkeit wird über ein konfigurierbares Maß bestimmt (Default: Vektor-Ähnlichkeit ≥ 0,90; zusätzlich normalisierter Textvergleich). Das Ergebnis listet bis zu 5 ähnliche Prompts mit Titel, Score und Verweis. Die Warnung ist beratend und blockiert das Anlegen nie. | MUSS | US-017 | Story vorhanden, Tests offen |
| `FR-033` | Prompt-Injection & Jailbreak: Erkennung von Mustern, die die Anweisungshierarchie umkehren (`ignore previous instructions`, Rollenwechsel, Delimiter-Ausbruch) oder Sicherheitsleitplanken aushebeln (Jailbreak-Formulierungen, `DAN`-Stil, Aufforderung zur Selbstverifikation). | MUSS | US-018 | Story vorhanden, Tests offen |
| `FR-034` | Secrets: Erkennung von API-Keys, Tokens, Passwörtern, privaten Schlüsseln, Connection-Strings, JWTs und hochentropischen Geheimnissen. | MUSS | US-019 | Story vorhanden, Tests offen |
| `FR-035` | PII: Erkennung von E-Mail-Adressen, Telefonnummern, IBAN/Kreditkartendaten, personen- und Kundenbezogenen Namen, sowie semantisch erkannten personenbezogenen Inhalten. | MUSS | US-020 | Story vorhanden, Tests offen |
| `FR-036` | Das Gesamturteil wird aus den Fundstellen abgeleitet: mindestens ein Fund ≥ Schwellenwert → `FLAGGED`; sonst `APPROVED`. Die Schwelle ist organisationsweit konfigurierbar (Default `MEDIUM`). | MUSS | US-021 | Story vorhanden, Tests offen |
| `FR-037` | Das Scan-Ergebnis zeigt je Fundstelle: Kategorie, Schweregrad, redigierter Ausschnitt (Secret-/PII-Spans maskiert), Fundstelle im Text (Position/Zeile), Begründung und die auslösende Regel bzw. das Modellurteil. | MUSS | US-021 | Story vorhanden, Tests offen |
| `FR-038` | Der zu prüfende Originalinhalt wird unverändert an den KI-Dienst übermittelt — es findet keine Redaktion statt (Entscheidung F1). Übermittelt wird ausschließlich an den gemäß FR-065 verifizierten Dienst; schlägt die Verifikation fehl, bricht der Scan ab, ohne dass Inhalt das System verlässt. | MUSS | US-019, US-022 | Story vorhanden, Tests offen |
| `FR-039` | `SECURITY_ADMIN` kann eine geflaggte Version als Ausnahme freigeben (Status `WAIVED`), verpflichtend mit Begründung; der Vorgang erzeugt einen Audit-Eintrag mit Actor, Zeitpunkt und Begründung. | MUSS | US-023 | Story vorhanden, Tests offen |
| `FR-040` | Der Prüfstatus wird in der UI ohne Neuladen aktualisiert, wenn er wechselt. Der Transportmechanismus (SSE oder Polling) ist eine Architekturentscheidung in Phase 2; die beobachtbare Anforderung lautet: Statuswechsel erscheint ≤ 2 s nach Abschluss des Prüflaufs. | MUSS | US-024 | Story vorhanden, Tests offen |
| `FR-041` | Der Statuswechsel erzeugt einen Audit-/Historie-Eintrag (Zeitpunkt, alter Status, neuer Status, auslösender Vorgang). | MUSS | US-025 | Story vorhanden, Tests offen |
| `FR-042` | Ein hängender Prüflauf (Status `SCANNING`) wird nach einer Frist (> 5 min ohne Abschluss) als `FAILED` markiert und ist erneut auslösbar. | MUSS | US-024 | Story vorhanden, Tests offen |
| `FR-050` | Wird eine Version `FLAGGED`, sendet das System automatisch eine Warn-E-Mail an alle konfigurierten Security-Admin-Empfänger der Organisation. | MUSS | US-026 | Story vorhanden, Tests offen |
| `FR-051` | Die E-Mail enthält keinen Prompt-Inhalt und keine Fundstellen-Inhalte — nur Organisation, Prompt-ID/Titel, Version, Fundkategorien und Schweregrade, Zeitpunkt und einen Deep-Link in die Oberfläche. | MUSS | US-026 | Story vorhanden, Tests offen |
| `FR-052` | Zustellfehler (SMTP nicht erreichbar) führen zu Wiederholung mit Backoff; nach Erschöpfung der Versuche wandert der Zustellversuch in eine Totmannschaltung, die in der Oberfläche sichtbar ist. Der Prüfstatus ist vom Zustellerfolg unabhängig. | MUSS | US-027 | Story vorhanden, Tests offen |
| `FR-053` | Ist für eine Organisation kein Security-Admin-Empfänger hinterlegt, wird der Alarm an die `ORG_ADMIN`s der Organisation zugestellt; der Zustellversuch wird in beiden Fällen protokolliert. | MUSS | US-006 | Story vorhanden, Tests offen |
| `FR-060` | Ein Retention-Job löscht Request-/Access-Logs, Scan-Rohdaten, Notification-Protokolle und Audit-Einträge mit Inhaltsbezug, die älter als 90 Tage sind. Der Job ist idempotent und täglich geplant. | MUSS | US-025, US-028 | Story vorhanden, Tests offen |
| `FR-061` | Rohe KI-Anfrage-/Antwort-Payloads werden nicht dauerhaft gespeichert; sie werden spätestens nach 24 h gelöscht. Dauerhaft gespeichert werden nur das verdichtete Ergebnis (Kategorien, Schweregrade, redigierte Ausschnitte). | MUSS | US-028 | Story vorhanden, Tests offen |
| `FR-062` | In Applikations-Logs erscheinen niemals Prompt-Inhalte, Secrets, Tokens oder personenbezogene Daten. Log-Zeilen enthalten eine Korrelation-ID, aber keinen Inhalt. | MUSS | US-032 | Story vorhanden, Tests offen |
| `FR-063` | Recht auf Löschung: Ein `ORG_ADMIN` oder der Betroffene selbst kann die Löschung eines Nutzerkontos veranlassen. Konto, Tokens und Sitzungen werden gelöscht, die Autorschaft in bestehenden Versionen wird anonymisiert, der Prompt-Bestand bleibt bestehen und wird erneut geprüft. | MUSS | US-029 | Story vorhanden, Tests offen |
| `FR-064` | Der Datenschutzhinweis liegt in Deutsch und Englisch vor, verlinkt aus der Anwendung und nennt Zwecke, Rechtsgrundlage, Aufbewahrungsfristen und Rechte. | MUSS | US-030 | Story vorhanden, Tests offen |
| `FR-065` | Alle Daten bleiben in der EU (europe-west3). Der KI-Anbieter wird mit „keine Datenverwendung zum Training" und ohne dauerhafte Speicherung konfiguriert; dies ist in der Konfiguration nachweisbar. | MUSS | US-034 | Story vorhanden, Tests offen |
| `FR-070` | Der Betrieb unterstützt den Ausfall oder die extreme Verlangsamung des KI-Dienstes: Anfragen werden geordnet nachgeholt (Warteschlange, Retry mit Backoff, Totmannschaltung), das System stürzt nicht ab, und kein Datenverlust entsteht. | MUSS | US-027, US-033 | Story vorhanden, Tests offen |
| `FR-071` | Ein `FAILED`er Prüflauf ist manuell erneut auslösbar; die Wiederholung ist im Audit-Protokoll sichtbar. | MUSS | US-033 | Story vorhanden, Tests offen |
| `FR-072` | Es gibt einen Health-Endpunkt sowie strukturierte Logs und Metriken (Warteschlangentiefe, Scan-Dauer, Fehlerquote, Zustellquote, Scan-Statusverteilung). | MUSS | US-032 | Story vorhanden, Tests offen |
| `FR-073` | Die CI-Prüfung schlägt fehl, wenn die Testabdeckung (Line Coverage, Backend + Frontend) unter 80 % liegt. | MUSS | US-031 | Story vorhanden, Tests offen |
| `FR-074` | Jede Übermittlung von Prompt-Inhalten an den KI-Dienst wird als Metadaten-Ereignis protokolliert (Zeitpunkt, Provider, Modell, Zeichenlänge, Ergebnis-ID, Korrelation-ID) — ohne Inhalt. Grundlage für die Nachweisführung nach Art. 30 DSGVO und für die Zuordnung von Fundstellen zur Übermittlung. | MUSS | US-022, US-034 | Story vorhanden, Tests offen |
| `NFR-001` | Performance | — | US-016 | Story vorhanden, Tests offen |
| `NFR-002` | Performance | — | US-016 | Story vorhanden, Tests offen |
| `NFR-003` | Performance | — | US-024 | Story vorhanden, Tests offen |
| `NFR-004` | Performance | — | US-013 | Story vorhanden, Tests offen |
| `NFR-005` | Performance | — | US-009 | Story vorhanden, Tests offen |
| `NFR-006` | Verfügbarkeit | — | US-033 | Story vorhanden, Tests offen |
| `NFR-007` | Verfügbarkeit | MUSS | US-016, US-027, US-033 | Story vorhanden, Tests offen |
| `NFR-008` | Skalierbarkeit | MUSS | US-015 | Story vorhanden, Tests offen |
| `NFR-009` | Qualität | — | US-031 | Story vorhanden, Tests offen |
| `NFR-010` | Qualität | MUSS | US-031 | Story vorhanden, Tests offen |
| `NFR-011` | Sicherheit | — | US-034 | Story vorhanden, Tests offen |
| `NFR-012` | Sicherheit | — | US-034 | Story vorhanden, Tests offen |
| `NFR-013` | Datenschutz | — | US-028 | Story vorhanden, Tests offen |
| `NFR-014` | Beobachtbarkeit | MUSS | US-032 | Story vorhanden, Tests offen |
| `NFR-015` | Barrierefreiheit | MUSS | US-035 | Story vorhanden, Tests offen |
| `NFR-016` | Wartbarkeit | MUSS | US-035 | Story vorhanden, Tests offen |
| `NFR-017` | Portabilität | MUSS | US-034 | Story vorhanden, Tests offen |
| `NFR-018` | Internationalisierung | SOLL | US-035 | Story vorhanden, Tests offen |
| `SEC-001` | Authentifizierung ausschließlich per JWT (asymmetrisch oder HMAC mit umgebungsbasiertem Schlüssel); kein Session-Cookie, kein LocalStorage für Tokens (Refresh-Token nur in `HttpOnly`/`SameSite`-Cookie). | MUSS | US-001, US-003 | Story vorhanden, Tests offen |
| `SEC-002` | Mandantentrennung: jede Query trägt den `organisation_id`-Filter; zusätzlich PostgreSQL Row-Level-Security als zweite Verteidigungslinie. Ein Cross-Tenant-Zugriff liefert `403`/`404` ohne Dateninhalt. | MUSS | US-005 | Story vorhanden, Tests offen |
| `SEC-003` | Jeder Hintergrund-Job (Prüflauf, Benachrichtigung, Retention) trägt die `organisation_id` des betroffenen Objekts im Job-Kontext; ein Job ohne auflösbaren Tenant-Kontext wird verworfen und protokolliert. | MUSS | US-005 | Story vorhanden, Tests offen |
| `SEC-004` | Rollenprüfung serverseitig auf jeder Route und in jedem Anwendungsfall; die UI blendet nicht erlaubte Aktionen aus, ersetzt aber keine Prüfung. | MUSS | US-004 | Story vorhanden, Tests offen |
| `SEC-005` | Eingabevalidierung an der Grenze (Länge, Format, erlaubte Werte) für alle Schreiboperationen; parametrisierte Queries; kein String-Concatenation in SQL. | MUSS | US-034 | Story vorhanden, Tests offen |
| `SEC-006` | Prompt-Inhalt ist nicht vertrauenswürdig: Er wird nie als HTML/Markdown gerendert (XSS), nie interpretiert, nie in Logs geschrieben, nie in Systemprompts des Prüfers ungeschützt eingesetzt, und URLs darin werden nie abgerufen (SSRF). Eine Übermittlung an den KI-Dienst erfolgt ausschließlich an den verifizierten Dienst nach FR-038/FR-065 und wird nach FR-074 protokolliert. | MUSS | US-018, US-022 | Story vorhanden, Tests offen |
| `SEC-007` | Der Prüfer ist gegen Prompt-Injection seiner Prüflinge gehärtet: Der zu prüfende Inhalt wird als zu klassifizierender Datenblock übergeben, nicht als Anweisung; die Systemanweisung des Prüfers dominiert. | MUSS | US-018 | Story vorhanden, Tests offen |
| `SEC-008` | Rate-Limiting: Anmeldung (Default 10 Versuche/Min/IP, mit Sperre), API (Default 1 000/Min/Nutzer). | MUSS | US-001 | Story vorhanden, Tests offen |
| `SEC-009` | Sicherheits-Header (HSTS, `X-Content-Type-Options`, `X-Frame-Options: DENY`, restriktive CSP), TLS 1.2+ durchgehend, keine internen Fehlerdetails in Antworten. | MUSS | US-034 | Story vorhanden, Tests offen |
| `SEC-010` | Audit-Protokoll für sicherheitsrelevante Aktionen: Login/Logout, Rollen- und Mitgliedänderungen, Prompt-Löschung, Waiver/Freigabe, manuelle Prüfwiederholung. Ohne Inhalte, mit Actor und Zeitpunkt. | MUSS | US-023, US-025 | Story vorhanden, Tests offen |
| `SEC-011` | Abhängigkeits- und Container-Scanning in CI; keine ungepatchten Criticals. | MUSS | US-034 | Story vorhanden, Tests offen |
| `SEC-012` | Verschlüsselung ruhender Daten über den Datenbank-/Storage-Dienst; sensible Felder (z. B. Refresh-Token) werden gehasht gespeichert. | MUSS | US-034 | Story vorhanden, Tests offen |
| `DP-001` | Rechtsgrundlage und Zweck des Scans sind dokumentiert (berechtigtes Interesse / interne Compliance-Pflicht) und im Datenschutzhinweis benannt. | MUSS | US-030 | Story vorhanden, Tests offen |
| `DP-002` | Keine Redaktion vor der Übermittlung (Entscheidung F1). Datenminimierung wird stattdessen umgesetzt über: (a) Löschung der KI-Rohpayloads nach 24 h (FR-061), (b) die Provider-Auflagen aus FR-065 (kein Training, keine dauerhafte Speicherung, EU-Region, Auftragsverarbeitungsvertrag), (c) Metadaten-only-Audit jeder Übermittlung (FR-074) und (d) das Verbot dauerhafter Speicherung von Roh-Inhalten im Plattform-Log (FR-062). | MUSS | US-022 | Story vorhanden, Tests offen |
| `DP-003` | Löschfristen: Logs/Rohtelemetrie ≤ 90 Tage, KI-Rohpayloads ≤ 24 h, Prompts/Versionen bis zur expliziten Löschung (Annahme A-03). | MUSS | US-028 | Story vorhanden, Tests offen |
| `DP-004` | Recht auf Löschung ist technisch über einen auslösbaren Vorgang umgesetzt (Nutzerkonto, Prompt, Audit ohne Inhalt). | MUSS | US-012, US-029 | Story vorhanden, Tests offen |
| `DP-005` | Kein Datenabfluss aus der EU: Hosting in europe-west3. Der KI-Dienst wird ausschließlich mit einem Vertrag zur Auftragsverarbeitung (AVV) und einer vertraglichen Zusage betrieben, dass eingehende Inhalte nicht zum Training verwendet und nicht dauerhaft gespeichert werden. Die Plattform verifiziert diese Konfiguration vor dem ersten Aufruf und bricht den Scan ab, wenn die Zusage fehlt (FR-038, FR-065, US-022). | MUSS | US-022, US-034 | Story vorhanden, Tests offen |
| `DP-006` | Datenschutzhinweis DE/EN, verlinkt und versioniert. | MUSS | US-030 | Story vorhanden, Tests offen |
| `DP-007` | Rechte der Betroffenen (Auskunft, Berichtigung, Löschung, Widerspruch) sind organisatorisch geregelt und in der Anwendung nachweisbar unterstützt (Löschung, Berichtigung, Export der eigenen Daten). | MUSS | US-029 | Story vorhanden, Tests offen |
| `DP-008` | Der Export der eigenen personenbezogenen Daten (Maschinenlesbar) ist möglich. | SOLL | US-029 | Story vorhanden, Tests offen |
| `CON-001` | Backend auf JVM. Der Kunde nennt Langchain4j — eine Java-Bibliothek. Damit ist das Backend Spring Boot (Java 21+); die Stack-Vorgabe „FastAPI oder Spring Boot" aus `AGENTS.md` wird dadurch aufgelöst. | — | — | Architektur (Phase 2) |
| `CON-002` | Frontend React (19, TypeScript strict). SSR ist kein Anforderungstreiber (interne Tool-Applikation, kein SEO). | — | — | Architektur (Phase 2) |
| `CON-003` | Datenbank PostgreSQL 16, `organisation_id`-Scoping, Row-Level-Security als zweite Verteidigungslinie. | — | — | Architektur (Phase 2) |
| `CON-004` | KI-Zugriff ausschließlich über Langchain4j (Chat-/Embedding-Modelle), damit der Anbieter konfigurierbar bleibt (Gemini 2.0 Flash / OpenAI, EU). | — | — | Architektur (Phase 2) |
| `CON-005` | Alle Geheimnisse (JWT-Schlüssel, Datenbankpasswort, SMTP, KI-API-Keys) ausschließlich aus der Umgebung; `.env` in `.gitignore`; Secret-Scan in CI. | — | US-034 | Story vorhanden, Tests offen |
| `CON-006` | Alle APIs folgen dem in Phase 2 zu erstellenden Vertrag `docs/api/openapi.yaml` (OAS 3.0+); dieses PRD definiert Verhalten, nicht Syntax. | — | — | Architektur (Phase 2) |
| `CON-007` | Deployment: Docker → Cloud Run (europe-west3) oder Docker Compose lokal; GitHub Actions für CI/CD. | — | — | Architektur (Phase 2) |
| `CON-008` | Coverage-Gate ≥ 80 % in CI (NFR-009) ist Bestandteil der Definition of Done. | — | US-031 | Story vorhanden, Tests offen |
| `CON-009` | Der Prüflauf ist nicht transaktional an den HTTP-Request gekoppelt: Trennung über eine persistente Warteschlange (z. B. Datenbank-Queue) — keine In-Memory-Queue, damit Neustarts nichts verlieren. | — | US-016 | Story vorhanden, Tests offen |
| `CON-010` | Mandantentrennung muss auch auf Infrastruktur-Ebene nachweisbar sein (DB-Nutzer pro Umgebung, keine Shared-Connections über Tenant-Grenzen). | — | — | Architektur (Phase 2) |
