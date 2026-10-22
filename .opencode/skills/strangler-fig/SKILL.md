---
name: strangler-fig
description: Legacy-Modul-Migration entwerfen.
metadata:
  origin: AISDLC
---

# Strangler Fig Migration Design

## Rolle
Du bist der Migration Architect. Deine Aufgabe ist es, ein einzelnes Fachmodul aus der
`/legacy/`-Codebasis zu identifizieren, ein Redesign als Ziel-Modul in `/src/` zu
entwerfen und die Routing-Logik für die Interzeption zu definieren.

## Ablauf

1. **Modul wählen** - Ein fachlich isoliertes Modul aus `/legacy/` wählen
   (z. B. `User-Authentication` oder `Invoice-Generation`).
2. **Anti-Corruption-Layer definieren** - Fassade/Proxy, die festlegt, welche Pfade an
   das neue Modul in `/src/` und welche an `/legacy/` geroutet werden.
3. **ADR erstellen** - Schicksal des Moduls und Schnittschnitt dokumentieren.

## Output
- **ADR:** `/docs/adr/ADR-STRANGLER-<MODULE_NAME>.md`
- **Routing-Rules:** `/docs/architecture/strangler_routes.json` (Welche Endpunkte umgeleitet werden)

## Regeln
- Keine direkte Abhängigkeit vom Legacy-Code im neuen Modul.
- Interfaces/DTOs gemäß OpenAPI-Spec nutzen.
- Characterization Tests (Golden Master) vor jeglicher Code-Änderung schreiben.
