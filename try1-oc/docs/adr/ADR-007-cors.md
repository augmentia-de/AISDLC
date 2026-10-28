# ADR-007: CORS-Konfiguration

## Kontext
SEC-004: CORS nur fuer konfigurierte Origins (falls UI). O-16. UI ist Could-Have
(A-07, API-first).

## Entscheidung
- CORS standardmaessig deaktiviert (keine Origins erlaubt) — API-first, kein Frontend
  im MVP.
- Konfiguration ueber Umgebungsvariable `CORS_ALLOWED_ORIGINS` (kommasepariert).
- Nur konfigurierte Origins; Credentials optional (Default false).
- Preflight (OPTIONS) nur fuer erlaubte Origins.

## Konsequenzen
- Positiv: Keine unbeabsichtigte Cross-Origin-Freigabe; bei UI-Nachruestung einfach
  konfigurierbar.
- Negativ: Frontend-Entwicklung erfordert explizite Konfiguration.

## Alternativen
- CORS fuer alle Origins: verworfen (SEC-004).
- CORS deaktiviert (kein Filter): verworfen, weil UI Could-Have nachruestbar sein soll.

## Status
Angenommen