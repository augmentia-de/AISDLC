import { expect, test } from '@playwright/test';

import { DIENST } from './support/harness';

/**
 * E2E-Ebene — Gesundheitsendpunkte (US-023, AC-023-1, NFR-006).
 *
 * Die Endpunkte antworten **ohne** Authentifizierung, damit die
 * Container-Runtime und der Load-Balancer pruefen koennen.
 *
 * <strong>Der Dienst</strong> ist im Standardlauf der Dienst-Doppelgaenger
 * (`ECHOBOX_API_BASE_URL`); mit gesetzter Umgebungsvariable spricht der Test
 * den laufenden Dienst an.
 *
 * Testorakel: `docs/api/openapi.yaml` -> `getHealth`, `getReadiness`.
 */

const BASIS = process.env.ECHOBOX_API_BASE_URL ?? DIENST;

test.describe('Gesundheitsendpunkte', () => {
  test('AC-023-1: der Liveness-Endpunkt antwortet ohne Token', async ({ request }) => {
    const response = await request.get(`${BASIS}/health`);

    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body.status).toBe('up');
    expect(body.service).toBe('echobox');
    expect(body.version).toBeTruthy();
    expect(body.checked_at).toBeTruthy();
  });

  test('AC-023-1: der Readiness-Endpunkt meldet die Bereitschaft', async ({ request }) => {
    const response = await request.get(`${BASIS}/health/ready`);

    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body.status).toBe('ready');
    expect(body.checks.database).toBe('ok');
    expect(body.checks.delivery_worker).toBe('ok');
    expect(body.checks.tenant_context).toBe('ok');
  });
});
