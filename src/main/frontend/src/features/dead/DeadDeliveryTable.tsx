import { replayDeliveryAction } from '@/app/actions';
import { DataTable, type TableColumn } from '@/components/DataTable';
import { StatusBadge } from '@/components/StatusBadge';
import { formatResponseStatus, formatTimestamp } from '@/lib/format';

import type { DeadDeliveryRow } from '../dead/deadDeliveryQueries';
import { presentDeliveryStatus } from '../protocol/statusPresentation';
import type { DeliveryStatus } from '@/types/api';

/**
 * Die Tabelle der toten Zustellungen (FR-031, AC-020-2).
 *
 * <strong>Die geforderten Felder</strong>: Ereignis-ID, Endpunkt, Ereignistyp,
 * Zahl der Versuche und der Zeitpunkt der Aufgabe. Die Totenliste enthaelt
 * ausschliesslich aufgegebene Zustaende — endgueltig beendete, aber nicht
 * aufgegebene Zustaende stehen im Zustellprotokoll.
 *
 * <strong>Das erneute Ausloesen ist eine Formularaktion</strong>
 * (ADR-018 §8) und wird **nur** dem Empfaenger-Admin des Endpunkts
 * angeboten (F-03, V-39). Der Dienst prueft das unabhaengig davon.
 */

/** Die Eigenschaften der Totenlistentabelle. */
export interface DeadDeliveryTableProps {
  /** Die toten Zustellungen der Seite. */
  items: DeadDeliveryRow[];
}

/**
 * Zeigt die Tabelle der toten Zustellungen.
 *
 * @param props die Eigenschaften der Totenlistentabelle
 * @returns die Tabelle der toten Zustellungen
 */
export function DeadDeliveryTable({ items }: DeadDeliveryTableProps) {
  const columns: Array<TableColumn<DeadDeliveryRow>> = [
    {
      header: 'Ereignis-ID',
      rowHeader: true,
      render: (delivery) => <code className="identifier">{delivery.eventId}</code>,
    },
    {
      header: 'Endpunkt',
      render: (delivery) => delivery.endpointUrl ?? delivery.endpointId,
    },
    {
      header: 'Ereignistyp',
      render: (delivery) => delivery.eventType,
    },
    {
      header: 'Zustand',
      render: (delivery) => {
        const presentation = presentDeliveryStatus(delivery.status as DeliveryStatus);
        return (
          <StatusBadge
            label={presentation.label}
            tone={presentation.tone}
            mark={presentation.mark}
          />
        );
      },
    },
    {
      header: 'Versuche',
      render: (delivery) => String(delivery.attemptCount),
    },
    {
      header: 'Antwortcode',
      render: (delivery) => formatResponseStatus(delivery.lastResponseStatus),
    },
    {
      header: 'Aufgegeben am',
      render: (delivery) => formatTimestamp(delivery.abandonedAt ?? delivery.updatedAt),
    },
  ];

  if (items.some((delivery) => delivery.canReplay)) {
    columns.push({
      header: 'Aktionen',
      render: (delivery) =>
        delivery.canReplay ? (
          <form action={replayDeliveryAction}>
            <input type="hidden" name="delivery_run_id" value={delivery.deliveryRunId} />
            <button type="submit" className="button">
              Erneut auslösen
            </button>
          </form>
        ) : null,
    });
  }

  return (
    <DataTable
      caption="Tote Zustellungen"
      columns={columns}
      rows={items}
      rowKey={(delivery) => delivery.deliveryRunId}
    />
  );
}
