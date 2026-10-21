import type { Problem, ValidationViolation } from '@/types/api';

/**
 * Der HTTP-Client der Oberflaeche — **serverseitig**.
 *
 * <strong>Warum server-seitig?</strong> Die Seiten sind Server Components
 * (ADR-018 §8): die Daten werden beim Rendern auf dem Server geholt, und die
 * Aktionen laufen als Server Actions. Der Browser spricht den Dienst **nie**
 * direkt an — damit braucht es **keine** CORS-Regel (O-15) und kein Token im
 * Browser-Speicher.
 *
 * <strong>Der Client spricht ausschliesslich die im Vertrag definierten
 * Operationen</strong> (`docs/api/openapi.yaml`). Es gibt keinen frei
 * waehlbaren Mandantenparameter: der Mandant steckt im Token (D-01).
 *
 * <strong>Fehlerbild:</strong> Jede Antwort, die kein 2xx ist, wird in eine
 * {@link Problem} uebersetzt und als {@link ApiError} geworfen. Die Seite
 * entscheidet danach, was sie zeigt — 404 bedeutet „fremde Ressource“ und wird
 * als Leerzustand behandelt (ADR-018 §6), 403 bedeutet „eigene Ressource,
 * falsche Rolle“.
 */

/** Der Praefix aller Operationen des Vertrags. */
export const API_PREFIX = '/api/v1';

/** Ein Fehler der API — mit dem Fehlerbild des Vertrags. */
export class ApiError extends Error {
  /** Der HTTP-Statuscode der Antwort. */
  readonly status: number;

  /** Der maschinenlesbare Fehlercode des Vertrags. */
  readonly code: string;

  /** Die verstaendliche Meldung (`detail`) — `null`, wenn der Vertrag keine nennt. */
  readonly detail: string | null;

  /** Die feldbezogenen Verstoesse (`violations`). */
  readonly violations: ValidationViolation[];

  /**
   * Erzeugt den Fehler aus dem Fehlerbild des Vertrags.
   *
   * @param problem das Fehlerbild der Antwort
   */
  constructor(problem: Problem) {
    super(problem.title);
    this.name = 'ApiError';
    this.status = problem.status;
    this.code = problem.code;
    this.detail = problem.detail ?? null;
    this.violations = problem.violations ?? [];
  }
}

/** Der Wert eines Query-Parameters; `undefined` und `null` entfallen. */
export type QueryValue = string | number | undefined | null;

/** Die Query-Parameter einer Operation — je Operation eine feste Auswahl. */
export type QueryParameters = Readonly<Record<string, QueryValue>>;

/**
 * Die Adresse des Dienstes.
 *
 * <strong>Kein fest verdrahteter Mandant, keine fest verdrahtete
 * Mandantenadresse:</strong> die Adresse kommt aus der Umgebung. Sie wird nur
 * **serverseitig** benutzt — der Browser kennt sie nicht.
 *
 * @returns die Basisadresse des Dienstes ohne abschliessenden Schraegstrich
 */
export function apiBaseUrl(): string {
  const configured = process.env.ECHOBOX_API_BASE_URL ?? 'http://localhost:8080';
  return configured.replace(/\/+$/, '');
}

/**
 * Baut die URL einer Operation.
 *
 * @param path der Pfad hinter dem Praefix, z. B. `/endpoints`
 * @param query die Query-Parameter (optional)
 * @return die vollstaendige URL
 */
export function buildUrl(path: string, query?: QueryParameters): string {
  const url = `${API_PREFIX}${path}`;

  if (!query) {
    return url;
  }

  const params = new URLSearchParams();
  for (const [name, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    params.set(name, String(value));
  }

  const queryString = params.toString();
  return queryString.length > 0 ? `${url}?${queryString}` : url;
}

/**
 * Prueft, ob ein Antwortkoerper das Fehlerbild des Vertrags traegt.
 *
 * @param body der geparste Antwortkoerper
 * @return true, wenn der Koerper ein `Problem` ist
 */
function isProblem(body: unknown): body is Problem {
  return (
    typeof body === 'object' &&
    body !== null &&
    'code' in body &&
    'status' in body &&
    'title' in body &&
    'type' in body
  );
}

/**
 * Liest das Fehlerbild aus einer Fehlerantwort.
 *
 * @param response die Antwort
 * @return das Fehlerbild — nie `undefined`
 */
async function readProblem(response: Response): Promise<Problem> {
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    body = undefined;
  }

  if (isProblem(body)) {
    return body;
  }

  return {
    type: 'about:blank',
    title: response.statusText.length > 0 ? response.statusText : 'Fehler',
    status: response.status,
    code: 'internal_error',
    detail: null,
    violations: null,
  };
}

/** Die Anfrage-Optionen des Clients. */
export interface RequestOptions {
  /** Der HTTP-Methode. */
  method: 'GET' | 'POST' | 'PATCH';
  /** Die Query-Parameter. */
  query?: QueryParameters;
  /** Der Request-Body (wird als JSON serialisiert). */
  body?: unknown;
  /** Das Bearer-Token der Sitzung. */
  token: string | null;
}

/**
 * Fuehrt eine Anfrage gegen die API aus.
 *
 * <strong>`cache: 'no-store'`</strong>: die Seite zeigt den **aktuellen**
 * Zustand. Ohne diese Angabe wuerde Next.js die Antwort zwischenspeichern —
 * ein Pausieren waere dann erst nach dem Neuladen sichtbar.
 *
 * @param path der Pfad hinter dem Praefix, z. B. `/endpoints`
 * @param options die Optionen der Anfrage
 * @return die Antwort als JSON
 * @throws ApiError bei jeder Antwort, die kein 2xx ist
 */
export async function request<T>(path: string, options: RequestOptions): Promise<T> {
  const url = `${apiBaseUrl()}${buildUrl(path, options.query)}`;
  const headers: Record<string, string> = { Accept: 'application/json' };

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  if (options.token !== null && options.token.length > 0) {
    headers.Authorization = `Bearer ${options.token}`;
  }

  const init: RequestInit = { method: options.method, headers, cache: 'no-store' };

  if (options.body !== undefined) {
    init.body = JSON.stringify(options.body);
  }

  let response: Response;
  try {
    response = await fetch(url, init);
  } catch {
    throw new ApiError({
      type: 'about:blank',
      title: 'Fehler',
      status: 0,
      code: 'internal_error',
      detail: 'Die Verbindung zum Dienst ist fehlgeschlagen.',
      violations: null,
    });
  }

  if (!response.ok) {
    throw new ApiError(await readProblem(response));
  }

  return (await response.json()) as T;
}

/** Die Operationen, die die Oberflaeche braucht — alle aus dem Vertrag. */
export const api = {
  /**
   * Liest eine Ressource.
   *
   * @param path der Pfad hinter dem Praefix
   * @param token das Bearer-Token der Sitzung
   * @param query die Query-Parameter
   * @returns die Antwort
   */
  get<T>(path: string, token: string | null, query?: QueryParameters): Promise<T> {
    return request<T>(path, { method: 'GET', query, token });
  },

  /**
   * Sendet einen Zustandswechsel.
   *
   * @param path der Pfad hinter dem Praefix
   * @param token das Bearer-Token der Sitzung
   * @param body der Request-Body (optional)
   * @returns die Antwort
   */
  post<T>(path: string, token: string | null, body?: unknown): Promise<T> {
    return request<T>(path, { method: 'POST', body, token });
  },
};
