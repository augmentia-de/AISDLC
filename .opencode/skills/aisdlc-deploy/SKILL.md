---
name: aisdlc-deploy
description: Deployment und Betrieb - Pipeline-Stufen mit Approval-Gates, Rollout-Strategien, Validierung, Smoke-Tests, Promotion, Rollback, SLIs/SLOs und Runbook. Nutze diesen Skill in Phase 9 nach bestandenem Security-Gate.
metadata:
  origin: adaptiert aus wshobson/deployment-pipeline-design + wshobson/deployment-strategies + wshobson/deployment-validation + wshobson/slo-implementation + wshobson/incident-runbook-templates + ECC/deployment-patterns + ECC/docker-patterns
---

## Voraussetzungen (Gate)

Vor dem Deploy pruefen und **nur bei erfuellten Bedingungen** starten:

| Bedingung | Nachweis |
|---|---|
| QA-Report: 0 Critical | `docs/qa/` Test-Report |
| Security-Report: 0 Critical, 0 High | `docs/qa/security-audit.md` |
| Coverage >= Projektschwelle | Coverage-Report |
| Human Approval nach Phase 8 | bestaetigt |
| Migrationsplan ruecklaeufig | dokumentiert (falls Schema-Aenderung) |

## 1. Pipeline-Stufen

```text
Build -> Test -> Security-Scan -> Package -> Staging -> Smoke -> Approval -> Prod
```

Jede Stufe hat: **Eingangskriterium, einen ausfuehrbaren Check, ein Ergebnis, ein
Abbruchkriterium.** Kein Schritt ohne klares Ergebnis - "gruen" oder "rot", nicht "sieht gut aus".

**Approval-Gates** dort, wo ein Fehler teuer waere: vor Staging (nach Security-Scan) und vor
Produktion (nach Smoke-Tests).

## 2. Rollout-Strategien

| Strategie | Ausfallwirkung | Einsatz |
|---|---|---|
| **Recreate** | Downtime | nur Maintenance-Fenster, migrationsfrei |
| **Rolling** | kurz inkonsistent | Default bei kompatiblen Aenderungen |
| **Blue/Green** | instant, doppelte Kosten | Datenbank-kompatible Releases |
| **Canary** | exponentiell klein zuerst | hohes Risiko, feinste Kontrolle |

**Migrationskompatibilitaet** (Expand/Contract) muss zu der Strategie passen: expandierendes
Schema zulaesst Rolling, Contract erst nach vollstaendigem Rollout.

## 3. Validierung vor dem Deploy (Pre-Flight)

- Konfiguration vollstaendig und gueltig - **fail closed**, nicht auf Default zurueckfallen.
- Secrets/Variablen in der Zielumgebung vorhanden.
- Health- und Readiness-Probes bestehen **vor** Traffic-Umschaltung.
- Ressourcen (CPU, Memory, Replicas) passen zum Lastprofil.
- Rollback-Pfad konkret: welches Image, welcher Befehl, wie lange.
- Versioniertes Artefakt (Digest/Tag), kein `latest`.

## 4. Smoke-Tests auf Staging

1. Health-Endpoint antwortet.
2. Ein repräsentatives End-to-End-Flavor laeuft durch (Anlegen, Lesen, Aendern, Loeschen).
3. Authentifizierung und Autorisierung greifen (401/403 wie erwartet).
4. Abhaengigkeiten erreichbar (DB, Queue, externes API).
5. Logs zeigen keine unerwarteten Fehler.

## 5. Promotion und Rollback

- **Promotion** erst nach gruenen Smoke-Tests und Approval.
- **Rollback** ist Teil des Deploys, nicht der Notfallplan danach: Befehl, Reihenfolge,
  Datenzustand-Handhabung und Autorisierung muessen vorab feststehen.
- Bei Canary: Abbruchkriterien (Fehlerrate, Latenz, SLO-Burn-Rate) **vor** dem Deploy setzen.

## 6. Observability und Betrieb (SLIs/SLOs)

| Ebene | Inhalt |
|---|---|
| **SLI** | messbare Nutzer-Signale (Erfolgsrate, Latenz, Verfuegbarkeit) |
| **SLO** | Zielwert je SLI (z. B. 99,9 % Erfolg in 30 Tagen) |
| **Error Budget** | verbleibende Toleranz; bei Verbrauch: Feature-Freeze |
| **Alerting** | symptombasiert (Nutzer-Effekt), nicht ursachenbasiert |
| **Dashboards** | je SLI, mit den relevanten Dimensionen |

Alerts brauchen eine **Runbook**-Referenz: Erkennung, Diagnose, Mitigation, Recovery,
Eskalation, Verantwortlicher.

## 7. Runbook-Struktur (Incident)

1. **Erkennung** - Alert, Schweregrad, erste 5 Minuten
2. **Triage** - was ist kaputt, Blast Radius, Nutzer-Auswirkung
3. **Mitigation** - konkrete Massnahme mit Befehl
4. **Recovery** - Verifikation, Monitoring, Rueckbau
5. **Kommunikation** - wer meldet was an wen
6. **Postmortem** - Ursache, Beitragende, Prevention, mit Datum

Ein Runbook ist fuer einen Menschen um 3 Uhr nachts geschrieben: keine Anamnese, sondern
Befehle.

## 8. Rollback-Plan

| Schritt | Angabe |
|---|---|
| Ausloeser | exakter Alarm- oder Fehlerwert |
| Befehl | exakter Deploy-/Rollback-Befehl |
| Reihenfolge | Migration zurueck? Daten zuerst oder Code? |
| Dauer | realistische geschaetzte Dauer |
| Kommunikation | wer informiert wen |
| Nachkontrolle | welche Pruefung bestaetigt die Erholung |

## Output

`docs/execution/deploy-report.md`:
- Umgebungen und Versionen (Digest/Tag)
- Staging- und Produktions-URL
- Smoke-Test-Ergebnisse (pass/fail je Check)
- Rollback-Plan und Ausloeser
- SLO-/Alerting-Status
