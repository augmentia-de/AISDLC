/**
 * Die Fehlanzeige am Feld.
 *
 * Ein feldbezogener Fehler steht <strong>am Feld</strong>, mit
 * `aria-describedby` am Feld verknuepft und mit `role="alert"`, damit ein
 * Screenreader ihn ansagt (ADR-018 §7). Ein Fehler, der nur als Toast
 * erscheint, wird von Screenreadern verpasst.
 */

/** Die Eigenschaften der Fehlanzeige. */
export interface FieldErrorProps {
  /** Die ID des Elements — sie steht in `aria-describedby` des Feldes. */
  id: string;
  /** Die Meldung aus `Problem.violations`. */
  message: string;
}

/**
 * Zeigt einen feldbezogenen Fehler.
 *
 * @param props die Eigenschaften der Fehlanzeige
 * @returns die Fehlanzeige
 */
export function FieldError({ id, message }: FieldErrorProps) {
  return (
    <p className="field-error" id={id} role="alert">
      {message}
    </p>
  );
}
