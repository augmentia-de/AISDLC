import { pauseEndpointAction, resumeEndpointAction, rotateEndpointSecretAction } from '@/app/actions';
import { DataTable, type TableColumn } from '@/components/DataTable';
import { EmptyState } from '@/components/EmptyState';
import { FieldError } from '@/components/FieldError';
import { StatusBadge } from '@/components/StatusBadge';
import type { Endpoint } from '@/types/api';

import { presentEndpointStatus } from './statusPresentation';

/**
 * Die Tabelle der Endpunkte (FR-001, FR-004).
 *
 * <strong>Echte Tabelle</strong> mit Zeilenkopf (ADR-018 §4). Sie zeigt die
 * Ziel-URL <strong>ohne</strong> das Secret, den Zustand, die Ereignistypen
 * und die Secret-Generation — das Secret selbst wird nie ausgegeben
 * (AC-001-4).
 *
 * <strong>Die Aktionen sind Formularaktionen</strong> (ADR-018 §8): jede
 * Schaltfläche liegt in einem `<form>` mit einer Server Action. Ohne
 * JavaScript wird das Formular normal abgeschickt — die Bedienung bleibt
 * vollstaendig moeglich.
 */

/** Die Eigenschaften der Endpunktliste. */
export interface EndpointListProps {
  /** Die Endpunkte des Mandanten. */
  items: Endpoint[];
  /** Der feldbezogene Fehler der Secret-Rotation einer Zeile. */
  rotationError: { endpointId: string; message: string } | null;
}

/**
 * Zeigt die Endpunktliste.
 *
 * @param props die Eigenschaften der Endpunktliste
 * @returns die Endpunktliste
 */
export function EndpointList({ items, rotationError }: EndpointListProps) {
  if (items.length === 0) {
    return (
      <EmptyState
        title="Keine Endpunkte vorhanden."
        hint="Legen Sie oben den ersten Endpunkt an, um Ereignisse zustellen zu lassen."
      />
    );
  }

  const columns: Array<TableColumn<Endpoint>> = [
    {
      header: 'Ziel-URL',
      rowHeader: true,
      render: (endpoint) => endpoint.target_url,
    },
    {
      header: 'Status',
      render: (endpoint) => {
        const presentation = presentEndpointStatus(endpoint.status);
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
      header: 'Ereignistypen',
      render: (endpoint) => (
        <ul className="tag-list">
          {endpoint.event_types.map((eventType) => (
            <li key={eventType}>{eventType}</li>
          ))}
        </ul>
      ),
    },
    {
      header: 'Secret-Version',
      render: (endpoint) => String(endpoint.secret_version),
    },
    {
      header: 'Aktionen',
      render: (endpoint) => (
        <div className="actions">
          {endpoint.status === 'paused' ? (
            <form action={resumeEndpointAction}>
              <input type="hidden" name="endpoint_id" value={endpoint.endpoint_id} />
              <button type="submit" className="button">
                Reaktivieren
              </button>
            </form>
          ) : (
            <form action={pauseEndpointAction}>
              <input type="hidden" name="endpoint_id" value={endpoint.endpoint_id} />
              <button type="submit" className="button">
                Pausieren
              </button>
            </form>
          )}
          <RotationForm endpoint={endpoint} error={rotationError} />
        </div>
      ),
    },
  ];

  return (
    <DataTable
      caption="Ihre Endpunkte"
      columns={columns}
      rows={items}
      rowKey={(endpoint) => endpoint.endpoint_id}
    />
  );
}

/** Die Eigenschaften des Rotationsformulars einer Zeile. */
interface RotationFormProps {
  /** Der Endpunkt, dessen Secret rotiert wird. */
  endpoint: Endpoint;
  /** Der feldbezogene Fehler der Rotation (oder `null`). */
  error: { endpointId: string; message: string } | null;
}

/**
 * Das Formular der Secret-Rotation (FR-003).
 *
 * <strong>Das neue Secret wird nie angezeigt</strong> (AC-019-4): das Feld ist
 * ein Passwortfeld, die Bestaetigung nennt nur die neue Version.
 *
 * @param props die Eigenschaften des Rotationsformulars
 * @returns das Rotationsformular
 */
function RotationForm({ endpoint, error }: RotationFormProps) {
  const fieldId = `endpoint-rotation-secret-${endpoint.endpoint_id}`;
  const errorId = `${fieldId}-error`;
  const fieldError = error !== null && error.endpointId === endpoint.endpoint_id ? error : null;

  return (
    <form className="rotation" action={rotateEndpointSecretAction}>
      <input type="hidden" name="endpoint_id" value={endpoint.endpoint_id} />
      <label htmlFor={fieldId}>Neues Secret</label>
      <input
        id={fieldId}
        name="secret"
        type="password"
        autoComplete="new-password"
        placeholder="mindestens 16 Zeichen"
        aria-invalid={fieldError !== null}
        aria-describedby={fieldError !== null ? errorId : undefined}
      />
      <button type="submit" className="button">
        Secret rotieren
      </button>
      {fieldError !== null ? <FieldError id={errorId} message={fieldError.message} /> : null}
    </form>
  );
}
