package com.echobox.tenant;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.echobox.common.Problem;
import com.echobox.common.ProblemFactory;
import com.echobox.common.ProblemWriter;
import com.echobox.common.ValidationViolation;
import com.echobox.event.PayloadSize;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lehnt **Mandantenparameter** ab (D-01, AC-001-5, AC-004-4).
 *
 * <p>Der Mandant stammt **ausschliesslich** aus der Identitaet (JWT-Claim
 * {@code organisation_id}). Ein mitgeschickter Mandantenparameter wird mit
 * **HTTP 422** {@code tenant_parameter_rejected} abgelehnt — im Body, im Query
 * **und** in einem Header. Er wird **nicht** stillschweigend ignoriert: ein
 * Ignorieren wuerde den Aufrufer im Glauben lassen, er haette gefiltert.
 *
 * <p>Der Filter laeuft **nach** der Security-Kette: eine nicht angemeldete
 * Anfrage ergibt 401, nicht 422. Die Pruefung des Bodies liest **nur** die
 * Feldnamen der obersten Ebene — die Nutzlast wird nicht materialisiert und
 * nicht erneut geparst.
 *
 * <p>Der Filter setzt zugleich die Grenze fuer die Groesse des Request-Bodies
 * (2 MiB). Die Grenze der **Nutzlast** (1 MiB) prueft der Ereignisvorgang.
 *
 * <p><strong>Die Grenze haengt nicht an {@code Content-Length} (S-20):</strong>
 * bei {@code Transfer-Encoding: chunked} ist die Laenge unbekannt ({@code -1}).
 * Der Body wird deshalb <strong>begrenzt</strong> gelesen — hoechstens 2 MiB,
 * danach bricht das Lesen ab und die Anfrage ergibt HTTP 413. Ohne diese
 * Grenze wuerde ein Mandant den Body unbeschaenkt in den Heap des geteilten
 * JVM laden (Ausfall fuer alle Mandanten).
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 10)
public class TenantParameterFilter extends OncePerRequestFilter {

    /** Die Namen, die einen Mandantenparameter bezeichnen. */
    public static final Set<String> TENANT_PARAMETER_NAMES = Set.of(
            "organisation_id", "organisationid", "organization_id", "organizationid",
            "org_id", "orgid", "tenant_id", "tenantid", "tenant",
            "x-organisation-id", "x-organization-id", "x-org-id", "x-tenant-id", "x-tenant");

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    /** Die Groesse des Puffers, in dem der Request-Body gelesen wird (S-20). */
    private static final int READ_CHUNK_BYTES = 8192;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (isBodyRelevant(request) && request.getContentLengthLong() > PayloadSize.MAX_REQUEST_BODY_BYTES) {
            writeProblem(response, ProblemFactory.create(ProblemFactory.PAYLOAD_TOO_LARGE,
                    HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload too large",
                    "Der Request-Body ueberschreitet die zulaessige Groesse.",
                    request.getRequestURI()));
            return;
        }

        String offending = findTenantParameterInQueryOrHeader(request);

        if (offending == null && isBodyRelevant(request)) {
            // S-20: der Body wird **begrenzt** gelesen — unabhaengig von
            // `Content-Length`. Bei `Transfer-Encoding: chunked` liefert
            // `getContentLengthLong()` die Laenge -1; ohne diese Grenze wuerde
            // der Body unbeschaenkt in den Heap gelesen (Ausfall aller
            // Mandanten). `null` bedeutet: groesser als die Grenze.
            byte[] body = readBounded(request, PayloadSize.MAX_REQUEST_BODY_BYTES);
            if (body == null) {
                writeProblem(response, ProblemFactory.create(ProblemFactory.PAYLOAD_TOO_LARGE,
                        HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload too large",
                        "Der Request-Body ueberschreitet die zulaessige Groesse.",
                        request.getRequestURI()));
                return;
            }
            offending = findTenantParameterInBody(body);
            if (offending == null) {
                request = new CachedBodyRequest(request, body);
            }
        }

        if (offending != null) {
            writeProblem(response, ProblemFactory.create(ProblemFactory.TENANT_PARAMETER_REJECTED,
                    HttpStatus.UNPROCESSABLE_ENTITY.value(), "Tenant parameter rejected",
                    "Der Mandant stammt aus der Identitaet; ein Mandantenparameter wird nicht akzeptiert.",
                    request.getRequestURI(),
                    List.of(new ValidationViolation(offending, "tenant_parameter_not_allowed",
                            "Der Mandant darf nicht mitgeschickt werden."))));
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Liest den Request-Body **begrenzt**: hoechstens {@code limit} Byte werden
     * gelesen, danach bricht das Lesen ab (S-20).
     *
     * <p>Die Grenze haengt **nicht** an {@code Content-Length}: bei einer
     * chunked Uebertragung ist die Laenge unbekannt (-1). Der Body wird deshalb
     * gelesen, bis die Grenze ueberschritten ist — der Abbruch erfolgt, bevor
     * der Body vollstaendig im Heap liegt.
     *
     * @param request der Request
     * @param limit   die Hoechstzahl der gelesenen Bytes
     * @return die gelesenen Bytes oder {@code null}, wenn der Body zu gross ist
     * @throws IOException wenn das Lesen scheitert
     */
    private static byte[] readBounded(HttpServletRequest request, int limit) throws IOException {
        try (InputStream stream = request.getInputStream()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(limit + 1, READ_CHUNK_BYTES));
            byte[] chunk = new byte[READ_CHUNK_BYTES];
            int total = 0;
            while (total <= limit) {
                int read = stream.read(chunk);
                if (read < 0) {
                    break;
                }
                int allowed = Math.min(read, limit + 1 - total);
                buffer.write(chunk, 0, allowed);
                total += allowed;
                if (total > limit) {
                    return null;
                }
            }
            return buffer.toByteArray();
        }
    }

    private static boolean isBodyRelevant(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method) && !"PATCH".equals(method)) {
            return false;
        }
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    private static String findTenantParameterInQueryOrHeader(HttpServletRequest request) {
        for (String name : request.getParameterMap().keySet()) {
            if (isTenantParameter(name)) {
                return name;
            }
        }
        for (String name : java.util.Collections.list(request.getHeaderNames())) {
            if (isTenantParameter(name)) {
                return name;
            }
        }
        return null;
    }

    private static String findTenantParameterInBody(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        Set<String> names = new LinkedHashSet<>();
        try (JsonParser parser = JSON_FACTORY.createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return null;
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                names.add(parser.currentName());
                parser.nextToken();
                parser.skipChildren();
            }
        } catch (IOException exception) {
            // Der Body ist kein gueltiges JSON — das behandelt der Controller (HTTP 400).
            return null;
        }
        for (String name : names) {
            if (isTenantParameter(name)) {
                return name;
            }
        }
        return null;
    }

    private static boolean isTenantParameter(String name) {
        if (name == null) {
            return false;
        }
        return TENANT_PARAMETER_NAMES.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private static void writeProblem(HttpServletResponse response, Problem problem) throws IOException {
        ProblemWriter.write(response, problem);
    }

    /** Ersetzt den Request-Body durch die bereits gelesenen Bytes. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return stream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("Nicht unterstuetzt.");
                }

                @Override
                public int read() {
                    return stream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
