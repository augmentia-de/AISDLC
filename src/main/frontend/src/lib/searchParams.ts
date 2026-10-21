/**
 * Die Query-Parameter der Seiten.
 *
 * <strong>Die Filter und die Seitennavigation liegen in der URL</strong> —
 * nicht im Zustand des Browsers. Das ist die Grundlage der progressiven
 * Erhoehung (ADR-018 §8): ein Filter ist ein **GET-Formular**, die
 * Seitennavigation sind **Links**. Beides funktioniert ohne JavaScript, und
 * eine Seite laesst sich als Lesezeichen speichern und weitergeben.
 */

/** Die Query-Parameter einer Seite — Next.js liefert sie als Promise. */
export type SearchParams = Promise<Record<string, string | string[] | undefined>>;

/**
 * Liest den ersten Wert eines Query-Parameters.
 *
 * @param value der Rohwert des Parameters
 * @returns der Wert oder `null`, wenn der Parameter fehlt
 */
export function param(value: string | string[] | undefined): string | null {
  if (typeof value === 'string') {
    return value;
  }
  if (Array.isArray(value) && value.length > 0) {
    return value[0] ?? null;
  }
  return null;
}
