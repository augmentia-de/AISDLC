package com.echobox.tenant;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import com.echobox.common.UnauthenticatedException;

/**
 * Der Mandant und die Rollen des angemeldeten Aufrufers.
 *
 * <p>Der Kontext ist **request-scoped** und liest **ausschliesslich** das JWT —
 * er greift **nicht** auf die Datenbank zu und setzt **nichts** am Mandanten.
 * Der Mandant wird erst **innerhalb** der Transaktion gesetzt, die auch liest
 * (ADR-002 §3, {@link TenantTransactionRunner}).
 *
 * <p>Die Rollen kommen aus **beiden** Quellen, die der Vertrag nennt: dem Claim
 * {@code roles} und der Autoritaet {@code ROLE_<rolle>}. Beide werden vereinigt —
 * so bleibt die Zuordnung offen, welche Form die Umgebung liefert.
 *
 * <p>Der Mandant stammt **ausschliesslich** aus der Identitaet (D-01). Ein
 * Mandantenparameter im Request wird abgelehnt, nie uebernommen.
 */
@Component
@RequestScope
public class TenantContext {

    /** Der Claim, der den Mandanten traegt. */
    public static final String ORGANISATION_ID_CLAIM = "organisation_id";

    /** Der Claim, der die Rollen traegt. */
    public static final String ROLES_CLAIM = "roles";

    /**
     * Liefert den Mandanten der Identitaet.
     *
     * @return der Mandant oder {@code ""}, wenn keine Identitaet vorliegt
     */
    public String organisationId() {
        JwtAuthenticationToken token = jwtToken();
        if (token == null) {
            return "";
        }
        Object claim = token.getToken().getClaims().get(ORGANISATION_ID_CLAIM);
        return claim == null ? "" : String.valueOf(claim);
    }

    /**
     * Liefert den Mandanten der Identitaet und verlangt, dass er gesetzt ist.
     *
     * @return der Mandant
     * @throws UnauthenticatedException wenn kein Mandant vorliegt (HTTP 401)
     */
    public String requireOrganisationId() {
        String organisationId = organisationId();
        if (organisationId.isBlank()) {
            throw new UnauthenticatedException();
        }
        return organisationId;
    }

    /**
     * Liefert die ausloesende Identitaet.
     *
     * @return die Identitaet oder {@code ""}, wenn sie unbekannt ist
     */
    public String subject() {
        JwtAuthenticationToken token = jwtToken();
        if (token == null) {
            return "";
        }
        String subject = token.getToken().getSubject();
        return subject == null ? "" : subject;
    }

    /**
     * Liefert die Rollen der Identitaet — Kleinbuchstaben, ohne Praefix.
     *
     * @return die Rollen
     */
    public Set<String> roles() {
        Set<String> roles = new LinkedHashSet<>();
        JwtAuthenticationToken token = jwtToken();
        if (token != null) {
            Object claim = token.getToken().getClaims().get(ROLES_CLAIM);
            if (claim instanceof Collection<?> values) {
                for (Object value : values) {
                    addRole(roles, String.valueOf(value));
                }
            } else if (claim instanceof String value) {
                for (String part : value.split("[,\\s]+")) {
                    addRole(roles, part);
                }
            }
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String name = authority.getAuthority();
                if (name != null && name.startsWith("ROLE_")) {
                    addRole(roles, name.substring("ROLE_".length()));
                }
            }
        }
        return Collections.unmodifiableSet(roles);
    }

    /**
     * Prueft, ob die Identitaet die Rolle traegt.
     *
     * @param role die Rolle (zum Beispiel {@code receiver_admin})
     * @return {@code true}, wenn die Rolle vorliegt
     */
    public boolean hasRole(String role) {
        return role != null && roles().contains(role.toLowerCase(Locale.ROOT));
    }

    private static void addRole(Set<String> roles, String raw) {
        if (raw == null) {
            return;
        }
        String role = raw.trim().toLowerCase(Locale.ROOT);
        if (role.startsWith("role_")) {
            role = role.substring("role_".length());
        }
        if (!role.isEmpty()) {
            roles.add(role);
        }
    }

    private static JwtAuthenticationToken jwtToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return token;
        }
        return null;
    }
}
