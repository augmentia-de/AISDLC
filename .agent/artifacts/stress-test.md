# Stresstest-Bericht — Schritt 4 (Grill-Me-Stresstest)

> Schritt 4 · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Skill: `aisdlc-grill-stress` · Eingang: Schritt 3, Commit `296662d`
> Gelesen: `docs/api/openapi.yaml` (Entwurf), `docs/architecture/*`, `docs/adr/*`,
> `docs/requirements/*`, `.agent/knowledge/schema.sql`, `.agent/artifacts/*`
>
> **Vertrag noch nicht eingefroren.** Dieser Bericht dokumentiert Vertragsdefekte
> **ohne** den Vertrag zu ändern (ADR-016: ein Eigentümer, eine Wahrheit). Der
> Orchestrator entscheidet über den Rücksprung zu Schritt 2.

---

## 0. Kurzfassung

### Ampel: **Rücksprung noetig** 🟠→🔴

Der Vertrag ist **noch nicht einfrierreif**: 22 Vertragsdefekte, davon 4
blockierend. Dazu kommt ein **architektonischer Bruch**, der weder im Vertrag
noch im Schema auflösbar ist (S-01): der `service_admin` soll alle Mandanten
sehen, die RLS erlaubt genau einen, und `BYPASSRLS` ist ausgeschlossen.

| Achse | Befunde | muss | sollte | optional |
|---|---|---|---|---|
| **Achse 1 — Standards** (Konventionen, Struktur, Benennung, tote Artefakte, Implementierbarkeit) | 12 | **2** | 9 | 1 |
| **Achse 2 — Spezifikation** (Vertrag ↔ Anforderung ↔ ADR ↔ Schema) | 23 | **5** | 15 | 3 |
| **Summe** | **35** | **7** | 24 | 4 |
| davon **Vertragsdefekte** in `docs/api/openapi.yaml` | **22** | **4** | 15 | 3 |

**Die sieben „muss"-Befunde in einem Satz:**
S-01 (Service-Admin sieht alle Mandanten — RLS verhindert es) ·
S-02 (`page.nextCursor` vs. `next_cursor` — jeder Listenaufruf) ·
S-03 (Aufbewahrung: Stichtag statt Anker, Markieren/Purge/Karenzzeit fehlen im Vertrag) ·
S-04 (wirksamer Rate-Limit-Grenzwert: `null` heißt zwei verschiedene Dinge) ·
S-05 („exakt die Nutzlastbytes" vs. kanonische Materialisierung) ·
N-01 (Mandantenkontext an die *Verbindung* statt an die *Transaktion* gebunden — jeder Query liefert 0 Zeilen) ·
N-02 (der Worker beansprucht Läufe ohne Mandantenermittlung — es wird nichts zugestellt).

**Was ohne Rücksprung geht:** 7 Architekturdateien sind bereits korrigiert
(§ 7). Die 4 blockierenden Vertragsdefekte sind **billig** zu beheben — je
ein bis drei Sätze in `docs/api/openapi.yaml` —, aber sie sind **nicht** im
Vorbeigehen zu machen: der Vertrag wird nach diesem Schritt eingefroren, und
eine Änderung daran ohne den Eigentümer (Schritt 2) erzeugt die zweite
Wahrheit, die ADR-016 ausdrücklich verbietet.

---

## 1. Designbaum — jeder Ast besucht

Die Wurzel ist die Absicht: *Ein zentraler Dienst nimmt Ereignisse entgegen,
stellt sie signiert und at-least-once an registrierte Empfänger-URLs weiter und
hält ein nachvollziehbares Protokoll über jeden Versuch.* Jeder folgende Ast
ist eine Entscheidung, die von der vorangehenden abhängt. **Kein Ast wurde
stillschweigend angenommen** — jeder wurde geprüft, und jeder trägt entweder
„geprüft, kein Befund" oder eine Befundnummer.

```
Wurzel: EchoBox — Ereignis-Briefkasten mit Zustellgarantie
│
├── A. ANNAHME (POST /api/v1/events)                        geprüft
│   ├── A1 Mandant & Identität (D-01)                       → S-01 (Soll: „muss“)
│   ├── A2 Idempotenz (Mandant, Schlüssel)                  → geprüft, ok
│   ├── A3 Größengrenze 1 MB & Kanonisierung               → S-05, S-20
│   ├── A4 Verschlüsselung im Ruhezustand (FR-012)         → geprüft, ok
│   └── A5 Transaktionales Outbox (Ereignis + Läufe)        → geprüft, ok
│
├── B. ZUSTELLUNG (Worker)                                   geprüft
│   ├── B1 Beanspruchung: SKIP LOCKED + Lease               → N-02
│   ├── B2 Zustandsautomat (9 Zustände)                     → S-07, N-06
│   ├── B3 Backoff & Streuung (F-04)                        → S-17
│   ├── B4 Signatur & drei Kennungen (ADR-004/005)          → geprüft, ok
│   ├── B5 Ablehnungssignal 410                              → S-13, S-14
│   ├── B6 Endpunkt-Begrenzung (F-01)                       → S-04
│   └── B7 RLS im Worker (mandantenweise)                    → N-01, N-02
│
├── C. PROTOKOLL & BETRIEB                                    geprüft
│   ├── C1 Zustellprotokoll (Metadaten, Cursor)             → S-02, S-08
│   ├── C2 Tote Zustellungen (mandantenübergreifend)         → S-01
│   ├── C3 Kennzahlen (Erfolgsquote, p95)                   → S-18
│   ├── C4 Aufbewahrung (zwei Fristen, F-02)                → S-03
│   └── C5 Health / Readiness                                → S-10
│
├── D. MANDANT & ROLLEN                                       geprüft
│   ├── D1 RLS fail-closed                                   → geprüft, ok
│   ├── D2 Rollenmatrix (F-03)                               → geprüft, ok
│   └── D3 service_admin mandantenübergreifend               → S-01  ← BRUCH
│
├── E. OBERFLÄCHE                                             geprüft
│   ├── E1 Drei Seiten, Datenquellen                         → geprüft, ok
│   ├── E2 Barrierefreiheit als Bauregel (ADR-018)           → geprüft, ok
│   └── E3 Fehlerdarstellung (Problem.violations)            → S-12
│
├── F. BETRIEB & CONTAINER                                    geprüft
│   ├── F1 Zwei Rollen, ein Image (ADR-013)                  → geprüft, ok
│   ├── F2 Konfiguration statt verdrahteter Zahlen           → N-08
│   └── F3 Instanz je Mandant vorbereitet                    → geprüft, ok
│
└── G. QUERVERTRAG & GOVERNANCE                               geprüft
    ├── G1 Ein Ort pro Wahrheit (ADR-016)                    → geprüft, ok
    ├── G2 Glossar als einziger Ort je Begriff               → N-07
    └── G3 Traceability Anforderung→Story→AK→Feature→Test    → N-10, N-12
```

**Am Rand des Baums** (nicht eigene Äste, aber geprüft): der **Vertrag**
(17 Operationen, 157 `$ref`, 33 Schemas), das **Schema** (10 Tabellen, 10
Policies, 33 Indizes), die **17 ADRs**, die **23 Features** (69 Szenarien),
die **Traceability-Matrix** und die **3 Artefakte** in `.agent/knowledge/`.

**Frontier leer.** Jeder Ast ist besucht; die verbleibenden offenen Fragen
sind keine Architekturfragen mehr, sondern **Entscheidungen des Menschen**
(§ 8) — der Skill verlangt, sie dem Menschen zu geben, nicht sie zu erfinden.

---

## 2. Achse 1 — Standards

*Axe: Repo-Konventionen, Benennung, Struktur, Konsistenz zwischen
`docs/architecture/` ↔ `docs/adr/` ↔ `.agent/artifacts/` ↔ `.agent/knowledge/`,
tote Artefakte, Implementierbarkeit.*

### N-01 — Der Mandantenkontext ist an die *Verbindung* gebunden statt an die *Transaktion* — **muss**

| | |
|---|---|
| **Fundstelle** | `docs/architecture/folder-structure.md:123-124` (vor der Korrektur `:121-122`), `docs/architecture/diagrams/03-component-api.mmd:12` („Connection-Initializer"), `docs/architecture/diagrams/03-component-api.mmd:30` („SET LOCAL app.organisation_id je Transaktion"), `docs/adr/ADR-002-mandantentrennung-rls.md:50-51` („Spring-Interceptor (TenantFilter) und ein TenantConnectionInitializer"), `docs/architecture/schema.sql:22-27` und `:386-396` (`set_config(…, is_local = true)`) |
| **Problem** | `app.set_tenant()` setzt `app.organisation_id` über `set_config(…, is_local = true)` — der Wert gilt **nur in der Transaktion, die ihn gesetzt hat**. Die Dokumente benennen aber eine *Verbindungs*-Initialisierung („Connection-Initializer“, „setzt den Kontext **vor** dem ersten Repository-Zugriff“). Mit einem Connection-Pool (HikariCP) ist das der klassische Fehler: der Wert landet entweder auf einer **anderen** Connection als die Abfrage (⇒ `current_setting('app.organisation_id', true)` = `NULL` ⇒ RLS fail-closed ⇒ **0 Zeilen**) oder, bei `set_config(…, false)`, **dauerhaft** auf der Connection und damit im Mandantenleck (Mandant A sieht Mandant B). |
| **Auswirkung** | Betrifft **jede** Operation: alle 17 antworten dann mit 404 (Ressource „nicht sichtbar“) bzw. leeren Listen. Umgekehrt ist die Le Variante ein **Mandantenleck** — NFR-003, AC-022-1/2/3, US-022, Z3 („Ein Anwendungsfehler kann kein fremdes Ereignis zeigen"). Der Fehler ist im Betrieb nicht als „Mandant falsch“, sondern als „alles leer“ sichtbar und wird deshalb leicht falsch diagnostiziert. |
| **Lösung** | Eine verbindliche Regel in der Architektur: `app.set_tenant(...)` wird **innerhalb** der Transaktion ausgeführt, die auch liest, und **immer** vor der ersten Abfrage. Kein Zugriff auf `app.*` außerhalb einer solchen Transaktion; kein `REQUIRES_NEW` nach dem Setzen; kein Autocommit. Die Trennung der Rollen: `TenantFilter` liest **nur** das JWT und füllt `TenantContext` (request-scoped, **ohne** DB-Zugriff); das Setzen des Mandanten passiert in der Transaktionsschicht. |
| **Empfehlungsstärke** | **muss** — die Umsetzung „wie dokumentiert“ macht den Dienst entweder tot oder undicht. |
| **Behandlung in diesem Schritt** | **Korrigiert** in `docs/architecture/`: `folder-structure.md` (Klassenbeschreibung + neue Konvention „Mandant“ in §4), `diagrams/03-component-api.mmd` (Komponente + Beschreibung), `architecture.md` §4 (zwei neue Zeilen: „Mandant und Transaktion“, „Mandant im Worker“). ADR-002 §„Konsequenzen“ nennt denselben Fehler und gehört Schritt 2 → **E-05** (offene Entscheidung: ADR-002 nachziehen, Klassenname umbenennen und die Traceability-Matrix nachziehen). |

### N-02 — Der Worker beansprucht Läufe ohne Mandantenermittlung — **muss**

| | |
|---|---|
| **Fundstelle** | `docs/architecture/diagrams/04-component-worker.mmd:9` und `:29` (vor der Korrektur `:25`), `docs/adr/ADR-006-zustellprozess-hintergrundprozess.md:46-53` (die Claim-SQL ohne Mandanten), `docs/adr/ADR-002-mandantentrennung-rls.md:34-37`, `docs/architecture/schema.sql:405-414` (`app.tenant_ids_for_worker()`) |
| **Problem** | Die Claim-Abfrage ist mandantenfiltert (`WHERE organisation_id = :mandant`). Das Worker-Diagramm zeigt den Schritt „Mandant ermitteln“ **nicht**; `app.tenant_ids_for_worker()` (SECURITY DEFINER, liefert nur Kennungen) kommt in keinem Diagramm vor. Wer das Diagramm umsetzt, schreibt eine mandantenlose Claim-Abfrage — und die liefert unter RLS **0 Zeilen**. |
| **Auswirkung** | AC-023-3 („genau eine Instanz führt den Versuch aus“), AC-023-4, NFR-006, Z1 („nichts verschwindet still“): es wird **gar nichts** zugestellt, und der Zustand bleibt auf `pending` — ohne Fehler, ohne Logeintrag. Der Ausfall ist still, also genau der, den der Dienst vermeiden soll. |
| **Lösung** | Im Worker-Diagramm die Kette „Scheduler → Mandantenermittlung (`app.tenant_ids_for_worker()`) → je Mandant `set_tenant` → Claim“ explizit zeichnen. Der Worker arbeitet **mandantenweise**; je Mandant eine eigene Transaktion. |
| **Empfehlungsstärke** | **muss** |
| **Behandlung** | **Korrigiert** in `docs/architecture/diagrams/04-component-worker.mmd` (Komponente `tenantResolver` + drei Relationen) und in `architecture.md` §4 (Zeile „Mandant im Worker“). |

### N-03 — Die Fehlerabbildung kennt 429 und 503 nicht — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/architecture/folder-structure.md:141` (vor der Korrektur `:130`; „400/401/403/404/409/413/422/500“) vs. `docs/adr/ADR-015-fehlersemantik-tenant-rollen.md:106-108` (429 `rate_limit_exceeded`, 503 `service_unavailable`) und `docs/api/openapi.yaml:43-54` (Fehlertabelle) |
| **Problem** | Die Abbildungstabelle der Ausnahme-Handler nennt 429 und 503 nicht. Die 503 der Readiness-Prüfung (`/health/ready`, `openapi.yaml:1401-1413`) würde dann als Standard-Spring-Fehler statt als `Problem` herausgehen. |
| **Auswirkung** | AC-023-1, NFR-006, ADR-015 §4 („Die Fehler werden **ausschließlich** an einer Stelle erzeugt“). Ein Aufrufer bekommt zwei Fehlerformate. |
| **Lösung** | 429 und 503 in die Liste aufnehmen. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Korrigiert** in `docs/architecture/folder-structure.md:130`. |

### N-04 — `PageResponse` benennt die Paginierungsfelder camelCase — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/architecture/folder-structure.md:142` (vor der Korrektur `:131`; „`limit`, `hasMore`, `nextCursor`“) vs. `docs/api/openapi.yaml:2586-2594` (`limit`, `has_more`, `next_cursor`) |
| **Problem** | Die Zielstruktur schreibt camelCase vor, das Schema snake_case. Wer ihr folgt, baut DTOs und Frontend-Typen mit falschen Feldnamen — der Fehler fällt erst im Vertragstest auf. |
| **Auswirkung** | Alle Listenoperationen (3) + die generierten Frontend-Typen + die Vertragstests. |
| **Lösung** | Feldnamen wie im Vertrag: `limit`, `has_more`, `next_cursor`; den Vertrag als Quelle nennen. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Korrigiert** in `docs/architecture/folder-structure.md:131`. Die Zwillingsfehler **im Vertrag** (S-02) bleiben Vertragsdefekte. |

### N-05 — Der Replay ist zwei Controllern zugewiesen, die Controllerzahl stimmt nicht — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/architecture/folder-structure.md:87` (vor der Korrektur `:86`; `delivery/DeliveryController` = Protokoll **und** Replay) vs. `:111` (`protocol/ProtocolController` = „Protokoll, Totenliste, **Replay**, Aufbewahrung“) vs. `docs/architecture/diagrams/03-component-api.mmd:18-21` (ein `deliveryController` für alles) vs. `.agent/artifacts/architecture.md:51` („4 Controller“) vs. `docs/requirements/traceability_matrix.json` (US-013 → `delivery/DeliveryController`, US-015/016 → `protocol/ProtocolController`, US-017 → `protocol/MetricsController`) |
| **Problem** | Dieselbe Operation (`POST /deliveries/{id}/replay`) steht in zwei Modulen; `GET /deliveries` ebenso. Der Katalog zählt 4 Controller, die Zielstruktur listet 5. |
| **Auswirkung** | Schritt 6 („ein `operationId` ein Controller“) — zwei Implementierungen oder eine unklare Zuordnung. |
| **Lösung** | Der Traceability-Matrix folgen (Schritt 1 hat sie entschieden): `delivery/DeliveryController` = Replay; `protocol/ProtocolController` = Protokoll, Totenliste, Aufbewahrung; `protocol/MetricsController` = Kennzahlen. Diagramm und Zielstruktur anpassen, Zählung korrigieren. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Korrigiert** in `docs/architecture/folder-structure.md:86, 110`, `docs/architecture/diagrams/03-component-api.mmd:18-21, 35-48`, `.agent/artifacts/architecture.md:51` („5 Controller, 8 Fachservices“). |

### N-06 — Der Zustandsautomat erlaubt den Replay jedes endgültigen Laufs — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/architecture/domain_model.md:169-173` (ASCII-Automat) und `:192` (Übergangstabelle, „beliebig endgültig“) vs. `docs/architecture/GLOSSARY.md:58` („ein abgelehnter Lauf (`rejected`) … nicht replaybar“) und `docs/api/openapi.yaml:1573-1585` („Nur **aufgegebene** Zustellungen können erneut ausgelöst werden“, 409 `delivery_state_conflict`) |
| **Problem** | Das Domain-Modell ist an dieser Stelle der Ausreißer: es erlaubt den Replay von `rejected` und `cancelled`, der Vertrag verbietet es (409). |
| **Auswirkung** | AC-013-2/3/5, FR-024, D-15, F-03. Ein Entwickler, der dem Domain-Modell folgt, liefert 202 für einen Replay, den der Vertrag mit 409 beantwortet. |
| **Lösung** | Übergang auf `abandoned` einschränken und im Automaten vermerken, dass `rejected`/`cancelled` endgültig, aber **nicht** replaybar sind. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Korrigiert** in `docs/architecture/domain_model.md` (ASCII-Automat und Übergangstabelle). |

### N-07 — Die Zustandswerte stehen im Fließtext deutsch, in den Werten englisch — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/architecture/GLOSSARY.md:15` („Status `aktiv` oder `pausiert`“), `docs/architecture/domain_model.md:39-40` („status (aktiv|pausiert)“), `docs/api/openapi.yaml:398-399` („mit dem Status `aktiv` angelegt“) vs. die tatsächlichen Werte `active`/`paused` (`openapi.yaml:1740`, `schema.sql:81`) |
| **Problem** | Der Fließtext schreibt deutsche Wörter in Code-Formatierung — als wären sie die Werte. Ein Client-Entwickler, der dem Text folgt, sendet `"aktiv"` und bekommt 422 `validation_failed`. |
| **Auswirkung** | AC-001-1, AC-002-1/2, US-019 (Statusdarstellung der Oberfläche). |
| **Lösung** | Entweder die deutschen Wörter ohne Code-Formatierung („Status *aktiv* oder *pausiert*“) oder beide ausdrücken („`active` oder `paused` (deutsch: aktiv/pausiert)“). Die **Werte** bleiben englisch, das Schema ist die Wahrheit. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Korrigiert** in `docs/architecture/GLOSSARY.md:14` und `docs/architecture/domain_model.md:38-39`. Die Vertragsstelle (`openapi.yaml:398-399`) ist ein Vertragsdefekt (S-21-Art, dort als **optional** geführt). |

### N-08 — Die Kommentare im Schema beschreiben den Stand **vor** F-01/F-02 — **sollte**

| | |
|---|---|
| **Fundstelle** | `.agent/knowledge/schema.sql:15-16` („Keine Frist- oder Grenzwert vorbelegen, solange O-01/O-02 offen sind“), `:273-274`, `:294-295`, `:297-298`, `:312-313`, `:315`, `:328-329` (dieselbe Formulierung) vs. ADR-010 §3 (Vorgabewert 60 gilt), ADR-012 §6 (Vorgabewert 30 gilt) |
| **Problem** | O-01 und O-02 sind **entschieden** (F-01, F-02). Die Kommentare behaupten weiterhin das Gegenteil. Schlimmer: `endpoint_rate_limits.enabled BOOLEAN NOT NULL DEFAULT FALSE` (`:281`) ist ein **dritter** Zustand neben „kein Override“ (`limit_value IS NULL`) und „abgeschaltet“ (`limit_value = 0`) — ADR-010 kennt nur zwei, und `enabled = FALSE` bedeutet faktisch „die Begrenzung ist aus“, obwohl ADR-010 sagt: „der Vorgabewert gilt **pro Umgebung**“. |
| **Auswirkung** | AC-018-4, AC-016-5, F-01, F-02. Der Default `FALSE` macht die Grenze **an** genau dort aus, wo sie **an** sein soll. |
| **Lösung** | Kommentare auf den entschiedenen Stand bringen; `enabled` entweder entfernen (zwei Zustände genügen) oder als „Override aktiv“ umdeuten und mit `DEFAULT`/CHECK in Einklang bringen. **Beide** Schemadateien byte-identisch ändern (ADR-016, Spiegel-Regel). |
| **Empfehlungsstärke** | **sollte** — aber **nicht** in diesem Schritt: `.agent/knowledge/schema.sql` ist die kanonische Datei und liegt außerhalb des Commit-Scopes; eine Änderung nur an der Kopie würde den Spiegel-Test brechen. Gehört in den Rücksprung (E-02). |

### N-09 — Das PRD führt die entschiedenen Punkte noch als offen — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/requirements/PRD.md:68` („totes Endpunkt-Ziel neu auslösen (Berechtigung offen, O-03)“), `:170` (FR-033 „die zulässige Rate steht noch nicht fest“), `:231-237` (O-01/O-02/O-03/O-06 mit Owner „Gate 1“), `:183` (MoSCoW) |
| **Problem** | Die Entscheidungen F-01…F-04 sind gefallen (Gate 1, 2026-10-21). Das PRD ist der Auftragseingang für Schritt 1 und wurde nicht nachgezogen. |
| **Auswirkung** | Wer das PRD statt der ADRs liest, arbeitet mit dem alten Stand. Die ADRs und das Briefing tragen die Wahrheit, aber „die Dateien sind die Wahrheit“ heißt: **die Dateien widersprechen sich**. |
| **Lösung** | Beim nächsten Schritt-1-S touch nachziehen: O-01/O-02/O-03/O-06 auf „entschieden“ setzen, die Zeile zu FR-033 und die Rollenzeile in §3 anpassen. |
| **Empfehlungsstärke** | **sollte** — kein Code-Blocker, aber ein Dauerwiderspruch in der Spec. |
| **Behandlung** | Nicht geändert: `docs/requirements/` gehört Schritt 1 (Folder Ownership). **E-06** (offen). |

### N-10 — Die Traceability-Prüfung prüft **kein** AK→Szenario — **sollte**

| | |
|---|---|
| **Fundstelle** | `.agent/tools/traceability_check.py:126-129` (prüft nur „Story hat eine Feature-Datei“) |
| **Problem** | Der Check meldet „Keine Lücken“, obwohl **32 der 101 Akzeptanzkriterien kein Gherkin-Szenario** haben (23 Features × 3 Szenarien = 69 Szenarien; 22 der 23 Stories sind unterdeckt). Konkret ohne Szenario: AC-016-4 (Filter/Paginierung der Totenliste) und **AC-016-5 (Aufbewahrung)** in US-016, AC-023-2/3 (Sharding, SKIP LOCKED) in US-023, AC-005-3…6 in US-005. |
| **Auswirkung** | Die Abdeckung wirkt vollständig, ist es aber nicht. Genau die Kriterien, die die uneindeutigen Punkte treffen (Aufbewahrung, Rate-Limit, Byte-Identität), haben **kein** Szenario — die Uneindeutigkeit fällt also nicht einmal auf. |
| **Lösung** | (a) In Schritt 5 die Testmatrix aus den **Story-JSONs** (101 AKs) bauen, nicht aus den Features (69 Szenarien); (b) den Check um eine AK→Szenario-Zählung erweitern (Ergebnis: „Kriterien ohne Szenario: 32“) — oder die Lücke in Schritt 1 schließen. |
| **Empfehlungsstärke** | **sollte** — die Prüfung ist ein Werkzeug des Frameworks, kein Anwendungscode. |

### N-11 — Leere Platzhalter-Artefakte in `.agent/artifacts/` — **optional**

| | |
|---|---|
| **Fundstelle** | `.agent/artifacts/deploy-report.md` (16 Byte), `.agent/artifacts/security-audit.md` (17 Byte), `.agent/artifacts/test-report.md` (14 Byte) |
| **Problem** | Drei Dateien ohne Inhalt — die Zielorte der Schritte 5, 8 und 9. Kein Fehler, aber tote Artefakte im Kanonischen Bereich. |
| **Auswirkung** | Gering: sie werden von den jeweiligen Schritten gefüllt. |
| **Lösung** | Stehen lassen (kein Löschen ohne menschliche Freigabe, AGENTS.md Regel 5) oder beim nächsten Framework-Touch aufräumen. |
| **Empfehlungsstärke** | **optional** |

### N-12 — Zwei Testebenen fehlen in der Zielstruktur — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/architecture/folder-structure.md:40-45` (Testbaum) und `:193` (Testkonvention) |
| **Problem** | (a) **Kein Lasttest** — NFR-002 verlangt „p95 < 200 ms **über 1.000 Einlieferungen**“ (PRD:118, 172). Das ist eine Messung über 1.000 Requests mit bis zu 1 MB Nutzlast; sie gehört in einen eigenen Lasttest, nicht in die Unit-Suite. (b) **Kein Parallelbetriebstest** — AC-023-3 („bei zwei parallelen Instanzen führt **genau eine** den Versuch aus“) und AC-023-4 (Lease-Ablauf) brauchen zwei Worker-Instanzen gegen dieselbe Datenbank. Ein normaler Integrationstest mit einem Worker prüft beides nicht. |
| **Auswirkung** | AC-023-3, AC-023-4, NFR-002, NFR-006 — drei Kriterien, die mit der beschriebenen Teststruktur **nicht** prüfbar sind. |
| **Lösung** | `tests/performance/` im Baum ergänzen; in der Testkonvention festhalten, welche Kriterien Parallelbetrieb brauchen. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Korrigiert** in `docs/architecture/folder-structure.md` (Baum + Konvention „Tests“). |

---

## 3. Achse 2 — Spezifikation

*Axe: `docs/api/openapi.yaml` ↔ `docs/requirements/` ↔ `docs/adr/` ↔ `docs/architecture/schema.sql`.
Die vollständige, zeilengenaue Liste der **Defekte im Vertrag** steht in § 5.*

### S-01 — `service_admin` soll alle Mandanten sehen, die RLS erlaubt genau einen — **muss**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:36` (Rollenmatrix: `service_admin` sieht „Endpunkte aller Mandanten“), `:464-469` (`listEndpoints`), `:1067-1074` + `:1117` (`listDeadDeliveries` „mandantenübergreifend“), `:1306` (`enforceRetention`, nur `service_admin`) · `docs/adr/ADR-015-fehlersemantik-tenant-rollen.md:35` (Rollenmatrix) und `:59-62` („Für `service_admin` gibt es **auch** keinen Mandantenfilter: der Service-Admin sieht alle Mandanten“) · `docs/requirements/PRD.md:156` (FR-004) · `docs/requirements/stories/US-016.json:17-22` (AC-016-1) · `docs/requirements/features/US-016.feature:10-13` — vs. `docs/adr/ADR-002-mandantentrennung-rls.md:20-37` und `.agent/knowledge/schema.sql:421-483` (jede Policy filtert auf `current_setting('app.organisation_id', true)`; **keine** Rolle mit `BYPASSRLS`) |
| **Problem** | Der Vertrag und die Rollenmatrix verlangen einen **mandantenübergreifenden** Blick für `service_admin`. ADR-002 legt RLS auf **jede** fachliche Tabelle fest, ohne Ausnahme, und schließt `BYPASSRLS` aus. Es gibt **keinen** Mechanismus, der „alle Mandanten“ darstellt: `current_setting(…, true)` liefert genau **einen** Wert oder `NULL`. Die Transaktion läuft also entweder mit einem fremden Mandanten (dann sieht der Admin nur dessen Zeilen) oder ohne gesetzten Mandanten (dann liefert die Policy **0 Zeilen** — der Admin sieht **nichts**). Beides verletzt FR-004, AC-004-2, AC-016-1, AC-017-1. |
| **Auswirkung** | Drei Operationen (`listEndpoints`, `listDeadDeliveries`, `enforceRetention`) sind gegen die vorliegende Architektur **nicht erfüllbar**; die Rollenmatrix aus ADR-015 §1 ist zur Hälfte nicht abgedeckt. Z6 („tote Zustellungen sind mandantenübergreifend abholbar“) ist nicht erfüllbar. Zusätzlich: `enforceRetention` ist per Schema **je Mandant** konfiguriert (`retention_settings`, PK `organisation_id`) — ein mandantenübergreifender Aufruf hat damit **keinen** eindeutigen Mandantenbezug (siehe S-03). |
| **Lösung** | Drei tragfähige Wege — **keiner davon darf am Vertrag vorbeigezogen werden**: (a) eine eigene Laufzeitrolle `echobox_ops` mit einer Policy, die `current_setting('app.organisation_ids', true)` als **Liste** auswertet (`organisation_id = ANY(...)`), gesetzt aus den Rollen des Tokens; (b) `SECURITY DEFINER`-Funktionen für die drei mandantenübergreifenden Abfragen, analog `app.tenant_ids_for_worker()`; (c) eine **zweite** Mandantenkontext-Variable neben `app.organisation_id`, die „alle Mandanten“ ausdrückt (z. B. `app.cross_tenant = true`), mit einer eigenen Policy-Variante. (a) ist die kleinste Änderung ohne Sonderrecht für Endbenutzerrollen; (b) passt am besten zum bestehenden Muster der schmalen Eigentümerfunktionen. Jeder Weg berührt **ADR-002 und das Schema** (beide Dateien byte-identisch) und ist damit ein **Rücksprung zu Schritt 2**. |
| **Empfehlungsstärke** | **muss** — blockiert die Implementierung der Service-Admin-Rolle. |
| **Entscheidung** | **E-01** (Abschnitt 8) — der Mensch wählt den Weg. Der Stresstest ändert **weder** ADR-002 noch das Schema (Schritt-2-Eigentum, und der Spiegel verlangt beide Dateien im selben Schritt). |

### S-02 — Die Paginierungsfelder sind im Text camelCase, im Schema snake_case — **muss**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:59-60` („Antwort unter `page.nextCursor` / `page.hasMore`“) vs. `:2586-2594` (`Page.properties`: `limit`, `has_more`, `next_cursor`; `required: [limit, has_more]`) |
| **Problem** | Der Prosa-Text nennt zwei Feldnamen, das Schema zwei andere. Das Schema ist die Norm — aber die Prosa steht im `info.description`, also **oben** im Vertrag, in dem Abschnitt, den jeder liest. |
| **Auswirkung** | Jede Listenoperation (`listEndpoints`, `listDeliveries`, `listDeadDeliveries`) und alle generierten Client-Typen. Ein Vertragstest, der gegen die Prosa gebaut wird, prüft die falschen Felder. |
| **Lösung** | In `info.description` `page.nextCursor` → `page.next_cursor` und `page.hasMore` → `page.has_more`. Zwei Wörter. |
| **Empfehlungsstärke** | **muss** — der Vertrag ist die Quelle für generierte Typen; zwei Namen für ein Feld sind ein eingefrorener Widerspruch. |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-01). Die Zwillingsstelle in `docs/architecture/folder-structure.md` ist bereits korrigiert (N-04). |

### S-03 — Die Aufbewahrung ist im Vertrag als globaler Stichtag beschrieben, nicht als Frist ab dem letzten Versuch — **muss**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:1296-1306` (Beschreibung `enforceRetention`), `:2508-2521` (`event_cutoff`, `delivery_cutoff` — „Datensätze **vor** diesem Zeitpunkt werden gelöscht“), `:2522-2533` (`deleted_events`, `deleted_delivery_runs`, `deleted_attempts`) · vs. `docs/adr/ADR-012-aufbewahrung.md:33-36` (zwei Fristen, **je Datenart**, Anker = letzter Versuch), `:54-64` (zwei Stufen: **markieren**, dann **Purge** nach einer **Karenzzeit**), `:66-74` (Reihenfolge und Kaskade), `:140-141` („die Karenzzeit … ist **im Vertrag ausgewiesen**“) |
| **Problem** | Fünf Widersprüche in einem Feld: (a) Der Vertrag beschreibt **globale Stichtage**, F-02/ADR-012 einen **je Datensatz** laufenden Fristbeginn (letzter Versuch). Ein Datensatz mit `last_attempt_at` vor dem Stichtag, dessen Ereignis aber nach dem Ereignis-Stichtag liegt, fällt bei der einen Regel durch und bei der anderen nicht. (b) Die **Karenzzeit** (ADR-012 §3, §6 — Konfiguration `echobox.retention.purge-delay`) ist im Vertrag **überhaupt nicht vorhanden**, obwohl ADR-012 sie ausdrücklich als „im Vertrag ausgewiesen“ bezeichnet. (c) Die Zähler heißen `deleted_*` — markiert wird aber erst (`deleted_at`), gelöscht wird später; welche Stufe gezählt wird, steht nicht da. (d) ADR-012 §1 sagt: die Ereignisfrist erfasst **auch die zugehörigen Zustellläufe und Versuche** — der Vertrag nennt drei getrennte Zähler und zwei getrennte Stichtage, als wären die Fristen unabhängig. Tatsächlich löscht das Schema per `ON DELETE CASCADE` (`schema.sql:222-225`, `:264-265`) die Läufe und Versuche **mit** dem Ereignis, auch wenn die Protokollfrist noch nicht abgelaufen ist. (e) `enforceRetention` ist eine `service_admin`-Operation über alle Mandanten, `retention_settings` ist aber **je Mandant** (PK `organisation_id`) — die Antwort nennt **ein** Fristenpaar für alle Mandanten. |
| **Auswirkung** | AC-016-5, FR-031, F-02, US-016. Das Kriterium „älter als die Frist wird gelöscht, jüngere bleiben erhalten“ ist mit dem vorliegenden Vertrag **nicht entscheidbar**: bei zwei Datensätzen mit unterschiedlichem `last_attempt_at` sagt der Vertrag „nach Stichtag“, die ADR „nach Alter je Datensatz“. Zusätzlich ist die Purge-Stufe terminiert (Karenzzeit) — ein Test, der „gelöscht“ prüft, muss warten oder die Stufe kennen. |
| **Lösung** | Im Vertrag: (1) den **Anker je Datenart** benennen (`last_attempt_at`; für Ereignisse ohne Versuch `accepted_at` — ADR-012 §2/§„Konsequenzen“); (2) die **Karenzzeit** ausweisen (Feld `purge_delay_days` in der Antwort oder in `x-delivery-protocol`/eigenem Abschnitt); (3) die Zähler umbenennen (`marked_*`) **oder** ausdrücklich benennen, welche Stufe sie zählen; (4) den **Geltungsbereich** der Operation angeben (alle Mandanten) und die Frage „welche Frist gilt, wenn die Mandanten unterschiedliche haben“ beantworten (Vorschlag: die jeweils **älteste** angewandte Frist bzw. eine Liste je Mandant); (5) die **Kaskade** benennen: das Löschen eines Ereignisses nimmt seine Läufe und Versuche mit — die Protokollfrist gilt nur für Läufe, deren Ereignis noch steht. |
| **Empfehlungsstärke** | **muss** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-02). |

### S-04 — Der „wirksame Grenzwert“ des Endpunkt-Limits hat zwei Bedeutungen — **muss**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:2442-2449` (`RateLimitObservation.limit`: „`null` bedeutet: die Begrenzung ist für diesen Endpunkt **abgeschaltet** (kein Override, `limit_value = 0`)“) und `:1273` (Beispiel `limit: null`) vs. `docs/adr/ADR-010-rate-limit.md:66-68` („`limit_value IS NULL` heißt: **kein Override** — es gilt die Vorgabe. `limit_value = 0` schaltet die Begrenzung **ab**“) und `docs/api/openapi.yaml:373` (`default_limit: 60`), `:2447-2448` („Vorgabewert **60**“) |
| **Problem** | Drei Aussagen, zwei Zustände: ADR-010 kennt **kein Override** (⇒ 60) und **abgeschaltet** (⇒ 0) — zwei verschiedene Werte, aber das Vertrag-Feld hat nur **einen** Wert (`null`) und **eine** Bedeutung („abgeschaltet“). Der Wert „60 durch Vorgabe“ und der Wert „0 abgeschaltet“ sind am Feld nicht unterscheidbar. Das Beispiel zeigt `limit: null` bei gleichzeitig dokumentiertem Vorgabewert 60 — der Standardfall sieht im Vertrag also wie „abgeschaltet“ aus. Dazu passt das Schema nicht: `endpoint_rate_limits.enabled BOOLEAN NOT NULL DEFAULT FALSE` (`schema.sql:281`) ist ein **dritter** Zustand, und der Default `FALSE` schaltet die Begrenzung faktisch aus, obwohl ADR-010 §3 den Vorgabewert **pro Umgebung** vorsieht. |
| **Auswirkung** | AC-018-4, AC-018-1, FR-033, F-01. Die Aussage „der wirksame Grenzwert ist 60“ ist am Vertragsfeld nicht ablesbar; ein Test, der `limit == 60` prüft, schlägt gegen das Beispiel fehl. Der Rate-Limiter selbst kann den Zustand „kein Override“ nicht vom Zustand „abgeschaltet“ unterscheiden. |
| **Lösung** | Im Vertrag: `limit` = der **wirksame** Grenzwert (also 60, sofern kein Override); **zusätzlich** ein Boolean, das „abgeschaltet“ ausdrückt (z. B. `limited: false` oder `limit: 0` mit der Bedeutung „0 = unbegrenzt“). `null` aus dem Enum entfernen oder eindeutig definieren. Das Beispiel auf `limit: 60` umstellen. Im Schema: `enabled` entfernen (zwei Zustände genügen) oder als „Override aktiv“ umdeuten — in **beiden** Schemadateien (Spiegel-Regel). |
| **Empfehlungsstärke** | **muss** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-03) + Schema-Teil in **E-02** (Rücksprung). |

### S-05 — „Exakt die Nutzlastbytes“ vs. kanonische Materialisierung — **muss**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:220-224` („**Der Request-Body sind exakt die Nutzlastbytes des Ereignisses** … zwingend, weil die zugestellten Bytes **byteidentisch** mit den eingelieferten Klartextbytes sein müssen (AC-007-3)“) und `:229` (Beispiel) vs. `:1962-1965` („`payload` nimmt einen beliebigen JSON-Wert entgegen; der Dienst **materialisiert ihn kanonisch** (RFC 8785, UTF-8, sortierte Schlüssel, ohne bedeutungslose Leerzeichen)“) |
| **Problem** | Für die Einlieferung als `payload` (JSON-Wert) sind die zugestellten Bytes die **kanonischen**, nicht die vom Aufrufer gesendeten. `{"b":1,"a":2}` wird als `{"a":2,"b":1}` zugestellt. Die Byte-Identität zwischen „eingeliefert“ und „zugestellt“ gilt damit nur für `payload_base64` (bytegenaue Übernahme). Der Vertrag behauptet das Gegenteil — und AC-007-3 verlangt die Byte-Identität wörtlich. |
| **Auswirkung** | AC-007-3, AC-010-1, FR-013, US-007, US-010. Der Empfänger prüft die Signatur über die **empfangenen** Bytes — das ist konsistent —, aber die Zusage „byteidentisch mit den eingelieferten“ ist für `payload` falsch. Ein Empfänger, der seinen eingelieferten Original-JSON-Text byteweise vergleicht, sieht eine Abweichung. |
| **Lösung** | Im Vertrag präzisieren: „Der Request-Body sind **die Nutzlastbytes des Ereignisses** — bei Einlieferung als `payload` die **kanonisch materialisierten** Bytes (RFC 8785), bei `payload_base64` die **exakt gelieferten** Bytes. Die zugestellten Bytes sind in beiden Fällen byteidentisch mit dem, **was das Ereignis trägt**.“ Zusätzlich in `EventIntakeRequest.payload` den Hinweis, dass die zugestellten Bytes die kanonische Form sind. |
| **Empfehlungsstärke** | **muss** — der ausgehende Vertrag ist eingefroren; eine falsche Zusage über die Byte-Identität ist ein Dauerfehler bei jedem Empfänger. |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-04). |

### S-06 — `DeliveryRun` führt die Replay-Historie nicht — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:2103-2181` (`DeliveryRun` — kein `replay_of`) vs. `docs/architecture/domain_model.md:103` („`replay_of` — Replay-Historie (AC-013-2)“, unter der Überschrift „Attribute, die den Vertrag tragen“) und `.agent/knowledge/schema.sql:199, 226-227` |
| **Problem** | Das Domain-Modell führt `replay_of` ausdrücklich als **vertragsrelevant**; im Vertrag existiert das Feld nicht. Der Empfänger-Admin, der in `GET /deliveries/{id}` einen Lauf liest, sieht nicht, dass er ein Replay ist und auf wen. |
| **Auswirkung** | AC-013-2 (Historie sichtbar), US-013. Der Historiebeweis ist nur in der **Replay-Antwort** sichtbar, nicht am Lauf selbst. |
| **Lösung** | `replay_of` (nullable UUID) in `DeliveryRun` aufnehmen und in der Beschreibung „`null`, wenn der Lauf kein Replay ist“. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-05). |

### S-07 — Der Ausgangszustand eines neuen Zustelllaufs ist uneindeutig — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:1203` (Beispiel `ReplayResponse.status: scheduled` bei `attempt_count: 0`) und `:1035` (`next_attempt_at` gesetzt) vs. `.agent/knowledge/schema.sql:190` (`status TEXT NOT NULL DEFAULT 'pending'`) und `docs/architecture/domain_model.md:116-117` (`pending` = „angelegt, noch kein Versuch ausgeführt“; `scheduled` = „ein Versuch ist geplant“) |
| **Problem** | Drei Stellen, zwei Zustände. Der Vertrag zeigt einen **neuen** Lauf (0 Versuche) im Zustand `scheduled`; das Schema legt `pending` als Default an. `docs/architecture/diagrams/05-sequence-delivery.mmd:28` schreibt „Status pending/scheduled“ und verschweigt die Wahl. |
| **Auswirkung** | AC-013-1, AC-012-2, FR-023 — und jede Abfrage, die auf `status = 'pending'` wartet (der Worker!). Falsch implementiert, beansprucht der Worker die Läufe nie. |
| **Lösung** | Eine Regel festlegen: ein neuer Lauf entsteht in `pending` mit gesetztem `next_attempt_at`; `scheduled` heißt „ein Versuch läuft gerade“. Vertragsbeispiel auf `pending` umstellen und die Zustandsbeschreibung in `DeliveryStatus` schärfen. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-06). In `docs/architecture/domain_model.md` ist die Regel jetzt als „die Zustände des Domain-Modells gelten“ hinterlegt (mit Vermerk auf den Widerspruch). |

### S-08 — Die Liste der endgültigen Zustände ist unvollständig — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:883-885` („Bei endgültigen Zuständen (`sent`, `abandoned`, `rejected`, `cancelled`) ist `next_attempt_at` **leer**“) und `:2162-2165` (dieselbe Liste) vs. `.agent/knowledge/schema.sql:217-219` (`delivery_runs_terminal_no_next_attempt_ck` — **zusätzlich** `no_subscriber`) und `docs/adr/ADR-006-zustellprozess-hintergrundprozess.md:87` |
| **Problem** | `no_subscriber` ist genauso endgültig (es gab nie einen Versuch) und fällt unter dieselbe DB-Constraint — im Vertrag fehlt es in beiden Aufzählungen. |
| **Auswirkung** | AC-015-2, AC-007-5, D-04. Ein Aufrufer, der `next_attempt_at` als „nicht endgültig“ interpretiert, wartet auf einen Versuch, den es nie geben wird. |
| **Lösung** | In beiden Stellen `no_subscriber` ergänzen. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-07). |

### S-09 — `Event.payload_media_type` ist im Vertrag nullable, im Schema `NOT NULL` — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:2077-2079` (`payload_media_type: {type: string, nullable: true}`, **nicht** in `required`) vs. `.agent/knowledge/schema.sql:148` (`payload_media_type TEXT NOT NULL DEFAULT 'application/json'`) |
| **Problem** | Der Vertrag erlaubt `null` und das Fehlen des Feldes; die Datenbank nicht. `EventIntakeRequest.payload_media_type` hat immerhin einen Default (`application/json`), aber `Event` (die Leseansicht) nicht. |
| **Auswirkung** | DTO- und Mapper-Logik: ein Mapper, der `null` zulässt, erzeugt einen Verstoß. Minor, aber eine von beiden Seiten muss nachgezogen werden. |
| **Lösung** | Vertrag: `payload_media_type` in `required` aufnehmen und `nullable` entfernen. (Oder das Schema nullable machen — die Default-Zeile spricht für „not null“.) |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-08). |

### S-10 — 429 und 503 sind definiert, aber von keiner Operation erreichbar — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:1619-1634` (`components.responses.RateLimitExceeded`, mit `Retry-After`) und `:1648-1660` (`ServiceUnavailable`) — **beide werden von keiner Operation referenziert**; `:1401-1413` (die 503 von `/health/ready` ist inline statt über die Komponente); die Fehlertabelle `:43-54` nennt 429 und 503; `docs/adr/ADR-015-fehlersemantik-tenant-rollen.md:106-108` ebenfalls |
| **Problem** | Die Fehlerliste verspricht zwei Codes, die der Client in keiner Operation erwarten kann. Maschinell geprüft: von 17 Operationen hat **keine** eine 429; die einzige 503 ist inline. Zwei Komponenten sind tote Bestandteile. |
| **Auswirkung** | ADR-015 §5, der Fehlerkatalog. Ein Client, der sich auf 429 einrichtet (Retry-After), findet sie im Vertrag nicht. |
| **Lösung** | Entweder die 429 dort referenzieren, wo sie entstehen kann (die schreibenden Operationen — `submitEvent`, `createEndpoint`, `rotateEndpointSecret`), oder die Codes aus der Fehlertabelle streichen und die beiden Komponenten löschen. Die 503 von `/health/ready` auf die Komponente umstellen. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-09). |

### S-11 — `tenant_parameter_rejected` ist im Fehlercode-Enum, aber von keiner Operation erzeugbar — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:1698` (der Code im `Problem.code`-Enum), `:2760` (die Vertragsregel: „Ein Mandantenparameter im Body, im Query oder im Header wird mit HTTP 422 (`tenant_parameter_rejected`) abgelehnt“) vs. `:1599-1618` (die einzige 422-Antwort, `ValidationFailed`, mit `code: validation_failed`) |
| **Problem** | Der Code existiert, aber keine Response erzeugt ihn. Die Regel fordert einen **eigenen** Code, die einzige 422 trägt `validation_failed`. |
| **Auswirkung** | AC-001-5, AC-004-4, D-01. Ein Aufrufer kann die „Mandantenparameter abgelehnt“ nicht von einer sonstigen Validierungsabweichung unterscheiden. |
| **Lösung** | Eine Komponente `TenantParameterRejected` (422) anlegen und an den Operationen referenzieren, die einen Body mit unbekanntem Feld ablehnen können — oder den Code aus dem Enum streichen und die Regel auf `validation_failed` umschreiben. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-10). |

### S-12 — `additionalProperties: false` vs. „Mandantenparameter ⇒ 422“: 400 oder 422? — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:1793`, `:1835`, `:1956`, `:2344`, `:2470` (`additionalProperties: false` in allen Request-Bodies) und `:1830-1831` (die Regel „Ein `organisation_id` … wird mit HTTP 422 abgelehnt“) vs. die Fehlertabelle `:45` (400 = „syntaktisch fehlerhafte Anfrage (kein gültiges JSON)“) und `:51` (422 = „semantisch ungültig (Validierung)“) |
| **Problem** | Ein Body mit `organisation_id` verletzt das Schema (`additionalProperties: false`) — also ist er **semantisch** ungültig (422) und nicht syntaktisch (400). Die Fehlertabelle trennt die Fälle, der Vertrag legt sich nicht fest. Die Implementierung entscheidet das dann (Jackson `FAIL_ON_UNKNOWN_PROPERTIES` ist in Spring Boot per Default **aus** → der Feldwert landet im Model, statt abgelehnt zu werden — das wäre eine stille Akzeptanz!). |
| **Auswirkung** | AC-001-5, AC-004-4, D-01, NFR-003. Ein still akzeptierter Mandantenparameter ist genau der Fall, den ADR-015 §2 („Er wird **nicht** stillschweigend ignoriert“) verbietet. |
| **Lösung** | Im Vertrag festlegen: „Ein Feld, das das Schema verletzt (unbekanntes Feld, falscher Typ), ist **422** `validation_failed` mit einem `violations`-Eintrag; ein **Mandantenparameter** erhält 422 `tenant_parameter_rejected`.“ Zusätzlich in `x-validation-rules` den Feldnamen `organisation_id` nennen. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-11). |

### S-13 — Der eingefrorene ausgehende Vertrag sagt nichts über 3xx — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:261-311` (die Ergebnismatrix: 2xx, 410, „5xx, 408, 429“, „übrige 4xx (außer 410)“, Transportfehler) |
| **Problem** | **3xx fehlt** — ebenso 1xx. Ein Empfänger, der mit 301/302 antwortet (sehr üblich bei HTTPS→HTTP-Umleitungen), fällt durch das Raster. Nach der Logik „übrige 4xx ⇒ aufgeben“ wäre 3xx weder 2xx noch 4xx: der Dispatcher müsste entscheiden, und jeder implementiert es anders. Der ausgehende Vertrag ist **eingefroren** — die Lücke wird jetzt dauerhaft. |
| **Auswirkung** | AC-010-3, AC-014-3/4, D-08, ADR-008 §1. Unentschieden bleibt auch, ob der HTTP-Client Weiterleitungen folgt (Java `HttpClient` folgt **nicht** von selbst; `followRedirects` ist eine bewusste Entscheidung). |
| **Lösung** | Eine Zeile in der Ergebnismatrix: „**3xx**: kein Wiederholen, der Lauf endet mit `rejected` / `rejection_reason = client_error`“ (oder „wie 5xx, mit Wiederholung“, falls Umleitungen als temporär gelten sollen) — plus die Festlegung, ob der Client Redirects folgt (`NEVER` ist die sichere Wahl, weil die Ziel-URL in der Signatur steckt). |
| **Empfehlungsstärke** | **sollte** — billig (zwei Sätze), aber vor dem Freeze zu beheben, weil danach jede Änderung eine neue Hauptversion braucht. |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-12). |

### S-14 — `timeout` und `network_error` haben keine Trennlinie — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:1769-1779` (`AttemptOutcome` mit `network_error` **und** `timeout`) und `:303-311` (der Transportfehler-Block nennt „Netzwerkfehler, DNS-Fehler, Verbindungs-Timeout, Lese-Timeout“ **ohne** Trennlinie) vs. `docs/architecture/domain_model.md:203-204` („`network_error` = DNS-, Verbindungs-, TLS-Fehler“, „`timeout` = Verbindungs- oder Lese-Timeout“) |
| **Problem** | Ein **Verbindungs-Timeout** ist in beiden Listen. Welcher `outcome` gilt, ist nicht entscheidbar — und der `outcome` ist ein **Vertragsfeld** im Protokoll (`GET /deliveries/{id}`). ADR-008 §6 nennt den Timeout einen Fehlversuch, aber ohne Trennlinie zum Netzwerkfehler. |
| **Auswirkung** | AC-011-3, AC-014-3, FR-030. Zwei verschiedene Klassifikationen für dasselbe Ereignis ⇒ die Auswertung „welche Fehlerklasse dominiert“ ist nicht reproduzierbar. |
| **Lösung** | Eine Trennlinie im Vertrag: „`timeout` = der Connect-Timeout (5 s) oder der Lese-Timeout (10 s) hat ausgelöst; `network_error` = DNS-, TLS- oder Verbindungsfehler **ohne** Timeout.“ Und im Schema prüfen, dass beide Werte zulässig bleiben (sie sind es, `schema.sql:259-261`). |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-13). |

### S-15 — Der Replay auf einen pausierten Endpunkt hat keine definierte Antwort — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:1173-1174` („**Pause:** Ist der Endpunkt pausiert, startet **kein** Versuch; die Zustellung endet erneut ohne Zustellung (D-03).“) vs. die 202-Antwort `:1193-1206` und die 409-Antwort `:1213-1214` |
| **Problem** | Der Vertrag sagt, was **nicht** passiert, nicht, **was** antwortet. 202 mit `status: cancelled`? 202 mit `status: pending`? 409 `delivery_state_conflict`? Der Zustand `cancelled` wäre endgültig und **nicht** replaybar (N-06) — dann wäre die tote Zustellung durch einen Replay **verloren** (ein Zustandswechsel, der gegen „Historie unverändert“ verstößt). |
| **Auswirkung** | AC-013-4, D-03, F-03. Der Pfad ist in der Übergangstabelle des Domain-Modells als „Endpunkt nicht pausiert“ Bedingung vermerkt — im Vertrag fehlt die Antwort. |
| **Lösung** | Im Vertrag festlegen: „Ist der Endpunkt pausiert, antwortet der Replay mit **409** `delivery_state_conflict` (Bedingung nicht erfüllt); es entsteht **kein** neuer Lauf.“ (Alternativ 202 mit `status: cancelled` — dann aber mit der Folge, dass der Vorgängerlauf seinen Zustand behält und nur ein Abbruchvermerk entsteht. Die erste Variante ist einfacher und verträgt sich mit „Historie unverändert“.) |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-14). |

### S-16 — Die Beispiele widersprechen sich und dem Backoff — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:962-963` (`listDeliveries`-Beispiel: `attempt_count: 3`, `max_attempts: 8`) vs. `:1021-1022` (`getDeliveryRun`-Beispiel **derselben** `delivery_run_id`: `attempt_count: 2` mit **zwei** Versuchen) · `:1040` (Versuch 1: `next_attempt_at: 09:16:23.004` = +1 min ✓) und `:1050` (Versuch 2: `next_attempt_at: 09:21:04.112` = +4 min 40 s — der Backoff (ADR-008 §2) verlangt nach dem zweiten Fehlversuch **2 min**, nicht 4:40) · `:804` (`payload_size_bytes: 34`) vs. das Nutzlast-Beispiel `:785-786` und `:229` (`{"order_id": 4711, "state": "shipped"}` = **38** Byte) · `:438` (`organisation_id: m-1` in der 201-Antwort von `createEndpoint`) vs. `:1875-1881` (die Feldbeschreibung: „**Nur** für `service_admin` gefüllt; für andere Rollen ist das Feld `null`“) |
| **Problem** | Vier Beispiele, die sich selbst widersprechen oder den dokumentierten Regeln widersprechen. Der Vertragstest-Plan (`folder-structure.md:217`: „eine Antwort mit **undokumentierten** Feldern ist ein Fehler“) macht die Beispiele zur Referenz — dann sind sie Testdaten mit Fehlern. |
| **Auswirkung** | AC-007-3, AC-011-1/2, AC-012-2, AC-015-1/2, AC-001-1/4. Besonders das `payload_size_bytes: 34` gegen 38 Byte: ein Test, der die Beispielwerte als Vorlage nimmt, prüft die falsche Größe. |
| **Lösung** | Die beiden Zustelllauf-Beispiele auf denselben Stand bringen (`attempt_count` und die Versuchsliste konsistent, der zweite `next_attempt_at` auf +2 min). `payload_size_bytes` auf 38 setzen (oder das Beispiel auf 34 Byte kürzen). `organisation_id` in der 201-Antwort auf `null` setzen (der Ersteller ist ein `receiver_admin`) oder die Feldbeschreibung relativieren. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-15). |

### S-17 — ADR-008 nennt die falsche Gesamtdauer — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/adr/ADR-008-retry-backoff-streuung.md:196-198` („Bei 8 Versuchen und Basis 1 Minute mit ×2 und Deckel 60 Minuten liegt der letzte Versuch rund **4 Stunden** nach dem ersten“) |
| **Problem** | Rechnung: 8 Versuche ⇒ 7 Wartezeiten = 1 + 2 + 4 + 8 + 16 + 32 + 60 = **123 Minuten ≈ 2 h 03 min** (Streuung ± 20 % ⇒ 1 h 38 min … 2 h 28 min). Nicht 4 Stunden — der Wert ist um den Faktor 2 falsch. |
| **Auswirkung** | ADR-008 nennt die Zahl selbst eine „**Zusage** gegenüber den Empfängern“. Der Vertrag nennt **keine** Gesamtdauer (`x-delivery-protocol.retry_policy` kennt nur die Parameter) — die Zusage existiert damit nur in der ADR, und dort ist sie falsch. Z1 („nach 24 h ein endgültiger Zustand“) bleibt zwar erfüllt, aber der Empfänger plant mit der falschen Zahl. |
| **Lösung** | ADR-008 auf „rund **2 Stunden**“ korrigieren und die Gesamtdauer **auch** in den Vertrag aufnehmen (als erwartetes Fenster, z. B. `expected_completion_window`), damit die Zusage dort steht, wo die Empfänger sie lesen. |
| **Empfehlungsstärke** | **sollte** — ADR gehört Schritt 2; der Vertragsteil ist V-16. |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-16) + ADR-Teil in **E-03** (Rücksprung). |

### S-18 — Das Vorgabefenster der Kennzahlen (7 Tage) steht nicht im Vertrag — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:1252-1254` (`from`/`to` **optional**, ohne Vorgabe) vs. `docs/adr/ADR-011-metriken-slo.md:32` („Default: die letzten 7 Tage bis jetzt“) und `:119-121` (bestätigt am 2026-10-21) |
| **Problem** | Ohne `from`/`to` muss der Dienst ein Fenster wählen; das Vertrag nennt keines. Das Beispiel zeigt zufällig 7 Tage (`:1264-1266`), was die Lücke kaschiert. |
| **Auswirkung** | AC-017-1/3, FR-032. Zwei Implementierungen liefern verschiedene Zahlen für dieselbe Anfrage. |
| **Lösung** | In den Parameterbeschreibungen `From`/`To` (`:1469-1484`) die Vorgabe nennen: „Ohne `from`/`to` gilt das Fenster der letzten **7 Tage** bis jetzt (ADR-011, bestätigt 2026-10-21).“ |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-17). |

### S-19 — Der Replay eines bereits replayten Laufs ist undefiniert — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:1149-1218` (die Replay-Operation, ohne Einschränkung) vs. `docs/architecture/domain_model.md:192` (der Replay ist an `abandoned` gebunden) |
| **Problem** | Der Vorgängerlauf bleibt `abandoned` (die Historie bleibt unverändert). Damit ist er **beliebig oft** replaybar — jeder Aufruf erzeugt einen **neuen** Zustelllauf. Der Vertrag sagt nichts darüber, ob das erlaubt, gewollt oder begrenzt ist. |
| **Auswirkung** | AC-013-1/2/5, FR-024. Ein Aufrufer kann durch wiederholte Replays eine Lücke erzeugen — und der Empfänger sieht mehrere Läufe mit derselben Ereignis-ID. |
| **Lösung** | Festlegen: „Ein Replay ist je aufgegebenem Zustelllauf **einmal** möglich; ein weiterer Replay desselben Laufs antwortet **409** `delivery_state_conflict`.“ (Alternativ bewusst erlauben — dann gehört es in den Vertragstext.) |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-18). |

### S-20 — Die Grenze von 1 MB gilt für die Nutzlast, nicht für den Request-Body — **sollte**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:751-754` („Gemessen werden die **Nutzlastbytes** — bei vorverschlüsselter Einlieferung also die Chiffre“) und `:2020-2021` (Validierungsregel) vs. `:1993-2000` (`payload_base64`) |
| **Problem** | `payload_base64` ist base64-kodiert: 1.048.576 Byte Nutzlast ⇒ ~1.398.102 Byte **Request-Body** (+ JSON-Overhead). Der Vertrag nennt keine Grenze für den Request-Body. Server mit einer Body-Grenze (Servlet-Container, Ingress, Gateway) lehnen solche Requests ab — mit 413, aber mit **falschem** Code, oder gar mit 400. |
| **Auswirkung** | NFR-001, D-13, AC-005-2/3, AC-008-1. Die Grenze „1 MB“ ist für einen Aufrufer mit vorverschlüsselter Einlieferung in Wahrheit ~1,4 MB. |
| **Lösung** | Im Vertrag dazusagen: „Die Grenze von 1.048.576 Byte betrifft die **Nutzlastbytes**; der Request-Body darf entsprechend größer sein (base64 ⇒ ~1,4×). Der Dienst akzeptiert einen Request-Body bis 2 MiB.“ Und in der Betriebsplanung (Schritt 9) die Body-Grenze des Containers entsprechend setzen. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-19). |

### S-21 — Der ausgehende `Content-Type` ist fest auf `application/json` verdrahtet — **optional**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:148-151` (`value: "application/json"`, daneben „Entspricht `payload_media_type` des Ereignisses“) vs. `:2010-2014` (`payload_media_type` frei, Default `application/json`) |
| **Problem** | Der `value` nennt `application/json`, die Beschreibung sagt „entspricht `payload_media_type`“. Für ein Ereignis mit `payload_media_type: application/xml` widersprechen sich beide. |
| **Auswirkung** | FR-013, US-008 — der Empfänger parst nach dem Header, den der Dienst setzt. |
| **Lösung** | `value: "<payload_media_type des Ereignisses> (Vorgabe: application/json)"`. |
| **Empfehlungsstärke** | **optional** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-20). |

### S-22 — `RejectionReason` führt `null` im Enum — **optional**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:1781-1788` (`type: string`, `nullable: true`, `enum: [permanent_rejection, client_error, null]`) |
| **Problem** | In YAML ist `null` der Wert `None`; zusammen mit `nullable: true` ist das in OpenAPI 3.0 zulässig, aber mehrere Generatoren (und strenge Linter) stolpern über einen Nullwert im Enum. |
| **Auswirkung** | Gering — der Wert entsteht in der DTO-Generierung (Schritt 6). |
| **Lösung** | `null` aus dem Enum streichen; `nullable: true` deckt den Fall ab. |
| **Empfehlungsstärke** | **optional** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-21). |

### S-23 — `payload: null` ist nicht ausgeschlossen — **optional**

| | |
|---|---|
| **Fundstelle** | `docs/api/openapi.yaml:1986-1992` (`payload` ohne `type`, ohne `nullable: false`) und `:2016-2019` (die Regel „Genau **eine** von `payload`/`payload_base64`“) |
| **Problem** | Ein Schema ohne `type` erlaubt jeden JSON-Wert — auch `null`. „Genau eine gesetzt“ ist mit `payload: null` nicht eindeutig (gesetzt, aber ohne Nutzlast). Bei `payload_encoding: pre_encrypted` ist `payload` verboten — `payload: null` fällt unter dieselbe Regel. |
| **Auswirkung** | AC-008-2, NFR-001 — ein leeres Ereignis (0 Byte Nutzlast) ist ein Grenzfall, der jetzt schon im Schema steckt. |
| **Lösung** | `nullable: false` setzen und in der Regel ausdrücklich „`payload: null` gilt als **nicht** gesetzt“ ergänzen. |
| **Empfehlungsstärke** | **optional** |
| **Behandlung** | **Vertragsdefekt** (Abschnitt 5, V-22). |

---

## 4. Developer-Interface — 9 Dimensionen

| # | Dimension | Ergebnis | Begründung / Fundstelle |
|---|---|---|---|
| 1 | **Auffindbarkeit** (Finden der passenden Operation) | 🟢 gut | 17 Operationen, alle mit `operationId`, `summary`, `description`, Tag; `x-acceptance-coverage` (101) und `x-requirement-coverage` (24) verweisen jeden Kriterium auf seinen Ort. Die Tags folgen den Epics. *Anmerkung:* die **Rollen** sind nur im Prosa-Text je Operation, nicht maschinenlesbar (kein `x-roles`) — die Rollenmatrix aus ADR-015 §1 steht an keiner Stelle im Vertrag. |
| 2 | **Konsistenz** (gleiche Dinge gleich benannt) | 🔴 mangelhaft | Drei Widersprüche: `page.nextCursor` vs. `next_cursor` (S-02), deutsch im Text vs. englisch in den Werten (N-07), `no_subscriber` fehlt in der Endgültigkeitsliste (S-08). Dazu: `Event.payload_media_type` nullable (S-09). |
| 3 | **Vollständigkeit** (deckt der Vertrag die Aufgabe ab?) | 🟡 weitgehend | Alle 23 Stories / 101 AKs / 24 Anforderungen sind auffindbar. Aber: 4 Fehlercodes sind **unerreichbar** (S-10, S-11), die Karenzzeit der Aufbewahrung fehlt (S-03), `replay_of` fehlt (S-06), 3xx fehlt (S-13), das Vorgabefenster fehlt (S-18), die Gesamtdauer der Zustellung fehlt (S-17). |
| 4 | **Fehlerbehandlung** (was kann schiefgehen, wie erfährt es der Aufrufer) | 🟡 strukturiert, Lücken | Ein Schema (`Problem`), eine Status→Code-Tabelle, `violations` je Feld, eine einzige Erzeugungsstelle (ADR-015 §4). Lücken: die Erreichbarkeit der Codes (S-10/S-11), die 400/422-Grenze bei Schemaverstößen (S-12), die Antwort auf „pausierter Endpunkt“ (S-15), die 503 der Readiness ist inline statt über die Komponente. |
| 5 | **Sicherheit & Mandantentrennung** | 🔴 **blockiert** | Stark: JWT-only, Mandant ausschließlich aus dem Token, Fremdes ⇒ 404 (kein Bestandsschutz), Secret nie in der Antwort, `writeOnly`, `additionalProperties: false`, RLS fail-closed. Aber: die **Service-Admin-Sicht auf alle Mandanten ist mit dem vorliegenden Schema nicht darstellbar** (S-01) — der zentrale Schutzfall des Dienstes hängt an einer ungeklärten Architekturentscheidung. |
| 6 | **Performanz & Grenzen** | 🟢 gut, mit Anmerkungen | Cursor-Paginierung (kein `total`), `limit` ≤ 200, 33 Indizes auf die realen Zugriffspfade, asynchrone Annahme, Metriken on demand. Anmerkungen: die 1-MB-Grenze ist eine **Nutzlast**-, keine Body-Grenze (S-20), die Gesamtdauer der Zustellung ist nirgends zugesagt (S-17), die Metrikenberechnung on demand ist ein acknowledged Risiko (ADR-011 §„Negativ“). |
| 7 | **Idempotenz & Wiederholbarkeit** | 🟡 gut, mit Lücken | Stark: Idempotenz je (Mandant, Schlüssel) mit gezieltem HMAC-Vergleich, 202 mit der **ersten** ID, 409 bei abweichender Nutzlast, at-least-once mit drei Kennungen, Replay als **neuer** Lauf. Lücken: die Wiederholbarkeit des Replay selbst (S-19), die Antwort bei pausiertem Endpunkt (S-15), die 429 für den Aufrufer (S-10), `pause`/`resume`/`secret-rotation` sind nicht als idempotent ausgewiesen. |
| 8 | **Testbarkeit** | 🔴 mangelhaft | 7 „muss"-Befunde machen mehrere AKs **nicht entscheidbar** (S-01, S-03, S-04, S-05). Dazu: **32 der 101 AKs haben kein Gherkin-Szenario** (N-10), der Traceability-Check meldet trotzdem „keine Lücken“; zwei Testebenen fehlen (N-12: Lasttest für NFR-002, Parallelbetriebstest für AC-023-3/4). Positiv: `ClockPort`, `IdGenerator` und die Ports machen Backoff, Signatur und Zustandsautomat unit-testbar; die RLS ist gegen echtes PostgreSQL nachgewiesen. |
| 9 | **Verständlichkeit & Dokumentation** | 🟡 brauchbar | Eine Erzählung, ein Glossar, ADRs mit Begründung und Alternativen, Beispiele je Operation. Lücken: die **Beispiele widersprechen sich** (S-16), der Prosa-/Schema-Widerspruch bei den Paginierungsfeldern (S-02), die falsche Gesamtdauer in ADR-008 (S-17), das PRD ist nicht nachgezogen (N-09), die Schema-Kommentare sind es auch nicht (N-08). |

---

## 5. Vertragsdefekte in `docs/api/openapi.yaml`

**Der Vertrag wurde nicht verändert.** Grund: ein Eigentümer, eine Wahrheit
(ADR-016); der Vertrag wird nach diesem Schritt eingefroren. Jeder Defekt
hier ist **Dokumentation**, kein Fix.

| ID | Pfad:Zeile | erwartet | tatsächlich | Auswirkung | Korrektur | Priorität |
|---|---|---|---|---|---|---|
| **V-01** | `docs/api/openapi.yaml:59-60` | `page.next_cursor` / `page.has_more` | `page.nextCursor` / `page.hasMore` (das Schema `:2586-2594` nutzt snake_case) | alle 3 Listenoperationen, generierte Client-Typen, Vertragstests | die zwei Feldnamen in `info.description` auf snake_case korrigieren | **muss** |
| **V-02** | `:1296-1306`, `:2508-2521`, `:2522-2533` | Anker je Datensatz (`last_attempt_at`), Markieren **und** Purge, Karenzzeit, Kaskade, Geltungsbereich (alle Mandanten) | globale Stichtage; nur `deleted_*`; keine Karenzzeit; je ein Fristenpaar | AC-016-5, FR-031, F-02, US-016 | Anker, Stufen, Karenzzeit, Kaskade und Geltungsbereich je Datenart benennen; Zähler umbenennen oder der Stufe zuordnen | **muss** |
| **V-03** | `:2442-2449`, `:1273` (Beispiel), `:373` | `limit` = wirksamer Grenzwert (Vorgabe **60**); „abgeschaltet“ **eigenständig** ausgewiesen | `null` heißt „abgeschaltet“ **und** „kein Override“ (⇒ 60); das Beispiel zeigt `limit: null` | AC-018-4, FR-033, F-01 | `limit` auf den wirksamen Wert festlegen, „abgeschaltet“ als eigenes Feld (`limited: false` bzw. `limit: 0`), Beispiel auf `limit: 60` | **muss** |
| **V-04** | `:220-224` vs. `:1962-1965` | „zugestellte Bytes = **Nutzlastbytes des Ereignisses**“ (bei `payload`: kanonisch materialisiert) | „byteidentisch mit den **eingelieferten** Klartextbytes“ | AC-007-3, FR-013, US-007/US-010 | Formulierung auf die kanonischen Bytes bzw. die exakt gelieferten Bytes umstellen; Hinweis in `EventIntakeRequest.payload` | **muss** |
| **V-05** | `:2103-2181` | `replay_of` (nullable) in `DeliveryRun` | Feld fehlt (Domain-Modell `:103` nennt es „vertragsrelevant“) | AC-013-2, US-013 | Feld aufnehmen und beschreiben | sollte |
| **V-06** | `:1203` (Beispiel) vs. `schema.sql:190` | neuer Lauf = `pending` | Beispiel `status: scheduled` bei `attempt_count: 0` | AC-013-1, AC-012-2, der Worker wartet auf `pending` | Beispiel auf `pending` umstellen, `DeliveryStatus` schärfen | sollte |
| **V-07** | `:883-885`, `:2162-2165` | endgültig: `sent`, `abandoned`, `rejected`, `cancelled`, **`no_subscriber`** | `no_subscriber` fehlt (DB-Constraint `schema.sql:217-219` kennt ihn) | AC-015-2, D-04 | in beiden Stellen ergänzen | sollte |
| **V-08** | `:2077-2079` vs. `schema.sql:148` | `payload_media_type` erforderlich, nicht null | nullable, nicht in `required` | DTO-/Mapper-Logik, `GET /events/{id}` | `required` ergänzen, `nullable` entfernen | sollte |
| **V-09** | `:1619-1634`, `:1648-1660` (ungenutzt), `:1401-1413` | 429/503 dort referenzieren, wo sie entstehen können | **keine** Operation kann 429 liefern; 503 inline; 2 tote Komponenten | ADR-015 §5, Client-Retry | 429 an den schreibenden Operationen referenzieren **oder** Codes streichen + Komponenten löschen; 503 auf die Komponente umstellen | sollte |
| **V-10** | `:1698` (Enum), `:2760` (Regel) vs. `:1599-1618` | eine 422-Antwort mit `code: tenant_parameter_rejected` | nur `ValidationFailed` mit `validation_failed` | AC-001-5, AC-004-4, D-01 | Komponente `TenantParameterRejected` anlegen und referenzieren **oder** Code streichen | sollte |
| **V-11** | `:1793`, `:1835`, `:1956`, `:2344`, `:2470` vs. `:45`/`:51`/`:1830-1831` | Verstoß gegen das Request-Schema = **422** `validation_failed`; Mandantenparameter = **422** `tenant_parameter_rejected` | 400 = „kein gültiges JSON“, 422 = „semantisch ungültig“ — der Vertrag legt den Fall **nicht** fest | AC-001-5, D-01, NFR-003 (stille Akzeptanz eines unbekannten Feldes) | die 400/422-Grenze im Vertrag ausschreiben; `organisation_id` in `x-validation-rules` benennen | sollte |
| **V-12** | `:261-311` | Ergebnismatrix ohne Lücke (1xx/3xx) | 3xx ist **nicht** definiert | AC-010-3, AC-014-3/4, eingefrorener Fremdvertrag | eine Zeile für 3xx + die Regel, ob der Client Redirects folgt | sollte |
| **V-13** | `:1769-1779` vs. `:303-311` | Trennlinie `timeout` vs. `network_error` | ein Verbindungs-Timeout passt in beide | AC-011-3, FR-030 | „`timeout` = Connect-/Lese-Timeout; `network_error` = DNS/TLS/Verbindung **ohne** Timeout“ | sollte |
| **V-14** | `:1173-1174` | die Antwort auf einen Replay bei pausiertem Endpunkt | nur „startet kein Versuch“ | AC-013-4, D-03, F-03 | 409 `delivery_state_conflict` (oder 202 mit `status: cancelled`) festlegen | sollte |
| **V-15** | `:962-963` vs. `:1021-1022`/`:1050`; `:804`; `:438` | widerspruchsfreie Beispiele | `attempt_count` 3 vs. 2; `next_attempt_at` +4:40 statt +2 min; `payload_size_bytes` 34 statt 38; `organisation_id: m-1` in der 201-Antwort eines `receiver_admin` | AC-007-3, AC-011-1/2, AC-012-2, AC-015-1/2, AC-001-4 | Beispiele konsistent rechnen und korrigieren | sollte |
| **V-16** | `:330-353` (`x-delivery-protocol.retry_policy`) | das erwartete Gesamtfenster (≈ 2 h) | kein Gesamtfenster genannt (ADR-008 nennt fälschlich 4 h) | Zusage gegenüber den Empfängern | ein Feld für das erwartete Fenster aufnehmen; ADR-008 korrigieren (Schritt 2) | sollte |
| **V-17** | `:1252-1254` (Parameter `From`/`To` in `:1469-1484`) | Vorgabefenster **7 Tage** (ADR-011) | keine Vorgabe genannt | AC-017-1/3, FR-032 | Vorgabe in den Parameterbeschreibungen nennen | sollte |
| **V-18** | `:1149-1218` | Regel für den Replay eines **bereits** replayten Laufs | keine Regel | AC-013-1/2/5, FR-024 | einmal-Replay-Regel + 409 festlegen (oder die Mehrfachfreigabe bewusst erlauben) | sollte |
| **V-19** | `:751-754`, `:1993-2000` | Aussage, dass die 1-MB-Grenze die **Nutzlast** betrifft und der Request-Body größer sein darf (base64 ≈ 1,4×) | nur die Nutzlastgrenze; keine Body-Aussage | NFR-001, D-13, AC-005-2/3, AC-008-1 | Body-Obergrenze (2 MiB) und die base64-Folge nennen | sollte |
| **V-20** | `:148-151` vs. `:2010-2014` | `value: "<payload_media_type>" (Vorgabe: application/json)` | `value: "application/json"`, Beschreibung sagt „entspricht `payload_media_type`“ | FR-013, US-008 | Wert an die Beschreibung angleichen | optional |
| **V-21** | `:1781-1788` | Enum ohne Nullwert (`nullable: true` genügt) | `enum: [permanent_rejection, client_error, null]` | DTO-Generatoren | `null` streichen | optional |
| **V-22** | `:1986-1992`, `:2016-2019` | `payload: null` gilt als **nicht** gesetzt | ein typenloses Schema erlaubt `null` | AC-008-2, NFR-001 | `nullable: false` + Regel ergänzen | optional |

**Maschinell geprüft und in Ordnung** (kein Defekt): 17 Operationen, alle mit
eindeutigem `operationId`, `summary`, `description`, `security`, `x-ac-id` **und**
`x-requirement-ids`; alle 157 `$ref` auflösbar; **33 Schemas, alle 33
referenziert** (die beiden ungenutzten Bestandteile sind **Antwort-Komponenten**,
keine Schemas — V-09); die Statuscode-Matrix (201×1, 202×2, 200×14, 400×4,
401×15, 403×15, 404×9, 409×3, 413×1, 422×15, 500×17, 503×1) ist vollständig mit
definierten Komponenten unterlegt; 202 antworten **nur** `submitEvent` und
`replayDelivery` (beide asynchron — NFR-002, D-10).

---

## 6. Testbarkeit — was gegen diese Architektur **nicht** prüfbar ist

| AK / NFR | Status | Grund | Erforderliche Teststrategie |
|---|---|---|---|
| **AC-016-1, AC-004-2** (Service-Admin sieht alle Mandanten) | 🔴 **nicht prüfbar** | Die RLS lässt genau einen Mandanten zu (S-01) — der Testfall „Mandant A und B, Admin sieht beide“ schlägt fehl, egal wie er implementiert ist | Integration (Testcontainers PG 16), **2 Mandanten + Service-Admin-Rolle**, erst nach E-01 |
| **AC-016-5** (Aufbewahrung) | 🔴 **nicht prüfbar** | Anker, Stufen, Karenzzeit und Kaskade sind im Vertrag nicht festgelegt (S-03) — „gelöscht“ hat drei mögliche Bedeutungen | Integration: Datensätze mit **unterschiedlichem** `last_attempt_at`; je Stufe ein Testfall; die Frist per Konfiguration gesetzt (30 Tage im Test) |
| **AC-018-4** (wirksamer Grenzwert) | 🔴 **nicht prüfbar** | `limit: null` vs. `limit: 60` — die Erwartung ist nicht entscheidbar (S-04) | Integration: Endpunkt **ohne** Override ⇒ 60; **mit** Override; **abgeschaltet** |
| **AC-007-3** (byteidentische Zustellung) | 🔴 **nicht prüfbar** | „eingeliefert“ vs. „kanonisiert“ (S-05) | Unit (Kanonisierung) + E2E gegen einen Stub-Empfänger, der die Bytes prüft — für `payload` (kanonisch) und `payload_base64` (exakt) getrennt |
| **AC-012-4** (Grenze 1 ⇒ genau ein Versuch ⇒ `abandoned`) | 🟡 prüfbar nach V-06 | braucht die Festlegung des Ausgangszustands | Unit (Zustandsautomat) + Integration |
| **AC-023-3** (genau eine Instanz führt den Versuch aus) | 🟡 prüfbar, **Layer fehlt** | braucht **zwei** Worker-Instanzen gegen eine Datenbank — in der Zielstruktur nicht vorgesehen (N-12) | Integration, zwei Worker, `SKIP LOCKED` + Lease |
| **AC-023-4** (Versuchszahl springt nicht) | 🟡 prüfbar, **Layer fehlt** | braucht einen Lease-Ablauf im Test (Zeitmanipulation über `ClockPort`) | Integration mit manipulierter Uhr + Worker-Absturz |
| **NFR-002** (p95 < 200 ms über 1.000 Einlieferungen) | 🟡 prüfbar, **Layer fehlt** | braucht einen **Lasttest** (1.000 Requests, bis 1 MB) — kein Unit-/Integrationstest (N-12) | `tests/performance/` |
| **AC-021-1…4** (Barrierefreiheit) | 🟢 prüfbar | axe-Scan + Tastaturtest | E2E (Playwright + axe) — in Schritt 5 vorgesehen |
| **AC-022-1…3** (RLS) | 🟢 prüfbar | gegen echtes PostgreSQL nachgewiesen (ADR-002 „Verifikation“) | Integration (Testcontainers), fail-closed + je Mandant |
| **AC-009-1…5** (Signatur) | 🟢 prüfbar | reine HMAC-Rechnung über die exakten Bytes | Unit (Signatur) + Vertragstest (Header-Name/Format) + E2E (Stub-Empfänger prüft) |
| **AC-011-5** (kein geplanter Zeitpunkt in der Vergangenheit) | 🟢 prüfbar | `ClockPort` | Unit |
| **AC-006-1…4** (Idempotenz) | 🟢 prüfbar | UNIQUE (Mandant, Schlüssel) + Fingerprint | Integration (202/409, je Mandant) |
| **32 AK ohne Gherkin-Szenario** | 🟡 Lücke | das Feature-Layer deckt nicht alle AKs ab (N-10) | die Testmatrix in Schritt 5 aus den **Story-JSONs** (101 AKs) bauen, nicht aus den Features (69 Szenarien) |

**Zusatzbefund für Schritt 5:** Der Traceability-Check
(`.agent/tools/traceability_check.py`) bestätigt die Kette
Anforderung→Story→Feature→Test→Code, prüft aber **nicht** AK→Szenario und
**nicht** AK→Test. In Schritt 5 müssen beide Zuordnungen je AK aus der
Matrix kommen, sonst bleibt die Abdeckung auf dem Level „Story hat ein
Feature“.

---

## 7. Korrekturen in `docs/architecture/` (in diesem Schritt vorgenommen)

Alle Korrekturen betreffen **echte Fehler**; keine bestätigte Entscheidung
(F-01…F-04, ADR-001…ADR-018) wurde angetastet, kein Vertrag und kein ADR
wurde geändert.

| # | Datei | Was | Grund (Befund) |
|---|---|---|---|
| 1 | `docs/architecture/folder-structure.md:123-124, 189` | `TenantConnectionInitializer`/`TenantFilter`: der Mandant wird **in der Transaktion** gesetzt, die auch liest; `TenantFilter` liest **nur** das JWT. Erläuterung mit dem fail-closed-FolgemFall | N-01 |
| 2 | `docs/architecture/folder-structure.md` | neue Konvention **„Mandant“** in §4 (transaktionsgebunden, kein Autocommit, kein `REQUIRES_NEW`) | N-01 |
| 3 | `docs/architecture/folder-structure.md:142` | `PageResponse`: `has_more`, `next_cursor` (wie im Vertrag) | N-04 |
| 4 | `docs/architecture/folder-structure.md:141` | `GlobalExceptionHandler`: 429 und 503 ergänzt | N-03 |
| 5 | `docs/architecture/folder-structure.md:87, 111` | Controllerzuordnung: Replay → `delivery/DeliveryController`; Protokoll/Totenliste/Aufbewahrung → `protocol/ProtocolController` (wie die Traceability-Matrix) | N-05 |
| 6 | `docs/architecture/folder-structure.md:45, 193` | Testbaum: `tests/performance/` ergänzt; Konvention „Tests“ um **Parallelbetrieb** (AC-023-3/4) und **Last** (NFR-002) erweitert | N-12 |
| 7 | `docs/architecture/diagrams/03-component-api.mmd` | Mandanten-Komponente: „transaktionsgebunden, nicht verbindungsgebunden“; Controller aufgeteilt (Delivery/Protocol/Metrics); Fehlercodes um 503 ergänzt | N-01, N-03, N-05 |
| 8 | `docs/architecture/diagrams/04-component-worker.mmd` | Komponente **Mandantenermittlung** (`app.tenant_ids_for_worker()`) + die Kette „Mandant → `set_tenant` → Claim“ | N-02 |
| 9 | `docs/architecture/domain_model.md` | Replay nur noch aus `abandoned` (Tabelle **und** ASCII-Automat); `rejected`/`cancelled` ausdrücklich **nicht** replaybar | N-06 |
| 10 | `docs/architecture/domain_model.md:39-40` | Zustandswerte `active`/`paused` (statt „aktiv/pausiert“) in der Entitätenübersicht | N-07 |
| 11 | `docs/architecture/domain_model.md` | Vermerk: der **neue** Zustelllauf entsteht in `pending`; der Widerspruch zum Vertragsbeispiel ist benannt (S-07) | S-07 |
| 12 | `docs/architecture/GLOSSARY.md:15` | Endpunkt-Status: `active`/`paused` mit dem deutschen Wort als Erläuterung | N-07 |
| 13 | `docs/architecture/architecture.md` | zwei neue Zeilen in §4: „Mandant und Transaktion“, „Mandant im Worker“ | N-01, N-02 |
| 14 | `docs/architecture/architecture.md` | Artefaktliste um `.agent/artifacts/stress-test.md` ergänzt | Konsistenz |
| 15 | `.agent/artifacts/architecture.md` | „4 Controller, 7 Fachservices“ → „5 Controller, 8 Fachservices“; Abschnitt **9a** mit den Schritt-4-Befunden; Artefaktliste ergänzt | N-05, Konsistenz |

**Bewusst nicht geändert** (und warum): `docs/api/openapi.yaml` (Eigentümer
Schritt 2 — 22 Vertragsdefekte dokumentiert, keiner repariert) ·
`docs/adr/**` (Schritt 2 — E-03, E-05) · `docs/requirements/**` (Schritt 1 —
E-06) · `.agent/knowledge/schema.sql` (kanonisch; die Spiegel-Regel verlangt
**beide** Dateien im selben Schritt, `.agent/knowledge/` liegt außerhalb des
Commit-Scopes — E-02) · `.agent/tools/traceability_check.py` (N-10 gehört nach
Schritt 5) · `.agent/artifacts/*.md` außer `architecture.md` (Platzhalter für
spätere Schritte, N-11).

---

## 8. Offene Entscheidungen für den Menschen

Der Skill verlangt, an dieser Stelle zu **fragen** statt zu entscheiden. Als
Subagent frage ich nicht nach — ich lege die Entscheidung samt Empfehlung
vor. **E-01…E-03 blockieren Schritt 5.**

| ID | Frage | Optionen | **Empfehlung** | Warum | Gate |
|---|---|---|---|---|---|
| **E-01** | Wie bekommt der `service_admin` die mandantenübergreifende Sicht, ohne `BYPASSRLS`? (S-01) | (a) eigene Laufzeitrolle `echobox_ops` mit `current_setting('app.organisation_ids')` als Liste; (b) `SECURITY DEFINER`-Funktionen für die drei mandantenübergreifenden Abfragen; (c) zweite Kontextvariable `app.cross_tenant` | **(b)** | Passt zum bestehenden Muster (schmale Eigentümerfunktionen statt Sonderrechten) und hält die Policy einfach: der Normalfall bleibt „ein Mandant“, der Ausnahmefall ist eine eng zugeschnittene Funktion. (a) ist kleiner, gibt aber einer Rolle dauerhaft mehr als einen Mandanten. | vor Schritt 5 |
| **E-02** | Wie wird der **wirksame** Rate-Limit-Grenzwert modelliert, damit „kein Override“ (60) und „abgeschaltet“ (0) unterscheidbar sind? (S-04, N-08) | (a) `enabled`-Spalte entfernen, `limit_value IS NULL` = kein Override, `0` = aus (ADR-010 wortgetreu); (b) `enabled` behalten, aber als „Override aktiv“ umdeuten | **(a)** | ADR-010 §3 kennt genau zwei Zustände; eine dritte Spalte erzeugt einen dritten, widersprüchlichen. Zusätzlich: die Kommentare im Schema auf den entschiedenen Stand bringen (O-01/O-02 sind zu). Beide Schemadateien byte-identisch ändern. | vor Schritt 5 |
| **E-03** | Darf ADR-008 nach dem Freeze korrigiert werden, oder wird die falsche „4 Stunden“ als Zusage festgeschrieben? (S-17) | (a) ADR-008 auf „≈ 2 Stunden“ korrigieren + Gesamtfenster in den Vertrag; (b) ADR unverändert lassen, nur den Vertrag ergänzen | **(a)** | Die Zahl ist eine Zusage gegenüber den Empfängern; eine falsche Zusage in einem „angenommenen“ ADR ist teurer als ein zusätzlicher Rücksprung. | vor dem Freeze |
| **E-04** | Soll die Aufbewahrung **zwei** Fristen behalten, obwohl das Löschen eines Ereignisses seine Läufe und Versuche mitnimmt? (S-03) | (a) zwei Fristen behalten und die Kaskade im Vertrag benennen; (b) auf eine Frist vereinfachen | **(a)** | F-02 ist bestätigt (getrennt, 30 Tage je Datenart). Die Trennung macht Sinn, solange man sagt, dass die Ereignisfrist ihre Läufe mitnimmt — das ist eine Entscheidung, die F-02 nicht berührt. | mit E-01 |
| **E-05** | Wie wird der **Namenswiderspruch** „Connection-Initializer“ aufgelöst, obwohl die Traceability-Matrix (Schritt 1) die Klasse so nennt? (N-01) | (a) umbenennen (`TenantTransactionInitializer`) und die Matrix in Schritt 1 nachziehen; (b) Namen behalten, Semantik in der Beschreibung festziehen (aktuell so umgesetzt) | **(b) jetzt, (a) später** | Der Matrix-Name ist eine Artefaktgrenze zwischen Schritt 1 und Schritt 5; ein Umbau mitten im Prozess kostet mehr als der klare Kommentar. Die Umbenennung kann mit dem nächsten Schritt-1-Touch nachgezogen werden. | optional |
| **E-06** | Wer zieht das **PRD** auf den entschiedenen Stand nach (O-01/O-02/O-03/O-06)? (N-09) | (a) Schritt 1 (Requirements-Orchestrator) beim nächsten Touch; (b) eine Korrekturanforderung an Schritt 1 im Rahmen des Rücksprungs | **(a) im Rahmen des Rücksprungs** | Das PRD ist die Anforderungsquelle für Schritt 1; ein Rücksprung bietet den natürlichen Anlass. Die ADRs tragen die Wahrheit, die Dauerwidersprüchlichkeit bleibt aber bestehen. | mit dem Rücksprung |
| **E-07** | Soll der Stresstest **32 AKs** um Gherkin-Szenarien ergänzen (Schritt 1) oder die Testmatrix in Schritt 5 direkt aus den Story-JSONs bauen? (N-10) | (a) Szenarien in Schritt 1 nachtragen; (b) Testmatrix aus den 101 AKs bauen, Szenarien als Smoke-Layer belassen | **(b)** | Die AKs sind die prüfbare Quelle; die Szenarien sind eine lesbare Auswahl. 32 nachträgliche Szenarien kosten Schritt-1-Aufwand ohne Prüftiefe — der offene Punkt ist aber, dass **genau** die AKs ohne Szenario (Aufbewahrung, Rate-Limit, Parallelbetrieb) die risikoreichsten sind. | Schritt 5 |
| **E-08** | Wie soll der Dienst mit **3xx**-Antworten umgehen? (S-13) | (a) kein Wiederholen, `rejected` / `client_error`; (b) wie 5xx, mit Wiederholung; (c) Redirects folgen | **(a) + (c) nein** | Der ausgehende Vertrag ist eingefroren: eine Lücke bleibt dauerhaft. Weiterleitungen zu folgen würde die signierte Ziel-URL aufweichen. | vor dem Freeze |
| **E-09** | Darf ein Replay **mehrfach** auf denselben aufgegebenen Lauf ausgelöst werden? (S-19) | (a) einmal, danach 409; (b) beliebig oft | **(a)** | Der Empfänger soll mehrere Zustellläufe derselben Ereignis-ID nicht unkontrolliert sehen; der Historiegedanke lebt davon, dass jeder Replay eine begründete, einmalige Aktion ist. | vor dem Freeze |

**Bestätigte Entscheidungen, die der Stresstest **nicht** neu aufgerollt hat:**
D-01…D-15, ADR-001…ADR-018, F-01 (60/min, `delay`), F-02 (30 Tage, getrennt, ab
letztem Versuch), F-03 (Replay nur Empfänger-Admin), F-04 (8 Versuche, 1 min,
×2, 60 min, ± 20 %) sowie O-07 (5 s + 10 s). Keiner dieser Werte wurde in
diesem Schritt verändert.

---

## 9. Was **nicht** zu beanstanden war

Damit der Bericht auch Bestand hat — geprüft und **in Ordnung**:

| Geprüft | Ergebnis |
|---|---|
| Vertragsform | OpenAPI 3.0.3, YAML gültig, 17 Operationen, alle mit eindeutigem `operationId`, `summary`, `description`, `security`, `x-ac-id` **und** `x-requirement-ids` |
| `$ref` | 157 Verweise, **alle** auflösbar; 33 Schemas, 31 referenziert (die zwei Ausnahmen sind V-09, bewusst dokumentiert) |
| Abdeckung | 23/23 Stories, 101/101 AKs, 24/24 Anforderungen auffindbar; `x-acceptance-coverage` und `x-requirement-coverage` ohne Dubletten und ohne unbekannte IDs |
| Zustandswerte ↔ Schema | `EndpointStatus`, `EventStatus`, `DeliveryStatus`, `AttemptOutcome`, `RejectionReason` stimmen **exakt** mit den CHECK-Constraints des Schemas überein (`schema.sql:80-81, 157-158, 205-207, 212-213, 259-261`) |
| Spaltennamen ↔ Vertragsfelder | 1:1: `attempt_count`, `max_attempts`, `next_attempt_at`, `last_attempt_at`, `last_response_status`, `last_error_code`, `rejection_reason`, `abandoned_at`, `attempt_id`, `attempt_number`, `started_at`, `finished_at`, `duration_ms`, `response_status`, `outcome`, `error_code`, `event_id`, `endpoint_id`, `delivery_run_id`, `secret_version`, `secret_rotated_at`, `paused_at` |
| Größengrenze | 1.048.576 Byte steht **fünfmal** gleich: NFR-001, Vertrag (`x-validation-rules` + Beschreibung), DB-CHECK (`schema.sql:162-166`), Fehlercode 413, Fehlertabelle — inklusive der korrekten Chiffre-Reserve (1.048.576 + 16 Byte GCM-Tag = 1.048.592) |
| Idempotenz | (Mandant, Schlüssel) ↔ `UNIQUE (organisation_id, idempotency_key)` ↔ gezielter HMAC-Fingerprint ↔ 202/409 — in Vertrag, Schema, ADR-007, Glossar und Domain-Modell **identisch** |
| „Kein Abnehmer" | Ereignis-Status, Lauf-Status, `endpoint_id IS NULL`, DB-CHECK, Glossar, Zustandsautomat, Vertrag (`EventStatus`, `DeliveryStatus`) — **konsistent** |
| Endgültige Zustände | `sent`/`abandoned`/`rejected`/`cancelled`/`no_subscriber` mit `next_attempt_at = NULL` in Schema-Constraint, ADR-006 §6 und Domain-Modell — **konsistent** (nur die *Prosa-Liste* im Vertrag ist unvollständig → V-07) |
| RLS | fail-closed, `current_setting(…, true)` ⇒ `NULL` ⇒ 0 Zeilen, **keine** Rolle mit `BYPASSRLS`, `FORCE ROW LEVEL SECURITY` bewusst nicht gesetzt, `SECURITY DEFINER` nur für die schmale Mandantenliste — **gegen echtes PostgreSQL 16 nachgewiesen** (ADR-002 „Verifikation“) |
| p95 < 200 ms (NFR-002) | Der Annahmepfad ist eine begrenzte Folge von Statements (kein Join, kein Roundtrip in den Worker, kein asynchroner Fremdaufruf), die Nutzlast ist auf 1 MB gedeckelt, die Zustellung läuft im Hintergrundprozessor. Der Zielwert ist **plausibel** erreichbar — die Messung selbst fehlt (N-12) |
| Worker-Kapazität | `Threads × (3600 s / 15 s)` ist korrekt gerechnet; die Lease muss länger sein als die Gesamtgrenze (ADR-008 §6) — konsistent |
| Backoff | monoton wachsend, nie in der Vergangenheit (`max(now(), geplant)`), deterministisch prüfbar (Seed), Streuung ≥ 1 s, Basisabstand (60 s) > Gesamtgrenze (15 s) — **widerspruchsfrei** |
| Signatur & Kennungen | Verfahren, Header-Name, Format `v1=<hex>`, Signier-Eingabe `<timestamp>.<payload_bytes>`, zeitkonstanter Vergleich, Zeitfenster 300 s — in Vertrag, ADR-004, ADR-005, ADR-009, Glossar und Sequenzdiagramm **identisch** |
| Ablehnungssignal | HTTP 410 + `X-EchoBox-Rejection: permanent` + JSON-Body — in Vertrag, ADR-009, Glossar, Domain-Modell und Schema (`rejection_reason`) **identisch** |
| „Ein Ort pro Wahrheit" | `.agent/knowledge/openapi.yaml` ist ein **reiner Verweis** (keine Pfade, keine Schemas) · das Schema-Spiegel ist **byte-identisch** (`diff` leer) · der ADR-Index deckt alle 18 Dateien ab (17 Entscheidungen + 1 Verweis) |
| Traceability | `python3 .agent/tools/traceability_check.py` → **grün** (keine Lücke) — mit der in N-10 beschriebenen Einschränkung |
| Schritt-3-Reduktion | `GET /api/v1/events` ist **wirklich** entfallen (keine Story, kein NFR, kein Akzeptanzpunkt), ADR-014 verweist **wirklich** nur noch, die 5 redundanten Tabellen in `architecture.md` sind **wirklich** weg |
| Diagramme | 5 Dateien, 4 C4-Ebenen + 1 Sequenz; der Sequenzablauf (Wiederholung, Pause, Replay) steht in keinem anderen Format; nach den Korrekturen dieses Schritts stimmen sie mit dem Text überein |

---

## 10. Selbstprüfung

| Prüfung | Befehl | Ergebnis |
|---|---|---|
| Traceability-Kette | `python3 .agent/tools/traceability_check.py` | **grün** — „Keine Luecken …“ (Exit 0) |
| Schema-Spiegel | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** (unverändert) |
| Vertrag unverändert | `git diff --stat -- docs/api/openapi.yaml` | **leer** |
| ADRs unverändert | `git diff --stat -- docs/adr/` | **leer** |
| Anforderungen unverändert | `git diff --stat -- docs/requirements/ docs/Anforderung.md` | **leer** |
| YAML gültig | `python3 -c "import yaml; yaml.safe_load(open('docs/api/openapi.yaml'))"` | **gültig** (unverändert) |
| YAML gültig (Architektur) | gleiche Prüfung für `docs/architecture/*.md` → keine YAML-Dateien; Mermaid-Dateien syntaktisch geprüft (ausgeglichene Klammern, gültige Bezeichner) | **ok** |
| Markdown | Struktur/Überschriften der geänderten Dateien | **ok** |
| Commit-Scope | `git add -A -- docs/architecture/ .agent/artifacts/` | **nur** diese beiden Pfade |

**Fazit für den Orchestrator:** Der Vertrag ist **nicht** einfrierreif. 22
Vertragsdefekte (4 blockierend) und ein Architekturbruch (S-01) verlangen einen
**Rücksprung zu Schritt 2**. E-01, E-02 und E-03 sind vor Schritt 5 zu
entscheiden; die 18 weiteren Vertragsdefekte (15 „sollte“, 3 „optional“) sind
Sammel-Patch-Material und können in derselben Runde mitgehen.
