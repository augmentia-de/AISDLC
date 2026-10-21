import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';

import DeliveryProtocolPage from '../../../src/main/frontend/src/app/deliveries/page';
import { jsonResponse, searchParams } from './support/actionRunner';
import { testToken } from './support/token';

/**
 * Unit-Ebene der Oberflaeche — Zustellprotokoll (US-020, FR-030).
 *
 * Getestet wird das Verhalten der Seite: je Zustellvorgang Zustand, Versuche,
 * letzter Versuch, Antwortcode und naechster Versuchszeitpunkt; die
 * kombinierbaren Filter nach Endpunkt, Status und Zeitraum; der
 * verstaendliche Leerzustand.
 *
 * <strong>Die Seite ist ein Server Component</strong> (ADR-018 §8): die Filter
 * sind ein GET-Formular und stehen in der URL der Seite. Der Test prueft
 * deshalb, **was** die Seite beim Dienst anfragt, und **was** sie anzeigt.
 *
 * Testorakel: `docs/api/openapi.yaml` -> `listDeliveries`; ADR-018.
 *
 * Abgedeckte Akzeptanzkriterien: AC-020-1, AC-020-3, AC-020-4.
 */

const mocks = vi.hoisted(() => ({
  cookieValue: null as string | null,
  revalidatePath: vi.fn(),
}));

vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (name: string) =>
      name === 'echobox.token' && mocks.cookieValue !== null
        ? { name, value: mocks.cookieValue }
        : undefined,
    set: vi.fn(),
    delete: vi.fn(),
  }),
}));

vi.mock('next/cache', async (importOriginal) => ({
  ...(await importOriginal<typeof import('next/cache')>()),
  revalidatePath: mocks.revalidatePath,
}));

vi.mock('next/navigation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('next/navigation')>()),
  redirect: (target: string) => {
    throw Object.assign(new Error('NEXT_REDIRECT'), { digest: `NEXT_REDIRECT;${target}` });
  },
}));

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

const DELIVERY_LISTE = {
  items: [DELIVERY],
  page: { limit: 50, has_more: false, next_cursor: null },
};

/**
 * Rendert die Zustellprotokollseite mit den Query-Parametern.
 *
 * @param parameter die Query-Parameter der Seite
 * @returns die gerenderte Seite
 */
async function renderPage(parameter: Record<string, string> = {}) {
  const element = await DeliveryProtocolPage({ searchParams: searchParams(parameter) });
  return render(element);
}

describe('Zustellprotokollseite', () => {
  beforeEach(() => {
    mocks.cookieValue = testToken(['developer']);
    mocks.revalidatePath.mockClear();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(DELIVERY_LISTE)));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('AC-020-1: zeigt je Vorgang Zustand, Versuche, letzten Versuch, Antwortcode und naechsten Versuch', async () => {
    await renderPage();

    expect(screen.getByText('order.changed')).toBeTruthy();
    expect(screen.getByText('failed')).toBeTruthy();
    expect(screen.getByText('503')).toBeTruthy();
    // Das Format ist `TT.MM.JJJJ HH:MM:SS` (UTC) — dasselbe, das der
    // Testfall der toten Zustellungen fuer `abandoned_at` erwartet
    // (`2026-10-21T10:22:11.900Z` -> `21.10.2026 10:22:11`). Der
    // Zeitpunkt `last_attempt_at` dieses Testfalls ist `2026-10-21T09:16:24Z`
    // und damit `21.10.2026 09:16:24` (siehe docs/execution/, Befund T-8).
    expect(screen.getByText('21.10.2026 09:16:24')).toBeTruthy();
  });

  it('AC-020-1: die Filter nach Endpunkt, Status und Zeitraum sind kombinierbar', async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;

    await renderPage({
      endpoint_id: '8f1c2f0e-2f4a-4a0e-9d5b-6a1c2e3f4a5b',
      status: 'failed',
      from: '2026-10-01',
      to: '2026-10-21',
    });

    const aufrufe = fetchMock.mock.calls.map((call) => String(call[0]));
    const anfrage = aufrufe.find((url) => url.includes('/api/v1/deliveries'));

    expect(anfrage).toBeDefined();
    expect(anfrage).toContain('endpoint_id=8f1c2f0e-2f4a-4a0e-9d5b-6a1c2e3f4a5b');
    expect(anfrage).toContain('status=failed');
    expect(anfrage).toContain('from=2026-10-01T00%3A00%3A00.000Z');
    expect(anfrage).toContain('to=2026-10-21T23%3A59%3A59.999Z');

    // Die Filter stehen **in der URL** — sie sind ein GET-Formular
    // (ADR-018 §8) und damit ohne JavaScript bedienbar.
    const formular = document.querySelector('form.filters');
    expect(formular?.getAttribute('method')).toBe('get');
    expect(formular?.getAttribute('action')).toBe('/deliveries');
  });

  it('AC-020-3: ohne Treffer erscheint ein verstaendlicher Leerzustand statt einer leeren Tabelle', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ items: [], page: { limit: 50, has_more: false, next_cursor: null } }),
      ),
    );

    await renderPage();

    expect(screen.getByText(/keine zustellvorgänge/i)).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('AC-020-4: die URL eines fremden Mandanten zeigt keine fremden Daten', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            type: 'https://echobox.example/errors/not-found',
            title: 'Not found',
            status: 404,
            code: 'not_found',
            detail: 'Die Ressource wurde nicht gefunden.',
          },
          404,
        ),
      ),
    );

    await renderPage({ organisation_id: 'm-2' });

    expect(screen.getByText(/keine zustellvorgänge/i)).toBeTruthy();
    expect(document.body.textContent ?? '').not.toContain('m-2');
  });
});
