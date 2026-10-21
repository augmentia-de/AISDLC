import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';

import {
  createEndpointAction,
  pauseEndpointAction,
  resumeEndpointAction,
  rotateEndpointSecretAction,
} from '../../../src/main/frontend/src/app/actions';
import EndpointsPage from '../../../src/main/frontend/src/app/endpoints/page';
import type { Endpoint } from '../../../src/main/frontend/src/types/api';
import { jsonResponse, runAction, searchParams } from './support/actionRunner';

/**
 * Unit-Ebene der Oberflaeche — Endpunktseite (US-019, FR-001, FR-002, FR-003).
 *
 * Getestet wird das Verhalten der Seite, nicht ihre Implementierung:
 * Endpunkte mit Status, Ziel-URL (ohne Secret) und Ereignistypen; eine
 * verstaendliche Fehlermeldung am Feld; der Statuswechsel beim Pausieren;
 * die Rotationsbestaetigung ohne Klartext.
 *
 * <strong>Die Seite ist ein Server Component</strong> (ADR-018 §8). Der Test
 * rendert die Seite deshalb **mit dem Ergebnis der Seite** — die Abfrage
 * (was wird beim Dienst angefragt?), die Aktion (was wird getan?) und die
 * Anzeige (was steht da?) werden gemeinsam geprueft. Die Aktionen laufen
 * ueber einen echten Formularabschick — ohne JavaScript.
 *
 * Testorakel: `docs/api/openapi.yaml` -> `listEndpoints`, `createEndpoint`,
 * `pauseEndpoint`, `resumeEndpoint`, `rotateEndpointSecret`; ADR-018.
 *
 * Abgedeckte Akzeptanzkriterien: AC-019-1, AC-019-2, AC-019-3, AC-019-4.
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

const ENDPOINT: Endpoint = {
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

/** Der Zustand des Dienst-Doppelgaengers — er veraendert sich mit den Aktionen. */
interface DienstZustand {
  endpunkt: Endpoint;
  aufrufe: Array<{ url: string; methode: string }>;
}

/**
 * Installiert einen Dienst-Doppelgaenger, der den Vertrag bedient.
 *
 * @returns der Zustand des Doppelgaengers
 */
function stubeDienst(): DienstZustand {
  const zustand: DienstZustand = { endpunkt: { ...ENDPOINT }, aufrufe: [] };

  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const methode = init?.method ?? 'GET';
      zustand.aufrufe.push({ url, methode });

      if (methode === 'POST') {
        if (url.includes('/pause')) {
          zustand.endpunkt = {
            ...zustand.endpunkt,
            status: 'paused',
            paused_at: '2026-10-21T11:40:00.000Z',
          };
          return jsonResponse(zustand.endpunkt);
        }
        if (url.includes('/resume')) {
          zustand.endpunkt = { ...zustand.endpunkt, status: 'active', paused_at: null };
          return jsonResponse(zustand.endpunkt);
        }
        if (url.includes('/secret-rotation')) {
          return jsonResponse({
            endpoint_id: zustand.endpunkt.endpoint_id,
            secret_version: zustand.endpunkt.secret_version + 1,
            rotated_at: '2026-10-21T12:05:11.204Z',
            rotated_by: 'user-42@orders.example.invalid',
          });
        }
        return jsonResponse(zustand.endpunkt);
      }

      return jsonResponse({
        items: [zustand.endpunkt],
        page: { limit: 50, has_more: false, next_cursor: null },
      });
    }),
  );

  return zustand;
}

/**
 * Rendert die Endpunktseite mit einem Ergebnis in der URL.
 *
 * @param ergebnis der Ergebniscode aus der Query-Datei (optional)
 * @returns die gerenderte Seite
 */
async function renderPage(ergebnis?: string) {
  const parameter: Record<string, string> = ergebnis === undefined ? {} : { ergebnis };
  const element = await EndpointsPage({ searchParams: searchParams(parameter) });
  return render(element);
}

describe('Endpunktseite', () => {
  beforeEach(() => {
    mocks.cookieValue = 'token-des-mandanten';
    mocks.revalidatePath.mockClear();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('AC-019-1: zeigt Status, Ziel-URL ohne Secret und die Ereignistypen', async () => {
    stubeDienst();

    await renderPage();

    expect(screen.getByText('https://orders.example.invalid/hooks/echobox')).toBeTruthy();
    expect(screen.getByText('order.changed')).toBeTruthy();
    expect(screen.getByText('aktiv')).toBeTruthy();
    expect(document.body.textContent ?? '').not.toContain('s3cr3t-rotationswert');
  });

  it('AC-019-1: das Secret wird nirgends angezeigt', async () => {
    stubeDienst();

    await renderPage();

    expect(document.body.textContent ?? '').not.toContain('secret');
  });

  it('AC-019-2: eine ungueltige Ziel-URL ergibt eine Fehlermeldung am Feld und legt keinen Endpunkt an', async () => {
    const dienst = stubeDienst();

    const ziel = await runAction(createEndpointAction, {
      target_url: 'http://orders.example.invalid/hook',
      secret: 's3cr3t-rotationswert',
      event_types: 'order.changed',
    });

    expect(ziel).toBe('/endpoints?ergebnis=url-ungueltig');
    // Es wurde **kein** Endpunkt angelegt: der Dienst wurde nicht gefragt.
    expect(dienst.aufrufe).toHaveLength(0);

    // Die Meldung steht **am Feld** — nicht nur am Seitenkopf (ADR-018 §7).
    await renderPage('url-ungueltig');

    const feld = screen.getByLabelText(/ziel-url/i);
    expect(feld.getAttribute('aria-describedby')).toBe('endpoint-target-url-error');
    expect(screen.getByText('Die Ziel-URL muss das Schema https verwenden.')).toBeTruthy();
  });

  it('AC-019-3: beim Pausieren wechselt der Status auf "pausiert"', async () => {
    const dienst = stubeDienst();

    const ziel = await runAction(pauseEndpointAction, { endpoint_id: ENDPOINT.endpoint_id });

    expect(ziel).toBe('/endpoints?ergebnis=pausiert');
    expect(dienst.aufrufe).toHaveLength(1);
    expect(dienst.aufrufe[0]?.url).toContain(`/api/v1/endpoints/${ENDPOINT.endpoint_id}/pause`);
    expect(dienst.aufrufe[0]?.methode).toBe('POST');

    // Die Seite zeigt den neuen Zustand — die Aktion hat den Zustand geaendert.
    await renderPage('pausiert');

    expect(screen.getByText('pausiert')).toBeTruthy();
    expect(screen.getByRole('button', { name: /reaktivier/i })).toBeTruthy();
  });

  it('AC-019-3: das Reaktivieren meldet das Ergebnis', async () => {
    const dienst = stubeDienst();

    const ziel = await runAction(resumeEndpointAction, { endpoint_id: ENDPOINT.endpoint_id });

    expect(ziel).toBe('/endpoints?ergebnis=reaktiviert');
    expect(dienst.aufrufe[0]?.url).toContain(`/api/v1/endpoints/${ENDPOINT.endpoint_id}/resume`);
  });

  it('AC-019-4: die Secret-Rotation wird bestaetigt, ohne das Secret anzuzeigen', async () => {
    const dienst = stubeDienst();

    const ziel = await runAction(rotateEndpointSecretAction, {
      endpoint_id: ENDPOINT.endpoint_id,
      secret: 'neues-s3cr3t-rotationswert',
    });

    expect(ziel).toBe(`/endpoints?ergebnis=rotiert&endpunkt=${ENDPOINT.endpoint_id}`);
    expect(dienst.aufrufe[0]?.url).toContain(
      `/api/v1/endpoints/${ENDPOINT.endpoint_id}/secret-rotation`,
    );
    expect(dienst.aufrufe[0]?.methode).toBe('POST');

    await renderPage('rotiert');

    expect(document.body.textContent ?? '').not.toContain('neues-s3cr3t-rotationswert');
    expect(screen.getByText(/rotation/i)).toBeTruthy();
  });

  it('AC-019-2: ein zu kurzes Secret wird am Feld beanstandet, ohne Anfrage an den Dienst', async () => {
    const dienst = stubeDienst();

    const ziel = await runAction(createEndpointAction, {
      target_url: 'https://orders.example.invalid/hooks/echobox',
      secret: 'zu-kurz',
      event_types: 'order.changed',
    });

    expect(ziel).toBe('/endpoints?ergebnis=secret-zu-kurz');
    expect(dienst.aufrufe).toHaveLength(0);
  });
});
