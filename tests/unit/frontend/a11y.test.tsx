import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from '@testing-library/react';
import axe from 'axe-core';

import DeadDeliveryPage from '../../../src/main/frontend/src/app/dead-deliveries/page';
import DeliveryProtocolPage from '../../../src/main/frontend/src/app/deliveries/page';
import EndpointsPage from '../../../src/main/frontend/src/app/endpoints/page';
import { jsonResponse, searchParams } from './support/actionRunner';
import { testToken } from './support/token';

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

const mocks = vi.hoisted(() => ({
  cookieValue: null as string | null,
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

vi.mock('next/navigation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('next/navigation')>()),
  redirect: (target: string) => {
    throw Object.assign(new Error('NEXT_REDIRECT'), { digest: `NEXT_REDIRECT;${target}` });
  },
}));

const LEERE_LISTE = { items: [], page: { limit: 50, has_more: false, next_cursor: null } };

describe('Barrierefreiheit der Seiten', () => {
  beforeEach(() => {
    mocks.cookieValue = testToken(['service_admin', 'receiver_admin']);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(LEERE_LISTE)));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('AC-021-2: die Endpunktseite hat keine schweren axe-Verstoesse', async () => {
    const { container } = render(
      await EndpointsPage({ searchParams: searchParams({}) }),
    );

    const results = await axe.run(container, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    });

    const serious = results.violations.filter(
      (violation) => violation.impact === 'critical' || violation.impact === 'serious',
    );

    expect(serious.map((violation) => `${violation.id}: ${violation.help}`)).toEqual([]);
  });

  it('AC-021-2: die Protokollseite hat keine schweren axe-Verstoesse', async () => {
    const { container } = render(
      await DeliveryProtocolPage({ searchParams: searchParams({}) }),
    );

    const results = await axe.run(container, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    });

    const serious = results.violations.filter(
      (violation) => violation.impact === 'critical' || violation.impact === 'serious',
    );

    expect(serious.map((violation) => `${violation.id}: ${violation.help}`)).toEqual([]);
  });

  it('AC-021-2: die Seite der toten Zustellungen hat keine schweren axe-Verstoesse', async () => {
    const { container } = render(
      await DeadDeliveryPage({ searchParams: searchParams({}) }),
    );

    const results = await axe.run(container, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    });

    const serious = results.violations.filter(
      (violation) => violation.impact === 'critical' || violation.impact === 'serious',
    );

    expect(serious.map((violation) => `${violation.id}: ${violation.help}`)).toEqual([]);
  });

  it('AC-021-4: Liste, Zeileninhalte und Statusmeldungen tragen Rolle und Inhalt', async () => {
    const { container } = render(
      await EndpointsPage({ searchParams: searchParams({}) }),
    );

    const namedRoles = container.querySelectorAll('[role], table, main, nav, h1, h2');
    expect(namedRoles.length).toBeGreaterThan(0);

    const results = await axe.run(container, {
      runOnly: { type: 'rule', values: ['aria-required-children', 'aria-required-parent', 'aria-roles', 'aria-command-name'] },
    });

    expect(results.violations.map((violation) => violation.id)).toEqual([]);
  });
});
