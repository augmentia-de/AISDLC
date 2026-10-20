# PromptGuard — Notfall- und Recovery-Überlegungen

| Feld | Wert |
|---|---|
| **Dokument** | Betrieb, Notfall, Recovery, Backup, Runbook |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Bezug** | NFR-006, NFR-007, NFR-008, NFR-013, NFR-017, FR-070, FR-072, SEC-012, R-01…R-06 |

---

## 1. Ausfallwirkung (Failover) — was passiert, wenn was ausfällt

| Ausfall | Wirkung | Verhalten | Datenverlust? | Recovery |
|---|---|---|---|---|
| **KI-Dienst** | Prüfung steht still | `DEGRADED`; Anlegen/Lesen/Suchen normal; Jobs warten und werden geordnet nachgeholt | **nein** | Provider-Konfiguration prüfen, Verifikation erneut auslösen, Queue abfließen lassen |
| **SMTP** | Keine Warn-E-Mails | Zustellversuche `RETRYING` → `DEAD_LETTER`; **Prüfstatus unberührt** | **nein** | `POST /notifications/{id}/retry` |
| **PostgreSQL** | Gesamter Dienst nicht verfügbar | `500` mit generischem Fehler + Korrelations-ID; **kein** Teilschreiben (Transaktionen) | **nein** (Commit-Grenzen) | DB wiederherstellen; offene Jobs laufen nach Timeout in den Watchdog-Pfad |
| **API-Instanz** | einzelne Instanz fällt aus | Cloud Run startet eine neue; offene SSE-Verbindungen brechen ab → Client reconnectet und erhält den **Snapshot** | **nein** | automatisch; keine menschliche Aktion |
| **Scan-Worker** | Prüfung steht still | Jobs bleiben `QUEUED` (persistent); kein Job geht verloren | **nein** | Worker neu starten; `locked_at`-Timeout räumt hängende Jobs auf |
| **Notification-Worker** | keine Zustellung | Jobs warten persistent | **nein** | Worker neu starten |
| **Retention-Job** | zu viel Telemetrie | `RetentionRun.status = FAILED` + Alarm; **kein** Datenverlust, aber Fristverletzung | **nein** | Job erneut auslösen; Ursache prüfen |
| **CI/CD** | kein Release | Coverage-Gate ohne neuen Nachweis; laufende Version bleibt bestehen | **nein** | Pipeline wiederholen |

> **Grundsatz:** Kein Ausfall einer **nachgelagerten** Komponente (KI, SMTP, Worker) darf den
> **Kernpfad** (Anlegen, Lesen, Suchen, Versionieren) blockieren. Nur der Ausfall der
> Datenbank betrifft den Kernpfad — und dort schützt die Transaktionsgrenze vor
> Teilschreibzuständen.

---

## 2. Wiederherstellungsziele

| Ziel | Wert | Begründung |
|---|---|---|
| **RTO** (Wiederanlaufzeit) | < 60 min | interner Dienst; der Kernpfad ist ohne KI/SMTP bereits nutzbar |
| **RPO** (zulässiger Datenverlust) | **0** für Fachdaten | Transaktionale Kopplung von Version und Job; keine In-Memory-Zustände |
| **RPO** für Telemetrie | bis 24 h | Logs und Rohpayloads sind verzichtbar |
| **Verfügbarkeit Kern-API** | 99,9 % | NFR-006 |
| **Verfügbarkeit Prüfpfad** | so hoch wie der Provider | Abhängigkeit, nicht eigene Verfügbarkeit |

---

## 3. Backup und Restore

| Objekt | Verfahren | Frequenz | Aufbewahrung |
|---|---|---|---|
| **PostgreSQL-Volume** | verwaltete Snapshots (Cloud SQL) + point-in-time-recovery | täglich + WAL-Kontinuierung | 35 Tage |
| **Warteschlangen** | Teil des Datenbank-Snapshots | gemeinsam | gemeinsam |
| **Konfiguration** | versioniert im Repository (Compose, Flyway, ENV-Templates) | je Änderung | unbegrenzt (Git) |
| **Datenschutzhinweis** | versioniert im Repository **und** in `privacy_notice` | je Änderung | unbegrenzt |
| **KI-Rohpayloads** | **kein** Backup | — | 24 h, Löschung (FR-061) |
| **Logs** | **kein** Backup | — | 90 Tage, Löschung (FR-060) |
| **Secrets** | Secret-Manager (nicht im Backup) | — |_rotation_ |

> **Wichtig:** Rohpayloads und Logs werden **nicht** gesichert — sie sind bewusst kurzlebig
> und würden im Backup denselben Inhalt länger vorhalten, als die Frist vorsieht. Ein Restore
> enthält damit **keine** alten Inhalte, die die Löschfrist verletzen würden.

### 3.1 Restore-Szenarien

| Szenario | Vorgehen | Datenwirkung |
|---|---|---|
| **Tabelle beschädigt** | Point-in-time-Recovery auf den Stand vor dem Fehler | Promotionen, die nach dem Zeitpunkt erfolgten, sind verloren — der Audit-Trail zeigt die Lücke |
| **Falsche Löschung durch Migration** | Restore des Vorzustands + Korrektur der Migration | Fachdaten vollständig wiederhergestellt; die Audit-Einträge der Löschung bleiben sichtbar |
| **Vollständiger DB-Verlust** | Restore des letzten Snapshots; offene `scan_job`-Einträge werden nach Timeout neu eingeplant | Fachdaten bis zum Snapshot; Jobs ab Snapshot |
| **Fehlerhafte Migration** | Flyway-Reparatur (`repair`) nach Review | Daten bleiben |

**Nach jedem Restore:** `scan_job` auf `RUNNING` mit veraltetem `locked_at` prüfen
(Watchdog-Pfad), `queueDepth` beobachten, `retentionRun` nachziehen, Provider-Verifikation
prüfen.

---

## 4. Betriebliche Runbook-Schritte

### 4.1 KI-Dienst nicht erreichbar

1. `GET /api/v1/provider/verification` → `verified` und `violations` prüfen.
2. `GET /api/v1/health` → Komponente `aiProvider` = `DEGRADED`/`DOWN`.
3. `GET /api/v1/ops/metrics` → `queueDepth`, `oldestQueuedJobAgeSeconds`, `errorRate`.
4. Provider-Status und Kontingent prüfen (extern).
5. Nach Recovery: `POST /api/v1/provider/verification` → `verified: true`.
6. Queue beobachten, bis `queueDepth` gegen 0 geht.
7. Versionen mit `FAILED` manuell erneut auslösen (`POST /api/v1/prompt-versions/{id}/rescan`).
8. Nachweis im Betriebsprotokoll festhalten (Zeitpunkt, Dauer, betroffene Versionen).

### 4.2 Warteschlange wächst

1. `queueDepth` und Alter des ältesten Jobs prüfen.
2. Prüfen, ob der Provider das Bottleneck ist (Dauer der Läufe) oder die Worker (Anzahl
   aktiver Worker, `locked_by`).
3. Worker skalieren (horizontal — `SKIP LOCKED` erlaubt beliebig viele).
4. Bei Provider-Limit: Rückstau kontrolliert abbauen (Backoff greift), **kein** Burst.

### 4.3 Totmannschaltung (Dead Letter)

1. `GET /api/v1/notifications?status=DEAD_LETTER` → Zustellversuche.
2. `GET /api/v1/health` + Metriken → Ursache (SMTP vs. Provider).
3. Nach Behebung: `POST /api/v1/notifications/{id}/retry` je Eintrag.
4. Scan-Jobs in der Totmannschaltung: nur manuell (`rescanPromptVersion`) — kein Auto-Retry.

### 4.4 Retention-Fehler

1. `GET /api/v1/ops/retention` → letzter Lauf mit `status` und `error`.
2. Ursache (DB-Fehler, Sperre, Volumen) klären.
3. `POST /api/v1/ops/retention` → manueller Lauf.
4. Fristverletzung dokumentieren (Compliance-Meldung an die DPO).

### 4.5 Mandantenleck-Verdacht (R-01)

1. **Sofort:** betroffene Instanz bzw. Funktion einschränken (Feature-Flag/Deploy einer
   gepatchten Version).
2. Umfang bestimmen: welche Queries, welcher Zeitraum, welche Organisationen.
3. Nachweis: Logs (inhaltsfrei!) über `correlationId` und `organisationId` auswerten.
4. Rotation aller betroffenen Zugangsdaten (Passwörter, Refresh-Tokens, API-Keys).
5. DPO und Product Owner informieren; Meldepflicht nach Art. 33 prüfen.
6. **Release-Stopp**, bis die Ursache behoben und der Negativtest wieder grün ist.

---

## 5. Skalierung (NFR-008)

| Zielwert | Maßnahme |
|---|---|
| 100 Organisationen | Mandantenspalten auf `organisation_id`; Partial-Indizes je Sortierfeld |
| 10 000 Nutzer | keine Sonderbehandlung; Token-validierung ist stateless |
| 1 000 000 Prompts | Volltextindex mit `simple`-Konfiguration; `organisation_id`-Filter; Partial-Index auf `deleted_at IS NULL` |
| 5 000 Prüfläufe/min | Worker horizontal skalierbar (`SKIP LOCKED`), Batch-Inserts der Fundstellen, `LISTEN/NOTIFY` als Weckruf mit 1-s-Polling-Fallback |

**Engpass** ist in der Regel der **Provider**, nicht die Queue. Deshalb ist die Queue als
**Dämpfer** dimensioniert, nicht als Durchsatz-Optimierung.

---

## 6. Notfall-Kontakte und Eskalation

| Rolle | Zuständigkeit | Erreichbarkeit |
|---|---|---|
| Plattform-Betrieb | Health, Metriken, Deploy, Backup, Provider-Konfiguration | Betriebs-SOP |
| Fachbereich/Product Owner (Alex) | fachliche Entscheidungen, Priorisierung, Kommunikation | intern |
| Datenschutzbeauftragte:r | Auflagen, AVV, Löschanfragen, Meldepflicht | intern |
| Security-Admin (je Organisation) | Bewertung geflaggter Prompts, Waiver | organisationsintern (F3) |

**Eskalationsstufen**

| Stufe | Auslöser | Maßnahme |
|---|---|---|
| **1 — Betrieb** | Queue wächst, einzelne Fehler | Betriebs-SOP, keine Kommunikation nach außen |
| **2 — Team** | `DEGRADED` > 1 h, Totmannschaltung wächst, `errorRate` > 5 % | Team-Bewertung, ggf. Provider-Wechsel (konfigurierbar) |
| **3 — Leitung + DPO** | Mandantenleck-Verdacht, AVV-Zweifel, Provider-Konfigurationsverstoß | sofortige Bewertung, ggf. Betriebssperre der Übermittlung |

> **Kill-Switch für die Übermittlung:** Ist die Provider-Konfiguration zweifelhaft, wird sie
> so konfiguriert, dass die **Verifikation fehlschlägt**. Damit wird **kein** Inhalt
> übermittelt — die Prüfung fällt auf `FAILED` zurück, die Registry bleibt nutzbar. Das ist
> der wirksamste und einfachste Notfallschalter (ADR-006).

---

## 7. Übungen

| Übung | Frequenz | Ziel |
|---|---|---|
| **Provider-Ausfall simulieren** | quartalsweise | Kernpfad bleibt nutzbar; Queue läuft geordnet ab; Recovery in < 60 min |
| **Restore aus dem Backup** | halbjährlich | RTO/RPO einhalten; Restore- und Nachlaufzeit messen |
| **Totmannschaltung durchspielen** | quartalsweise | SOP ist verständlich, ohne Fachwissen |
| **Mandanten-Isolationstest** | bei **jedem** Release (CI) | kein Leck (R-01) |
| **Retention-Nachweis** | quartalsweise | Löschmengen plausibel, Fristen eingehalten |

---

## 8. Grenzen

| Grenze | Folge |
|---|---|
| **Kein Multi-AZ-Active-Active für die Datenbank** | ein Datenbank-Ausfall ist ein Vollausfall; RTO < 60 min über verwaltete Failover |
| **Kein Not-Aus für einzelne Mandanten** | ein einzelner „boeswilliger" Mandant kann die Queue füllen; Gegenmaßnahme ist Rate-Limiting und Warteschlangentiefe-Metrik |
| **Kein automatisches Provider-Failover** | der Provider ist konfigurierbar (ENV), ein Umschalten ist ein Deploy — bewusst manuell, damit kein ungeprüfter Provider Daten erhält |
| **Keine Wiederherstellung von Rohpayloads** | ein Provider-Debug nach > 24 h ist nicht möglich — bewusste Folge von FR-061 |

---

## 9. Verweise

* `docs/architecture/degraded-mode.md` — Verhalten ohne KI-Dienst
* `docs/architecture/error-retry-strategy.md` — Retry, Backoff, Totmannschaltung
* `docs/architecture/observability.md` — Health, Metriken, Alarme
* `docs/architecture/privacy-retention.md` — Backup- und Löschfristen
* `docs/adr/ADR-004-storage-retention-and-privacy.md`, `ADR-005`, `ADR-006`
* `docs/requirements/PRD.md` — NFR-006…NFR-008, NFR-013, NFR-017, FR-070, FR-072
