import { expect, test } from '@playwright/test';

/**
 * E2E-Ebene — Tote Zustellungen (US-020, FR-031).
 *
 * Abgedeckte Akzeptanzkriterien: AC-020-2, AC-020-3.
 */

const DEAD_DELIVERY = {
  delivery_run_id: 'b4c3d2e1-7f60-4a5b-9c8d-2e3f4a5b6c7d',
  event_id: '5d8e9f0a-1b2c-4d3e-8f90-a1b2c3d4e5f6',
  organisation_id: 'm-1',
  endpoint_id: '8f1c2f0e-2f4a-4a0e-9d5b-6a1c2e3f4a5b',
  endpoint_url: 'https://orders.example.invalid/hooks/echobox',
  event_type: 'order.changed',
  status: 'abandoned',
  attempt_count: 8,
  last_response_status: 503,
  last_attempt_at: '2026-10-21T10:22:11.900Z',
  abandoned_at: '2026-10-21T10:22:11.900Z',
  created_at: '2026-10-21T09:40:05.120Z',
};

test.describe('Tote Zustellungen', () => {
  test('AC-020-2: die Seite zeigt die toten Zustellungen mit den geforderten Feldern', async ({ page }) => {
    await page.route('**/api/v1/dead-deliveries**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [DEAD_DELIVERY], page: { limit: 50, has_more: false, next_cursor: null } }),
      });
    });

    await page.goto('/dead-deliveries');

    await expect(page.getByText('order.changed')).toBeVisible();
    await expect(page.getByText('abandoned')).toBeVisible();
    await expect(page.getByText('503')).toBeVisible();
    await expect(page.getByText(/10:22:11/)).toBeVisible();
  });

  test('AC-020-3: ohne Treffer erscheint ein verstaendlicher Leerzustand', async ({ page }) => {
    await page.route('**/api/v1/dead-deliveries**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [], page: { limit: 50, has_more: false, next_cursor: null } }),
      });
    });

    await page.goto('/dead-deliveries');

    await expect(page.getByText(/keine toten zustellungen/i)).toBeVisible();
  });
});
