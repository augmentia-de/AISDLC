package com.echobox.endpoint;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.echobox.common.ClockPort;
import com.echobox.common.ForbiddenException;
import com.echobox.common.NotFoundException;
import com.echobox.crypto.PayloadCipher;
import com.echobox.endpoint.api.EndpointCreateRequest;
import com.echobox.endpoint.api.EndpointUpdateRequest;
import com.echobox.endpoint.api.SecretRotationResponse;
import com.echobox.tenant.TenantContext;

/**
 * Die Fachlogik des Endpunktmoduls (FR-001 bis FR-004).
 *
 * <p><strong>Mandant vor Rolle (V-39):</strong> geprueft wird <strong>zuerst
 * der Mandant</strong>, <strong>danach die Rolle</strong>. Gehoert die
 * adressierte Ressource nicht dem Mandanten aus der Identitaet, endet der
 * Aufruf mit <strong>404</strong> — die Existenz fremder Objekte wird nicht
 * preisgegeben (D-02). Erst wenn die Ressource dem eigenen Mandanten gehoert,
 * wird die Rolle geprueft; passt sie nicht, ergibt der Aufruf <strong>403</strong>.
 *
 * <p><strong>Der Mandant kommt ausschliesslich aus der Identitaet</strong>
 * ({@link TenantContext}) — nie aus einem Request-Parameter und nie aus dem
 * Request-Body (D-01).
 *
 * <p>Das Secret wird verschluesselt abgelegt und <strong>nie</strong> im
 * Klartext ausgegeben (AC-001-4).
 */
@Service
public class EndpointService {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointService.class);

    /** Die Rolle, die Endpunkte verwalten darf (jeweils im eigenen Mandanten). */
    public static final String ROLE_RECEIVER_ADMIN = "receiver_admin";

    /** Die Rolle des Plattform-Betriebs — verwaltet Endpunkte im eigenen Mandanten. */
    public static final String ROLE_SERVICE_ADMIN = "service_admin";

    private final EndpointRepository repository;

    private final PayloadCipher cipher;

    private final ClockPort clock;

    private final TenantContext tenantContext;

    /**
     * Erzeugt die Fachlogik.
     *
     * @param repository    der JDBC-Adapter
     * @param cipher        die Chiffre fuer die Nutzlast und das Secret
     * @param clock         die Uhr des Dienstes
     * @param tenantContext der Mandant aus der Identitaet
     */
    public EndpointService(EndpointRepository repository,
                           PayloadCipher cipher,
                           ClockPort clock,
                           TenantContext tenantContext) {
        this.repository = repository;
        this.cipher = cipher;
        this.clock = clock;
        this.tenantContext = tenantContext;
    }

    /**
     * Registriert einen Endpunkt (FR-001).
     *
     * @param request der Request-Body
     * @return der angelegte Endpunkt
     */
    public Endpoint register(EndpointCreateRequest request) {
        requireEndpointAdministrator();
        request.validate();
        EndpointTargetUrl targetUrl = EndpointTargetUrl.of(request.targetUrl());
        PayloadCipher.Ciphertext secret = cipher.encrypt(request.secret().getBytes(StandardCharsets.UTF_8));
        Instant now = clock.now();
        Endpoint endpoint = repository.insert(tenantContext.organisationId(), targetUrl, secret.bytes(),
                secret.nonce(), request.eventTypes(), now);
        LOG.info("Endpunkt {} im Mandant {} registriert.", endpoint.endpointId(), endpoint.organisationId());
        return endpoint;
    }

    /**
     * Aendert Ziel-URL und/oder Ereignistypen (FR-001). Das Secret wird
     * <strong>nicht</strong> hier geaendert.
     *
     * @param endpointId die Endpunkt-ID
     * @param request    der Request-Body
     * @return der geaenderte Endpunkt
     */
    public Endpoint update(UUID endpointId, EndpointUpdateRequest request) {
        Endpoint endpoint = requireOwnEndpoint(endpointId);
        requireEndpointAdministrator();
        request.validate();
        EndpointTargetUrl targetUrl = request.targetUrl() == null ? null : EndpointTargetUrl.of(request.targetUrl());
        List<String> eventTypes = request.eventTypes();
        repository.update(tenantContext.organisationId(), endpointId, targetUrl, eventTypes, clock.now());
        return reload(endpointId, endpoint);
    }

    /**
     * Pausiert den Endpunkt (FR-002, D-03).
     *
     * <p>Ein pausierter Endpunkt erhaelt keine Zustellung: die offenen
     * Zustelllaeufe werden abgebrochen ({@code cancelled}), es entsteht
     * <strong>keine</strong> Warteschlange.
     *
     * @param endpointId die Endpunkt-ID
     * @return der pausierte Endpunkt
     */
    public Endpoint pause(UUID endpointId) {
        Endpoint endpoint = requireOwnEndpoint(endpointId);
        requireEndpointAdministrator();
        if (endpoint.status() == EndpointStatus.PAUSED) {
            return endpoint;
        }
        Instant now = clock.now();
        int cancelled = repository.pause(tenantContext.organisationId(), endpointId, now);
        LOG.info("Endpunkt {} pausiert — {} offene Zustelllaeufe abgebrochen.", endpointId, cancelled);
        return reload(endpointId, endpoint);
    }

    /**
     * Reaktiviert den Endpunkt (FR-002, D-03).
     *
     * <p>Es wird <strong>nichts nachgeliefert</strong> — es wird kein
     * Zustelllauf erzeugt.
     *
     * @param endpointId die Endpunkt-ID
     * @return der reaktivierte Endpunkt
     */
    public Endpoint resume(UUID endpointId) {
        Endpoint endpoint = requireOwnEndpoint(endpointId);
        requireEndpointAdministrator();
        if (endpoint.status() == EndpointStatus.ACTIVE) {
            return endpoint;
        }
        repository.updateStatus(tenantContext.organisationId(), endpointId, EndpointStatus.ACTIVE, null);
        LOG.info("Endpunkt {} reaktiviert — es wird nichts nachgeliefert.", endpointId);
        return reload(endpointId, endpoint);
    }

    /**
     * Rotiert das Secret des Endpunkts (FR-003).
     *
     * <p>Das alte Secret wird <strong>nie</strong> ausgegeben und
     * <strong>nie</strong> im Klartext gespeichert. Die Rotation wird mit
     * Zeitpunkt und ausloesender Identitaet protokolliert (AC-003-2).
     *
     * @param endpointId die Endpunkt-ID
     * @param secret     das neue Secret
     * @return der Stand der Rotation
     */
    public SecretRotationResponse rotateSecret(UUID endpointId, String secret) {
        Endpoint endpoint = requireOwnEndpoint(endpointId);
        requireSecretRotationRight();
        PayloadCipher.Ciphertext ciphertext = cipher.encrypt(secret.getBytes(StandardCharsets.UTF_8));
        int newVersion = endpoint.secretVersion() + 1;
        Instant rotatedAt = clock.now();
        String rotatedBy = tenantContext.subject();
        repository.rotateSecret(tenantContext.organisationId(), endpointId, ciphertext.bytes(), ciphertext.nonce(),
                newVersion, rotatedAt, rotatedBy);
        LOG.info("Secret des Endpunkts {} auf Version {} rotiert.", endpointId, newVersion);
        return new SecretRotationResponse(endpointId.toString(), newVersion, rotatedAt, rotatedBy);
    }

    /**
     * Laedt den Endpunkt aus dem Mandanten — ein fremder Endpunkt ergibt
     * <strong>404</strong> (D-02).
     *
     * @param endpointId die Endpunkt-ID
     * @return der Endpunkt
     */
    public Endpoint requireOwnEndpoint(UUID endpointId) {
        return repository.findById(tenantContext.organisationId(), endpointId)
                .orElseThrow(() -> {
                    LOG.info("Endpunkt {} existiert im Mandanten nicht.", endpointId);
                    return new NotFoundException();
                });
    }

    private Endpoint reload(UUID endpointId, Endpoint previous) {
        return repository.findById(tenantContext.organisationId(), endpointId).orElse(previous);
    }

    /**
     * Endpunkte verwalten duerfen {@code receiver_admin} und
     * {@code service_admin} — jeweils im <strong>eigenen</strong> Mandanten.
     */
    private void requireEndpointAdministrator() {
        if (!tenantContext.hasRole(ROLE_RECEIVER_ADMIN) && !tenantContext.hasRole(ROLE_SERVICE_ADMIN)) {
            throw new ForbiddenException();
        }
    }

    /**
     * Die Secret-Rotation ist <strong>ausschliesslich</strong> dem
     * Empfaenger-Admin gestattet — ein Entwickler und der Plattform-Betrieb
     * erhalten 403 (ADR-015 §1).
     */
    private void requireSecretRotationRight() {
        if (!tenantContext.hasRole(ROLE_RECEIVER_ADMIN)) {
            throw new ForbiddenException();
        }
    }
}
