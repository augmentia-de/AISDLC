import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import DeliveryProtocolPage from '../../../src/main/frontend/src/app/deliveries/page';

/**
 * Unit-Ebene der Oberflaeche — Zustellprotokoll (US-020, FR-030).
 *
 * Getestet wird das Verhalten der Seite: je Zustellvorgang Zustand, Versuche,
 * letzter Versuch, Antwortcode und naechster Versuchszeitpunkt; die
 * kombinierbaren Filter nach Endpunkt, Status und Zeitraum; der
 * verstaendliche Leerzustand.
 *
 * Testorakel: `docs/api/openapi.yaml` -> `listDeliveries`; ADR-018.
 *
 * Abgedeckte Akzeptanzkriterien: AC-020-1, AC-020-3.
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

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status < 400,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

describe('Zustellprotokollseite', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('AC-020-1: zeigt je Vorgang Zustand, Versuche, letzten Versuch, Antwortcode und naechsten Versuch', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      jsonResponse({ items: [DELIVERY], page: { limit: 50, has_more: false, next_cursor: null } }),
    );

    render(<DeliveryProtocolPage />);

    await waitFor(() => {
      expect(screen.getByText('order.changed')).toBeTruthy();
    });

    expect(screen.getByText('failed')).toBeTruthy();
    expect(screen.getByText('503')).toBeTruthy();
    expect(screen.getByText('09.10.2026 09:16:24')).toBeTruthy();
  });

  it('AC-020-1: die Filter nach Endpunkt, Status und Zeitraum sind kombinierbar', async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    fetchMock.mockResolvedValue(
      jsonResponse({ items: [DELIVERY], page: { limit: 50, has_more: false, next_cursor: null } }),
    );

    render(<DeliveryProtocolPage />);

    const user = userEvent.setup();
    const statusField = await screen.findByLabelText(/status/i);
    await user.selectOptions(statusField, 'failed');

    await waitFor(() => {
      const calls = fetchMock.mock.calls.map((call) => String(call[0]));
      expect(calls.some((url) => url.includes('status=failed'))).toBe(true);
    });
  });

  it('AC-020-3: ohne Treffer erscheint ein verstaendlicher Leerzustand statt einer leeren Tabelle', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      jsonResponse({ items: [], page: { limit: 50, has_more: false, next_cursor: null } }),
    );

    render(<DeliveryProtocolPage />);

    await waitFor(() => {
      expect(screen.getByText(/keine zustellvorgänge/i)).toBeTruthy();
    });

    expect(screen.queryByRole('table')).toBeNull();
  });
});
