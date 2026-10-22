import type { StatusPresentation } from '@/components/StatusBadge';
import type { EndpointStatus } from '@/types/api';

/**
 * Die Darstellung des Endpunktzustands.
 *
 * <strong>Die Endpunktseite zeigt die deutschen Zustandswoerter</strong>
 * („aktiv“, „pausiert“) — so steht es im Testvertrag (Schritt 5 §6). Das
 * Zustellprotokoll zeigt dagegen die maschinenlesbaren Werte des Vertrags
 * (`failed`, `abandoned`, …). Beide Anzeigen tragen Text, Farbe und Form —
 * der Zustand hängt <strong>nie</strong> allein an der Farbe (ADR-018 §3).
 */

/**
 * Bildet den Zustand eines Endpunkts auf seine Anzeige ab.
 *
 * @param status der Zustand aus dem Vertrag
 * @returns die Darstellung
 */
export function presentEndpointStatus(status: EndpointStatus): StatusPresentation {
  if (status === 'paused') {
    return { label: 'pausiert', tone: 'pausiert', mark: '■' };
  }
  return { label: 'aktiv', tone: 'aktiv', mark: '●' };
}
