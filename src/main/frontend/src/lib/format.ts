/**
 * Die Formatierung der Zeitpunkte und Zahlen der Oberflaeche.
 *
 * <strong>UTC:</strong> die Zeitpunkte des Vertrags sind RFC-3339-Zeitpunkte
 * in UTC. Die Anzeige rechnet sie in die UTC-Anzeige um — unabhaengig von der
 * Zeitzone des Browsers. Sonst wuerde derselbe Vorgang je nach Browser
 * anders aussehen.
 */

/** Der Platzhalter fuer einen fehlenden Wert. */
export const EMPTY_VALUE = '—';

/**
 * Fuellt eine Zahl auf zwei Stellen auf.
 *
 * @param value die Zahl
 * @returns die zweistellige Zeichenfolge
 */
function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value);
}

/**
 * Formatiert einen Zeitpunkt als `TT.MM.JJJJ HH:MM:SS` (UTC).
 *
 * @param value der Zeitpunkt des Vertrags (RFC 3339) oder `null`
 * @returns die formatierte Zeichenfolge — der Platzhalter, wenn der Wert fehlt
 */
export function formatTimestamp(value: string | null | undefined): string {
  if (value === null || value === undefined || value.length === 0) {
    return EMPTY_VALUE;
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return EMPTY_VALUE;
  }

  const day = pad(date.getUTCDate());
  const month = pad(date.getUTCMonth() + 1);
  const hours = pad(date.getUTCHours());
  const minutes = pad(date.getUTCMinutes());
  const seconds = pad(date.getUTCSeconds());

  return `${day}.${month}.${date.getUTCFullYear()} ${hours}:${minutes}:${seconds}`;
}

/**
 * Formatiert einen Antwortcode des Empfaengers.
 *
 * @param value der HTTP-Statuscode oder `null`
 * @returns die Zahl als Zeichenfolge — der Platzhalter, wenn es keinen gab
 */
export function formatResponseStatus(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return EMPTY_VALUE;
  }
  return String(value);
}

/**
 * Formatiert die Zahl der Versuche mit ihrer Grenze.
 *
 * @param attempts die Zahl der ausgefuehrten Versuche
 * @param maxAttempts die Versuchsgrenze oder `null`
 * @returns die Zeichenfolge, z. B. `2 / 8`
 */
export function formatAttempts(attempts: number, maxAttempts: number | null | undefined): string {
  return maxAttempts === null || maxAttempts === undefined
    ? String(attempts)
    : `${attempts} / ${maxAttempts}`;
}

/**
 * Wandelt ein Datum (`JJJJ-MM-TT`) in den Anfang des Tages in UTC.
 *
 * @param value das Datum aus dem Datumsfeld
 * @returns der RFC-3339-Zeitpunkt oder `null`
 */
export function dateToStartOfDay(value: string): string | null {
  if (value.length === 0) {
    return null;
  }
  const date = new Date(`${value}T00:00:00.000Z`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/**
 * Wandelt ein Datum (`JJJJ-MM-TT`) in das Ende des Tages in UTC.
 *
 * @param value das Datum aus dem Datumsfeld
 * @returns der RFC-3339-Zeitpunkt oder `null`
 */
export function dateToEndOfDay(value: string): string | null {
  if (value.length === 0) {
    return null;
  }
  const date = new Date(`${value}T23:59:59.999Z`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
