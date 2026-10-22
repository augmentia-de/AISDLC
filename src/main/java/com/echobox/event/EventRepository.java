package com.echobox.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.echobox.common.SqlParameters;
import com.echobox.tenant.TenantTransactionRunner;

/**
 * Der JDBC-Adapter des Ereignismoduls.
 *
 * <p><strong>Mandant vor allem anderen:</strong> jeder Zugriff laeuft ueber
 * {@link TenantTransactionRunner} — der Mandant wird <strong>innerhalb</strong>
 * der Transaktion gesetzt, die auch liest (ADR-002 §3). Zusaetzlich traegt
 * <strong>jede</strong> Abfrage den Mandanten <strong>explizit</strong>: die
 * Datenbank erzwingt die Trennung durch RLS, die Anwendung filtert
 * <strong>zusaetzlich</strong> (NFR-003).
 *
 * <p>Die Nutzlast wird <strong>ausschliesslich verschluesselt</strong>
 * gespeichert: {@code payload_ciphertext}, {@code payload_nonce} und
 * {@code payload_fingerprint}. Ein Klartextanteil entsteht nicht (FR-012).
 */
@Repository
public class EventRepository {

    private static final String SELECT_EVENT = """
            SELECT event_id, organisation_id, event_type, idempotency_key, payload_fingerprint,
                   payload_is_pre_encrypted, payload_media_type, payload_size_bytes, status,
                   accepted_at, created_at, updated_at
            FROM app.events
            WHERE organisation_id = :organisationId
              AND event_id = :eventId
              AND deleted_at IS NULL
            """;

    private static final String SELECT_EVENT_BY_KEY = """
            SELECT event_id, organisation_id, event_type, idempotency_key, payload_fingerprint,
                   payload_is_pre_encrypted, payload_media_type, payload_size_bytes, status,
                   accepted_at, created_at, updated_at
            FROM app.events
            WHERE organisation_id = :organisationId
              AND idempotency_key = :idempotencyKey
              AND deleted_at IS NULL
            """;

    private static final String INSERT_EVENT = """
            INSERT INTO app.events
                (event_id, organisation_id, event_type, idempotency_key, payload_ciphertext, payload_nonce,
                 payload_key_version, payload_fingerprint, payload_is_pre_encrypted, payload_media_type,
                 payload_size_bytes, status, accepted_at, created_at, updated_at)
            VALUES
                (:eventId, :organisationId, :eventType, :idempotencyKey, :payloadCiphertext, :payloadNonce,
                 :payloadKeyVersion, :payloadFingerprint, :payloadIsPreEncrypted, :payloadMediaType,
                 :payloadSizeBytes, :status, :acceptedAt, :createdAt, :updatedAt)
            ON CONFLICT (organisation_id, idempotency_key) DO NOTHING
            """;

    private static final String INSERT_DELIVERY_RUN = """
            INSERT INTO app.delivery_runs
                (delivery_run_id, organisation_id, event_id, endpoint_id, event_type, status,
                 attempt_count, max_attempts, next_attempt_at, created_at, updated_at)
            VALUES
                (:deliveryRunId, :organisationId, :eventId, :endpointId, :eventType, :status,
                 0, :maxAttempts, :nextAttemptAt, :createdAt, :updatedAt)
            """;

    /**
     * Die Nutzlast des Ereignisses in ihrer gespeicherten Form — die Chiffre
     * und ihre Nonce (ADR-003).
     *
     * <p>Der Zustellzyklus entschluesselt die Nutzlast unmittelbar vor dem
     * Versuch; bei vorverschluesselten Ereignissen (FR-013) wird die Chiffre
     * unveraendert weitergereicht.
     */
    private static final String SELECT_PAYLOAD = """
            SELECT event_id, organisation_id, payload_ciphertext, payload_nonce,
                   payload_is_pre_encrypted, payload_media_type, accepted_at
            FROM app.events
            WHERE organisation_id = :organisationId
              AND event_id = :eventId
              AND deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;

    private final TenantTransactionRunner tenantRunner;

    /**
     * Erzeugt den Adapter.
     *
     * @param jdbcTemplate die JDBC-Vorlage
     * @param tenantRunner der Mandantentransaktions-Runner
     */
    public EventRepository(JdbcTemplate jdbcTemplate, TenantTransactionRunner tenantRunner) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.tenantRunner = tenantRunner;
    }

    /**
     * Liest das Ereignis zum Idempotenzschluessel.
     *
     * @param organisationId der Mandant
     * @param idempotencyKey der Idempotenzschluessel
     * @return das Ereignis, wenn es existiert
     */
    public Optional<Event> findByIdempotencyKey(String organisationId, String idempotencyKey) {
        return tenantRunner.execute(organisationId, () -> {
            List<Event> rows = jdbc.query(SELECT_EVENT_BY_KEY, SqlParameters.source()
                    .addValue("organisationId", organisationId)
                    .addValue("idempotencyKey", idempotencyKey), EVENT_MAPPER);
            return rows.stream().findFirst();
        });
    }

    /**
     * Liest das Ereignis zur Ereignis-ID.
     *
     * @param organisationId der Mandant
     * @param eventId        die Ereignis-ID
     * @return das Ereignis, wenn es im Mandanten existiert
     */
    public Optional<Event> findById(String organisationId, UUID eventId) {
        return tenantRunner.execute(organisationId, () -> {
            List<Event> rows = jdbc.query(SELECT_EVENT, SqlParameters.source()
                    .addValue("organisationId", organisationId)
                    .addValue("eventId", eventId), EVENT_MAPPER);
            return rows.stream().findFirst();
        });
    }

    /**
     * Legt das Ereignis an — <strong>wenn</strong> der Idempotenzschluessel im
     * Mandanten noch frei ist.
     *
     * <p>Der Wettbewerb um denselben Schluessel wird von der Datenbank
     * entschieden ({@code UNIQUE (organisation_id, idempotency_key)}): unter
     * keinen Umstaenden kann ein zweites Ereignis mit demselben Schluessel
     * entstehen (ADR-007).
     *
     * @param organisationId   der Mandant
     * @param eventId          die Ereignis-ID
     * @param eventType        der Ereignistyp
     * @param idempotencyKey   der Idempotenzschluessel
     * @param payloadCiphertext die Chiffre der Nutzlast
     * @param payloadNonce     die Nonce der Chiffre
     * @param fingerprint      der Fingerprint der Nutzlast
     * @param preEncrypted     ob die Bytes bereits verschluesselt eingeliefert wurden
     * @param payloadMediaType der Medientyp der Nutzlast
     * @param payloadSizeBytes die Groesse der Nutzlastbytes
     * @param status           der Zustand des Ereignisses
     * @param acceptedAt       der Annahmezeitpunkt
     * @return {@code true}, wenn das Ereignis angelegt wurde
     */
    public boolean insertIfAbsent(String organisationId,
                                  UUID eventId,
                                  String eventType,
                                  String idempotencyKey,
                                  byte[] payloadCiphertext,
                                  byte[] payloadNonce,
                                  byte[] fingerprint,
                                  boolean preEncrypted,
                                  String payloadMediaType,
                                  long payloadSizeBytes,
                                  String status,
                                  Instant acceptedAt) {
        return tenantRunner.execute(organisationId, () -> jdbc.update(INSERT_EVENT, SqlParameters.source()
                .addValue("eventId", eventId)
                .addValue("organisationId", organisationId)
                .addValue("eventType", eventType)
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("payloadCiphertext", payloadCiphertext)
                .addValue("payloadNonce", payloadNonce)
                .addValue("payloadKeyVersion", 1)
                .addValue("payloadFingerprint", fingerprint)
                .addValue("payloadIsPreEncrypted", preEncrypted)
                .addValue("payloadMediaType", payloadMediaType)
                .addValue("payloadSizeBytes", payloadSizeBytes)
                .addValue("status", status)
                .addValue("acceptedAt", acceptedAt)
                .addValue("createdAt", acceptedAt)
                .addValue("updatedAt", acceptedAt)) == 1);
    }

    /**
     * Legt einen Zustelllauf an — in <strong>derselben</strong> Transaktion wie
     * das Ereignis (transaktionales Outbox-Muster, D-04).
     *
     * @param organisationId der Mandant
     * @param deliveryRunId  die Zustelllauf-ID
     * @param eventId        die Ereignis-ID
     * @param endpointId     der Endpunkt (nullable bei {@code no_subscriber})
     * @param eventType      der Ereignistyp
     * @param status         der Zustand des Laufs
     * @param maxAttempts    die Versuchsgrenze (nullable = keine Grenze)
     * @param nextAttemptAt  der naechste Versuchszeitpunkt (nullable)
     * @param now            der Zeitpunkt der Anlage
     */
    public void insertDeliveryRun(String organisationId,
                                  UUID deliveryRunId,
                                  UUID eventId,
                                  UUID endpointId,
                                  String eventType,
                                  String status,
                                  Integer maxAttempts,
                                  Instant nextAttemptAt,
                                  Instant now) {
        tenantRunner.execute(organisationId, () -> {
            jdbc.update(INSERT_DELIVERY_RUN, SqlParameters.source()
                    .addValue("deliveryRunId", deliveryRunId)
                    .addValue("organisationId", organisationId)
                    .addValue("eventId", eventId)
                    .addValue("endpointId", endpointId)
                    .addValue("eventType", eventType)
                    .addValue("status", status)
                    .addValue("maxAttempts", maxAttempts)
                    .addValue("nextAttemptAt", nextAttemptAt)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now));
            return null;
        });
    }

    /**
     * Liest die gespeicherte Nutzlast eines Ereignisses — die Chiffre und ihre
     * Nonce.
     *
     * <p>Die Entschluesselung gehoert **nicht** hierher: sie ist Sache des
     * Zustellzyklus, der die Bytes unmittelbar vor dem Versuch braucht
     * (ADR-003). Ein vorverschluesseltes Ereignis (FR-013) wird unveraendert
     * weitergereicht.
     *
     * @param organisationId der Mandant
     * @param eventId        die Ereignis-ID
     * @return die gespeicherte Nutzlast, wenn das Ereignis im Mandanten existiert
     */
    public Optional<PayloadMaterial> findPayload(String organisationId, UUID eventId) {
        return tenantRunner.execute(organisationId, () -> {
            List<PayloadMaterial> rows = jdbc.query(SELECT_PAYLOAD, SqlParameters.source()
                    .addValue("organisationId", organisationId)
                    .addValue("eventId", eventId), (rs, rowNum) -> new PayloadMaterial(
                    rs.getObject("event_id", UUID.class),
                    rs.getString("organisation_id"),
                    rs.getBytes("payload_ciphertext"),
                    rs.getBytes("payload_nonce"),
                    rs.getBoolean("payload_is_pre_encrypted"),
                    rs.getString("payload_media_type"),
                    rs.getTimestamp("accepted_at").toInstant()));            return rows.stream().findFirst();
        });
    }

    private static final org.springframework.jdbc.core.RowMapper<Event> EVENT_MAPPER =
            (rs, rowNum) -> mapEvent(rs);

    private static Event mapEvent(ResultSet rs) throws SQLException {
        return new Event(
                rs.getObject("event_id", UUID.class),
                rs.getString("organisation_id"),
                rs.getString("event_type"),
                rs.getString("idempotency_key"),
                rs.getBytes("payload_fingerprint"),
                rs.getBoolean("payload_is_pre_encrypted")
                        ? PayloadEncoding.PRE_ENCRYPTED : PayloadEncoding.PLAINTEXT,
                rs.getString("payload_media_type"),
                rs.getLong("payload_size_bytes"),
                rs.getString("status"),
                0,
                rs.getTimestamp("accepted_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    /**
     * Die gespeicherte Nutzlast eines Ereignisses — die Chiffre und ihre Nonce.
     *
     * @param eventId         die Ereignis-ID
     * @param organisationId  der besitzende Mandant
     * @param ciphertext      die Chiffre der Nutzlast
     * @param nonce           die Nonce der Chiffre
     * @param preEncrypted    ob die Bytes bereits verschluesselt eingeliefert wurden
     * @param mediaType       der Medientyp der Nutzlast
     * @param acceptedAt      der Annahmezeitpunkt
     */
    public record PayloadMaterial(UUID eventId,
                                  String organisationId,
                                  byte[] ciphertext,
                                  byte[] nonce,
                                  boolean preEncrypted,
                                  String mediaType,
                                  Instant eventAcceptedAt) {

        /**
         * Erzeugt das Material mit kopierten Feldern.
         *
         * @param eventId         die Ereignis-ID
         * @param organisationId  der besitzende Mandant
         * @param ciphertext      die Chiffre der Nutzlast
         * @param nonce           die Nonce der Chiffre
         * @param preEncrypted    ob die Bytes bereits verschluesselt eingeliefert wurden
         * @param mediaType       der Medientyp der Nutzlast
         * @param eventAcceptedAt der Annahmezeitpunkt
         */
        public PayloadMaterial {
            ciphertext = ciphertext == null ? new byte[0] : ciphertext.clone();
            nonce = nonce == null ? new byte[0] : nonce.clone();
        }
    }
}
