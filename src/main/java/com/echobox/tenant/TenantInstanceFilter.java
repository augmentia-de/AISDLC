package com.echobox.tenant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.echobox.common.Problem;
import com.echobox.common.ProblemFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Die Mandanteninstanz: eine Instanz kann an **einen** Mandanten gebunden
 * werden (ADR-013, AC-023-2).
 *
 * <p>Eine gebundene Instanz bedient **ausschliesslich** diesen Mandanten. Eine
 * Anfrage eines anderen Mandanten wird mit **HTTP 403** {@code forbidden}
 * abgelehnt — sie wird nicht etwa als „fremde Ressource“ (404) behandelt: der
 * Aufrufer ist angemeldet und berechtigt, aber **diese Instanz** fuehrt diesen
 * Mandanten nicht.
 *
 * <p>Ohne Bindung ({@code echobox.tenant.id} leer) gilt der geteilte Betrieb —
 * der Standard (ADR-013).
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 20)
public class TenantInstanceFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String boundOrganisationId;

    private final TenantContext tenantContext;

    /**
     * Erzeugt die Bindung.
     *
     * @param boundOrganisationId der gebundene Mandant (leer = geteilter Betrieb)
     * @param tenantContext        der Mandant aus der Identitaet
     */
    public TenantInstanceFilter(@org.springframework.beans.factory.annotation.Value("${echobox.tenant.id:}")
                                String boundOrganisationId,
                                TenantContext tenantContext) {
        this.boundOrganisationId = boundOrganisationId == null ? "" : boundOrganisationId.trim();
        this.tenantContext = tenantContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!boundOrganisationId.isBlank() && isAuthenticated()) {
            String organisationId = tenantContext.organisationId();
            if (!organisationId.isBlank() && !boundOrganisationId.equals(organisationId)) {
                writeProblem(response, request, ProblemFactory.create(ProblemFactory.FORBIDDEN,
                        HttpStatus.FORBIDDEN.value(), "Forbidden",
                        "Diese Instanz bedient nur den Mandanten " + boundOrganisationId + ".",
                        request.getRequestURI()));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static void writeProblem(HttpServletResponse response, HttpServletRequest request, Problem problem)
            throws IOException {
        response.setStatus(problem.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(MAPPER.writeValueAsString(problem));
    }
}
