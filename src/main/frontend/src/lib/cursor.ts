/**
 * Die cursor-basierte Seitennavigation.
 *
 * <strong>Kein `total`</strong> (FR-030): die Liste des Vertrags nennt nur
 * `limit`, `has_more` und `next_cursor`. Die Oberflaeche zeigt deshalb keine
 * Gesamtzahl an, sondern nur, ob es eine weitere Seite gibt.
 *
 * <strong>Der Cursor steht in der URL</strong> (nicht im Zustand des
 * Browsers): die Seitennavigation ist damit eine **Linknavigation** und
 * funktioniert ohne JavaScript (ADR-018 §8).
 */

/** Der Zustand der Seitennavigation. */
export interface CursorState {
  /** Der Cursor der aktuellen Seite; `null` = die erste Seite. */
  current: string | null;
  /** Der Cursor der naechsten Seite; `null` = es gibt keine. */
  next: string | null;
  /** Ob es eine weitere Seite gibt. */
  hasMore: boolean;
}

/**
 * Baut den Link auf eine Seite der Liste.
 *
 * <strong>Die Filter bleiben erhalten</strong>: ein Cursor gilt nur fuer
 * dieselbe Filterung (der Cursor traegt den Sortierschluessel der Liste).
 *
 * @param path der Pfad der Seite, z. B. `/deliveries`
 * @param params die aktuellen Query-Parameter (ohne den Cursor)
 * @param cursor der Cursor der Zielseite; `null` = die erste Seite
 * @returns der Link auf die Zielseite
 */
export function pageLink(
  path: string,
  params: Readonly<Record<string, string>>,
  cursor: string | null,
): string {
  const query = new URLSearchParams();
  for (const [name, value] of Object.entries(params)) {
    if (name === 'cursor' || value.length === 0) {
      continue;
    }
    query.set(name, value);
  }
  if (cursor !== null && cursor.length > 0) {
    query.set('cursor', cursor);
  }

  const queryString = query.toString();
  return queryString.length > 0 ? `${path}?${queryString}` : path;
}
