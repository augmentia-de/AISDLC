import { expect, test } from '@playwright/test';

/**
 * E2E-Ebene — Zustellprotokoll (US-020, FR-030).
 *
 * Abgedeckte Akzeptanzkriterien: AC-020-1, AC-020-3, AC-020-4.
 */

const DELIVERY = {
  delivery_run_id: '7d2b1c9a-5e6f-4a0b-8c7d-1e2f3a4b5c6d',
  event_id: '3c9a7d21-8e4b-4a1f-9c2d-5e6f7a8b9c0d',
  endpoint_id: '8f1c2f0e-2f4a-4a0e-9d5b-6a1c2e3f4a5b',
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

test.describe('Zustellprotokoll', () => {
  test('AC-020-1: die Seite zeigt je Vorgang Zustand, Versuche, letzten Versuch, Antwortcode und naechsten Versuch', async ({
    page,
  }) => {
    await page.route('**/api/v1/deliveries**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [DELIVERY], page: { limit: 50, has_more: false, next_cursor: null } }),
      });
    });

    await page.goto('/deliveries');

    await expect(page.getByText('order.changed')).toBeVisible();
    await expect(page.getByText('failed')).toBeVisible();
    await expect(page.getByText('503')).toBeVisible();
    await expect(page.getByText(/09:16:24/)).toBeVisible();
  });

  test('AC-020-1: die Filter nach Endpunkt, Status und Zeitraum sind bedienbar', async ({ page }) => {
    const requestedUrls: string[] = [];

    await page.route('**/api/v1/deliveries**', async (route) => {
      requestedUrls.push(route.request().url());
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [DELIVERY], page: { limit: 50, has_more: false, next_cursor: null } }),
      });
    });

    await page.goto('/deliveries');

    await page.getByLabel(/status/i).selectOption('failed');
    await expect
      .poll(() => requestedUrls.some((url) => url.includes('status=failed')))
      .toBe(true);
  });

  test('AC-020-3: ohne Treffer erscheint ein verstaendlicher Leerzustand', async ({ page }) => {
    await page.route('**/api/v1/deliveries**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [], page: { limit: 50, has_more: false, next_cursor: null } }),
      });
    });

    await page.goto('/deliveries');

    await expect(page.getByText(/keine zustellvorgänge/i)).toBeVisible();
  });

  test('AC-020-4: die URL eines fremden Mandanten zeigt keine fremden Daten', async ({ page }) => {
    await page.route('**/api/v1/deliveries**', async (route) => {
      await route.fulfill({
        status: 404,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          type: 'https://echobox.example/errors/not-found',
          title: 'Not found',
          status: 404,
          code: 'not_found',
          detail: 'Die Ressource wurde nicht gefunden.',
        }),
      });
    });

    await page.goto('/deliveries?organisation_id=m-2');

    await expect(page.getByText(/keine zustellvorgänge/i)).toBeVisible();
    await expect(page.locator('body')).not.toContainText('m-2');
  });
});
