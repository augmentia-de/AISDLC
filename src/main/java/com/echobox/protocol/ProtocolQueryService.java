package com.echobox.protocol;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.echobox.common.CursorCodec;
import com.echobox.common.NotFoundException;
import com.echobox.common.PageResponse;
import com.echobox.common.ValidationException;
import com.echobox.delivery.DeliveryAttempt;
import com.echobox.delivery.DeliveryRepository;
import com.echobox.delivery.DeliveryRun;
import com.echobox.protocol.api.DeliveryListResponse;
import com.echobox.protocol.api.DeliveryRunDetailResponse;
import com.echobox.protocol.api.DeliveryRunResponse;

/**
 * Die Lesezugriffe des Zustellprotokolls (FR-030, US-015).
 *
 * <p>Die Protokollsuche laeuft <strong>ausschliesslich ueber Metadaten</strong>
 * (D-11, ADR-017): Ereignis-ID, Ereignistyp, Endpunkt, Zustand und Zeitraum
 * sind filterbar; eine Suche nach <strong>Nutzlastinhalt</strong> existiert
 * nicht. Die Antwort nennt deshalb <strong>nie</strong> eine Nutzlast.
 *
 * <p><strong>Mandant vor allem anderen:</strong> die Liste ist
 * <strong>mandantengefiltert</strong> — sie zeigt die Laeufe des Mandanten aus
 * der Identitaet, auch dem {@code service_admin} (E-10). Der
 * <strong>Einzelabruf</strong> eines Laufs eines <strong>fremden</strong>
 * Mandanten endet mit <strong>404</strong> — auch fuer den
 * {@code service_admin} (D-02, V-27): die mandantenuebergreifende Sicht gilt
 * nur fuer Endpunktliste, Totenliste und Aufbewahrung (E-01).
 *
 * <p><strong>Sortierung:</strong> {@code created_at DESC, delivery_run_id DESC}
 * — damit ist die Reihenfolge total und der Cursor traegt den
 * Sortierschluessel. Der Zeitraumfilter bezieht sich auf denselben Zeitpunkt
 * (den <strong>Anlegezeitpunkt</strong>, V-40).
 */
@Service
public class ProtocolQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(ProtocolQueryService.class);

    private final DeliveryRepository repository;

    private final CursorCodec cursorCodec;

    /**
     * Erzeugt die Lesezugriffe.
     *
     * @param repository  der JDBC-Adapter des Zustellmoduls
     * @param cursorCodec der Codec der opaken Cursor
     */
    public ProtocolQueryService(DeliveryRepository repository, CursorCodec cursorCodec) {
        this.repository = repository;
        this.cursorCodec = cursorCodec;
    }

    /**
     * Listet das Zustellprotokoll <strong>des Mandanten</strong>.
     *
     * @param organisationId der Mandant aus der Identitaet
     * @param endpointId     der Filter auf den Endpunkt (nullable = alle)
     * @param eventId        der Filter auf die Ereignis-ID (nullable = alle)
     * @param eventType      der Filter auf den Ereignistyp (nullable = alle)
     * @param statuses       die gewuenschten Zustaende (leer = alle)
     * @param from           der Beginn des Zeitraums auf {@code created_at} (nullable)
     * @param to             das Ende des Zeitraums auf {@code created_at} (nullable)
     * @param limit          die Zahl der Eintraege je Seite
     * @param cursor         der Cursor der vorherigen Seite (nullable)
     * @return die Seite
     */
    public DeliveryListResponse list(String organisationId,
                                     UUID endpointId,
                                     UUID eventId,
                                     String eventType,
                                     List<String> statuses,
                                     Instant from,
                                     Instant to,
                                     int limit,
                                     String cursor) {
        CursorPosition position = decodeCursor(cursorCodec, cursor);
        List<DeliveryRun> rows = repository.list(organisationId, endpointId, eventId, eventType, statuses,
                from, to, limit + 1, position.createdAt(), position.deliveryRunId());
        return toPage(rows, limit);
    }

    /**
     * Liest einen Zustelllauf <strong>mit allen Versuchen</strong>.
     *
     * <p>Ein Lauf eines fremden Mandanten ergibt <strong>404</strong> — auch
     * fuer den {@code service_admin} (D-02, V-27).
     *
     * @param organisationId der Mandant aus der Identitaet
     * @param deliveryRunId  die Zustelllauf-ID
     * @return der Zustelllauf mit seinen Versuchen
     * @throws NotFoundException wenn der Lauf im Mandanten nicht existiert
     */
    public DeliveryRunDetailResponse get(String organisationId, UUID deliveryRunId) {
        DeliveryRun run = repository.findById(organisationId, deliveryRunId).orElseThrow(() -> {
            LOG.info("Der Zustelllauf {} existiert im Mandanten nicht.", deliveryRunId);
            return new NotFoundException();
        });
        List<DeliveryAttempt> attempts = new ArrayList<>(repository.attempts(organisationId, deliveryRunId));
        attempts.sort(Comparator.comparingInt(DeliveryAttempt::attemptNumber));
        return DeliveryRunDetailResponse.of(run, attempts);
    }

    private DeliveryListResponse toPage(List<DeliveryRun> rows, int limit) {
        boolean hasMore = rows.size() > limit;
        List<DeliveryRun> page = hasMore ? new ArrayList<>(rows.subList(0, limit)) : new ArrayList<>(rows);
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            DeliveryRun last = page.get(page.size() - 1);
            nextCursor = encodeCursor(last.createdAt(), last.deliveryRunId());
        }
        List<DeliveryRunResponse> items = page.stream().map(DeliveryRunResponse::of).toList();
        return new DeliveryListResponse(items, DeliveryListResponse.Page.of(
                new PageResponse(limit, hasMore, nextCursor)));
    }

    /**
     * Der Cursor ist <strong>opak</strong> und HMAC-signiert: er traegt den
     * Sortierschluessel der Liste (Anlegezeitpunkt und Zustelllauf-ID). Die
     * Position haengt am Schluessel, nicht an einem Offset — zwischen zwei
     * Seiten geaenderte Datensaetze werden deshalb weder doppelt noch
     * uebersprungen. Ein manipulierter Cursor wird mit HTTP 422 abgelehnt.
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
     * @param createdAt    der Anlegezeitpunkt
     * @param deliveryRunId die Zustelllauf-ID
     */
    private record CursorPosition(Instant createdAt, UUID deliveryRunId) {

        /** Der Anfang der Liste — kein Cursor. */
        private static final CursorPosition START = new CursorPosition(null, null);
    }
}
