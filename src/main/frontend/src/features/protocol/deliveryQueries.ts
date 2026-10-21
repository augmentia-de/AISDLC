import { api } from '@/lib/apiClient';
import { dateToEndOfDay, dateToStartOfDay } from '@/lib/format';
import { emptyPage, PAGE_LIMIT, toProblem, type ListResult } from '@/lib/listResult';
import type { DeliveryList, DeliveryRun, DeliveryStatus } from '@/types/api';

/**
 * Die Abfrage des Zustellprotokolls.
 *
 * <strong>Die Filter des Vertrags</strong> (`endpoint_id`, `status`, `from`,
 * `to`) und die cursor-basierte Seitennavigation — <strong>kein `total`</strong>
 * (FR-030). Die Suche laeuft ausschliesslich ueber Metadaten (D-11).
 *
 * <strong>Der Zeitraumfilter</strong> bezieht sich im Zustellprotokoll auf den
 * <em>Anlegezeitpunkt</em> des Zustelllaufs (`created_at`) — denselben
 * Schluessel wie die Sortierung (ADR-017 §4, V-40).
 */

/** Der Zustand der Filter — die Werte stehen in der URL der Seite. */
export interface DeliveryFilter {
  /** Der Filter auf den Endpunkt (UUID). */
  endpointId: string;
  /** Der Filter auf den Zustand — der Vertragswert. */
  status: DeliveryStatus | '';
  /** Der Beginn des Zeitraums (`JJJJ-MM-TT`). */
  from: string;
  /** Das Ende des Zeitraums (`JJJJ-MM-TT`). */
  to: string;
}

/**
 * Liest die Filter aus den Query-Parametern der Seite.
 *
 * @param params die Query-Parameter der Seite
 * @returns der Zustand der Filter
 */
export function filterFromParams(
  params: Record<string, string | string[] | undefined>,
): DeliveryFilter {
  const single = (value: string | string[] | undefined): string => {
    if (typeof value === 'string') {
      return value;
    }
    if (Array.isArray(value) && value.length > 0) {
      return value[0] ?? '';
    }
    return '';
  };

  const status = single(params.status);

  return {
    endpointId: single(params.endpoint_id),
    status: status as DeliveryStatus | '',
    from: single(params.from),
    to: single(params.to),
  };
}

/**
 * Liest das Zustellprotokoll.
 *
 * @param token das Bearer-Token der Sitzung
 * @param filter die Filter der Seite
 * @param cursor der Cursor der Seite (`null` = die erste Seite)
 * @returns die Zustaende und das Fehlerbild der Abfrage
 */
export async function loadDeliveryList(
  token: string | null,
  filter: DeliveryFilter,
  cursor: string | null,
): Promise<ListResult<DeliveryRun>> {
  try {
    const list = await api.get<DeliveryList>('/deliveries', token, {
      endpoint_id: filter.endpointId.length > 0 ? filter.endpointId : undefined,
      status: filter.status.length > 0 ? filter.status : undefined,
      from: dateToStartOfDay(filter.from) ?? undefined,
      to: dateToEndOfDay(filter.to) ?? undefined,
      limit: PAGE_LIMIT,
      cursor: cursor ?? undefined,
    });

    return { items: list.items, page: list.page, problem: null };
  } catch (error) {
    const problem = toProblem(error);
    if (problem.status === 404) {
      // Fremde Ressource: der Leerzustand, keine Fehlermeldung (ADR-018 §6).
      return { items: [], page: emptyPage(), problem: null };
    }
    return { items: [], page: emptyPage(), problem };
  }
}
