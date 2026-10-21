package com.echobox.protocol;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.echobox.common.CursorCodec;
import com.echobox.common.ForbiddenException;
import com.echobox.common.PageResponse;
import com.echobox.common.ValidationException;
import com.echobox.delivery.DeliveryRepository;
import com.echobox.delivery.DeliveryStatus;
import com.echobox.protocol.api.DeadDeliveryListResponse;
import com.echobox.protocol.api.DeadDeliveryResponse;
import com.echobox.tenant.CrossTenantScope;
import com.echobox.tenant.TenantContext;

/**
 * Die Lesezugriffe der Totenliste (FR-031, US-016).
 *
 * <p>Die Totenliste ist <strong>mandantenuebergreifend</strong>: sie zeigt die
 * toten Zustellungen <strong>aller</strong> Mandanten. Diese Sicht wird
 * ausschliesslich ueber die schmale Eigentuemerfunktion des Schemas
 * hergestellt ({@code app.list_dead_deliveries_cross_tenant}, ADR-002 §7) —
 * <strong>nicht</strong> ueber eine Laufzeitrolle mit {@code BYPASSRLS}. Die
 * Funktion ist <strong>fail-closed</strong>: ohne die Freigabe
 * {@code app.cross_tenant = 'service_admin'} liefert sie <strong>keine</strong>
 * Zeile. Der Aufruf laeuft deshalb <strong>immer</strong> ueber den
 * {@link CrossTenantScope} — und der <strong>erst</strong>, nachdem die Rolle
 * geprueft wurde.
 *
 * <p><strong>Nur {@code service_admin}</strong> darf die Totenliste lesen; ein
 * {@code receiver_admin} erhaelt HTTP 403 (AC-016-2).
 *
 * <p><strong>Der Inhalt ist fest verdrahtet:</strong> die Eigentuemerfunktion
 * kennt <strong>keinen</strong> Statusparameter und liefert ausschliesslich
 * {@code abandoned} (N-14). Der Query-Parameter {@code status} des Vertrags
 * kennt nur diesen einen Wert — er wird <strong>nicht</strong> weitergereicht,
 * sondern <strong>geprueft</strong>: ein anderer Wert ist ein Verstoss gegen
 * den Vertrag (HTTP 422).
 *
 * <p>Der Zeitraumfilter bezieht sich auf den <strong>Aufgabezeitpunkt</strong>
 * ({@code abandoned_at}) — nicht auf den Anlegezeitpunkt (V-40). Der Cursor
 * traegt dagegen den Anlegezeitpunkt, weil die Funktion danach sortiert.
 */
@Service
public class DeadDeliveryQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(DeadDeliveryQueryService.class);

    /** Die Rolle, die die Totenliste lesen darf. */
    public static final String ROLE_SERVICE_ADMIN = "service_admin";

    /** Der einzige zulaessige Status der Totenliste. */
    private static final String DEAD_STATUS = DeliveryStatus.ABANDONED.wireValue();

    private final DeliveryRepository repository;

    private final CursorCodec cursorCodec;

    private final CrossTenantScope crossTenantScope;

    private final TenantContext tenantContext;

    /**
     * Erzeugt die Lesezugriffe.
     *
     * @param repository      der JDBC-Adapter des Zustellmoduls
     * @param cursorCodec     der Codec der opaken Cursor
     * @param crossTenantScope der mandantenuebergreifende Leseweg
     * @param tenantContext   der Mandant und die Rollen aus der Identitaet
     */
    public DeadDeliveryQueryService(DeliveryRepository repository,
                                    CursorCodec cursorCodec,
                                    CrossTenantScope crossTenantScope,
                                    TenantContext tenantContext) {
        this.repository = repository;
        this.cursorCodec = cursorCodec;
        this.crossTenantScope = crossTenantScope;
        this.tenantContext = tenantContext;
    }

    /**
     * Listet die toten Zustellungen <strong>aller Mandanten</strong>.
     *
     * @param endpointId der Filter auf den Endpunkt (nullable = alle)
     * @param eventType  der Filter auf den Ereignistyp (nullable = alle)
     * @param statuses   die gewuenschten Zustaende — erlaubt ist nur
     *                   {@code abandoned}
     * @param from       der Beginn des Zeitraums auf {@code abandoned_at} (nullable)
     * @param to         das Ende des Zeitraums auf {@code abandoned_at} (nullable)
     * @param limit      die Zahl der Eintraege je Seite
     * @param cursor     der Cursor der vorherigen Seite (nullable)
     * @return die Seite
     * @throws ForbiddenException wenn die Rolle die Totenliste nicht lesen darf
     * @throws ValidationException wenn ein Status unzulaessig ist
     */
    public DeadDeliveryListResponse list(UUID endpointId,
                                         String eventType,
                                         List<String> statuses,
                                         Instant from,
                                         Instant to,
                                         int limit,
                                         String cursor) {
        requireServiceAdmin();
        requireDeadStatusOnly(statuses);
        CursorPosition position = decodeCursor(cursorCodec, cursor);
        List<DeliveryRepository.DeadDelivery> rows = crossTenantScope.asServiceAdmin(() ->
                repository.listDeadDeliveriesCrossTenant(endpointId, eventType, limit + 1, from, to,
                        position.createdAt(), position.deliveryRunId()));
        return toPage(rows, limit);
    }

    private DeadDeliveryListResponse toPage(List<DeliveryRepository.DeadDelivery> rows, int limit) {
        boolean hasMore = rows.size() > limit;
        List<DeliveryRepository.DeadDelivery> page = hasMore
                ? new ArrayList<>(rows.subList(0, limit))
                : new ArrayList<>(rows);
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            DeliveryRepository.DeadDelivery last = page.get(page.size() - 1);
            nextCursor = encodeCursor(last.createdAt(), last.deliveryRunId());
        }
        List<DeadDeliveryResponse> items = page.stream().map(DeadDeliveryResponse::of).toList();
        return new DeadDeliveryListResponse(items, DeadDeliveryListResponse.Page.of(
                new PageResponse(limit, hasMore, nextCursor)));
    }

    /**
     * Die Totenliste lesen darf <strong>nur</strong> der Service-Admin — der
     * Empfaenger-Admin erhaelt HTTP 403 (AC-016-2).
     */
    private void requireServiceAdmin() {
        if (!tenantContext.hasRole(ROLE_SERVICE_ADMIN)) {
            LOG.info("Die Totenliste wurde von einer Rolle ohne '{}-Recht angefordert.",
                    ROLE_SERVICE_ADMIN);
            throw new ForbiddenException();
        }
    }

    /**
     * Die Totenliste enthaelt <strong>ausschliesslich aufgegebene
     * Zustellungen</strong>. Ein mitgeschickter Status muss deshalb
     * {@code abandoned} sein — jeder andere Wert waere ein Verstoss gegen den
     * Vertrag (HTTP 422).
     */
    private static void requireDeadStatusOnly(List<String> statuses) {
        if (statuses == null) {
            return;
        }
        for (String status : statuses) {
            if (!DEAD_STATUS.equals(status)) {
                throw new ValidationException("status", "status_invalid",
                        "Die Totenliste enthaelt ausschliesslich aufgegebene Zustellungen — "
                                + "der einzige zulaessige Status ist '" + DEAD_STATUS + "'.");
            }
        }
    }

    /**
     * Der Cursor ist <strong>opak</strong> und HMAC-signiert: er traegt den
     * Sortierschluessel der Liste (Anlegezeitpunkt und Zustelllauf-ID). Ein
     * manipulierter Cursor wird mit HTTP 422 abgelehnt.
     */
    private String encodeCursor(Instant createdAt, UUID deliveryRunId) {
        return cursorCodec.encode(createdAt.toEpochMilli() + ":" + deliveryRunId);
    }

    private static CursorPosition decodeCursor(CursorCodec cursorCodec, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return CursorPosition.START;
        }
        String raw = cursorCodec.decode(cursor);
        int separator = raw.indexOf(':');
        try {
            Instant createdAt = Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separator)));
            UUID deliveryRunId = UUID.fromString(raw.substring(separator + 1));
            return new CursorPosition(createdAt, deliveryRunId);
        } catch (RuntimeException e) {
            throw new ValidationException("cursor", "cursor_invalid",
                    "Der Cursor ist ungueltig — er gehoert zu einer anderen Operation oder einem anderen Filter.");
        }
    }

    /**
     * Der Sortierschluessel, den der Cursor traegt.
     *
     * @param createdAt     der Anlegezeitpunkt
     * @param deliveryRunId die Zustelllauf-ID
     */
    private record CursorPosition(Instant createdAt, UUID deliveryRunId) {

        /** Der Anfang der Liste — kein Cursor. */
        private static final CursorPosition START = new CursorPosition(null, null);
    }
}
