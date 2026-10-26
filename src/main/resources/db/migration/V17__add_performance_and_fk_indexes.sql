-- ===================================================================
-- Flash Sale B2C - UTC2 Database Migration V17
-- Bổ sung các index tối ưu hiệu năng (Performance) và Khóa ngoại (Foreign Keys)
-- Triệt tiêu Sequential Scan trên các bảng Đơn hàng, Sản phẩm, Flash Sale, Ví, Voucher
-- ===================================================================

-- -------------------------------------------------------------------
-- PHÂN HỆ 1: ĐƠN HÀNG & THANH TOÁN (ORDERS & PAYMENTS)
-- -------------------------------------------------------------------

-- Khóa ngoại trên bảng orders
CREATE INDEX IF NOT EXISTS idx_orders_buyer_id 
    ON orders(buyer_id);

CREATE INDEX IF NOT EXISTS idx_orders_store_id 
    ON orders(store_id);

CREATE INDEX IF NOT EXISTS idx_orders_slot_id 
    ON orders(slot_id);

CREATE INDEX IF NOT EXISTS idx_orders_voucher_id 
    ON orders(voucher_id);

CREATE INDEX IF NOT EXISTS idx_orders_shipping_address_id 
    ON orders(shipping_address_id);

-- Lọc và phân trang đơn hàng theo người mua + trạng thái + thời gian giảm dần
CREATE INDEX IF NOT EXISTS idx_orders_buyer_status_created_at 
    ON orders(buyer_id, status, created_at DESC);

-- Lọc và phân trang đơn hàng theo gian hàng + trạng thái + thời gian giảm dần
CREATE INDEX IF NOT EXISTS idx_orders_store_status_created_at 
    ON orders(store_id, status, created_at DESC);

-- Partial index phục vụ scheduler quét đơn flash sale quá hạn thanh toán
CREATE INDEX IF NOT EXISTS idx_orders_pending_timeout 
    ON orders(expires_at) 
    WHERE status = 'PENDING_PAYMENT' AND slot_id IS NOT NULL;

-- Khóa ngoại trên bảng order_items
CREATE INDEX IF NOT EXISTS idx_order_items_variant_id 
    ON order_items(variant_id);

CREATE INDEX IF NOT EXISTS idx_order_items_flash_sale_item_id 
    ON order_items(flash_sale_item_id);

-- Tra cứu thanh toán theo order_id cho mọi trạng thái (PENDING, FAILED, EXPIRED,...)
CREATE INDEX IF NOT EXISTS idx_payments_order_id 
    ON payments(order_id);

-- -------------------------------------------------------------------
-- PHÂN HỆ 2: SẢN PHẨM & BIẾN THỂ (PRODUCTS & VARIANTS)
-- -------------------------------------------------------------------

-- Khóa ngoại trên bảng products
CREATE INDEX IF NOT EXISTS idx_products_store_id 
    ON products(store_id);

CREATE INDEX IF NOT EXISTS idx_products_category_id 
    ON products(category_id);

-- Lọc sản phẩm của gian hàng theo trạng thái
CREATE INDEX IF NOT EXISTS idx_products_store_status 
    ON products(store_id, status);

-- Partial index phục vụ API đề xuất / mới nhất (findForYouProducts)
CREATE INDEX IF NOT EXISTS idx_products_active_created_at 
    ON products(created_at DESC) 
    WHERE status = 'ACTIVE';

-- Tối ưu tổng hợp giá và tồn kho cho các biến thể đang ACTIVE
CREATE INDEX IF NOT EXISTS idx_product_variants_product_status 
    ON product_variants(product_id, status);

-- -------------------------------------------------------------------
-- PHÂN HỆ 3: FLASH SALE CỐT LÕI (SLOTS & ITEMS)
-- -------------------------------------------------------------------

-- Tối ưu scheduler quét slot kích hoạt và kết thúc
CREATE INDEX IF NOT EXISTS idx_flash_sale_slots_status_times 
    ON flash_sale_slots(status, start_time, end_time);

-- Khóa ngoại variant_id trên flash_sale_items và kiểm tra variant đang trong flash sale
CREATE INDEX IF NOT EXISTS idx_flash_sale_items_variant_id 
    ON flash_sale_items(variant_id);

-- Lấy danh sách item theo slot và trạng thái
CREATE INDEX IF NOT EXISTS idx_flash_sale_items_slot_status 
    ON flash_sale_items(slot_id, status);

-- -------------------------------------------------------------------
-- PHÂN HỆ 4: VÍ ĐIỆN TỬ & SỔ CÁI TÀI CHÍNH (WALLETS & TRANSACTIONS)
-- -------------------------------------------------------------------

-- Phân trang sổ cái biến động số dư theo ví
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_wallet_created 
    ON wallet_transactions(wallet_id, created_at DESC);

-- Lọc và tổng hợp giao dịch ví theo loại (WITHDRAWAL, REVENUE,...)
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_wallet_type 
    ON wallet_transactions(wallet_id, transaction_type);

-- Đối soát giao dịch tài chính theo đơn hàng
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_order_id 
    ON wallet_transactions(order_id);

-- -------------------------------------------------------------------
-- PHÂN HỆ 5: KHUYẾN MÃI & GIỎ HÀNG (VOUCHERS & CARTS)
-- -------------------------------------------------------------------

-- Lọc voucher theo gian hàng
CREATE INDEX IF NOT EXISTS idx_vouchers_store_id 
    ON vouchers(store_id);

-- Lọc voucher còn hiệu lực
CREATE INDEX IF NOT EXISTS idx_vouchers_status_end_time 
    ON vouchers(status, end_time);

-- Kiểm tra giới hạn số lần sử dụng voucher của người dùng
CREATE INDEX IF NOT EXISTS idx_voucher_usages_user_voucher 
    ON voucher_usages(user_id, voucher_id);

CREATE INDEX IF NOT EXISTS idx_voucher_usages_voucher_id 
    ON voucher_usages(voucher_id);

-- Khóa ngoại trên giỏ hàng phục vụ kiểm tra và cascade delete
CREATE INDEX IF NOT EXISTS idx_cart_items_variant_id 
    ON cart_items(variant_id);

-- -------------------------------------------------------------------
-- PHÂN HỆ 6: BẢO MẬT, PHÂN QUYỀN, GIAN HÀNG & ẢNH ĐA HÌNH
-- -------------------------------------------------------------------

-- Tra cứu người dùng theo vai trò
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id 
    ON user_roles(role_id);

-- Tra cứu quyền trong nhóm quyền
CREATE INDEX IF NOT EXISTS idx_group_permissions_permission_id 
    ON group_permissions(permission_id);

-- Tra cứu tài khoản OAuth2 theo user_id
CREATE INDEX IF NOT EXISTS idx_auth_accounts_user_id 
    ON auth_accounts(user_id);

-- Lấy danh sách gian hàng nổi bật được duyệt
CREATE INDEX IF NOT EXISTS idx_stores_status_created_at 
    ON stores(status, created_at DESC);

-- Partial index hỗ trợ scheduler dọn dẹp ảnh INACTIVE đã hết hạn
CREATE INDEX IF NOT EXISTS idx_images_inactive_cleanup 
    ON images(cloudinary_deleted, updated_at) 
    WHERE status = 'INACTIVE';
