# MVP Spec: Mini-Ticket-System (Kundenanforderung)

**Projekt:** mini-ticket  
**Ziel:** Kleines internes Ticket-System für Support-Anfragen  
**Zeitrahmen:** 1-2 Tage  
**Profil:** mvp

## Geschäftsanforderung
Das Support-Team soll einfache Tickets erfassen, bearbeiten und schließen können. Fokus auf schnelle Umsetzung, keine komplexe Workflow-Engine.

## Features
- Ticket anlegen (Titel, Beschreibung, Priorität: low/medium/high)
- Ticket-Liste mit Filter nach Status/Priorität
- Ticket-Detailansicht
- Status ändern (open/in_progress/resolved/closed)
- Einfache Kommentare

## Technische Anforderungen
- REST-API (intern)
- Einfache Persistenz (z.B. SQLite/Postgres)
- Minimales UI (oder nur API falls API-first)
- Keine externen Integrationen erforderlich

## Nicht-Funktional
- Funktional korrekt
- Keine Secrets im Code
- Grundlegende Validierung

## Akzeptanzkriterien
- [ ] Ticket kann angelegt werden
- [ ] Statuswechsel funktioniert
- [ ] Filter funktioniert
- [ ] Kommentare möglich
- [ ] Basis-Tests vorhanden

## Offene Punkte
- Authentifizierung: erstmal überspringen oder Basic (intern)
- Deployment: lokal/docker minimal
