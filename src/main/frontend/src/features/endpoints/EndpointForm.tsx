import { createEndpointAction } from '@/app/actions';
import { FieldError } from '@/components/FieldError';

/**
 * Das Registrierungsformular der Endpunkte (FR-001).
 *
 * <strong>Ein Server Component mit einer Formularaktion</strong>
 * (ADR-018 §8): das Formular wird **ohne JavaScript** abgeschickt, der
 * Server legt den Endpunkt an und leitet mit dem Ergebnis zurueck. Es gibt
 * **keinen** Klickhandler und **keinen** Zustand im Browser.
 *
 * <strong>Die Pruefung passiert vor dem Senden</strong> (AC-019-2): eine
 * ungueltige Ziel-URL erzeugt eine verstaendliche Meldung <strong>am
 * Feld</strong> — und es wird kein Endpunkt angelegt. Der Dienst prueft
 * trotzdem; die Pruefung hier ersetzt die Pruefung dort nicht.
 *
 * <strong>Kein Mandantenparameter</strong> (D-01): der Request enthaelt genau
 * `target_url`, `secret` und `event_types`.
 */

/** Die Eigenschaften des Formulars. */
export interface EndpointFormProps {
  /** Der feldbezogene Fehler — `null`, wenn das Formular korrekt ist. */
  fieldError: { name: string; message: string } | null;
}

/**
 * Zeigt das Registrierungsformular.
 *
 * @param props die Eigenschaften des Formulars
 * @returns das Formular
 */
export function EndpointForm({ fieldError }: EndpointFormProps) {
  const targetUrlError = fieldError?.name === 'target_url' ? fieldError : null;
  const secretError = fieldError?.name === 'secret' ? fieldError : null;
  const eventTypesError = fieldError?.name === 'event_types' ? fieldError : null;

  return (
    <section className="card" aria-labelledby="endpoints-form-heading">
      <h2 id="endpoints-form-heading">Endpunkt anlegen</h2>

      <form className="form" action={createEndpointAction}>
        <div className="form__field">
          <label htmlFor="endpoint-target-url">Ziel-URL</label>
          <input
            id="endpoint-target-url"
            name="target_url"
            type="text"
            inputMode="url"
            autoComplete="url"
            placeholder="https://empfaenger.example.invalid/hooks/echobox"
            aria-invalid={targetUrlError !== null}
            aria-describedby={targetUrlError !== null ? 'endpoint-target-url-error' : undefined}
          />
          {targetUrlError !== null ? (
            <FieldError id="endpoint-target-url-error" message={targetUrlError.message} />
          ) : null}
        </div>

        <div className="form__field">
          <label htmlFor="endpoint-secret">Secret</label>
          <input
            id="endpoint-secret"
            name="secret"
            type="password"
            autoComplete="new-password"
            placeholder="mindestens 16 Zeichen"
            aria-invalid={secretError !== null}
            aria-describedby={secretError !== null ? 'endpoint-secret-error' : undefined}
          />
          {secretError !== null ? (
            <FieldError id="endpoint-secret-error" message={secretError.message} />
          ) : null}
        </div>

        <div className="form__field">
          <label htmlFor="endpoint-event-types">Ereignistypen</label>
          <input
            id="endpoint-event-types"
            name="event_types"
            type="text"
            placeholder="kommagetrennt, z. B. typ1, typ2"
            aria-invalid={eventTypesError !== null}
            aria-describedby={eventTypesError !== null ? 'endpoint-event-types-error' : undefined}
          />
          {eventTypesError !== null ? (
            <FieldError id="endpoint-event-types-error" message={eventTypesError.message} />
          ) : null}
        </div>

        <div className="form__actions">
          <button type="submit" className="button button--primary">
            Endpunkt registrieren
          </button>
        </div>
      </form>
    </section>
  );
}
