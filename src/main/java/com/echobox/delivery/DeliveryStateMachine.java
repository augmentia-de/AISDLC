package com.echobox.delivery;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Die Zustandsuebergaenge des Zustelllaufs — die **einzige** Stelle, die
 * Zustaende aendert (ADR-006, D-10).
 *
 * <p>Die Uebergaenge folgen der Uebergangstabelle in
 * {@code docs/architecture/domain_model.md} Abschnitt 3.3:
 *
 * <pre>
 *   pending   -&gt; scheduled, cancelled, no_subscriber, delayed
 *   scheduled -&gt; sent, rejected, failed, cancelled, delayed
 *   failed    -&gt; scheduled, delayed, abandoned, cancelled
 *   delayed   -&gt; scheduled, abandoned, cancelled
 * </pre>
 *
 * <p>Ein **endgueltiger** Zustand hat **keinen** Ausgang: eine abgebrochene,
 * aufgegebene, abgelehnte oder zugestellte Zustellung wird nie wieder
 * eingeplant.
 *
 * <p><strong>Der Weg in den Zustand {@code delayed} (A-6 / B-17):</strong> der
 * Ratenbegrenzungs-Pfad verschiebt einen Lauf auch dann, wenn er gerade
 * beansprucht wurde — der Uebergang {@code scheduled -&gt; delayed} ist deshalb
 * zulaessig. Die Uebergangstabelle in
 * {@code docs/architecture/domain_model.md} §3.3 fuehrt diesen Uebergang (sie
 * wurde nachgezogen); diese Klasse und die Tabelle sind dieselbe Quelle der
 * Wahrheit.
 */
public final class DeliveryStateMachine {

    private static final Map<DeliveryStatus, Set<DeliveryStatus>> TRANSITIONS = Map.of(
            DeliveryStatus.PENDING, EnumSet.of(DeliveryStatus.SCHEDULED, DeliveryStatus.CANCELLED,
                    DeliveryStatus.NO_SUBSCRIBER, DeliveryStatus.DELAYED),
            DeliveryStatus.SCHEDULED, EnumSet.of(DeliveryStatus.SENT, DeliveryStatus.REJECTED,
                    DeliveryStatus.FAILED, DeliveryStatus.CANCELLED, DeliveryStatus.DELAYED),
            DeliveryStatus.FAILED, EnumSet.of(DeliveryStatus.SCHEDULED, DeliveryStatus.DELAYED,
                    DeliveryStatus.ABANDONED, DeliveryStatus.CANCELLED),
            DeliveryStatus.DELAYED, EnumSet.of(DeliveryStatus.SCHEDULED, DeliveryStatus.ABANDONED,
                    DeliveryStatus.CANCELLED));

    private DeliveryStateMachine() {
        // statische Zustandsregeln — keine Instanz
    }

    /**
     * Liefert, ob ein Zustand endgueltig ist.
     *
     * @param status der Zustand
     * @return {@code true}, wenn der Zustand endgueltig ist
     */
    public static boolean isTerminal(DeliveryStatus status) {
        return status != null && status.isTerminal();
    }

    /**
     * Prueft, ob der Uebergang zulaessig ist.
     *
     * @param from der Ausgangszustand
     * @param to   der Zielzustand
     * @return {@code true}, wenn der Uebergang zulaessig ist
     */
    public static boolean canTransition(DeliveryStatus from, DeliveryStatus to) {
        if (from == null || to == null || isTerminal(from)) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Der Zustand nach der Pause des Endpunkts (D-03).
     *
     * <p>Ein offener Wiederholungsvorgang wird abgebrochen: {@code pending},
     * {@code scheduled}, {@code failed} und {@code delayed} werden
     * {@code cancelled}. Ein endgueltiger Zustand bleibt unveraendert — eine
     * erfolgreich zugestellte Zustellung wird durch eine Pause nicht
     * rueckgenommen.
     *
     * @param status der Zustand vor der Pause
     * @return der Zustand nach der Pause
     */
    public static DeliveryStatus onEndpointPaused(DeliveryStatus status) {
        if (status == null || isTerminal(status)) {
            return status;
        }
        return DeliveryStatus.CANCELLED;
    }

    /**
     * Der Zustand eines Laufs, der gerade beansprucht wurde (ADR-006 §3).
     *
     * <p>Ein beanspruchter Lauf ist <strong>{@code scheduled}</strong>: ein
     * Versuch laeuft. Ein bereits laufender Lauf bleibt {@code scheduled} —
     * der Zustand wechselt nicht auf sich selbst, er bleibt derselbe.
     *
     * @param status der Zustand vor der Beanspruchung
     * @return der Zustand nach der Beanspruchung
     */
    public static DeliveryStatus onClaimed(DeliveryStatus status) {
        if (status == null || isTerminal(status)) {
            return status;
        }
        return DeliveryStatus.SCHEDULED;
    }

    /**
     * Der Zustand eines Laufs, dessen Versuch durch das Endpunkt-Limit
     * verschoben wurde (FR-033, ADR-010).
     *
     * <p>Der Lauf traegt den Zustand {@code delayed} und einen naechsten
     * Versuchszeitpunkt — der Versuch geht <strong>nicht</strong> verloren
     * (AC-018-1).
     *
     * <p>Der Uebergang ist <strong>aus jedem offenen Zustand</strong> zulaessig,
     * den die Uebergangstabelle kennt: das Endpunkt-Limit wirkt auch auf den
     * <strong>ersten</strong> Versuch eines frischen Laufs — sonst kaeme ein
     * Endpunkt in der ersten Minute ueber das Limit hinaus. Der Vertrag und
     * ADR-010 nennen den Zustand {@code delayed} ohne Einschraenkung des
     * Ausgangszustands. Die Uebergangstabelle in
     * {@code docs/architecture/domain_model.md} §3.3 fuehrt die Uebergaenge
     * {@code pending -> delayed} und {@code scheduled -> delayed} (A-6 / B-17).
     *
     * @param status der Zustand vor der Verschiebung
     * @return der Zustand nach der Verschiebung
     */
    public static DeliveryStatus onRateLimited(DeliveryStatus status) {
        if (status == null || isTerminal(status) || !canTransition(status, DeliveryStatus.DELAYED)) {
            return status;
        }
        return DeliveryStatus.DELAYED;
    }

    /**
     * Der Zustand, in dem ein Lauf seinen naechsten Versuch **traegt**.
     *
     * <p>Ein endgueltiger Zustand traegt **keinen** naechsten Versuchszeitpunkt
     * (DB-Constraint {@code delivery_runs_terminal_no_next_attempt_ck}) — fuer
     * ihn liefert die Methode {@code null}.
     *
     * @param status der Zustand
     * @return {@code SCHEDULED} fuer einen offenen Lauf, {@code null} fuer einen endgueltigen
     */
    public static DeliveryStatus nextAttemptAtFor(DeliveryStatus status) {
        if (status == null || isTerminal(status)) {
            return null;
        }
        return DeliveryStatus.SCHEDULED;
    }
}
