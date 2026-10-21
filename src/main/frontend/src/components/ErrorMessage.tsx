/**
 * Die Fehlermeldung einer Liste oder einer Aktion.
 *
 * <strong>`role="alert"`</strong> — der Screenreader liest die Meldung sofort
 * vor (ADR-018 §2). Der Text ist <strong>verstaendlich</strong>: er sagt, was
 * zu tun ist, und nennt keinen technischen Fehlercode.
 */

/** Die Eigenschaften der Fehlermeldung. */
export interface ErrorMessageProps {
  /** Der Text der Meldung. */
  message: string;
}

/**
 * Zeigt eine Fehlermeldung.
 *
 * @param props die Eigenschaften der Fehlermeldung
 * @returns die Fehlermeldung
 */
export function ErrorMessage({ message }: ErrorMessageProps) {
  return (
    <p className="error-message" role="alert">
      {message}
    </p>
  );
}
