# Traceability — Lückenliste

> Schritt 1 (Anforderungserhebung) · Agent: `aisdlc-requirements`
> Erzeugt: 2026-10-21 · **Nachgezogen: 2026-10-22** (Schritt 1 erneut, ausschliesslich die Matrix)
> Werkzeug: `python3 .agent/tools/traceability_check.py` · Matrix: `docs/requirements/traceability_matrix.json`

**Kein Prozentwert.** Jede Lücke bekommt eine Entscheidung und eine Begründung, warum sie
bleibt. Der Report wurde nach Schritt 5 (Testbezüge) und Schritt 6 (Implementierung)
fortgeschrieben; der Nachzug vom 2026-10-22 zieht die Matrix auf den **umgesetzten**
Stand (Schritt 6 `e9e1bc1`, Schritt 7 `bd15f19`).

---

## Ergebnis des Werkzeugs

```
$ python3 .agent/tools/traceability_check.py
Keine Luecken — die Kette Anforderung -> Story -> Feature -> Test -> Code ist vollstaendig.
Exit-Code: 0
```

| # | Prüfung | Ergebnis |
|---|---|---|
| 1 | Anforderung in der PRD ohne Story | **grün** — 24 Anforderungen (18 FR + 6 NFR), jede hat mindestens eine Story |
| 2 | Story verweist auf unbekannte Anforderung | **grün** — jeder Verweis zeigt auf eine PRD-Zeile |
| 3 | Story ohne Feature-Datei | **grün** — 23 Stories, 23 Feature-Dateien |
| 4 | Story ohne Anforderungs-Bezug | **grün** — jede Story nennt mindestens eine Anforderungs-ID |
| 5 | Akzeptanzkriterium ohne Matrix-Eintrag | **grün** — 101 Akzeptanzkriterien, alle in der Matrix geführt **und** im neuen `acceptance_criteria_index` mit Story, Anforderung und Test |
| 6 | Matrix-Eintrag ohne Test | **grün (inhaltlich)** — jeder Eintrag nennt nur Testdateien, die existieren; der Umfang ist 23 Stories / 101 AKs / 24 Anforderungen unverändert |
| 7 | Matrix-Eintrag ohne Code | **grün (inhaltlich)** — alle Codeverweise existieren, **außer** `docker-compose.yml` (S-22, offen, Schritt 9) |

**Eine gefüllte Matrix ist kein Beleg.** Die Prüfungen 6 und 7 sind jetzt gegen das
**Dateisystem** gelaufen, nicht gegen eine gefüllte Liste. Das Werkzeug selbst prüft die
Pfade weiterhin **nicht** — das macht der Nachzug maschinell (siehe unten).

---

## Maschineller Nachweis des Nachzugs

Geprüft am 2026-10-22 über alle 24 Matrix-Einträge:

| Größe | Wert |
|---|---|
| Pfadverweise auf existierende Dateien (Tests + Code) | **184** |
| Ausdrücklich als **offen** markierte Verweise (mit `owner` und `step`) | **1** (`docker-compose.yml`, S-22) |
| Tote Pfade ohne Markierung | **0** |
| AK-Index-Einträge mit mindestens einem existierenden Test | **101 von 101** |
| Anforderungen mit `coverage_state: "verified"` | **23** |
| Anforderungen mit `coverage_state: "partial"` | **1** (NFR-006 — siehe S-22) |

---

## Verbleibende Lücken und ihre Entscheidungen

### L-01 — Testbezüge waren Platzhalter (`coverage_state: "seeded"`) → **abgebaut**

**Lücke (Stand Schritt 1):** Jeder Matrix-Eintrag verwies auf geplante Testdateien
(`tests/...`), die es noch nicht gab. Das Werkzeug prüft nur, ob das Feld gefüllt ist —
nicht, ob die Datei existiert.

**Entscheidung:** Die Testpfade wurden in **Schritt 5** erzeugt und in Schritt 6/7
umgesetzt. Der Nachzug vom 2026-10-22 hat sie gegen das Dateisystem geprüft: jeder
`tests`-Eintrag steht jetzt auf `state: "implemented"` und nennt eine existierende Datei.

**Abgebaut in:** Schritt 5 (Testmatrix), Schritt 6/7 (Implementierung), Nachzug 2026-10-22.

### L-02 — Codebezüge waren Platzhalter (`coverage_state: "seeded"`) → **abgebaut**

**Lücke (Stand Schritt 1):** Die Matrix nannte geplante Quelldateien. `src/` existierte
noch nicht.

**Entscheidung:** Die Codeverweise wurden in **Schritt 6/7** geprüft. Der Nachzug hat die
**fünf stale Pfade** auf ihre tatsächlichen Träger umgehängt (siehe L-05) und jeden
verbleibenden Verweis gegen das Dateisystem geprüft.

**Abgebaut in:** Schritt 6/7, Nachzug 2026-10-22.

### L-03 — Offene Punkte O-01 bis O-07

**Lücke:** Sieben Fragen sind bewusst nicht entschieden — fünf davon wörtlich aus
Abschnitt 13 der Anforderung, zwei aus Zahlenlücken in FR-022/FR-023 und aus Abschnitt 11.

**Entscheidung:** Sie sind als offene Punkte mit Owner (Fachbereich / Gate 1 bzw. Gate 2),
Empfehlung und Begründung erfasst. Sie bleiben **unberührt** — der Nachzug trifft zu
**keiner** dieser Fragen eine Entscheidung. **O-04** (Verschlüsselung im Ruhezustand)
und **O-05** (AVV) sind weiterhin offen, beide mit Gate 2 als Owner.

**Warum sie bleiben:** Der Fachbereich hat sie bewusst offen gelassen; eine Festlegung
durch den Requirements-Orchestrator wäre eine erfundene Anforderung.

### L-04 — Keine Anforderung ohne Story

**Keine Lücke.** Alle 24 Anforderungen der Anforderung sind durch Stories abgedeckt:

| Anforderung | Story | Anforderung | Story |
|---|---|---|---|
| FR-001 | US-001, US-019 | FR-022 | US-011 |
| FR-002 | US-002, US-019 | FR-023 | US-012 |
| FR-003 | US-003, US-019 | FR-024 | US-013 |
| FR-004 | US-004 | FR-025 | US-014 |
| FR-010 | US-005 | FR-030 | US-015, US-020 |
| FR-011 | US-006 | FR-031 | US-016, US-020 |
| FR-012 | US-007 | FR-032 | US-017 |
| FR-013 | US-008 | FR-033 | US-018 |
| FR-020 | US-009 | NFR-001 … NFR-006 | US-005, US-005, US-022, US-007, US-021, US-023 |

### L-05 — Fünf stale Codepfade (aus Schritt 1 geplant, in Schritt 6/7 nie angelegt)

**Lücke:** Fünf Verweise der Matrix zeigten auf Dateien, die es nicht gibt. Der Vertrag
und das Schema sind unberührt — es ist eine **Dokumentations**-Lücke, keine
Implementierungslücke: die Verantwortung ist an einen anderen Baustein geflossen.

| Geplanter Pfad (Schritt 1) | Tatsächlicher Träger (Schritt 6/7) | Bewertung |
|---|---|---|
| `crypto/SecretStore.java` | `crypto/PayloadCipher.java` (Chiffre des Secrets), `endpoint/EndpointRepository.java` (`secret_ciphertext`/`secret_nonce`), `delivery/DeliveryDispatcher.secret(…)` | **geflossen** — Pfad umgehängt |
| `delivery/PayloadSource.java` | `delivery/DeliveryDispatcher.payloadBytes(…)` | **geflossen** — Pfad umgehängt |
| `delivery/RetryScheduler.java` | `delivery/DeliveryDispatcher.record(…)` mit `delivery/BackoffPolicy.java` | **geflossen** — Pfad umgehängt |
| `tenant/TenantConnectionInitializer.java` | `tenant/TenantTransactionRunner.java` — `setTenant(…)` **innerhalb** der lesenden Transaktion (ADR-002 §3) | **geflossen** — Pfad umgehängt; der historische Name bleibt in `docs/architecture/folder-structure.md` §2.6 stehen (E-05: der Name bleibt, die Semantik steht im ADR) |
| `config/TenantInstanceConfiguration.java` | `config/EchoBoxProperties.java` (`echobox.tenant.id`), `delivery/DeliveryWorker.java` (`buildInstanceId(…)`), `tenant/TenantInstanceFilter.java` (Bindung durchsetzen) | **geflossen** — Pfad umgehängt |

**Entscheidung:** Jeder der fünf Pfade ist auf den tatsächlichen Träger umgehängt; die
Matrix trägt die Erklärung je Eintrag (`note`). **Kein Klassen- oder Modulname wurde
umbenannt** (E-05).

### L-06 — Zwei Testdateien belegen AK, standen aber nicht in der Matrix

**Lücke:** `AC-023-2` (Mandanteninstanz) und `AC-023-5` (getrennte Umgebungen) waren ohne
Testverweis — die sie belegenden Dateien fehlten in der Liste.

**Entscheidung:** `tests/integration/deployment/TenantInstanceIT.java` (AC-023-2) und
`tests/integration/deployment/EnvironmentSeparationIT.java` (AC-023-5) sind im Eintrag
NFR-006 eingetragen; beide AK stehen mit Story (US-023), Anforderung (NFR-006) und Test
im neuen `acceptance_criteria_index`. **Kein AK wurde geändert oder ergänzt** — der
Umfang bleibt 23 Stories / 101 AKs / 24 Anforderungen.

### L-07 — Querschnittliche Testdateien standen nicht in der Matrix

**Lücke:** Dateien wie `RoleCapabilityMatrixIT`, `ForeignResourceIsolationIT`,
`AlwaysPresentFieldsIT` und `AlwaysPresentFieldsContractTest` belegen AKs der
Anforderungen, standen aber in keiner Testliste.

**Entscheidung:** Je Anforderung nennt die Matrix jetzt **jede** Datei, die ein AK des
Eintrags belegt. Die Liste ist aus dem Repository abgeleitet (AK-ID im Namen eines
Testfalls) und deckungsgleich mit dem Beleg `.agent/artifacts/traceability-verification.md`
Abschnitt 3.

### S-22 — `docker-compose.yml` fehlt → **offen, Owner Schritt 9**

**Lücke:** Die in `docs/architecture/folder-structure.md` §1 und `.agent/knowledge/stack.md`
vorgesehene lokale Compose-Datei existiert nicht. Der Verweis der Matrix ist als
`state: "open"` mit `owner` und `step` markiert — **nicht** als erledigt.

**Warum sie bleibt:** Kein Test verlangt sie — `EnvironmentSeparationIT` prüft die
Konfigurationen und den Bereitstellungsdeskriptor, nicht Compose. Der Befund ist in
Schritt 7 als **S-22** erfasst (`.agent/artifacts/refactoring-report.md` §4 und §6) und
gehört zu **Schritt 9 (Deployment & Betrieb)**.

**Abgebaut in:** Schritt 9.

---

## Was als Nächstes passiert

1. **Schritt 8 (Security & Compliance)** prüft die Kette vor Gate 2 erneut — jetzt gegen
   die tatsächlichen Dateien. Die Matrix behauptet nichts mehr, was das Repository nicht
   hergibt.
2. **Schritt 9 (Deployment & Betrieb)** erstellt `docker-compose.yml` (S-22) und schließt
   den Eintrag in der Matrix (`state: "open"` → `state: "implemented"`).
3. Eine Änderung am Vertrag oder am Schema ist **kein** Rücksprung in diese Matrix —
   sie wäre ein Rücksprung nach Schritt 2 (der Vertrag ist seit Schritt 4 eingefroren).

**Regel:** Lücken werden nie durch Entfernen von Matrix-Einträgen geschlossen. Eine
weggelassene Zeile ist eine verschwundene Anforderung.
