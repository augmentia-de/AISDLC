import { expect, test } from '@playwright/test';

import {
  anmelden,
  aufrufe,
  DELIVERY,
  setzeDienst,
  setzeDienstZurueck,
} from './support/harness';

/**
 * E2E-Ebene — Zustellprotokoll (US-020, FR-030).
 *
 * Der Dienst-Doppelgaenger steht an der Dienstgrenze; die Oberflaeche spricht
 * ihn serverseitig an (ADR-018 §8).
 *
 * Abgedeckte Akzeptanzkriterien: AC-020-1, AC-020-3, AC-020-4.
 */

test.describe('Zustellprotokoll', () => {
  test.beforeEach(async ({ request, context }) => {
    await setzeDienstZurueck(request);
    await setzeDienst(request, { zustellungen: [DELIVERY] });
    await anmelden(
      context,
      ['developer'],
      'm-1',
      test.info().project.use.baseURL ?? 'http://127.0.0.1:3000',
    );
  });

  test('AC-020-1: die Seite zeigt je Vorgang Zustand, Versuche, letzten Versuch, Antwortcode und naechsten Versuch', async ({
    page,
  }) => {
    await page.goto('/deliveries');

    await expect(page.getByText('order.changed')).toBeVisible();
    await expect(page.getByText('failed')).toBeVisible();
    await expect(page.getByText('503')).toBeVisible();
    await expect(page.getByText(/09:16:24/)).toBeVisible();
  });

  test('AC-020-1: die Filter nach Endpunkt, Status und Zeitraum sind bedienbar', async ({
    page,
    request,
  }) => {
    await page.goto('/deliveries');

    await page.getByLabel(/status/i).selectOption('failed');
    await page.getByRole('button', { name: /filter anwenden/i }).click();

    await expect(page).toHaveURL(/status=failed/);

    const angerufen = await aufrufe(request);
    expect(
      angerufen.some(
        (anfrage) =>
          anfrage.pfad === '/api/v1/deliveries' && anfrage.query.status === 'failed',
      ),
    ).toBe(true);
  });

  test('AC-020-3: ohne Treffer erscheint ein verstaendlicher Leerzustand', async ({ page, request }) => {
    await setzeDienst(request, { zustellungen: [] });

    await page.goto('/deliveries');

    await expect(page.getByText(/keine zustellvorgänge/i)).toBeVisible();
  });

  test('AC-020-4: die URL eines fremden Mandanten zeigt keine fremden Daten', async ({
    page,
    request,
  }) => {
    await setzeDienst(request, { zustellungen: [] });

    await page.goto('/deliveries?organisation_id=m-2');

    await expect(page.getByText(/keine zustellvorgänge/i)).toBeVisible();
    await expect(page.locator('body')).not.toContainText('m-2');
  });
});

/**
 * <strong>ADR-018 §8:</strong> der Filter ist eine **Formularaktion** — er
 * funktioniert auch ohne JavaScript.
 */
test.describe('Zustellprotokoll ohne JavaScript', () => {
  test.use({ javaScriptEnabled: false });

  test('ADR-018 §8: der Filter funktioniert auch ohne JavaScript', async ({ page, request }) => {
    await setzeDienstZurueck(request);
    await setzeDienst(request, { zustellungen: [DELIVERY] });
    await anmelden(
      page.context(),
      ['developer'],
      'm-1',
      test.info().project.use.baseURL ?? 'http://127.0.0.1:3000',
    );

    await page.goto('/deliveries');

    await page.getByLabel(/status/i).selectOption('failed');
    await page.getByRole('button', { name: /filter anwenden/i }).click();

    await expect(page).toHaveURL(/status=failed/);
    await expect(page.getByText('order.changed')).toBeVisible();
  });
});
