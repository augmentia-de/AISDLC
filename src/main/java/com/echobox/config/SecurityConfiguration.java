package com.echobox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.echobox.common.ProblemFactory;
import com.echobox.common.ProblemWriter;

/**
 * Die Anmeldung und die Zugriffsregeln des Dienstes.
 *
 * <p>Der Dienst ist eine Maschinen-zu-Maschinen-Schnittstelle: **Bearer-Token**
 * (JWT), **keine** Sitzung, **kein** CSRF-Schutz (es gibt keinen Cookie, den
 * ein fremder Herkunft ausloesen koennte).
 *
 * <p>Der Mandant stammt **ausschliesslich** aus dem Token (Claim
 * {@code organisation_id}); ein Mandantenparameter im Request wird abgelehnt
 * (D-01). Die Rollen werden als Autoritaet {@code ROLE_<rolle>} gefuehrt und
 * zusaetzlich als Claim {@code roles} gelesen.
 *
 * <p>Die **Rollenpruefung** der mandantenbezogenen Operationen sitzt **nicht**
 * hier, sondern in der Fachlogik: geprueft wird **erst der Mandant** (fremde
 * Ressource → 404), **dann die Rolle** (eigener Mandant, falsche Rolle → 403).
 * Eine Rollenpruefung an der URL wuerde die Reihenfolge umkehren und damit die
 * Existenz fremder Objekte preisgeben (D-02, V-39).
 *
 * <p>Alle Fehlerantworten tragen das Schema {@code Problem} des Vertrags.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    /**
     * Baut die Filterkette.
     *
     * @param http der HTTP-Konfigurator
     * @return die Filterkette
     * @throws Exception bei einem Konfigurationsfehler
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint entryPoint = problemAuthenticationEntryPoint();
        AccessDeniedHandler accessDeniedHandler = problemAccessDeniedHandler();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/health", "/health/ready", "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    /**
     * Der JWT-Decoder. Die JWKS-Adresse kommt aus der Konfiguration; der
     * Decoder laedt die Schluessel **lazy** — beim Start wird nichts geholt.
     *
     * @param properties die Betriebskonfiguration
     * @return der Decoder
     */
    @Bean
    public JwtDecoder jwtDecoder(EchoBoxProperties properties) {
        String jwkSetUri = properties.getSecurity().getJwkSetUri();
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            return token -> {
                throw new JwtException("Kein Identity Provider konfiguriert.");
            };
        }
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    /**
     * Der Eintrittspunkt: eine nicht angemeldete Anfrage ergibt HTTP 401.
     *
     * @return der Eintrittspunkt
     */
    @Bean
    public AuthenticationEntryPoint problemAuthenticationEntryPoint() {
        return (request, response, exception) -> ProblemWriter.write(response,
                ProblemFactory.create(ProblemFactory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.value(),
                        "Unauthorized", "Das Bearer-Token fehlt oder ist ungueltig.", request.getRequestURI()));
    }

    /**
     * Der Zugriffsschutz: eine angemeldete, aber nicht berechtigte Anfrage
     * ergibt HTTP 403.
     *
     * @return der Zugriffsschutz
     */
    @Bean
    public AccessDeniedHandler problemAccessDeniedHandler() {
        return (request, response, exception) -> ProblemWriter.write(response,
                ProblemFactory.create(ProblemFactory.FORBIDDEN, HttpStatus.FORBIDDEN.value(),
                        "Forbidden", "Diese Rolle darf die Operation fuer diesen Mandanten nicht ausfuehren.",
                        request.getRequestURI()));
    }
}
