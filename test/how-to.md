# How-To: AISDLC3 Profile-Test

Dieses Verzeichnis enthält Beispiel-Specs, um den profilbasierten Ansatz zu testen.

## Struktur
- `test/mvp/spec.md` - Minimale Kundenanforderung für internes Ticket-System
- `test/enterprise/spec.md` - Vollständige Enterprise-Anforderung für externes Kundenportal
- `test/how-to.md` - Diese Anleitung

## Testen im MVP-Modus
1. Konfiguration setzen: `.agent/config.json` -> `"profile": "mvp"`, `"allow_fast_track": true`, `"traceability": "light"`, `"gates.traceability_hard_fail": false`
2. Orchestrator starten (Default-Agent `aisdlc-requirements`) mit `test/mvp/spec.md`
3. Erwartung: Weniger Schritte, minimale Artefakte, schnellere Durchläufe

## Testen im Enterprise-Modus
1. Konfiguration setzen: `"profile": "enterprise"`, `"allow_fast_track": false`, `"traceability": "full"`, `"gates.traceability_hard_fail": true`
2. Orchestrator starten mit `test/enterprise/spec.md`
3. Erwartung: Voller 9-Phasen-Zyklus, Gates, strenge Traceability, Vertrags-Einfrierung

## Hinweise
- Vertragsklassifikation in `docs/architecture/contract-matrix.md` beachten
- Shell ist auf Least Privilege umgestellt (Default-Deny)
- Subagent-Antworten werden jetzt tolerant geparst
- Fast-Track nur bei internen Änderungen ohne Vertragsbetroffenheit
