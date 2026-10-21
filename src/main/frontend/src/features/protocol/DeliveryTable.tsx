import { DataTable, type TableColumn } from '@/components/DataTable';
import { StatusBadge } from '@/components/StatusBadge';
import { formatAttempts, formatResponseStatus, formatTimestamp } from '@/lib/format';
import type { DeliveryRun } from '@/types/api';

import { presentDeliveryStatus } from './statusPresentation';

/**
 * Die Tabelle des Zustellprotokolls (FR-030, AC-020-1).
 *
 * <strong>Die geforderten Felder</strong>: Zustand, Anzahl der Versuche,
 * Zeitpunkt des letzten Versuchs, Antwortcode des Empfaengers und
 * naechster Versuchszeitpunkt. Bei endgueltigen Zustaenden ist der
 * naechste Versuchszeitpunkt <strong>leer</strong> — es wird kein Zeitpunkt
 * in der Vergangenheit oder in der Zukunft angezeigt (AC-015-2).
 *
 * <strong>Kein `total`</strong>: die Seitennavigation des Vertrags kennt nur
 * `has_more` und `next_cursor`.
 */

/** Die Eigenschaften der Protokolltabelle. */
export interface DeliveryTableProps {
  /** Die Zustaende der Seite. */
  items: DeliveryRun[];
}

/**
 * Zeigt die Tabelle des Zustellprotokolls.
 *
 * @param props die Eigenschaften der Protokolltabelle
 * @returns die Tabelle des Zustellprotokolls
 */
export function DeliveryTable({ items }: DeliveryTableProps) {
  const columns: Array<TableColumn<DeliveryRun>> = [
    {
      header: 'Ereignistyp',
      rowHeader: true,
      render: (delivery) => delivery.event_type,
    },
    {
      header: 'Zustand',
      render: (delivery) => {
        const presentation = presentDeliveryStatus(delivery.status);
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
      render: (delivery) => formatAttempts(delivery.attempt_count, delivery.max_attempts),
    },
    {
      header: 'Letzter Versuch',
      render: (delivery) => formatTimestamp(delivery.last_attempt_at),
    },
    {
      header: 'Antwortcode',
      render: (delivery) => formatResponseStatus(delivery.last_response_status),
    },
    {
      header: 'Naechster Versuch',
      render: (delivery) => formatTimestamp(delivery.next_attempt_at),
    },
  ];

  return (
    <DataTable
      caption="Zustellprotokoll"
      columns={columns}
      rows={items}
      rowKey={(delivery) => delivery.delivery_run_id}
    />
  );
}
