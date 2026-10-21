/**
 * Das Test-Token der Sitzung.
 *
 * <strong>Warum ein selbst gebautes JWT?</strong> Die Oberflaeche liest die
 * Rollen und den Mandanten aus dem Token, um eine Aktion **anzubieten**
 * (ADR-015 §3). Der Dienst prueft die Rechte unabhaengig davon. Fuer den Test
 * genuegt deshalb ein Token mit den richtigen Anspruechen — er wird nie
 * gegen einen echten Dienst geprueft.
 */

/**
 * Kodiert einen Text in Base64url.
 *
 * @param text der Text
 * @returns die Base64url-Kodierung
 */
function base64url(text: string): string {
  const bytes = new TextEncoder().encode(text);
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * Baut ein Token mit den Rollen und dem Mandanten einer Identitaet.
 *
 * @param rollen die Rollen der Identitaet
 * @param organisationId der Mandant der Identitaet
 * @returns das Token (Base64url, ohne gueltige Signatur — der Dienst prueft es nicht)
 */
export function testToken(rollen: string[], organisationId = 'm-1'): string {
  const header = base64url(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const payload = base64url(JSON.stringify({ roles: rollen, organisation_id: organisationId }));
  return `${header}.${payload}.testsignatur`;
}
