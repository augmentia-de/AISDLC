import type { BadgeTone } from '@/components/StatusBadge';
import type { DeliveryStatus } from '@/types/api';

/**
 * Die Darstellung des Zustellzustands.
 *
 * <strong>Das Zustellprotokoll zeigt die maschinenlesbaren Werte</strong> des
 * Vertrags (`sent`, `failed`, `abandoned`, …) — sie sind die Werte, nach
 * denen der Vertrag filtert. Die Form und die Farbe kommen zusaetzlich dazu:
 * der Zustand haengt <strong>nie</strong> allein an der Farbe (ADR-018 §3).
 */

/** Die Darstellung eines Zustands. */
export interface StatusPresentation {
  /** Der Text des Zustands. */
  label: string;
  /** Die Farbtonfamilie. */
  tone: BadgeTone;
  /** Das unterscheidbare Merkmal neben dem Text. */
  mark: string;
}

/**
 * Bildet den Zustand eines Zustelllaufs auf seine Anzeige ab.
 *
 * @param status der Zustand aus dem Vertrag
 * @returns die Darstellung
 */
export function presentDeliveryStatus(status: DeliveryStatus): StatusPresentation {
  switch (status) {
    case 'sent':
      return { label: status, tone: 'erfolg', mark: '✓' };
    case 'failed':
    case 'abandoned':
    case 'rejected':
      return { label: status, tone: 'fehler', mark: '✕' };
    case 'pending':
    case 'scheduled':
    case 'delayed':
      return { label: status, tone: 'offen', mark: '▲' };
    default:
      return { label: status, tone: 'neutral', mark: '○' };
  }
}

/**
 * Die deutschen Bezeichnungen der Filterwerte.
 *
 * <strong>Warum uebersetzt und nicht roh?</strong> das Protokoll zeigt die
 * Werte des Vertrags, die Filterleiste zeigt die verstaendlichen deutschen
 * Begriffe. Der <em>Wert</em> des Filters bleibt der Vertragswert — nur die
 * Beschriftung ist uebersetzt.
 */
export const DELIVERY_STATUS_FILTER: ReadonlyArray<{ value: DeliveryStatus; label: string }> = [
  { value: 'pending', label: 'wartend' },
  { value: 'scheduled', label: 'eingeplant' },
  { value: 'delayed', label: 'verzoegert' },
  { value: 'sent', label: 'zugestellt' },
  { value: 'failed', label: 'fehlgeschlagen' },
  { value: 'abandoned', label: 'aufgegeben' },
  { value: 'rejected', label: 'abgelehnt' },
  { value: 'cancelled', label: 'abgebrochen' },
  { value: 'no_subscriber', label: 'ohne Empfaenger' },
];
