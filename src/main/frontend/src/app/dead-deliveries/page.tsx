import { EmptyState } from '@/components/EmptyState';
import { ErrorMessage } from '@/components/ErrorMessage';
import { Notice } from '@/components/Notice';
import { Pagination } from '@/components/Pagination';
import { DeadDeliveryTable } from '@/features/dead/DeadDeliveryTable';
import { loadDeadDeliveries } from '@/features/dead/deadDeliveryQueries';
import { feedbackFor } from '@/lib/feedback';
import { param, type SearchParams } from '@/lib/searchParams';
import { readSession } from '@/lib/session';
import Link from 'next/link';

/**
 * Die Seite der toten Zustellungen (US-020, FR-031, FR-024).
 *
 * <strong>Ein Server Component</strong> (ADR-018 §8): die Seite rendert auf
 * dem Server, das erneute Ausloesen ist eine **Formularaktion**.
 *
 * <strong>Die Liste kommt aus der Operation, die die Identitaet lesen
 * darf</strong> (O-14): die Totenliste ist mandantenuebergreifend und nur der
 * Service-Administration vorbegeben; der Empfaenger-Admin liest die
 * aufgegebenen Zustellungen ueber das Zustellprotokoll mit dem Filter
 * `status=abandoned`. So ist das erneute Ausloesen (FR-024) fuer den
 * Empfaenger-Admin erreichbar.
 *
 * <strong>Das erneute Ausloesen ist dem Empfaenger-Admin vorbehalten**
 * (D-15, F-03, V-39): die Aktion wird nur angeboten, wenn die Identitaet
 * diese Rolle traegt **und** der Vorgang ihrem Mandanten gehoert. Der Dienst
 * prueft es unabhaengig davon — ein fremder Mandant ergibt <strong>404</strong>,
 * die eigene Ressource mit falscher Rolle <strong>403</strong>.
 *
 * @param props die Eigenschaften der Seite
 * @returns die Seite der toten Zustellungen
 */
export default async function DeadDeliveryPage({ searchParams }: { searchParams: SearchParams }) {
  const [session, params] = await Promise.all([readSession(), searchParams]);

  const feedback = feedbackFor(param(params.ergebnis));
  const cursor = param(params.cursor);
  const result = await loadDeadDeliveries(session.token, session, cursor);

  if (result.problem !== null && result.problem.status === 401) {
    return (
      <main id="inhalt" className="page">
        <h1>Tote Zustellungen</h1>
        <ErrorMessage message="Die Anmeldung fehlt oder ist abgelaufen. Bitte melden Sie sich erneut an." />
        <p>
          <Link href="/">Zur Anmeldung</Link>
        </p>
      </main>
    );
  }

  return (
    <main id="inhalt" className="page">
      <h1>Tote Zustellungen</h1>
      <p className="page__intro">
        Hier sehen Sie die Zustellungen, die nach erschöpften Versuchen aufgegeben wurden — mit
        Ereignis-ID, Endpunkt, Ereignistyp, Zahl der Versuche und dem Zeitpunkt der Aufgabe.
      </p>

      {result.source === 'protokoll' ? (
        <p className="page__intro">
          Sie sehen die aufgegebenen Zustellungen Ihres Mandanten — gefiltert aus dem
          Zustellprotokoll.
        </p>
      ) : null}

      {feedback.notice !== null ? <Notice message={feedback.notice} /> : null}
      {feedback.error !== null ? <ErrorMessage message={feedback.error} /> : null}
      {result.problem !== null && result.problem.status !== 401 ? (
        <ErrorMessage message="Die toten Zustellungen konnten nicht geladen werden." />
      ) : null}

      {result.items.length === 0 ? (
        <EmptyState
          title="Keine toten Zustellungen gefunden."
          hint="In diesem Zeitraum wurde keine Zustellung aufgegeben."
        />
      ) : null}

      {result.items.length > 0 ? <DeadDeliveryTable items={result.items} /> : null}

      <Pagination
        path="/dead-deliveries"
        params={{}}
        state={{ current: cursor, next: result.page.next_cursor, hasMore: result.page.has_more }}
      />
    </main>
  );
}
