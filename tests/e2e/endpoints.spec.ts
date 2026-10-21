import { expect, test } from '@playwright/test';

import {
  anmelden,
  ENDPOINT,
  setzeDienst,
  setzeDienstZurueck,
} from './support/harness';

/**
 * E2E-Ebene — Endpunktseite (US-019, FR-001, FR-002, FR-003).
 *
 * Der Test faehrt die echte Bedienung im Browser: Endpunkte ansehen, einen
 * Endpunkt registrieren, pausieren und das Secret rotieren. Die Oberflaeche
 * spricht den Dienst **serverseitig** an (ADR-018 §8) — der Dienst-Doppelgaenger
 * steht an der Dienstgrenze und bedient den Vertrag.
 *
 * Testorakel: `docs/api/openapi.yaml` -> `createEndpoint`, `listEndpoints`,
 * `pauseEndpoint`, `resumeEndpoint`, `rotateEndpointSecret`; ADR-018.
 *
 * Abgedeckte Akzeptanzkriterien: AC-019-1, AC-019-2, AC-019-3, AC-019-4.
 */

test.describe('Endpunktverwaltung', () => {
  test.beforeEach(async ({ request, context }) => {
    await setzeDienstZurueck(request);
    await setzeDienst(request, { endpunkte: [ENDPOINT] });
    await anmelden(
      context,
      ['receiver_admin'],
      'm-1',
      test.info().project.use.baseURL ?? 'http://127.0.0.1:3000',
    );
  });

  test('AC-019-1: die Seite zeigt Status, Ziel-URL ohne Secret und die Ereignistypen', async ({
    page,
  }) => {
    await page.goto('/endpoints');

    await expect(page.getByText(ENDPOINT.target_url)).toBeVisible();
    await expect(page.getByText('order.changed')).toBeVisible();
    await expect(page.getByText('aktiv')).toBeVisible();
    await expect(page.locator('body')).not.toContainText('s3cr3t-rotationswert');
  });

  test('AC-019-2: eine ungueltige Ziel-URL ergibt eine Fehlermeldung am Feld', async ({ page }) => {
    await page.goto('/endpoints');

    await page.getByLabel(/ziel-url/i).fill('http://orders.example.invalid/hook');
    await page.getByRole('button', { name: /registrier/i }).click();

    await expect(page.getByText('Die Ziel-URL muss das Schema https verwenden.')).toBeVisible();

    // Die Meldung steht **am Feld** (ADR-018 §7).
    await expect(page.getByLabel(/ziel-url/i)).toHaveAttribute(
      'aria-describedby',
      'endpoint-target-url-error',
    );
  });

  test('AC-019-3: nach dem Pausieren wechselt der Status sofort auf "pausiert"', async ({
    page,
  }) => {
    await page.goto('/endpoints');

    await page.getByRole('button', { name: /pausier/i }).click();

    await expect(page.getByText('pausiert')).toBeVisible();
    await expect(page.getByRole('button', { name: /reaktivier/i })).toBeVisible();
  });

  test('AC-019-4: die Rotation wird bestaetigt, ohne das Secret anzuzeigen', async ({ page }) => {
    await page.goto('/endpoints');

    await page.getByLabel(/neues secret/i).fill('neues-s3cr3t-rotationswert');
    await page.getByRole('button', { name: /rotier/i }).click();

    await expect(page.getByText(/rotation/i)).toBeVisible();
    await expect(page.locator('body')).not.toContainText('neues-s3cr3t-rotationswert');
  });
});

/**
 * <strong>ADR-018 §8:</strong> „Alle drei Seiten sind ohne JavaScript
 * bedienbar (Server Components, progressive Erhoehung). Interaktive Elemente
 * (Pause/Reaktivieren, Replay, Filter) arbeiten als Formularaktion.“
 *
 * Dieser Block schaltet das JavaScript im Browser **ab**. Die Bedienung muss
 * trotzdem gehen: das Formular wird normal abgeschickt, die Server Action
 * laeuft, die Seite zeigt den neuen Zustand.
 */
test.describe('Endpunktverwaltung ohne JavaScript', () => {
  test.use({ javaScriptEnabled: false });

  test('ADR-018 §8: das Pausieren funktioniert auch ohne JavaScript', async ({ page, request }) => {
    await setzeDienstZurueck(request);
    await setzeDienst(request, { endpunkte: [ENDPOINT] });
    await anmelden(
      page.context(),
      ['receiver_admin'],
      'm-1',
      test.info().project.use.baseURL ?? 'http://127.0.0.1:3000',
    );

    await page.goto('/endpoints');

    await page.getByRole('button', { name: /pausier/i }).click();

    await expect(page.getByText('pausiert')).toBeVisible();
  });
});
