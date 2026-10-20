# Phase 1 — Anforderungserhebung · Zusammenfassung

**Projekt:** PromptGuard — Zentrale Prompt-Verwaltung & Compliance-Prüfung
**Phase:** 1 (+ 1a Grill-Me) · **Agent:** `aisdlc-requirements` · **Skill:** `aisdlc-grill-stress`
**Stand:** 2026-10-20 · **Status:** `zur Freigabe` — Entscheidungen F1–F10 getroffen, menschliche
Freigabe des Artefaktsets steht aus
**Input:** `example/Anforderung/mail.txt` (Kunden-Mail, Alex, Head of Developer Tooling)

---

## 1. Ergebnis in einem Satz

Aus einer unstrukturierten Kunden-Mail wurde ein vollständig rückverfolgbares
Anforderungspaket: **97 Anforderungen**, **8 Epics**, **35 User Stories**, **108 Akzeptanzkriterien**
in Gherkin, **3 fachliche Invarianten**, **9 Zielkonflikte mit Auflösung**, **6 Risiken** — und
**10 beantwortete Entscheidungen**, die den Vertrag in Phase 2 einfrieren.

## 2. Erzeugte Artefakte

| Artefakt | Pfad | Umfang |
|---|---|---|
| **PRD** | `docs/requirements/PRD.md` | 19 Abschnitte, 9 Zielkonflikte, 8 Epics, 12 Annahmen |
| **User Stories (JSON)** | `docs/requirements/stories/US-001…US-035.json` | 35 Dateien, schema-konform (id, epic, requirement_ids, constraint_refs, acceptance_criteria, priority, status) |
| **BDD-Features (Gherkin)** | `docs/requirements/features/US-*.feature` | 35 Dateien, je Happy-Path / Rejection-Path / Edge-Case, getaggt mit Story-, Epic- und Anforderungs-IDs |
| **Traceability Matrix** | `docs/requirements/traceability_matrix.json` | 89 Einträge, 97 Gaps (Typ: no-test-yet / architecture-constraint / post-mvp), 6 manuelle Kriterien |
| **Anforderungskatalog** | `docs/requirements/artifacts/requirements.md` | alle 97 Anforderungen mit Story- und Status-Zuordnung |
| **User Stories (lesbar)** | `docs/requirements/artifacts/user-stories.md` | Epic → Story → AC-Tabelle |
| **Risiko-Register** | `docs/requirements/artifacts/risk-register.md` | Top 5 + akzeptiertes Restrisiko R-06 + Beobachtungspunkte + Frühwarnindikatoren |
| **Grill-Me Runde 1** | `docs/requirements/grill-me-round1.md` | F1–F10 mit Empfehlung **und** Entscheidung |
| **Entscheidungen (maschinenlesbar)** | `docs/requirements/open_decisions.json` | `status: resolved`, 10 Entscheidungen, 12 Annahmen, 3 Invarianten, 1 akzeptiertes Risiko |
| **Generator** | `docs/requirements/tools/generate_artifacts.py` | Single Source of Truth für Stories/Features/Matrix |

**Konsistenz (automatisiert geprüft):** PRD ↔ Matrix 97/97 ohne Differenz · jede Story hat
≥ 1 Anforderung und ≥ 1 vollständiges AC · jede Story ↔ genau ein Feature · alle 108 AC in der
Matrix · jede Story in ≥ 1 Matrix-Eintrag referenziert · A-/I-IDs ausschließlich in `constraint_refs`.

## 3. Getroffene Entscheidungen (Grill-Me Runde 1)

| # | Entscheidung | Abweichung |
|---|---|---|
| F1 | **Unredigierte Übermittlung** des Originalinhalts an den externen KI-Dienst | **Ja — bewusst** (Empfehlung war „redigiert") |
| F2 | 1 Organisation = 1 Abteilung; 1 Nutzer : 1 Organisation | nein |
| F3 | Security-Admin organisationsintern (`ORG_ADMIN` als Empfänger-Fallback) | nein |
| F4 | Nur `APPROVED`/`WAIVED` sind nutzbar; begründete Ausnahme durch `SECURITY_ADMIN` | nein |
| F5 | 90-Tage-Löschung nur für Logs/Telemetrie (Rohtelemetrie 24 h); Prompt-Inhalte bis zur Löschung | nein |
| F6 | Kategorien pflegt nur `ORG_ADMIN` | nein |
| F7 | Kein SSO/MFA im MVP; MFA-Einbaupunkt wird in Phase 2 per ADR vorbereitet | nein |
| F8 | Duplikatschwelle 0,90 + normalisierter Textvergleich, konfigurierbar, nie blockierend | nein |
| F9 | 80-%-Coverage ist harter CI-Blocker | nein |
| F10 | React 19 + Spring Boot 3 / Java 21 + PostgreSQL 16 bestätigt | nein |

### [SECURITY] F1 — was das für das System bedeutet

Die Empfehlung („redigiert") wurde überstimmt: **erkannte Secrets und personenbezogene Daten
verlassen das System** und werden vom externen Dienst verarbeitet. Die Plattform verteilt damit
genau die Inhalte, die sie selbst als Risiko markiert.

**Verbindliche Gegenmaßnahmen (MUSS, in Phase 2 umzusetzen, in Phase 8 nachzuweisen):**

1. Vertrag zur Auftragsverarbeitung (AVV) mit dem KI-Anbieter, inkl. Trainingsverbot und
   Löschgarantie — **FR-065, DP-005**.
2. **Provider-Verifikation vor dem ersten Aufruf:** schlägt die Zusage fehl, bricht der Scan ab
   und **kein Inhalt verlässt das System** — **FR-038, US-022**.
3. **Metadaten-Audit jeder Übermittlung** ohne Inhalt (Art. 30 DSGVO) — **FR-074 (neu)**.
4. Die lokale deterministische Vorprüfung bleibt **Pflicht** und fließt in das Gesamturteil ein —
   **FR-031**.
5. Aufnahme in die **Datenschutz-Folgenabschätzung**; Restrisiko als **R-06 akzeptiert** dokumentiert.

Betroffene Artefakte wurden vollständig umgeschrieben: FR-038, FR-065, FR-074 (neu), DP-002,
DP-005, SEC-006, FR-031, A-12, Glossar, US-019, US-022 (neu), US-034, Risiko-Register, DoD.

## 4. Was inhaltlich entschieden wurde (aus der Anforderung abgeleitet)

| Thema | Entscheidung | Begründung |
|---|---|---|
| **Stack** | React 19 + **Spring Boot 3 / Java 21** + PostgreSQL 16 | Der Kunde nennt **Langchain4j** — eine JVM-Bibliothek. Das Rahmen-„FastAPI *oder* Spring Boot" ist damit faktisch aufgelöst (CON-001). |
| **Prüfstatus** | `PENDING → SCANNING → APPROVED / FLAGGED / FAILED`, dazu `WAIVED` | Status hängt an der **Version**, nicht am Prompt — sonst wäre ein geänderter Prompt weiter „freigegeben". |
| **Datenschutz im Prüfprozess** | Lokale Vorprüfung + Provider-Auflagen statt Redaktion | Folge der Entscheidung F1. |
| **E-Mail-Inhalt** | Nur Metadaten + Deep-Link, **nie** Prompt-Inhalt oder Fundstellen | Sonst verteilt die Warnung genau die Daten, die sie melden soll. |
| **90-Tage-Regel** | Logs/Telemetrie/Rohtelemetrie (24 h), **nicht** Prompt-Inhalte | Sonst wäre das Register nach 90 Tagen leer. |
| **Duplikatprüfung** | Org-gebunden, 0,90, **nie blockierend** | Der Kunde sagt „Warnung"; ein globaler Index widerspräche der Isolation. |
| **Ausfallsicherheit** | Persistente Warteschlange, Backoff, Totmannschaltung, `FAILED` manuell wiederholbar | „Nicht abstürzen, sondern geordnet nachholen" — eine In-Memory-Queue würde bei Neustart Jobs verlieren. |

## 5. Gefundene Zielkonflikte (und ihre Auflösungen)

K-1 Asynchronität ↔ Sicherheit · K-2 Isolation ↔ Duplikatprüfung · K-3 90-Tage-Löschung ↔ Registry ·
K-4 Warn-E-Mail ↔ DSGVO · K-5 LLM-Prüfung ↔ Datenminimierung (**zugunsten Erkennungsqualität
entschieden — Restrisiko R-06**) · K-6 Ausfallsicherheit ↔ Fail-safe · K-7 Langchain4j ↔ Framework-Stack ·
K-8 80 % Coverage ↔ KI-Testbarkeit · K-9 Realtime-UI ↔ stateless Betrieb.
— Vollständig in `PRD.md` §15.

## 6. Top-Risiken

R-01 Mandantenleck (kritisch) · R-02 Fehlalarme (hoch) · R-03 Prüfstau (hoch) ·
R-04 kompromittierter Prüfer (kritisch) · R-05 ignorierter Alarm (hoch) ·
**R-06 unredigierte Übermittlung (hoch/hoch — akzeptiert, F1)**.
Gegenmaßnahmen sind in konkrete Anforderungs-IDs verankert.

## 7. Fakten, die selbst ermittelt wurden (nicht erfragt)

- **Greenfield:** `src/` leer, `tests/` nur leere Ordnungen, keine Build-Dateien, kein `infra/`,
  `docs/api/` und `docs/adr/` leer. Betroffene Module entstehen komplett neu.
- **Vorlagen geprüft:** `.agent/knowledge/stack.md` (Stack-Defaults), `.agent/knowledge/schema.sql`
  und `.agent/knowledge/openapi.yaml` (Template — von Phase 2 zu ersetzen),
  `.agent/artifacts/*.md` (Stub-Dateien).
- **Referenz-Application:** `example/taskflow.md` — Formatvorbild für PRD/Features/Matrix/ADR-Reihenfolge.

## 8. Abweichungen und Hinweise

| # | Sachverhalt | Behandlung |
|---|---|---|
| 1 | Die Workflow-Datei `01-requirements.md` nennt `.agent/artifacts/` als Zielordner, die Rechte-Matrix in `AGENTS.md` erlaubt dem Requirements-Orchestrator aber **ausschließlich `docs/requirements/`**. | **Rechte-Matrix hat Vorrang** (AGENTS.md Regel 3/4, `how-to.md`: „Ein Agent darf nur in seine zugewiesenen Verzeichnisse schreiben"). Die Artefakte liegen unter `docs/requirements/artifacts/`. `.agent/artifacts/*.md` wurden **nicht** überschrieben. |
| 2 | Beim ersten Generator-Lauf entstand durch einen Pfadfehler versehentlich `docs/docs/requirements/` (71 Dateien). | Verzeichnis entfernt (nur Dateien aus diesem Lauf), Generator korrigiert, Output-Verifikation über Konsistenzcheck. |
| 3 | Nach der Umbenennung von US-022 (Slug `redaction-before-llm` → `external-provider-transfer`) blieb die alte Feature-Datei zurück (36 Dateien für 35 Stories). | Verwaiste Datei entfernt — eigenes Artefakt desselben Schritts, das die Story widersprüchlich doppelt abgebildet hätte. |
| 4 | `FR-017` („Prompt kopieren") ist `SOLL`, hat aber bewusst **keine Story** — es liegt in §6.3 (Post-MVP). | In der Matrix als Gap-Art `post-mvp` geführt, `blocks_gate_1: false`. |
| 5 | 7 Architektur-Randbedingungen (`CON-001…004`, `006`, `007`, `010`) haben keine User Story. | Als Gap-Art `architecture-constraint` geführt — Nachweis in Phase 2 über ADR und `openapi.yaml`, nicht über Tests. |
| 6 | `CON-009` und `CON-008` haben sehrwohl Stories (US-016, US-031), weil sie beobachtbares Verhalten haben (Job überlebt Neustart; Gate schlägt fehl). | Keine Sonderbehandlung nötig. |

## 9. Empfohlener nächster Schritt

1. **Menschliche Freigabe** des Phase-1-Artefaktsets (Gate laut `.agent/workflows/01-requirements.md`).
2. **Ein Commit für Schritt 1** (`docs(sdlc-1): …`) — der Commit entsteht vor dem Gate.
3. Phase 2 (Architecture Orchestrator): `docs/api/openapi.yaml`, `docs/architecture/domain_model.md`,
   C4-Diagramme, ADR-001…ADR-008, `.agent/knowledge/schema.sql`.
   **ADR-006 muss die unredigierte Übermittlung, den AVV und die Provider-Verifikation abbilden.**
4. Parallel: Datenschutz-Folgenabschätzung um **R-06** ergänzen und den AVV mit dem KI-Anbieter
   abschließen.

**Gate-Status: Phase 1 zur Freigabe bereit.** Offene Entscheidungen: keine.
