package com.echobox.endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.echobox.common.CursorCodec;
import com.echobox.common.CursorPosition;
import com.echobox.common.NotFoundException;
import com.echobox.common.PageResponse;
import com.echobox.endpoint.api.EndpointListResponse;
import com.echobox.endpoint.api.EndpointResponse;

/**
 * Die Lesezugriffe des Endpunktmoduls (FR-004).
 *
 * <p>Die Liste ist <strong>mandantengefiltert</strong> — mit einer Ausnahme:
 * der {@code service_admin} sieht die Endpunkte <strong>aller</strong> Mandanten.
 * Diese mandantenuebergreifende Sicht wird ausschliesslich ueber die schmale
 * Eigentuemerfunktion des Schemas hergestellt
 * ({@code app.list_endpoints_cross_tenant}, ADR-002 §7) — <strong>nicht</strong>
 * ueber eine Laufzeitrolle mit {@code BYPASSRLS}.
 *
 * <p>Der <strong>Einzelabruf</strong> einer Ressource eines fremden Mandanten
 * endet mit <strong>404</strong> — auch fuer den {@code service_admin} (D-02,
 * V-27). Die mandantenuebergreifende Sicht gilt nur fuer die Listen- und
 * Betriebsoperationen.
 *
 * <p><strong>Sortierung:</strong> {@code updated_at DESC, endpoint_id DESC} —
 * damit ist die Reihenfolge total und der Cursor traegt den Sortierschluessel.
 */
@Service
public class EndpointQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointQueryService.class);

    /** Die groesste Zahl der Endpunkte je Seite. */
    public static final int MAX_LIMIT = 200;

    private final EndpointRepository repository;

    private final CursorCodec cursorCodec;

    /**
     * Erzeugt die Lesezugriffe.
     *
     * @param repository  der JDBC-Adapter
     * @param cursorCodec der Codec der opaken Cursor
     */
    public EndpointQueryService(EndpointRepository repository, CursorCodec cursorCodec) {
        this.repository = repository;
        this.cursorCodec = cursorCodec;
    }

    /**
     * Liest einen Endpunkt im Mandanten.
     *
     * @param organisationId der Mandant aus der Identitaet
     * @param endpointId     die Endpunkt-ID
     * @return der Endpunkt
     * @throws NotFoundException wenn der Endpunkt nicht existiert oder einem
     *                           fremden Mandanten gehoert
     */
    public Endpoint get(String organisationId, UUID endpointId) {
        return repository.findById(organisationId, endpointId)
                .orElseThrow(() -> {
                    LOG.info("Endpunkt {} existiert im Mandanten nicht.", endpointId);
                    return new NotFoundException();
                });
    }

    /**
     * Listet die Endpunkte <strong>des Mandanten</strong>.
     *
     * @param organisationId der Mandant aus der Identitaet
     * @param statuses       die gewuenschten Status (leer = alle)
     * @param limit          die Zahl der Eintraege je Seite
     * @param cursor         der Cursor der vorherigen Seite (nullable)
     * @return die Seite
     */
    public EndpointListResponse list(String organisationId, List<String> statuses, int limit, String cursor) {
        CursorPosition position = cursorCodec.decodePosition(cursor);
        List<Endpoint> rows = repository.list(organisationId, statuses, limit + 1,
                position.sortKey(), position.id());
        return toPage(rows, limit);
    }

    /**
     * Listet die Endpunkte <strong>aller Mandanten</strong> — ausschliesslich
     * ueber die Eigentuemerfunktion des Schemas.
     *
     * @param statuses die gewuenschten Status (leer = alle)
     * @param limit    die Zahl der Eintraege je Seite
     * @param cursor   der Cursor der vorherigen Seite (nullable)
     * @return die Seite
     */
    public EndpointListResponse listCrossTenant(List<String> statuses, int limit, String cursor) {
        CursorPosition position = cursorCodec.decodePosition(cursor);
        String status = statuses == null || statuses.isEmpty() ? null : statuses.get(0);
        List<Endpoint> rows = repository.listCrossTenant(status, limit + 1,
                position.sortKey(), position.id());
        return toPage(rows, limit);
    }

    private EndpointListResponse toPage(List<Endpoint> rows, int limit) {
        boolean hasMore = rows.size() > limit;
        List<Endpoint> page = hasMore ? new ArrayList<>(rows.subList(0, limit)) : new ArrayList<>(rows);
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            Endpoint last = page.get(page.size() - 1);
            nextCursor = cursorCodec.encodePosition(last.updatedAt(), last.endpointId());
        }
        List<EndpointResponse> items = page.stream().map(EndpointResponse::of).toList();
        return new EndpointListResponse(items, new PageResponse(limit, hasMore, nextCursor));
    }
}
