# Reduktionsbericht — Schritt 3 (Simplify & Reduktion)

> Schritt 3 · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Eingang: Schritt 2 (Commit `7422cae`, `docs/sdlc-2): API-Contract, Domain-Modell
> und ADRs`), Gate 1 am 2026-10-21 freigegeben.
> Skill: `aisdlc-simplify` (Schritt 3 der AI-SDLC).

## 0. Kurzfassung

| Kennzahl | vorher | nachher |Bemerkung |
|---|---|---|---|
| Vertragsoperationen | 18 | **17** | `GET /api/v1/events` entfallen (kein Abnehmer) |
| ADRs mit eigener Entscheidung | 18 | **17** | ADR-014 in ADR-008 zusammengeführt; ADR-014 bleibt als Verweisdatei |
| Zeilen `docs/architecture/architecture.md` | 376 | **278** | eine Erzählung + Verweise statt Tabellen-Duplikate |
| Zeilen `docs/architecture/domain_model.md` | 275 | **227** | Fachbegriffe an eine Stelle (Glossar) |
| Redundante Entscheidungstabellen in `architecture.md` | 5 | **0** | §4–§8 verweisen jetzt auf die ADRs |
| Stories / AKs / Anforderungen | 23 / 101 / 24 | **23 / 101 / 24** | **unverändert** — Abdeckung intakt |
| Offene Punkte O-01…O-07 | 5 offen | **2 offen** (O-04, O-05) | F-01…F-04 eingetragen |
| Tabellen in `docs/architecture/schema.sql` | 10 | **10** | Schema unverändert (Begründung in §7) |

**Nichts von der Abdeckung ist entfallen.** Jede Story, jedes
Akzeptanzkriterium und jede Anforderung ist weiterhin im Vertrag auffindbar —
maschinell geprüft (§8).

---

## 1. Was überdimensioniert war

Der Deletion-Test des Skills: *Weglassen, was keine Story, kein NFR und keine
ADR-Bindung hat.* Ergebnis — drei Fundstellen, alle im Dokumentations- und
Vertragsbereich, **keine** im Datenmodell:

| # | Fundstelle | Diagnose | Beleg |
|---|---|---|---|
| 1 | `GET /api/v1/events` (`listEvents`) | trug **keine** Story, **kein** NFR und **keinen** Akzeptanzpunkt, der nicht auch anderswo realisiert wird | die einzige Zuordnung war AC-007-5 — und der spricht vom *Zustellprotokoll*, nicht von einer Ereignisliste; keine der drei Oberflächenseiten braucht sie; `docs/requirements/` enthält keinen Verweis auf eine Ereignisliste |
| 2 | ADR-014 neben ADR-008 | zwei ADRs variieren **dieselbe** Entscheidung: das Verhalten des **fehlgeschlagenen Zustellversuchs** (wie oft, wie weit auseinander, wie lange) | ADR-014 verweist selbst auf ADR-008 („Verhältnis zum Backoff“); ADR-006 verweist auf „das Zeitlimit je Versuch (15 s, ADR-014)“ |
| 3 | `docs/architecture/architecture.md` §4–§8 | **dieselben** Inhalte wie die ADRs (Mandantentrennung, Verschlüsselung, Betrieb, Fehlersemantik, verworfene Optionen) — eine zweite, kürzere Wahrheit | ADR-002, ADR-003, ADR-010, ADR-012, ADR-013, ADR-014, ADR-015 enthalten dieselben Festlegungen mit Begründung und Alternativen |

Zwei weitere Verdachtsstellen wurden geprüft und **nicht** angetastet:

| Verdacht | Prüfung | Ergebnis |
|---|---|---|
| `docs/architecture/GLOSSARY.md` **und** `domain_model.md` §1 — zwei Orte für die Fachbegriffe? | beide Dateien definierten Endpunkt, Ereignis, Zustellung, Zustelllauf, tote Zustellung | **bestätigt** → konsolidiert (§3) |
| Schema-Spiegel `.agent/knowledge/schema.sql` + `docs/architecture/schema.sql` — zwei Wahrheiten? | ADR-016: das Schema hat **eine** kanonische Quelle plus eine byte-identische versionierte Kopie | **gewollt**, bleibt unverändert |

---

## 2. Was entfernt wurde

### 2.1 `GET /api/v1/events` (Operation `listEvents`)

| | |
|---|---|
| **Entfernt** | Operation `listEvents`, Schema `EventList`, Filter `event_type` / `idempotency_key` / `status` / `from` / `to` der Ereignisliste |
| **Bleibt** | `GET /api/v1/events/{eventId}` (ein einzelnes Ereignis, Metadaten) |
| **Warum kein Verlust entsteht** | Die Suche über Ereignis-Metadaten (AC-007-5: „Ereignis-ID, Typ, Endpunkt, Status, Zeitraum“) ist eine **Filterfrage des Zustellprotokolls** — `GET /api/v1/deliveries` trägt genau diese Filter (`event_id`, `event_type`, `endpoint_id`, `status`, `from`, `to`). Jedes eingelieferte Ereignis ist über seinen Zustelllauf auffindbar, **auch** ein Ereignis ohne Abnehmer (Zustand `no_subscriber`, D-04). |
| **Folge** | ein Controller weniger, ein DTO-Paar weniger, ein Vertragstest weniger, ein Filter-Durchlauf weniger |
| **Dokumentiert in** | ADR-017 §4 (Reduktionshinweis), `api-review.md` §3, `architecture.md` §3, `folder-structure.md` (§2.2 `EventController`) |

### 2.2 Redundante Tabellen in `docs/architecture/architecture.md`

Die Abschnitte „Mandantentrennung“, „Verschlüsselung im Ruhezustand“,
„Betrieb“, „Fehlerbehandlung“ und „Entwurfsoptionen, die verworfen wurden“
wurden durch **Verweise auf die ADRs** ersetzt — eine Zeile pro Thema, plus
die zwei Regeln, die man sich merken muss (403 = Rolle, 404 = Mandant;
Body = exakt die Nutzlastbytes). Die Begründungen, Alternativen und
Konsequenzen stehen **einmal** in den ADRs.

---

## 3. Was zusammengefasst wurde

| Was | Vorher | Nachher | Warum |
|---|---|---|---|
| **ADRs** | ADR-008 (Wiederholung/Backoff/Streuung) + ADR-014 (Zeitlimit je Versuch) | **ADR-008 „Wiederholung, Backoff, Streuung und Zeitlimit je Versuch“**; ADR-014 bleibt als **Verweisdatei** (kein Löschen ohne menschliche Freigabe) | beide variieren dieselbe Entscheidung; die Nummerierung wird **nicht** umgestellt — eine Umnummerierung würde jeden Verweis in Vertrag, Architektur und Artefakten brechen, ohne eine Entscheidung zu gewinnen |
| **Fachbegriffe** | `GLOSSARY.md` **und** `domain_model.md` §1 | **Glossar ist der einzige Ort** je Begriff; `domain_model.md` §1 verweist darauf und behält nur die zwei Begriffe, die man beim Lesen des Zustandsautomata braucht | eine Stelle je Begriff (ADR-016, `aisdlc-glossary-adr`) |
| **Architektur-Volltext** | 376 Zeilen mit fünf Tabellen-Duplikaten | **278 Zeilen**: eine Erzählung + Verweise | Klarheit vor Vollständigkeit |
| **Kurzfassung `folder-structure.md`** | eigener „Planungsauftrag an OpenCode“ mit Zielpfad-Tabelle | Verweis auf `docs/architecture/folder-structure.md` §5 | eine Wahrheit für den Plan |

---

## 4. Was geblieben ist — und warum

| Element | Bleibt | Begründung |
|---|---|---|
| 17 ADRs (ADR-001…ADR-013, ADR-015…ADR-018) | ja | 17 **unabhängige** Entscheidungen; jede hat eigene Konsequenzen. Eine weitere Zusammenfassung (z. B. ADR-003 + ADR-017) würde fachfremde Themen in eine ADR ziehen — die Suche/Paginierung ist keine Verschlüsselungsentscheidung. |
| 10 Tabellen im Schema, 10 RLS-Policies, 33 Indizes | ja | jede Tabelle trägt ein fachliches Attribut; `docs/architecture/schema.sql` ist die byte-identische Kopie des kanonischen Schemas (ADR-016) |
| `GET /api/v1/events/{eventId}` | ja | AC-015-3 (fremdes Ereignis ⇒ 404) und der Einzelabruf des Ereignisses |
| `x-delivery-protocol` | ja | eingefrorener Fremdvertrag (FR-020, D-07, D-08) — eine Stelle, nicht drei |
| `x-acceptance-coverage` (101) und `x-requirement-coverage` (24) | ja | Nachweis der Abdeckung; jeder Eintrag zeigt **wo** |
| 5 Diagramme (4 C4 + 1 Sequenz) | ja | jede Ebene beantwortet eine andere Frage; der Sequenz-Ablauf (Wiederholung, Pause, Replay) steht in keinem anderen Format |
| `GLOSSARY.md` | ja | einziger Ort je Fachbegriff, mit Negation und Quelle |
| `domain_model.md` (Entitäten, Zustandsautomat, Invarianten) | ja | der Zustandsautomat ist die **fachliche** Wahrheit; keine andere Datei führt ihn |

---

## 5. Berührte Story / AK / Anforderung

**Die Abdeckung ist intakt.** Die Reduktion hat genau **ein** Akzeptanzkriterium
berührt und **keine** Anforderung und **keine** Story entfernt.

| ID | Vorher realisiert in | Nachher | Warum die Abdeckung intakt bleibt |
|---|---|---|---|
| **AC-007-5** (Suche über Metadaten, keine Suche nach Nutzlastinhalt) | `listEvents` + `getEvent` | **`listDeliveries` + `getEvent`** | das AK spricht vom **Zustellprotokoll** („im Zustellprotokoll nach dem Ereignis gesucht wird“); `listDeliveries` trägt die Filter Ereignis-ID, Ereignistyp, Endpunkt, Status, Zeitraum — genau die geforderten Metadaten. Die Aussage „eine Suche nach Nutzlastinhalt existiert nicht“ bleibt unverändert im Vertrag (`info.description`, ADR-017). |
| **FR-012** (Verschlüsselung im Ruhezustand) | `getEvent`, `listEvents`, `submitEvent` | `getEvent`, `listDeliveries`, `submitEvent` | die Anforderung ist „Nutzlast verschlüsselt speichern, nur zur Zustellung entschlüsseln, nicht im Klartext lesbar“ — das betrifft **keine** Listenoperation, sondern den Speicherpfad. Die Zuordnung ist unverändert, nur der Listen-Eintrag zeigt jetzt auf die Metadatensuche, die es ohnehin trägt. |
| **AC-018-4** (kein vorbelegter Grenzwert, solange O-01 offen) | `getEndpointMetrics` | `getEndpointMetrics` — Wortlaut angepasst | das AK war an den **offenen** Punkt O-01 gekoppelt. Mit F-01 ist der Grenzwert entschieden: `rate_limit.limit` weist den **wirksamen** Grenzwert aus (Vorgabe 60). Die im AK geforderte **Zählung** je Endpunkt und Zeitfenster läuft unverändert und ist weiterhin im Vertrag ausgewiesen. Die Datei `docs/requirements/` gehört Schritt 1 und wurde **nicht** angefasst — die Auflösung steht hier und in ADR-010. |
| **AC-016-5** (Aufbewahrung, Frist konfigurierbar) | `enforceRetention` | `enforceRetention` — **Antwortform** präzisiert | F-02 legt **zwei** Fristen fest (Ereignisse, Zustellprotokolle). Die alte Antwort nannte **eine** Frist und **einen** Stichtag — das war mit zwei Fristen nicht darstellbar. Die Antwort nennt jetzt `event_retention_days` / `delivery_retention_days` und `event_cutoff` / `delivery_cutoff`. Das AK selbst ist unverändert und weiterhin genau an dieser Operation realisiert. |
| **AC-011-1, AC-011-2, AC-012-1** (Backoff, Streuung, Versuchsgrenze) | `x-delivery-protocol.retry_policy` | dito — nur die **Vorgabewerte** sind im Text genannt (F-04) | die Mechanik (Verdopplung, volle Streuung, Grenze je Lauf eingefroren) ist unverändert; neu ist nur, **mit welchem Vorgabewert** sie startet. |
| **AC-013-3** (Replay ohne Berechtigung ⇒ 403) | `replayDelivery` | dito — Berechtigung final benannt (F-03) | das AK bleibt unverändert; der Vertrag benennt jetzt die **entschiedene** Berechtigung statt der vorläufigen (D-15). |
| **23 Stories, 101 AKs, 24 Anforderungen** | — | **unverändert** | maschinell geprüft: alle 101 AK-IDs aus `docs/requirements/stories/*.json` sind im Vertrag auffindbar, alle 24 Anforderungs-IDs aus der PRD ebenfalls, alle 23 Stories vertreten. `x-acceptance-coverage` hat 101 eindeutige Einträge, `x-requirement-coverage` 24. |

---

## 6. Wo die Entscheidungen jetzt stehen

| Festlegung | Wert | ADR | Vertrag | Architektur |
|---|---|---|---|---|
| **F-01** Rate-Limit | 60 Versuche/Endpunkt/Minute, `delay` | ADR-010 §3, §„Festlegung“ | `x-delivery-protocol.rate_limit` (`default_limit: 60`), `RateLimitObservation.limit`, `info.description` | `architecture.md` §4; `domain_model.md` §3.1 (`delayed`) |
| **F-02** Aufbewahrung | je 30 Tage, getrennt, ab letztem Versuch | ADR-012 §1, §2, §6 | `enforceRetention` (Beschreibung + `RetentionEnforcementResponse`), `info.description` | `architecture.md` §4; `GLOSSARY.md` „Aufbewahrung“ |
| **F-03** Replay-Berechtigung | nur Empfänger-Admin | ADR-015 §1, §„Festlegung“ | `replayDelivery` (Beschreibung), `x-contract-rules.defaults` | `architecture.md` §4; `domain_model.md` §3.3/§4 |
| **F-04** Versuche/Backoff | 8 / 1 min / ×2 / 60 min / ± 20 % | ADR-008 §7 | `x-delivery-protocol.retry_policy.configuration`, `DeliveryRun.max_attempts` | `architecture.md` §3.2 |
| O-07 Zeitlimit | 5 s + 10 s = 15 s | ADR-008 §6 | `x-delivery-protocol.retry_policy.attempt_timeout` | `architecture.md` §3.2 |
| O-04 / O-05 | **offen** (Gate 2) | ADR-003, ADR-012, ADR-017 | — | `architecture.md` §7 |

---

## 7. Bewusst **nicht** geändert

| Bereich | Warum nicht |
|---|---|
| `.agent/knowledge/schema.sql` und `docs/architecture/schema.sql` | Die bestätigten Werte sind **Vorgabewerte der Konfiguration**; das Schema speichert bewusst **keine** vorbelegten Grenz- oder Fristwerte (nur je Endpunkt bzw. Mandant **Overrides**, `NULL` = „kein Override“). Die Semantik des Schemas ändert sich also **nicht**. Die Kommentare im Schema nennen noch O-01/O-02 — eine reine Textänderung, die nach der Spiegel-Regel **beide** Dateien im selben Schritt ändern müsste; `.agent/knowledge/` gehört nicht zum Commit-Scope dieses Schritts. **Nachzuholen in Schritt 6**, wenn die Flyway-Migration `V1__initial_schema.sql` entsteht (dort sind beide Dateien ohnehin im selben Schritt). |
| `.agent/knowledge/stack.md` | dort steht noch „kein vorbelegter Wert, solange O-02 offen ist“. Auch das ist eine reine Textzeile — die Datei liegt aber außerhalb des Commit-Scopes dieses Schritts und würde sonst als uncommittete Fremdänderung im Arbeitsbaum liegen. **Nachzuholen in Schritt 6** (dort entsteht ohnehin die Flyway-Migration und wird das Stack-Wissen nachgezogen). |
| `docs/requirements/**` (PRD, Stories, Features, Traceability-Matrix) | Ergebnisbereich von Schritt 1. Die Auflösung von O-01/O-02/O-03/O-06 steht deshalb **hier** und in den ADRs — so, wie es das Briefing verlangt. |
| `docs/Anforderung.md` | Roh-Input; unverändert. |
| `src/`, `tests/` | noch kein Anwendungscode (Schritt 6). |
| Der Vertrag wird **nicht eingefroren** | Der Einfrierpunkt kommt **nach Schritt 4** (AGENTS.md). Der Vertrag ist in diesem Schritt geändert worden — das war erlaubt und nötig (F-01…F-04 sichtbar machen, eine Operation entfernen). |
| ADR-003 + ADR-017, ADR-006 + ADR-013, ADR-010 + ADR-011, ADR-015 + ADR-004 | geprüft und **nicht** zusammengefasst: es sind jeweils **verschiedene** Entscheidungen. Suche/Paginierung ist keine Verschlüsselungsentscheidung; Kennzahlen sind keine Begrenzungsentscheidung. |
| Nummerierung der ADRs | nicht umgestellt (siehe §3) — eine Umnummerierung bricht Verweise, ohne eine Entscheidung zu gewinnen. |

---

## 8. Prüfungen

| Prüfung | Befehl | Ergebnis |
|---|---|---|
| Traceability-Kette | `python3 .agent/tools/traceability_check.py` | **grün** — keine Lücke |
| Schema-Spiegel | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** |
| Vertrag: YAML | `python3 -c "import yaml; yaml.safe_load(...)"` | **gültig** |
| Vertrag: Operationen | Zählung über `paths` | **17**, alle mit `operationId`, `summary`, `description`, `responses`, `x-ac-id`, `x-requirement-ids` |
| Vertrag: `$ref` | Auflösung aller 157 Verweise | **alle auflösbar** |
| Vertrag: ungenutzte Schemas | 33 Schemas, alle referenziert | **keine** |
| Vertrag: Abdeckung | 101 AK-Einträge, 24 Anforderungs-Einträge, 23 Stories | **vollständig, keine Dubletten, keine unbekannten IDs** |
| Artefaktpfade | alle Dateien in `docs/architecture/`, `docs/api/`, `docs/adr/`, `.agent/artifacts/` existieren | **ok** |

---

## 9. Offen für Schritt 4 (Stresstest)

1. **Die Wirkung der Vorgabewerte auf die Antwortfelder** — sind `limit`,
   `max_attempts` und die beiden Fristen nullable genug, um „kein Override“
   und „abgeschaltet“ abzubilden?
2. **Der Weg zum Ereignis seit der Entfernung der Ereignisliste** — reicht der
   Filter `event_id` / `event_type` in `GET /api/v1/deliveries` für alle
   Betriebsfragen?
3. **Die Aufbewahrungsantwort** — zwei Fristen, zwei Stichtage, eine
   `dry_run`-Semantik: ist das für den Service-Admin lesbar?
4. **O-04 / O-05** bleiben fachliche Fragen (Gate 2) — sie sind im Vertrag
   bereits so abgesichert, dass die Antwort des Fachbereichs die API nicht
   verändert.
