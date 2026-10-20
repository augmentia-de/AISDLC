# PromptGuard — DSGVO-, Aufbewahrungs- und Löschkonzept

| Feld | Wert |
|---|---|
| **Dokument** | DSGVO, Aufbewahrung, Recht auf Löschung, Audit-Trail |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Bezug** | US-028, US-029, US-030, FR-060…FR-065, DP-001…DP-008, ADR-004, ADR-006 |

> **[SECURITY] F1 / R-06:** Der Originalinhalt eines Prompts wird **unredigiert** an den
> externen KI-Dienst übermittelt. Das ist eine bewusste Entscheidung des Product Owners.
> Dieses Dokument beschreibt, **wie** das Restrisiko begrenzt und nachgewiesen wird.

---

## 1. Rollen und Verantwortlichkeiten

| Rolle | Verantwortung | Beleg |
|---|---|---|
| **Verantwortlicher** | die Organisation (der Mandant) | DP-001 |
| **Auftragsverarbeiter** | PromptGuard (Plattformbetrieb) | AVV mit den Kunden |
| **Unterauftragsverarbeiter** | KI-Anbieter (Gemini/OpenAI, EU) | **AVV** mit dem KI-Anbieter — Pflicht (DP-005) |
| **Plattform-Betrieb** | Betrieb ohne fachlichen Datenzugriff | O7, F3 |
| **Datenschutzbeauftragte:r** | Prüft Auflagen, Verifikation, Löschvorgänge, Nachweise | US-022, US-030 |

---

## 2. Rechtsgrundlage und Zweck (DP-001)

| Zweck | Rechtsgrundlage | Dokumentiert im Datenschutzhinweis |
|---|---|---|
| Prüfung von Prompts auf Injection/Jailbreak/Secrets/PII | **berechtigtes Interesse** (Art. 6 Abs. 1 lit. f) **oder** interne Compliance-Pflicht | ja |
| Nachweisführung über Freigaben und Löschungen | **berechtigtes Interesse** (Art. 6 Abs. 1 lit. f) | ja |
| Authentifizierung und Berechtigungsprüfung | **Vertragserfüllung** (Art. 6 Abs. 1 lit. b) | ja |
| Betriebssicherheit (Logs, Metriken) | **berechtigtes Interesse** | ja |

Der Zweck wird **im Datenschutzhinweis** benannt (DP-001, DP-006) und ist in beiden Sprachen
veröffentlicht (FR-064).

---

## 3. Aufbewahrungsmatrix

| Klasse | Objekte | Frist | Löschung | Beleg |
|---|---|---|---|---|
| **Fachinhalt** | `prompt`, `prompt_version`, `scan_finding.redacted_excerpt`, `waiver.reason` | **unbegrenzt** bis zur expliziten Löschung | `DELETE /prompts/{id}` bzw. Recht auf Löschung | A-03, F5, FR-060 |
| **Verdichtetes Ergebnis** | `scan_run` (ohne Rohpayload), Status, Schwellen, maskierte Fundstellen | unbegrenzt, solange der Prompt existiert | Kaskade mit dem Prompt | FR-061 |
| **KI-Rohpayload** | `scan_raw_payload` | **24 Stunden** | eigener, stündlicher Job | FR-061, DP-003 |
| **Telemetrie** | Request-/Access-Logs, `notification_delivery`-Protokolle, `transfer_audit`, `audit_event` | **90 Tage** | täglicher Retention-Job | FR-060, DP-003 |
| **Sitzungen** | `refresh_token` | 15 min (Access) / 30 Tage (Refresh, rotiert) | Ablauf + Kette-Invalidierung | FR-007 |
| **Qualitätsnachweise** | `quality_gate_attestation` | bis zum nächsten Release | Betrieb | US-031 |

> **Auslegungshinweis (Befund B-08):** FR-060 nennt „Audit-Einträge **mit Inhaltsbezug**".
> Da Audit-Einträge **per Konstruktion inhaltsfrei** sind (FR-062), wird der Begriff als
> „Audit-Einträge, deren Aufbewahrung an den Inhalt gekoppelt ist" ausgelegt — praktisch
> die gesamte Audit-Tabelle. Die Auslegung ist im Datenschutzhinweis zu benennen und mit
> der DPO zu bestätigen.

---

## 4. Löschungsjob (US-028, FR-060)

| Job | Takt | Wirkung | Idempotenz |
|---|---|---|---|
| `RetentionJob` | täglich 03:00 UTC | löscht Telemetrie > 90 Tage, Rohpayloads > 24 h | **ja** — zweiter Lauf löscht `0` und meldet `0` ohne Fehler (AC-028-2) |
| `RawPayloadJob` | stündlich | löscht KI-Rohpayloads > 24 h | ja |
| `OrphanCleanup` | täglich | verwirft verwaiste Jobs/Versionen ohne Mandanten-Kontext (SEC-003) | ja |

**Fehlerverhalten (AC-028-3):** Schlägt ein Löschvorgang fehl, endet der Lauf mit
`status: FAILED`, wird erneut versucht und löst einen Alarm aus. Die Löschungen laufen **je
Objektgruppe in einer eigenen Transaktion** — ein Fehler rollt nur diese Gruppe zurück, es
bleibt **kein** teilweiser oder inkonsistenter Zustand.

**Nachweis:** Jeder Lauf schreibt eine Zusammenfassung der gelöschten Mengen (Anzahl je
Klasse, Zeitfenster) in die Logs und in `audit_event` (`RETENTION_RUN`) — **ohne Inhalt**.
Abrufbar über `GET /ops/retention` (Befund in Phase 5/6 umzusetzen).

**Was NICHT gelöscht wird:** `prompt`, `prompt_version`, `scan_run`, `scan_finding` —
Inhalte bleiben bis zur expliziten Löschung (A-03, F5, K-3).

---

## 5. Recht auf Löschung (US-029, FR-063, DP-004, DP-007)

### 5.1 Zwei Wege

| Weg | Operation | Auslöser |
|---|---|---|
| **Betroffener selbst** | `DELETE /api/v1/me` | Der Nutzer löscht sein Konto |
| **ORG_ADMIN** | `DELETE /api/v1/users/{userId}` | Die Organisation veranlasst die Löschung |

### 5.2 Wirkung

| Objekt | Wirkung |
|---|---|
| `app_user`, `membership` | **gelöscht** |
| `refresh_token` | **gelöscht** (alle Sitzungen) |
| `invitation` des Nutzers | **gelöscht** |
| `prompt.created_by`, `prompt_version.created_by` | **anonymisiert** (`NULL`) — der Prompt-Bestand bleibt bestehen |
| betroffene `prompt_version` | Status `APPROVED|FLAGGED|WAIVED → PENDING`; **erneute Prüfung** eingereiht (der Prompt wird ohne personenbezogenen Autor neu bewertet) |
| `waiver.waived_by` | `NULL` (anonymisiert) |
| `audit_event.actor_id`, `actor_email` | `NULL` — **Zeitpunkt und Aktion bleiben** (AC-029-3) |
| `scan_finding` | bleibt am Inhalt des Prompts (Fachinhalt gehört der Organisation) |

### 5.3 Begründung der Anonymisierung (Befund B-09)

Der **fachliche Inhalt** eines Prompts gehört der Organisation, nicht der Person, die ihn
verfasst hat. Eine physische Löschung der Versionen würde:

* die Nachvollziehbarkeit des Registers zerstören (FR-013, Zweck des Produkts),
* die Historie (`status_transition`, `audit_event`) sinnlos machen,
* die Pflicht zur Nachweisführung (Art. 30) unterlaufen.

Die Anonymisierung entfernt die **Person** vollständig und behält den **Inhalt**. Das ist
eine rechtliche Abwägung, die mit dem Product Owner **und der DPO zu bestätigen** ist
(Befund B-09 in `.agent/artifacts/api-review.md`). Die erneute Prüfung nach Anonymisierung
bewahrt zusätzlich die Trennung von Person und Freigabe.

### 5.4 Auskunft, Berichtigung, Export, Widerspruch

| Recht | Umsetzung | Beleg |
|---|---|---|
| **Auskunft** | `GET /api/v1/me`, `GET /api/v1/me/export` | DP-007, DP-008, US-029 |
| **Berichtigung** | `PATCH /api/v1/me` (Anzeigename, E-Mail, Sprache) | DP-007 |
| **Export (maschinenlesbar)** | `GET /api/v1/me/export` → JSON | DP-008, AC-029 |
| **Widerspruch** | organisatorisch: Widerruf der Einladung (`revokeInvitation`) und Entzug des Zugangs (`removeOrganisationMember`) | DP-007 — im MVP **ohne** eigene Operation |
| **Löschung** | 5.1 | DP-004 |

---

## 6. Übermittlung an den KI-Dienst (F1, DP-002, DP-005, FR-038, FR-065, FR-074)

### 6.1 Der akzeptierte Punkt

Der Originalinhalt eines Prompts wird **unredigiert** an den externen KI-Dienst
übermittelt. Erkannte Secrets und personenbezogene Daten verlassen damit das System
(R-06). Der Product Owner hat das bewusst entschieden (F1).

### 6.2 Pflichtmaßnahmen — ohne sie ist die Übermittlung unzulässig

| Maßnahme | Anforderung | Technische Durchsetzung |
|---|---|---|
| **Vertrag zur Auftragsverarbeitung (AVV)** | FR-065, DP-005 | `AI_PROVIDER_AVV_REFERENCE` muss gesetzt sein; ohne AVV ⇒ Abbruch |
| **EU-Region `europe-west3`** | FR-065, DP-005 | Laufzeitprüfung des Endpunkts; ohne EU ⇒ Abbruch |
| **Trainingsverzicht** | FR-065, DP-002 | `AI_PROVIDER_TRAINING_OPT_OUT=true`; sonst ⇒ Abbruch |
| **Keine dauerhafte Speicherung** | FR-065, DP-002 | `AI_PROVIDER_RETENTION=zero`; sonst ⇒ Abbruch |
| **Provider-Verifikation als Gate** | FR-038, AC-022-2 | `ProviderVerification` prüft **vor jedem Aufruf**; bei Verstoß **kein** Aufruf, Version → `FAILED` mit `PROVIDER_POLICY_VIOLATION` |
| **Metadaten-Audit je Übermittlung** | FR-074 | `transfer_audit`: Zeitpunkt, Provider, Modell, Zeichenlänge, Ergebnis-ID, Korrelations-ID — **ohne Inhalt** |
| **Lokale Vorprüfung bleibt Pflicht** | FR-031, F1 | Stufe (a) läuft **vor** dem externen Aufruf und fließt in das Gesamturteil ein |
| **Rohpayloads ≤ 24 h** | FR-061, DP-003 | `scan_raw_payload.expires_at`, stündlicher Job |
| **Kein Inhalt im Plattform-Log** | FR-062, DP-002 | zentrale Log-Konfiguration + automatischer Test (AC-032-2) |

### 6.3 Was minimiert wird — innerhalb der akzeptierten Grenze

Der Provider erhält **nur** den zu klassifizierenden Text der Version:

* **keinen** Mandantenkontext und **keinen** Organisationsnamen,
* **keinen** Prompt-Titel und **keine** Beschreibung,
* **keine** Nutzerkennungen, E-Mail-Adressen oder Rollen,
* **keine** anderen Prompts, keine Audit-Einträge, keine Metadaten der Organisation,
* **keine** Tokens und **keine** Plattform-Secrets.

> **Kein Inhalts-Hash im Übermittlungsnachweis:** Ein Hash des Inhalts wäre ein ableitbarer
> Fingerabdruck und würde das Nachweis-Ziel (Metadaten ohne Rückbezug auf den Inhalt)
> unterlaufen. Deshalb wird **nur die Zeichenzahl** protokolliert.

### 6.4 Status des Risikos

| Feld | Wert |
|---|---|
| Risiko | R-06 — unredigierte Übermittlung |
| Likelihood | **hoch** (bei secret-haltigen Prompts) |
| Impact | **hoch** |
| Status | **akzeptiert** durch den Product Owner (2026-10-20) |
| Nachweis | AVV, EU-Region, Trainingsverbot, Löschgarantie, Provider-Gate, Metadaten-Audit, lokale Vorprüfung |
| Fortschreibung | Aufnahme in die **Datenschutz-Folgenabschätzung**; Wiedervorlage bei Vertragsänderung beim KI-Anbieter |

---

## 7. Datenschutzhinweis (US-030, FR-064, DP-006)

| Anforderung | Umsetzung |
|---|---|
| **Zwei Sprachen** | `GET /api/v1/privacy-notice` liefert beide Sprachen in einer Antwort |
| **Öffentlich erreichbar** | `security: []` — auch ohne Anmeldung |
| **Versioniert** | `version` + `effectiveFrom`; ältere Fassungen über `GET /api/v1/privacy-notice/versions` (AC-030-2) |
| **Inhalt** | Zwecke, Rechtsgrundlage, Aufbewahrungsfristen, Rechte, **Provider-Auflagen**, Kontakt |
| **Verlinkt** | aus der Anwendung heraus; `x-stories: [US-030]` |

---

## 8. Verschlüsselung und Zugriff (SEC-012, ADR-004)

| Objekt | Maßnahme |
|---|---|
| Datenbank-Volume | Verschlüsselung über den Datenbankdienst (Cloud SQL/Supabase EU) |
| `app_user.password_hash` | bcrypt/argon2id, Cost ≥ 12 |
| `refresh_token.token_hash` | **nur Hash** — ein DB-Dump erlaubt kein Replay (AC-034-6) |
| `invitation.token_hash` | nur Hash |
| TLS | 1.2+ durchgehend, HTTP → HTTPS umgeleitet |
| CI-Secrets | nur aus ENV/Secret-Manager; Secret-Scan über Repo **und** Image |

---

## 9. Rechte der Betroffenen — Übersicht

| Recht | Technische Umsetzung | Status im MVP |
|---|---|---|
| Auskunft | `GET /me`, `GET /me/export` | umgesetzt |
| Berichtigung | `PATCH /me` | umgesetzt |
| Löschung | `DELETE /me`, `DELETE /users/{userId}` | umgesetzt |
| Datenexport | `GET /me/export` (JSON) | umgesetzt |
| Widerspruch | `revokeInvitation`, `removeOrganisationMember` | organisatorisch, **ohne** eigene Operation |
| Beschwerde | organisatorisch (Kontakt im Datenschutzhinweis) | organisatorisch |

---

## 10. Abdeckung der Akzeptanzkriterien

| AK | Erwartung | Umsetzung |
|---|---|---|
| AC-028-1 | Retention löscht Telemetrie > 90 Tage, **Prompts bleiben**; Löschmengen werden zusammengefasst | 4 |
| AC-028-2 | Zweiter Lauf löscht nichts, meldet `0`, ohne Fehler | 4 (Idempotenz) |
| AC-028-3 | Fehlgeschlagener Job endet mit Fehler, wird wiederholt, Alarm; kein Teilzustand | 4 (Fehlerverhalten) |
| AC-029-1 | Konto, Tokens, Sitzungen gelöscht; Autorschaft anonymisiert; Prompts bleiben und werden neu geprüft | 5.2 |
| AC-029-2 | Fremde Organisation ⇒ `403`, nichts gelöscht | 5.1 + ADR-002 (B-02) |
| AC-029-3 | Audit-Einträge bleiben mit Zeitpunkt, ohne personenbezogenen Verweis, ohne Inhalt | 5.2 |
| AC-030-1 | Datenschutzhinweis DE **und** EN mit Zweck, Rechtsgrundlage, Fristen, Rechten | 7 |
| AC-030-2 | Aktuelle Fassung mit Versionskennung; Vorversion archiviert | 7 |

---

## 11. Verweise

* `docs/adr/ADR-004-storage-retention-and-privacy.md` — Aufbewahrungsmatrix und Löschpfade
* `docs/adr/ADR-006-ai-provider-integration-and-verification.md` — F1, Provider-Gate, Nachweis
* `docs/architecture/security-threat-model.md` — Datenklassifikation und zulässige Flüsse
* `docs/architecture/observability.md` — inhaltsfreie Logs, Übermittlungsnachweis
* `docs/api/openapi.yaml` — `eraseOwnAccount`, `eraseUserAccount`, `exportOwnData`,
  `getPrivacyNotice`, `listRetentionRuns`, `triggerRetentionRun`
* `docs/requirements/PRD.md` — FR-060…FR-065, DP-001…DP-008, A-03, F1, F5, R-06
