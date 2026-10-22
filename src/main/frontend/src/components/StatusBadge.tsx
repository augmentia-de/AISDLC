/**
 * Die Zustandsanzeige.
 *
 * <strong>Der Zustand wird nie allein ueber Farbe transportiert</strong>
 * (ADR-018 §3): die Anzeige traegt den <strong>Text</strong> des Zustands,
 * die Farbtonfamilie und ein <strong>unterscheidbares Merkmal</strong> (die
 * Form). Wer die Farben nicht unterscheiden kann, liest den Text.
 */

/** Die Farbtonfamilie der Anzeige. */
export type BadgeTone = 'aktiv' | 'pausiert' | 'erfolg' | 'fehler' | 'offen' | 'neutral';

/**
 * Die Darstellung eines Zustands — **eine** Quelle fuer alle Bausteine.
 *
 * <strong>Die Endpunktseite, das Zustellprotokoll und die Totenliste zeigen
 * denselben Zustand auf dieselbe Weise:</strong> Text, Farbtonfamilie und ein
 * unterscheidbares Merkmal. Der Zustand haengt <strong>nie</strong> allein an
 * der Farbe (ADR-018 §3).
 */
export interface StatusPresentation {
  /** Der Text des Zustands. */
  label: string;
  /** Die Farbtonfamilie. */
  tone: BadgeTone;
  /** Das unterscheidbare Merkmal neben dem Text. */
  mark: string;
}

/** Die Eigenschaften der Zustandsanzeige. */
export type StatusBadgeProps = StatusPresentation;

/**
 * Zeigt einen Zustand als Text, Farbe und Form.
 *
 * @param props die Eigenschaften der Anzeige
 * @returns die Anzeige
 */
export function StatusBadge({ label, tone, mark }: StatusBadgeProps) {
  return (
    <span className={`badge badge--${tone}`}>
      <span className="badge__mark" aria-hidden="true">
        {mark}
      </span>
      <span className="badge__label">{label}</span>
    </span>
  );
}
