import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';

import { replayDeliveryAction } from '../../../src/main/frontend/src/app/actions';
import DeadDeliveryPage from '../../../src/main/frontend/src/app/dead-deliveries/page';
import { mayReplay } from '../../../src/main/frontend/src/features/dead/deadDeliveryQueries';
import { jsonResponse, runAction, searchParams } from './support/actionRunner';
import { testToken } from './support/token';

/**
 * Unit-Ebene der Oberflaeche — Tote Zustellungen (US-020, FR-031, FR-024).
 *
 * Getestet wird das Verhalten der Seite: die toten Zustellungen aller Mandanten
 * mit den geforderten Feldern und der verstaendliche Leerzustand.
 *
 * <strong>O-14 — das erneute Ausloesen fuer den Empfaenger-Admin</strong>:
 * die Totenliste ist nur der Service-Administration zugänglich; der
 * Empfaenger-Admin liest die aufgegebenen Zustellungen ueber das
 * Zustellprotokoll (`status=abandoned`) und darf seinen eigenen Vorgang
 * erneut ausloesen — **nur** seinen (F-03, V-39).
 *
 * Testorakel: `docs/api/openapi.yaml` -> `listDeadDeliveries`,
 * `listDeliveries`, `replayDelivery`; ADR-018.
 *
 * Abgedeckte Akzeptanzkriterien: AC-020-2, AC-020-3, FR-024.
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

/** Der Eintrag des Zustellprotokolls — die Quelle des Empfaenger-Admins. */
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

/**
 * Rendert die Seite der toten Zustellungen.
 *
 * @param parameter die Query-Parameter der Seite
 * @returns die gerenderte Seite
 */
async function renderPage(parameter: Record<string, string> = {}) {
  const element = await DeadDeliveryPage({ searchParams: searchParams(parameter) });
  return render(element);
}

describe('Seite der toten Zustellungen', () => {
  beforeEach(() => {
    mocks.cookieValue = testToken(['service_admin']);
    mocks.revalidatePath.mockClear();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ items: [DEAD_DELIVERY], page: { limit: 50, has_more: false, next_cursor: null } })));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('AC-020-2: zeigt die toten Zustellungen mit Ereignis-ID, Endpunkt, Ereignistyp, Versuchen und Aufgabezeitpunkt', async () => {
    await renderPage();

    expect(screen.getByText('order.changed')).toBeTruthy();
    expect(screen.getByText('5d8e9f0a-1b2c-4d3e-8f90-a1b2c3d4e5f6')).toBeTruthy();
    expect(screen.getByText('abandoned')).toBeTruthy();
    expect(screen.getByText('503')).toBeTruthy();
    expect(screen.getByText('21.10.2026 10:22:11')).toBeTruthy();
  });

  it('AC-020-3: ohne Treffer erscheint ein verstaendlicher Leerzustand statt einer leeren Tabelle', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ items: [], page: { limit: 50, has_more: false, next_cursor: null } }),
      ),
    );

    await renderPage();

    expect(screen.getByText(/keine toten zustellungen/i)).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('O-14: der Empfaenger-Admin liest die aufgegebenen Zustellungen ueber das Zustellprotokoll', async () => {
    mocks.cookieValue = testToken(['receiver_admin'], 'm-1');
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    fetchMock.mockResolvedValue(
      jsonResponse({ items: [AUFGEGEBENER_LAUF], page: { limit: 50, has_more: false, next_cursor: null } }),
    );

    await renderPage();

    const aufrufe = fetchMock.mock.calls.map((call) => String(call[0]));
    const anfrage = aufrufe.find((url) => url.includes('/api/v1/deliveries'));

    expect(anfrage).toBeDefined();
    expect(anfrage).toContain('status=abandoned');
    // Die Totenliste ist der Service-Administration vorbehalten — der
    // Empfaenger-Admin fragt sie **nicht** ab.
    expect(aufrufe.some((url) => url.includes('/api/v1/dead-deliveries'))).toBe(false);
  });

  it('O-14: der Empfaenger-Admin darf seinen eigenen Vorgang erneut ausloesen', async () => {
    mocks.cookieValue = testToken(['receiver_admin'], 'm-1');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ items: [AUFGEGEBENER_LAUF], page: { limit: 50, has_more: false, next_cursor: null } }),
      ),
    );

    await renderPage();

    expect(screen.getByRole('button', { name: /erneut auslösen/i })).toBeTruthy();
  });

  it('O-14: der Entwickler darf den Vorgang nicht erneut ausloesen', async () => {
    mocks.cookieValue = testToken(['developer'], 'm-1');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ items: [AUFGEGEBENER_LAUF], page: { limit: 50, has_more: false, next_cursor: null } }),
      ),
    );

    await renderPage();

    expect(screen.queryByRole('button', { name: /erneut auslösen/i })).toBeNull();
  });

  it('O-14: der Empfaenger-Admin eines anderen Mandanten darf den Vorgang nicht erneut ausloesen', async () => {
    mocks.cookieValue = testToken(['receiver_admin'], 'm-2');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ items: [AUFGEGEBENER_LAUF], page: { limit: 50, has_more: false, next_cursor: null } }),
      ),
    );

    await renderPage();

    expect(screen.queryByRole('button', { name: /erneut auslösen/i })).toBeNull();
  });

  it('O-14: nur der Empfaenger-Admin des Endpunkts darf (F-03, V-39)', () => {
    const empfaengerAdmin = {
      token: 't',
      roles: ['receiver_admin'],
      organisationId: 'm-1',
    };

    expect(mayReplay(empfaengerAdmin, 'm-1')).toBe(true);
    expect(mayReplay(empfaengerAdmin, 'm-2')).toBe(false);
    expect(mayReplay({ ...empfaengerAdmin, roles: ['developer'] }, 'm-1')).toBe(false);
    expect(mayReplay({ ...empfaengerAdmin, roles: ['service_admin'] }, 'm-1')).toBe(false);
  });

  it('O-14: das erneute Ausloesen loest genau einen Aufruf beim Dienst aus', async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    fetchMock.mockResolvedValue(
      jsonResponse({
        previous_delivery_run_id: DEAD_DELIVERY.delivery_run_id,
        delivery_run_id: 'c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f',
        event_id: DEAD_DELIVERY.event_id,
        status: 'pending',
        attempt_count: 0,
        replayed_at: '2026-10-21T11:00:00.000Z',
      }),
    );

    const ziel = await runAction(replayDeliveryAction, {
      delivery_run_id: DEAD_DELIVERY.delivery_run_id,
    });

    expect(ziel).toBe('/dead-deliveries?ergebnis=replayed');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain(`/api/v1/deliveries/${DEAD_DELIVERY.delivery_run_id}/replay`);
    expect(options.method).toBe('POST');
  });
});
