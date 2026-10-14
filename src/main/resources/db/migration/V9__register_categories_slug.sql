-- V9: Register categories.slug (was added out-of-band on production DB)
-- This migration formalizes the categories.slug column that already exists
-- as NOT NULL on Supabase but was not tracked in Flyway.
--
-- Migration is idempotent:
--   - Skip ALTER TABLE if column already exists
--   - Backfill NULL slugs from name (same pattern as V8 for products)
--   - CREATE INDEX IF NOT EXISTS for unique partial index

-- ============================================================
-- STEP 1: Ensure categories.slug column exists
-- ============================================================

ALTER TABLE categories ADD COLUMN IF NOT EXISTS slug VARCHAR(255);

-- ============================================================
-- STEP 2: Backfill NULL slugs from name
-- Converts text to ASCII-safe lowercase hyphen-separated format
-- Idempotent: only sets slug where slug IS NULL
-- ============================================================

UPDATE categories
SET slug = LOWER(
    REGEXP_REPLACE(
        REGEXP_REPLACE(
            REGEXP_REPLACE(name, '[^a-zA-Z0-9[:space:]]', '', 'g'),
            '[[:space:]]+', '-', 'g'
        ),
        '-+', '-', 'g'
    )
)
WHERE slug IS NULL;

-- ============================================================
-- STEP 3: Partial unique index (allows NULL but prevents duplicate non-null)
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_slug
    ON categories(slug) WHERE slug IS NOT NULL;