# AI-SDLC3: Gemeinsame Best-Practice-Verbesserungsversion

Dieses Dokument vereint Kritik, Analyse und konkrete Umsetzungsschritte, um AISDLC3 sowohl für kleine Projekte/MVPs als auch für Enterprise-Projekte nutzbar zu machen. Ziel ist ein **profilbasierter, modularer Ansatz** (statt zweier separater Codebasen), der Governance bewahrt, aber Overhead, Fragilität und Sicherheitsrisiken reduziert.

## 1) Kritische Punkte & Bewertung

| Kritikpunkt | Schwere | Status | Bewertung |
| :-- | :-- | :-- | :-- |
| **Hohe Token-Kosten & Overhead** | Hoch | Teilweise gemildert | Steckbrief (`briefing.md`), kurze Antworten, Skill-Laden on-demand helfen, reichen aber für MVPs nicht aus. |
| **Prozess-Starrheit (Bürokratie)** | Hoch | Zu streng | 9-Phasen-Zyklus, Vertrags-Einfrierung, verpflichtende Gates bremsen explorative Entwicklung. `Nachzug` zeigt Bruch der Regeln. |
| **Fragile Subagenten-Antworten (3-Zeilen-Regel)** | Sehr hoch | Kritisch | Parser des Orchestrators ist abhängig von exaktem Format. Freitext bricht Ablauf leicht. |
| **Abhängigkeit von initialer Spezifikation** | Hoch | Gut adressiert | `aisdlc-grill-stress`, `REQUIREMENT_GAP` vorhanden, aber nicht foolproof. |
| **Shell-Permissions (global `allow`)** | Kritisch | Unzureichend | Größtes echtes Sicherheitsrisiko. Pfadschutz allein reicht nicht. |
| **Git-Commit-Trick (`git reset -q && git add -A -- <paths>`)** | Hoch | Fragil | Bricht leicht (z.B. durch Tools wie `rtk`), pathspec-Probleme bei leeren Ordnern. |
| **Traceability als harter Gate-Blocker** | Hoch | Zu streng | Regex/Naming-Unterschiede können Deployment unnötig blockieren. |

## 2) Best-Practice-Lösung: Profilbasierter Ansatz

Ein einheitliches Framework mit konfigurierbarem Profil ist dem Forken eindeutig vorzuziehen. Konfiguration zentralisieren und konsequent vom **Orchestrator** durchsetzen.

### 2.1 Modusdefinition

| Dimension | `profile: mvp` (Kleines Projekt/MVP) | `profile: enterprise` (Enterprise/Compliance) |
| :-- | :-- | :-- |
| **Phasen-Umfang** | Reduziert auf Kernphasen: `Requirements → (minimal Architecture) → Code → Test → Deploy` (3–5 Schritte effektiv) | Voller 9-Phasen-Zyklus inkl. Simplify, Grill-Me, Security, CI-Pipeline |
| **Dokumentation** | Minimal: `docs/requirements/PRD.md` + nötige Code-Artefakte. ADRs/Glossar nur bei Bedarf. | Vollständig: ADRs, C4, OpenAPI, Glossar, Traceability, Security-Audit, Deploy-Report |
| **Qualitätsgates** | Locker/Manuell, Fokus auf Funktionstüchtigkeit. Security-Minimum (Secrets-Scan). Folder-Ownership & Commits bleiben verpflichtend. | Streng: GATE 1 & 2, `traceability_check` hart bei kritischen Pfaden, Security 0 Critical/High, Coverage-Ziele |
| **Traceability** | `light` (Story→Code) - Warnung bei Lücken | `full` (Req→Story→Feature→Test→Code) - Hard-Fail bei Vertrags-/kritischen Pfaden |
| **Vertrags-Einfrierung** | Nur bei als `extern`/`public` klassifizierten Verträgen erzwingen | Streng nach Schritt 4, Rücksprung zu Schritt 2 bei Vertragsänderung |
| **Fast-Track/Patch** | Erlaubt (mit Vertragsprüfung) | Stark eingeschränkt, nur mit Begründung & Review |

### 2.2 Neue zentrale Konfiguration

**Neu anlegen:** `.agent/config.json`

```json
{
  "$schema": "./schemas/config.schema.json",
  "profile": "mvp",
  "allow_fast_track": true,
  "traceability": "light",
  "gates": {
    "enforce_gate1": true,
    "enforce_gate2": true,
    "traceability_hard_fail": false
  },
  "security": {
    "enforce_minimal_scan": true,
    "block_on_critical_secrets": true
  },
  "context": {
    "briefing_first": true,
    "strict_three_line_reply": false
  }
}
```

**Anpassen:** `.opencode/opencode.jsonc` - `profile` als Default-Referenz ergänzen (Backwards-kompatibel). Primäre Quelle bleibt `.agent/config.json`, da Subagenten zuverlässiger darauf zugreifen können.

## 3) Konkrete Änderungen pro Kritikpunkt

### 3.1 Token-Kosten & Overhead
| Ort | Änderung | Begründung |
| :-- | :-- | :-- |
| `.agent/config.json` | Profil steuern | Zentrale Kontrolle |
| `.agent/PHASES.md` | Neue Spalten `mvp` (`yes\|no\|optional`), `required_artifacts` pro Schritt | Orchestrator überspringt nur wirklich optionale Artefakte/Schritte |
| `.agent/prompts/orchestrator/*` | Profilabhängige Checklisten | Nur nötige Dateien im Briefing aufführen |
| `.opencode/agents/aisdlc-orchestrator.md` | "Wenn `profile: mvp` -> überspringe optionale Artefakte (Glossar, umfangreiche ADR-Sets) sofern nicht benötigt" | Weniger Kontext, weniger Tokens |

### 3.2 Prozess-Starrheit & Rücksprünge
| Ort | Änderung | Begründung |
| :-- | :-- | :-- |
| `.opencode/agents/aisdlc-orchestrator.md` | **Fast-Track/Patch-Modus** ergänzen | Direkte Korrekturschleife für Bugfixes ohne vollen Phasenbaum |
| `AGENTS.md` | Eskalation um "Patch" erweitern | Klare Regeln |
| `docs/architecture/contract-matrix.md` (neu) | Vertragspartner/Klassifikation (`internal\|external\|public`) erfassen | Nur bei `external/public` strenger Freeze/Rücksprung erzwingen |

**Regel:** Fast-Track nur erlaubt, wenn **keine** Vertragsdateien betroffen (`docs/api/**`, `docs/architecture/contract*`, `openapi.yaml`). Sonst Light-Gate oder voller Rücksprung.

### 3.3 Fragile 3-Zeilen-Antworten
| Ort | Änderung | Begründung |
| :-- | :-- | :-- |
| `.opencode/agents/aisdlc-orchestrator.md` §3a | Parsing auf **Regex-Extraktion** umstellen | Toleranter gegenüber Freitext/Markdown |
| `.opencode/agents/aisdlc-orchestrator.md` | Fallback-Mechanismus | Bei Parsing-Fehlschlag einmalig Korrektur anfordern, nicht direkt abbrechen |

**Regex-Vorschlag:**
```regex
/(?m)^STATUS:\s*(done|done_with_open_points|blocked)\b.*$/
/(?m)^COMMIT:\s*(.+)$/
/(?m)^OFFENE PUNKTE:\s*(.+)$/
```
Extrahiere überall im Output (nicht nur "erste drei Zeilen"), ignoriere Einleitungen/Überschriften.

### 3.4 Shell-Permissions (Least Privilege)
| Ort | Änderung | Begründung |
| :-- | :-- | :-- |
| `.opencode/opencode.jsonc` | **Default `deny`** für Shell, nur gezielte `ask`/`allow` für destruktive Befehle behalten | Behebt größtes Sicherheitsrisiko |
| `.opencode/agents/*.md` | Pro Agent explizite `permissions.shell` (Allowlist) | Least Privilege erzwingen |
| `AGENTS.md` | Kommentar zu Shell-Schutz korrigieren ("Schutz über Pfad-Rechte allein reicht nicht") | Ehrlichere Dokumentation |

**Vorschlag für Allowlists (orientiert am EchoBox-Volllauf):**
| Agent | Shell | Bemerkung |
| :-- | :-- | :-- |
| `aisdlc-requirements` | `deny` (oder minimal `read-only` Tools) | Soll primär lesen/dokumentieren |
| `aisdlc-architecture` | Sehr eingeschränkt (`git log/status/diff`, read-only) | Überwiegend Dokumentation |
| `aisdlc-execution` | `allow` für `git`, `mvn`/`npm`/`pnpm`/`yarn`, `node`, `python`, `java`, `bash` (nur projektbezogen) | Build/Test/Run nötig |
| `aisdlc-qa` | `allow` für `git`, Test-Runner, Linter/Scanner | QA/Scan |
| `aisdlc-orchestrator` | `deny` für Ausführung, nur minimal für Git-Status/Log zum Prüfen | Darf nie selbst editieren, Shell nur zum Verifizieren |

**Hinweis:** Vor Umstellung auf Default-Deny **vollständige Liste** tatsächlich genutzter Kommandos aus `run-log.jsonl` extrahieren.

### 3.5 Traceability robuster & modusspezifisch
| Ort | Änderung | Begründung |
| :-- | :-- | :-- |
| `.agent/tools/traceability_check.py` | Modi `off\|light\|full`, `--mode`, Fuzzy-Matching, Ignore-List | Weniger fragil |
| `AGENTS.md` / Orchestrator | Soft-Fail/Hard-Fail je Profil & Vertragsklasse | Verhindert Blockade durch Regex-Fehler |
| GATE 2 Logik | Nur bei `external/public` + `full` Hard-Fail mit 0 Lücken erzwingen | Pragmatisch aber sicher |

### 3.6 Git-Commits robuster
| Ort | Änderung | Begründung |
| :-- | :-- | :-- |
| `AGENTS.md` Commit-Convention | Robuster Ansatz ergänzen | Weniger fragil gegenüber `rtk` |
| Orchestrator/Workflows | Validierung vor Commit (`git diff --cached --name-only`), Existenzprüfung | Frühes Erkennen von Fehlern |
| Vorschlag | `git add -A -- <paths>` beibehalten, aber `git commit -m ... -m ... -- <paths>` als Fallback oder `git commit --only -- <paths>` testen/validieren | Kompatibler mit Tools |

## 4) Sofort umsetzbare Schritte (Reihenfolge)

Um Risiken zu minimieren, **inkrementell** vorgehen:

| # | Aktion | Ziel | Status |
| :-- | :-- | :-- | :-- |
| **1** | `.agent/config.json` anlegen | Profil zentralisieren | **TODO** |
| **2** | `.agent/schemas/config.schema.json` anlegen | Validierung | **TODO** |
| **3** | `.agent/PHASES.md` um `mvp`-Spalten erweitern | Orchestrator kann überspringen | **TODO** |
| **4** | `docs/architecture/contract-matrix.md` anlegen | Vertragsklassifikation für Freeze/ Fast-Track | **TODO** |
| **5** | `.opencode/agents/aisdlc-orchestrator.md` anpassen | Profil-lesen, Regex-Parsing, Fast-Track, modusspezifische Gates | **TODO** |
| **6** | `.agent/tools/traceability_check.py` erweitern | `off/light/full`, Soft/Hard-Fail | **TODO** |
| **7** | `AGENTS.md` anpassen | Regeln zu Profil, Shell, Parsing, Fast-Track ergänzen/korrigieren | **TODO** |
| **8** | **Shell umstellen:** `.opencode/opocolio.jsonc` auf Default-Deny + Agent-Allowlists | Sicherheitsrisiko beheben | **TODO** (sehr sorgfältig) |
| **9** Commit-Convention robuster gestalten | Index-Trick evaluieren/absichern | Weniger fragil | **TODO** |

> Hinweis: Tippfehler `opocolio.jsonc` korrigieren zu `opencode.jsonc` bei Umsetzung.

## 5) Fazit

Der vorgeschlagene profilbasierte Ansatz ist die **richtige strategische Lösung**. Er balanciert perfekt zwischen:

- **Enterprise:** Volle Governance, Auditierbarkeit, strenge Gates, vollständige Traceability
- **MVP/Agil:** Weniger Overhead, schnellere Iterationen, pragmatische Gates, direkteres Arbeiten

**Wichtigste Sofortfixes:** Tolerantes Parsing + Default-Deny für Shell + zentraler Profil-Schalter. Mit der oben genannten Reihenfolge kann man riskante Änderungen (Shell) erst nach vollständiger Erfassung der benötigten Befehle umsetzen und so Brüche vermeiden.

**Kernprinzipien bleiben unverhandelbar:** Delegation statt Ausführung, Folder-Ownership, atomare Commits pro Schritt (modusspezifisch reduziert, nicht abgeschafft), Briefing als Einstiegspunkt.


## 6) Durchgeführte Umsetzung (Status)

Umgesetzt in dieser Iteration:

| Änderung | Ort | Bemerkung |
| :-- | :-- | :-- |
| **Zentrale Konfiguration** | `.agent/config.json`, `.agent/schemas/config.schema.json` | Profil `mvp`/`enterprise`, Gates/Traceability/Security konfigurierbar |
| **Vertragsklassifikation** | `docs/architecture/contract-matrix.md` | Grundlage für Freeze/ Fast-Track |
| **Phasen-Mapping** | `.agent/PHASES.md` | MVP/Enterprise-Markierung pro Schritt |
| **Orchestrator** | `.opencode/agents/aisdlc-orchestrator.md` | Profil-Lesen, Regex-basiertes Parsing, Fast-Track mit Vertragsprüfung, profilabhängige Gates |
| **Shell Least Privilege** | `.opencode/opencode.jsonc`, Agent-Frontmatter (requirements/architecture/execution/qa/orchestrator) | Default-Deny, gezielte Allowlists |
| **Traceability** | `.agent/tools/traceability_check.py` | `--mode off/light/full`, Soft-Fail im MVP/light |
| **AGENTS.md** | Regeln ergänzt | Profil, Fast-Track, Soft/Hard-Fail, Shell-Hinweis |

**Test-Verzeichnis:** `test/mvp/` und `test/enterprise/` angelegt für künftige Profil-Tests.
