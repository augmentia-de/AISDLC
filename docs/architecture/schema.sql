-- ============================================================================
-- PromptGuard — PostgreSQL 16 Schema (Version 1.0.0)
-- ============================================================================
-- **Quelle der Feldtypen für den eingefrorenen API-Vertrag**
--   `docs/api/openapi.yaml` → `components.schemas`
--
-- Diese Datei ist die *Referenz* für den Vertrag: jedes Feld einer
-- Komponente im Vertrag existiert hier mit **gleichem Typ und gleicher
-- Nullbarkeit**. Abweichungen werden in `.agent/artifacts/api-review.md`
-- als Befund geführt (Schema-Abgleich-Runde).
--
-- ABWEICHUNG VOM PROZESSWEG (Befund B-01 in api-review.md):
--   Der Workflow `02-architecture.md` nennt `.agent/knowledge/schema.sql` als
--   Ziel. `.agent/knowledge/` ist für den Architecture-Orchestrator in
--   diesem Lauf **schreibgeschützt**; das projektspezifische Schema liegt
--   daher unter `docs/architecture/schema.sql` und wird beim nächsten Lauf
--   nach `.agent/knowledge/schema.sql` gespiegelt.
--
-- Mandantentrennung (SEC-002, SEC-003, ADR-002):
--   * Jede fachliche Tabelle führt `organisation_id NOT NULL`.
--   * Row-Level-Security ist die zweite Verteidigungslinie.
--   * Der Tenant-Kontext wird **pro Transaktion** gesetzt:
--         SET LOCAL app.organisation_id = '<uuid>';
--   * Ein Hintergrundjob ohne auflösbaren Tenant-Kontext wird verworfen.
--
-- Namenskonvention: Tabellen im Singular (`prompt`, `scan_run`), Spalten im
-- Singular-Snake-Case. `user` ist in PostgreSQL reserviert → `app_user`.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- Aufzählungen (CHECK-Constraints statt ENUM: Umstellung ohne Down-Migration)
-- ---------------------------------------------------------------------------
-- role           : DEVELOPER | SECURITY_ADMIN | ORG_ADMIN
-- user_status    : ACTIVE | SUSPENDED | ERASED
-- scan_status    : PENDING | SCANNING | APPROVED | FLAGGED | WAIVED | FAILED
-- scan_run_status: PENDING | RUNNING | COMPLETED | FAILED | ABORTED
-- severity       : LOW | MEDIUM | HIGH | CRITICAL
-- finding_cat    : PROMPT_INJECTION | JAILBREAK | SECRET | PII
-- finding_stage  : PRECHECK | DUPLICATE | CLASSIFICATION
-- job_status     : QUEUED | RUNNING | COMPLETED | FAILED | DEAD_LETTER
-- notif_status   : PENDING | SENDING | SENT | RETRYING | DEAD_LETTER
-- invitation_stat: PENDING | ACCEPTED | EXPIRED | REVOKED

-- ============================================================================
-- Mandant und Identität (E1)
-- ============================================================================

CREATE TABLE IF NOT EXISTS organisation (
    id                            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name                          VARCHAR(200)  NOT NULL,
    -- FR-036: Mindest-Schweregrad, ab dem ein Scan FLAGGED wird (Default MEDIUM).
    scan_threshold                VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM',
    -- FR-032 / F8 / A-08: Duplikatschwelle, organisationsweit konfigurierbar.
    duplicate_threshold           NUMERIC(4, 3) NOT NULL DEFAULT 0.900,
    duplicate_detection_enabled   BOOLEAN       NOT NULL DEFAULT TRUE,
    -- FR-015: Kapazitätsgrenzen der Tag-Vergabe.
    max_tags_per_prompt           INTEGER       NOT NULL DEFAULT 10,
    max_tags_per_organisation     INTEGER       NOT NULL DEFAULT 200,
    -- Gecachter Verifikationsstand der Plattformkonfiguration (ADR-006).
    provider_verified             BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_organisation_scan_threshold
        CHECK (scan_threshold IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_organisation_duplicate_threshold
        CHECK (duplicate_threshold >= 0 AND duplicate_threshold <= 1),
    CONSTRAINT ck_organisation_tag_limits
        CHECK (max_tags_per_prompt BETWEEN 1 AND 50
               AND max_tags_per_organisation BETWEEN 1 AND 5000)
);

CREATE TABLE IF NOT EXISTS app_user (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(320) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    display_name   VARCHAR(200) NULL,
    -- NFR-018 / US-035: Oberflächensprache; API-Feldnamen bleiben englisch.
    locale         VARCHAR(8)   NOT NULL DEFAULT 'de',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_app_user_locale CHECK (locale IN ('de', 'en'))
);

-- E-Mail ist global eindeutig (F2: ein Nutzer gehört genau einer Organisation).
CREATE UNIQUE INDEX IF NOT EXISTS ux_app_user_email_lower ON app_user (LOWER(email));

CREATE TABLE IF NOT EXISTS membership (
    user_id         UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    organisation_id UUID        NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    role            VARCHAR(32) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ  NULL,
    CONSTRAINT pk_membership PRIMARY KEY (user_id, organisation_id),
    CONSTRAINT ck_membership_role
        CHECK (role IN ('DEVELOPER', 'SECURITY_ADMIN', 'ORG_ADMIN')),
    CONSTRAINT ck_membership_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ERASED'))
);

-- F2: ein Nutzer gehört genau einer Organisation (1:1).
CREATE UNIQUE INDEX IF NOT EXISTS ux_membership_user ON membership (user_id);
CREATE INDEX IF NOT EXISTS ix_membership_org ON membership (organisation_id, role);

-- ---------------------------------------------------------------------------
-- Sitzungen, Token-Rotation, Einladungen (E1)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS refresh_token (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    organisation_id UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    -- Session-Kette: bei Replay einer rotierten Kette wird die Kette invalidiert.
    session_id      UUID         NOT NULL,
    token_hash      VARCHAR(255) NOT NULL,
    parent_token_id UUID         NULL REFERENCES refresh_token(id) ON DELETE SET NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ  NULL,
    revoked_reason  VARCHAR(64)  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ux_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS ix_refresh_token_session
    ON refresh_token (organisation_id, session_id);

CREATE TABLE IF NOT EXISTS invitation (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    email           VARCHAR(320) NOT NULL,
    role            VARCHAR(32)  NOT NULL,
    -- Nur der Hash wird gespeichert (SEC-012).
    token_hash      VARCHAR(255) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    expires_at      TIMESTAMPTZ  NOT NULL,
    accepted_at     TIMESTAMPTZ  NULL,
    created_by      UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_invitation_role
        CHECK (role IN ('DEVELOPER', 'SECURITY_ADMIN', 'ORG_ADMIN')),
    CONSTRAINT ck_invitation_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT ux_invitation_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS ix_invitation_org ON invitation (organisation_id, status);

-- FR-006: Alarmempfänger (1-n) je Organisation.
CREATE TABLE IF NOT EXISTS alert_recipient (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    email           VARCHAR(320) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ux_alert_recipient UNIQUE (organisation_id, email)
);

-- ============================================================================
-- Taxonomie (E2)
-- ============================================================================

CREATE TABLE IF NOT EXISTS category (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_category_org_name
    ON category (organisation_id, LOWER(name));

CREATE TABLE IF NOT EXISTS tag (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name            VARCHAR(60)  NOT NULL,
    -- FR-015: normalisiert (lower-case), eindeutig je Organisation.
    normalised_name VARCHAR(60)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ux_tag_org_normalised UNIQUE (organisation_id, normalised_name)
);

-- ============================================================================
-- Prompts und Versionen (E2)
-- ============================================================================

CREATE TABLE IF NOT EXISTS prompt (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id    UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    title              VARCHAR(200) NOT NULL,
    description        VARCHAR(2000) NULL,
    category_id        UUID         NOT NULL REFERENCES category(id),
    -- Denormalisierter Status der aktuellen Version (Filter/Sortierung).
    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    current_version_id UUID         NULL,
    -- FR-063: bei Löschung des Kontos wird die Autorschaft anonymisiert.
    created_by         UUID         NULL REFERENCES app_user(id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at         TIMESTAMPTZ  NULL,
    CONSTRAINT ck_prompt_status
        CHECK (status IN ('PENDING', 'SCANNING', 'APPROVED', 'FLAGGED', 'WAIVED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS ix_prompt_org_status
    ON prompt (organisation_id, status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_prompt_org_updated
    ON prompt (organisation_id, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_prompt_org_title
    ON prompt (organisation_id, LOWER(title));

-- Volltextsuche FR-020 (Prefix- und Substring-Treffer).
CREATE INDEX IF NOT EXISTS ix_prompt_search ON prompt USING GIN (
    to_tsvector('simple', COALESCE(title, '') || ' ' || COALESCE(description, ''))
);

CREATE TABLE IF NOT EXISTS prompt_version (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_id       UUID         NOT NULL REFERENCES prompt(id) ON DELETE CASCADE,
    organisation_id UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    version_no      INTEGER      NOT NULL,
    content         TEXT         NOT NULL,
    -- I-1: die Freigabe ist an den Inhalts-Hash gebunden.
    content_hash    CHAR(64)     NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    -- FR-063: Autorschaft wird bei Löschung des Kontos anonymisiert.
    created_by      UUID         NULL REFERENCES app_user(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    scanned_at      TIMESTAMPTZ  NULL,
    CONSTRAINT ck_prompt_version_status
        CHECK (status IN ('PENDING', 'SCANNING', 'APPROVED', 'FLAGGED', 'WAIVED', 'FAILED')),
    CONSTRAINT ux_prompt_version UNIQUE (prompt_id, version_no)
);

CREATE INDEX IF NOT EXISTS ix_prompt_version_prompt
    ON prompt_version (prompt_id, version_no DESC);
CREATE INDEX IF NOT EXISTS ix_prompt_version_org_hash
    ON prompt_version (organisation_id, content_hash);

-- Zirkuläre Referenz prompt.current_version_id -> prompt_version.id
ALTER TABLE prompt DROP CONSTRAINT IF EXISTS fk_prompt_current_version;
ALTER TABLE prompt
    ADD CONSTRAINT fk_prompt_current_version
    FOREIGN KEY (current_version_id) REFERENCES prompt_version(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS prompt_tag (
    prompt_id       UUID NOT NULL REFERENCES prompt(id) ON DELETE CASCADE,
    tag_id          UUID NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    CONSTRAINT pk_prompt_tag PRIMARY KEY (prompt_id, tag_id)
);

CREATE INDEX IF NOT EXISTS ix_prompt_tag_tag ON prompt_tag (organisation_id, tag_id);

-- ============================================================================
-- Prüfläufe, Fundstellen, Duplikate (E4)
-- ============================================================================

CREATE TABLE IF NOT EXISTS scan_run (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    prompt_version_id   UUID         NOT NULL REFERENCES prompt_version(id) ON DELETE CASCADE,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    started_at          TIMESTAMPTZ  NULL,
    finished_at         TIMESTAMPTZ  NULL,
    duration_ms         INTEGER      NULL,
    attempt_count       INTEGER      NOT NULL DEFAULT 0,
    -- Angewandte Schwellenwerte (FR-036, FR-032) — Nachweis des Urteils.
    threshold           VARCHAR(16)  NULL,
    duplicate_threshold NUMERIC(4, 3) NULL,
    -- Technischer Fehlertext **ohne** Prompt-Inhalt (FR-062).
    last_error          VARCHAR(500) NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_scan_run_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'ABORTED'))
);

CREATE INDEX IF NOT EXISTS ix_scan_run_version
    ON scan_run (organisation_id, prompt_version_id, created_at DESC);

CREATE TABLE IF NOT EXISTS scan_finding (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id  UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    scan_id          UUID         NOT NULL REFERENCES scan_run(id) ON DELETE CASCADE,
    category         VARCHAR(32)  NOT NULL,
    severity         VARCHAR(16)  NOT NULL,
    -- FR-037: nur maskiert; der übermittelte Originalinhalt bleibt unverändert (F1).
    redacted_excerpt VARCHAR(500) NOT NULL,
    start_offset     INTEGER      NOT NULL,
    end_offset       INTEGER      NOT NULL,
    -- `line` und `column` sind im Vertrag die JSON-Felder; in SQL sind sie reservierte
    -- Wörter, deshalb die Spalten `line_no` / `column_no` (Mapping in der Persistenzschicht).
    line_no          INTEGER      NOT NULL,
    column_no        INTEGER      NOT NULL,
    explanation      TEXT         NOT NULL,
    detector         VARCHAR(64)  NOT NULL,
    stage            VARCHAR(16)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_scan_finding_category
        CHECK (category IN ('PROMPT_INJECTION', 'JAILBREAK', 'SECRET', 'PII')),
    CONSTRAINT ck_scan_finding_severity
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_scan_finding_stage
        CHECK (stage IN ('PRECHECK', 'DUPLICATE', 'CLASSIFICATION'))
);

CREATE INDEX IF NOT EXISTS ix_scan_finding_scan
    ON scan_finding (organisation_id, scan_id, severity);

-- Beratende Duplikatwarnung (FR-032) — nur innerhalb der eigenen Organisation.
CREATE TABLE IF NOT EXISTS duplicate_match (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id    UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    scan_id            UUID         NOT NULL REFERENCES scan_run(id) ON DELETE CASCADE,
    matched_prompt_id  UUID         NOT NULL REFERENCES prompt(id) ON DELETE CASCADE,
    matched_version_no INTEGER      NOT NULL,
    title              VARCHAR(200) NOT NULL,
    score              NUMERIC(4, 3) NOT NULL,
    method             VARCHAR(32)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_duplicate_match_method
        CHECK (method IN ('VECTOR', 'NORMALIZED_TEXT')),
    CONSTRAINT ck_duplicate_match_score
        CHECK (score >= 0 AND score <= 1)
);

CREATE INDEX IF NOT EXISTS ix_duplicate_match_scan
    ON duplicate_match (organisation_id, scan_id);

-- Manuelle Ausnahme-Freigabe (FR-039, I-3).
CREATE TABLE IF NOT EXISTS waiver (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id   UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    prompt_version_id UUID         NOT NULL REFERENCES prompt_version(id) ON DELETE CASCADE,
    reason            VARCHAR(2000) NOT NULL,
    waived_by         UUID         NULL REFERENCES app_user(id) ON DELETE SET NULL,
    waived_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_waiver_reason_length CHECK (char_length(reason) >= 10)
);

CREATE INDEX IF NOT EXISTS ix_waiver_version ON waiver (organisation_id, prompt_version_id);

-- ============================================================================
-- Statusmodell, Historie, Audit (E5, SEC-010)
-- ============================================================================

CREATE TABLE IF NOT EXISTS status_transition (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id   UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    prompt_id         UUID         NOT NULL REFERENCES prompt(id) ON DELETE CASCADE,
    prompt_version_id UUID         NOT NULL REFERENCES prompt_version(id) ON DELETE CASCADE,
    version_no        INTEGER      NOT NULL,
    from_status       VARCHAR(16)  NOT NULL,
    to_status         VARCHAR(16)  NOT NULL,
    cause             VARCHAR(255) NOT NULL,
    actor_id          UUID         NULL REFERENCES app_user(id) ON DELETE SET NULL,
    at                TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_status_transition_prompt
    ON status_transition (organisation_id, prompt_id, at DESC);

CREATE TABLE IF NOT EXISTS audit_event (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    actor_id        UUID         NULL REFERENCES app_user(id) ON DELETE SET NULL,
    actor_email     VARCHAR(320) NULL,
    action          VARCHAR(64)  NOT NULL,
    entity_type     VARCHAR(32)  NOT NULL,
    entity_id       UUID         NULL,
    from_status     VARCHAR(16)  NULL,
    to_status       VARCHAR(16)  NULL,
    cause           VARCHAR(255) NULL,
    -- Technische Metadaten, **ohne** Inhalt (FR-062, SEC-010).
    metadata        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_audit_event_entity_type CHECK (entity_type IN (
        'ORGANISATION', 'USER', 'MEMBERSHIP', 'INVITATION', 'PROMPT',
        'PROMPT_VERSION', 'SCAN', 'NOTIFICATION', 'CATEGORY'))
);

CREATE INDEX IF NOT EXISTS ix_audit_event_entity
    ON audit_event (organisation_id, entity_type, entity_id, at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_event_at ON audit_event (at);

-- Outbox für den Live-Status (ADR-005/ADR-008): monoton steigende Sequenz.
CREATE TABLE IF NOT EXISTS status_event (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id   UUID        NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    prompt_id         UUID        NOT NULL REFERENCES prompt(id) ON DELETE CASCADE,
    prompt_version_id UUID        NOT NULL REFERENCES prompt_version(id) ON DELETE CASCADE,
    version_no        INTEGER     NOT NULL,
    status            VARCHAR(16) NOT NULL,
    sequence          BIGINT      NOT NULL,
    finding_count     INTEGER     NOT NULL DEFAULT 0,
    occurred_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ux_status_event_sequence UNIQUE (sequence)
);

CREATE INDEX IF NOT EXISTS ix_status_event_org
    ON status_event (organisation_id, sequence);

-- ============================================================================
-- Alarmierung (E6)
-- ============================================================================

CREATE TABLE IF NOT EXISTS notification_delivery (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id    UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    prompt_id          UUID         NOT NULL REFERENCES prompt(id) ON DELETE CASCADE,
    prompt_version_id  UUID         NOT NULL REFERENCES prompt_version(id) ON DELETE CASCADE,
    version_no         INTEGER      NOT NULL,
    recipient          VARCHAR(320) NOT NULL,
    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts           INTEGER      NOT NULL DEFAULT 0,
    last_error         VARCHAR(500) NULL,
    -- Nur Kategorien und Schweregrade — nie Fundstellen-Inhalte (FR-051).
    finding_categories VARCHAR(32)  NOT NULL DEFAULT '',
    finding_severities VARCHAR(32)  NOT NULL DEFAULT '',
    next_retry_at      TIMESTAMPTZ  NULL,
    sent_at            TIMESTAMPTZ  NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_notification_delivery_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'RETRYING', 'DEAD_LETTER'))
);

CREATE INDEX IF NOT EXISTS ix_notification_delivery_org_status
    ON notification_delivery (organisation_id, status, created_at DESC);

-- ============================================================================
-- Persistente Warteschlangen (CON-009, ADR-005)
-- ============================================================================

CREATE TABLE IF NOT EXISTS scan_job (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id   UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    job_type          VARCHAR(32)  NOT NULL DEFAULT 'SCAN',
    prompt_version_id UUID         NULL REFERENCES prompt_version(id) ON DELETE CASCADE,
    notification_id   UUID         NULL REFERENCES notification_delivery(id) ON DELETE CASCADE,
    status            VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',
    attempt_count     INTEGER      NOT NULL DEFAULT 0,
    max_attempts      INTEGER      NOT NULL DEFAULT 5,
    available_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    locked_at         TIMESTAMPTZ  NULL,
    locked_by         VARCHAR(64)  NULL,
    last_error        VARCHAR(500) NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_scan_job_type
        CHECK (job_type IN ('SCAN', 'NOTIFICATION', 'RETENTION')),
    CONSTRAINT ck_scan_job_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'DEAD_LETTER'))
);

CREATE INDEX IF NOT EXISTS ix_scan_job_poll ON scan_job (status, available_at, created_at);
CREATE INDEX IF NOT EXISTS ix_scan_job_org  ON scan_job (organisation_id, job_type, status);

-- ============================================================================
-- Übermittlungsnachweis und Rohpayloads (FR-074, FR-061)
-- ============================================================================

CREATE TABLE IF NOT EXISTS transfer_audit (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id   UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    scan_id           UUID         NULL REFERENCES scan_run(id) ON DELETE SET NULL,
    prompt_version_id UUID         NULL REFERENCES prompt_version(id) ON DELETE SET NULL,
    provider          VARCHAR(64)  NOT NULL,
    model             VARCHAR(128) NOT NULL,
    content_length    INTEGER      NOT NULL,
    transferred_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    result_id         UUID         NULL,
    correlation_id    VARCHAR(64)  NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_transfer_audit_org
    ON transfer_audit (organisation_id, transferred_at DESC);

-- Rohpayloads werden spätestens nach 24 h gelöscht (FR-061, DP-003).
CREATE TABLE IF NOT EXISTS scan_raw_payload (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    scan_id         UUID         NULL REFERENCES scan_run(id) ON DELETE CASCADE,
    payload         JSONB        NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_scan_raw_payload_expiry ON scan_raw_payload (expires_at);

-- ============================================================================
-- Datenschutzhinweis, Retention, Qualitätsnachweis (E7, E8)
-- ============================================================================

CREATE TABLE IF NOT EXISTS privacy_notice (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    version        VARCHAR(32)  NOT NULL,
    locale         VARCHAR(8)   NOT NULL,
    title          VARCHAR(200) NOT NULL,
    body_markdown  TEXT         NOT NULL,
    effective_from TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_privacy_notice_locale CHECK (locale IN ('de', 'en')),
    CONSTRAINT ux_privacy_notice UNIQUE (version, locale)
);

CREATE TABLE IF NOT EXISTS retention_run (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    status         VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
    started_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    finished_at    TIMESTAMPTZ  NULL,
    deleted_counts JSONB        NOT NULL DEFAULT '{}'::jsonb,
    error          VARCHAR(500) NULL,
    triggered_by   VARCHAR(64)  NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_retention_run_started ON retention_run (started_at DESC);

CREATE TABLE IF NOT EXISTS quality_gate_attestation (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    commit_sha        VARCHAR(64)  NOT NULL,
    pipeline_url      VARCHAR(500) NOT NULL,
    threshold         NUMERIC(5, 2) NOT NULL,
    backend_coverage  NUMERIC(5, 2) NOT NULL,
    frontend_coverage NUMERIC(5, 2) NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    reported_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_quality_gate_status CHECK (status IN ('PASSED', 'FAILED')),
    CONSTRAINT ux_quality_gate_commit UNIQUE (commit_sha)
);

-- ============================================================================
-- Row-Level-Security — zweite Verteidigungslinie (SEC-002, ADR-002)
-- ---------------------------------------------------------------------------
-- Die Anwendung setzt den Tenant-Kontext zu Beginn jeder Transaktion:
--     SET LOCAL app.organisation_id = '<uuid>';
-- Ein Query ohne diesen Kontext liefert deshalb eine **leere** Menge,
-- niemals fremde Daten.
-- ============================================================================

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'membership', 'refresh_token', 'invitation', 'alert_recipient',
        'category', 'tag', 'prompt', 'prompt_version', 'prompt_tag',
        'scan_run', 'scan_finding', 'duplicate_match', 'waiver',
        'status_transition', 'audit_event', 'status_event',
        'scan_job', 'notification_delivery', 'transfer_audit', 'scan_raw_payload'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I '
            'USING (organisation_id = current_setting(''app.organisation_id'', true)::uuid) '
            'WITH CHECK (organisation_id = current_setting(''app.organisation_id'', true)::uuid)',
            t
        );
    END LOOP;
END
$$;

-- `app_user` und `organisation` sind mandantenübergreifend (Nutzerkonto bzw.
-- Mandant selbst). Sie tragen keine organisation_id und sind deshalb nicht in
-- der RLS-Liste; der Zugriff darauf erfolgt ausschließlich über die
-- Membership-Join mit dem Token-Mandanten.

-- ============================================================================
-- Seed: Kategorien je Organisation (FR-014, A-06)
-- ============================================================================
-- Beim Anlegen einer Organisation in derselben Transaktion:
--
--   INSERT INTO category (organisation_id, name, is_default)
--   SELECT :org_id, c.name, TRUE
--   FROM (VALUES ('Customer Support'), ('Code Gen'), ('Data Analysis'),
--              ('Documentation'), ('Other')) AS c(name);
--
-- ============================================================================
-- Retention-Ausführung (FR-060, idempotent) — siehe ADR-004
-- ============================================================================
--   -- 24-h-Frist (FR-061)
--   DELETE FROM scan_raw_payload WHERE expires_at < NOW();
--   -- 90-Tage-Frist (FR-060, DP-003)
--   DELETE FROM transfer_audit      WHERE transferred_at < NOW() - INTERVAL '90 days';
--   DELETE FROM notification_delivery WHERE created_at < NOW() - INTERVAL '90 days';
--   DELETE FROM audit_event         WHERE at < NOW() - INTERVAL '90 days';
--   DELETE FROM refresh_token       WHERE expires_at < NOW() - INTERVAL '90 days';
--   -- prompt / prompt_version / scan_run / scan_finding bleiben bestehen
--   -- (A-03, F5): Inhalte bis zur expliziten Löschung.
--
-- ============================================================================
-- Aufräumen: Veraltete Sitzungen (Logout-Kette, FR-007)
--   DELETE FROM refresh_token
--    WHERE revoked_at IS NOT NULL AND revoked_at < NOW() - INTERVAL '30 days';
-- ============================================================================
