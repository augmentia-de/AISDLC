'use server';

import { revalidatePath } from 'next/cache';
import { redirect } from 'next/navigation';

import {
  parseEventTypes,
  validateEventTypes,
  validateSecret,
  validateTargetUrl,
} from '@/features/endpoints/validation';
import { api, ApiError } from '@/lib/apiClient';
import { clearSessionToken, readSession, writeSessionToken } from '@/lib/session';

/**
 * Die Aktionen der Oberflaeche — **Server Actions**.
 *
 * <strong>Warum Server Actions?</strong> ADR-018 §8 verlangt, dass alle drei
 * Seiten **ohne JavaScript** bedienbar sind: die interaktiven Elemente
 * (Pause/Reaktivieren, Secret-Rotation, Replay) arbeiten als **Formularaktion**.
 * Eine Server Action ist genau das — ein Formular, das ohne JavaScript als
 * normaler POST abgeschickt wird und dessen Ergebnis der Server verarbeitet.
 *
 * <strong>Das Ergebnis der Aktion</strong> steht als **Code** in der URL, auf
 * die die Seite zurueckleitet (`ergebnis=angelegt`). Der Text steht an einer
 * Stelle (`lib/feedback.ts`). Damit ist die Rueckmeldung bei jedem Aktionstyp
 * gleich — mit und ohne JavaScript.
 */

/** Das Muster einer Kennung, die in die URL uebernommen werden darf. */
const IDENTIFIER = /^[A-Za-z0-9-]{1,64}$/;

/**
 * Prueft eine Kennung, die in die URL uebernommen werden soll.
 *
 * @param value die Kennung
 * @returns die Kennung oder `null`, wenn sie nicht in die URL gehoert
 */
function safeIdentifier(value: FormDataEntryValue | null): string | null {
  const text = typeof value === 'string' ? value : '';
  return IDENTIFIER.test(text) ? text : null;
}

/**
 * Bestimmt den Ergebniscode aus einem Fehler des Dienstes.
 *
 * <strong>Mandant vor Rolle</strong> (V-39): 404 = fremde Ressource,
 * 403 = eigene Ressource mit falscher Rolle. Die Oberflaeche erfindet diese
 * Reihenfolge nicht, sondern zeigt genau das an, was der Dienst sagt.
 *
 * @param error der aufgefangene Fehler
 * @param secretCode der Code fuer einen Fehler am Secret-Feld
 * @returns der Ergebniscode
 */
function codeForProblem(error: unknown, secretCode: string): string {
  if (!(error instanceof ApiError)) {
    return 'dienst-fehler';
  }

  if (error.status === 401) {
    return 'unautorisiert';
  }
  if (error.status === 403) {
    return 'verboten';
  }
  if (error.status === 404) {
    return 'nicht-gefunden';
  }
  if (error.status === 409) {
    return 'konflikt';
  }
  if (error.status === 422) {
    const violation = error.violations[0];
    if (violation === undefined) {
      return 'dienst-fehler';
    }
    if (violation.field === 'secret') {
      return secretCode;
    }
    if (violation.field === 'event_types') {
      return 'typen-fehlen';
    }
    if (violation.field === 'target_url') {
      return 'url-ungueltig';
    }
    return 'dienst-fehler';
  }

  return 'dienst-fehler';
}

/**
 * Meldet sich an — das Token wird im **httpOnly-Cookie** abgelegt.
 *
 * @param formData die Formulardaten
 */
export async function signInAction(formData: FormData): Promise<void> {
  const token = String(formData.get('token') ?? '').trim();

  if (token.length === 0) {
    redirect('/?ergebnis=kein-token');
  }

  await writeSessionToken(token);
  redirect('/endpoints');
}

/**
 * Meldet ab — das Token-Cookie wird entfernt.
 */
export async function signOutAction(): Promise<void> {
  await clearSessionToken();
  redirect('/');
}

/**
 * Legt einen Endpunkt an (FR-001).
 *
 * <strong>Die Pruefung passiert vor dem Senden</strong> (AC-019-2): bei einem
 * Verstoess wird **kein** Endpunkt angelegt, und die Meldung steht am Feld.
 *
 * @param formData die Formulardaten
 */
export async function createEndpointAction(formData: FormData): Promise<void> {
  const targetUrl = String(formData.get('target_url') ?? '').trim();
  const secret = String(formData.get('secret') ?? '').trim();
  const eventTypes = String(formData.get('event_types') ?? '');

  const urlProblem = validateTargetUrl(targetUrl);
  if (urlProblem !== null) {
    redirect(`/endpoints?ergebnis=${urlProblem}`);
  }

  const secretProblem = validateSecret(secret);
  if (secretProblem !== null) {
    redirect(`/endpoints?ergebnis=${secretProblem}`);
  }

  const typesProblem = validateEventTypes(eventTypes);
  if (typesProblem !== null) {
    redirect(`/endpoints?ergebnis=${typesProblem}`);
  }

  const session = await readSession();

  let code: string | null = null;
  try {
    await api.post('/endpoints', session.token, {
      target_url: targetUrl,
      secret,
      event_types: parseEventTypes(eventTypes),
    });
  } catch (error) {
    code = codeForProblem(error, 'secret-zu-kurz');
  }

  if (code !== null) {
    redirect(`/endpoints?ergebnis=${code}`);
  }

  revalidatePath('/endpoints');
  redirect('/endpoints?ergebnis=angelegt');
}

/**
 * Pausiert einen Endpunkt (FR-002).
 *
 * @param formData die Formulardaten
 */
export async function pauseEndpointAction(formData: FormData): Promise<void> {
  const endpointId = safeIdentifier(formData.get('endpoint_id'));
  if (endpointId === null) {
    redirect('/endpoints?ergebnis=nicht-gefunden');
  }

  const session = await readSession();

  let code: string | null = null;
  try {
    await api.post(`/endpoints/${endpointId}/pause`, session.token);
  } catch (error) {
    code = codeForProblem(error, 'secret-zu-kurz');
  }

  if (code !== null) {
    redirect(`/endpoints?ergebnis=${code}`);
  }

  revalidatePath('/endpoints');
  redirect('/endpoints?ergebnis=pausiert');
}

/**
 * Reaktiviert einen Endpunkt (FR-002).
 *
 * @param formData die Formulardaten
 */
export async function resumeEndpointAction(formData: FormData): Promise<void> {
  const endpointId = safeIdentifier(formData.get('endpoint_id'));
  if (endpointId === null) {
    redirect('/endpoints?ergebnis=nicht-gefunden');
  }

  const session = await readSession();

  let code: string | null = null;
  try {
    await api.post(`/endpoints/${endpointId}/resume`, session.token);
  } catch (error) {
    code = codeForProblem(error, 'secret-zu-kurz');
  }

  if (code !== null) {
    redirect(`/endpoints?ergebnis=${code}`);
  }

  revalidatePath('/endpoints');
  redirect('/endpoints?ergebnis=reaktiviert');
}

/**
 * Rotiert das Secret eines Endpunkts (FR-003).
 *
 * <strong>Das neue Secret wird nie angezeigt</strong> (AC-019-4): die
 * Bestaetigung nennt nur die neue Version.
 *
 * @param formData die Formulardaten
 */
export async function rotateEndpointSecretAction(formData: FormData): Promise<void> {
  const endpointId = safeIdentifier(formData.get('endpoint_id'));
  if (endpointId === null) {
    redirect('/endpoints?ergebnis=nicht-gefunden');
  }

  const secret = String(formData.get('secret') ?? '').trim();

  const secretProblem = validateSecret(secret);
  if (secretProblem !== null) {
    redirect(
      `/endpoints?ergebnis=rotation-secret-zu-kurz&endpunkt=${encodeURIComponent(endpointId)}`,
    );
  }

  const session = await readSession();

  let code: string | null = null;
  try {
    await api.post(`/endpoints/${endpointId}/secret-rotation`, session.token, { secret });
  } catch (error) {
    code = codeForProblem(error, 'rotation-secret-zu-kurz');
  }

  if (code !== null) {
    redirect(
      `/endpoints?ergebnis=${code}&endpunkt=${encodeURIComponent(endpointId)}`,
    );
  }

  revalidatePath('/endpoints');
  redirect(`/endpoints?ergebnis=rotiert&endpunkt=${encodeURIComponent(endpointId)}`);
}

/**
 * Loest einen aufgegebenen Zustellvorgang erneut aus (FR-024).
 *
 * <strong>Nur der Empfaenger-Admin des Endpunkts</strong> darf das (F-03,
 * V-39). Der Dienst prueft das; die Oberflaeche bietet die Aktion nur an.
 *
 * @param formData die Formulardaten
 */
export async function replayDeliveryAction(formData: FormData): Promise<void> {
  const deliveryRunId = safeIdentifier(formData.get('delivery_run_id'));
  if (deliveryRunId === null) {
    redirect('/dead-deliveries?ergebnis=nicht-gefunden');
  }

  const session = await readSession();

  let code: string | null = null;
  try {
    await api.post(`/deliveries/${deliveryRunId}/replay`, session.token, {});
  } catch (error) {
    code = codeForProblem(error, 'secret-zu-kurz');
  }

  if (code !== null) {
    redirect(`/dead-deliveries?ergebnis=${code}`);
  }

  revalidatePath('/dead-deliveries');
  redirect('/dead-deliveries?ergebnis=replayed');
}
