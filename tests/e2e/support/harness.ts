import type { BrowserContext } from '@playwright/test';

/**
 * Die gemeinsamen Bausteine der Browser-Tests.
 *
 * <strong>Die Anmeldung</strong>: die Oberflaeche ist ein Server Component
 * (ADR-018 §8); das Bearer-Token liegt in einem httpOnly-Cookie. Der Test
 * legt dieses Cookie direkt an — der Weg ueber das Anmeldeformular ist in
 * {@code tests/unit/frontend/} geprueft.
 *
 * <strong>Der Dienst-Doppelgaenger</strong> steht an der Dienstgrenze
 * (`tests/e2e/support/mock-api.mjs`). Die Tests steuern seinen Zustand ueber
 * `/__control/zustand` — damit ist jeder Testfall eindeutig.
 */

/** Die Adresse des Dienst-Doppelgaengers. */
export const DIENST = process.env.ECHOBOX_MOCK_BASE_URL ?? 'http://127.0.0.1:4010';

/** Der Endpunkt der Testdaten. */
export const ENDPOINT = {
  endpoint_id: '8f1c2f0e-2f4a-4a0e-9d5b-6a1c2e3f4a5b',
  organisation_id: 'm-1',
  target_url: 'https://orders.example.invalid/hooks/echobox',
  status: 'active',
  event_types: ['order.changed'],
  secret_version: 1,
  secret_rotated_at: null,
  paused_at: null,
  created_at: '2026-10-21T09:10:00.000Z',
  updated_at: '2026-10-21T09:10:00.000Z',
};

/** Der Zustelllauf der Testdaten. */
export const DELIVERY = {
  delivery_run_id: '7d2b1c9a-5e6f-4a0b-8c7d-1e2f3a4b5c6d',
  event_id: '3c9a7d21-8e4b-4a1f-9c2d-5e6f7a8b9c0d',
  endpoint_id: ENDPOINT.endpoint_id,
  organisation_id: 'm-1',
  event_type: 'order.changed',
  status: 'failed',
  attempt_count: 2,
  max_attempts: 8,
  replay_of: null,
  last_attempt_at: '2026-10-21T09:16:24.000Z',
  next_attempt_at: '2026-10-21T09:18:24.190Z',
  last_response_status: 503,
  last_error_code: null,
  rejection_reason: null,
  created_at: '2026-10-21T09:15:22.481Z',
  updated_at: '2026-10-21T09:16:24.190Z',
};

/** Die aufgegebene Zustellung der Testdaten. */
export const DEAD_DELIVERY = {
  delivery_run_id: 'b4c3d2e1-7f60-4a5b-9c8d-2e3f4a5b6c7d',
  event_id: '5d8e9f0a-1b2c-4d3e-8f90-a1b2c3d4e5f6',
  organisation_id: 'm-1',
  endpoint_id: ENDPOINT.endpoint_id,
  endpoint_url: ENDPOINT.target_url,
  event_type: 'order.changed',
  status: 'abandoned',
  attempt_count: 8,
  last_response_status: 503,
  last_attempt_at: '2026-10-21T10:22:11.900Z',
  abandoned_at: '2026-10-21T10:22:11.900Z',
  created_at: '2026-10-21T09:40:05.120Z',
};

/**
 * Kodiert einen Text in Base64url.
 *
 * @param text der Text
 * @returns die Base64url-Kodierung
 */
function base64url(text: string): string {
  return Buffer.from(text, 'utf8')
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

/**
 * Baut ein Token mit den Rollen und dem Mandanten einer Identitaet.
 *
 * @param rollen die Rollen der Identitaet
 * @param organisationId der Mandant der Identitaet
 * @returns das Token
 */
export function token(rollen: string[], organisationId = 'm-1'): string {
  const header = base64url(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const nutzlast = base64url(JSON.stringify({ roles: rollen, organisation_id: organisationId }));
  return `${header}.${nutzlast}.testsignatur`;
}

/**
 * Meldet den Test an — das Token liegt im httpOnly-Cookie.
 *
 * @param context der Browser-Kontext
 * @param rollen die Rollen der Identitaet
 * @param organisationId der Mandant der Identitaet
 * @param basis die Adresse der Oberflaeche
 */
export async function anmelden(
  context: BrowserContext,
  rollen: string[],
  organisationId = 'm-1',
  basis = 'http://127.0.0.1:3000',
): Promise<void> {
  await context.addCookies([
    {
      name: 'echobox.token',
      value: token(rollen, organisationId),
      url: basis,
      httpOnly: true,
      sameSite: 'Lax',
    },
  ]);
}

/**
 * Setzt die Daten des Dienst-Doppelgaengers.
 *
 * @param request die API des Testclients
 * @param zustand die neuen Daten
 */
export async function setzeDienst(
  request: import('@playwright/test').APIRequestContext,
  zustand: {
    endpunkte?: unknown[];
    zustellungen?: unknown[];
    tote?: unknown[];
  },
): Promise<void> {
  const antwort = await request.post(`${DIENST}/__control/zustand`, { data: zustand });
  if (!antwort.ok()) {
    throw new Error(`Der Dienst-Doppelgaenger liess den Zustand nicht: ${antwort.status()}`);
  }
}

/**
 * Setzt den Dienst-Doppelgaenger zurueck.
 *
 * @param request die API des Testclients
 */
export async function setzeDienstZurueck(
  request: import('@playwright/test').APIRequestContext,
): Promise<void> {
  await request.post(`${DIENST}/__control/zuruecksetzen`);
}

/**
 * Liest die aufgezeichneten Anfragen an den Dienst.
 *
 * @param request die API des Testclients
 * @returns die Anfragen
 */
export async function aufrufe(
  request: import('@playwright/test').APIRequestContext,
): Promise<Array<{ methode: string; pfad: string; query: Record<string, string> }>> {
  const antwort = await request.get(`${DIENST}/__control/aufrufe`);
  const body = (await antwort.json()) as {
    aufrufe: Array<{ methode: string; pfad: string; query: Record<string, string> }>;
  };
  return body.aufrufe;
}
