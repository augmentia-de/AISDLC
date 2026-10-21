import type { Metadata } from 'next';
import Link from 'next/link';
import type { ReactNode } from 'react';

import { signOutAction } from '@/app/actions';
import { readSession } from '@/lib/session';

import './globals.css';

/**
 * Das Grundgeruest der Oberflaeche.
 *
 * <strong>Landmarken und Sprungmarke</strong> (ADR-018 §2): die Sprungmarke
 * fuehrt direkt zum Hauptinhalt, die Navigation traegt eine
 * `aria-label`-Beschriftung, und der Hauptinhalt liegt in genau einem
 * `<main>`.
 *
 * <strong>Der Abmelde-Knopf ist eine Formularaktion</strong> (ADR-018 §8):
 * er loest das Token-Cookie ohne JavaScript.
 */

export const metadata: Metadata = {
  title: 'EchoBox',
  description: 'EchoBox — Endpunkte, Zustellprotokoll und tote Zustellungen.',
};

/** Die Eigenschaften des Grundgeruestes. */
export interface RootLayoutProps {
  /** Der Inhalt der Seite. */
  children: ReactNode;
}

/**
 * Zeigt das Grundgeruest.
 *
 * @param props die Eigenschaften des Grundgeruestes
 * @returns das Grundgeruest
 */
export default async function RootLayout({ children }: RootLayoutProps) {
  const session = await readSession();

  return (
    <html lang="de">
      <body>
        <a className="skip-link" href="#inhalt">
          Zum Hauptinhalt springen
        </a>

        <header className="app-header">
          <p className="app-header__brand">EchoBox</p>
          {session.token !== null ? (
            <form className="app-header__session" action={signOutAction}>
              <button type="submit" className="button">
                Abmelden
              </button>
            </form>
          ) : null}
        </header>

        <nav className="app-nav" aria-label="Hauptnavigation">
          <ul>
            <li>
              <Link href="/endpoints">Endpunkte</Link>
            </li>
            <li>
              <Link href="/deliveries">Zustellprotokoll</Link>
            </li>
            <li>
              <Link href="/dead-deliveries">Tote Zustellungen</Link>
            </li>
          </ul>
        </nav>

        {children}

        <footer className="app-footer">
          <p>EchoBox — Webhooks zustellen und nachvollziehen.</p>
        </footer>
      </body>
    </html>
  );
}
