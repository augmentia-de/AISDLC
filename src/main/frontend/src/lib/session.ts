import { cookies } from 'next/headers';

/**
 * Die Sitzung der Oberflaeche.
 *
 * <strong>Warum ein Cookie und nicht der Browser-Speicher?</strong> Die
 * Seiten sind **Server Components** (ADR-018 §8): der Dienst wird
 * server-seitig angesprochen. Das Bearer-Token muss deshalb **serverseitig**
 * vorliegen — ein Token im `localStorage` ist fuer den Server nicht
 * erreichbar. Das Token liegt in einem **httpOnly-Cookie**: der Browser
 * kann es nicht auslesen, aber der Server kann es mitgeben. Ein
 * `localStorage`-Token waere zudem ueber ein XSS-Skript auslesbar.
 *
 * <strong>Der Mandant kommt nie aus dem Request</strong> (D-01), sondern aus
 * dem Token (`organisation_id`). Die Rollen werden **nur fuer die Anzeige**
 * gelesen — die Durchsetzung bleibt beim Dienst (ADR-015 §3).
 */

/** Der Name des Cookies mit dem Bearer-Token. */
export const TOKEN_COOKIE = 'echobox.token';

/** Die Rolle des Empfaenger-Admins (D-15, F-03). */
export const ROLE_RECEIVER_ADMIN = 'receiver_admin';

/** Die Rolle der Service-Administration (E-01). */
export const ROLE_SERVICE_ADMIN = 'service_admin';

/** Die Identitaet der Sitzung. */
export interface Session {
  /** Das Bearer-Token — `null`, wenn niemand angemeldet ist. */
  token: string | null;
  /** Die Rollen des Tokens — nur fuer die Anzeige. */
  roles: string[];
  /** Der Mandant des Tokens (`organisation_id`) — `null`, wenn unbekannt. */
  organisationId: string | null;
}

/**
 * Dekodiert ein Base64url-Segment eines JWT.
 *
 * @param segment das Segment
 * @return der geparste Inhalt oder `null`, wenn das Segment unlesbar ist
 */
function decodeSegment(segment: string): unknown {
  const normalized = segment.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized.padEnd(segment.length + ((4 - (segment.length % 4)) % 4), '=');

  try {
    const binary = atob(padded);
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}

/**
 * Liest die Rollen und den Mandanten aus einem Token.
 *
 * <strong>Nur fuer die Anzeige:</strong> das Token wird nicht geprueft — die
 * Pruefung gehoert dem Dienst. Die Oberflaeche benutzt die Rollen, um eine
 * Aktion <em>anzubieten</em>, die der Dienst ohnehin prueft.
 *
 * @param token das Bearer-Token oder `null`
 * @returns die Rollen und der Mandant des Tokens
 */
export function decodeIdentity(token: string | null): { roles: string[]; organisationId: string | null } {
  if (token === null || token.length === 0) {
    return { roles: [], organisationId: null };
  }

  const payload = decodeSegment(token.split('.')[1] ?? '');
  if (typeof payload !== 'object' || payload === null) {
    return { roles: [], organisationId: null };
  }

  const claim = (payload as { roles?: unknown }).roles;
  let roles: string[] = [];
  if (Array.isArray(claim)) {
    roles = claim.filter((role): role is string => typeof role === 'string');
  } else if (typeof claim === 'string') {
    roles = claim.split(/[ ,]+/).filter((role) => role.length > 0);
  }

  const organisation = (payload as { organisation_id?: unknown }).organisation_id;

  return {
    roles,
    organisationId: typeof organisation === 'string' ? organisation : null,
  };
}

/**
 * Liest die Sitzung aus dem Cookie der Anfrage.
 *
 * @returns die Sitzung — ohne Token, wenn niemand angemeldet ist
 */
export async function readSession(): Promise<Session> {
  const store = await cookies();
  const token = store.get(TOKEN_COOKIE)?.value ?? null;

  return { token, ...decodeIdentity(token) };
}

/**
 * Legt das Bearer-Token im Cookie ab.
 *
 * <strong>httpOnly:</strong> das Token ist fuer ein Skript im Browser nicht
 * lesbar. <strong>sameSite=lax:</strong> der Cookie wird nur bei
 *_same-site_-Anfragen mitgeschickt. <strong>secure</strong> nur im Betrieb
 * ueber HTTPS — im Testlauf laeuft die Oberflaeche ueber HTTP.
 *
 * @param token das Bearer-Token
 */
export async function writeSessionToken(token: string): Promise<void> {
  const store = await cookies();
  store.set(TOKEN_COOKIE, token, {
    httpOnly: true,
    sameSite: 'lax',
    path: '/',
    secure: process.env.NODE_ENV === 'production',
  });
}

/**
 * Entfernt das Bearer-Token aus dem Cookie.
 */
export async function clearSessionToken(): Promise<void> {
  const store = await cookies();
  store.delete(TOKEN_COOKIE);
}

/**
 * Prueft, ob die Identitaet eine Rolle traegt.
 *
 * @param session die Sitzung
 * @param role die gesuchte Rolle
 * @return true, wenn die Rolle in der Identitaet steht
 */
export function hasRole(session: Session, role: string): boolean {
  return session.roles.includes(role);
}
