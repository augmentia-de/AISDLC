# ADR-009: Fehlerformat (strukturierte Fehlerantwort)

## Kontext
FR-020, AC-009-3: Fehlerantworten folgen {error, message, details}. NFR-002/SEC-002.

## Entscheidung
- Alle 4xx/5xx-Antworten verwenden das Envelope:
  `{ "error": "<code>", "message": "<text>", "details": [ {"field":..., "message":...} ] }`.
- error: maschinenlesbarer Code (z. B. validation_failed, not_found, unauthorized).
- message: englisch, Menschenlesbar.
- details: optional, bei Validierungsfehlern feldbezogen.
- Kein Stacktrace/interne Details in Antworten (SEC-001).
- Globaler Exception-Handler mappt Exceptions auf dieses Format.

## Konsequenzen
- Positiv: Einheitlich, testbar, AC-009-3 erfuellt.
- Negativ: Abweichung vom RFC 7807 Problem Details; aber Anforderung bindend.

## Alternativen
- RFC 7807 (type/title/status/detail/instance): verworfen, weil AC-009-3 explizit
  {error, message, details} verlangt.

## Status
Angenommen