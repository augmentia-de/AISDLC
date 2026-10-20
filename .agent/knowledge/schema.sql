-- ============================================================
-- AI SDLC Project — PostgreSQL Schema (Template)
-- ============================================================
-- Dieses Schema ist ein Template. Passe es an dein Projekt an.
-- Auto-applied via Docker Compose auf ersten Start.
-- ============================================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- Organisations (Multi-Tenancy)
-- ============================================================
CREATE TABLE IF NOT EXISTS organisations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- Users
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(320) UNIQUE NOT NULL,
    hashed_password VARCHAR(255) NOT NULL,
    organisation_id UUID REFERENCES organisations(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- Projects (oder deine Haupt-Entitaet)
-- ============================================================
CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'submitted',
    organisation_id UUID REFERENCES organisations(id) ON DELETE CASCADE,
    created_by UUID REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- Indexes
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_projects_org ON projects(organisation_id);
CREATE INDEX IF NOT EXISTS idx_users_org ON users(organisation_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- ============================================================
-- Retention Policy (GDPR: 90-day auto-delete)
-- ============================================================
-- Implementiere als Cron-Job oder Scheduled Task:
-- DELETE FROM projects WHERE created_at < NOW() - INTERVAL '90 days';
-- DELETE FROM users WHERE created_at < NOW() - INTERVAL '90 days' AND NOT EXISTS (SELECT 1 FROM projects WHERE created_by = users.id);