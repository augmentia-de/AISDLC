package com.echobox.testsupport;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Erzeugt angemeldete Anfragen fuer die Integrationstests.
 *
 * <p>Der Mandant und die Rolle kommen aus dem Token — nie aus einem Parameter
 * (D-01). Der Test legt beide in den Token, damit die Reihenfolge der
 * Pruefungen (erst Mandant, dann Rolle — V-39) pruefbar wird.
 *
 * <p>Die Rollen werden sowohl als Claim {@code roles} als auch als Autoritaet
 * {@code ROLE_<rolle>} gesetzt: welche der beiden Formen die Anwendung liest,
 * entscheidet die Implementierung in Schritt 6 — der Test bleibt derselbe.
 */
public final class Auth {

    public static final String DEVELOPER = "developer";
    public static final String RECEIVER_ADMIN = "receiver_admin";
    public static final String SERVICE_ADMIN = "service_admin";

    private Auth() {
        // statische Helferklasse
    }

    /**
     * Erzeugt eine Anfrage im Namen einer Rolle eines Mandanten.
     *
     * @param organisationId der Mandant aus der Identitaet
     * @param subject        die ausloesende Identitaet
     * @param roles          die Rollen des Tokens
     * @return der Request-Post-Processor
     */
    public static RequestPostProcessor as(String organisationId, String subject, String... roles) {
        List<GrantedAuthority> authorities = List.of(roles).stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        RequestPostProcessor identity = jwt()
                .jwt(token -> token
                        .subject(subject)
                        .claim("organisation_id", organisationId)
                        .claim("roles", List.of(roles)))
                .authorities(authorities);

        RequestPostProcessor csrfToken = csrf();

        return request -> {
            identity.postProcessRequest(request);
            csrfToken.postProcessRequest(request);
            return request;
        };
    }

    /**
     * Erzeugt eine Anfrage eines Entwicklers.
     *
     * @param organisationId der Mandant aus der Identitaet
     * @param subject        die ausloesende Identitaet
     * @return der Request-Post-Processor
     */
    public static RequestPostProcessor developer(String organisationId, String subject) {
        return as(organisationId, subject, DEVELOPER);
    }

    /**
     * Erzeugt eine Anfrage eines Empfaenger-Admins.
     *
     * @param organisationId der Mandant aus der Identitaet
     * @param subject        die ausloesende Identitaet
     * @return der Request-Post-Processor
     */
    public static RequestPostProcessor receiverAdmin(String organisationId, String subject) {
        return as(organisationId, subject, RECEIVER_ADMIN);
    }

    /**
     * Erzeugt eine Anfrage eines Service-Admins.
     *
     * @param organisationId der Mandant aus der Identitaet
     * @param subject        die ausloesende Identitaet
     * @return der Request-Post-Processor
     */
    public static RequestPostProcessor serviceAdmin(String organisationId, String subject) {
        return as(organisationId, subject, SERVICE_ADMIN);
    }
}
