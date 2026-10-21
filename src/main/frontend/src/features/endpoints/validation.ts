/**
 * Die Feldpruefung des Registrierungsformulars.
 *
 * <strong>Die Pruefung passiert vor dem Senden</strong> (AC-019-2): eine
 * ungueltige Ziel-URL erzeugt eine verstaendliche Meldung <strong>am
 * Feld</strong> — und es wird kein Endpunkt angelegt. Der Dienst prueft
 * trotzdem; die Pruefung hier ersetzt die Pruefung dort nicht.
 *
 * <strong>Warum ein Code statt einer Meldung?</strong> Das Ergebnis der
 * Pruefung geht als **Code** in die URL der Seite (`ergebnis=url-ungueltig`).
 * Der Text steht an **einer** Stelle — in `lib/feedback.ts`. So kann die
 * Meldung nicht an zwei Orten auseinanderlaufen.
 */

/** Das Ergebnis der Pruefung der Ziel-URL — `null`, wenn der Wert gueltig ist. */
export type TargetUrlProblem = 'url-fehlt' | 'url-zu-lang' | 'url-fehlerhaft' | 'url-ungueltig' | 'url-zugangsdaten';

/** Das Ergebnis der Pruefung des Secrets — `null`, wenn der Wert gueltig ist. */
export type SecretProblem = 'secret-zu-kurz';

/** Das Ergebnis der Pruefung der Ereignistypen — `null`, wenn der Wert gueltig ist. */
export type EventTypesProblem = 'typen-fehlen';

/** Die hoechste Laenge der Ziel-URL (`EndpointCreateRequest.target_url`). */
export const TARGET_URL_MAX_LENGTH = 2000;

/** Die kleinste Laenge des Secrets (`EndpointCreateRequest.secret`). */
export const SECRET_MIN_LENGTH = 16;

/**
 * Prueft die Ziel-URL.
 *
 * @param value der eingegebene Wert
 * @returns der Fehlercode oder `null`, wenn der Wert gueltig ist
 */
export function validateTargetUrl(value: string): TargetUrlProblem | null {
  const trimmed = value.trim();

  if (trimmed.length === 0) {
    return 'url-fehlt';
  }

  if (trimmed.length > TARGET_URL_MAX_LENGTH) {
    return 'url-zu-lang';
  }

  let url: URL;
  try {
    url = new URL(trimmed);
  } catch {
    return 'url-fehlerhaft';
  }

  if (url.protocol !== 'https:') {
    return 'url-ungueltig';
  }

  if (url.username.length > 0 || url.password.length > 0) {
    return 'url-zugangsdaten';
  }

  return null;
}

/**
 * Prueft das Secret.
 *
 * @param value der eingegebene Wert
 * @returns der Fehlercode oder `null`, wenn der Wert gueltig ist
 */
export function validateSecret(value: string): SecretProblem | null {
  return value.trim().length < SECRET_MIN_LENGTH ? 'secret-zu-kurz' : null;
}

/**
 * Prueft die Ereignistypen.
 *
 * @param value die eingegebenen Ereignistypen
 * @returns der Fehlercode oder `null`, wenn der Wert gueltig ist
 */
export function validateEventTypes(value: string): EventTypesProblem | null {
  return parseEventTypes(value).length === 0 ? 'typen-fehlen' : null;
}

/**
 * Zerlegt die Eingabe der Ereignistypen.
 *
 * @param value der eingegebene Wert (kommagetrennt)
 * @returns die Ereignistypen ohne Leerzeichen und ohne Duplikate
 */
export function parseEventTypes(value: string): string[] {
  const types = value
    .split(',')
    .map((type) => type.trim())
    .filter((type) => type.length > 0);

  return [...new Set(types)];
}
