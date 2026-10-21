/**
 * Die Rueckmeldung einer Aktion.
 *
 * <strong>Warum ein Code und kein Text in der URL?</strong> Die Aktionen
 * laufen als **Server Actions** (ADR-018 §8). Nach der Aktion leitet die
 * Seite auf eine URL mit dem Ergebnis zurueck — auch dann, wenn der Browser
 * kein JavaScript hat. In dieser URL steht **nur ein Code aus einer festen
 * Liste**; der Text wird daraus server-seitig bestimmt. Damit kann kein
 * fremder Inhalt in die Seite zurueckgespiegelt werden.
 *
 * <strong>Die Meldungen stehen am Feld, nicht nur am Seitenkopf</strong>
 * (ADR-018 §7): ein feldbezogener Fehler wird mit `aria-describedby` am Feld
 * verknuepft.
 */

/** Die Meldung einer Aktion — genau eine Quelle fuer die Anzeige. */
export interface Feedback {
  /** Die Bestaetigung der Aktion (`role="status"`). */
  notice: string | null;
  /** Die Fehlermeldung der Aktion (`role="alert"`). */
  error: string | null;
  /** Der feldbezogene Fehler des Registrierungsformulars. */
  field: { name: string; message: string } | null;
  /** Der feldbezogene Fehler der Secret-Rotation einer Zeile. */
  rotationField: { endpointId: string; message: string } | null;
}

/** Eine leere Rueckmeldung — kein Ergebnis, kein Fehler. */
export const NO_FEEDBACK: Feedback = {
  notice: null,
  error: null,
  field: null,
  rotationField: null,
};

/** Die Meldung fuer ein falsches Schema — wortgleich mit dem Vertrag. */
const URL_SCHEME = 'Die Ziel-URL muss das Schema https verwenden.';

/** Die Meldung fuer eingebettete Zugangsdaten. */
const URL_CREDENTIALS = 'Die Ziel-URL darf keine eingebetteten Zugangsdaten enthalten.';

/** Die Meldung fuer eine zu lange Ziel-URL. */
const URL_TOO_LONG = 'Die Ziel-URL darf hoechstens 2000 Zeichen lang sein.';

/** Die Meldung fuer eine leere Ziel-URL. */
const URL_REQUIRED = 'Die Ziel-URL darf nicht leer sein.';

/** Die Meldung fuer eine syntaktisch unbrauchbare Ziel-URL. */
const URL_INVALID = 'Die Ziel-URL ist keine gueltige URL.';

/** Die Meldung fuer ein zu kurzes Secret. */
const SECRET_TOO_SHORT = 'Das Secret muss mindestens 16 Zeichen lang sein.';

/** Die Meldung fuer fehlende Ereignistypen. */
const EVENT_TYPES_REQUIRED = 'Mindestens ein Ereignistyp muss angegeben werden.';

/** Die Ergebnisse der Aktionen — der Code steht in der URL. */
const MELDUNGEN: Record<string, Feedback> = {
  angelegt: { notice: 'Der Endpunkt wurde angelegt.', error: null, field: null, rotationField: null },
  pausiert: {
    notice: 'Der Endpunkt nimmt bis zur Wiederaufnahme keine Zustellungen an.',
    error: null,
    field: null,
    rotationField: null,
  },
  reaktiviert: {
    notice: 'Der Endpunkt nimmt wieder Zustellungen an.',
    error: null,
    field: null,
    rotationField: null,
  },
  rotiert: {
    notice: 'Die Secret-Rotation wurde bestaetigt.',
    error: null,
    field: null,
    rotationField: null,
  },
  replayed: {
    notice: 'Der Zustellvorgang wurde erneut ausgelöst.',
    error: null,
    field: null,
    rotationField: null,
  },
  'url-fehlt': {
    notice: null,
    error: null,
    field: { name: 'target_url', message: URL_REQUIRED },
    rotationField: null,
  },
  'url-ungueltig': {
    notice: null,
    error: null,
    field: { name: 'target_url', message: URL_SCHEME },
    rotationField: null,
  },
  'url-zugangsdaten': {
    notice: null,
    error: null,
    field: { name: 'target_url', message: URL_CREDENTIALS },
    rotationField: null,
  },
  'url-zu-lang': {
    notice: null,
    error: null,
    field: { name: 'target_url', message: URL_TOO_LONG },
    rotationField: null,
  },
  'url-fehlerhaft': {
    notice: null,
    error: null,
    field: { name: 'target_url', message: URL_INVALID },
    rotationField: null,
  },
  'secret-zu-kurz': {
    notice: null,
    error: null,
    field: { name: 'secret', message: SECRET_TOO_SHORT },
    rotationField: null,
  },
  'typen-fehlen': {
    notice: null,
    error: null,
    field: { name: 'event_types', message: EVENT_TYPES_REQUIRED },
    rotationField: null,
  },
  'rotation-secret-zu-kurz': {
    notice: null,
    error: null,
    field: null,
    rotationField: { endpointId: '', message: SECRET_TOO_SHORT },
  },
  verboten: {
    notice: null,
    error: 'Diese Ansicht ist fuer Ihre Rolle nicht freigegeben.',
    field: null,
    rotationField: null,
  },
  'nicht-gefunden': {
    notice: null,
    error: 'Die angeforderten Daten wurden nicht gefunden.',
    field: null,
    rotationField: null,
  },
  konflikt: {
    notice: null,
    error: 'Der Vorgang laesst sich in diesem Zustand nicht mehr ausloesen.',
    field: null,
    rotationField: null,
  },
  unautorisiert: {
    notice: null,
    error: 'Die Anmeldung fehlt oder ist abgelaufen. Bitte melden Sie sich erneut an.',
    field: null,
    rotationField: null,
  },
  'dienst-fehler': {
    notice: null,
    error: 'Der Dienst ist gerade nicht erreichbar. Bitte versuchen Sie es spaeter erneut.',
    field: null,
    rotationField: null,
  },
  'kein-token': {
    notice: null,
    error: 'Bitte geben Sie ein Token ein.',
    field: null,
    rotationField: null,
  },
};

/**
 * Bestimmt die Rueckmeldung aus dem Ergebniscode der URL.
 *
 * @param code der Code aus der Query-Datei (`ergebnis`)
 * @param endpointId der Endpunkt, zu dem die Rueckmeldung gehoert (optional)
 * @returns die Rueckmeldung — leer, wenn der Code unbekannt ist
 */
export function feedbackFor(code: string | null | undefined, endpointId?: string): Feedback {
  if (code === null || code === undefined || code.length === 0) {
    return NO_FEEDBACK;
  }

  const feedback = MELDUNGEN[code];
  if (feedback === undefined) {
    return NO_FEEDBACK;
  }

  if (feedback.rotationField !== null && endpointId !== undefined) {
    return { ...feedback, rotationField: { endpointId, message: feedback.rotationField.message } };
  }

  return feedback;
}
