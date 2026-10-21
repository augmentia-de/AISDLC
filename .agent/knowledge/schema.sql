-- ============================================================================
-- <Projektname> — PostgreSQL-Schema (Template)
--
-- Diese Datei ist das KANONISCHE Datenbankschema des Projekts. Sie wird in
-- Phase 2 (Konzept & Architektur) aus dem Datenmodell erzeugt.
--
-- Regeln:
--   * Jede fachliche Tabelle traegt organisation_id und steht unter RLS.
--   * Basis-Lookups (Login, Token-Rotation, Einladung) brauchen SECURITY
--     DEFINER-Funktionen, weil der Mandant dort erst ermittelt wird.
--   * Der Mandant kommt immer aus dem Auftragssatz, nie aus einem Parameter.
--   * Indizes: idempotent anlegen (CREATE INDEX IF NOT EXISTS).
--
-- Diese Datei wird in der Architekturphase versioniert. Eine Kopie im
-- Commit-Scope der Architekturphase ist byte-identisch; ein Mirror-Test erzwingt
-- das (siehe Schema-Entscheidung im zugehoerigen ADR).
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------- Schema
CREATE SCHEMA IF NOT EXISTS app;

-- ---------------------------------------------------------------- Basis-Tabellen
-- <hier die Tabellen des Projekts>

-- ---------------------------------------------------------------- Mandantierung
-- <hier die SECURITY DEFINER Basis-Lookups>

-- ---------------------------------------------------------------- RLS
-- <hier die Row-Level-Security-Deklarationen>

-- ---------------------------------------------------------------- Indizes
-- <hier die Indizes>

COMMIT;
