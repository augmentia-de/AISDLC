import { DELIVERY_STATUS_FILTER } from './statusPresentation';
import type { DeliveryFilter } from './deliveryQueries';

/**
 * Die Filterleiste des Zustellprotokolls (AC-020-1, AC-015-4).
 *
 * <strong>Die Filter sind kombinierbar</strong> — der Vertrag nennt
 * `endpoint_id`, `event_id`, `event_type`, `status`, `from` und `to` als
 * Parameter einer Operation; die Leiste bietet Endpunkt, Status und Zeitraum
 * an. <strong>Die Suche laeuft ausschliesslich ueber Metadaten</strong>
 * (D-11): es gibt kein Feld fuer den Inhalt der Nutzlast.
 *
 * <strong>Das Formular ist ein GET-Formular</strong> (ADR-018 §8): die
 * Filter stehen in der URL der Seite, und das Formular wird auch ohne
 * JavaScript abgeschickt. Die Werte der Filter sind **Vertragswerte** — nur
 * die Beschriftung der Liste ist uebersetzt.
 *
 * <strong>Der Zeitraumfilter</strong> bezieht sich im Zustellprotokoll auf den
 * <em>Anlegezeitpunkt</em> des Zustelllaufs (`created_at`) — denselben
 * Schluessel wie die Sortierung (ADR-017 §4, V-40).
 */

/** Die Eigenschaften der Filterleiste. */
export interface DeliveryFiltersProps {
  /** Der aktuelle Zustand der Filter. */
  filter: DeliveryFilter;
}

/**
 * Zeigt die Filterleiste.
 *
 * @param props die Eigenschaften der Filterleiste
 * @returns die Filterleiste
 */
export function DeliveryFilters({ filter }: DeliveryFiltersProps) {
  return (
    <form
      className="filters"
      method="get"
      action="/deliveries"
      aria-label="Filter des Zustellprotokolls"
    >
      <div className="form__field">
        <label htmlFor="filter-endpoint-id">Endpunkt-ID</label>
        <input
          id="filter-endpoint-id"
          name="endpoint_id"
          type="text"
          defaultValue={filter.endpointId}
        />
      </div>

      <div className="form__field">
        <label htmlFor="filter-status">Status</label>
        <select id="filter-status" name="status" defaultValue={filter.status}>
          <option value="">Alle Zustaende</option>
          {DELIVERY_STATUS_FILTER.map((entry) => (
            <option key={entry.value} value={entry.value}>
              {entry.label}
            </option>
          ))}
        </select>
      </div>

      <div className="form__field">
        <label htmlFor="filter-from">Zeitraum von</label>
        <input id="filter-from" name="from" type="date" defaultValue={filter.from} />
      </div>

      <div className="form__field">
        <label htmlFor="filter-to">Zeitraum bis</label>
        <input id="filter-to" name="to" type="date" defaultValue={filter.to} />
      </div>

      <div className="form__actions">
        <button type="submit" className="button">
          Filter anwenden
        </button>
      </div>
    </form>
  );
}
