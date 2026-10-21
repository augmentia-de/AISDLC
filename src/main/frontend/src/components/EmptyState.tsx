/**
 * Der Leerzustand.
 *
 * Eine leere Liste ist ein <strong>verstaendlicher Leerzustand</strong> mit
 * Erklaerung — keine leere Tabelle (AC-020-3, ADR-018 §5). Der Leerzustand
 * wird per `role="status"` angekuendigt, damit ein Screenreader ihn vorliest.
 */

/** Die Eigenschaften des Leerzustands. */
export interface EmptyStateProps {
  /** Der Titel — was ist leer? */
  title: string;
  /** Der Handlungsvorschlag (optional). */
  hint?: string;
}

/**
 * Zeigt den Leerzustand.
 *
 * @param props die Eigenschaften des Leerzustands
 * @returns der Leerzustand
 */
export function EmptyState({ title, hint }: EmptyStateProps) {
  return (
    <div className="empty-state" role="status">
      <p className="empty-state__title">{title}</p>
      {hint !== undefined ? <p className="empty-state__hint">{hint}</p> : null}
    </div>
  );
}
