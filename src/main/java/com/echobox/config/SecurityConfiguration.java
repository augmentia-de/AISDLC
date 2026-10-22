package com.echobox.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
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
     * <p>Der Decoder prueft mehr als die Signatur (S-21):
     * <ul>
     *   <li>die **Zielgruppe** ({@code aud}) muss die konfigurierte Audience
     *       enthalten — ein Token, das derselbe Identity Provider fuer einen
     *       anderen Client ausgestellt hat, wird abgelehnt;</li>
     *   <li>die **Ablaufzeit** ({@code exp}) ist **Pflicht** — ein Token ohne
     *       Ablaufzeit waere unbegrenzt gueltig.</li>
     * </ul>
     * Beide Pruefungen schlagen fehl, wenn die Audience nicht konfiguriert ist:
     * die Audience wird dann **nicht** geprueft (es gibt nichts zu pruefen),
     * die Ablaufzeit aber weiterhin erzwungen.
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
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(validators(properties.getSecurity().getAudience()));
        return decoder;
    }

    /**
     * Die Pruefkette des Decoders: Zeitstempel, Pflicht-Ablaufzeit, Audience.
     *
     * @param audience die erwartete Zielgruppe (leer = keine Zielgruppenpruefung)
     * @return die Pruefkette
     */
    static OAuth2TokenValidator<Jwt> validators(String audience) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(expiryRequired());
        if (audience != null && !audience.isBlank()) {
            validators.add(audienceRequired(audience.trim()));
        }
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    /**
     * Die Ablaufzeit ist Pflicht: ein Token ohne {@code exp} ist unbegrenzt
     * gueltig und wird abgelehnt.
     *
     * @return die Pruefung
     */
    private static OAuth2TokenValidator<Jwt> expiryRequired() {
        return token -> {
            if (token.getExpiresAt() == null) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                        "Das Token traegt keine Ablaufzeit (exp).", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    /**
     * Die Zielgruppe muss die konfigurierte Audience enthalten.
     *
     * @param audience die erwartete Zielgruppe
     * @return die Pruefung
     */
    private static OAuth2TokenValidator<Jwt> audienceRequired(String audience) {
        return token -> {
            List<String> audiences = token.getAudience();
            if (audiences == null || !audiences.contains(audience)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                        "Das Token ist nicht fuer diese API ausgegeben (aud).", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
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
