import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import EndpointsPage from '../../../src/main/frontend/src/app/endpoints/page';

/**
 * Unit-Ebene der Oberflaeche — Endpunktseite (US-019, FR-001, FR-002, FR-003).
 *
 * Getestet wird das Verhalten der Seite, nicht ihre Implementierung:
 * Endpunkte mit Status, Ziel-URL (ohne Secret) und Ereignistypen; eine
 * verstaendliche Fehlermeldung am Feld; der sofortige Statuswechsel beim
 * Pausieren; die Rotationsbestaetigung ohne Klartext.
 *
 * Testorakel: `docs/api/openapi.yaml` -> `listEndpoints`, `pauseEndpoint`,
 * `resumeEndpoint`, `rotateEndpointSecret`; ADR-018.
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

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status < 400,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

describe('Endpunktseite', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('AC-019-1: zeigt Status, Ziel-URL ohne Secret und die Ereignistypen', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      jsonResponse({ items: [ENDPOINT], page: { limit: 50, has_more: false, next_cursor: null } }),
    );

    render(<EndpointsPage />);

    await waitFor(() => {
      expect(screen.getByText('https://orders.example.invalid/hooks/echobox')).toBeTruthy();
    });

    expect(screen.getByText('order.changed')).toBeTruthy();
    expect(screen.getByText('aktiv')).toBeTruthy();
    expect(document.body.textContent ?? '').not.toContain('s3cr3t-rotationswert');
  });

  it('AC-019-1: das Secret wird nirgends angezeigt', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      jsonResponse({ items: [ENDPOINT], page: { limit: 50, has_more: false, next_cursor: null } }),
    );

    render(<EndpointsPage />);

    await waitFor(() => {
      expect(screen.getByText('https://orders.example.invalid/hooks/echobox')).toBeTruthy();
    });

    expect(document.body.textContent ?? '').not.toContain('secret');
  });

  it('AC-019-2: eine ungueltige Ziel-URL ergibt eine Fehlermeldung am Feld und legt keinen Endpunkt an', async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
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
        },
        422,
      ),
    );

    render(<EndpointsPage />);

    const user = userEvent.setup();
    const urlField = await screen.findByLabelText(/ziel-url/i);
    await user.type(urlField, 'http://orders.example.invalid/hook');
    await user.click(screen.getByRole('button', { name: /registrier/i }));

    await waitFor(() => {
      expect(screen.getByText('Die Ziel-URL muss das Schema https verwenden.')).toBeTruthy();
    });

    const calls = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/api/v1/endpoints'));
    expect(calls.every((call) => (call[1] as RequestInit | undefined)?.method !== 'POST')).toBe(true);
  });

  it('AC-019-3: beim Pausieren wechselt der Status sofort auf "pausiert"', async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (init?.method === 'POST' && url.includes('/pause')) {
        return jsonResponse({ ...ENDPOINT, status: 'paused', paused_at: '2026-10-21T11:40:00.000Z' });
      }
      return jsonResponse({ items: [ENDPOINT], page: { limit: 50, has_more: false, next_cursor: null } });
    });

    render(<EndpointsPage />);

    const user = userEvent.setup();
    const pauseButton = await screen.findByRole('button', { name: /pausier/i });
    await user.click(pauseButton);

    await waitFor(() => {
      expect(screen.getByText('pausiert')).toBeTruthy();
    });
    expect(screen.getByRole('button', { name: /reaktivier/i })).toBeTruthy();
  });

  it('AC-019-4: die Secret-Rotation wird bestaetigt, ohne das Secret anzuzeigen', async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (init?.method === 'POST' && url.includes('/secret-rotation')) {
        return jsonResponse({
          endpoint_id: ENDPOINT.endpoint_id,
          secret_version: 2,
          rotated_at: '2026-10-21T12:05:11.204Z',
          rotated_by: 'user-42@orders.example.invalid',
        });
      }
      return jsonResponse({ items: [ENDPOINT], page: { limit: 50, has_more: false, next_cursor: null } });
    });

    render(<EndpointsPage />);

    const user = userEvent.setup();
    const rotateButton = await screen.findByRole('button', { name: /rotier/i });
    await user.click(rotateButton);

    await waitFor(() => {
      expect(screen.getByText(/rotation/i)).toBeTruthy();
    });

    expect(document.body.textContent ?? '').not.toContain('neues-s3cr3t-rotationswert');
  });
});
