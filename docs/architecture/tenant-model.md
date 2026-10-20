# PromptGuard — Mandantentrennungs-Modell

| Feld | Wert |
|---|---|
| **Dokument** | Mandantentrennung (Invariante I-2) über alle Ebenen |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Bezug** | ADR-002 (verbindlich), R-01 (kritisches Risiko) |

> **Leitsatz (P1):** „Isolation vor Feature — Mandantentrennung ist keine Funktion, sondern
> eine Invariante. Kein Endpoint, kein Hintergrund-Job, kein Cache darf sie umgehen."

---

## 1. Mandantenmodell

* **1 Organisation = 1 Abteilung = 1 Mandant** (F2).
* **1 Nutzer : 1 Organisation.** Es gibt keine Mehrfachmitgliedschaften, keine
  organisationsübergreifenden Rollen (A-01, F2).
* **Der Security-Admin ist organisationsintern** (F3). Ein globaler Security-Admin über
  Organisationen hinweg existiert **nicht** — auch nicht in Phase 2+.
* **Der Plattform-Betrieb hat keinen fachlichen Datenzugriff** (O7). Er arbeitet auf
  Betriebsebene: Health, Metriken, Backups, Provider-Konfiguration, Warteschlangen-Tiefe.

---

## 2. Fünf Ebenen — jede muss bestehen

Die Trennung ist **mehrstufig** gebaut: Ein einzelner vergessener Filter darf nicht zum
Leck führen. Jede Ebene fängt einen Fehler der darunterliegenden ab.

| # | Ebene | Maßnahme | Schützt gegen |
|---|---|---|---|
| **1** | **Identität** | JWT-Prüfung: Signatur, `exp`, `sub`, `organisation_id`, `role`; Session-Kette bei Token-Rotation | Fälschung, Wiederverwendung eines rotierten Refresh-Tokens (AC-003-2) |
| **2** | **Rolle** | Serverseitige Rollenprüfung je Operation (`x-required-role` je Operation im Vertrag) | Berechtigungsüberschreitung (SEC-004, AC-004-2, AC-023-2) |
| **3** | **Query** | `organisation_id`-Filter auf **jeder** Query **plus** PostgreSQL RLS | vergessener Filter (R-01) |
| **4** | **Cache / Suche / Stream** | Cache-Key enthält den Mandanten; Volltextindex wird über den Tenant-Filter abgefragt; SSE liefert nur eigene Ereignisse | Leck über Sekundärstrukturen |
| **5** | **Hintergrundjob** | Jeder Job trägt `organisation_id` **im Datensatz**; ohne auflösbaren Kontext: verwerfen und protokollieren | Job ohne Tenant-Kontext (SEC-003) |

### 2.1 Ebene 1 — Identität

* **Der Mandant ist ein Token-Claim.** `organisation_id` wird **nie** aus Body, Query oder
  Header gelesen. Es gibt **keinen** Parameter, den ein Client frei setzen könnte.
* **Access-Token:** kurzlebig (Default 15 min), Claims `sub`, `organisation_id`, `role`,
  `exp`. Signaturschlüssel ausschließlich aus der Umgebung (FR-001, SEC-001).
* **Refresh-Token:** nur als `HttpOnly`/`SameSite`-Cookie; Rotation bei jeder Nutzung; bei
  Replay einer rotierten Kette wird die **gesamte Kette** invalidiert (FR-007, AC-003-2).
* **Passwort:** bcrypt/argon2id, Cost ≥ 12; das Klartextpasswort verlässt den
  Registrierungs-/Login-Fluss nie (FR-008).

### 2.2 Ebene 2 — Rolle

| Rolle | Rechte |
|---|---|
| `DEVELOPER` | Eigene Organisation: Prompts anlegen, lesen, bearbeiten, versionieren, suchen, löschen (eigene) |
| `SECURITY_ADMIN` | Zusätzlich: geflaggte Versionen prüfen und freigeben (Waiver), Warnungen empfangen, Audit lesen, Totmannschaltung wiederholen |
| `ORG_ADMIN` | Zusätzlich: Mitglieder und Rollen, Einladungen, Kategorien, Organisationseinstellungen, Alarmempfänger, Recht auf Löschung |

* Die Prüfung ist **serverseitig und in jedem Anwendungsfall** (SEC-004). Die UI blendet
  nicht erlaubte Aktionen aus, **ersetzt aber keine** Prüfung.
* `403` entsteht **nur** bei fehlender Rolle — **niemals** bei fehlendem Mandanten.

### 2.3 Ebene 3 — Query und RLS

**Drei Maßnahmen, alle drei verpflichtend:**

1. **Repository-Signatur:** Jede abfragende Methode nimmt `organisationId` als
   **Pflichtparameter**. Es existiert keine Repository-Methode ohne Mandantenparameter —
   der Fehler wird beim Kompilieren sichtbar.
2. **SQL-Ebene:** Der Filter entsteht über eine Specification, die implizit den aktuellen
   Mandantenkontext einsetzt. **Freie SQL-Fragmente mit String-Konkatenation sind
   verboten** (SEC-005).
3. **RLS:** Jede mandantenbehaftete Tabelle hat eine Policy
   `USING (organisation_id = current_setting('app.organisation_id', true)::uuid)`.
   Der Wert wird **pro Transaktion** gesetzt (`SET LOCAL app.organisation_id = :orgId`).

> **Konsequenz der RLS:** Ein Query, der den Filter vergisst, liefert eine **leere Menge**
> — niemals fremde Daten. Das ist die entscheidende Eigenschaft: Der Fehler wird *lautlos
> harmlos* statt *laut gefährlich*.

**Connection-Pool:** Beim `checkout` wird der Kontext zurückgesetzt
(`SET LOCAL app.organisation_id = ''`), damit ein Folge-Request **nicht** den Wert der
vorigen Transaktion erbt. Dieser Pfad braucht einen eigenen Test
(„Connection aus Pool: Mandant A, danach Mandant B").

### 2.4 Ebene 4 — Cache, Suche, Stream

| Struktur | Regel |
|---|---|
| Cache-Key | enthält `organisation_id` (`prompt:{orgId}:{promptId}`) |
| Volltextindex | `to_tsvector` über Titel/Beschreibung; die Abfrage filtert **zusätzlich** über `organisation_id` |
| Suchtreffer | `totalElements`, Facetten und Vorschauen beziehen sich **nur** auf die eigene Organisation |
| SSE-Kanal | filtert über `organisation_id` aus dem Token; ein Fremd-Prompt liefert `404` und eröffnet **keinen** Kanal |
| Metriken | aggregiert und **ohne** Mandantenbezug; keine Kennzahlen je Organisation, die Rückschlüsse auf Inhalt erlauben |

### 2.5 Ebene 5 — Hintergrundjobs

```
Job (scan_job)                    Worker
   organisation_id  ──────────▶  SET LOCAL app.organisation_id = job.organisation_id
   prompt_version_id                  │
   status, attempt_count              ▼
   available_at, locked_by        alle Queries des Laufs laufen innerhalb dieser Organisation
```

* Ein Job **ohne** auflösbaren Tenant-Kontext (gelöschter Mandant, Null-Wert) wird
  **verworfen und protokolliert** — er wird niemals „irgendwohin" ausgeführt (SEC-003).
* Der Job-Kontext wird **nicht** aus dem Payload abgeleitet, sondern aus dem Datensatz.

---

## 3. Antwortregel: `404` statt `403`

| Fall | Status | Begründung |
|---|---|---|
| Rolle fehlt, Ressource existiert in der **eigenen** Organisation | `403` | Berechtigungsfrage |
| Ressource existiert in einer **fremden** Organisation | `404` | Keine Existenzinformation (FR-023, AC-005-2) |
| Ressource existiert **nirgends** | `404` | wie oben |
| `size > 200`, unzulässiger Parameterwert | `400` | Formatfrage (AC-015-3) |

**Was eine `404` nie enthält:** Titel, Eigentümer, Prüfstatus, Anzahl, Vorschau, Fundstelle,
Zeitstempel oder irgendein Hinweis, dass es das Objekt gibt (AC-005-2, AC-021-2, AC-025-2).

**Dokumentierte Ausnahme:** `DELETE /api/v1/users/{userId}` und
`GET /api/v1/users/{userId}/export` antworten bei einem Nutzer einer **fremden**
Organisation mit **`403`**, weil AC-029-2 das so fordert. Damit ist die Existenz einer
User-ID schwach offengelegt. Abhilfe: User-IDs sind UUIDv4 und nicht erratbar. Die
Ausnahme ist bewusst dokumentiert (`.agent/artifacts/api-review.md`, Befund B-02) und wird
in Phase 8 (Security-Scan) geprüft.

---

## 4. Sichtbarkeit im Vertrag

Die Trennung ist **am Vertrag** prüfbar, nicht nur im Code:

| Sichtbarkeitspunkt | Konkretisierung |
|---|---|
| Je Operation | `x-tenant-scope: "organisation_id"` (bzw. `"none (…)"` mit Begründung) |
| Je Operation | `x-required-role: "DEVELOPER, SECURITY_ADMIN, ORG_ADMIN"` |
| Je Operation | ein **Mandant:**-Absatz in der Beschreibung, der den konkreten Fall benennt |
| Je Ressource | Feld `organisationId` (ADR-002) — nachgezogen für `Tag`, `StatusTransition`, `Waiver`, `PromptVersion` |
| Je Sammlung | `totalElements` bezieht sich auf die **gefilterte** Menge der eigenen Organisation |
| Story-Zuordnung | `x-stories` je Operation — maschinell geprüft |

**Prüfskript:** `docs/api/check_contract.py` bricht ab, wenn einer Operation
`x-tenant-scope` oder `x-required-role` fehlt.

---

## 5. Nachweis im Test

| Testart | Was sie belegt | Story |
|---|---|---|
| **Negativ-Integrationstest je Route** | Cross-Tenant-Zugriff liefert `404` bzw. `403` **ohne** Dateninhalt | US-005, US-031 (AC-031-3) |
| **RLS-Test ohne Anwendungsfilter** | Ein Query **ohne** `organisation_id`-Filter liefert eine leere Menge | SEC-002 |
| **Pool-Reset-Test** | Mandant B sieht nicht die Daten von Mandant A nach einem Pool-Checkout | ADR-002 |
| **Job-Kontext-Test** | Ein Job ohne auflösbaren Tenant-Kontext wird verworfen und protokolliert | SEC-003 |
| **Such-Isolationstest** | Ein Suchbegriff, der nur in einer fremden Organisation vorkommt, liefert `totalElements = 0` ohne Facetten | AC-005-3, AC-013-2 |
| **SSE-Isolationstest** | Ein Kanal zu einem fremden Prompt wird nicht eröffnet | US-024, ADR-008 |

> **Release-Stopp:** Jeder Fehlschlag eines Negativtests ist ein Release-Stopp — Root Cause
> vor Gate 2 (Risiko-Register, Frühwarnindikatoren).

---

## 6. Restrisiken

| Risiko | Bewertung | Gegenmaßnahme |
|---|---|---|
| **R-01** Mandantenleck durch einen vergessenen Filter | mittel / **kritisch** | Fünf Ebenen, davon RLS als zweite Verteidigungslinie; Pflicht-Negativtest je Route; Review-Checkliste |
| **403-Ausnahme** bei User-Löschung (B-02) | niedrig | UUIDv4-IDs; in Phase 8 prüfen, ob `404` mit AC-029-2 vereinbar ist |
| RLS-Overhead bei 1 000 000 Prompts | niedrig | Partial-Index je `organisation_id` je Sortierfeld; im Lasttest nachmessen |
| Pool-Reset als Fehlerquelle | mittel | eigener Test, Review-Checkliste |

---

## 7. Verweise

* `docs/adr/ADR-002-multi-tenancy-and-authorisation.md` — verbindliche Entscheidung
* `docs/architecture/diagrams/08-tenant-isolation.mmd` — Ablaufdiagramm der Trennung
* `docs/architecture/schema.sql` — RLS-Policies und Indizes
* `docs/api/openapi.yaml` — `x-tenant-scope`, `x-required-role`, `organisationId`
* `docs/api/check_contract.py` — maschinelle Prüfung
* `docs/requirements/PRD.md` — FR-009, FR-023, SEC-002, SEC-003, F2, F3, I-2, R-01
