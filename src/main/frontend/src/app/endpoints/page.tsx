import { ErrorMessage } from '@/components/ErrorMessage';
import { Notice } from '@/components/Notice';
import { EndpointForm } from '@/features/endpoints/EndpointForm';
import { EndpointList } from '@/features/endpoints/EndpointList';
import { loadEndpointList } from '@/features/endpoints/endpointQueries';
import { feedbackFor } from '@/lib/feedback';
import { param, type SearchParams } from '@/lib/searchParams';
import { readSession } from '@/lib/session';
import Link from 'next/link';

/**
 * Die Endpunktseite (US-019, FR-001, FR-002, FR-003).
 *
 * <strong>Ein Server Component</strong> (ADR-018 §8): die Seite rendert auf
 * dem Server, holt die Daten beim Dienst und stellt die Aktionen als
 * **Formularaktionen** bereit. Die Seite ist **ohne JavaScript bedienbar** —
 * es gibt keinen Klickhandler und keinen Zustand im Browser.
 *
 * <strong>Der Mandant kommt aus dem Token</strong> (D-01); die Seite kennt
 * keinen Mandantenparameter.
 *
 * @param props die Eigenschaften der Seite
 * @returns die Endpunktseite
 */
export default async function EndpointsPage({ searchParams }: { searchParams: SearchParams }) {
  const [session, params] = await Promise.all([readSession(), searchParams]);

  const feedback = feedbackFor(param(params.ergebnis), param(params.endpunkt) ?? undefined);
  const result = await loadEndpointList(session.token);

  if (result.problem !== null && result.problem.status === 401) {
    return (
      <main id="inhalt" className="page">
        <h1>Endpunkte</h1>
        <ErrorMessage message="Die Anmeldung fehlt oder ist abgelaufen. Bitte melden Sie sich erneut an." />
        <p>
          <Link href="/">Zur Anmeldung</Link>
        </p>
      </main>
    );
  }

  return (
    <main id="inhalt" className="page">
      <h1>Endpunkte</h1>
      <p className="page__intro">
        Hier verwalten Sie die Empfaenger-URLs Ihres Mandanten. Das Secret wird verschluesselt
        abgelegt und nie im Klartext ausgegeben.
      </p>

      <EndpointForm fieldError={feedback.field} />

      {feedback.notice !== null ? <Notice message={feedback.notice} /> : null}
      {feedback.error !== null ? <ErrorMessage message={feedback.error} /> : null}
      {result.problem !== null && result.problem.status !== 401 ? (
        <ErrorMessage message="Die Endpunkte konnten nicht geladen werden." />
      ) : null}

      <EndpointList items={result.items} rotationError={feedback.rotationField} />
    </main>
  );
}
