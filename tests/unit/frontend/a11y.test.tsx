import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, waitFor } from '@testing-library/react';
import axe from 'axe-core';

import EndpointsPage from '../../../src/main/frontend/src/app/endpoints/page';
import DeliveryProtocolPage from '../../../src/main/frontend/src/app/deliveries/page';
import DeadDeliveryPage from '../../../src/main/frontend/src/app/dead-deliveries/page';

/**
 * Unit-Ebene der Oberflaeche — Barrierefreiheit (US-021, NFR-005).
 *
 * Der automatische WCAG-2.1-AA-Scan laeuft ueber alle drei Seiten. Im
 * Browser-Test (Playwright) wird der volle Scan gefahren; dieser Test prueft
 * die Komponentenebene, damit ein Verstoss schon beim Komponententest
 * auffaellt und nicht erst im E2E-Lauf.
 *
 * <strong>Grenze des jsdom-Scans:</strong> jsdom kennt kein Layout und keine
 * Farben — die Kontrastpruefung (AC-021-3) kann hier nicht laufen. Sie ist im
 * Playwright-Test {@code tests/e2e/a11y.spec.ts} abgesichert, weil nur der
 * echte Browser Farben berechnet.
 *
 * Testorakel: ADR-018; `docs/api/openapi.yaml` -> `x-requirement-coverage`
 * (NFR-005).
 *
 * Abgedeckte Akzeptanzkriterien: AC-021-2, AC-021-4.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status < 400,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

const EMPTY_LIST = { items: [], page: { limit: 50, has_more: false, next_cursor: null } };

describe('Barrierefreiheit der Seiten', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(EMPTY_LIST)));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('AC-021-2: die Endpunktseite hat keine schweren axe-Verstoesse', async () => {
    const { container } = render(<EndpointsPage />);

    await waitFor(() => {
      expect(container).toBeTruthy();
    });

    const results = await axe.run(container, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    });

    const serious = results.violations.filter(
      (violation) => violation.impact === 'critical' || violation.impact === 'serious',
    );

    expect(serious.map((violation) => `${violation.id}: ${violation.help}`)).toEqual([]);
  });

  it('AC-021-2: die Protokollseite hat keine schweren axe-Verstoesse', async () => {
    const { container } = render(<DeliveryProtocolPage />);

    const results = await axe.run(container, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    });

    const serious = results.violations.filter(
      (violation) => violation.impact === 'critical' || violation.impact === 'serious',
    );

    expect(serious.map((violation) => `${violation.id}: ${violation.help}`)).toEqual([]);
  });

  it('AC-021-2: die Seite der toten Zustellungen hat keine schweren axe-Verstoesse', async () => {
    const { container } = render(<DeadDeliveryPage />);

    const results = await axe.run(container, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    });

    const serious = results.violations.filter(
      (violation) => violation.impact === 'critical' || violation.impact === 'serious',
    );

    expect(serious.map((violation) => `${violation.id}: ${violation.help}`)).toEqual([]);
  });

  it('AC-021-4: Liste, Zeileninhalte und Statusmeldungen tragen Rolle und Inhalt', async () => {
    const { container } = render(<EndpointsPage />);

    await waitFor(() => {
      expect(container).toBeTruthy();
    });

    const namedRoles = container.querySelectorAll('[role], table, main, nav, h1, h2');
    expect(namedRoles.length).toBeGreaterThan(0);

    const results = await axe.run(container, {
      runOnly: { type: 'rule', values: ['aria-required-children', 'aria-required-parent', 'aria-roles', 'aria-command-name'] },
    });

    expect(results.violations.map((violation) => violation.id)).toEqual([]);
  });
});
