# PromptGuard — Observability

| Feld | Wert |
|---|---|
| **Dokument** | Observability: Health, strukturierte Logs, Metriken, Traces |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Bezug** | US-032, FR-072, NFR-014, FR-062, FR-074, AC-032-1…AC-032-3 |

---

## 1. Grundsatz

> **Betrieb muss möglich sein, ohne je Nutzerdaten zu lesen.**

Observability ist bei PromptGuard kein Komfort-Feature, sondern **Voraussetzung** für
Compliance: Mandantentrennung (R-01), Nachweisführung nach Art. 30 DSGVO (FR-074) und der
Nachweis, dass **niemals** Inhalt im Log landet (FR-062, AC-032-2) sind nur prüfbar, wenn
es eine technische Beobachtung gibt.

| Prinzip | Umsetzung |
|---|---|
| **Inhaltsfreiheit** | Keine Logzeile enthält Prompt-Inhalt, Secrets, Tokens oder personenbezogene Daten |
| **Korrelation** | Jede Zeile trägt eine `correlationId`, die Anfrage, Job, Provider-Aufruf, Audit und Fehlerantwort verbindet |
| **Aggregiert** | Metriken sind organisationsübergreifend und tragen **keinen** Mandantenbezug |
| **Einheitlich** | JSON-Format, strukturiert, kein Freitext |

---

## 2. Health

| Endpunkt | Zweck | Codes |
|---|---|---|
| `GET /api/v1/health` | Gesamtstatus mit Komponentenstatus | `200` (inkl. `DEGRADED`), `503` (nur bei `DOWN`) |

**Komponenten:**

| Komponente | `UP` | `DEGRADED` | `DOWN` |
|---|---|---|---|
| `database` | Verbindung + triviale Query in < 1 s | Antwortzeit erhöht | keine Verbindung |
| `scanQueue` | Abfrage der Warteschlange möglich | Tiefe > 1 000 oder ältester Job > 15 min | Queue nicht erreichbar |
| `aiProvider` | Verifikation bestanden | Verifikation **nicht** bestanden (Konfiguration) oder Provider langsam | Provider nicht erreichbar |

> **Wichtig (NFR-006, NFR-007):** Ein KI-Ausfall führt zu `DEGRADED`, **nicht** zu `DOWN` —
> und niemals zu `503` der Kern-API. Die Registry bleibt benutzbar; nur der Scan steht still.

**Liveness vs. Readiness:** Liveness beantwortet „läuft der Prozess" (ohne Abhängigkeiten),
Readiness beantwortet „kann er Traffic annehmen" (Datenbank erreichbar). Ein Provider-Ausfall
macht die Instanz **nicht** unready — sonst würde Cloud Run alle Instanzen aus dem
Load-Balancing nehmen und der Dienst wäre vollständig unbenutzbar.

---

## 3. Strukturierte Logs

### 3.1 Format

```json
{
  "timestamp": "2026-10-20T09:12:31.442Z",
  "level": "INFO",
  "service": "scan-worker",
  "event": "scan.completed",
  "correlationId": "01J8Z6M2QK9Y3F7V0C4T5N6P7R",
  "organisationId": "3f2b9c10-1111-4a1b-9f01-2c3d4e5f6071",
  "promptId": "6b2f4a11-2222-4a1b-8f01-2c3d4e5f6072",
  "promptVersionId": "9a1b2c3d-3333-4a1b-7f01-2c3d4e5f6073",
  "scanId": "1b2c3d4e-4444-4a1b-6f01-2c3d4e5f6074",
  "durationMs": 8421,
  "findingCount": 2,
  "duplicateWarningCount": 1,
  "status": "FLAGGED"
}
```

### 3.2 Erlaubte Felder

| Feld | Erlaubt | Begründung |
|---|---|---|
| `correlationId` | ja | Rückverfolgung ohne Inhalt |
| `organisationId` | ja | *nur* die UUID der **eigenen** Organisation; kein Name, keine E-Mail |
| `promptId`, `promptVersionId`, `scanId`, `notificationId` | ja | technische Fremdschlüssel |
| `event`, `level`, `service`, `timestamp` | ja | Betrieb |
| **Kennzahlen** | ja | Dauer, Anzahl, Warteschlangentiefe, Fehlerklasse |
| **Status** | ja | `APPROVED`, `FLAGGED`, … |
| **Fehlerklasse** | ja | z. B. `PROVIDER_TIMEOUT` — **kein** Stacktrace, **keine** Rohfehlermeldung mit URL oder Fragment |
| ~~Titel, Beschreibung, Inhalt~~ | **nein** | FR-062 |
| ~~Fundstellen-Ausschnitt~~ | **nein** | FR-062 — nur der maskierte Ausschnitt in der API, nie im Log |
| ~~E-Mail-Adresse, Name~~ | **nein** | PII |
| ~~Token, API-Key~~ | **nein** | SEC-012 |

### 3.3 Durchsetzung

1. **Zentrale Log-Konfiguration**, die keine beliebigen `toString()`-Werte durchreicht —
   nur die explizit benannten Felder werden geschrieben.
2. **Logger-API:** Fachlogik bekommt einen Logger, der **nur** einen Schlüssel-Wert-Satz
   entgegennimmt; freie Textnachrichten mit interpolierten Inhalten sind unzulässig
   (statische Analyse + Review-Checkliste).
3. **Automatischer Test (AC-032-2):** Ein Request mit Secret- und PII-haltigem Prompt wird
   ausgeführt; anschließend wird **jede** Logzeile des Requests geprüft — kein Prompt-Text,
   kein Secret, kein Kundenname, kein Fundstellen-Ausschnitt.
4. **Exception-Handler:** Unbehandelte Exceptions werden mit `correlationId` und **Fehlerklasse**
   protokolliert, nicht mit dem Stacktrace des Request-Kontexts; der Stacktrace landet in
   einem **getrennten**, geschützten Log mit demselben `correlationId`.

> **Hinweis zum Zweckkonflikt:** Der Stacktrace ist für die Fehlersuche wertvoll und enthält
> u. U. SQL-Fragmente. Er wird deshalb **getrennt** von den Anwendungslogs geführt und
> unterliegt derselben 90-Tage-Frist.

---

## 4. Metriken

`GET /api/v1/ops/metrics` → `MetricsSnapshot` (JSON, organisationsübergreifend).

| Metrik | Typ | Bedeutung | Alarmgrenze |
|---|---|---|---|
| `queueDepth` | Gauge | offene Jobs in der Warteschlange | > 1 000 |
| `oldestQueuedJobAgeSeconds` | Gauge | Alter des ältesten Jobs | > 900 s |
| `scanDurationMs.p50/p95/p99` | Histogramm | Enqueue → Urteil | p95 < 30 s, p99 < 120 s |
| `errorRate` | Gauge | Anteil fehlgeschlagener Prüfläufe im Fenster | > 5 % / 24 h |
| `deliveryRate` | Gauge | Anteil zugestellter Warn-E-Mails | < 95 % / 24 h |
| `statusDistribution` | Gauge | Verteilung der Scan-Status | `FLAGGED` > 25 % / 7 Tage |
| `httpRequests` (Counter) | Counter | Requests je Statuscode | 5xx-Spike |
| `httpLatency` (Histogram) | Histogramm | API-Latenz je Operation | p95 `POST /prompts` < 500 ms |
| `authFailures` (Counter) | Counter | fehlgeschlagene Logins | Rate-Limit greift |
| `providerVerification` | Gauge | `1` = verifiziert | `0` → sofort |
| `retentionLastRunStatus` | Gauge | letzter Retention-Lauf | `FAILED` → Alarm |
| `deadLetterCount` | Gauge | Jobs und Zustellungen in der Totmannschaltung | > 0 → Sichtung |

**Was **nicht** in Metriken steht:** Mandantenkennzahlen, Prompt-Titel, Inhalte, Fundstellen,
E-Mail-Adressen, Suchbegriffe. Aggregierte Zählwerte über alle Mandanten sind zulässig, weil
sie **keinen** Rückschluss auf einen einzelnen Mandanten erlauben. Kein Label mit
`organisationId` — das wäre ein Mandantenleck in der Telemetrie.

---

## 5. Traces

**Entscheidung:** Verteilte Traces (OpenTelemetry) werden **im MVP nicht** eingeführt.

| Argument | Bewertung |
|---|---|
| **Dafür** | einheitliche Sicht auf Request → Job → Provider; erleichtert die Analyse der 30-s-Latenz |
| **Dafür** | `correlationId` ist bereits der Ansatzpunkt |
| **Dagegen** | ein Tracer im Backend erzeugt **Metadaten über Promptinhalte**, wenn Spans ihn als Attribut tragen — zusätzliche Angriffsfläche für R-01/FR-062 |
| **Dagegen** | Betriebsaufwand (Collector, Exporter, Storage) ohne Nutzen für die MVP-Akzeptanzkriterien |
| **Dagegen** | `NFR-014` verlangt Health, strukturierte Logs und Metriken — **keine** Traces |

**Kompromiss (umgesetzt):**

* `correlationId` ist der **verbindende** Schlüssel über alle Komponenten — Request, Job,
  Provider-Aufruf, `transfer_audit`, Audit-Eintrag, Fehlerantwort, SSE-Ereignis.
* Der Provider-Aufruf wird über `transfer_audit` (Metadaten) nachvollziehbar — **ohne**
  Inhalt und **ohne** ableitbaren Fingerabdruck (kein Inhalts-Hash im Übermittlungsnachweis).
* Traces sind als **spätere Erweiterung** vorgesehen, mit der Auflage, dass **kein**
  Prompt-Inhalt als Attribut in einen Span darf.

---

## 6. Übermittlungsnachweis (FR-074, Art. 30 DSGVO)

Jede Übermittlung an den KI-Dienst erzeugt eine Zeile in `transfer_audit`:

| Feld | Inhalt | Ausdrücklich **nicht** |
|---|---|---|
| `transferredAt` | Zeitpunkt | |
| `provider` | Anbieter-Kennung | |
| `model` | Modell-Kennung | |
| `contentLength` | **nur die Zeichenzahl** | kein Inhalt, kein Ausschnitt, **kein Hash** (ableitbarer Fingerabdruck) |
| `resultId` | ID des Scan-Ergebnisses | |
| `correlationId` | Verknüpfung zu Log und Audit | |

Die Tabelle wird **nach 90 Tagen** gelöscht (ADR-004).

---

## 7. Alarme

| Alarm | Bedingung | Wirkung |
|---|---|---|
| Provider-Verifikation fehlgeschlagen | `ProviderVerification.verified = false` | **sofort** — kein Aufruf mehr möglich, Scan bricht ab |
| Queue staut | `queueDepth > 1000` **oder** ältester Job > 15 min | Skalierung/Provider-Limit prüfen (R-03) |
| Fehlerquote hoch | `errorRate > 5 %` über 24 h | Provider-/Queue-Incident eröffnen |
| Zustellquote niedrig | `deliveryRate < 95 %` über 24 h | SMTP-Routing und Totmannschaltung prüfen (R-05) |
| Retention fehlgeschlagen | letzter Lauf `FAILED` | Lauf wiederholen, Ursache prüfen |
| Totmannschaltung nicht leer | `deadLetterCount > 0` | tägliche Sichtung (SOP) |
| Anomaler Login | `authFailures` über Schwelle | Sperre/Kontoprüfung (SEC-008) |
| **Kritisch** | `FLAGGED`-Anteil > 25 % über 7 Tage | Schwellen- und Detektor-Kalibrierung prüfen (R-02) |

---

## 8. Zugriff auf die Betriebsebene

| Rolle | Health | Metriken | Audit der eigenen Org | Audit organisationsübergreifend | Prompt-Inhalt |
|---|---|---|---|---|---|
| `DEVELOPER` | nein | nein | nein | nein | nein |
| `SECURITY_ADMIN` | nein | nein | ja | nein | nein |
| `ORG_ADMIN` | nein | nein | ja | nein | nein |
| **Plattform-Betrieb** | ja | ja | **nein** | **nein** (nur Aggregate) | **nein** |

* Health und Metriken sind **ohne** fachlichen Zugriff und ohne Mandantenbezug.
* Der Betrieb bekommt **keinen** Einzelsicht-Export und **keine** Rohpayloads im
  fachlichen Vertrag (Befund B-10, ADR-006).
* Logs enthalten **keinen** Inhalt — deshalb kann der Betrieb sie ohne Mandantenbezug
  auswerten, ohne die Trennung zu verletzen.

---

## 9. Abdeckung der Akzeptanzkriterien

| AK | Erwartung | Umsetzung |
|---|---|---|
| AC-032-1 | Health **und** Metriken antworten mit `200` und melden Warteschlangentiefe, Scan-Dauer, Fehlerquote, Zustellquote, Statusverteilung; jede Logzeile trägt eine Korrelation-ID | `getHealth`, `getMetrics`, `MetricsSnapshot`, `correlationId` in jeder Logzeile |
| AC-032-2 | Keine Logzeile enthält Prompt-Text, Secret, Kundenname oder Fundstellen-Ausschnitt | Durchsetzung + automatischer Test (3.3) |
| AC-032-3 | Fehlerantwort enthält generischen Fehler **und** Korrelation-ID, **keinen** Stacktrace, kein internes Detail | ADR-009, `Problem` |

---

## 10. Verweise

* `docs/adr/ADR-004-storage-retention-and-privacy.md` — 90-Tage-Frist für Telemetrie
* `docs/adr/ADR-006-ai-provider-integration-and-verification.md` — Übermittlungsnachweis
* `docs/adr/ADR-009-error-model.md` — Fehlerformat mit `correlationId`
* `docs/architecture/degraded-mode.md` — `DEGRADED` vs. `DOWN`
* `docs/api/openapi.yaml` — `getHealth`, `getMetrics`, `HealthStatus`, `MetricsSnapshot`
* `docs/requirements/PRD.md` — FR-072, FR-074, NFR-014, US-032
