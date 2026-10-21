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
--   * Die Grenzwerte sind entschieden (Gate 1, 2026-10-21) und stammen aus
--     der Betriebskonfiguration: F-01 (60 Versuche je Endpunkt und Minute),
--     F-02 (30 Tage je Datenart, Fristbeginn = letzter Versuch). Die Tabellen
--     speichern nur **Overrides** — NULL bedeutet "kein Override, es gilt die
--     Vorgabe" (D-12, D-14).
--
-- Mandantentrennung (ADR-002):
--   * Die Anwendung verbindet sich als echobox_app (NOLOGIN-Rolle echobox_owner
--     besitzt die Objekte). echobox_app ist kein Eigner und hat kein
--     BYPASSRLS — fuer sie greift die Row-Level-Security.
--   * Zu Beginn jeder Transaktion setzt der Dienst den Mandanten aus dem JWT:
--     SELECT app.set_tenant('m-1');  -> set_config('app.organisation_id', …, true)
--     Der Wert ist transaktionslokal und wird beim COMMIT/MODIFY zurueckgesetzt.
--     Der Kontext ist an die **Transaktion** gebunden, nicht an die
--     Verbindung: er wird **innerhalb** der Transaktion gesetzt, die auch
--     liest, immer vor der ersten Abfrage. Kein Autocommit, kein Zugriff auf
--     app.* ausserhalb einer solchen Transaktion, kein REQUIRES_NEW nach dem
--     Setzen. Ein Connection-Pool aendert daran nichts.
--   * current_setting('app.organisation_id', true) liefert NULL, wenn der
--     Mandant nicht gesetzt wurde — die Policy liefert dann KEINE Zeile
--     (fail-closed). Ein vergessener Mandantenfilter zeigt nichts.
--   * FORCE ROW LEVEL SECURITY wird bewusst NICHT gesetzt: der Eigner
--     (echobox_owner) braucht die schmalen SECURITY DEFINER-Funktionen fuer
--     Wartung, Worker-Mandantenermittlung und die mandantenuebergreifende
--     Sicht des Service-Admins. Der Eigner ist NOLOGIN und nicht an die
--     Anwendung vergeben.
--   * Der Worker arbeitet **mandantenweise**: Scheduler -> Mandantenermittlung
--     (app.tenant_ids_for_worker()) -> je Mandant app.set_tenant(...) ->
--     Beanspruchung. Ohne diesen Schritt liefert die Beanspruchung 0 Zeilen.
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
        FOREIGN KEY (replay_of) REFERENCES app.delivery_runs (delivery_run_id) ON DELETE SET NULL,
    CONSTRAINT delivery_runs_replay_of_uq
        UNIQUE (replay_of)
);

COMMENT ON TABLE  app.delivery_runs IS 'Zustelllauf: Ereignis an Endpunkt, ueber alle Versuche (FR-030, D-06).';
COMMENT ON COLUMN app.delivery_runs.event_type IS 'Denormalisiert aus dem Ereignis — durchsuchbares Metadatum ohne Join (D-11).';
COMMENT ON COLUMN app.delivery_runs.lease_owner IS 'Worker-Instanz, die den Lauf gerade beansprucht (AC-023-3).';
COMMENT ON COLUMN app.delivery_runs.lease_expires_at IS 'Ablauf der Beanspruchung. Nach Ablauf darf ein anderer Worker den Lauf uebernehmen (AC-023-4).';
COMMENT ON COLUMN app.delivery_runs.replay_of IS
    'Vorgängerlauf bei einem Replay — die Historie bleibt erhalten (AC-013-2). '
    'UNIQUE (replay_of): ein aufgegebener Zustelllauf kann **einmal** replayed werden; '
    'ein weiterer Replay verstoesst gegen die Constraint (HTTP 409, ADR-006).';
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

-- Konfigurierter Grenzwert des Endpunkt-Limits. **Zwei Zustaende** (ADR-010 §3,
-- E-02): limit_value NULL = **kein Override** — es gilt die Vorgabe aus der
-- Betriebskonfiguration (60 Versuche je Endpunkt und Minute, F-01);
-- limit_value = 0 schaltet die Begrenzung fuer diesen Endpunkt **ab**.
-- Die Spalte `enabled` ist entfallen: sie wuerde einen dritten, widerspruechlichen
-- Zustand erzeugen (ADR-010 kennt genau zwei).
CREATE TABLE IF NOT EXISTS app.endpoint_rate_limits (
    endpoint_id            UUID        PRIMARY KEY,
    organisation_id        TEXT        NOT NULL,
    limit_value            INTEGER,
    window_seconds         INTEGER     NOT NULL DEFAULT 60,
    strategy               TEXT        NOT NULL DEFAULT 'delay',
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

-- Migrationsschritt (idempotent): die entfallene Spalte `enabled` entfernen.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'endpoint_rate_limits'
          AND column_name = 'enabled'
    ) THEN
        ALTER TABLE app.endpoint_rate_limits DROP COLUMN enabled;
    END IF;
END
$$;

COMMENT ON TABLE app.endpoint_rate_limits IS
    'Konfigurierter Schutz je Endpunkt (FR-033). limit_value NULL = **kein Override** — '
    'es gilt die Vorgabe 60 Versuche je Endpunkt und Minute (F-01). limit_value = 0 = '
    'Begrenzung **abgeschaltet** (ADR-010 §3, E-02).';
COMMENT ON COLUMN app.endpoint_rate_limits.limit_value IS
    'Wirksamer Grenzwert-Override je Endpunkt. NULL = kein Override (Vorgabe 60/min, F-01); '
    '0 = Begrenzung abgeschaltet. Kein dritter Zustand — die Spalte `enabled` ist entfallen (E-02).';

-- Beobachtung: Zaehlung der Zustellversuche je Endpunkt und Zeitfenster.
-- Der Zaehler laeuft **immer**, unabhaengig vom Grenzwert (ADR-010 §1, AC-018-4).
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
    'Zaehlung der Zustellversuche je Endpunkt und Zeitfenster — laeuft immer, auch ohne '
    'Grenzwert; der Grenzwert ist belegt einstellbar statt geschaetzt (ADR-010, AC-018-4).';

-- Aufbewahrung je Mandant. **Overrides** der Betriebsvorgabe: NULL = kein
-- Override — es gilt die Vorgabe (30 Tage je Datenart, F-02).
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
    'Aufbewahrungsfristen je Mandant, getrennt fuer Ereignisse und Zustellprotokolle. '
    'NULL = **kein Override** — es gilt die Vorgabe 30 Tage je Datenart (F-02, ADR-012 §6). '
    'Anker ist der letzte Zustellversuch (last_attempt_at); fuer Ereignisse ohne Versuch '
    'der Annahmezeitpunkt (accepted_at).';

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

-- Mandant setzen. Aufruf **innerhalb** der Transaktion, die auch liest —
-- immer vor der ersten Abfrage:
--   BEGIN; SELECT app.set_tenant('m-1'); SELECT … ; COMMIT;
-- Der Wert ist transaktionslokal (is_local = true) und wird beim COMMIT
-- zurueckgesetzt. Ein leerer Mandant wird abgelehnt (fail-closed).
--
-- Der Kontext ist an die **Transaktion** gebunden, NICHT an die Verbindung
-- (ADR-002 §3, N-01): wird er in einem Servlet-Filter, beim Anlegen einer
-- Pool-Verbindung oder nach einem Commit gesetzt, ist er beim ersten Query
-- zurueckgesetzt (Autocommit) oder auf einer anderen Connection gesetzt —
-- current_setting('app.organisation_id', true) liefert dann NULL und **jede**
-- Abfrage liefert 0 Zeilen (fail-closed).
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
    'Setzt den Mandanten der aktuellen Transaktion aus dem Auftragssatz (NFR-003, AC-022-3). '
    'Transaktionsgebunden, nicht verbindungsgebunden: Aufruf innerhalb der Transaktion, die '
    'auch liest (ADR-002 §3, N-01).';

-- Mandantenermittlung fuer den Worker. Schmal zugeschnitten: liefert nur
-- Mandantenkennungen, keine Nutzlast und keine Mandantendaten. Ohne diese
-- Funktion kaeme der Worker mangels Mandantenliste nicht an seine Arbeit,
-- ohne dass BYPASSRLS vergeben werden muesste (ADR-002).
--
-- Der Worker arbeitet **mandantenweise** (ADR-002 §5, ADR-006 §3, N-02):
--   Scheduler -> app.tenant_ids_for_worker() -> je Mandant BEGIN;
--   SELECT app.set_tenant(<mandant>); SELECT … (Claim) …; COMMIT;
-- Die Claim-Abfrage ist mandantenfiltert; ohne gesetzten Mandanten liefert
-- sie unter RLS 0 Zeilen — es wuerde **nichts** zugestellt.
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
    'Liefert nur Mandantenkennungen fuer den Zustell-Worker (kein BYPASSRLS noetig, ADR-002). '
    'Der Worker setzt damit je Mandant den Kontext und arbeitet mandantenweise (ADR-006 §3).';

-- ------------------------------------------------- Mandantenuebergreifende Sicht
-- Drei schmale Eigentümerfunktionen fuer die Operationen, die **alle**
-- Mandanten sehen duerfen (`service_admin`): Endpunktliste, Totenliste und
-- Aufbewahrungsdurchsetzung (ADR-002 §7, E-01).
--
-- Sicherheitsregeln:
--   * SECURITY DEFINER, ausgefuehrt als Eigner `echobox_owner`. Der Eigner ist
--     NOLOGIN und hat **kein** BYPASSRLS; er umgeht die RLS nur, weil
--     FORCE ROW LEVEL SECURITY bewusst nicht gesetzt ist.
--   * `search_path` ist fest verdrahtet (`app, pg_temp`) — kein Aufruf kann
--     eine fremde Funktion einschleusen.
--   * EXECUTE ist **nur** der Applikationsrolle `echobox_app` gegeben; die
--     Endbenutzerrollen (`developer`, `receiver_admin`, `service_admin`) sind
--     keine Datenbankrollen — die Freigabe an `echobox_app` bedeutet **nicht**,
--     dass jede Rolle sie benutzen darf. Die Rollenpruefung bleibt in der
--     Anwendung (ADR-015).
--   * Die Funktionen liefern ausschliesslich die jeweils benoetigte
--     **Projektion**: **kein** Nutzlastklartext, **keine** Chiffre, **keine**
--     Secrets, **keine** Schluessel.
--   * Fail-closed: ohne `app.cross_tenant = 'service_admin'` liefern alle drei
--     Funktionen **keine** Zeile. Die Anwendung setzt die Variable
--     transaktionslokal, **nachdem** die JWT-Rolle `service_admin` geprueft ist:
--       SELECT set_config('app.cross_tenant', 'service_admin', true);

-- Endpunktliste mandantenuebergreifend. Liefert **nur** die Projektion des
-- Vertrags `Endpoint` — ohne Secret, ohne Chiffre.
CREATE OR REPLACE FUNCTION app.list_endpoints_cross_tenant(
    p_status            TEXT        DEFAULT NULL,
    p_limit             INTEGER     DEFAULT 50,
    p_after_updated_at  TIMESTAMPTZ DEFAULT NULL,
    p_after_endpoint_id UUID        DEFAULT NULL
) RETURNS TABLE (
    endpoint_id        UUID,
    organisation_id    TEXT,
    target_url         TEXT,
    status             TEXT,
    event_types        TEXT[],
    secret_version     INTEGER,
    secret_rotated_at  TIMESTAMPTZ,
    paused_at          TIMESTAMPTZ,
    created_at         TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = app, pg_temp
AS $$
DECLARE
    v_limit INTEGER := LEAST(GREATEST(COALESCE(p_limit, 50), 1), 200);
BEGIN
    -- Fail-closed: ohne die ausdrueckliche Freigabe keine Zeile.
    IF current_setting('app.cross_tenant', true) IS DISTINCT FROM 'service_admin' THEN
        RETURN;
    END IF;

    RETURN QUERY
    SELECT e.endpoint_id,
           e.organisation_id,
           e.target_url,
           e.status,
           COALESCE(t.event_types, ARRAY[]::TEXT[]),
           e.secret_version,
           e.secret_rotated_at,
           e.paused_at,
           e.created_at,
           e.updated_at
    FROM app.endpoints e
    LEFT JOIN LATERAL (
        SELECT array_agg(et.event_type ORDER BY et.event_type) AS event_types
        FROM app.endpoint_event_types et
        WHERE et.endpoint_id = e.endpoint_id
    ) t ON TRUE
    WHERE e.deleted_at IS NULL
      AND (p_status IS NULL OR e.status = p_status)
      AND (p_after_updated_at IS NULL
           OR (e.updated_at, e.endpoint_id) < (p_after_updated_at, p_after_endpoint_id))
    ORDER BY e.updated_at DESC, e.endpoint_id DESC
    LIMIT v_limit + 1;
END
$$;

COMMENT ON FUNCTION app.list_endpoints_cross_tenant(TEXT, INTEGER, TIMESTAMPTZ, UUID) IS
    'Endpunktliste mandantenuebergreifend fuer `service_admin` (FR-004, AC-004-2). '
    'Liefert nur die Vertragsprojektion `Endpoint` — kein Secret, keine Chiffre. '
    'Fail-closed ohne `app.cross_tenant = ''service_admin''';

-- Totenliste mandantenuebergreifend. Liefert **nur** die Projektion des
-- Vertrags `DeadDelivery` — kein Secret, keine Chiffre, kein Nutzlastinhalt.
CREATE OR REPLACE FUNCTION app.list_dead_deliveries_cross_tenant(
    p_endpoint_id           UUID        DEFAULT NULL,
    p_event_type            TEXT        DEFAULT NULL,
    p_status                TEXT        DEFAULT NULL,
    p_limit                 INTEGER     DEFAULT 50,
    p_from                  TIMESTAMPTZ DEFAULT NULL,
    p_to                    TIMESTAMPTZ DEFAULT NULL,
    p_after_created_at      TIMESTAMPTZ DEFAULT NULL,
    p_after_delivery_run_id UUID        DEFAULT NULL
) RETURNS TABLE (
    delivery_run_id      UUID,
    organisation_id      TEXT,
    event_id             UUID,
    endpoint_id          UUID,
    endpoint_url         TEXT,
    event_type           TEXT,
    status               TEXT,
    attempt_count        INTEGER,
    last_response_status INTEGER,
    last_attempt_at      TIMESTAMPTZ,
    abandoned_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = app, pg_temp
AS $$
DECLARE
    v_limit INTEGER := LEAST(GREATEST(COALESCE(p_limit, 50), 1), 200);
BEGIN
    IF current_setting('app.cross_tenant', true) IS DISTINCT FROM 'service_admin' THEN
        RETURN;
    END IF;

    RETURN QUERY
    SELECT d.delivery_run_id,
           d.organisation_id,
           d.event_id,
           d.endpoint_id,
           ep.target_url,
           d.event_type,
           d.status,
           d.attempt_count,
           d.last_response_status,
           d.last_attempt_at,
           d.abandoned_at,
           d.created_at
    FROM app.delivery_runs d
    LEFT JOIN app.endpoints ep ON ep.endpoint_id = d.endpoint_id
    WHERE d.deleted_at IS NULL
      AND (p_status IS NULL OR d.status = p_status)
      AND (p_endpoint_id IS NULL OR d.endpoint_id = p_endpoint_id)
      AND (p_event_type IS NULL OR d.event_type = p_event_type)
      AND (p_from IS NULL OR COALESCE(d.abandoned_at, d.created_at) >= p_from)
      AND (p_to IS NULL OR COALESCE(d.abandoned_at, d.created_at) < p_to)
      AND (p_after_created_at IS NULL
           OR (d.created_at, d.delivery_run_id) < (p_after_created_at, p_after_delivery_run_id))
    ORDER BY d.created_at DESC, d.delivery_run_id DESC
    LIMIT v_limit + 1;
END
$$;

COMMENT ON FUNCTION app.list_dead_deliveries_cross_tenant(UUID, TEXT, TEXT, INTEGER, TIMESTAMPTZ, TIMESTAMPTZ, TIMESTAMPTZ, UUID) IS
    'Totenliste mandantenuebergreifend fuer `service_admin` (FR-031, AC-016-1). '
    'Liefert nur die Vertragsprojektion `DeadDelivery` — kein Secret, kein Nutzlastinhalt. '
    'Fail-closed ohne `app.cross_tenant = ''service_admin''';

-- Aufbewahrungsdurchsetzung mandantenuebergreifend.
--
-- Mechanik (ADR-012):
--   * Anker je Datensatz: letzter Zustellversuch (`last_attempt_at`); fuer
--     Ereignisse ohne Versuch der Annahmezeitpunkt (`accepted_at`).
--   * Zwei Stufen: **markieren** (`deleted_at` setzen, sofort unsichtbar) und
--     **Purge** (physische Loeschung nach der Karenzzeit).
--   * Kaskade: das Loeschen eines Ereignisses nimmt seine Zustelllaeufe und
--     Versuche **mit**; die Protokollfrist gilt nur fuer Laeufe, deren
--     Ereignis noch steht.
--   * Geltungsbereich: alle Mandanten; je Mandant die angewandte Frist
--     (Override aus `retention_settings`, sonst die Vorgabe).
--   * `dry_run` aendert nichts; die Zaehler weisen dann die vorgemerkten
--     Mengen aus.
--
-- Diese Funktion ist die **einzige** schreibende mandantenuebergreifende
-- Funktion. Sie schreibt ausschliesslich `deleted_at` und loescht im Purge —
-- sie kann keine fachlichen Daten anlegen, aendern oder loeschen
-- (ADR-002 §7).
CREATE OR REPLACE FUNCTION app.enforce_retention_cross_tenant(
    p_dry_run                 BOOLEAN DEFAULT TRUE,
    p_event_retention_days    INTEGER DEFAULT NULL,
    p_delivery_retention_days INTEGER DEFAULT NULL,
    p_purge_delay_days        INTEGER DEFAULT NULL
) RETURNS TABLE (
    organisation_id         TEXT,
    event_retention_days    INTEGER,
    delivery_retention_days INTEGER,
    event_cutoff            TIMESTAMPTZ,
    delivery_cutoff         TIMESTAMPTZ,
    marked_events           BIGINT,
    marked_delivery_runs    BIGINT,
    marked_attempts         BIGINT,
    purged_events           BIGINT,
    purged_delivery_runs    BIGINT,
    purged_attempts         BIGINT
)
LANGUAGE plpgsql
SECURITY DEFINER
VOLATILE
SET search_path = app, pg_temp
AS $$
DECLARE
    v_purge_delay_days    INTEGER := COALESCE(p_purge_delay_days, 0);
    v_purge_before        TIMESTAMPTZ;
    v_org                 TEXT;
    v_event_days          INTEGER;
    v_delivery_days       INTEGER;
    v_event_cutoff        TIMESTAMPTZ;
    v_delivery_cutoff     TIMESTAMPTZ;
    v_mark_event_ids      UUID[];
    v_mark_run_ids        UUID[];
    v_purge_event_ids     UUID[];
    v_purge_run_ids       UUID[];
    v_purge_attempt_ids   UUID[];
    v_marked_events       BIGINT;
    v_marked_runs         BIGINT;
    v_marked_attempts     BIGINT;
    v_purged_events       BIGINT;
    v_purged_runs         BIGINT;
    v_purged_attempts     BIGINT;
BEGIN
    IF current_setting('app.cross_tenant', true) IS DISTINCT FROM 'service_admin' THEN
        RETURN;
    END IF;

    v_purge_before := now() - make_interval(days => v_purge_delay_days);

    FOR v_org IN
        SELECT rs.organisation_id FROM app.retention_settings rs
        UNION
        SELECT ev.organisation_id FROM app.events ev
        UNION
        SELECT dr.organisation_id FROM app.delivery_runs dr
        UNION
        SELECT ep.organisation_id FROM app.endpoints ep
    LOOP
        -- Angewandte Fristen: Override je Mandant, sonst die Vorgabe.
        -- (Aggregat, damit der Fall "kein Override" nicht ins Leere laeuft.)
        SELECT COALESCE(max(rs.event_retention_days), p_event_retention_days),
               COALESCE(max(rs.delivery_retention_days), p_delivery_retention_days)
        INTO v_event_days, v_delivery_days
        FROM app.retention_settings rs
        WHERE rs.organisation_id = v_org;

        v_event_cutoff    := CASE WHEN v_event_days IS NULL THEN NULL
                                  ELSE now() - make_interval(days => v_event_days) END;
        v_delivery_cutoff := CASE WHEN v_delivery_days IS NULL THEN NULL
                                  ELSE now() - make_interval(days => v_delivery_days) END;

        -- (1) Ereignisse markieren: Anker = letzter Versuch, sonst Annahme.
        SELECT array_agg(e.event_id) INTO v_mark_event_ids
        FROM app.events e
        WHERE e.organisation_id = v_org
          AND e.deleted_at IS NULL
          AND v_event_cutoff IS NOT NULL
          AND COALESCE((SELECT max(d.last_attempt_at)
                          FROM app.delivery_runs d
                          WHERE d.event_id = e.event_id
                            AND d.organisation_id = e.organisation_id),
                       e.accepted_at) < v_event_cutoff;
        v_marked_events := COALESCE(array_length(v_mark_event_ids, 1), 0);

        -- (2) Zustelllaeufe markieren: eigene Frist **oder** Kaskade vom
        --     markierten (bzw. bereits markierten) Ereignis.
        SELECT array_agg(d.delivery_run_id) INTO v_mark_run_ids
        FROM app.delivery_runs d
        WHERE d.organisation_id = v_org
          AND d.deleted_at IS NULL
          AND (   (v_delivery_cutoff IS NOT NULL
                   AND d.last_attempt_at IS NOT NULL
                   AND d.last_attempt_at < v_delivery_cutoff)
               OR d.event_id = ANY(v_mark_event_ids)
               OR d.event_id IN (SELECT ev.event_id
                                 FROM app.events ev
                                 WHERE ev.organisation_id = v_org
                                   AND ev.deleted_at IS NOT NULL));
        v_marked_runs := COALESCE(array_length(v_mark_run_ids, 1), 0);

        -- (3) Versuche zaehlen: sie verlieren mit ihrem Lauf die Sichtbarkeit.
        SELECT count(*) INTO v_marked_attempts
        FROM app.delivery_attempts a
        WHERE a.delivery_run_id = ANY(v_mark_run_ids);

        -- (4) Purge: physisch loeschen, was laenger als die Karenzzeit markiert
        --     ist. Versuche zuerst, dann Laeufe, dann Ereignisse.
        SELECT array_agg(a.attempt_id) INTO v_purge_attempt_ids
        FROM app.delivery_attempts a
        JOIN app.delivery_runs d ON d.delivery_run_id = a.delivery_run_id
        WHERE d.organisation_id = v_org
          AND d.deleted_at IS NOT NULL
          AND d.deleted_at <= v_purge_before;

        SELECT array_agg(d.delivery_run_id) INTO v_purge_run_ids
        FROM app.delivery_runs d
        WHERE d.organisation_id = v_org
          AND d.deleted_at IS NOT NULL
          AND d.deleted_at <= v_purge_before;

        SELECT array_agg(e.event_id) INTO v_purge_event_ids
        FROM app.events e
        WHERE e.organisation_id = v_org
          AND e.deleted_at IS NOT NULL
          AND e.deleted_at <= v_purge_before
          AND NOT EXISTS (SELECT 1 FROM app.delivery_runs d
                          WHERE d.event_id = e.event_id
                            AND (d.deleted_at IS NULL OR d.deleted_at > v_purge_before));

        v_purged_attempts := COALESCE(array_length(v_purge_attempt_ids, 1), 0);
        v_purged_runs     := COALESCE(array_length(v_purge_run_ids, 1), 0);
        v_purged_events   := COALESCE(array_length(v_purge_event_ids, 1), 0);

        IF NOT p_dry_run THEN
            IF v_marked_events > 0 THEN
                UPDATE app.events SET deleted_at = now()
                WHERE event_id = ANY(v_mark_event_ids) AND deleted_at IS NULL;
            END IF;
            IF v_marked_runs > 0 THEN
                UPDATE app.delivery_runs SET deleted_at = now()
                WHERE delivery_run_id = ANY(v_mark_run_ids) AND deleted_at IS NULL;
            END IF;
            IF v_purged_attempts > 0 THEN
                DELETE FROM app.delivery_attempts WHERE attempt_id = ANY(v_purge_attempt_ids);
            END IF;
            IF v_purged_runs > 0 THEN
                DELETE FROM app.delivery_runs WHERE delivery_run_id = ANY(v_purge_run_ids);
            END IF;
            IF v_purged_events > 0 THEN
                DELETE FROM app.events WHERE event_id = ANY(v_purge_event_ids);
            END IF;
        END IF;

        organisation_id         := v_org;
        event_retention_days    := v_event_days;
        delivery_retention_days := v_delivery_days;
        event_cutoff            := v_event_cutoff;
        delivery_cutoff         := v_delivery_cutoff;
        marked_events           := v_marked_events;
        marked_delivery_runs    := v_marked_runs;
        marked_attempts         := v_marked_attempts;
        purged_events           := v_purged_events;
        purged_delivery_runs    := v_purged_runs;
        purged_attempts         := v_purged_attempts;
        RETURN NEXT;
    END LOOP;
END
$$;

COMMENT ON FUNCTION app.enforce_retention_cross_tenant(BOOLEAN, INTEGER, INTEGER, INTEGER) IS
    'Aufbewahrungsdurchsetzung mandantenuebergreifend fuer `service_admin` (FR-031, AC-016-5). '
    'Zwei Fristen, Anker `last_attempt_at`, zwei Stufen (markieren, dann Purge nach der '
    'Karenzzeit), Kaskade Ereignis -> Laeufe -> Versuche. Einzige schreibende '
    'mandantenuebergreifende Funktion: sie schreibt ausschliesslich `deleted_at` (ADR-002 §7). '
    'Fail-closed ohne `app.cross_tenant = ''service_admin''';

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
ALTER FUNCTION app.list_endpoints_cross_tenant(TEXT, INTEGER, TIMESTAMPTZ, UUID) OWNER TO echobox_owner;
ALTER FUNCTION app.list_dead_deliveries_cross_tenant(UUID, TEXT, TEXT, INTEGER, TIMESTAMPTZ, TIMESTAMPTZ, TIMESTAMPTZ, UUID) OWNER TO echobox_owner;
ALTER FUNCTION app.enforce_retention_cross_tenant(BOOLEAN, INTEGER, INTEGER, INTEGER) OWNER TO echobox_owner;

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

-- Kein Bestandteil darf per PUBLIC ausfuehrbar sein (PostgreSQL gibt EXECUTE
-- auf Funktionen standardmaessig an PUBLIC). Jede Freigabe ist **explizit**.
REVOKE ALL ON FUNCTION app.touch_updated_at() FROM PUBLIC;
REVOKE ALL ON FUNCTION app.set_tenant(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION app.tenant_ids_for_worker() FROM PUBLIC;
REVOKE ALL ON FUNCTION app.list_endpoints_cross_tenant(TEXT, INTEGER, TIMESTAMPTZ, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION app.list_dead_deliveries_cross_tenant(UUID, TEXT, TEXT, INTEGER, TIMESTAMPTZ, TIMESTAMPTZ, TIMESTAMPTZ, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION app.enforce_retention_cross_tenant(BOOLEAN, INTEGER, INTEGER, INTEGER) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION app.set_tenant(TEXT) TO echobox_app, echobox_worker;
GRANT EXECUTE ON FUNCTION app.tenant_ids_for_worker() TO echobox_worker;

-- Die mandantenuebergreifende Sicht des Service-Admins: EXECUTE **nur** fuer
-- die Applikationsrolle. Der Worker bekommt sie **nicht**; die Endbenutzer-
-- rollen (`developer`, `receiver_admin`, `service_admin`) sind keine
-- Datenbankrollen — die Rollenpruefung bleibt in der Anwendung (ADR-015).
-- Ohne `app.cross_tenant = 'service_admin'` liefern die Funktionen keine
-- Zeile (fail-closed).
GRANT EXECUTE ON FUNCTION app.list_endpoints_cross_tenant(TEXT, INTEGER, TIMESTAMPTZ, UUID) TO echobox_app;
GRANT EXECUTE ON FUNCTION app.list_dead_deliveries_cross_tenant(UUID, TEXT, TEXT, INTEGER, TIMESTAMPTZ, TIMESTAMPTZ, TIMESTAMPTZ, UUID) TO echobox_app;
GRANT EXECUTE ON FUNCTION app.enforce_retention_cross_tenant(BOOLEAN, INTEGER, INTEGER, INTEGER) TO echobox_app;

COMMIT;
