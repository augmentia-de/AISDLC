import { expect, test } from '@playwright/test';

import {
  anmelden,
  aufrufe,
  DEAD_DELIVERY,
  setzeDienst,
  setzeDienstZurueck,
} from './support/harness';

/**
 * E2E-Ebene — Tote Zustellungen (US-020, FR-031, FR-024).
 *
 * <strong>O-14:</strong> das erneute Ausloesen (FR-024) muss fuer den
 * **Empfaenger-Admin** erreichbar sein. Die Totenliste ist nur der
 * Service-Administration zugänglich; der Empfaenger-Admin liest die
 * aufgegebenen Zustellungen ueber das Zustellprotokoll (`status=abandoned`)
 * und darf **seinen** Vorgang erneut ausloesen — **nur** seinen (F-03, V-39).
 *
 * Abgedeckte Akzeptanzkriterien: AC-020-2, AC-020-3, FR-024.
 */

/** Der aufgegebene Zustelllauf im Zustellprotokoll — die Quelle des Empfaenger-Admins. */
const AUFGEGEBENER_LAUF = {
  delivery_run_id: DEAD_DELIVERY.delivery_run_id,
  event_id: DEAD_DELIVERY.event_id,
  endpoint_id: DEAD_DELIVERY.endpoint_id,
  organisation_id: 'm-1',
  event_type: DEAD_DELIVERY.event_type,
  status: 'abandoned',
  attempt_count: DEAD_DELIVERY.attempt_count,
  max_attempts: 8,
  replay_of: null,
  last_attempt_at: DEAD_DELIVERY.last_attempt_at,
  next_attempt_at: null,
  last_response_status: DEAD_DELIVERY.last_response_status,
  last_error_code: null,
  rejection_reason: null,
  created_at: DEAD_DELIVERY.created_at,
  updated_at: DEAD_DELIVERY.abandoned_at,
};

test.describe('Tote Zustellungen', () => {
  test.beforeEach(async ({ request }) => {
    await setzeDienstZurueck(request);
    await setzeDienst(request, { tote: [DEAD_DELIVERY], zustellungen: [AUFGEGEBENER_LAUF] });
  });

  test('AC-020-2: die Seite zeigt die toten Zustellungen mit den geforderten Feldern', async ({
    page,
    context,
  }) => {
    await anmelden(
      context,
      ['service_admin'],
      'm-1',
      test.info().project.use.baseURL ?? 'http://127.0.0.1:3000',
    );

    await page.goto('/dead-deliveries');

    await expect(page.getByText('order.changed')).toBeVisible();
    await expect(page.getByText('abandoned')).toBeVisible();
    await expect(page.getByText('503')).toBeVisible();
    await expect(page.getByText(/10:22:11/)).toBeVisible();
  });

  test('AC-020-3: ohne Treffer erscheint ein verstaendlicher Leerzustand', async ({
    page,
    request,
    context,
  }) => {
    await setzeDienst(request, { tote: [], zustellungen: [] });
    await anmelden(
      context,
      ['service_admin'],
      'm-1',
      test.info().project.use.baseURL ?? 'http://127.0.0.1:3000',
    );

    await page.goto('/dead-deliveries');

    await expect(page.getByText(/keine toten zustellungen/i)).toBeVisible();
  });

  test('O-14: der Empfaenger-Admin erreicht die aufgegebenen Zustellungen ueber das Zustellprotokoll', async ({
    page,
    request,
    context,
  }) => {
    await anmelden(
      context,
      ['receiver_admin'],
      'm-1',
      test.info().project.use.baseURL ?? 'http://127.0.0.1:3000',
    );

    await page.goto('/dead-deliveries');

    await expect(page.getByText(DEAD_DELIVERY.event_id)).toBeVisible();

    const angerufen = await aufrufe(request);
    expect(
      angerufen.some(
        (anfrage) => anfrage.pfad === '/api/v1/deliveries' && anfrage.query.status === 'abandoned',
      ),
    ).toBe(true);
  });

  test('O-14: der Empfaenger-Admin loest seinen Vorgang erneut aus', async ({
    page,
    request,
    context,
  }) => {
    await anmelden(
      context,
      ['receiver_admin'],
      'm-1',
      test.info().project.use.baseURL ?? 'http://127.0.0.1:3000',
    );

    await page.goto('/dead-deliveries');

    await page.getByRole('button', { name: /erneut auslösen/i }).click();

    await expect(page.getByText(/erneut ausgelöst/i)).toBeVisible();

    const angerufen = await aufrufe(request);
    expect(
      angerufen.some(
        (anfrage) =>
          anfrage.methode === 'POST' &&
          anfrage.pfad === `/api/v1/deliveries/${DEAD_DELIVERY.delivery_run_id}/replay`,
      ),
    ).toBe(true);
  });

  test('O-14: der Entwickler darf den Vorgang nicht erneut ausloesen', async ({
    page,
    context,
  }) => {
    await anmelden(
      context,
      ['developer'],
      'm-1',
      test.info().project.use.baseURL ?? 'http://127.0.0.1:3000',
    );

    await page.goto('/dead-deliveries');

    await expect(page.getByRole('button', { name: /erneut auslösen/i })).toHaveCount(0);
  });

  test('O-14: der Empfaenger-Admin eines anderen Mandanten darf den Vorgang nicht erneut ausloesen', async ({
    page,
    context,
  }) => {
    await anmelden(
      context,
      ['receiver_admin'],
      'm-2',
      test.info().project.use.baseURL ?? 'http://127.0.0.1:3000',
    );

    await page.goto('/dead-deliveries');

    await expect(page.getByRole('button', { name: /erneut auslösen/i })).toHaveCount(0);
  });
});

/**
 * <strong>ADR-018 §8:</strong> das erneute Ausloesen ist eine Formularaktion —
 * es funktioniert auch ohne JavaScript.
 */
test.describe('Tote Zustellungen ohne JavaScript', () => {
  test.use({ javaScriptEnabled: false });

  test('ADR-018 §8: das erneute Ausloesen funktioniert auch ohne JavaScript', async ({
    page,
    request,
    context,
  }) => {
    await setzeDienstZurueck(request);
    await setzeDienst(request, { tote: [DEAD_DELIVERY], zustellungen: [AUFGEGEBENER_LAUF] });
    await anmelden(
      context,
      ['receiver_admin'],
      'm-1',
      test.info().project.use.baseURL ?? 'http://127.0.0.1:3000',
    );

    await page.goto('/dead-deliveries');

    await page.getByRole('button', { name: /erneut auslösen/i }).click();

    await expect(page.getByText(/erneut ausgelöst/i)).toBeVisible();
  });
});
