-- ===================================================================
-- Migration: V11__add_product_search_index.sql
-- Mô tả: Thêm chỉ mục phục vụ tối ưu hóa tìm kiếm sản phẩm và lọc giá
-- ===================================================================

CREATE INDEX IF NOT EXISTS idx_products_status_created ON products(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_products_name_lower ON products(LOWER(name));
CREATE INDEX IF NOT EXISTS idx_products_category_status ON products(category_id, status);
CREATE INDEX IF NOT EXISTS idx_product_variants_search ON product_variants(product_id, status, original_price);
