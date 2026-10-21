/**
 * Der Aufruf einer Server Action im Test.
 *
 * <strong>Warum ueber einen Helfer?</strong> Eine Server Action beendet ihre
 * Arbeit mit einer **Weiterleitung** (`redirect`). Next.js wirft dafuer einen
 * Fehler mit dem Feld `digest` (`NEXT_REDIRECT;<ziel>`). Der Helfer faengt
 * genau diesen Fehler ab und liefert das Ziel — der Test kann damit pruefen,
 * **wohin** die Seite nach der Aktion zurueckleitet.
 */

/**
 * Das Praefix, mit dem Next.js die Weiterleitung kennzeichnet. */
const REDIRECT_PREFIX = 'NEXT_REDIRECT;';

/**
 * Baut die Query-Parameter einer Seite.
 *
 * @param werte die Werte der Query-Parameter
 * @returns die Query-Parameter als Promise (Next.js liefert sie so)
 */
export function searchParams(werte: Record<string, string>): Promise<Record<string, string>> {
  return Promise.resolve(werte);
}

/**
 * Ruft eine Server Action mit Formulardaten auf.
 *
 * @param action die Server Action
 * @param daten die Formulardaten
 * @returns das Ziel der Weiterleitung oder `null`, wenn die Aktion nicht
 *          weitergeleitet hat
 */
export async function runAction(
  action: (formData: FormData) => Promise<void>,
  daten: Record<string, string>,
): Promise<string | null> {
  const formData = new FormData();
  for (const [name, value] of Object.entries(daten)) {
    formData.append(name, value);
  }

  try {
    await action(formData);
  } catch (error) {
    const digest = (error as { digest?: unknown }).digest;
    if (typeof digest === 'string' && digest.startsWith(REDIRECT_PREFIX)) {
      return digest.slice(REDIRECT_PREFIX.length);
    }
    throw error;
  }

  return null;
}

/**
 * Baut eine JSON-Antwort des Dienstes.
 *
 * @param body der Antwortkoerper
 * @param status der HTTP-Statuscode
 * @returns die Antwort
 */
export function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status < 400,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}
