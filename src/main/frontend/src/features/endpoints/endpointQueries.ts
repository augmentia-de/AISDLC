import { api } from '@/lib/apiClient';
import { emptyPage, PAGE_LIMIT, toProblem, type ListResult } from '@/lib/listResult';
import type { Endpoint, EndpointList } from '@/types/api';

/**
 * Die Abfrage der Endpunktliste.
 *
 * <strong>Die Liste ist mandantengefiltert</strong> (FR-004): ein
 * `receiver_admin` sieht nur die Endpunkte seines Mandanten, ein
 * `service_admin` die Endpunkte aller Mandanten. Der Mandant wird **nicht**
 * angefragt — er steckt im Token (D-01).
 */

/**
 * Liest die Endpunktliste.
 *
 * @param token das Bearer-Token der Sitzung
 * @returns die Endpunkte und das Fehlerbild der Abfrage
 */
export async function loadEndpointList(token: string | null): Promise<ListResult<Endpoint>> {
  try {
    const list = await api.get<EndpointList>('/endpoints', token, { limit: PAGE_LIMIT });
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
