# ADR-008: API-first (kein Frontend im MVP)

## Kontext
O-11, R-02: UI-Wunsch riskiert Scope-Creep im 1-2-Tage-Rahmen. Spec erlaubt
"nur API falls API-first". A-07: API-first, UI Could-Have.

## Entscheidung
- MVP liefert nur die REST-API + OpenAPI-Vertrag. Kein Frontend.
- UI ist Could-Have und erst nach MVP.
- Vertrag (OpenAPI) ist die Schnittstelle fuer spaetere UI/Integration.

## Konsequenzen
- Positiv: Scope eingehalten; Vertrag als stabile Basis.
- Negativ: Keine direkte Endnutzer-UI; Demo erfolgt ueber API (curl/Postman).

## Alternativen
- Minimales UI im MVP: verworfen (R-02, Zeitrahmen).
- Full-Stack mit React: verworfen (Scope, kein Bedarf).

## Status
Angenommen