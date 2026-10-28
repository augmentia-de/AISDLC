# ADR-004: Auth-Strategie (HTTP Basic, optional/umgebungs-gesteuert)

## Kontext
SEC-003 ist offen (O-02). R-01: API ohne Schutz im internen Netz. Spec: ueberspringen
oder Basic. PRD empfiehlt Basic Auth + Session fuer internes Tool.

## Entscheidung
- Authentifizierung: HTTP Basic Auth ueber Spring Security.
- Aktivierung umgebungs-gesteuert: `AUTH_ENABLED=true|false` (Default `false` in Dev/Test,
  `true` in Produktion). Credentials ueber `AUTH_USER`/`AUTH_PASSWORD` (Umgebungsvariablen,
  SEC-001).
- Kein User-Management, keine Sessions, kein JWT im MVP.
- /health (und Alias) sind oeffentlich (security: []).
- Bei aktivierter Auth: 401 bei fehlenden/ungueltigen Credentials.

## Konsequenzen
- Positiv: Minimal, intern ausreichend, keine Secret-Speicherung im Code.
- Negativ: Basic Auth ohne HTTPS unsicher -> nur ueber TLS in Prod (Deployment-Verantwortung).
  Keine feingranulare Autorisierung (nur ein Account).
- R-01 teilweise gemindert; fuer externe Exposition nicht geeignet (aber CON-001 intern).

## Alternativen
- Keine Auth: verworfen fuer Prod (R-01), aber als Dev-Default beibehalten.
- JWT/OAuth2: verworfen (Overkill fuer internes Tool, Scope).
- API-Key: verworfen (kein Vorteil gegenueber Basic fuer internen Gebrauch).

## Status
Angenommen