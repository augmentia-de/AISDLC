---
name: aisdlc-simplify
description: Vereinfacht Code verhaltenserhaltend und entfernt nachweislich ungenutzten Code - klarer, wartbarer, ohne Funktionsaenderung. Nutze diesen Skill in Phase 3 (Simplify vor dem Stresstest) und in Phase 7 (zweiter Durchlauf nach der Implementierung).
metadata:
  origin: adaptiert aus ECC/code-simplifier + ECC/refactor-cleaner + wshobson/refactor-clean + wshobson/tech-debt
---

## Wann

- **Phase 3 (Simplify & Reduktion):** vor dem Grill-Me-Stresstest, damit nur Wesentliches geprüft wird.
- **Phase 7 (Refactoring & Clean-up):** zweiter Durchlauf auf dem fertigen Code - dort sieht man
  defensiven Code, der durch Tests ersetzt wurde, und Abstraktionen, die sich nicht bewährt haben.

## Grundsätze

1. Klarheit vor Clevernheit.
2. Konsistenz mit dem Repo-Stil, nicht mit dem persönlichen Stil.
3. Verhalten exakt erhalten - die Vereinfachung darf nichts ändern.
4. Nur vereinfachen, wo das Ergebnis **nachweisbar** leichter zu warten ist.

## Simplify-Ziele

**Struktur**
- tief verschachtelte Logik in benannte Funktionen extrahieren
- komplexe Bedingungen durch Early Returns ersetzen, wo das klarer ist
- verkettete Callbacks/Then-Ketten auflösen
- toten Code und ungenutzte Importe entfernen

**Lesbarkeit**
- beschreibende Namen statt Abkürzungen
- keine verschachtelten Ternaries
- lange Ketten in Zwischenvariablen zerlegen
- Destrukturierung, wenn sie den Zugriff klarer macht

**Qualität**
- Debug-Ausgaben entfernen
- auskommentierten Code entfernen
- duplizierte Logik zusammenführen
- über-abstrahierte Single-Use-Helfer auflösen

## Dead-Code-Erkennung (stack-agnostisch)

Statische Analyse des Projekts nutzen, z. B. `knip`, `depcheck`, `ts-prune`, `vulture`,
`jdeps`, `cargo-machete` oder das Analysewerkzeug der jeweiligen Sprache. Falls keines
vorhanden ist: Import- und Referenzsuche (`grep`) als Ersatz.

Ergebnisse nach Risiko einstufen:

| Risiko | Bedeutung |
|---|---|
| **SAFE** | ungenutzte Exporte, Importe, Abhängigkeiten, Dateien |
| **CAREFUL** | dynamische Importe, Reflection, Registrierung per String |
| **RISKY** | Teil einer öffentlichen API, von außen referenziert |

## Ablauf

1. **Analysieren** - Erkennungswerkzeuge parallel laufen lassen, Ergebnisse einstufen.
2. **Verifizieren** - für jeden Kandidaten alle Referenzen suchen (auch dynamische),
   öffentliche API prüfen, Git-Historie auf Kontext ansehen.
3. **Sicher entfernen** - nur SAFE zuerst, eine Kategorie pro Durchgang
   (Abhängigkeiten → Exporte → Dateien → Duplikate), nach jedem Batch Tests laufen lassen.
4. **Duplikate zusammenführen** - die beste Implementierung behalten (vollständigste,
   am besten getestete), Importe anpassen, Überzähliges löschen, Tests prüfen.

## Technische Schulden

- Inventar aufnehmen, each Debt-Item nach Auswirkung bewerten, priorisierte Liste mit
  geschätztem Aufwand erzeugen.
- Nicht beheben, was außerhalb des aktuellen Auftrags liegt - dokumentieren statt liegen lassen.

## Sicherheitsregeln

- Keine Verhaltensaenderung, auch keine "offensichtliche" Verbesserung der Fachlogik.
- Nichts löschen, ohne Beleg: keine Referenz im Code, keine Reflection, keine
  Konfiguration, keine externe Nutzung.
- Keine Konfigurations- oder Formatierungs-Änderung als Nebeneffekt.
- Keine Abhängigkeit entfernen, ohne die Nutzung zu belegen.
- Nach jedem Schritt Tests laufen lassen und das Ergebnis nennen.

## Output

- Liste der Vereinfachungen mit Datei:Zeile und Begründung
- Liste des entfernten Dead Codes mit Beleg (keine Referenz gefunden)
- Verifikationsnachweis (Tests, Coverage vorher/nachher)
- Verbleibende technische Schulden mit Priorität
