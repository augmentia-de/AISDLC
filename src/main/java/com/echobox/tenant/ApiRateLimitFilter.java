package com.echobox.tenant;

import java.io.IOException;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.echobox.common.ApiRateLimiter;
import com.echobox.common.ProblemFactory;
import com.echobox.common.ProblemWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Das <strong>Anfragelimit der API</strong> (S-24).
 *
 * <p>Der eingefrorene Vertrag sagt fuer jede Operation HTTP <strong>429</strong>
 * {@code rate_limit_exceeded} mit dem Header {@code Retry-After} zu. Dieser
 * Filter setzt diese Zusage um: er zaehlt die Anfragen je Mandant und lehnt
 * eine Anfrage, die das Limit ueberschreitet, mit <strong>429</strong> und
 * {@code Retry-After} (Wartezeit in Sekunden) ab.
 *
 * <p>Der Filter laeuft <strong>nach</strong> der Security-Kette: der Mandant
 * stammt aus der Identitaet, nicht aus der Anfrage. Anfragen <em>ohne</em>
 * Identitaet werden nicht gezaehlt — sie werden von der Security-Kette ohnehin
 * mit 401 abgelehnt, und der Gesundheitsendpunkt bleibt immer erreichbar.
 *
 * <p>Das Endpunkt-Limit aus FR-033 ist davon unberuehrt: es zaehlt je
 * Endpunkt und wirkt in der Zustellung.
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 15)
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final ApiRateLimiter limiter;

    /**
     * Erzeugt den Filter.
     *
     * @param limiter der Zaehler des Anfragelimits
     */
    public ApiRateLimitFilter(ApiRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String organisationId = organisationIdOf();
        if (organisationId == null) {
            chain.doFilter(request, response);
            return;
        }

        ApiRateLimiter.Decision decision = limiter.acquire(organisationId);
        if (!decision.permitted()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            ProblemWriter.write(response, ProblemFactory.create(ProblemFactory.RATE_LIMIT_EXCEEDED,
                    HttpStatus.TOO_MANY_REQUESTS.value(), "Too many requests",
                    "Zu viele Anfragen. Bitte später erneut versuchen.",
                    request.getRequestURI()));
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Liefert den Mandanten der Identitaet — oder {@code null}, wenn keine
     * Identitaet vorliegt (nicht angemeldete Anfrage, Gesundheitsendpunkt).
     *
     * @return der Mandant oder {@code null}
     */
    private static String organisationIdOf() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication instanceof JwtAuthenticationToken token)) {
            return null;
        }
        Object claim = token.getToken().getClaims().get(TenantContext.ORGANISATION_ID_CLAIM);
        if (claim == null) {
            return null;
        }
        String organisationId = String.valueOf(claim).trim();
        return organisationId.isEmpty() ? null : organisationId;
    }
}
