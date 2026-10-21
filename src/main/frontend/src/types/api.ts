/**
 * Die Typen der EchoBox-Oberflaeche.
 *
 * <strong>Eine Quelle:</strong> `docs/api/openapi.yaml` — der eingefrorene
 * Vertrag. Jeder Typ nennt im Kommentar das Schema, aus dem er abgeleitet
 * ist. Die Typen stehen <strong>nur hier</strong>; die Komponenten importieren
 * sie von dieser Stelle (keine zweite Kopie, kein "zweites API-Modul").
 *
 * <strong>Abgleich mit dem Vertrag:</strong> `npm run contract:check`
 * (`scripts/check-contract-types.mjs`) liest die Schemas aus dem Vertrag und
 * prueft, dass die Feldnamen dieses Moduls mit dem Vertrag uebereinstimmen.
 * Eine Aenderung im Vertrag, die hier nicht nachgezogen wurde, bricht den
 * Check — und damit die CI.
 *
 * <strong>Schreibweise:</strong> Die Feldnamen bleiben <strong>snake_case</strong>,
 * genau wie im Vertrag und in der JSON-Antwort. Eine Umbenennung in der
 * Oberflaeche wuerde eine stille Abweichung vom Vertrag erzeugen.
 */

/* ------------------------------------------------------------------ Aufzählungen */

/** `components.schemas.EndpointStatus` — der Zustand eines Endpunkts. */
export type EndpointStatus = 'active' | 'paused';

/** `components.schemas.DeliveryStatus` — der Zustand eines Zustelllaufs. */
export type DeliveryStatus =
  | 'pending'
  | 'scheduled'
  | 'delayed'
  | 'sent'
  | 'failed'
  | 'abandoned'
  | 'rejected'
  | 'cancelled'
  | 'no_subscriber';

/** `components.schemas.DeadDeliveryStatus` — immer `abandoned`. */
export type DeadDeliveryStatus = 'abandoned';

/* ------------------------------------------------------------------ Fehlerbild */

/** `components.schemas.ValidationViolation` — der feldbezogene Verstoss. */
export interface ValidationViolation {
  /** Das betroffene Feld, z. B. `target_url`. */
  field: string;
  /** Der maschinenlesbare Verstosscode, z. B. `scheme_not_allowed`. */
  code: string;
  /** Die verstaendliche Meldung fuer den Aufrufer. */
  message: string;
}

/**
 * `components.schemas.Problem` — das einheitliche Fehlerschema.
 *
 * Die Oberflaeche zeigt `detail` und `violations` an; `code` und `status`
 * tragen die Unterscheidung der Fehlerarten (404 = fremde Ressource,
 * 403 = eigene Ressource, falsche Rolle).
 */
export interface Problem {
  /** Die stabile URI der Fehlerklasse. */
  type: string;
  /** Die kurze, nichttechnische Ueberschrift. */
  title: string;
  /** Der HTTP-Statuscode — identisch mit der Response. */
  status: number;
  /** Der maschinenlesbare Fehlercode. */
  code: string;
  /** Was zu tun ist — nicht nur, was schiefging. */
  detail?: string | null;
  /** Der Pfad der Anfrage. */
  instance?: string | null;
  /** Die feldbezogenen Verstoesse (422). */
  violations?: ValidationViolation[] | null;
}

/* ------------------------------------------------------------------- Seitennavigation */

/** `components.schemas.Page` — die cursor-basierte Seitennavigation. */
export interface Page {
  /** Die Zahl der Eintraege je Seite. */
  limit: number;
  /** Ob es eine weitere Seite gibt. */
  has_more: boolean;
  /** Der Cursor der naechsten Seite; `null` bei der letzten Seite. */
  next_cursor: string | null;
}

/* --------------------------------------------------------------------- Endpunkte */

/** `components.schemas.Endpoint` — ein registrierter Empfaenger. */
export interface Endpoint {
  /** Die Endpunkt-ID (UUID). */
  endpoint_id: string;
  /** Der besitzende Mandant — immer gefuellt. */
  organisation_id: string;
  /** Die Ziel-URL des Empfaengers (nie das Secret). */
  target_url: string;
  /** Der Zustand des Endpunkts. */
  status: EndpointStatus;
  /** Die abonnierten Ereignistypen. */
  event_types: string[];
  /** Die Nummer der Secret-Generation (Startwert 1). */
  secret_version: number;
  /** Der Zeitpunkt der letzten Secret-Rotation. */
  secret_rotated_at: string | null;
  /** Der Zeitpunkt der Pause. */
  paused_at: string | null;
  /** Der Anlegezeitpunkt. */
  created_at: string;
  /** Der Zeitpunkt der letzten Aenderung. */
  updated_at: string;
}

/** `components.schemas.EndpointList` — eine Seite der Endpunktliste. */
export interface EndpointList {
  /** Die Endpunkte der Seite. */
  items: Endpoint[];
  /** Die Seitennavigation. */
  page: Page;
}

/** `components.schemas.SecretRotationResponse` — der Stand der Rotation. */
export interface SecretRotationResponse {
  /** Die Endpunkt-ID. */
  endpoint_id: string;
  /** Die neue Secret-Generation. */
  secret_version: number;
  /** Der Zeitpunkt der Rotation. */
  rotated_at: string;
  /** Die Identitaet der ausloesenden Rolle (Audit). */
  rotated_by: string;
}

/* --------------------------------------------------------------------- Zustellung */

/** `components.schemas.DeliveryRun` — ein Zustelllauf. */
export interface DeliveryRun {
  /** Die Zustelllauf-ID (UUID). */
  delivery_run_id: string;
  /** Die Zustelllauf-ID des Vorgaengers, wenn dieser Lauf ein Replay ist. */
  replay_of?: string | null;
  /** Die Ereignis-ID (UUID). */
  event_id: string;
  /** Die Endpunkt-ID; `null` bei `no_subscriber`. */
  endpoint_id?: string | null;
  /** Der besitzende Mandant — immer gefuellt. */
  organisation_id: string;
  /** Der Ereignistyp. */
  event_type: string;
  /** Der Zustand des Laufs. */
  status: DeliveryStatus;
  /** Die Zahl der bereits ausgefuehrten Versuche. */
  attempt_count: number;
  /** Die konfigurierte Versuchsgrenze; `null`, wenn keine gesetzt ist. */
  max_attempts?: number | null;
  /** Der Zeitpunkt des letzten Versuchs. */
  last_attempt_at?: string | null;
  /** Der naechste Versuchszeitpunkt; leer bei endgueltigen Zustaenden. */
  next_attempt_at?: string | null;
  /** Der HTTP-Statuscode der Antwort des Empfaengers im letzten Versuch. */
  last_response_status?: number | null;
  /** Der Fehlercode des letzten Versuchs. */
  last_error_code?: string | null;
  /** Der Grund einer endgueltigen Ablehnung. */
  rejection_reason?: string | null;
  /** Der Anlegezeitpunkt des Laufs. */
  created_at: string;
  /** Der Zeitpunkt der letzten Aenderung. */
  updated_at: string;
}

/** `components.schemas.DeliveryList` — eine Seite des Zustellprotokolls. */
export interface DeliveryList {
  /** Die Zustaende der Seite. */
  items: DeliveryRun[];
  /** Die Seitennavigation. */
  page: Page;
}

/** `components.schemas.DeadDelivery` — eine aufgegebene Zustellung. */
export interface DeadDelivery {
  /** Die Zustelllauf-ID (UUID). */
  delivery_run_id: string;
  /** Die Ereignis-ID (UUID). */
  event_id: string;
  /** Der besitzende Mandant — immer gefuellt. */
  organisation_id: string;
  /** Die Endpunkt-ID (UUID). */
  endpoint_id: string;
  /** Die Ziel-URL des Endpunkts. */
  endpoint_url?: string | null;
  /** Der Ereignistyp. */
  event_type: string;
  /** Der Zustand — immer `abandoned`. */
  status: DeadDeliveryStatus;
  /** Die Zahl der ausgefuehrten Versuche. */
  attempt_count: number;
  /** Der HTTP-Statuscode der Antwort des Empfaengers im letzten Versuch. */
  last_response_status?: number | null;
  /** Der Zeitpunkt des letzten Versuchs. */
  last_attempt_at?: string | null;
  /** Der Zeitpunkt der Aufgabe — immer gefuellt. */
  abandoned_at: string;
  /** Der Anlegezeitpunkt des Laufs. */
  created_at?: string;
}

/** `components.schemas.DeadDeliveryList` — eine Seite der Totenliste. */
export interface DeadDeliveryList {
  /** Die toten Zustellungen der Seite. */
  items: DeadDelivery[];
  /** Die Seitennavigation. */
  page: Page;
}

/** `components.schemas.ReplayResponse` — der neu angelegte Zustelllauf. */
export interface ReplayResponse {
  /** Der aufgegebene Zustelllauf — bleibt unveraendert erhalten. */
  previous_delivery_run_id: string;
  /** Der neue Zustelllauf mit neuer ID. */
  delivery_run_id: string;
  /** Die Ereignis-ID — dieselbe wie beim Vorgänger. */
  event_id: string;
  /** Der Zustand des neuen Laufs. */
  status: DeliveryStatus;
  /** Die Zahl der Versuche — beginnt wieder bei 0. */
  attempt_count: number;
  /** Der naechste Versuchszeitpunkt. */
  next_attempt_at?: string | null;
  /** Der Zeitpunkt des Replays. */
  replayed_at?: string | null;
}
