import { expect, test } from '@playwright/test';

/**
 * E2E-Ebene — Endpunktseite (US-019, FR-001, FR-002, FR-003).
 *
 * Der Test faehrt die echte Bedienung im Browser: Endpunkte ansehen, einen
 * Endpunkt registrieren, pausieren und das Secret rotieren. Die API wird
 * auf der Netzwerkebene mit Vertragsdaten bedient (`page.route`), damit die
 * Reise deterministisch bleibt; der HTTP-Vertrag selbst wird in den
 * Integrationstests geprueft.
 *
 * Testorakel: `docs/api/openapi.yaml` -> `createEndpoint`, `listEndpoints`,
 * `pauseEndpoint`, `resumeEndpoint`, `rotateEndpointSecret`; ADR-018.
 *
 * Abgedeckte Akzeptanzkriterien: AC-019-1, AC-019-2, AC-019-3, AC-019-4.
 */

const ENDPOINT = {
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

test.describe('Endpunktverwaltung', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/endpoints**', async (route) => {
      const request = route.request();
      if (request.method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ items: [ENDPOINT], page: { limit: 50, has_more: false, next_cursor: null } }),
        });
        return;
      }
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(ENDPOINT),
      });
    });

    await page.route('**/api/v1/endpoints/*/pause', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...ENDPOINT, status: 'paused', paused_at: '2026-10-21T11:40:00.000Z' }),
      });
    });

    await page.route('**/api/v1/endpoints/*/secret-rotation', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          endpoint_id: ENDPOINT.endpoint_id,
          secret_version: 2,
          rotated_at: '2026-10-21T12:05:11.204Z',
          rotated_by: 'user-42@orders.example.invalid',
        }),
      });
    });
  });

  test('AC-019-1: die Seite zeigt Status, Ziel-URL ohne Secret und die Ereignistypen', async ({ page }) => {
    await page.goto('/endpoints');

    await expect(page.getByText(ENDPOINT.target_url)).toBeVisible();
    await expect(page.getByText('order.changed')).toBeVisible();
    await expect(page.getByText('aktiv')).toBeVisible();
    await expect(page.locator('body')).not.toContainText('s3cr3t-rotationswert');
  });

  test('AC-019-2: eine ungueltige Ziel-URL ergibt eine Fehlermeldung am Feld', async ({ page }) => {
    await page.route('**/api/v1/endpoints', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 422,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            type: 'https://echobox.example/errors/validation-failed',
            title: 'Validation failed',
            status: 422,
            code: 'validation_failed',
            detail: 'Die Anfrage ist semantisch ungueltig.',
            violations: [
              {
                field: 'target_url',
                code: 'scheme_not_allowed',
                message: 'Die Ziel-URL muss das Schema https verwenden.',
              },
            ],
          }),
        });
        return;
      }
      await route.fallback();
    });

    await page.goto('/endpoints');

    await page.getByLabel(/ziel-url/i).fill('http://orders.example.invalid/hook');
    await page.getByRole('button', { name: /registrier/i }).click();

    await expect(page.getByText('Die Ziel-URL muss das Schema https verwenden.')).toBeVisible();
  });

  test('AC-019-3: nach dem Pausieren wechselt der Status sofort auf "pausiert"', async ({ page }) => {
    await page.goto('/endpoints');

    await page.getByRole('button', { name: /pausier/i }).click();

    await expect(page.getByText('pausiert')).toBeVisible();
    await expect(page.getByRole('button', { name: /reaktivier/i })).toBeVisible();
  });

  test('AC-019-4: die Rotation wird bestaetigt, ohne das Secret anzuzeigen', async ({ page }) => {
    await page.goto('/endpoints');

    await page.getByRole('button', { name: /rotier/i }).click();

    await expect(page.getByText(/rotation/i)).toBeVisible();
    await expect(page.locator('body')).not.toContainText('neues-s3cr3t-rotationswert');
  });
});
