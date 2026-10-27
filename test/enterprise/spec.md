# Enterprise Spec: Kundenportal-API (Kundenanforderung)

**Projekt:** customer-portal-api  
**Ziel:** Extern nutzbare Kundenportal-API mit strenger Governance  
**Profil:** enterprise  
**Compliance:** hohe Anforderungen an Nachvollziehbarkeit & Sicherheit

## Geschäftsanforderung
Kunden sollen über ein Portal Aufträge einsehen, Dokumente herunterladen und Support-Tickets erstellen. Die API wird von Partnern genutzt -> externer Vertrag.

## Features
- OAuth2/OIDC Authentifizierung & Autorisierung (RBAC)
- Auftragsübersicht mit Pagination/Filter
- Dokumenten-Download (pre-signed URLs, Audit)
- Support-Tickets mit Statusverlauf
- Benachrichtigungen
- Mandantenfähigkeit (Multi-Tenant)

## Externe Schnittstellen
- REST API gemäß Vertrag (OpenAPI) - `docs/api/openapi.yaml` (external/public)
- Webhooks für Statusänderungen

## Nicht-Funktional
- Sicherheit: 0 Critical/High Findings
- Traceability: Req -> Story -> Test -> Code vollständig
- API-Vertrag eingefroren nach Schritt 4
- Audit-Logging
- DSGVO-relevante Aspekte
- Vollständige ADRs
- C4-Diagramme

## Akzeptanzkriterien
- [ ] Vollständige OpenAPI-Spezifikation
- [ ] AuthN/Z, RBAC durchgetestet
- [ ] Traceability 100% belegt
- [ ] Security-Scan sauber
- [ ] Deployment-Pipeline dokumentiert

## Risiken
- Vertragspartnerbindung
- Datenschutz
- Zugriffskontrolle
