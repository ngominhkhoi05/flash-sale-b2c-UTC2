-- V15: Add updated_at to products (OpenSearch index reconcile)
-- Muc dich: cho phep scheduler doi chieu (reconcile) cac san pham da thay doi
-- giua index OpenSearch va database bang cach quet theo updated_at.
--
-- Luu y: khong sua file migration da ap dung. Day la migration moi, idempotent
-- (IF NOT EXISTS) nen chay lai nhieu lan cung an toan.

ALTER TABLE products ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

-- Backfill cho du lieu cu (created_at la moc thoi gian gan nhat).
UPDATE products SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE updated_at IS NULL;

-- Index phuc vu quet reconcile theo updated_at (chi doc cac dong thay doi gan day).
CREATE INDEX IF NOT EXISTS idx_products_updated_at ON products(updated_at);
