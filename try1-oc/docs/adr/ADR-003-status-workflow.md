# ADR-003: Status-Workflow (keine Transition-Validierung)

## Kontext
FR-007 erlaubt Status open/in_progress/resolved/closed. O-04 fragt nach erlaubten
Uebergaengen; O-15 nach Semantik resolved vs. closed. A-02 entscheidet: keine Engine.

## Entscheidung
- Status ist ein Enum-Feld; jeder Wert aus der Enum ist jederzeit setzbar (PATCH).
- Keine Transition-Validierung, keine Zustandsmaschine.
- Semantik (A-10): resolved = Loesung vorgeschlagen; closed = abgeschlossen/akzeptiert.
- Ungueltige Werte werden mit 400 abgelehnt (FR-018).

## Konsequenzen
- Positiv: Einfach, schnell umsetzbar, flexibel fuer Support-Team.
- Negativ: Inkonsistente Statusfolgen moeglich; keine Audit-Historie (Out-of-Scope).
- Reporting kann Status inkonsistent sehen — akzeptiert (R-03).

## Alternativen
- Strenge Zustandsmaschine (nur definierte Uebergaenge): verworfen (A-02, Scope).
- Status-Historie/Audit-Log: verworfen (Out-of-Scope, Won't-Have).

## Status
Angenommen