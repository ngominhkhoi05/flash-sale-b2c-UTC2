-- ===================================================================
-- Flash Sale B2C - UTC2 Database Migration V3
-- Tao bang images polymorphic phuc vu quan ly anh da doi tuong
-- Thay the 5 cot anh roi rac:
--   products.image_url
--   product_variants.image_url
--   stores.logo_url
--   users.avatar_url
--   product_reviews.image_urls (JSONB)
-- ===================================================================

CREATE TABLE images (
    id BIGSERIAL PRIMARY KEY,
    owner_type              VARCHAR(20)   NOT NULL,
    owner_id                BIGINT        NOT NULL,
    url                     VARCHAR(500)  NOT NULL,
    cloudinary_public_id    VARCHAR(255),
    display_order           INT           DEFAULT 0,
    is_primary              BOOLEAN       DEFAULT FALSE,
    cloudinary_deleted      BOOLEAN       DEFAULT FALSE,
    status                  VARCHAR(20)   DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_images_owner_type CHECK (
        owner_type IN ('PRODUCT','VARIANT','USER','STORE','REVIEW')
    ),
    CONSTRAINT chk_images_order CHECK (display_order >= 0),
    CONSTRAINT chk_images_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

-- Tra truy van chinh: liet ke anh cua 1 owner theo thu tu hien thi
CREATE INDEX idx_images_owner
    ON images(owner_type, owner_id, display_order);

-- 1 user chi co toi da 1 avatar ACTIVE
CREATE UNIQUE INDEX uq_images_user_one
    ON images(owner_id)
    WHERE owner_type = 'USER' AND status = 'ACTIVE';

-- 1 store chi co toi da 1 logo ACTIVE
CREATE UNIQUE INDEX uq_images_store_one
    ON images(owner_id)
    WHERE owner_type = 'STORE' AND status = 'ACTIVE';

-- 1 product/variant chi co toi da 1 anh primary ACTIVE
CREATE UNIQUE INDEX uq_images_owner_one_primary
    ON images(owner_type, owner_id)
    WHERE owner_type IN ('PRODUCT','VARIANT')
      AND is_primary = TRUE
      AND status = 'ACTIVE';

-- ===================================================================
-- Ghi chu ky thuat:
-- 1. Model polymorphic (owner_type + owner_id): linh hoat nhung KHONG
--    the tao FK vat ly toi 5 bang owner. Tinh toan ven tham chieu
--    do Service dam bao (kiem tra owner ton tai truoc khi insert).
-- 2. cloudinary_public_id luu public_id de goi Cloudinary destroy API.
-- 3. cloudinary_deleted flag dung cho idempotency khi retry destroy.
-- 4. Soft delete bang status='INACTIVE'; scheduled job se don record
--    INACTIVE + cloudinary_deleted=true + updated_at < now()-30d.
-- 5. V3 chi tao bang moi, KHONG drop cot cu (la do V4 dam nhiem).
--    Truoc V4 app van boot OK voi ddl-auto=validate.
-- ===================================================================