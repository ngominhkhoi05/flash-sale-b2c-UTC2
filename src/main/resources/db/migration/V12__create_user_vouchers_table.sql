-- ===================================================================
-- Migration: V12__create_user_vouchers_table.sql
-- Mô tả: Tạo bảng lưu trữ voucher vào ví cá nhân người dùng (Kho voucher)
-- ===================================================================

CREATE TABLE IF NOT EXISTS user_vouchers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    voucher_id BIGINT NOT NULL REFERENCES vouchers(id) ON DELETE CASCADE,
    saved_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_voucher UNIQUE (user_id, voucher_id)
);

CREATE INDEX IF NOT EXISTS idx_user_vouchers_user_id ON user_vouchers(user_id);
CREATE INDEX IF NOT EXISTS idx_user_vouchers_voucher_id ON user_vouchers(voucher_id);
