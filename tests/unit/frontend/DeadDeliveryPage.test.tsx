import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';

import DeadDeliveryPage from '../../../src/main/frontend/src/app/dead-deliveries/page';

/**
 * Unit-Ebene der Oberflaeche — Tote Zustellungen (US-020, FR-031).
 *
 * Getestet wird das Verhalten der Seite: die toten Zustellungen aller Mandanten
 * mit den geforderten Feldern und der verstaendliche Leerzustand.
 *
 * Testorakel: `docs/api/openapi.yaml` -> `listDeadDeliveries`; ADR-018.
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

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status < 400,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

describe('Seite der toten Zustellungen', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('AC-020-2: zeigt die toten Zustellungen mit Ereignis-ID, Endpunkt, Ereignistyp, Versuchen und Aufgabezeitpunkt', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      jsonResponse({ items: [DEAD_DELIVERY], page: { limit: 50, has_more: false, next_cursor: null } }),
    );

    render(<DeadDeliveryPage />);

    await waitFor(() => {
      expect(screen.getByText('order.changed')).toBeTruthy();
    });

    expect(screen.getByText('5d8e9f0a-1b2c-4d3e-8f90-a1b2c3d4e5f6')).toBeTruthy();
    expect(screen.getByText('abandoned')).toBeTruthy();
    expect(screen.getByText('503')).toBeTruthy();
    expect(screen.getByText('21.10.2026 10:22:11')).toBeTruthy();
  });

  it('AC-020-3: ohne Treffer erscheint ein verstaendlicher Leerzustand statt einer leeren Tabelle', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      jsonResponse({ items: [], page: { limit: 50, has_more: false, next_cursor: null } }),
    );

    render(<DeadDeliveryPage />);

    await waitFor(() => {
      expect(screen.getByText(/keine toten zustellungen/i)).toBeTruthy();
    });

    expect(screen.queryByRole('table')).toBeNull();
  });
});
