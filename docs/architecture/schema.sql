-- ============================================================================
-- EchoBox — Ereignis-Briefkasten mit Zustellgarantie
-- PostgreSQL-Schema (kanonisch)
--
-- Diese Datei ist das KANONISCHE Datenbankschema des Projekts. Eine Kopie im
-- Commit-Scope der Architekturphase (docs/architecture/schema.sql) ist damit
-- byte-identisch; ein Mirror-Test erzwingt das (ADR-016).
--
-- Regeln:
--   * Jede fachliche Tabelle traegt organisation_id und steht unter RLS.
--   * Der Mandant kommt immer aus dem Auftragssatz, nie aus einem Parameter.
--   * Die Nutzlast wird ausschliesslich verschluesselt gespeichert (ADR-003);
--     Metadaten bleiben durchsuchbar (D-11).
--   * Indizes: idempotent anlegen (CREATE INDEX IF NOT EXISTS).
--   * Keine Frist- oder Grenzwert vorbelegen, solange O-01/O-02 offen sind
--     (D-12, D-14) — die Werte kommen aus der Konfiguration.
--
-- Mandantentrennung (ADR-002):
--   * Die Anwendung verbindet sich als echobox_app (NOLOGIN-Rolle echobox_owner
--     besitzt die Objekte). echobox_app ist kein Eigner und hat kein
--     BYPASSRLS — fuer sie greift die Row-Level-Security.
--   * Zu Beginn jeder Transaktion setzt der Dienst den Mandanten aus dem JWT:
--     SELECT app.set_tenant('m-1');  -> set_config('app.organisation_id', …, true)
--     Der Wert ist transaktionslokal und wird beim COMMIT/MODIFY zurueckgesetzt.
--   * current_setting('app.organisation_id', true) liefert NULL, wenn der
--     Mandant nicht gesetzt wurde — die Policy liefert dann KEINE Zeile
--     (fail-closed). Ein vergessener Mandantenfilter zeigt nichts.
--   * FORCE ROW LEVEL SECURITY wird bewusst NICHT gesetzt: der Eigner
--     (echobox_owner) braucht die schmalen SECURITY DEFINER-Funktionen fuer
--     Wartung und Worker-Mandantenermittlung. Der Eigner ist NOLOGIN und
--     nicht an die Anwendung vergeben.
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------- Schema
CREATE SCHEMA IF NOT EXISTS app;
COMMENT ON SCHEMA app IS 'Fachlicher Bereich von EchoBox (Mandant, Endpunkte, Ereignisse, Zustellung).';

-- ---------------------------------------------------------------- Rollen
-- Die Rollen sind clusterweit und idempotent anzulegen. Sie sind NOLOGIN,
-- damit sich nur die Laufzeitkonten anmelden koennen.

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'echobox_owner') THEN
    CREATE ROLE echobox_owner NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'echobox_app') THEN
    CREATE ROLE echobox_app NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'echobox_worker') THEN
    CREATE ROLE echobox_worker NOLOGIN;
  END IF;
END
$$;

COMMENT ON ROLE echobox_owner IS 'Eigner der Schema-Objekte. NOLOGIN, kein BYPASSRLS, keine Anwendung.';
COMMENT ON ROLE echobox_app IS 'Laufzeitrolle der HTTP-Anwendung. RLS greift; setzt den Mandanten je Transaktion.';
COMMENT ON ROLE echobox_worker IS 'Laufzeitrolle des Zustell-Workers. RLS greift; arbeitet mandantenweise.';

-- ---------------------------------------------------------------- Tabellen

-- Endpunkt: eine Registrierung mit Ziel-URL, Secret und Ereignistypen.
CREATE TABLE IF NOT EXISTS app.endpoints (
    endpoint_id            UUID        PRIMARY KEY,
    organisation_id        TEXT        NOT NULL,
    target_url             TEXT        NOT NULL,
    target_host            TEXT        NOT NULL,
    secret_ciphertext      BYTEA       NOT NULL,
    secret_nonce           BYTEA       NOT NULL,
    secret_key_version     INTEGER     NOT NULL DEFAULT 1,
    secret_version         INTEGER     NOT NULL DEFAULT 1,
    secret_rotated_at      TIMESTAMPTZ,
    status                 TEXT        NOT NULL DEFAULT 'active',
    paused_at              TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    CONSTRAINT endpoints_status_ck
        CHECK (status IN ('active', 'paused')),
    CONSTRAINT endpoints_target_url_ck
        CHECK (target_url LIKE 'https://%'),
    CONSTRAINT endpoints_target_url_no_credentials_ck
        CHECK (target_url NOT LIKE '%://%@%'),
    CONSTRAINT endpoints_secret_ciphertext_ck
        CHECK (octet_length(secret_ciphertext) <= 1024),
    CONSTRAINT endpoints_secret_nonce_ck
        CHECK (octet_length(secret_nonce) = 12),
    CONSTRAINT endpoints_secret_key_version_ck
        CHECK (secret_key_version >= 1),
    CONSTRAINT endpoints_secret_version_ck
        CHECK (secret_version >= 1)
);

COMMENT ON TABLE  app.endpoints IS 'Registrierung eines Empfaenger-Endpunkts (FR-001).';
COMMENT ON COLUMN app.endpoints.target_host IS 'Aus target_url geparster Host — durchsuchbares Metadatum, keine Nutzlast.';
COMMENT ON COLUMN app.endpoints.secret_ciphertext IS 'Verschluesseltes Endpunkt-Secret (AEAD). Nie im Klartext ausgeben (AC-001-4).';
COMMENT ON COLUMN app.endpoints.secret_key_version IS 'Version des Verschluesselungsschluessels (Schluesselrotation ohne Datenmigration).';
COMMENT ON COLUMN app.endpoints.secret_version IS 'Laufende Generation des Secrets (erhoeht sich bei jeder Rotation).';
COMMENT ON COLUMN app.endpoints.deleted_at IS 'Aufbewahrung: gesetzt = logisch geloescht, nicht mehr sichtbar (D-12).';

-- Abonnierte Ereignistypen — normalisiert, damit die Abfrage
-- "welche Endpunkte abonnieren Typ X" indexierbar bleibt.
CREATE TABLE IF NOT EXISTS app.endpoint_event_types (
    endpoint_id            UUID        NOT NULL,
    organisation_id        TEXT        NOT NULL,
    event_type             TEXT        NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT endpoint_event_types_pk PRIMARY KEY (endpoint_id, event_type),
    CONSTRAINT endpoint_event_types_event_type_ck
        CHECK (char_length(event_type) BETWEEN 1 AND 200),
    CONSTRAINT endpoint_event_types_endpoint_fk
        FOREIGN KEY (endpoint_id) REFERENCES app.endpoints (endpoint_id) ON DELETE CASCADE
);

COMMENT ON TABLE app.endpoint_event_types IS 'Vom Endpunkt abonnierte Ereignistypen (FR-001).';

-- Secret-Rotationshistorie: Zeitpunkt und ausloesende Identitaet, **kein**
-- altes Secret (AC-003-2).
CREATE TABLE IF NOT EXISTS app.endpoint_secret_history (
    rotation_id            UUID        PRIMARY KEY,
    organisation_id        TEXT        NOT NULL,
    endpoint_id            UUID        NOT NULL,
    secret_version         INTEGER     NOT NULL,
    rotated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    rotated_by             TEXT        NOT NULL,
    CONSTRAINT endpoint_secret_history_endpoint_fk
        FOREIGN KEY (endpoint_id) REFERENCES app.endpoints (endpoint_id) ON DELETE CASCADE,
    CONSTRAINT endpoint_secret_history_version_ck
        CHECK (secret_version >= 1)
);

COMMENT ON TABLE app.endpoint_secret_history IS 'Nachweis der Secret-Rotation ohne Klartext (FR-003, AC-003-2).';

-- Ereignis: eine eingelieferte Nutzlast. Die Nutzlast liegt NIE im Klartext
-- in der Datenbank (FR-012, NFR-004).
CREATE TABLE IF NOT EXISTS app.events (
    event_id               UUID        PRIMARY KEY,
    organisation_id        TEXT        NOT NULL,
    event_type             TEXT        NOT NULL,
    idempotency_key        TEXT        NOT NULL,
    payload_ciphertext     BYTEA       NOT NULL,
    payload_nonce          BYTEA       NOT NULL,
    payload_key_version    INTEGER     NOT NULL DEFAULT 1,
    payload_fingerprint    BYTEA       NOT NULL,
    payload_is_pre_encrypted BOOLEAN    NOT NULL DEFAULT FALSE,
    payload_media_type     TEXT        NOT NULL DEFAULT 'application/json',
    payload_size_bytes     INTEGER     NOT NULL,
    status                 TEXT        NOT NULL DEFAULT 'accepted',
    accepted_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    CONSTRAINT events_idempotency_uq
        UNIQUE (organisation_id, idempotency_key),
    CONSTRAINT events_status_ck
        CHECK (status IN ('accepted', 'no_subscriber')),
    CONSTRAINT events_event_type_ck
        CHECK (char_length(event_type) BETWEEN 1 AND 200),
    CONSTRAINT events_idempotency_key_ck
        CHECK (char_length(idempotency_key) BETWEEN 1 AND 200),
    CONSTRAINT events_payload_size_ck
        CHECK (payload_size_bytes BETWEEN 0 AND 1048576),
    CONSTRAINT events_payload_ciphertext_ck
        CHECK (octet_length(payload_ciphertext) <= 1048592),
    CONSTRAINT events_payload_nonce_ck
        CHECK (octet_length(payload_nonce) = 12),
    CONSTRAINT events_payload_fingerprint_ck
        CHECK (octet_length(payload_fingerprint) = 32)
);

COMMENT ON TABLE  app.events IS 'Eingeliefertes Ereignis; Nutzlast ausschliesslich verschluesselt (FR-010, FR-012).';
COMMENT ON COLUMN app.events.payload_fingerprint IS
    'Gezielter HMAC-SHA256 ueber die kanonischen Nutzlastbytes. Dient ausschliesslich dem Vergleich: gleicher Idempotenzschluessel mit abweichender Nutzlast -> HTTP 409 (D-05). Kein Klartextanteil, kein Index.';
COMMENT ON COLUMN app.events.payload_is_pre_encrypted IS
    'true = die Bytes wurden bereits verschluesselt eingeliefert und werden ohne Entschlüsseln weitergereicht (FR-013).';
COMMENT ON COLUMN app.events.status IS 'accepted = mindestens ein Abnehmer; no_subscriber = Ereignis ohne Abnehmer (D-04).';
COMMENT ON COLUMN app.events.deleted_at IS 'Aufbewahrung: gesetzt = logisch geloescht (D-12).';

-- Zustelllauf: das Zustellprotokoll eines Ereignisses an einem Endpunkt.
-- Die Zeilen sind zugleich die **dauerhafte Warteschlange**: sie entstehen in
-- derselben Transaktion wie das Ereignis (transaktionales Outbox-Muster).
CREATE TABLE IF NOT EXISTS app.delivery_runs (
    delivery_run_id        UUID        PRIMARY KEY,
    organisation_id        TEXT        NOT NULL,
    event_id               UUID        NOT NULL,
    endpoint_id            UUID,
    event_type             TEXT        NOT NULL,
    status                 TEXT        NOT NULL DEFAULT 'pending',
    attempt_count          INTEGER     NOT NULL DEFAULT 0,
    max_attempts           INTEGER,
    next_attempt_at        TIMESTAMPTZ,
    last_attempt_at        TIMESTAMPTZ,
    last_response_status   INTEGER,
    last_error_code        TEXT,
    rejection_reason       TEXT,
    abandoned_at           TIMESTAMPTZ,
    replay_of              UUID,
    lease_owner            TEXT,
    lease_expires_at       TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    CONSTRAINT delivery_runs_status_ck
        CHECK (status IN ('pending', 'scheduled', 'delayed', 'sent', 'failed',
                          'abandoned', 'rejected', 'cancelled', 'no_subscriber')),
    CONSTRAINT delivery_runs_attempt_count_ck
        CHECK (attempt_count >= 0),
    CONSTRAINT delivery_runs_max_attempts_ck
        CHECK (max_attempts IS NULL OR max_attempts >= 1),
    CONSTRAINT delivery_runs_rejection_reason_ck
        CHECK (rejection_reason IS NULL OR rejection_reason IN ('permanent_rejection', 'client_error')),
    CONSTRAINT delivery_runs_no_subscriber_ck
        CHECK ((status = 'no_subscriber' AND endpoint_id IS NULL)
            OR (status <> 'no_subscriber' AND endpoint_id IS NOT NULL)),
    CONSTRAINT delivery_runs_terminal_no_next_attempt_ck
        CHECK (status NOT IN ('sent', 'abandoned', 'rejected', 'cancelled', 'no_subscriber')
            OR next_attempt_at IS NULL),
    CONSTRAINT delivery_runs_abandoned_at_ck
        CHECK (status <> 'abandoned' OR abandoned_at IS NOT NULL),
    CONSTRAINT delivery_runs_event_fk
        FOREIGN KEY (event_id) REFERENCES app.events (event_id) ON DELETE CASCADE,
    CONSTRAINT delivery_runs_endpoint_fk
        FOREIGN KEY (endpoint_id) REFERENCES app.endpoints (endpoint_id) ON DELETE CASCADE,
    CONSTRAINT delivery_runs_replay_of_fk
        FOREIGN KEY (replay_of) REFERENCES app.delivery_runs (delivery_run_id) ON DELETE SET NULL
);

COMMENT ON TABLE  app.delivery_runs IS 'Zustelllauf: Ereignis an Endpunkt, ueber alle Versuche (FR-030, D-06).';
COMMENT ON COLUMN app.delivery_runs.event_type IS 'Denormalisiert aus dem Ereignis — durchsuchbares Metadatum ohne Join (D-11).';
COMMENT ON COLUMN app.delivery_runs.lease_owner IS 'Worker-Instanz, die den Lauf gerade beansprucht (AC-023-3).';
COMMENT ON COLUMN app.delivery_runs.lease_expires_at IS 'Ablauf der Beanspruchung. Nach Ablauf darf ein anderer Worker den Lauf uebernehmen (AC-023-4).';
COMMENT ON COLUMN app.delivery_runs.replay_of IS 'Vorgängerlauf bei einem Replay — die Historie bleibt erhalten (AC-013-2).';
COMMENT ON COLUMN app.delivery_runs.next_attempt_at IS 'Naechster Versuchszeitpunkt; NULL bei endgueltigen Zustaenden (AC-015-2).';
COMMENT ON COLUMN app.delivery_runs.abandoned_at IS 'Zeitpunkt der Aufgabe — Pflichtangabe in der Totenliste (AC-016-1).';

-- Zustellversuch: ein einzelner HTTP-Versuch.
CREATE TABLE IF NOT EXISTS app.delivery_attempts (
    attempt_id             UUID        PRIMARY KEY,
    organisation_id        TEXT        NOT NULL,
    delivery_run_id        UUID        NOT NULL,
    event_id               UUID        NOT NULL,
    endpoint_id            UUID,
    event_accepted_at      TIMESTAMPTZ NOT NULL,
    attempt_number         INTEGER     NOT NULL,
    started_at             TIMESTAMPTZ NOT NULL,
    finished_at            TIMESTAMPTZ,
    duration_ms            INTEGER,
    response_status        INTEGER,
    outcome                TEXT        NOT NULL,
    error_code             TEXT,
    next_attempt_at        TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT delivery_attempts_run_number_uq
        UNIQUE (delivery_run_id, attempt_number),
    CONSTRAINT delivery_attempts_attempt_number_ck
        CHECK (attempt_number >= 1),
    CONSTRAINT delivery_attempts_outcome_ck
        CHECK (outcome IN ('success', 'retryable_failure', 'permanent_rejection',
                            'client_error', 'network_error', 'timeout', 'cancelled')),
    CONSTRAINT delivery_attempts_duration_ck
        CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT delivery_attempts_run_fk
        FOREIGN KEY (delivery_run_id) REFERENCES app.delivery_runs (delivery_run_id) ON DELETE CASCADE
);

COMMENT ON TABLE app.delivery_attempts IS 'Einzelner HTTP-Versuch eines Zustelllaufs (FR-030, D-06).';
COMMENT ON COLUMN app.delivery_attempts.attempt_id IS 'Versuchs-ID — fuer jeden HTTP-Versuch eindeutig (FR-021).';
COMMENT ON COLUMN app.delivery_attempts.event_accepted_at IS
    'Denormalisiert: Zeitpunkt der Annahme. Damit ist die Zustelllatenz (p95 von Annahme bis erfolgreicher Antwort) ohne Join berechenbar (FR-032).';

-- Konfigurierter Grenzwert des Endpunkt-Limits. Der Wert bleibt NULL, solange
-- O-01 offen ist — es wird kein Grenzwert vorbelegt (D-14).
CREATE TABLE IF NOT EXISTS app.endpoint_rate_limits (
    endpoint_id            UUID        PRIMARY KEY,
    organisation_id        TEXT        NOT NULL,
    limit_value            INTEGER,
    window_seconds         INTEGER     NOT NULL DEFAULT 60,
    strategy               TEXT        NOT NULL DEFAULT 'delay',
    enabled                BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT endpoint_rate_limits_limit_ck
        CHECK (limit_value IS NULL OR limit_value >= 0),
    CONSTRAINT endpoint_rate_limits_window_ck
        CHECK (window_seconds >= 1),
    CONSTRAINT endpoint_rate_limits_strategy_ck
        CHECK (strategy IN ('delay', 'block')),
    CONSTRAINT endpoint_rate_limits_endpoint_fk
        FOREIGN KEY (endpoint_id) REFERENCES app.endpoints (endpoint_id) ON DELETE CASCADE
);

COMMENT ON TABLE app.endpoint_rate_limits IS
    'Konfigurierter Schutz je Endpunkt (FR-033). limit_value NULL = kein Grenzwert vorbelegt, solange O-01 offen ist (D-14).';

-- Beobachtung: Zaehlung der Zustellversuche je Endpunkt und Zeitfenster.
-- Datenbasis fuer die noch offene Festlegung O-01 (AC-018-4).
CREATE TABLE IF NOT EXISTS app.endpoint_rate_limit_counters (
    endpoint_id            UUID        NOT NULL,
    organisation_id        TEXT        NOT NULL,
    window_start           TIMESTAMPTZ NOT NULL,
    attempt_count          BIGINT      NOT NULL DEFAULT 0,
    delayed_count          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT endpoint_rate_limit_counters_pk PRIMARY KEY (endpoint_id, window_start),
    CONSTRAINT endpoint_rate_limit_counters_count_ck
        CHECK (attempt_count >= 0 AND delayed_count >= 0),
    CONSTRAINT endpoint_rate_limit_counters_endpoint_fk
        FOREIGN KEY (endpoint_id) REFERENCES app.endpoints (endpoint_id) ON DELETE CASCADE
);

COMMENT ON TABLE app.endpoint_rate_limit_counters IS
    'Zaehlung der Zustellversuche je Endpunkt und Zeitfenster — Datenbasis fuer O-01 (AC-018-4).';

-- Aufbewahrung je Mandant. Keine Frist vorbelegt, solange O-02 offen ist (D-12).
CREATE TABLE IF NOT EXISTS app.retention_settings (
    organisation_id        TEXT        PRIMARY KEY,
    event_retention_days   INTEGER,
    delivery_retention_days INTEGER,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT retention_settings_event_days_ck
        CHECK (event_retention_days IS NULL OR event_retention_days >= 1),
    CONSTRAINT retention_settings_delivery_days_ck
        CHECK (delivery_retention_days IS NULL OR delivery_retention_days >= 1)
);

COMMENT ON TABLE app.retention_settings IS
    'Aufbewahrungsfristen je Mandant, getrennt fuer Ereignisse und Zustellprotokolle. NULL = keine Frist (O-02, D-12).';

-- Audit: nachvollziehbare Aktionen mit Identitaet. Ohne Nutzlastinhalt.
CREATE TABLE IF NOT EXISTS app.audit_log (
    audit_id               UUID        PRIMARY KEY,
    organisation_id        TEXT        NOT NULL,
    actor_id               TEXT        NOT NULL,
    actor_role             TEXT        NOT NULL,
    action                 TEXT        NOT NULL,
    resource_type          TEXT        NOT NULL,
    resource_id            UUID,
    details                JSONB       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT audit_log_actor_role_ck
        CHECK (actor_role IN ('developer', 'receiver_admin', 'service_admin', 'system')),
    CONSTRAINT audit_log_details_ck
        CHECK (jsonb_typeof(details) = 'object')
);

COMMENT ON TABLE app.audit_log IS 'Nachvollziehbarkeit der Aktionen (FR-003, FR-024). Ohne Nutzlastinhalt.';

-- ---------------------------------------------------------------- Trigger

CREATE OR REPLACE FUNCTION app.touch_updated_at() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS endpoints_touch_updated_at ON app.endpoints;
CREATE TRIGGER endpoints_touch_updated_at BEFORE UPDATE ON app.endpoints
    FOR EACH ROW EXECUTE FUNCTION app.touch_updated_at();

DROP TRIGGER IF EXISTS events_touch_updated_at ON app.events;
CREATE TRIGGER events_touch_updated_at BEFORE UPDATE ON app.events
    FOR EACH ROW EXECUTE FUNCTION app.touch_updated_at();

DROP TRIGGER IF EXISTS delivery_runs_touch_updated_at ON app.delivery_runs;
CREATE TRIGGER delivery_runs_touch_updated_at BEFORE UPDATE ON app.delivery_runs
    FOR EACH ROW EXECUTE FUNCTION app.touch_updated_at();

DROP TRIGGER IF EXISTS endpoint_rate_limits_touch_updated_at ON app.endpoint_rate_limits;
CREATE TRIGGER endpoint_rate_limits_touch_updated_at BEFORE UPDATE ON app.endpoint_rate_limits
    FOR EACH ROW EXECUTE FUNCTION app.touch_updated_at();

DROP TRIGGER IF EXISTS retention_settings_touch_updated_at ON app.retention_settings;
CREATE TRIGGER retention_settings_touch_updated_at BEFORE UPDATE ON app.retention_settings
    FOR EACH ROW EXECUTE FUNCTION app.touch_updated_at();

-- ---------------------------------------------------------------- Funktionen

-- Mandant setzen. Aufruf zu Beginn jeder Transaktion:
--   SELECT app.set_tenant('m-1');
-- Der Wert ist transaktionslokal (is_local = true) und wird beim COMMIT
-- zurueckgesetzt. Ein leerer Mandant wird abgelehnt (fail-closed).
CREATE OR REPLACE FUNCTION app.set_tenant(p_organisation_id TEXT) RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_organisation_id IS NULL OR btrim(p_organisation_id) = '' THEN
        RAISE EXCEPTION 'app.set_tenant: der Mandant darf nicht leer sein'
            USING ERRCODE = '22023';
    END IF;
    PERFORM set_config('app.organisation_id', p_organisation_id, true);
END
$$;

COMMENT ON FUNCTION app.set_tenant(TEXT) IS
    'Setzt den Mandanten der aktuellen Transaktion aus dem Auftragssatz (NFR-003, AC-022-3).';

-- Mandantenermittlung fuer den Worker. Schmal zugeschnitten: liefert nur
-- Mandantenkennungen, keine Nutzlast und keine Mandantendaten. Ohne diese
-- Funktion kaeme der Worker mangels Mandantenliste nicht an seine Arbeit,
-- ohne dass BYPASSRLS vergeben werden muesste (ADR-002).
CREATE OR REPLACE FUNCTION app.tenant_ids_for_worker() RETURNS TABLE (organisation_id TEXT)
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = app, pg_temp
AS $$
    SELECT DISTINCT e.organisation_id FROM app.events e
    UNION
    SELECT DISTINCT d.organisation_id FROM app.delivery_runs d
$$;

COMMENT ON FUNCTION app.tenant_ids_for_worker() IS
    'Liefert nur Mandantenkennungen fuer den Zustell-Worker (kein BYPASSRLS noetig, ADR-002).';

-- ---------------------------------------------------------------- RLS

ALTER TABLE app.endpoints ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.endpoint_event_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.endpoint_secret_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.events ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.delivery_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.delivery_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.endpoint_rate_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.endpoint_rate_limit_counters ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.retention_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.audit_log ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS endpoints_tenant_isolation ON app.endpoints;
CREATE POLICY endpoints_tenant_isolation ON app.endpoints
    USING (organisation_id = current_setting('app.organisation_id', true)
           AND deleted_at IS NULL)
    WITH CHECK (organisation_id = current_setting('app.organisation_id', true));

DROP POLICY IF EXISTS endpoint_event_types_tenant_isolation ON app.endpoint_event_types;
CREATE POLICY endpoint_event_types_tenant_isolation ON app.endpoint_event_types
    USING (organisation_id = current_setting('app.organisation_id', true))
    WITH CHECK (organisation_id = current_setting('app.organisation_id', true));

DROP POLICY IF EXISTS endpoint_secret_history_tenant_isolation ON app.endpoint_secret_history;
CREATE POLICY endpoint_secret_history_tenant_isolation ON app.endpoint_secret_history
    USING (organisation_id = current_setting('app.organisation_id', true))
    WITH CHECK (organisation_id = current_setting('app.organisation_id', true));

DROP POLICY IF EXISTS events_tenant_isolation ON app.events;
CREATE POLICY events_tenant_isolation ON app.events
    USING (organisation_id = current_setting('app.organisation_id', true)
           AND deleted_at IS NULL)
    WITH CHECK (organisation_id = current_setting('app.organisation_id', true));

DROP POLICY IF EXISTS delivery_runs_tenant_isolation ON app.delivery_runs;
CREATE POLICY delivery_runs_tenant_isolation ON app.delivery_runs
    USING (organisation_id = current_setting('app.organisation_id', true)
           AND deleted_at IS NULL)
    WITH CHECK (organisation_id = current_setting('app.organisation_id', true));

DROP POLICY IF EXISTS delivery_attempts_tenant_isolation ON app.delivery_attempts;
CREATE POLICY delivery_attempts_tenant_isolation ON app.delivery_attempts
    USING (organisation_id = current_setting('app.organisation_id', true))
    WITH CHECK (organisation_id = current_setting('app.organisation_id', true));

DROP POLICY IF EXISTS endpoint_rate_limits_tenant_isolation ON app.endpoint_rate_limits;
CREATE POLICY endpoint_rate_limits_tenant_isolation ON app.endpoint_rate_limits
    USING (organisation_id = current_setting('app.organisation_id', true))
    WITH CHECK (organisation_id = current_setting('app.organisation_id', true));

DROP POLICY IF EXISTS endpoint_rate_limit_counters_tenant_isolation ON app.endpoint_rate_limit_counters;
CREATE POLICY endpoint_rate_limit_counters_tenant_isolation ON app.endpoint_rate_limit_counters
    USING (organisation_id = current_setting('app.organisation_id', true))
    WITH CHECK (organisation_id = current_setting('app.organisation_id', true));

DROP POLICY IF EXISTS retention_settings_tenant_isolation ON app.retention_settings;
CREATE POLICY retention_settings_tenant_isolation ON app.retention_settings
    USING (organisation_id = current_setting('app.organisation_id', true))
    WITH CHECK (organisation_id = current_setting('app.organisation_id', true));

DROP POLICY IF EXISTS audit_log_tenant_isolation ON app.audit_log;
CREATE POLICY audit_log_tenant_isolation ON app.audit_log
    USING (organisation_id = current_setting('app.organisation_id', true))
    WITH CHECK (organisation_id = current_setting('app.organisation_id', true));

-- ---------------------------------------------------------------- Indizes

-- Idempotenz: UNIQUE (organisation_id, idempotency_key) ist bereits gesetzt.

CREATE INDEX IF NOT EXISTS events_tenant_accepted_at_idx
    ON app.events (organisation_id, accepted_at DESC);
CREATE INDEX IF NOT EXISTS events_tenant_type_accepted_at_idx
    ON app.events (organisation_id, event_type, accepted_at DESC);
CREATE INDEX IF NOT EXISTS events_tenant_status_idx
    ON app.events (organisation_id, status);

CREATE INDEX IF NOT EXISTS endpoint_event_types_tenant_type_idx
    ON app.endpoint_event_types (organisation_id, event_type);
CREATE INDEX IF NOT EXISTS endpoint_event_types_type_idx
    ON app.endpoint_event_types (event_type);

CREATE INDEX IF NOT EXISTS endpoint_secret_history_tenant_endpoint_idx
    ON app.endpoint_secret_history (organisation_id, endpoint_id, rotated_at DESC);

-- Zustellprotokoll: Filter ueber Endpunkt, Status, Ereignistyp, Zeitraum.
CREATE INDEX IF NOT EXISTS delivery_runs_tenant_created_at_idx
    ON app.delivery_runs (organisation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS delivery_runs_tenant_status_next_attempt_idx
    ON app.delivery_runs (organisation_id, status, next_attempt_at);
CREATE INDEX IF NOT EXISTS delivery_runs_tenant_endpoint_created_idx
    ON app.delivery_runs (organisation_id, endpoint_id, created_at DESC);
CREATE INDEX IF NOT EXISTS delivery_runs_tenant_event_idx
    ON app.delivery_runs (organisation_id, event_id);
CREATE INDEX IF NOT EXISTS delivery_runs_tenant_type_created_idx
    ON app.delivery_runs (organisation_id, event_type, created_at DESC);

-- Tote Zustellungen: die Totenliste filtert ueber Status und Zeitraum.
CREATE INDEX IF NOT EXISTS delivery_runs_dead_letter_idx
    ON app.delivery_runs (organisation_id, abandoned_at DESC)
    WHERE status = 'abandoned';

-- Worker: duenende Laeufe je Mandant beanspruchen (FOR UPDATE SKIP LOCKED).
CREATE INDEX IF NOT EXISTS delivery_runs_due_idx
    ON app.delivery_runs (organisation_id, next_attempt_at)
    WHERE status IN ('pending', 'scheduled', 'failed', 'delayed');

-- Leichenfaelle nach Worker-Absturz: ueberfaellige Beanspruchungen.
CREATE INDEX IF NOT EXISTS delivery_runs_lease_idx
    ON app.delivery_runs (lease_expires_at)
    WHERE lease_owner IS NOT NULL;

CREATE INDEX IF NOT EXISTS delivery_attempts_run_started_idx
    ON app.delivery_attempts (delivery_run_id, started_at);
CREATE INDEX IF NOT EXISTS delivery_attempts_tenant_started_idx
    ON app.delivery_attempts (organisation_id, started_at DESC);

-- Kennzahlen: Erfolgsquote und p95-Latenz je Endpunkt (FR-032).
CREATE INDEX IF NOT EXISTS delivery_attempts_endpoint_started_idx
    ON app.delivery_attempts (endpoint_id, started_at DESC)
    WHERE endpoint_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS delivery_attempts_endpoint_success_latency_idx
    ON app.delivery_attempts (endpoint_id, event_accepted_at)
    WHERE outcome = 'success';

CREATE INDEX IF NOT EXISTS endpoint_rate_limit_counters_tenant_window_idx
    ON app.endpoint_rate_limit_counters (organisation_id, window_start DESC);

CREATE INDEX IF NOT EXISTS audit_log_tenant_occurred_idx
    ON app.audit_log (organisation_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS audit_log_resource_idx
    ON app.audit_log (resource_type, resource_id);

-- ---------------------------------------------------------------- Eigentuemer
-- Die Migration laeuft unter einer Rolle, die Mitglied von echobox_owner ist
-- (z. B. ein Migrationskonto mit CREATEROLE). Die Objekte werden dem Eigner
-- uebergeben, damit die schmalen SECURITY DEFINER-Funktionen die RLS
-- umgehen koennen (ADR-002). Der Eigner ist NOLOGIN.

ALTER SCHEMA app OWNER TO echobox_owner;

ALTER TABLE app.endpoints OWNER TO echobox_owner;
ALTER TABLE app.endpoint_event_types OWNER TO echobox_owner;
ALTER TABLE app.endpoint_secret_history OWNER TO echobox_owner;
ALTER TABLE app.events OWNER TO echobox_owner;
ALTER TABLE app.delivery_runs OWNER TO echobox_owner;
ALTER TABLE app.delivery_attempts OWNER TO echobox_owner;
ALTER TABLE app.endpoint_rate_limits OWNER TO echobox_owner;
ALTER TABLE app.endpoint_rate_limit_counters OWNER TO echobox_owner;
ALTER TABLE app.retention_settings OWNER TO echobox_owner;
ALTER TABLE app.audit_log OWNER TO echobox_owner;

ALTER FUNCTION app.touch_updated_at() OWNER TO echobox_owner;
ALTER FUNCTION app.set_tenant(TEXT) OWNER TO echobox_owner;
ALTER FUNCTION app.tenant_ids_for_worker() OWNER TO echobox_owner;

-- ---------------------------------------------------------------- Rechte

GRANT USAGE ON SCHEMA app TO echobox_app, echobox_worker;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    app.endpoints,
    app.endpoint_event_types,
    app.endpoint_secret_history,
    app.events,
    app.delivery_runs,
    app.delivery_attempts,
    app.endpoint_rate_limits,
    app.endpoint_rate_limit_counters,
    app.retention_settings,
    app.audit_log
TO echobox_app;

-- Der Worker liest und schreibt Zustellungen; Endpunktkonfiguration und
-- Audit schreibt er nicht.
GRANT SELECT, INSERT, UPDATE ON
    app.endpoints,
    app.endpoint_event_types,
    app.events,
    app.delivery_runs,
    app.delivery_attempts,
    app.endpoint_rate_limits,
    app.endpoint_rate_limit_counters
TO echobox_worker;

GRANT EXECUTE ON FUNCTION app.set_tenant(TEXT) TO echobox_app, echobox_worker;
GRANT EXECUTE ON FUNCTION app.tenant_ids_for_worker() TO echobox_worker;

COMMIT;
