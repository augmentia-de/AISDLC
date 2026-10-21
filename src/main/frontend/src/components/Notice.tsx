/**
 * Die Bestaetigung einer Aktion.
 *
 * <strong>`role="status"`</strong> mit `aria-live="polite"` — der
 * Screenreader liest die Bestaetigung vor, ohne den Nutzer zu unterbrechen
 * (ADR-018 §2). Sie ersetzt <strong>keinen</strong> Fehler am Feld: ein
 * Feldfehler bleibt am Feld (ADR-018 §7).
 */

/** Die Eigenschaften der Bestaetigung. */
export interface NoticeProps {
  /** Der Text der Bestaetigung. */
  message: string;
}

/**
 * Zeigt eine Bestaetigung.
 *
 * @param props die Eigenschaften der Bestaetigung
 * @returns die Bestaetigung
 */
export function Notice({ message }: NoticeProps) {
  return (
    <p className="notice" role="status" aria-live="polite">
      {message}
    </p>
  );
}
