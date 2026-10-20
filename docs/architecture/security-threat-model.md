# PromptGuard — Sicherheits- und Bedrohungsmodell

| Feld | Wert |
|---|---|
| **Dokument** | Sicherheits- und Bedrohungsmodell (inkl. Standorte der vier Scan-Arten) |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Bezug** | SEC-001…SEC-012, ADR-002, ADR-006, OWASP Top 10, R-01…R-06 |

> **[SECURITY] F1:** Der Originalinhalt eines Prompts wird **unredigiert** an den externen
> KI-Dienst übermittelt. Das ist eine bewusste Entscheidung des Product Owners
> (R-06, akzeptiertes Restrisiko). Die Pflichtmaßnahmen sind in ADR-006 festgeschrieben.

---

## 1. Schutzgüter

| # | Schutzgut | Warum | Schutzmaßnahme |
|---|---|---|---|
| G1 | **Mandantentrennung** | Abteilungen dürfen einander nicht sehen | ADR-002, 5 Ebenen, RLS |
| G2 | **Prompt-Inhalt** | darf das System nicht unkontrolliert verlassen | F1-Risiko begrenzt, FR-062, DP-002 |
| G3 | **Erkennungsqualität** | ein manipulierter Prüfer ist schlimmer als kein Prüfer | ADR-006, SEC-007 |
| G4 | **Zugangsdaten** (Passwörter, Tokens, API-Keys) | Konto- und Systembetrug | bcrypt ≥ 12, Token-Hashing, ENV-only, Secret-Scan |
| G5 | **Nachweisbarkeit** | Art. 30 DSGVO, SEC-010 | Audit-Trail ohne Inhalt, `transfer_audit` |
| G6 | **Verfügbarkeit der Registry** | Kerngeschäft darf nicht am KI-Dienst hängen | ADR-005, degraded mode |

---

## 2. Angreifer und Bedrohungen

| Angreifer | Ziel | Vektor | Gegenmaßnahme | Angriffspunkt im Code |
|---|---|---|---|---|
| **Entwickler einer Organisation A** | Daten der Organisation B | Path-Traversal über IDs, IDOR | `organisation_id`-Filter + RLS; fremde Ressource → `404` | Repository, Controller |
| **Entwickler** | Rechteausweitung (Waiver, Rollen) | direkter API-Aufruf | serverseitige Rollenprüfung (`x-required-role`), `403` | Security-Filter, Service |
| **Registrierter Fremder** | Zugang zu einer bestehenden Organisation | Registrierung mit `organisationId` | Registrierung **ignoriert** die Organisationswahl und erzeugt eine neue (AC-002-3) | `registerUser` |
| **Angreifer mit gestohlenem Access-Token** | dauerhafter Zugriff | Token-Missbrauch | 15 min Gültigkeit, Refresh-Rotation, Session-Kette-Invalidierung | Token-Service |
| **Replay eines rotierten Refresh-Tokens** | Zugriff | Token-Wiederverwendung | Invalidierung der **ganzen** Kette (AC-003-2) | `refreshTokens` |
| **Unprivilegierter Angreifer** | Massen-Account-Erstellung | Registrierungsflut | Rate-Limit 10/Min/IP + Sperre (SEC-008) | Rate-Limiter |
| **Prüfling (Prompt-Inhalt)** | Prüfer manipulieren | Prompt-Injection im Inhalt | Prüfling nur als **Datenblock**, Systemanweisung dominiert, kein Werkzeugaufruf, kein Netzzugang (SEC-006, SEC-007) | `ClassificationModule` |
| **Prüfling** | SSRF über enthaltene URLs | URL im Prompt-Inhalt | Der Prüfer hat **keinen** Netzzugang; URLs werden nie abgerufen | `ClassificationModule` |
| **Prüfling** | XSS in der Oberfläche | HTML/Markdown im Inhalt | Inhalt wird **nie** als HTML/Markdown gerendert; React escapet, zusätzlich restriktive CSP | Frontend |
| **Prüfling** | Log-Vergiftung | Secrets/PII im Inhalt | Log-Konfiguration ohne Objekt-`toString()`; automatischer Test (AC-032-2) | Logging |
| **Schwachstellen im SQL** | Datenabfluss, Manipulation | Injection über Suchbegriff/Filter | parametrisierte Queries, keine String-Konkatenation; Suchbegriff ist Daten, keine Anweisung (AC-013-3) | Repositories |
| **Geänderte Provider-Konfiguration** | Datenabfluss ins Nicht-EU / ins Training | ENV- oder Provider-Settings ändern | Provider-Verifikation **vor jedem Aufruf**; Verstoß ⇒ Abbruch ohne Inhalt (FR-038) | `ProviderVerification` |
| **Kompromittierter CI** | manipulierter Nachweis | Coverage manipuliert | Gate läuft **in** der Pipeline; Nachweis ist Beleg, nicht Ersatz | CI, `reportQualityGateAttestation` |
| **Insider mit Betriebszugriff** | Fachdaten | Betriebs-API | Plattform-Betrieb hat **keinen** fachlichen Zugriff (O7, F3) | Betriebsrollen |
| **SQL-Injection in der Admin-Sicht** | Rechtevergabe | unsichere Parameter | `x-required-role` je Operation, Validierung an der Grenze | Controller |

---

## 3. Die vier Scan-Arten — wo sie laufen

Die vier Erkennungskategorien haben **unterschiedliche Vertrauensstufen und
unterschiedliche Orte**. Das ist die zentrale Sicherheitsaussage dieses Dokuments.

| Kategorie | Ort | Technik | Kennt der Provider den Inhalt? | Blockiert? |
|---|---|---|---|---|
| **PROMPT_INJECTION** | Stufe (c) — LLM-Klassifikation | Modellurteil + Regelwerk | **ja** (unredigiert, F1) | `FLAGGED` ab Schwelle |
| **JAILBREAK** | Stufe (c) — LLM-Klassifikation | Modellurteil + Regelwerk | **ja** (unredigiert, F1) | `FLAGGED` ab Schwelle |
| **SECRET** | Stufe (a) — **lokale** Vorprüfung | deterministische Muster (API-Keys, Tokens, private Schlüssel, Connection-Strings, JWTs, Entropie) | **ja** (der Gesamtinhalt wird übermittelt) | `FLAGGED` ab Schwelle |
| **PII** | Stufe (a) — **lokale** Vorprüfung | deterministische Muster (E-Mail, Telefon, IBAN/Kreditkarte, Namen) + semantisch über das Modell | **ja** (der Gesamtinhalt wird übermittelt) | `FLAGGED` ab Schwelle |

### 3.1 Konsequenzen

1. **Die lokale Vorprüfung ist Pflicht** und läuft **vor** dem externen Aufruf (FR-031,
   F1-Mindestmaßnahme 3). Damit existiert eine **lokale** Erkennungs- und Meldespur, auch
   wenn der Provider ausfällt. Secret- und PII-Funde werden **immer** lokal erhoben.
2. **Alle vier Kategorien fließen in das Gesamturteil ein** (Stufe d): mindestens ein Fund
   ≥ Schwellenwert ⇒ `FLAGGED`. Die Herkunft des Fundes (lokal oder Modell) ändert die
   Schwellenlogik nicht.
3. **Der Provider erhält den Gesamtinhalt** — das ist die akzeptierte Grenze aus F1. Es gibt
   **keinen** Redaktionsschritt (ADR-006, Entscheidung „Was ausdrücklich nicht gebaut wird").
4. **Der Duplikatvergleich** (Stufe b) ist eine eigene Kategorie: er ist **beratend** und
   blockiert nie. Er nutzt Embeddings und läuft **nur** innerhalb der eigenen Organisation.
5. **Maskierung betrifft nur die Anzeige.** `scan_finding.redacted_excerpt` zeigt ein
   maskiertes Fragment; der übermittelte Originalinhalt bleibt unverändert. Das ist der
   entscheidende Unterschied zwischen „Anzeige geschützt" und „Übermittlung geschützt" —
   Letzteres gibt es bewusst **nicht** (F1).

---

## 4. Härtung des Prüfers gegen seine Prüflinge (SEC-007, R-04)

| Maßnahme | Umsetzung |
|---|---|
| **Rollentrennung** | Der Prüfling ist ein **Datenblock** in der Nachricht, keine Anweisung. Die Systemanweisung des Prüfers steht **vor** und **über** ihm. |
| **Kein Werkzeugaufruf** | Der Prüfer ruft **keine** Funktionen, Tools oder URLs auf. Er liefert ausschließlich ein Urteil. |
| **Kein Netzzugang** | Der Worker hat für die Klassifikation keinen Internetzugang — nur der konfigurierte Provider-Endpunkt. |
| **Strukturierte Antwort** | Die Modellantwort muss einem festen Schema entsprechen; freier Text wird verworfen. Ein „injiziertes" Urteil wird nicht übernommen, weil es dem Schema widerspricht. |
| **Deterministische Vorstufe** | Secret-/PII-Erkennung läuft **vor** dem Modell und ist nicht vom Modell beeinflussbar. |
| **Zwei-Augen-Effekt** | Eine Injection, die den Prüfer „auf APPROVED"manipulieren will, erzeugt selbst ein **Muster**, das in Stufe (c) als `PROMPT_INJECTION` auffällt. |
| **Schwellenlogik serverseitig** | Das Modell darf nur Schweregrade **vorschlagen**; `FLAGGED`/`APPROVED` entscheidet der Server aus den Fundstellen (FR-036). Ein Modell kann sich **nicht** selbst freigeben. |

---

## 5. Mandantentrennung als Sicherheitsmaßnahme

Details: `docs/architecture/tenant-model.md`, ADR-002.

| Aspekt | Maßnahme |
|---|---|
| Mandantquelle | ausschließlich das Token-Claim `organisation_id` |
| Query | `organisation_id`-Filter + RLS (`USING (organisation_id = current_setting(...))`) |
| Jobs | `organisation_id` im Job-Datensatz; ohne Kontext ⇒ verwerfen und protokollieren |
| Cache/Suche | Mandant im Cache-Key; Suchindex über Tenant-Filter abgefragt |
| Antworten | fremde Ressource ⇒ `404` ohne Existenzinformation |
| Kanal (SSE) | nur Ereignisse der eigenen Organisation |

---

## 6. Datenklassifikation und zulässige Flüsse

| Datenklasse | Beispiele | Darf das System verlassen? | Aufbewahrung |
|---|---|---|---|
| **Fachinhalt** | Prompt-Inhalt, Versionen, maskierte Fundstellen | **ja** — an den Provider (F1, mit Auflagen) | unbegrenzt bis Löschung |
| **Mandantenmetadaten** | Organisationsname, Rollen, E-Mail-Adressen von Nutzern | **nein** — werden nie übermittelt | wie Fachinhalt |
| **Übermittlungsnachweis** | Zeitpunkt, Provider, Modell, Zeichenlänge, Korrelation | nein | 90 Tage |
| **Rohpayload** | Rohe Anfrage-/Antwort-Payload | nein | **24 h**, dann gelöscht |
| **Credentials** | Passwort-Hashes, Refresh-Token-Hashes, Einladungshashes | **nein** — je nach Art | Sitzung 30 Tage / Token 15 min |
| **Telemetrie** | Logs, Metriken, Statusübergänge | nein | 90 Tage |

> **Was der Provider genau bekommt:** den zu klassifizierenden Text der Version — **keinen**
> Mandantenkontext, **keinen** Prompt-Titel, **keine** Nutzerkennungen, **keine**
> E-Mail-Adressen, **keine** anderen Prompts, **keine** Metadaten der Organisation, keine
> Audit-Einträge, keine Tokens und keine Plattform-Secrets (ADR-006).

---

## 7. Kontrollen aus SEC-001 … SEC-012 und ihre Verankerung

| Anforderung | Maßnahme | Verankerung |
|---|---|---|
| SEC-001 | JWT asymmetrisch/HMAC, Schlüssel aus ENV; Refresh-Token nur als `HttpOnly`/`SameSite`-Cookie | ADR-002, `refreshTokens` |
| SEC-002 | Tenant-Filter + RLS; Cross-Tenant ⇒ `403`/`404` ohne Dateninhalt | ADR-002, `tenant-model.md` |
| SEC-003 | Job-Kontext mit `organisation_id`; ohne Kontext ⇒ verwerfen | ADR-005 |
| SEC-004 | Rollenprüfung serverseitig je Operation (`x-required-role`) | `openapi.yaml` |
| SEC-005 | Validierung an der Grenze, parametrisierte Queries | ADR-001, ADR-009 |
| SEC-006 | Inhalt nie rendern, nie interpretieren, nie loggen, kein SSRF | ADR-006, Abschnitt 3 |
| SEC-007 | Prüfer-Härtung | ADR-006, Abschnitt 4 |
| SEC-008 | Rate-Limiting 10/Min/IP (Login), 1 000/Min/Nutzer (API) | `error-retry-strategy.md` |
| SEC-009 | HSTS, `X-Content-Type-Options`, `X-Frame-Options: DENY`, restriktive CSP, TLS 1.2+, keine internen Fehlerdetails | ADR-009, Reverse Proxy |
| SEC-010 | Audit für Login/Logout, Rollen-/Mitgliedänderungen, Löschung, Waiver, manuelle Wiederholung | `audit_event` |
| SEC-011 | Dependency- und Container-Scanning in CI | ADR-001, CI |
| SEC-012 | Verschlüsselung ruhender Daten; sensible Felder gehasht | ADR-004 |

---

## 8. Bedrohungen des Prüflaufs selbst (Sicherheit der Erkennung)

| Bedrohung | Wirkung | Gegenmaßnahme |
|---|---|---|
| **Fehlalarm** (R-02) | Nutzer umgehen das System | Schwellenwert je Organisation, `WAIVED`-Pfad, nur maskierte Ausschnitte, KPI „Fehlalarmquote" |
| **Prüfer wird kompromittiert** (R-04) | gefährliche Prompts passieren | lokale Vorprüfung zuerst, Schwellenlogik serverseitig, kein Werkzeugaufruf |
| **Provider-Ausfall** (R-03) | Registry blockiert | persistente Queue, Backoff, degraded mode, `FAILED` reproduzierbar |
| **Alarme werden ignoriert** (R-05) | Warnungen ohne Wirkung | E-Mail nur mit Metadaten, Zustellquote als KPI, Schwellen je Organisation |
| **Unredigierte Übermittlung** (R-06) | Secrets/PII verlassen das System | AVV, EU-Region, Trainingsverbot, Löschgarantie, Provider-Verifikation mit Abbruch, Metadaten-Audit, lokale Vorprüfung |
| **Queue-Manipulation** durch Mandanten | fremde Jobs bearbeiten | `scan_job` ist mandantengebunden; Jobs werden nur vom Worker angefasst |

---

## 9. Was in Phase 8 (Security & Compliance) geprüft wird

* Dependency-Scan und Container-Scan ohne Critical/High (SEC-011, AC-034-1).
* Secret-Scan über Repository **und** Image (AC-034-2).
* Header- und TLS-Prüfung (AC-034-4).
* Nachweis: alle Secrets aus ENV, keine im Quellbaum, im Image-Layer oder im
  Konfigurations-Dump (AC-034-5).
* Nachweis: Refresh-Token gehasht, Region `europe-west3`, Provider ohne Training und ohne
  dauerhafte Speicherung (AC-034-6).
* Prüfung der `403`-Ausnahme bei User-Löschung (Befund B-02).

---

## 10. Verweise

* `docs/adr/ADR-002-multi-tenancy-and-authorisation.md` — Mandantentrennung
* `docs/adr/ADR-006-ai-provider-integration-and-verification.md` — F1, Provider-Gate, Prüfer-Härtung
* `docs/architecture/tenant-model.md` — Trennung über alle Ebenen
* `docs/architecture/privacy-retention.md` — Datenflüsse und Fristen
* `docs/architecture/diagrams/04-component-scan-worker.mmd` — Standorte der Scan-Arten
* `docs/requirements/PRD.md` — SEC-001…SEC-012, R-01…R-06, F1
