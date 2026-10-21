import { signInAction } from '@/app/actions';
import { ErrorMessage } from '@/components/ErrorMessage';
import { FieldError } from '@/components/FieldError';
import { Notice } from '@/components/Notice';
import { feedbackFor } from '@/lib/feedback';
import { param, type SearchParams } from '@/lib/searchParams';
import { readSession } from '@/lib/session';
import Link from 'next/link';

/**
 * Die Startseite.
 *
 * <strong>Die Anmeldung</strong>: das Bearer-Token wird ueber eine
 * **Server Action** im httpOnly-Cookie abgelegt. Das ist die Voraussetzung
 * dafuer, dass die Seiten **Server Components** sind (ADR-018 §8): nur der
 * Server kann den Dienst ansprechen, und nur der Server kennt das Token.
 *
 * <strong>Ohne JavaScript bedienbar:</strong> das Formular ist eine
 * Formularaktion; nach dem Absenden liegt das Token im Cookie und die Seite
 * zeigt die Endpunkte.
 *
 * @param props die Eigenschaften der Seite
 * @returns die Startseite
 */
export default async function HomePage({ searchParams }: { searchParams: SearchParams }) {
  const [session, params] = await Promise.all([readSession(), searchParams]);
  const feedback = feedbackFor(param(params.ergebnis));

  return (
    <main id="inhalt" className="page">
      <h1>EchoBox</h1>
      <p className="page__intro">
        Diese Oberfläche zeigt die Endpunkte, das Zustellprotokoll und die toten Zustellungen des
        Dienstes.
      </p>

      {feedback.notice !== null ? <Notice message={feedback.notice} /> : null}
      {feedback.error !== null ? <ErrorMessage message={feedback.error} /> : null}

      {session.token !== null ? (
        <section className="card" aria-labelledby="angemeldet-heading">
          <h2 id="angemeldet-heading">Angemeldet</h2>
          <p>Sie sind mit einem Token angemeldet. Die Seiten zeigen die Daten Ihres Mandanten.</p>
          <ul className="link-list">
            <li>
              <Link href="/endpoints">Endpunkte verwalten</Link>
            </li>
            <li>
              <Link href="/deliveries">Zustellprotokoll lesen</Link>
            </li>
            <li>
              <Link href="/dead-deliveries">Tote Zustellungen ansehen</Link>
            </li>
          </ul>
        </section>
      ) : (
        <section className="card" aria-labelledby="anmelden-heading">
          <h2 id="anmelden-heading">Anmelden</h2>
          <p>
            Bitte geben Sie Ihr Bearer-Token ein. Es wird ausschließlich in einem httpOnly-Cookie
            abgelegt und nie im Browser-Speicher gespeichert.
          </p>

          <form className="form" action={signInAction}>
            <div className="form__field">
              <label htmlFor="signin-token">Bearer-Token</label>
              <input
                id="signin-token"
                name="token"
                type="password"
                autoComplete="off"
                aria-invalid={feedback.error !== null}
                aria-describedby={feedback.error !== null ? 'signin-token-error' : undefined}
              />
              {feedback.error !== null ? (
                <FieldError id="signin-token-error" message={feedback.error} />
              ) : null}
            </div>

            <div className="form__actions">
              <button type="submit" className="button button--primary">
                Anmelden
              </button>
            </div>
          </form>
        </section>
      )}
    </main>
  );
}
