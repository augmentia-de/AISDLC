import { EmptyState } from '@/components/EmptyState';
import { ErrorMessage } from '@/components/ErrorMessage';
import { Pagination } from '@/components/Pagination';
import { DeliveryFilters } from '@/features/protocol/DeliveryFilters';
import { DeliveryTable } from '@/features/protocol/DeliveryTable';
import { filterFromParams, loadDeliveryList } from '@/features/protocol/deliveryQueries';
import { param, type SearchParams } from '@/lib/searchParams';
import { readSession } from '@/lib/session';
import Link from 'next/link';

/**
 * Die Zustellprotokollseite (US-020, FR-030, AC-020-1).
 *
 * <strong>Ein Server Component</strong> (ADR-018 §8): die Seite rendert auf
 * dem Server. Die Filter sind ein **GET-Formular**, die Seitennavigation
 * sind **Links** — beides funktioniert ohne JavaScript.
 *
 * <strong>Mandant vor Rolle</strong> (V-39): ein 404 des Dienstes (fremde
 * Ressource) wird als Leerzustand behandelt — fremde Daten erscheinen
 * <strong>nicht</strong> (AC-020-4, ADR-018 §6).
 *
 * @param props die Eigenschaften der Seite
 * @returns die Zustellprotokollseite
 */
export default async function DeliveryProtocolPage({ searchParams }: { searchParams: SearchParams }) {
  const [session, params] = await Promise.all([readSession(), searchParams]);

  const filter = filterFromParams(params);
  const cursor = param(params.cursor);
  const result = await loadDeliveryList(session.token, filter, cursor);

  const navParams: Record<string, string> = {};
  if (filter.endpointId.length > 0) {
    navParams.endpoint_id = filter.endpointId;
  }
  if (filter.status.length > 0) {
    navParams.status = filter.status;
  }
  if (filter.from.length > 0) {
    navParams.from = filter.from;
  }
  if (filter.to.length > 0) {
    navParams.to = filter.to;
  }

  if (result.problem !== null && result.problem.status === 401) {
    return (
      <main id="inhalt" className="page">
        <h1>Zustellprotokoll</h1>
        <ErrorMessage message="Die Anmeldung fehlt oder ist abgelaufen. Bitte melden Sie sich erneut an." />
        <p>
          <Link href="/">Zur Anmeldung</Link>
        </p>
      </main>
    );
  }

  return (
    <main id="inhalt" className="page">
      <h1>Zustellprotokoll</h1>
      <p className="page__intro">
        Hier sehen Sie je Zustellvorgang den Zustand, die Anzahl der Versuche, den Zeitpunkt des
        letzten Versuchs, den Antwortcode des Empfängers und den nächsten Versuchszeitpunkt. Die
        Suche läuft ausschließlich über Metadaten.
      </p>

      <DeliveryFilters filter={filter} />

      {result.problem !== null && result.problem.status !== 401 ? (
        <ErrorMessage message="Das Zustellprotokoll konnte nicht geladen werden." />
      ) : null}

      {result.items.length === 0 ? (
        <EmptyState
          title="Keine Zustellvorgänge gefunden."
          hint="Passen Sie die Filter an oder wählen Sie einen anderen Zeitraum."
        />
      ) : null}

      {result.items.length > 0 ? <DeliveryTable items={result.items} /> : null}

      <Pagination path="/deliveries" params={navParams} state={{ current: cursor, next: result.page.next_cursor, hasMore: result.page.has_more }} />
    </main>
  );
}
