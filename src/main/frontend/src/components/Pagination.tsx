import Link from 'next/link';

import { pageLink, type CursorState } from '@/lib/cursor';

/**
 * Die Seitennavigation der Listen.
 *
 * <strong>Keine Gesamtzahl</strong> (FR-030): der Vertrag nennt nur `limit`,
 * `has_more` und `next_cursor`. Die Anzeige sagt deshalb, ob es eine weitere
 * Seite gibt — nicht, wie viele Eintraege es insgesamt sind.
 *
 * <strong>Die Navigation sind Links</strong> (ADR-018 §8): der Cursor steht in
 * der URL der Seite. Damit blaettern die Listen auch ohne JavaScript, und
 * eine Seite laesst sich als Lesezeichen speichern.
 */

/** Die Eigenschaften der Seitennavigation. */
export interface PaginationProps {
  /** Der Pfad der Seite, z. B. `/deliveries`. */
  path: string;
  /** Die aktuellen Query-Parameter (ohne den Cursor). */
  params: Readonly<Record<string, string>>;
  /** Der Zustand der Navigation. */
  state: CursorState;
}

/**
 * Zeigt die Seitennavigation.
 *
 * @param props die Eigenschaften der Seitennavigation
 * @returns die Seitennavigation
 */
export function Pagination({ path, params, state }: PaginationProps) {
  const onFirstPage = state.current === null;

  const zustand = onFirstPage
    ? state.hasMore
      ? 'Erste Seite — es gibt eine weitere Seite.'
      : 'Erste Seite — keine weiteren Seiten.'
    : state.hasMore
      ? 'Es gibt eine weitere Seite.'
      : 'Letzte Seite erreicht.';

  return (
    <nav className="pagination" aria-label="Seitennavigation">
      {onFirstPage ? (
        <button type="button" className="button" disabled>
          Zurueck
        </button>
      ) : (
        <Link className="button" href={pageLink(path, params, null)}>
          Zurueck
        </Link>
      )}
      <p className="pagination__state" role="status" aria-live="polite">
        {zustand}
      </p>
      {state.next === null ? (
        <button type="button" className="button" disabled>
          Weiter
        </button>
      ) : (
        <Link className="button" href={pageLink(path, params, state.next)}>
          Weiter
        </Link>
      )}
    </nav>
  );
}
