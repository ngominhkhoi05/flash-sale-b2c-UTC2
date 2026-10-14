-- ===================================================================
-- Flash Sale B2C - UTC2 Database Migration V4
-- Xoa 5 cot anh roi rac da duoc thay the bang bang images (V3)
-- ===================================================================

-- 1. Anh dai dien chinh cua san pham SPU (gallery gio o bang images)
ALTER TABLE products DROP COLUMN image_url;

-- 2. Anh rieng cua tung bien the SKU (gallery gio o bang images)
ALTER TABLE product_variants DROP COLUMN image_url;

-- 3. Logo gian hang (gio o bang images voi owner_type=''STORE'')
ALTER TABLE stores DROP COLUMN logo_url;

-- 4. Avatar nguoi dung (gio o bang images voi owner_type=''USER'')
ALTER TABLE users DROP COLUMN avatar_url;

-- 5. Album anh feedback cua danh gia (gio o bang images voi owner_type=''REVIEW'')
ALTER TABLE product_reviews DROP COLUMN image_urls;

-- ===================================================================
-- LUU Y QUAN TRONG VE BOOT VOI ddl-auto=validate:
-- Sau khi V4 chay thanh cong, neu code Java Entity con giu field
-- imageUrl / avatarUrl / logoUrl / imageUrls, app se fail o buoc
-- Hibernate schema validation. Day la tin hieu dung de biet can
-- migrate code Java sang doc/ghi qua bang images.
--
-- Truoc khi chay V4, can dam bao:
--   - Entity Product bo field imageUrl
--   - Entity ProductVariant bo field imageUrl
--   - Entity Store bo field logoUrl
--   - Entity User bo field avatarUrl
--   - Entity ProductReview bo field imageUrls
--   - Service/Repository/DTO cap nhat de truy van tu bang images
-- ===================================================================