# ADR-002 — Mandantenmodell und Autorisierung: 1 Organisation = 1 Mandant, RLS als zweite Verteidigungslinie

| Feld | Wert |
|---|---|
| **ID** | ADR-002 |
| **Titel** | Mandantenmodell und Autorisierung |
| **Status** | **Angenommen** |
| **Datum** | 2026-10-20 |
| **Entscheider** | Architecture Orchestrator (`aisdlc-architecture`), bestätigt durch F2/F3 |
| **Betrifft** | FR-003, FR-004, FR-009, FR-023, SEC-002, SEC-003, SEC-004, CON-003, CON-010, F2, F3, I-2, R-01 |
| **Supersediert** | — |

## Kontext

**R-01 (Mandantenleck)** ist als kritisches Risiko eingestuft: ein einziger Query oder
Hintergrundjob ohne `organisation_id`-Filter gibt Daten einer fremden Abteilung preis. Die
Anforderungen fordern die Trennung auf **jeder** Route — Authentifizierung, Autorisierung,
Mandantenfilter, Hintergrundjobs, Suche, Export, Cache, Logs, Metriken (FR-009).

Entscheidungen aus Schritt 1, die hier festgeschrieben werden:

* **F2** — 1 Organisation = 1 Abteilung = 1 Mandant; ein Nutzer gehört **genau einer**
  Organisation (A-01). Es gibt keine Mehrfachmitgliedschaften und keine organisations-
  übergreifende Rollen.
* **F3** — der Security-Admin ist **organisationsintern**. Es existiert **kein** globaler
  Security-Admin über Organisationen hinweg; der Plattform-Betrieb hat **keinen** fachlichen
  Zugriff (Out of Scope O7).

Damit ist das Mandantenmodell einfach — und genau deshalb gefährlich: Ein einziger
vergessener Filter reicht für ein Leck. Der Vertrag darf diese Trennung nicht implizit
lassen, sondern muss sie **sichtbar** machen.

## Entscheidung

### 1. Mandantengrenze ist der Token

Der `organisation_id` ist ein **Claim des Access-Tokens** (FR-001). Er ist die einzige
Autoritätsquelle für den Mandanten — es gibt keinen Org-Parameter im Request, den ein
Client frei setzen könnte. `organisation_id` wird **nie** aus dem Body oder Query gelesen.

### 2. Drei Ebenen, jede muss bestehen

| Ebene | Maßnahme | Wirkt gegen |
|---|---|---|
| **A — Identität** | JWT-Verifikation, `exp`, Signaturprüfung, Session-Kette (Refresh-Rotation) | Fälschung, Wiederverwendung |
| **B — Rolle** | Serverseitige Rollenprüfung je Operation (`DEVELOPER` / `SECURITY_ADMIN` / `ORG_ADMIN`) | Berechtigungsüberschreitung (SEC-004) |
| **C — Mandant** | `organisation_id`-Filter auf **jeder** Query + PostgreSQL RLS | vergessene Filter (R-01) |

Die UI blendet nicht erlaubte Aktionen aus, **ersetzt aber keine** der drei Prüfungen
(SEC-004). `403` entsteht nur bei fehlender Rolle, niemals bei fehlendem Mandanten.

### 3. Fünf Ebenen statt einer (C ist mehrstufig)

1. **Repository-Ebene** — jede abfragende Methode nimmt `organisationId` als **Pflicht-
   parameter**. Es gibt keine Repository-Methode ohne Mandantenparameter. Die Signatur macht
   den Fehler beim Kompilieren sichtbar.
2. **SQL-Ebene** — der Filter entsteht über eine Specification, die implizit immer den
   aktuellen Mandantenkontext einsetzt. Freie SQL-Fragmente mit String-Konkatenation sind
   verboten (SEC-005).
3. **RLS-Ebene** — jede mandantenbehaftete Tabelle hat eine Policy
   `USING (organisation_id = current_setting('app.organisation_id'))`. Der Wert wird je
   Transaktion über `SET LOCAL app.organisation_id = :orgId` gesetzt. **Ein Query, der den
   Filter vergisst, liefert deshalb eine leere Menge statt fremder Daten.** Das ist die
   zweite Verteidigungslinie (SEC-002).
4. **Cache-/Such-Ebene** — Cache-Keys enthalten die `organisation_id`; der Volltextindex
   trägt sie als Spalte und wird über den Tenant-Filter abgefragt.
5. **Hintergrundjob-Ebene** — jeder Job führt die `organisation_id` im Kontext mit. Ein Job
   ohne auflösbaren Tenant-Kontext wird **verworfen und protokolliert**, nicht geraten
   (SEC-003).

### 4. Antworten: 404 statt 403 bei fehlendem Mandanten

Ressourcen einer fremden Organisation sind **nicht existent**:

| Fall | Status | Begründung |
|---|---|---|
| Rolle fehlt, Ressource existiert in der eigenen Org | `403` | Berechtigungsfrage |
| Ressource existiert in einer fremden Org | `404` | Keine Existenzinformation (FR-023) |
| Ressource existiert nirgends | `404` | wie oben |
| `size` > 200, unzulässiger Query-Parameter | `400` | Formatfrage (AC-015-3) |

**Dokumentierte Ausnahme:** `DELETE /api/v1/users/{userId}` und
`GET /api/v1/users/{userId}/export` antworten bei einem Nutzer einer **fremden** Organisation
mit **`403`**, weil AC-029-2 das so fordert. Damit wird die Existenz einer User-ID schwach
offengelegt. Abhilfe: User-IDs sind UUIDv4 und damit nicht erratbar; die Ausnahme ist
bewusst dokumentiert (`.agent/artifacts/api-review.md`, Befund B-02) und wird in Phase 8
geprüft.

### 5. Sichtbarkeit im Vertrag

Der Vertrag macht die Trennung an zwei Stellen überprüfbar:

* je Operation die Extension `x-tenant-scope` (`organisation_id` bzw. `none`),
* je Ressource das Feld `organisationId`.

Zusätzlich ist in jeder Operationsbeschreibung ein **Mandant:**-Absatz mit dem konkreten
Sachverhalt formuliert (z. B. „ein Prompt einer fremden Organisation ist nicht existent →
`404`").

### 6. Infrastruktur (CON-010)

* Ein DB-Benutzer je Umgebung; keine Connection-Pools, die Tenant-Grenzen überschreiten.
* Der Tenant-Kontext wird **pro Transaktion** gesetzt, nicht pro Verbindung — sonst kann ein
  Folge-Request den Wert der vorigen Transaktion erben.
* Der Connection-Pool setzt den Kontext bei `checkout` zurück (`SET LOCAL app.organisation_id = ''`),
  damit ein RLS-Filter nie leer ausfällt und stillschweigend „alles sichtbar" ergibt.

## Konsequenzen

### Positiv

* Ein vergessener Filter führt zu **leeren Ergebnissen**, nicht zu einem Leck — RLS fängt
  den Fehler ab, bevor er Schaden anrichtet.
* Die `404`-Regel verhindert die Nutzung von `403`/`404` als Existenzorakel
  (Enumeration via Zeit- oder Statusunterschiede).
* Der Token als Mandantengrenze schließt Parameter-Missbrauch aus.
* Die Pflicht-Mandantenparameter in der Repository-Signatur machen den Fehler zur
  Compile-Zeit sichtbar.

### Negativ / Kosten

* **Performance:** RLS plus Anwendungsfilter bedeutet doppelte Filterung. Bei 1 000 000
  Prompts (A-10) ist ein Partial-Index je `organisation_id` je Sortierfeld nötig
  (`(organisation_id, updated_at DESC)` usw.).
* **RLS-Overhead:** RLS-Prüfung bei jeder Zeile. Bei Mengengerüst und Indexierung
  vertretbar; im Lasttest nachzumessen.
* **Verbindungs-Pool:** Der Reset des Tenant-Kontexts beim `checkout` ist eine Fehlerquelle
  und braucht einen eigenen Test („Connection aus Pool, Mandant A, dann Mandant B").
* **Ausnahme 403 bei User-Löschung:** Ein Restbestand an Existenzinformation. Dokumentiert
  und bewusst.

### Neutral

* Ein **globaler** Plattform-Admin über alle Organisationen bleibt ausgeschlossen (O7) und
  ist auch in Phase 2+ nicht vorgesehen. Der Plattform-Betrieb arbeitet ausschließlich auf
  Betriebsebene (Health, Metriken, Backups, Provider-Konfiguration) — ohne fachlichen
  Prompt-Zugriff.

## Alternativen, die verworfen wurden

| Alternative | Warum verworfen |
|---|---|
| Mandant als Parameter (`?organisationId=`) | Erlaubt Eskalation durch einen Client mit einem gültigen Token. |
| Schema pro Mandant | Nicht mit 100 Organisationen und 1 000 000 Prompts vereinbar (A-10), Betriebsaufwand ohne Gegenwert. |
| Ein globaler Security-Admin (F3 verworfen) | Widerspricht P1 und O7; vergrößert die Angriffsfläche erheblich. |
| Nur Anwendungsfilter, ohne RLS | R-01 ist als kritisch eingestuft — eine einzige Verteidigungslinie ist zu dünn. |
| `403` bei Cross-Tenant (einheitlich) | Wird zum Existenzorakel; AC-005-2 fordert `404` ausdrücklich. |

## Verweise

* `docs/requirements/PRD.md` §8 (FR-003, FR-004, FR-009, FR-023), §10 (SEC-002…SEC-004), §16 (R-01)
* `docs/architecture/diagrams/08-tenant-isolation.mmd`
* `docs/api/openapi.yaml` — `x-tenant-scope`, `organisationId`, `404`-Regel
* `.agent/artifacts/api-review.md` — Befund B-02 (403-Ausnahme bei User-Löschung)
