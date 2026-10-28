-- Kanonisches Schema (mini-ticket)
-- Postgres (prod) + SQLite (dev/test) kompatibel (portable Typen: TEXT).
-- Spiegel-Regel: docs/architecture/schema.sql ist byte-identisch (SchemaMirrorIT).

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS tickets (
    id          TEXT    PRIMARY KEY,
    title       TEXT    NOT NULL CHECK (length(title) BETWEEN 1 AND 200),
    description TEXT             CHECK (description IS NULL OR length(description) <= 4000),
    priority    TEXT    NOT NULL CHECK (priority IN ('low','medium','high')) DEFAULT 'medium',
    status      TEXT    NOT NULL CHECK (status IN ('open','in_progress','resolved','closed')) DEFAULT 'open',
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tickets_status     ON tickets (status);
CREATE INDEX IF NOT EXISTS idx_tickets_priority   ON tickets (priority);
CREATE INDEX IF NOT EXISTS idx_tickets_created_at ON tickets (created_at DESC);

CREATE TABLE IF NOT EXISTS comments (
    id          TEXT    PRIMARY KEY,
    ticket_id   TEXT    NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    text        TEXT    NOT NULL CHECK (length(text) BETWEEN 1 AND 2000),
    author      TEXT    NOT NULL DEFAULT 'anonymous',
    created_at  TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_comments_ticket_id  ON comments (ticket_id);
CREATE INDEX IF NOT EXISTS idx_comments_created_at ON comments (created_at ASC);