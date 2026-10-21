import { ApiError } from '@/lib/apiClient';
import type { Page, Problem } from '@/types/api';

/**
 * Das Ergebnis einer Listenabfrage.
 *
 * <strong>Mandant vor Rolle</strong> (V-39): ein 404 bedeutet eine fremde
 * Ressource. Der Dienst antwortet dann mit 404; die Seite zeigt den
 * Leerzustand und <strong>keine</strong> fremden Daten (AC-020-4,
 * ADR-018 §6). Ein 403 ist eine eigene Ressource mit falscher Rolle und
 * wird als Rollenmeldung angezeigt.
 */

/** Das Ergebnis einer Listenabfrage. */
export interface ListResult<T> {
  /** Die Eintraege der Seite. */
  items: T[];
  /** Die Seitennavigation der Antwort. */
  page: Page;
  /** Das Fehlerbild der Abfrage (oder einer Aktion) — `null`, wenn es keines gibt. */
  problem: Problem | null;
}

/** Die Zahl der Eintraege je Seite — der Vorgabewert des Vertrags. */
export const PAGE_LIMIT = 50;

/**
 * Die Seitennavigation einer leeren Liste.
 *
 * @returns die Seitennavigation ohne weitere Seite
 */
export function emptyPage(): Page {
  return { limit: PAGE_LIMIT, has_more: false, next_cursor: null };
}

/**
 * Liest das Fehlerbild aus einem Fehler der API.
 *
 * @param error der aufgefangene Fehler
 * @returns das Fehlerbild
 */
export function toProblem(error: unknown): Problem {
  if (error instanceof ApiError) {
    return {
      type: 'about:blank',
      title: error.message,
      status: error.status,
      code: error.code,
      detail: error.detail,
      violations: error.violations,
    };
  }
  return {
    type: 'about:blank',
    title: 'Fehler',
    status: 0,
    code: 'internal_error',
    detail: 'Die Verbindung zum Dienst ist fehlgeschlagen.',
    violations: null,
  };
}
