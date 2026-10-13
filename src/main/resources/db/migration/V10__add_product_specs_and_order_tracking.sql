-- ===================================================================
-- Flash Sale B2C - UTC2 Database Migration V10
-- Bo sung cac cot thong tin chi tiet san pham (specs, features, commitments, size_chart)
-- va thong tin van chuyen don hang (tracking_number, shipped_at, completed_at)
-- phuc vu Frontend Integration (Gap Report 2026-10-13)
-- ===================================================================

-- 1. Bang products: them cac truong JSONB dac ta chi tiet san pham
ALTER TABLE products ADD COLUMN IF NOT EXISTS specs JSONB;
ALTER TABLE products ADD COLUMN IF NOT EXISTS features JSONB;
ALTER TABLE products ADD COLUMN IF NOT EXISTS commitments JSONB;
ALTER TABLE products ADD COLUMN IF NOT EXISTS size_chart JSONB;
ALTER TABLE products ADD COLUMN IF NOT EXISTS description_rich JSONB;

-- 2. Bang orders: them cac truong tracking va thoi gian van chuyen
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipped_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
