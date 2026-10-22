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
     * Die Zielgruppen-Pruefung ist **immer** aktiv (S-29): eine leere
     * Konfiguration schaltet sie nicht ab, sondern lehnt **jedes** Token ab
     * (fail-closed). Der Startabbruch bei leerer Konfiguration kommt aus der
     * {@link AudiencePolicy} — der Decoder bleibt auch ohne Zielgruppe
     * konstruierbar, damit die Pruefkette fuer sich allein pruefbar bleibt.
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
     * Die Zielgruppen-Vorgabe (S-29).
     *
     * <p>Der Dienst startet **nicht**, wenn die Zielgruppe leer ist: eine leere
     * Zielgruppe waere ein stilles Fail-open (ein Token eines anderen Clients
     * desselben Identity Providers wuerde angenommen). Der Vorgabewert
     * {@code echobox} bleibt unveraendert.
     *
     * @param properties die Betriebskonfiguration
     * @return die Zielgruppen-Vorgabe
     * @throws IllegalStateException wenn die Zielgruppe leer ist
     */
    @Bean
    public AudiencePolicy audiencePolicy(EchoBoxProperties properties) {
        return AudiencePolicy.required(properties.getSecurity().getAudience());
    }

    /**
     * Die Pruefkette des Decoders: Zeitstempel, Pflicht-Ablaufzeit, Audience.
     *
     * <p>Die Zielgruppen-Pruefung ist **immer** Teil der Kette (S-29) — eine
     * leere Zielgruppe schaltet sie nicht ab.
     *
     * @param audience die erwartete Zielgruppe
     * @return die Pruefkette
     */
    static OAuth2TokenValidator<Jwt> validators(String audience) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(expiryRequired());
        validators.add(audienceRequired(audience));
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
     * <p>Ist keine Zielgruppe konfiguriert, wird **jedes** Token abgelehnt
     * (fail-closed, S-29): eine leere Konfiguration darf die Pruefung nicht
     * abschalten.
     *
     * @param audience die erwartete Zielgruppe
     * @return die Pruefung
     */
    private static OAuth2TokenValidator<Jwt> audienceRequired(String audience) {
        String expected = audience == null ? "" : audience.trim();
        return token -> {
            if (expected.isEmpty()) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                        "Die Zielgruppe der Token ist nicht konfiguriert.", null));
            }
            List<String> audiences = token.getAudience();
            if (audiences == null || !audiences.contains(expected)) {
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
