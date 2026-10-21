import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';

import DeadDeliveryPage from '../../../src/main/frontend/src/app/dead-deliveries/page';
import DeliveryProtocolPage from '../../../src/main/frontend/src/app/deliveries/page';
import EndpointsPage from '../../../src/main/frontend/src/app/endpoints/page';
import { jsonResponse, searchParams } from './support/actionRunner';
import { testToken } from './support/token';

/**
 * Unit-Ebene der Oberflaeche — **progressive Erhoehung** (ADR-018 §8).
 *
 * ADR-018 §8 (bei Gate 1 bestaetigt): „Alle drei Seiten sind ohne JavaScript
 * **bedienbar** (Server Components, progressive Erhoehung) ... Interaktive
 * Elemente (Pause/Reaktivieren, Replay, Filter) arbeiten als
 * **Formularaktion**.“
 *
 * <strong>Dieser Test ist der Nachweis dieser Zusage.</strong> Er prueft
 * zweierlei:
 *
 * 1. <strong>Die Quelle:</strong> keine Datei der Oberflaeche ist eine Client
 *    Komponente (`'use client'`), keine benutzt einen Klickhandler oder einen
 *    Zustand im Browser. Die Seiten sind Server Components.
 * 2. <strong>Das Ergebnis:</strong> jede Schaltflaeche der gerenderten Seiten
 *    liegt in einem `<form>` — eine Formularaktion. Ohne JavaScript wird das
 *    Formular normal abgeschickt; die Seite bleibt bedienbar.
 *
 * Testorakel: ADR-018 §8.
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

/**
 * Findet das Wurzelverzeichnis der Oberflaeche.
 *
 * <strong>Warum eine Suche nach oben?</strong> der Test laeuft aus der
 * Projektwurzel **oder** aus dem Verzeichnis der Oberflaeche — der Pfad der
 * Testdatei ist in beiden Faellen nicht verlaesslich. Gesucht wird deshalb
 * das Verzeichnis, in dem die Oberflaeche liegt.
 *
 * @returns der Pfad zum Quelltext der Oberflaeche
 */
function findeOberflaeche(): string {
  let verzeichnis = process.cwd();

  for (let schritt = 0; schritt < 6; schritt += 1) {
    const kandidat = join(verzeichnis, 'src', 'main', 'frontend', 'src');
    if (existsSync(kandidat)) {
      return kandidat;
    }
    verzeichnis = dirname(verzeichnis);
  }

  throw new Error('Das Verzeichnis der Oberflaeche wurde nicht gefunden.');
}

/** Das Wurzelverzeichnis der Oberflaeche. */
const OBERFLAECHE = findeOberflaeche();

/**
 * Sammelt alle Quelltextdateien der Oberflaeche.
 *
 * @param verzeichnis das Verzeichnis
 * @returns die Pfade der Dateien
 */
function quellen(verzeichnis: string): string[] {
  const ergebnis: string[] = [];
  for (const eintrag of readdirSync(verzeichnis, { withFileTypes: true })) {
    const pfad = join(verzeichnis, eintrag.name);
    if (eintrag.isDirectory()) {
      ergebnis.push(...quellen(pfad));
    } else if (eintrag.name.endsWith('.ts') || eintrag.name.endsWith('.tsx')) {
      ergebnis.push(pfad);
    }
  }
  return ergebnis;
}

const LEERE_LISTE = { items: [], page: { limit: 50, has_more: false, next_cursor: null } };

/** Der aufgegebene Zustelllauf — die Quelle des Empfaenger-Admins. */
const AUFGEGEBENER_LAUF = {
  delivery_run_id: 'b4c3d2e1-7f60-4a5b-9c8d-2e3f4a5b6c7d',
  event_id: '5d8e9f0a-1b2c-4d3e-8f90-a1b2c3d4e5f6',
  endpoint_id: '8f1c2f0e-2f4a-4a0e-9d5b-6a1c2e3f4a5b',
  organisation_id: 'm-1',
  event_type: 'order.changed',
  status: 'abandoned',
  attempt_count: 8,
  max_attempts: 8,
  replay_of: null,
  last_attempt_at: '2026-10-21T10:22:11.900Z',
  next_attempt_at: null,
  last_response_status: 503,
  last_error_code: null,
  rejection_reason: null,
  created_at: '2026-10-21T09:40:05.120Z',
  updated_at: '2026-10-21T10:22:11.900Z',
};

/** Die Antwort des Dienstes mit dem aufgegebenen Zustelllauf. */
const AUFGEGEBENE_LISTE = {
  items: [AUFGEGEBENER_LAUF],
  page: { limit: 50, has_more: false, next_cursor: null },
};

describe('Progressive Erhoehung (ADR-018 §8)', () => {
  beforeEach(() => {
    mocks.cookieValue = testToken(['service_admin', 'receiver_admin']);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(LEERE_LISTE)));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('keine Datei der Oberflaeche ist eine Client Komponente', () => {
    const treffer = quellen(OBERFLAECHE).filter((pfad) =>
      readFileSync(pfad, 'utf8').includes("'use client'"),
    );

    expect(treffer).toEqual([]);
  });

  it('keine Datei der Oberflaeche benutzt einen Klickhandler oder Browserzustand', () => {
    // Die Muster sind **Code-Muster** (`onClick=`), damit ein erwaehnendes
    // Kommentar (etwa "ein `localStorage`-Token waere ...") nicht zaehlt.
    const verboten = ['onClick=', 'onSubmit=', 'useState(', 'useEffect(', 'localStorage.'];
    const treffer: string[] = [];

    for (const pfad of quellen(OBERFLAECHE)) {
      const inhalt = readFileSync(pfad, 'utf8');
      for (const muster of verboten) {
        if (inhalt.includes(muster)) {
          treffer.push(`${pfad}: ${muster}`);
        }
      }
    }

    expect(treffer).toEqual([]);
  });

  it('jede Schaltflaeche der Endpunktseite liegt in einem Formular', async () => {
    render(await EndpointsPage({ searchParams: searchParams({}) }));

    const knoepfe = Array.from(document.querySelectorAll('button'));
    expect(knoepfe.length).toBeGreaterThan(0);

    for (const knopf of knoepfe) {
      expect(knopf.closest('form')).not.toBeNull();
    }
  });

  it('jede Schaltflaeche der Protokollseite liegt in einem Formular', async () => {
    render(await DeliveryProtocolPage({ searchParams: searchParams({}) }));

    // Nur die **aktivierbaren** Schaltflaeche: die abgeschalteten der
    // Seitennavigation haben keine Funktion und brauchen deshalb kein
    // Formular.
    const knoepfe = Array.from(document.querySelectorAll('button:not([disabled])'));
    expect(knoepfe.length).toBeGreaterThan(0);

    for (const knopf of knoepfe) {
      expect(knopf.closest('form')).not.toBeNull();
    }
  });

  it('jede Schaltflaeche der Seite der toten Zustellungen liegt in einem Formular', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(AUFGEGEBENE_LISTE)));

    render(await DeadDeliveryPage({ searchParams: searchParams({}) }));

    const knoepfe = Array.from(document.querySelectorAll('button:not([disabled])'));
    expect(knoepfe.length).toBeGreaterThan(0);

    for (const knopf of knoepfe) {
      expect(knopf.closest('form')).not.toBeNull();
    }
  });

  it('der Filter des Zustellprotokolls ist ein GET-Formular — ohne JavaScript bedienbar', async () => {
    render(await DeliveryProtocolPage({ searchParams: searchParams({}) }));

    const formular = document.querySelector('form.filters');
    expect(formular?.getAttribute('method')).toBe('get');
    expect(formular?.getAttribute('action')).toBe('/deliveries');
  });

  it('das erneute Ausloesen ist eine Formularaktion des Empfaenger-Admins', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(AUFGEGEBENE_LISTE)));

    render(await DeadDeliveryPage({ searchParams: searchParams({}) }));

    const knopf = screen.getByRole('button', { name: /erneut auslösen/i });
    const formular = knopf.closest('form');

    expect(formular).not.toBeNull();
    expect(formular?.querySelector('input[name="delivery_run_id"]')?.getAttribute('value')).toBe(
      'b4c3d2e1-7f60-4a5b-9c8d-2e3f4a5b6c7d',
    );
  });
});
