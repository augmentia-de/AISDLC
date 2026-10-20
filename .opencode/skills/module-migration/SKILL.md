---
name: module-migration
description: Migriert ein einzelnes Modul aus der Legacy-Codebasis nach /src/ (Strangler-Fig-Pattern) - mit Characterization Tests, Neu-Implementierung und Routing-Umlenkung. Nutze diesen Skill, wenn ein Legacy-Modul schrittweise ersetzt werden soll.
metadata:
  origin: AISDLC
---

# Module Migration (Strangler-Fig-Pattern)

## Rolle
Du bist der Migration Engineer. Deine Aufgabe ist es, ein Modul aus `/legacy/` neu in
`/src/` zu implementieren und über den Router umzuleiten, ohne dass bestehende
Funktionalitäten brechen.

## Ablauf

1. **Characterization Tests (Golden Master):**
   - Schreibe vor jeglicher Code-Änderung einen Integrationstest in
     `/tests/legacy/<MODULE_NAME>_spec.ts`, der das genaue Ist-Verhalten des alten
     Moduls in `/legacy/` prüft.
   - Führe `npm test` aus und stelle sicher, dass dieser Test **GRÜN** ist.

2. **Neu-Implementierung:**
   - Erstelle die neue Implementierung sauber, strukturiert und typensicher unter
     `/src/modules/<MODULE_NAME>/`.
   - Greife **nicht** direkt auf Alt-Code aus `/legacy/` zu, sondern nutze
     Interfaces/DTOs gemäß OpenAPI-Spec.

3. **Routing-Umlenkung:**
   - Passe den zentralen Proxy/Router an (`/src/router/gateway.ts`), sodass Anfragen
     für `<MODULE_NAME>` ab sofort an das neue Modul in `/src/modules/<MODULE_NAME>/`
     geleitet werden.

4. **Verifizierung & Parallellauf:**
   - Führe erneut alle Tests aus: `npm test`.
   - Die ursprünglichen Characterization-Tests müssen weiterhin **GRÜN** sein
     (Regressionstest bestanden).

## Output
- **Ersetztes Legacy-Modul:** `/legacy/<MODULE_PATH>`
- **Neues Modul:** `/src/modules/<MODULE_NAME>/`
- **Aktivierte Routen:** Liste der umgeleiteten API-Endpunkte
- **Test-Ergebnis:** Bestätigung, dass alle Legacy- & New-Tests bestanden wurden

## Regeln
- Vor jeder Code-Änderung Characterization Tests schreiben.
- Kein direkter Zugriff auf `/legacy/`-Code im neuen Modul.
- Alle Tests müssen grün sein, bevor die Migration als abgeschlossen gilt.
