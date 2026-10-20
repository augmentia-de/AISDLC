# PromptGuard — Product Requirement Document

| Feld | Wert |
|---|---|
| Produkt | **PromptGuard** — Zentrale Prompt-Verwaltung & Compliance-Prüfung |
| Version | **1.0** (Grill-Me Runde 1 beantwortet — 2026-10-20) |
| Status | `zur Freigabe` — Entscheidungen F1–F10 getroffen; menschliche Freigabe des Artefaktsets steht aus |
| Datum | 2026-10-20 |
| Quelle | `example/Anforderung/mail.txt` (Kunden-Mail) + `AGENTS.md` / `.agent/knowledge/stack.md` |
| Phase | 1 — Anforderungserhebung (SDLC-Schritt 1 + 1a) |
| Autor | Requirements Orchestrator (`aisdlc-requirements`) |
| Sprachstand | Dokumentation DE · API/Code-Begriffe EN (Gherkin-Szenarien in EN) |

> **Status der Anforderung:** vollständig spezifiziert — **97 Anforderungen** (49 FR, 18 NFR,
> 12 SEC, 8 DP, 10 CON), **35 User Stories**, **108 Akzeptanzkriterien**, **3 Invarianten**,
> **9 Zielkonflikte**, **6 Risiken**. Alle 10 Grill-Me-Fragen (F1–F10) sind beantwortet und in
> [§14](#14-entscheidungen-grill-me-runde-1--beantwortet) dokumentiert.
>
> **[SECURITY] F1 weicht von der Empfehlung ab:** Der Originalinhalt eines Prompts wird
> **unredigiert** an den externen KI-Dienst übermittelt — erkannte Secrets und personenbezogene
> Daten verlassen das System. Der Product Owner hat diese Abweichung bewusst entschieden. Die
> Pflichtmaßnahmen (Auftragsverarbeitungsvertrag, EU-Region, Löschgarantie, Trainingsverbot,
> Provider-Verifikation, Metadaten-Audit) sind als `MUSS` in FR-038, FR-065, FR-074, DP-002,
> DP-005 und US-022/US-034 verankert; das verbleibende Restrisiko ist als **R-06 (akzeptiert)**
> dokumentiert. Details in [§15](#15-zielkonflikte--auflösungen) und
> `artifacts/risk-register.md`.

---

## 1. Executive Summary

**PromptGuard** ist eine mandantenfähige Plattform, in der Entwicklerteams ihre LLM-Prompts
zentral **anlegen, kategorisieren, versionieren, suchen** und **automatisch auf Sicherheitsrisiken
und Compliance-Verstöße prüfen** lassen.

Kern der Lösung ist ein **asynchroner Prüfprozess**: Ein neu angelegter Prompt wird sofort
gespeichert (`PENDING`) und im Hintergrund von einem KI-Service (Langchain4j) geprüft. Die Prüfung
liefert ein **Freigabe-Urteil** (`APPROVED` / `FLAGGED` / `FAILED`) mit Fundstellen, die im UI live
eintreffen. Erkannte Risiken — Prompt Injection, Jailbreak, Secrets, personenbezogene Daten —
lösen automatisch eine Warn-E-Mail an den Security-Admin der jeweiligen Organisation aus.

**Harte Mandantentrennung** ist die oberste Sicherheitsanforderung: Entwickler der Abteilung A
sehen unter keinen Umständen Prompts, Fundstellen, Suchergebnisse oder Existenzhinweise der
Abteilung B. Diese Trennung gilt auch für den Duplikat-Vergleich, Fehlermeldungen, Logs, Caches
und Metriken.

| | |
|---|---|
| **Problem** | Prompts liegen verstreut in Git-Repos, Textdateien und lokalen Ablagen — kein Überblick, keine Qualitäts- oder Sicherheitskontrolle, zunehmende Doppelarbeit. |
| **Lösung** | Zentrales Register + asynchroner Compliance-Scan + Live-Status + Alarmierung. |
| **Nutzen** | Sichtbarkeit, doppelte Arbeit vermieden, Secrets/PII werden vor der Nutzung erkannt, DSGVO-nachweisbare Aufbewahrung. |
| **Nicht-Ziel** | Kein Prompt-Runtime-Serving, kein Prompt-Marketplace, keine Modell-Orchestrierung im MVP. |

---

## 2. Problem Statement & Kontext

Die Entwicklung nutzt LLM-Prompts in vielfältigen Anwendungsfällen (Kundensupport-Bots,
Code-Generatoren u. a.). Die Prompts liegen unkoordiniert verstreut. Es fehlen:

1. **Zentrales Register** — kein Überblick, welche Prompts existieren, wer sie nutzt, in welcher Version.
2. **Qualitätskontrolle** — keine Prüfung auf Redundanz (doppelte Arbeit) oder auf ungenutzte/veraltete Stände.
3. **Sicherheitskontrolle** — keine automatische Prüfung auf Prompt Injection, Jailbreak, versehentlich eingebettete Secrets oder personenbezogene Daten.
4. **Nachvollziehbarkeit** — kein Audit, wer hat wann welchen Prompt geändert, wer hat ihn freigegeben.
5. **Datenschutz** — keine definierte Aufbewahrung, kein Löschkonzept, keine nachweisbare Mandantentrennung.

**Randbedingungen aus der Kunden-Mail:** Mehrere Abteilungen/Organisationen nutzen die Plattform
parallel; Anmeldung per JWT; React-Oberfläche; Prüfung mit Langchain4j; Prüfung darf die UI nicht
blockieren; Live-Status in der UI; Warn-E-Mail an den Security-Admin; OWASP-Niveau, keine Secrets
im Code, ≥ 80 % Testabdeckung; DSGVO mit 90-Tage-Löschung; geordnetes Nachholen bei Ausfall oder
Langsamkeit des KI-Dienstes.

---

## 3. Vision, Mission & Leitprinzipien

**Vision** — Der zentrale, vertrauenswürdige Ort, an dem jedes LLM-Prompt eines Unternehmens
entsteht, versioniert wird und sicher in den Einsatz kommt.

**Mission** — Entwickler bekommen in Sekunden Feedback, ob ihr Prompt sauber ist, und
Organisationen bekommen die Gewissheit, dass vertrauliche Daten und Injection-Tricks weder
gespeichert noch weitergereicht werden.

**Leitprinzipien** (gelten für jede Design-Entscheidung in Phase 2+):

| # | Prinzip | Konsequenz |
|---|---|---|
| P1 | **Isolation vor Feature** | Mandantentrennung ist keine Funktion, sondern eine Invariante. Kein Endpoint, kein Hintergrund-Job, kein Cache darf sie umgehen. |
| P2 | **Fail-safe, nicht fail-closed-blind** | Ein verdächtiger Prompt wird blockiert, ein *nicht prüfbarer* Prompt wird als ungeprüft markiert und ist **reproduzierbar** manuell erneut auslösbar. |
| P3 | **Nicht blockieren, sondern informieren** | Asynchronität: Speichern ist sofort, Prüfung folgt. Die UI bleibt reaktionsfähig. |
| P4 | **Datenminimierung** | Was das System nicht speichern muss (Roh-Payloads, Secrets, Log-Inhalte), speichert es nicht. **Einschränkung (F1):** vor der Übermittlung an den KI-Dienst wird **nicht** redigiert — Minimierung wird stattdessen über Löschfristen, Provider-Auflagen und Metadaten-Logging umgesetzt. |
| P5 | **Nachvollziehbarkeit** | Jede Statusänderung, Freigabe und Löschung ist auditierbar. |
| P6 | **Verdacht ≠ Schuld** | Die KI-Prüfung erzeugt einen *Hinweis* mit Begründung und Schweregrad; die Entscheidung über eine Ausnahme trifft ein Mensch (Security-Admin). |

---

## 4. Stakeholders & Personas

| Persona | Rolle | Bedürfnis | Rechte |
|---|---|---|---|
| **Entwickler:in** | `DEVELOPER` | Prompt anlegen, bearbeiten, versionieren, finden; wissen, ob er freigegeben ist | nur eigene Organisation |
| **Security-Admin** | `SECURITY_ADMIN` | Warnungen erhalten, geflaggte Prompts prüfen, ggf. Ausnahme freigeben, eigene Prompts verwalten | nur eigene Organisation |
| **Org-Admin** | `ORG_ADMIN` | Mitglieder und Rollen verwalten, Kategorien pflegen, Security-Admin-Empfänger und Org-Einstellungen setzen | nur eigene Organisation |
| **Registrierender Nutzer** | → `ORG_ADMIN` der neuen Org | Selbst-Organisation anlegen, Team einladen | eigene neue Organisation |
| **Plattform-Betrieb** | Betrieb | Betrieb, SLIs, Backups, Provider-Konfiguration | plattformweit, **ohne** fachlichen Prompt-Zugriff |
| **Fachbereich/Product Owner** | Alex (Head of Developer Tooling) | Überblick, Kennzahlen, Nachweis für Compliance | Bericht/Export (orgspezifisch) |

**Ausdrücklich nicht als Persona:** ein **globaler** Security-Admin über alle Organisationen hinweg.
Die Kunden-Mail sagt „der Security-Admin" ohne Geltungsbereich → **offene Entscheidung F3**.

---

## 5. Ziele, Erfolgskriterien & KPIs

| # | Ziel | Messbare Größe (KPI) | Zielwert |
|---|---|---|---|
| K1 | Ein zentrales Register schafft Überblick | Anteil der Prompts, die in PromptGuard statt in Git/Textdateien gepflegt werden | ≥ 80 % nach 6 Monaten |
| K2 | Doppelte Arbeit vermeiden | Anteil der Neuanlagen mit Duplikatwarnung, die zur Wiederverwendung statt zur Neuanlage führen | ≥ 50 % der Warnungen |
| K3 | Kein Secret im Register | Anzahl freigegebener Prompts mit Secret-/PII-Fund | **0** (hartes Kriterium) |
| K4 | Mandantentrennung ohne Lücken | Cross-Tenant-Zugriffsversuche in Tests, die Daten liefern | **0** |
| K5 | UI bleibt reaktionsfähig | p95 Latenz `POST /prompts` | < 500 ms (Scan läuft asynchron) |
| K6 | Scan ist schnell genug | p95 Scan-Dauer (Enqueue → Urteil) | < 30 s |
| K7 | Alarmierung wirkt | Anteil Security-Admin-Alarme, die zugestellt werden | ≥ 99 % innerhalb von 5 min |
| K8 | DSGVO nachweisbar | Anteil alter Roh-Payloads/Logs > 90 Tage | **0** |
| K9 | Qualität | Line Coverage Backend + Frontend | ≥ 80 % (Quality Gate) |

---

## 6. Scope

### 6.1 In Scope (MVP)

| # | Umfang |
|---|---|
| S1 | Registrierung (erzeugt eine neue Organisation), Login, JWT, Refresh/Logout, Rollen `DEVELOPER` / `SECURITY_ADMIN` / `ORG_ADMIN` |
| S2 | Strikte Mandantentrennung auf Datensatz-, Such-, Cache-, Log- und Job-Ebene |
| S3 | Prompt-CRUD: Titel, Beschreibung, Inhalt, Kategorie, Tags, Versionierung |
| S4 | Kategorienverwaltung (Seed-Liste + CRUD durch `ORG_ADMIN`), Tags (frei, orgspezifisch) |
| S5 | Volltextsuche + Filter (Kategorie, Tag, Scan-Status) + Sortierung + Pagination |
| S6 | Asynchroner KI-Prüflauf: Duplikatprüfung, Prompt-Injection/Jailbreak, Secrets, PII |
| S7 | Scan-Ergebnis mit Fundstellen (Kategorie, Schweregrad, redigierter Fundstelle, Begründung) |
| S8 | Scan-Statusmodell inkl. Live-Aktualisierung in der UI |
| S9 | Warn-E-Mail an Security-Admin der Organisation (ohne Prompt-Inhalt) + Zustellungs-Wiederholung |
| S10 | Manuelle Ausnahme-Freigabe (`WAIVED`) eines geflaggten Prompts durch `SECURITY_ADMIN`, mit Begründung und Audit-Eintrag |
| S11 | 90-Tage-Retention-Job, Recht auf Löschung, Datenschutzhinweis DE/EN, EU-Hosting |
| S12 | Observability (Health, strukturierte Logs ohne Prompt-Inhalt, Metriken), Coverage-Gate ≥ 80 % |

### 6.2 Out of Scope (bewusst nicht gebaut)

| # | Item | Begründung |
|---|---|---|
| O1 | Prompt-Runtime / Serving / Ausführung von Prompts | Die Mail fordert *Verwaltung und Prüfung*, keine Ausführung. Plattform bleibt Registry. |
| O2 | Prompt-Marketplace / organisationsübergreifender Austausch | Widerspricht der Isolation (P1) und dem Zweck (Abteilungs-Working-Groups). |
| O3 | Platzhalter-/Variablen-Syntax und Template-Engine | Erhöht die Scan-Angriffsfläche erheblich und ist nicht gefordert. |
| O4 | A/B-Testing, Prompt-Performance-/Kosten-Messung | Nicht gefordert; eigenes Vorhaben. |
| O5 | Git-Sync / Import bestehender Prompt-Repositories | Für MVP zu groß; K1 (Überblick) wird über Migration im MVP-Betrieb erreicht. |
| O6 | SSO / OIDC / SAML, MFA | Nicht gefordert → **F9**. |
| O7 | Globaler Plattform-Admin mit Sicht auf alle Organisationen | Widerspricht Isolation; nur Betrieb ohne Fachdaten. |
| O8 | Chat-/LLM-Interface in der UI | Nicht gefordert. |
| O9 | Freigabe-Workflow mit mehrstufiger Abnahme (Vier-Augen) | MVP: eine Security-Admin-Freigabe genügt. |

### 6.3 Später (Post-MVP, keine Story in diesem Backlog)

Export/Reporting der Compliance-Lage · Dashboard der Kennzahlen K1–K3 · Integration in CI/CD
(Prompt-Lint) · SSO/OIDC · MFA · Feinabstimmbare Erkennungsschwellen pro Organisation ·
Mehrsprachige Inhalte (i18n) · Webhooks statt E-Mail.

---

## 7. Fachliches Modell & Terminologie

### 7.1 Entitäten

```
Organisation 1──n User            (Mitglied einer Organisation, Rolle je Mitglied)
Organisation 1──n Prompt          (logischer Prompt, besitzt Versionen)
Prompt       1──n PromptVersion   (unveränderlicher Snapshot, trägt den Prüfstatus)
PromptVersion 1──1 Scan           (Prüflauf über genau diese Version)
Scan         1──n Finding         (Fundstelle: Kategorie, Schweregrad, redigierter Ausschnitt)
Organisation 1──n Category        (Klassifikation, orgspezifisch)
Prompt       n──n Tag             (frei, orgspezifisch)
PromptVersion 1──n Notification   (Zustellversuch der Warn-E-Mail)
Organisation 1──n AuditEvent      (sicherheitsrelevante Aktion, ohne Prompt-Inhalt)
```

**Schlüsselattribute**

| Entität | Attribute |
|---|---|
| `Organisation` | `id`, `name`, `created_at` |
| `User` | `id`, `email`, `password_hash`, `organisation_id`, `role`, `status`, `created_at` |
| `Prompt` | `id`, `organisation_id`, `title`, `description`, `category_id`, `status` (Status der aktuellen Version), `current_version_id`, `created_by`, `created_at`, `updated_at`, `deleted_at` |
| `PromptVersion` | `id`, `prompt_id`, `version_no`, `content`, `content_hash`, `status`, `created_by`, `created_at`, `scanned_at` |
| `Scan` | `id`, `prompt_version_id`, `status`, `started_at`, `finished_at`, `attempt_count`, `last_error` (technisch, ohne Inhalt) |
| `Finding` | `id`, `scan_id`, `category`, `severity`, `redacted_excerpt`, `explanation`, `detector` |
| `Category` | `id`, `organisation_id`, `name`, `is_default` |
| `Tag` | `id`, `organisation_id`, `name` (unique je Organisation) |
| `Notification` | `id`, `prompt_version_id`, `recipient`, `status`, `attempts`, `last_error`, `sent_at` |
| `AuditEvent` | `id`, `organisation_id`, `actor_id`, `action`, `entity_type`, `entity_id`, `at`, `metadata` (ohne Inhalt) |

### 7.2 Statusmodell (Kern der Fachlogik)

Der Prüfstatus hängt **ausschließlich an einer Version**, nicht am Prompt. Der Prompt zeigt den
Status seiner aktuellen Version.

| Status | Bedeutung | Prompt nutzbar? |
|---|---|---|
| `PENDING` | Angelegt, Prüfung eingereiht, noch nicht gestartet | nein |
| `SCANNING` | Prüfung läuft gerade | nein |
| `APPROVED` | Prüfung abgeschlossen, kein Fund ab Schwellenwert | **ja** |
| `FLAGGED` | Risiko erkannt (Injection/Jailbreak/Secret/PII ≥ Schwelle) | nein |
| `WAIVED` | Risiko erkannt, von `SECURITY_ADMIN` bewusst freigegeben (mit Begründung + Audit) | **ja** |
| `FAILED` | Prüfung technisch nicht durchführbar (KI-Dienst nicht erreichbar) nach allen Wiederholversuchen | nein |

**Zustandsübergänge (Invarianten)**

| Von | Nach | Bedingung |
|---|---|---|
| — | `PENDING` | Prompt/Version angelegt |
| `PENDING` | `SCANNING` | Worker übernimmt den Job |
| `SCANNING` | `APPROVED` | kein Fund ≥ Schwellenwert |
| `SCANNING` | `FLAGGED` | mindestens ein Fund ≥ Schwellenwert |
| `SCANNING` | `FAILED` | alle Wiederholversuche fehlgeschlagen |
| `FAILED` | `PENDING` | manuelle oder automatische Wiederholung |
| `FLAGGED` | `WAIVED` | nur `SECURITY_ADMIN`, mit Pflicht-Begründung, Audit-Eintrag |
| `FLAGGED`/`WAIVED`/`APPROVED` | `PENDING` | **jede inhaltliche Änderung erzeugt eine neue Version** — die alte Freigabe gilt nie für neuen Inhalt |

**Invariante I-1 (sicherheitskritisch):** Eine Freigabe (`APPROVED`/`WAIVED`) ist an den
`content_hash` der Version gebunden. Jede Änderung des Inhalts erzeugt eine neue Version mit
Status `PENDING`. Es gibt keinen Pfad, auf dem ein Prompt mit geändertem Inhalt den Status
`APPROVED` behält.

**Invariante I-2 (Isolation):** Jede Lese- und Schreiboperation ist auf `organisation_id` des
authentifizierten Nutzers beschränkt — ohne Ausnahme, auch für Hintergrund-Jobs, Suchindex,
Aggregate, Fehlermeldungen, Existenzprüfungen (`404` statt `403`, wenn die Existenz selbst
schützen soll) und Cache-Keys.

**Invariante I-3 (Freigabe-Gate):** Nur `APPROVED` und `WAIVED` gelten als „freigegeben". Alles
andere ist gespeichert, aber nicht nutzbar/exportierbar.

### 7.3 Glossar

| Begriff | Definition |
|---|---|
| **Mandant / Organisation** | Isolierte Einheit (Abteilung). Alle Daten, Rollen, Kategorien, Tags, Suchergebnisse sind orgspezifisch. |
| **Prompt** | Logischer Datensatz mit Titel, Beschreibung, Kategorie, Tags und ≥ 1 Version. |
| **Version** | Unveränderlicher Snapshot des Prompt-Inhalts. Trägt den Prüfstatus. |
| **Scan / Prüflauf** | Asynchroner KI-Prüfvorgang über genau eine Version. |
| **Finding / Fundstelle** | Konkreter Befund eines Scans: Kategorie, Schweregrad, maskiert gezeigter Ausschnitt, Begründung. Die Maskierung betrifft nur die **Anzeige**; der übermittelte Inhalt bleibt unverändert (F1). |
| **Schweregrad** | `LOW` \| `MEDIUM` \| `HIGH` \| `CRITICAL`. |
| **Schwelle** | Konfigurierbarer Mindest-Schweregrad, ab dem ein Scan `FLAGGED` wird. Default `MEDIUM`. |
| **Duplikatwarnung** | Advisory-Hinweis, dass ein inhaltlich ähnlicher Prompt in **derselben** Organisation existiert. Blockiert nie. |
| **Redaktion** | Ersetzen erkannter Secret-/PII-Spans durch einen Platzhalter. **In PromptGuard nicht verwendet (F1):** Der Originalinhalt wird unverändert übermittelt; Fundstellen werden nur in der *Anzeige* maskiert. |
| **Ausnahme / Waiver** | Dokumentierte manuelle Freigabe eines geflaggten Prompts. |
| **Erkennungskategorien** | `PROMPT_INJECTION`, `JAILBREAK`, `SECRET`, `PII`. |

---

## 8. Funktionale Anforderungen

> Format: `FR-xxx` · **Muss/Soll**: `MUSS` = verbindlich für den MVP, `SOLL` = vorgesehen,
> Umfang im MVP nicht bindend.

### 8.0 Epic-Übersicht

| Epic | Zweck | Stories | Umfang |
|---|---|---|---|
| **E1** Identität & Zugriff | Anmeldung, Rollen, Mitgliedschaft, Isolation | US-001 … US-006 | FR-001…FR-009 · SEC-001…SEC-004 · SEC-008 |
| **E2** Prompt-Verwaltung | CRUD, Kategorien, Tags, Versionierung, Löschen | US-007 … US-012 | FR-010…FR-017 · DP-004 |
| **E3** Suche & Filterung | Volltextsuche, Filter, Sortierung, Pagination | US-013 … US-015 | FR-020…FR-023 · NFR-004 · NFR-008 |
| **E4** Asynchrone KI-Prüfung | Prüflauf, Duplikat, Injection, Secrets, PII, Redaktion, Waiver | US-016 … US-023 | FR-030…FR-039 · DP-002 · SEC-006 · SEC-007 |
| **E5** Status & Live-Aktualisierung | Live-Status, Audit-Historie | US-024 … US-025 | FR-040…FR-042 · NFR-003 · SEC-010 |
| **E6** Alarmierung | Warn-E-Mail, Wiederholung, Totmannschaltung | US-026 … US-027 | FR-050…FR-053 · NFR-007 |
| **E7** Datenschutz & Aufbewahrung | Retention, Recht auf Löschung, Datenschutzhinweis | US-028 … US-030 | FR-060…FR-064 · DP-001 · DP-003…DP-008 |
| **E8** Qualität & Betrieb | Coverage-Gate, Observability, Degraded Mode, Sicherheitsbaseline, Barrierefreiheit | US-031 … US-035 | FR-070…FR-073 · FR-065 · NFR-006…NFR-018 · SEC-005 · SEC-009 · SEC-011 · SEC-012 · DP-005 · CON-005 · CON-008 |

> **35 User Stories · 108 Akzeptanzkriterien · 35 Gherkin-Features** — Details in
> `artifacts/user-stories.md` und `stories/US-*.json`.

### E1 — Identität & Zugriff

| ID | Anforderung | Priorität |
|---|---|---|
| FR-001 | Der Dienst stellt ausschließlich JWT-basierte Anmeldung bereit (E-Mail + Passwort). Der Access-Token enthält `sub`, `organisation_id`, `role`, `exp`; das Signieren erfolgt mit einem ausschließlich aus der Umgebung gelesenen Schlüssel. | MUSS |
| FR-002 | Registrierung legt eine **neue Organisation** an und setzt den ersten Nutzer als `ORG_ADMIN`. Eine Registrierung kann keiner bestehenden Organisation beitreten. | MUSS |
| FR-003 | Ein Gast ohne gültiges Token erhält `401`; ein gültiges Token ohne erforderliche Rolle erhält `403`. Beide Antworten enthalten keine Datenexistenzinformation. | MUSS |
| FR-004 | Rollenmodell: `DEVELOPER` (eigene Organisation, Schreiben/Lesen), `SECURITY_ADMIN` (zusätzlich Review, Waiver, Alarm-Empfang), `ORG_ADMIN` (zusätzlich Mitglieder, Kategorien, Org-Einstellungen). Rollen sind organisationsgebunden. | MUSS |
| FR-005 | `ORG_ADMIN` lädt Mitglieder per E-Mail-Einladung ein und weist Rollen zu. Eine Einladung ist einmalig, zeitlich begrenzt (Default 7 Tage) und nur für die eigene Organisation gültig. | MUSS |
| FR-006 | `ORG_ADMIN` hinterlegt je Organisation eine Liste von Security-Admin-Empfängeradressen (1–n). Es sind nur Adressen innerhalb der Organisation hinterlegbar. | MUSS |
| FR-007 | Access-Token sind kurzlebig (Default 15 min); Refresh-Token werden rotiert (Default 30 Tage) und bei Nutzung invalidiert; Logout invalidiert die Session-Serienkette. | MUSS |
| FR-008 | Passwort-Hashing mit bcrypt/argon2id (Cost ≥ 12). Das Klartextpasswort verlässt den Registrierungs-/Login-Fluss nie. | MUSS |
| FR-009 | Die Anwendung stellt die Mandantentrennung auf **jeder** Route sicher (Authentifizierung, Autorisierung, Mandantenfilter, Hintergrund-Jobs, Suche, Export, Cache, Logs, Metriken). | MUSS |

### E2 — Prompt-Verwaltung

| ID | Anforderung | Priorität |
|---|---|---|
| FR-010 | Ein Prompt besteht aus Titel (Pflicht, 1–200 Zeichen), Beschreibung (optional, ≤ 2 000 Zeichen), Inhalt (Pflicht, ≤ 50 000 Zeichen), genau einer Kategorie und 0–n Tags. | MUSS |
| FR-011 | Beim Anlegen wird automatisch Version 1 erzeugt mit Status `PENDING`; die API-Antwort bestätigt das Anlegen **unabhängig** vom Prüfergebnis (asynchron, siehe FR-030). | MUSS |
| FR-012 | Jede Änderung von Inhalt, Titel, Beschreibung, Kategorie oder Tags erzeugt eine **neue Version** mit Status `PENDING`. Die vorherige Version bleibt unverändert und behält ihren Status. | MUSS |
| FR-013 | Versionshistorie ist einsehbar (Version, Ersteller, Zeitpunkt, Status, Fundstellen-Zahl); die Versionsinhalte sind unveränderlich. Der Vergleich zweier Versionen wird angeboten. | MUSS |
| FR-014 | Kategorien sind organisationsspezifisch. Beim Anlegen der Organisation wird eine Seed-Liste angelegt (`Customer Support`, `Code Gen`, `Data Analysis`, `Documentation`, `Other`). `ORG_ADMIN` kann Kategorien anlegen, umbenennen, deaktivieren; Kategorien mit existierenden Prompts können nicht gelöscht, nur deaktiviert werden. | MUSS |
| FR-015 | Tags sind freitext, organisationsspezifisch, Groß-/Kleinschreibung wird normalisiert, Duplikate innerhalb einer Organisation sind unmöglich. Max. 10 Tags je Prompt, max. 200 Tags je Organisation. | MUSS |
| FR-016 | Ein `ORG_ADMIN` oder der Ersteller löscht einen Prompt. Dabei werden alle Versionen, Scans und Fundstellen gelöscht; der Audit-Eintrag bleibt ohne Prompt-Inhalt bestehen. | MUSS |
| FR-017 | Ein Prompt kann kopiert werden; die Kopie startet als neue, eigenständige Organisationseinheit mit Status `PENDING`. | SOLL — **Post-MVP, ohne Story in diesem Backlog** (§6.3) |

### E3 — Suche & Filterung

| ID | Anforderung | Priorität |
|---|---|---|
| FR-020 | Volltextsuche über Titel, Beschreibung und Inhalt innerhalb der eigenen Organisation. Mindestens-Präfix- und Substring-Treffer; die Suche berücksichtigt keine gelöschten Objekte. | MUSS |
| FR-021 | Filter: Kategorie (Mehrfachauswahl), Tag (Mehrfachauswahl), Scan-Status (Mehrfachauswahl). Filter sind kombinierbar. | MUSS |
| FR-022 | Sortierung nach Aktualisierungszeit, Erstellungszeit, Titel und Prüfstatus (jeweils auf-/absteigend). Pagination mit Seite und Seitengröße (Default 50, max 200). | MUSS |
| FR-023 | Suchergebnisse enthalten **niemals** Daten einer anderen Organisation — auch nicht als Anzahl, Vorschau, Fundstelle oder Existenzhinweis. | MUSS |

### E4 — Asynchrone KI-Prüfung

| ID | Anforderung | Priorität |
|---|---|---|
| FR-030 | Das Anlegen/Bearbeiten eines Prompts liefert sofort eine Bestätigung; die Prüfung startet asynchron über eine Warteschlange. Die UI wird durch die Prüfung nicht blockiert. | MUSS |
| FR-031 | Jede Version wird genau einem Prüflauf unterzogen. Der Prüflauf umfasst: (a) lokale deterministische Vorprüfung (Secrets/PII), (b) Duplikatvergleich, (c) LLM-Klassifikation, (d) Aggregation zum Gesamturteil. Die Stufen (a) und (b) laufen **vor** (c); ihre Fundstellen fließen in das Gesamturteil ein. In Stufe (c) wird der Originalinhalt **unverändert** übermittelt (FR-038). | MUSS |
| FR-032 | **Duplikatwarnung:** Der Vergleich erfolgt **ausschließlich innerhalb derselben Organisation**. Ähnlichkeit wird über ein konfigurierbares Maß bestimmt (Default: Vektor-Ähnlichkeit ≥ 0,90; zusätzlich normalisierter Textvergleich). Das Ergebnis listet bis zu 5 ähnliche Prompts mit Titel, Score und Verweis. Die Warnung ist **beratend** und blockiert das Anlegen nie. | MUSS |
| FR-033 | **Prompt-Injection & Jailbreak:** Erkennung von Mustern, die die Anweisungshierarchie umkehren (`ignore previous instructions`, Rollenwechsel, Delimiter-Ausbruch) oder Sicherheitsleitplanken aushebeln (Jailbreak-Formulierungen, `DAN`-Stil, Aufforderung zur Selbstverifikation). | MUSS |
| FR-034 | **Secrets:** Erkennung von API-Keys, Tokens, Passwörtern, privaten Schlüsseln, Connection-Strings, JWTs und hochentropischen Geheimnissen. | MUSS |
| FR-035 | **PII:** Erkennung von E-Mail-Adressen, Telefonnummern, IBAN/Kreditkartendaten, personen- und Kundenbezogenen Namen, sowie semantisch erkannten personenbezogenen Inhalten. | MUSS |
| FR-036 | Das Gesamturteil wird aus den Fundstellen abgeleitet: mindestens ein Fund ≥ Schwellenwert → `FLAGGED`; sonst `APPROVED`. Die Schwelle ist organisationsweit konfigurierbar (Default `MEDIUM`). | MUSS |
| FR-037 | Das Scan-Ergebnis zeigt je Fundstelle: Kategorie, Schweregrad, **redigierter** Ausschnitt (Secret-/PII-Spans maskiert), Fundstelle im Text (Position/Zeile), Begründung und die auslösende Regel bzw. das Modellurteil. | MUSS |
| FR-038 | Der zu prüfende **Originalinhalt** wird **unverändert** an den KI-Dienst übermittelt — es findet **keine Redaktion** statt (Entscheidung F1). Übermittelt wird ausschließlich an den gemäß FR-065 verifizierten Dienst; schlägt die Verifikation fehl, bricht der Scan ab, ohne dass Inhalt das System verlässt. | MUSS |
| FR-039 | `SECURITY_ADMIN` kann eine geflaggte Version als Ausnahme freigeben (Status `WAIVED`), verpflichtend mit Begründung; der Vorgang erzeugt einen Audit-Eintrag mit Actor, Zeitpunkt und Begründung. | MUSS |

### E5 — Status & Live-Aktualisierung

| ID | Anforderung | Priorität |
|---|---|---|
| FR-040 | Der Prüfstatus wird in der UI **ohne Neuladen** aktualisiert, wenn er wechselt. Der Transportmechanismus (SSE oder Polling) ist eine Architekturentscheidung in Phase 2; die beobachtbare Anforderung lautet: Statuswechsel erscheint ≤ 2 s nach Abschluss des Prüflaufs. | MUSS |
| FR-041 | Der Statuswechsel erzeugt einen Audit-/Historie-Eintrag (Zeitpunkt, alter Status, neuer Status, auslösender Vorgang). | MUSS |
| FR-042 | Ein hängender Prüflauf (Status `SCANNING`) wird nach einer Frist (> 5 min ohne Abschluss) als `FAILED` markiert und ist erneut auslösbar. | MUSS |

### E6 — Alarmierung

| ID | Anforderung | Priorität |
|---|---|---|
| FR-050 | Wird eine Version `FLAGGED`, sendet das System automatisch eine Warn-E-Mail an alle konfigurierten Security-Admin-Empfänger der Organisation. | MUSS |
| FR-051 | Die E-Mail enthält **keinen Prompt-Inhalt und keine Fundstellen-Inhalte** — nur Organisation, Prompt-ID/Titel, Version, Fundkategorien und Schweregrade, Zeitpunkt und einen Deep-Link in die Oberfläche. | MUSS |
| FR-052 | Zustellfehler (SMTP nicht erreichbar) führen zu Wiederholung mit Backoff; nach Erschöpfung der Versuche wandert der Zustellversuch in eine Totmannschaltung, die in der Oberfläche sichtbar ist. Der Prüfstatus ist vom Zustellerfolg **unabhängig**. | MUSS |
| FR-053 | Ist für eine Organisation kein Security-Admin-Empfänger hinterlegt, wird der Alarm an die `ORG_ADMIN`s der Organisation zugestellt; der Zustellversuch wird in beiden Fällen protokolliert. | MUSS |

### E7 — Datenschutz & Aufbewahrung

| ID | Anforderung | Priorität |
|---|---|---|
| FR-060 | Ein Retention-Job löscht Request-/Access-Logs, Scan-Rohdaten, Notification-Protokolle und Audit-Einträge mit Inhaltsbezug, die älter als 90 Tage sind. Der Job ist idempotent und täglich geplant. | MUSS |
| FR-061 | Rohe KI-Anfrage-/Antwort-Payloads werden nicht dauerhaft gespeichert; sie werden spätestens nach 24 h gelöscht. Dauerhaft gespeichert werden nur das verdichtete Ergebnis (Kategorien, Schweregrade, redigierte Ausschnitte). | MUSS |
| FR-062 | In Applikations-Logs erscheinen **niemals** Prompt-Inhalte, Secrets, Tokens oder personenbezogene Daten. Log-Zeilen enthalten eine Korrelation-ID, aber keinen Inhalt. | MUSS |
| FR-063 | Recht auf Löschung: Ein `ORG_ADMIN` oder der Betroffene selbst kann die Löschung eines Nutzerkontos veranlassen. Konto, Tokens und Sitzungen werden gelöscht, die Autorschaft in bestehenden Versionen wird anonymisiert, der Prompt-Bestand bleibt bestehen und wird erneut geprüft. | MUSS |
| FR-064 | Der Datenschutzhinweis liegt in Deutsch und Englisch vor, verlinkt aus der Anwendung und nennt Zwecke, Rechtsgrundlage, Aufbewahrungsfristen und Rechte. | MUSS |
| FR-065 | Alle Daten bleiben in der EU (europe-west3). Der KI-Anbieter wird mit „keine Datenverwendung zum Training" und ohne dauerhafte Speicherung konfiguriert; dies ist in der Konfiguration nachweisbar. | MUSS |

### E8 — Qualität & Betrieb

| ID | Anforderung | Priorität |
|---|---|---|
| FR-070 | Der Betrieb unterstützt den Ausfall oder die extreme Verlangsamung des KI-Dienstes: Anfragen werden **geordnet** nachgeholt (Warteschlange, Retry mit Backoff, Totmannschaltung), das System stürzt nicht ab, und kein Datenverlust entsteht. | MUSS |
| FR-071 | Ein `FAILED`er Prüflauf ist manuell erneut auslösbar; die Wiederholung ist im Audit-Protokoll sichtbar. | MUSS |
| FR-072 | Es gibt einen Health-Endpunkt sowie strukturierte Logs und Metriken (Warteschlangentiefe, Scan-Dauer, Fehlerquote, Zustellquote, Scan-Statusverteilung). | MUSS |
| FR-073 | Die CI-Prüfung schlägt fehl, wenn die Testabdeckung (Line Coverage, Backend + Frontend) unter 80 % liegt. | MUSS |
| FR-074 | Jede Übermittlung von Prompt-Inhalten an den KI-Dienst wird als **Metadaten-Ereignis** protokolliert (Zeitpunkt, Provider, Modell, Zeichenlänge, Ergebnis-ID, Korrelation-ID) — **ohne Inhalt**. Grundlage für die Nachweisführung nach Art. 30 DSGVO und für die Zuordnung von Fundstellen zur Übermittlung. | MUSS |

---

## 9. Nicht-funktionale Anforderungen

| ID | Kategorie | Anforderung | Zielwert |
|---|---|---|---|
| NFR-001 | Performance | `POST /prompts` bestätigt das Anlegen asynchron | p95 < 500 ms |
| NFR-002 | Performance | Prüflauf Enqueue → Urteil | p95 < 30 s, p99 < 120 s |
| NFR-003 | Performance | Live-Statuswechsel in der Oberfläche | ≤ 2 s nach Urteil |
| NFR-004 | Performance | Suche/Filter bei 100 000 Prompts in einer Organisation | p95 < 300 ms |
| NFR-005 | Performance | `GET /prompts/{id}` | p95 < 200 ms |
| NFR-006 | Verfügbarkeit | Verfügbarkeit der Kern-API (Anlegen, Lesen, Suchen) — **unabhängig** von der Erreichbarkeit des KI-Dienstes | 99,9 % |
| NFR-007 | Verfügbarkeit | Degraded Mode: KI-Dienst nicht erreichbar → Anlegen funktioniert, Prüfung bleibt in Warteschlange/`FAILED`, kein Fehler in der UI, kein Datenverlust | MUSS |
| NFR-008 | Skalierbarkeit | Auslegungsziel: 100 Organisationen, 10 000 Nutzer, 1 000 000 Prompts, 5 000 Prüfläufe/min | MUSS |
| NFR-009 | Qualität | Testabdeckung Backend + Frontend | ≥ 80 % Line Coverage, Gate in CI |
| NFR-010 | Qualität | Testarten: Unit, Integration (echte DB, echte Tenant-Isolation), E2E (Login → Prompt → Scan → Alarm) | MUSS |
| NFR-011 | Sicherheit | OWASP Top 10 — keine offenen Findings mit Critical/High zum Security-Gate (Phase 8) | 0 |
| NFR-012 | Sicherheit | Keine Secrets in Code, Konfiguration, Images oder Logs; Secret-Scan in CI | 0 |
| NFR-013 | Datenschutz | Retention 90 Tage; Roh-Payloads ≤ 24 h; EU-Hosting ohne Datenabfluss | 0 Verstöße |
| NFR-014 | Beobachtbarkeit | Health-Endpunkt, strukturierte Logs mit Korrelation-ID, Metriken; Alerts bei Warteschlangentiefe > X und Fehlerquote > Y | MUSS |
| NFR-015 | Barrierefreiheit | WCAG 2.1 AA (Tastaturbedienung, Screenreader, Kontrast) für die Web-Oberfläche | MUSS |
| NFR-016 | Wartbarkeit | Typsicherheit strikt (TypeScript strict, Java-Compiler-Warnungen als Fehler), Formatierung/Linting in CI, keine kritischen Warnungen | MUSS |
| NFR-017 | Portabilität | Container-Images laufen als non-root, mit gepinnten Tags und Health-Check; lokale Entwicklung über `docker-compose` | MUSS |
| NFR-018 | Internationalisierung | Oberfläche deutsch (Zielgruppe), API-Fehler und Code englisch; Datenschutzhinweis DE/EN | SOLL |

---

## 10. Sicherheitsanforderungen

| ID | Anforderung | Priorität |
|---|---|---|
| SEC-001 | Authentifizierung ausschließlich per JWT (asymmetrisch oder HMAC mit umgebungsbasiertem Schlüssel); kein Session-Cookie, kein LocalStorage für Tokens (Refresh-Token nur in `HttpOnly`/`SameSite`-Cookie). | MUSS |
| SEC-002 | Mandantentrennung: jede Query trägt den `organisation_id`-Filter; zusätzlich PostgreSQL Row-Level-Security als zweite Verteidigungslinie. Ein Cross-Tenant-Zugriff liefert `403`/`404` **ohne** Dateninhalt. | MUSS |
| SEC-003 | Jeder Hintergrund-Job (Prüflauf, Benachrichtigung, Retention) trägt die `organisation_id` des betroffenen Objekts im Job-Kontext; ein Job ohne auflösbaren Tenant-Kontext wird verworfen und protokolliert. | MUSS |
| SEC-004 | Rollenprüfung serverseitig auf jeder Route und in jedem Anwendungsfall; die UI blendet nicht erlaubte Aktionen aus, ersetzt aber keine Prüfung. | MUSS |
| SEC-005 | Eingabevalidierung an der Grenze (Länge, Format, erlaubte Werte) für alle Schreiboperationen; parametrisierte Queries; kein String-Concatenation in SQL. | MUSS |
| SEC-006 | Prompt-Inhalt ist **nicht vertrauenswürdig**: Er wird nie als HTML/Markdown gerendert (XSS), nie interpretiert, nie in Logs geschrieben, nie in Systemprompts des Prüfers ungeschützt eingesetzt, und URLs darin werden nie abgerufen (SSRF). Eine Übermittlung an den KI-Dienst erfolgt ausschließlich an den verifizierten Dienst nach FR-038/FR-065 und wird nach FR-074 protokolliert. | MUSS |
| SEC-007 | Der Prüfer ist gegen Prompt-Injection **seiner Prüflinge** gehärtet: Der zu prüfende Inhalt wird als zu klassifizierender Datenblock übergeben, nicht als Anweisung; die Systemanweisung des Prüfers dominiert. | MUSS |
| SEC-008 | Rate-Limiting: Anmeldung (Default 10 Versuche/Min/IP, mit Sperre), API (Default 1 000/Min/Nutzer). | MUSS |
| SEC-009 | Sicherheits-Header (HSTS, `X-Content-Type-Options`, `X-Frame-Options: DENY`, restriktive CSP), TLS 1.2+ durchgehend, keine internen Fehlerdetails in Antworten. | MUSS |
| SEC-010 | Audit-Protokoll für sicherheitsrelevante Aktionen: Login/Logout, Rollen- und Mitgliedänderungen, Prompt-Löschung, Waiver/Freigabe, manuelle Prüfwiederholung. Ohne Inhalte, mit Actor und Zeitpunkt. | MUSS |
| SEC-011 | Abhängigkeits- und Container-Scanning in CI; keine ungepatchten Criticals. | MUSS |
| SEC-012 | Verschlüsselung ruhender Daten über den Datenbank-/Storage-Dienst; sensible Felder (z. B. Refresh-Token) werden gehasht gespeichert. | MUSS |

---

## 11. Datenschutz-Anforderungen (DSGVO)

| ID | Anforderung | Priorität |
|---|---|---|
| DP-001 | Rechtsgrundlage und Zweck des Scans sind dokumentiert (berechtigtes Interesse / interne Compliance-Pflicht) und im Datenschutzhinweis benannt. | MUSS |
| DP-002 | **Keine Redaktion vor der Übermittlung** (Entscheidung F1). Datenminimierung wird stattdessen umgesetzt über: (a) Löschung der KI-Rohpayloads nach 24 h (FR-061), (b) die Provider-Auflagen aus FR-065 (kein Training, keine dauerhafte Speicherung, EU-Region, Auftragsverarbeitungsvertrag), (c) Metadaten-only-Audit jeder Übermittlung (FR-074) und (d) das Verbot dauerhafter Speicherung von Roh-Inhalten im Plattform-Log (FR-062). | MUSS |
| DP-003 | Löschfristen: Logs/Rohtelemetrie ≤ 90 Tage, KI-Rohpayloads ≤ 24 h, Prompts/Versionen bis zur expliziten Löschung (Annahme **A-03**). | MUSS |
| DP-004 | Recht auf Löschung ist technisch über einen auslösbaren Vorgang umgesetzt (Nutzerkonto, Prompt, Audit ohne Inhalt). | MUSS |
| DP-005 | Kein Datenabfluss aus der EU: Hosting in europe-west3. Der KI-Dienst wird ausschließlich mit einem **Vertrag zur Auftragsverarbeitung (AVV)** und einer vertraglichen Zusage betrieben, dass eingehende Inhalte **nicht zum Training** verwendet und **nicht dauerhaft gespeichert** werden. Die Plattform verifiziert diese Konfiguration vor dem ersten Aufruf und bricht den Scan ab, wenn die Zusage fehlt (FR-038, FR-065, US-022). | MUSS |
| DP-006 | Datenschutzhinweis DE/EN, verlinkt und versioniert. | MUSS |
| DP-007 | Rechte der Betroffenen (Auskunft, Berichtigung, Löschung, Widerspruch) sind organisatorisch geregelt und in der Anwendung nachweisbar unterstützt (Löschung, Berichtigung, Export der eigenen Daten). | MUSS |
| DP-008 | Der Export der eigenen personenbezogenen Daten (Maschinenlesbar) ist möglich. | SOLL |

---

## 12. Technische Randbedingungen

| ID | Randbedingung | Herkunft |
|---|---|---|
| CON-001 | **Backend auf JVM.** Der Kunde nennt Langchain4j — eine Java-Bibliothek. Damit ist das Backend Spring Boot (Java 21+); die Stack-Vorgabe „FastAPI **oder** Spring Boot" aus `AGENTS.md` wird dadurch aufgelöst. | Kunden-Mail + `.agent/knowledge/stack.md` |
| CON-002 | **Frontend React** (19, TypeScript strict). SSR ist kein Anforderungstreiber (interne Tool-Applikation, kein SEO). | Kunden-Mail „React" |
| CON-003 | Datenbank PostgreSQL 16, `organisation_id`-Scoping, Row-Level-Security als zweite Verteidigungslinie. | `AGENTS.md` + SEC-002 |
| CON-004 | KI-Zugriff **ausschließlich** über Langchain4j (Chat-/Embedding-Modelle), damit der Anbieter konfigurierbar bleibt (Gemini 2.0 Flash / OpenAI, EU). | Kunden-Mail + `AGENTS.md` |
| CON-005 | Alle Geheimnisse (JWT-Schlüssel, Datenbankpasswort, SMTP, KI-API-Keys) ausschließlich aus der Umgebung; `.env` in `.gitignore`; Secret-Scan in CI. | `AGENTS.md` |
| CON-006 | Alle APIs folgen dem in Phase 2 zu erstellenden Vertrag `docs/api/openapi.yaml` (OAS 3.0+); dieses PRD definiert Verhalten, nicht Syntax. | `AGENTS.md` |
| CON-007 | Deployment: Docker → Cloud Run (europe-west3) oder Docker Compose lokal; GitHub Actions für CI/CD. | `AGENTS.md` |
| CON-008 | Coverage-Gate ≥ 80 % in CI (NFR-009) ist Bestandteil der Definition of Done. | Kunden-Mail |
| CON-009 | Der Prüflauf ist **nicht** transaktional an den HTTP-Request gekoppelt: Trennung über eine persistente Warteschlange (z. B. Datenbank-Queue) — keine In-Memory-Queue, damit Neustarts nichts verlieren. | FR-070 + NFR-007 |
| CON-010 | Mandantentrennung muss auch auf Infrastruktur-Ebene nachweisbar sein (DB-Nutzer pro Umgebung, keine Shared-Connections über Tenant-Grenzen). | SEC-002 |

---

## 13. Annahmen

| ID | Annahme | Begründung / Auswirkung bei Nichtbestätigung |
|---|---|---|
| A-01 | „Mandant" = Organisation (Abteilung). Ein Nutzer gehört genau **einer** Organisation. | **Bestätigt (F2).** Auswirkung: eine `organisation_id` je Nutzer, ein Tenant-Filter je Query. |
| A-02 | Die Security-Admin-Rolle ist **organisationsintern**; ein plattformweiter Security-Admin existiert im MVP nicht. | **Bestätigt (F3).** Betrifft SEC-002, FR-050, FR-053. |
| A-03 | Die 90-Tage-Löschung gilt für **Logs, Rohtelemetrie und Scan-Rohpayloads**, nicht für Prompts und Versionen. Diese bleiben bis zur expliziten Löschung. | **Bestätigt (F5).** Betrifft DP-003, FR-060, FR-061. |
| A-04 | Der Prüflauf darf eine Freigabe nicht ersetzen: ohne bestandenen Scan ist ein Prompt nicht nutzbar (außer `WAIVED` durch `SECURITY_ADMIN`). | **Bestätigt (F4).** Betrifft I-3, FR-039. |
| A-05 | Ein Prompt ist ein *Entwurf mit Prüfstatus*, kein ausführbares Artefakt. Nutzung erfolgt durch Kopieren/Export aus der Oberfläche. | **Bestätigt** (Scope-Entscheidung O1). |
| A-06 | Kategorien sind organisationsspezifisch; die Seed-Liste ist identisch in allen Organisationen. | **Bestätigt (F6).** |
| A-07 | Registrierung ist frei (Self-Service) und erzeugt eine neue Organisation; Beitritt zu bestehenden Organisationen ausschließlich per Einladung. | **Bestätigt.** Betrifft FR-002, US-002. |
| A-08 | Der Duplikatschwellwert ist organisationsweit konfigurierbar, Default 0,90. | **Bestätigt (F8).** |
| A-09 | Der KI-Anbieter ist EU-basiert, ohne Training und ohne dauerhafte Speicherung (Zero-Retention), und es besteht ein Vertrag zur Auftragsverarbeitung. **Bestätigt (F1).** | Betrifft FR-065, DP-005, A-09. |
| A-10 | Mengengerüst: 100 Organisationen, 10 000 Nutzer, 1 000 000 Prompts (NFR-008). | Wenn deutlich größer: Suche auf Volltextindex/Partitionierung umstellen (Phase 2). |
| A-11 | Der Kunde akzeptiert React + Spring Boot (Java) als Technologie — abgeleitet aus „React" und „Langchain4j". | **Bestätigt (F10).** |
| A-12 | **Prompt-Inhalte werden unredigiert an den KI-Dienst übermittelt** (F1). Das ist die einzige verbleibende Annahme mit **aktivem Restrisiko** (R-06) — alle übrigen Annahmen sind durch Entscheidungen ersetzt. | Betrifft FR-038, DP-002, R-06. |

---

## 14. Entscheidungen (Grill-Me Runde 1 — beantwortet)

Beantwortet am **2026-10-20** durch den Product Owner. Herkunft der Empfehlungen und die
vollständige Begründung: `grill-me-round1.md`. Maschinenlesbar: `open_decisions.json`.

| # | Frage | **Entscheidung** | Abweichung von der Empfehlung | Betroffene IDs |
|---|---|---|---|---|
| **F1** | Übermittlung an den externen KI-Dienst | **Unredigierte Übermittlung des Originalinhalts.** Keine Redaktion vor dem Aufruf. | **Ja — bewusste Abweichung** (Empfehlung war „redigiert"). Restrisiko R-06 akzeptiert. | FR-038, FR-065, FR-074, DP-002, DP-005, SEC-006 |
| **F2** | Mandantenmodell | **1 Organisation = 1 Abteilung**, 1 Nutzer : 1 Organisation | nein | A-01, CON-003, SEC-002 |
| **F3** | Geltungsbereich Security-Admin | **Organisationsintern** (+ `ORG_ADMIN` als Empfänger-Fallback) | nein | A-02, FR-050, FR-053, SEC-002 |
| **F4** | Nutzung ungeprüfter Prompts | **Nein** — nur `APPROVED`/`WAIVED` sind nutzbar; `SECURITY_ADMIN` kann begründet freigeben | nein | I-3, A-04, FR-039 |
| **F5** | Geltungsbereich 90-Tage-Löschung | **Nur Logs, Rohtelemetrie und KI-Rohpayloads** (letztere 24 h); Prompt-Inhalte bis zur expliziten Löschung | nein | A-03, DP-003, FR-060, FR-061 |
| **F6** | Pflegerechte Kategorien | **Nur `ORG_ADMIN`**, Seed-Liste je Organisation | nein | A-06, FR-014 |
| **F7** | SSO/OIDC und MFA im MVP | **Nein für den MVP**; MFA für `SECURITY_ADMIN`/`ORG_ADMIN` als Nachrüstung, Einbaupunkt wird in Phase 2 per ADR festgeschrieben | nein | O6, SEC-001 |
| **F8** | Duplikaterkennung | **Vektor-Ähnlichkeit ≥ 0,90** plus normalisierter Textvergleich, je Organisation konfigurierbar, **nie blockierend** | nein | A-08, FR-032 |
| **F9** | 80-%-Abdeckung | **Harter Blocker** in CI (Line Coverage, Backend + Frontend) | nein | NFR-009, FR-073, CON-008 |
| **F10** | Technologie-Framework | **React 19 + Spring Boot 3 / Java 21 + PostgreSQL 16** | nein | A-11, CON-001, CON-002, CON-004 |

**Konsequenz aus F1 (verbindlich für Phase 2):**

1. Die Redaktionsstufe entfällt als Komponente. FR-038/DP-002/US-022 wurden entsprechend umgeschrieben.
2. **Pflichtmaßnahmen**, ohne die die Übermittlung nicht zulässig ist: Vertrag zur
   Auftragsverarbeitung (AVV) mit dem KI-Anbieter, EU-Region, Trainingsverbot, Löschgarantie,
   Provider-Konfigurationsprüfung vor dem ersten Aufruf (bei Verstoß: Scan bricht ab, kein
   Inhalt verlässt das System) und Metadaten-Audit jeder Übermittlung (FR-074).
3. Der lokale deterministische Vorprüfschritt (FR-031, Stufe 1) bleibt **zwingend** — er liefert die
   Secret-/PII-Fundstellen und ist die Grundlage für `FLAGGED`.
4. Das Restrisiko ist als **R-06** dokumentiert und in der Datenschutz-Folgenabschätzung zu führen.

---

## 15. Zielkonflikte & Auflösungen

| # | Konflikt | Auflösung |
|---|---|---|
| K-1 | „Asynchron, UI nicht ausbremsen" ↔ „sicher, nur geprüfte Prompts nutzen" | Speichern ist sofort und blockiert nie; die **Freigabe** (nicht das Speichern) ist das Gate. I-3 + F4. |
| K-2 | „Mandantentrennung unter keinen Umständen" ↔ „Duplikatprüfung über die Plattform" | Der Duplikatvergleich ist **orgsgebunden**; es existiert kein globaler Prompt-Index. FR-032 + I-2. |
| K-3 | „90 Tage löschen" ↔ „zentrales Register mit Verlauf" | Trennung der Aufbewahrungsobjekte: Telemetrie 90 Tage, Inhalte bis zur Löschung (A-03, F5). |
| K-4 | „Warn-E-Mail bei Risiko" ↔ „DSGVO / keine Secrets in E-Mails" | E-Mail enthält **nur Metadaten + Deep-Link**, nie Inhalt oder Fundstellen. FR-051. |
| K-5 | „LLM-Prüfung" ↔ „Datenminimierung" | **Bewusst zugunsten der Erkennungsqualität entschieden (F1):** Übermittlung **unredigiert**. Datenminimierung wird über Löschfristen (24 h Rohpayloads), Provider-Auflagen (EU, kein Training, Löschgarantie, AVV) und Metadaten-Audit umgesetzt. **Restrisiko R-06 — vom Product Owner akzeptiert.** |
| K-6 | „Ausfallsicherheit" ↔ „Fail-safe: verdächtige Prompts nie nutzbar" | Technischer Fehler ⇒ `FAILED` (blockiert), aber **reproduzierbar** und sichtbar; kein Absturz, kein Datenverlust. FR-070/071, NFR-007. |
| K-7 | „Langchain4j" ↔ Framework-Stack „FastAPI oder Spring Boot" | Langchain4j ⇒ JVM ⇒ Spring Boot (CON-001). Der Stack ist damit entschieden, nicht offen. |
| K-8 | „80 % Coverage" ↔ „asynchrone KI-Prüfung ist schwer testbar" | KI-Antworten werden über ein Adapter-Interface hinterlegt (Stub/Fake im Test); der 80-%-Nachweis gilt für die Orchestrierungslogik, nicht für das Modell. NFR-009. |
| K-9 | „Realtime-Status in der UI" ↔ „stateless Cloud Run" | SSE (oder Polling-Fallback) läuft serverloskompatibel; der Transport ist Phase-2-Entscheidung, das Verhalten (FR-040) steht fest. |

---

## 16. Risiken (Top 5 → `artifacts/risk-register.md`)

| ID | Risiko | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-01 | Mandantenleck (ein Fehler in einem Query ohne Tenant-Filter) | mittel | **kritisch** | RLS als zweite Verteidigungslinie, Pflicht-Integrationstest „Cross-Tenant → keine Daten", SEC-002, Review-Checkliste |
| R-02 | False-Positives bei Secret-/PII-Erkennung → Nutzer umgehen das System | hoch | hoch | Konfigurierbare Schwelle, `WAIVED`-Pfad mit Begründung, Feedback der Nutzer, false-positive-Quote als KPI |
| R-03 | KI-Dienst langsam/ausgefallen ⇒ Prüfungen stauen ⇒ Registry blockiert | mittel | hoch | Persistente Warteschlange, Backoff, DLQ, degraded mode (Anlegen bleibt möglich), `FAILED` manuell wiederholbar |
| R-04 | Prompt-Injection im Prüfling manipuliert den Prüfer (Prüfer kompromittiert) | mittel | **kritisch** | Redaktion, strikte Trennung Datenblock/Systemanweisung, kein Werkzeugaufruf aus Prüflinginhalt, SEC-007 |
| R-05 | Fehlende Akzeptanz bei Security-Teams (zu viele Fehlalarme ⇒ Alarm wird ignoriert) | mittel | hoch | Schwellen pro Organisation, Priorisierung der Kategorien, KPI K7, Kalibrierung auf Basis realer Funde |
| R-06 | **Unredigierte Übermittlung (F1):** erkannte Secrets und personenbezogene Daten verlassen das System und gelangen zum KI-Dienst. | **hoch** (bei secret-haltigen Prompts) | **hoch** | **Akzeptiertes Restrisiko** (Entscheidung F1, Product Owner). Pflichtmaßnahmen: AVV mit EU-Anbieter, Trainingsverbot, Löschgarantie, Provider-Konfigurationsprüfung mit Abbruch bei Verstoß (FR-038), Metadaten-Audit jeder Übermittlung (FR-074), lokale Vorprüfung bleibt Pflicht (FR-031), Aufnahme in die Datenschutz-Folgenabschätzung. |

---

## 17. Definition of Done (MVP)

1. Alle `MUSS`-Anforderungen FR/NFR/SEC/DP implementiert und durch Story-ACs abgedeckt.
2. Traceability-Matrix lückenlos: jede Anforderung → Story → AC → Gherkin-Szenario → Test.
3. Testabdeckung ≥ 80 % (Backend + Frontend), Coverage-Gate in CI grün (F9 bestätigt).
4. Mandantentrennungstests inkl. Negativfällen (403/404 ohne Datenleck) grün.
5. Security-Scan (Phase 8): 0 Critical, 0 High — inkl. Secret-Scan, Dependency-Scan,
   Container-Scan und Security-Header-Prüfung (US-034).
6. Retention-Job nachweisbar idempotent und 90-Tage-Regel greift (Test).
7. Degraded Mode nachgewiesen: KI-Dienst aus → Anlegen funktioniert, kein Absturz, kein Datenverlust.
8. Barrierefreiheits-Nachweis (US-035): Tastaturbedienung, Screenreader-Beschriftung, Kontrast.
9. **Provider-Nachweis (F1):** Auftragsverarbeitungsvertrag mit dem KI-Anbieter liegt vor; die
   Konfiguration ist EU-gebunden, mit Trainingsverbot und Löschgarantie; die
   Konfigurationsprüfung bricht bei Verstoß ab (US-022); jede Übermittlung ist im
   Metadaten-Audit nachweisbar (FR-074).
10. `docs/api/openapi.yaml` deckt alle Stories ab; alle Endpunkte mandantenbeschränkt.

---

## 18. Übergabe an Phase 2 (Architektur)

Phase 2 erzeugt: `docs/api/openapi.yaml`, `docs/architecture/domain_model.md`,
`docs/architecture/diagrams/*.mmd`, `docs/adr/ADR-*.md`, `.agent/knowledge/schema.sql`.

**Muss die Architektur aus diesem PRD abbilden:** alle Entitäten aus [§7.1](#71-entitäten), das
Statusmodell und die Invarianten I-1…I-3 aus [§7.2](#72-statusmodell-kern-der-fachlogik), die
Mandanten-Filterpflicht auf jeder Route (SEC-002), die persistente Prüf-Warteschlange (CON-009),
den Redaktionsschritt vor dem externen LLM (FR-038) und die Trennung von Scan-Status und
Prompt-Inhalt in allen Fehlermeldungen.

**Empfohlene ADRs:** ADR-001 Stack (Spring Boot 3 / Java 21 + React 19 / PostgreSQL 16),
ADR-002 Mandantenmodell & RLS, ADR-003 Statusmodell & Freigabe-Gate, ADR-004 Retention vs.
Registry-Zweck (A-03), ADR-005 Persistente Job-Queue (CON-009), ADR-006 Umgang mit KI-Anbieter &
Redaktion (F1), ADR-007 Duplikatverfahren (F8), ADR-008 Live-Transport (SSE vs. Polling).

---

## 19. Artefakte dieser Phase

| Artefakt | Pfad |
|---|---|
| PRD (dieses Dokument) | `docs/requirements/PRD.md` |
| User Stories (JSON) | `docs/requirements/stories/US-*.json` (35) |
| BDD-Features (Gherkin) | `docs/requirements/features/*.feature` (35) |
| Traceability Matrix | `docs/requirements/traceability_matrix.json` |
| Anforderungen kompakt | `docs/requirements/artifacts/requirements.md` |
| User Stories lesbar | `docs/requirements/artifacts/user-stories.md` |
| Risiko-Register | `docs/requirements/artifacts/risk-register.md` |
| Grill-Me Runde 1 (F1–F10) | `docs/requirements/grill-me-round1.md` |
| Offene Entscheidungen (maschinenlesbar) | `docs/requirements/open_decisions.json` |
| Generator (Single Source of Truth für Stories/Features/Matrix) | `docs/requirements/tools/generate_artifacts.py` |
| Phasen-Zusammenfassung | `docs/requirements/PHASE1_SUMMARY.md` |
