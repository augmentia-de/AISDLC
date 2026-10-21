import { api } from '@/lib/apiClient';
import { emptyPage, PAGE_LIMIT, toProblem } from '@/lib/listResult';
import { ROLE_RECEIVER_ADMIN, ROLE_SERVICE_ADMIN, hasRole, type Session } from '@/lib/session';
import type {
  DeadDelivery,
  DeadDeliveryList,
  DeliveryList,
  DeliveryRun,
  Page,
  Problem,
} from '@/types/api';

/**
 * Die Abfrage der toten Zustellungen.
 *
 * <strong>Die Totenliste ist mandantenuebergreifend und nur der
 * Service-Administration vorbehalten</strong> (E-01); der `receiver_admin`
 * erhaelt dort HTTP 403. Der **Empfaenger-Admin** liest die aufgegebenen
 * Zustellungen deshalb ueber das **Zustellprotokoll** mit dem Filter
 * `status=abandoned` — das ist der einzige Leseweg, den der Vertrag ihm
 * zugesteht (FR-024, F-03, V-39).
 *
 * <strong>Das erneute Ausloesen** ist ausschliesslich dem Empfaenger-Admin
 * **des Endpunkts** erlaubt: die Rolle muss im Token stehen **und** der
 * Zustelllauf muss dem Mandanten der Identitaet gehoeren. Der Dienst prueft
 * dasselbe — die Oberflaeche bietet die Aktion nur an.
 */

/** Die Herkunft der toten Zustellungen. */
export type DeadDeliverySource = 'totenliste' | 'protokoll';

/** Eine Zeile der Seite der toten Zustellungen. */
export interface DeadDeliveryRow {
  /** Die Zustelllauf-ID (UUID). */
  deliveryRunId: string;
  /** Die Ereignis-ID (UUID). */
  eventId: string;
  /** Der besitzende Mandant — immer gefuellt. */
  organisationId: string;
  /** Die Endpunkt-ID (UUID). */
  endpointId: string;
  /** Die Ziel-URL des Endpunkts — nur aus der Totenliste bekannt. */
  endpointUrl: string | null;
  /** Der Ereignistyp. */
  eventType: string;
  /** Der Zustand — `abandoned`. */
  status: string;
  /** Die Zahl der ausgefuehrten Versuche. */
  attemptCount: number;
  /** Der HTTP-Statuscode der Antwort des Empfaengers im letzten Versuch. */
  lastResponseStatus: number | null;
  /** Der Zeitpunkt des letzten Versuchs. */
  lastAttemptAt: string | null;
  /** Der Zeitpunkt der Aufgabe — nur aus der Totenliste bekannt. */
  abandonedAt: string | null;
  /** Der Zeitpunkt der letzten Aenderung — der Ersatz fuer den Aufgabezeitpunkt. */
  updatedAt: string | null;
  /** Ob die Identitaet diesen Vorgang erneut ausloesen darf (F-03). */
  canReplay: boolean;
}

/** Das Ergebnis der Abfrage der toten Zustellungen. */
export interface DeadDeliveryResult {
  /** Die Zeilen der Seite. */
  items: DeadDeliveryRow[];
  /** Die Seitennavigation der Antwort. */
  page: Page;
  /** Das Fehlerbild der Abfrage — `null`, wenn es keines gibt. */
  problem: Problem | null;
  /** Die Herkunft der Liste. */
  source: DeadDeliverySource;
}

/**
 * Prueft, ob die Identitaet den Vorgang erneut ausloesen darf.
 *
 * <strong>F-03 und V-39:</strong> nur der Empfaenger-Admin **des Endpunkts**
 * darf einen aufgegebenen Vorgang erneut ausloesen. Das ist die Identitaet
 * mit der Rolle `receiver_admin` **und** der Mandant des Zustelllaufs.
 *
 * @param session die Sitzung
 * @param organisationId der Mandant des Zustelllaufs
 * @returns true, wenn das erneute Ausloesen angeboten werden darf
 */
export function mayReplay(session: Session, organisationId: string): boolean {
  if (!hasRole(session, ROLE_RECEIVER_ADMIN)) {
    return false;
  }
  return session.organisationId !== null && session.organisationId === organisationId;
}

/**
 * Bildet einen Eintrag der Totenliste auf eine Zeile ab.
 *
 * @param delivery der Eintrag des Vertrags
 * @param session die Sitzung
 * @returns die Zeile der Seite
 */
function rowFromDeadDelivery(delivery: DeadDelivery, session: Session): DeadDeliveryRow {
  return {
    deliveryRunId: delivery.delivery_run_id,
    eventId: delivery.event_id,
    organisationId: delivery.organisation_id,
    endpointId: delivery.endpoint_id,
    endpointUrl: delivery.endpoint_url ?? null,
    eventType: delivery.event_type,
    status: delivery.status,
    attemptCount: delivery.attempt_count,
    lastResponseStatus: delivery.last_response_status ?? null,
    lastAttemptAt: delivery.last_attempt_at ?? null,
    abandonedAt: delivery.abandoned_at,
    updatedAt: null,
    canReplay: mayReplay(session, delivery.organisation_id),
  };
}

/**
 * Bildet einen Eintrag des Zustellprotokolls auf eine Zeile ab.
 *
 * <strong>Der Zeitpunkt der Aufgabe</strong> steht nur in der Totenliste. Aus
 * dem Protokoll kommt stattdessen der Zeitpunkt der letzten Aenderung — der
 * Zeitpunkt, zu dem der Lauf aufgegeben wurde.
 *
 * @param delivery der Eintrag des Vertrags
 * @param session die Sitzung
 * @returns die Zeile der Seite
 */
function rowFromDeliveryRun(delivery: DeliveryRun, session: Session): DeadDeliveryRow {
  return {
    deliveryRunId: delivery.delivery_run_id,
    eventId: delivery.event_id,
    organisationId: delivery.organisation_id,
    endpointId: delivery.endpoint_id ?? '',
    endpointUrl: null,
    eventType: delivery.event_type,
    status: delivery.status,
    attemptCount: delivery.attempt_count,
    lastResponseStatus: delivery.last_response_status ?? null,
    lastAttemptAt: delivery.last_attempt_at ?? null,
    abandonedAt: null,
    updatedAt: delivery.updated_at,
    canReplay: mayReplay(session, delivery.organisation_id),
  };
}

/**
 * Liest die toten Zustellungen — mit der Operation, die die Identitaet lesen
 * darf.
 *
 * @param token das Bearer-Token der Sitzung
 * @param session die Sitzung
 * @param cursor der Cursor der Seite (`null` = die erste Seite)
 * @returns die Zeilen der Seite und das Fehlerbild der Abfrage
 */
export async function loadDeadDeliveries(
  token: string | null,
  session: Session,
  cursor: string | null,
): Promise<DeadDeliveryResult> {
  const canReadDeadList = session.roles.includes(ROLE_SERVICE_ADMIN);

  if (canReadDeadList) {
    try {
      const list = await api.get<DeadDeliveryList>('/dead-deliveries', token, {
        limit: PAGE_LIMIT,
        cursor: cursor ?? undefined,
      });
      return {
        items: list.items.map((delivery) => rowFromDeadDelivery(delivery, session)),
        page: list.page,
        problem: null,
        source: 'totenliste',
      };
    } catch (error) {
      const problem = toProblem(error);
      if (problem.status !== 403) {
        if (problem.status === 404) {
          return { items: [], page: emptyPage(), problem: null, source: 'totenliste' };
        }
        return { items: [], page: emptyPage(), problem, source: 'totenliste' };
      }
      // 403: der Dienst kennt die Rolle nicht — der Weg ueber das
      // Zustellprotokoll bleibt (FR-024).
    }
  }

  try {
    const list = await api.get<DeliveryList>('/deliveries', token, {
      status: 'abandoned',
      limit: PAGE_LIMIT,
      cursor: cursor ?? undefined,
    });
    return {
      items: list.items.map((delivery) => rowFromDeliveryRun(delivery, session)),
      page: list.page,
      problem: null,
      source: 'protokoll',
    };
  } catch (error) {
    const problem = toProblem(error);
    if (problem.status === 404) {
      return { items: [], page: emptyPage(), problem: null, source: 'protokoll' };
    }
    return { items: [], page: emptyPage(), problem, source: 'protokoll' };
  }
}
