# ĐẶC TẢ YÊU CẦU API BACKEND (API-REQUEST-BE.MD)

> **Tài liệu bàn giao kỹ thuật & đặc tả API cho đội ngũ Backend (`flash-sale-b2c-UTC2`)**  
> **Dự án**: Flash Sale B2C Multi-Vendor Marketplace  
> **Kiến trúc**: Spring Boot 3.4.x (Java 25) + PostgreSQL 16 + Redis (Redisson Distributed Lock & Lua Scripts) + WebSocket STOMP  
> **Ngày cập nhật**: 18/10/2026 (Vòng 6 - Rà soát Quyền Store & Chuẩn hóa Toàn diện Luồng Rút tiền Ví Seller)  
> **Hạn hoàn thành đề xuất**: do PO/BE thỏa thuận

---

## BẢNG TỔNG HỢP TIẾN ĐỘ & PHẠM VI ẢNH HƯỞNG

| STT | Endpoint | Method | Role yêu cầu | Ưu tiên | Màn hình FE bị chặn | Trạng thái BE |
|---|---|---|---|---|---|---|
| 1 | `/api/v1/admin/stores` | GET | `ROLE_ADMIN` | **P0** | Quản lý gian hàng (`/admin/stores`) | ✅ Đã hoàn thành (Lát 2) |
| 2 | `/api/v1/admin/users` | GET | `ROLE_ADMIN` | **P0** | Quản lý người dùng (`/admin/users`) | ✅ Đã hoàn thành (Lát 3) |
| 3 | `/api/v1/admin/users/{id}/status` | PATCH | `ROLE_ADMIN` | **P0** | Khóa/mở khóa tài khoản (`/admin/users`) | ✅ Đã hoàn thành (Lát 3) |
| 4 | `/api/v1/admin/products` | GET | `ROLE_ADMIN` | **P0** | Quản lý sản phẩm sàn (`/admin/products`) | ✅ Đã hoàn thành (Lát 4) |
| 5 | `/api/v1/admin/products/{id}/status` | PATCH | `ROLE_ADMIN` | **P0** | Duyệt/khóa sản phẩm (`/admin/products`) | ✅ Đã hoàn thành (Lát 4) |
| 6 | `/api/v1/admin/flash-sales/slots/{id}/telemetry` | GET | `ROLE_ADMIN` | **P0** | Monitor Flash Sale (`/admin/flash-sale/monitor`) | ✅ Đã hoàn thành (Lát 7) |
| 7 | `/api/v1/admin/flash-sales/slots/{id}/emergency-pause` | POST | `ROLE_ADMIN` | **P0** | Dừng khẩn cấp phiên sale (`/admin/flash-sale/monitor`) | ✅ Đã hoàn thành (Lát 7) |
| 8 | `/api/v1/admin/flash-sales/items/batch-approve` | POST | `ROLE_ADMIN` | **P0** | Duyệt hàng loạt SKU (`/admin/flash-sale/approvals`) | ✅ Đã hoàn thành (Lát 1) |
| 9 | `/api/v1/seller/vouchers` | GET | `ROLE_SELLER` | **P1** | Quản lý Voucher Shop (`/seller/vouchers`) | ✅ Đã hoàn thành (Lát 5) |
| 10 | `/api/v1/seller/vouchers/{id}/status` | PATCH | `ROLE_SELLER` | **P1** | Bật/Tắt Voucher Shop (`/seller/vouchers`) | ✅ Đã hoàn thành (Lát 5) |
| 11 | `/api/v1/admin/vouchers` | GET | `ROLE_ADMIN` | **P1** | Quản lý Voucher toàn sàn (`/admin/vouchers`) | ✅ Đã hoàn thành (Lát 5) |
| 12 | `/api/v1/admin/vouchers/kpis` | GET | `ROLE_ADMIN` | **P1** | Thống kê ngân sách Voucher (`/admin/vouchers`) | ✅ Đã hoàn thành (Lát 5) |
| 13 | `/api/v1/seller/wallet/bank-account` | GET, PUT | `ROLE_SELLER` | **P1** | Cấu hình tài khoản ngân hàng (`/seller/wallet`) | ✅ Đã hoàn thành (Lát 8) |
| 14 | `/api/v1/admin/wallets/withdrawals` | GET | `ROLE_ADMIN` | **P1** | Danh sách duyệt rút tiền ví (`/admin/wallets`) | ✅ Đã hoàn thành (Lát 8) |
| 15 | `/api/v1/admin/wallets/withdrawals/{id}/approve` | PATCH | `ROLE_ADMIN` | **P1** | Duyệt chi chuyển khoản ví (`/admin/wallets`) | ✅ Đã hoàn thành (Lát 8) |
| 16 | `/api/v1/admin/wallets/withdrawals/{id}/reject` | PATCH | `ROLE_ADMIN` | **P1** | Từ chối lệnh rút tiền & hoàn số dư (`/admin/wallets`) | ✅ Đã hoàn thành (Lát 8) |
| 17 | `/api/v1/stores/me/**` | PUT, POST, DELETE | `ROLE_SELLER` | **P1** | Bảo mật phân quyền Store & Kho (`/seller/**`) | ✅ Đã hoàn thành (Lát 6) |
| 18 | `/api/v1/admin/flash-sales/items/counts` | GET | `ROLE_ADMIN` | **P2** | Thống kê SKU theo slot (`/admin/flash-sale/approvals`) | ❌ Chưa làm |
| 19 | `/api/v1/admin/flash-sales/items/batch-reject` | POST | `ROLE_ADMIN` | **P2** | Từ chối hàng loạt SKU (`/admin/flash-sale/approvals`) | ❌ Chưa làm |
| 20 | `/api/v1/seller/analytics/revenue-chart` | GET | `ROLE_SELLER` | **P2** | Biểu đồ doanh thu shop (`/seller`) | ❌ Chưa làm |
| 21 | `/api/v1/admin/reconciliation/summary` | GET | `ROLE_ADMIN` | **P2** | Đối soát tài chính & phí sàn 5% (`/admin/reconciliation`) | ❌ Chưa làm |

> [!WARNING] Cảnh báo Kiến trúc & Phân kỳ Sprint
> Endpoint **Emergency Pause (P0)** liên quan trực tiếp đến tính nhất quán tồn kho DB/Redis và các đơn hàng đang trong luồng thanh toán ZaloPay (hold inventory). Cần thiết kế kỹ lưỡng và thận trọng.
> **Khuyến nghị phân chia nhỏ Sprint**:
> - **Sprint P0-A**: Hoàn thiện cụm CRUD Admin cốt lõi (`stores`, `users`, `products`, `batch-approve`) để mở khóa ngay 3 trang quản trị vận hành.
> - **Sprint P0-B**: Triển khai `telemetry` và `emergency-pause` với kịch bản kiểm thử tải trọng & rollback an toàn.

---

## 1. NHÓM ƯU TIÊN P0 (KHẨN CẤP - CHẶN MÀN HÌNH CHÍNH)

### 1.1. Lấy danh sách gian hàng toàn sàn
- **Ưu tiên**: P0
- **Chức năng**: Cho phép Admin tra cứu, lọc và duyệt danh sách gian hàng trên toàn sàn.
- **Method & URL**: `GET /api/v1/admin/stores`
- **Role yêu cầu**: `@PreAuthorize("hasRole('ADMIN')")`
- **Controller đề xuất**: `AdminStoreController.java` (package `com.b2c.flash_sale_b2c_UTC2.store.controller`)
- **Query Parameters**:
  - `status` (String, optional): `PENDING` | `APPROVED` | `REJECTED` | `BANNED` | `SUSPENDED`
  - `search` (String, optional): Tên shop, email chủ shop, số điện thoại
  - `page` (int, default: 0), `size` (int, default: 10), `sort` (String, default: "createdAt,desc")
- **Quy tắc nghiệp vụ**:
  - Khi không truyền `status`, trả về tất cả gian hàng theo bộ lọc phân trang.
  - Sắp xếp mặc định theo thời gian tạo mới nhất (`createdAt,DESC`).
- **Mã lỗi**: `401 Unauthorized`, `403 Forbidden`.
- **Màn hình FE sử dụng**: `/admin/stores` (`src/app/admin/stores/page.tsx`).
- **Response Thành công (`ApiResponse<PageResponse<StoreResponse>>`)**:
```json
{
  "success": true,
  "code": 200,
  "message": "Lấy danh sách gian hàng thành công",
  "data": {
    "content": [
      {
        "id": 1,
        "storeName": "Anker Official Store",
        "slug": "anker-official-store",
        "status": "PENDING",
        "description": "Gian hàng chính hãng thiết bị sạc Anker",
        "logoUrl": "https://cdn.vibemart.vn/stores/logo-anker.png",
        "bannerUrl": "https://cdn.vibemart.vn/stores/banner-anker.png",
        "defaultCommissionRate": 0.0500,
        "ownerEmail": "seller1@demo.local",
        "ownerFullName": "Nguyễn Văn Bán",
        "ownerPhone": "0901234567",
        "createdAt": "2026-10-14T08:00:00Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "timestamp": "2026-10-18T20:00:00Z"
}
```
- **Điều kiện nghiệm thu (Acceptance Criteria)**:
  - *Input*: `GET /api/v1/admin/stores?page=0&size=10&status=PENDING` với JWT có `ROLE_ADMIN`.
  - *Kỳ vọng*: HTTP 200, body bọc `ApiResponse<PageResponse<StoreResponse>>`, chỉ chứa các gian hàng có trạng thái `PENDING`.
  - *Lỗi kiểm thử*: Không có token -> 401; token `ROLE_BUYER` hoặc `ROLE_SELLER` -> 403 Forbidden.

---

### 1.2. Quản lý người dùng toàn hệ thống
- **Ưu tiên**: P0
- **Chức năng**: Admin xem danh sách người dùng và khóa/mở khóa tài khoản.
- **Method & URL**:
  - `GET /api/v1/admin/users`
  - `PATCH /api/v1/admin/users/{id}/status`
- **Role yêu cầu**: `@PreAuthorize("hasRole('ADMIN')")`
- **Controller đề xuất**: `AdminUserController.java` (package `com.b2c.flash_sale_b2c_UTC2.user.controller`)
- **Query Parameters cho `GET`**:
  - `role` (String, optional): `ADMIN` | `SELLER` | `BUYER`
  - `status` (String, optional): `ACTIVE` | `LOCKED` | `PENDING_VERIFY`
  - `search` (String, optional): Tìm theo email, họ tên, số điện thoại
  - `page` (int, default: 0), `size` (int, default: 10)
- **Request Body cho `PATCH /{id}/status`**:
```json
{
  "status": "LOCKED",
  "reason": "Phát hiện hành vi gian lận voucher khuyến mãi"
}
```
- **Quy tắc nghiệp vụ**:
  - Không cho phép Admin tự khóa tài khoản của chính mình (kiểm tra `currentUserId == targetUserId`).
  - Khi khóa tài khoản: vô hiệu hóa refresh token trong Redis (`jti`), thu hồi phiên WebSocket ngay lập tức.
- **Mã lỗi**: `USER_NOT_FOUND` (404), `CANNOT_LOCK_SELF` (400).
- **Màn hình FE sử dụng**: `/admin/users` (`src/app/admin/users/page.tsx`).
- **Điều kiện nghiệm thu (Acceptance Criteria)**:
  - *Input*: `PATCH /api/v1/admin/users/2/status` với body `{"status": "LOCKED"}` bởi Admin ID 1.
  - *Kỳ vọng*: HTTP 200, user 2 chuyển thành `LOCKED`; user 2 dùng token hiện tại gọi API tiếp theo bị chặn 403.
  - *Lỗi kiểm thử*: Admin 1 cố tự khóa mình (`/admin/users/1/status`) -> ném `CANNOT_LOCK_SELF` (400).

---

### 1.3. Quản lý sản phẩm toàn sàn (Admin Product Moderation)
- **Ưu tiên**: P0
- **Chức năng**: Admin xem danh sách sản phẩm trên toàn sàn và phê duyệt/gỡ bỏ sản phẩm vi phạm.
- **Method & URL**:
  - `GET /api/v1/admin/products`
  - `PATCH /api/v1/admin/products/{id}/status`
- **Role yêu cầu**: `@PreAuthorize("hasRole('ADMIN')")`
- **Controller**: `AdminProductController.java` (package `com.b2c.flash_sale_b2c_UTC2.product.controller`)
- **Query Parameters cho `GET`**:
  - `storeId` (Long, optional): Lọc theo shop
  - `categoryId` (Long, optional): Lọc theo ngành hàng
  - `status` (String, optional): `DRAFT` | `PENDING_APPROVAL` | `ACTIVE` | `REJECTED` | `BANNED`
  - `search` (String, optional): Tên sản phẩm, mã SKU
  - `page` (int, default: 0), `size` (int, default: 10)
- **Request Body cho `PATCH /{id}/status`**:
```json
{
  "status": "BANNED",
  "reason": "Sản phẩm vi phạm bản quyền thương hiệu"
}
```
- **Màn hình FE sử dụng**: `/admin/products` (`src/app/admin/products/page.tsx`).
- **Điều kiện nghiệm thu (Acceptance Criteria)**:
  - *Input*: `GET /api/v1/admin/products?status=PENDING_APPROVAL&page=0&size=10`.
  - *Kỳ vọng*: Trả về danh sách sản phẩm chờ duyệt kèm tên Store và danh mục. Khi PATCH sang `ACTIVE`, sản phẩm xuất hiện công khai trên Storefront.
  - *Lỗi kiểm thử*: Không tìm thấy ID sản phẩm -> trả về `PRODUCT_NOT_FOUND` (404).

---

### 1.4. Giám sát Telemetry phiên Flash Sale Realtime
- **Ưu tiên**: P0
- **Chức năng**: Cung cấp số liệu kỹ thuật theo thời gian thực (tải Redis, RPS, trạng thái khóa, đơn thành công).
- **Method & URL**: `GET /api/v1/admin/flash-sales/slots/{id}/telemetry`
- **Role yêu cầu**: `@PreAuthorize("hasRole('ADMIN')")`
- **Controller**: `AdminFlashSaleController.java`
- **Định nghĩa từng chỉ số & Nguồn dữ liệu khả thi**:
  1. `currentRps`: Requests per second tại thời điểm hiện tại.
     - *Nguồn*: Redis sliding window counter key `flashsale:telemetry:req:{slotId}:{unixSecond}`. Mỗi request vào API reserve tăng counter `INCR`. Telemetry đọc giây `unixSecond - 1`.
  2. `activeLocks`: Số đơn hàng đang giữ kho chờ thanh toán.
     - *Nguồn*: `redisTemplate.opsForZSet().zCard("flashsale:pending_reservations:index")`.
  3. `successfulOrders`: Số đơn Flash Sale thanh toán thành công.
     - *Nguồn*: Đếm trong PostgreSQL `orders` với `slot_id = :id` và `status = 'PAID'` (hoặc Redis counter `flashsale:orders:success:{slotId}`).
  4. `oversoldIncidents`: Số vụ việc bán vượt tồn kho (luôn phải bằng 0 nhờ Redisson Lock + Lua).
  5. `redisLatencyMs`: Độ trễ P99 đo thời gian thực thi Redis Lua command.
  6. `isPaused`: Phiên có đang bị dừng khẩn cấp hay không.
- **Tần suất & Caching**: Cache kết quả trong Redis với TTL 2 giây để tránh làm nghẽn DB khi Admin mở dashboard monitor.
- **Response Thành công (`ApiResponse<RealtimeTelemetryResponse>`)**:
```json
{
  "success": true,
  "code": 200,
  "message": "Lấy telemetry phiên Flash Sale thành công",
  "data": {
    "slotId": 3,
    "status": "ACTIVE",
    "isPaused": false,
    "currentRps": 3540,
    "totalRequests": 128500,
    "successfulOrders": 4820,
    "failedRequests": 120,
    "oversoldIncidents": 0,
    "redisLatencyMs": 1.2,
    "systemHealth": "OPTIMAL"
  },
  "timestamp": "2026-10-18T20:00:00Z"
}
```
- **Điều kiện nghiệm thu (Acceptance Criteria)**:
  - *Input*: `GET /api/v1/admin/flash-sales/slots/1/telemetry`.
  - *Kỳ vọng*: Trả về đúng slot 1, `currentRps >= 0`, `oversoldIncidents == 0`, `systemHealth = 'OPTIMAL'`.
  - *Lỗi kiểm thử*: `slotId` không tồn tại -> trả về `SLOT_NOT_FOUND` (404).

---

### 1.5. Tạm dừng khẩn cấp phiên Flash Sale (Emergency Pause & Rollback)
- **Ưu tiên**: P0
- **Chức năng**: Admin dừng khẩn cấp phiên Flash Sale khi phát hiện sự cố hệ thống hoặc tấn công bot.
- **Method & URL**: `POST /api/v1/admin/flash-sales/slots/{id}/emergency-pause`
- **Role yêu cầu**: `@PreAuthorize("hasRole('ADMIN')")`
- **Controller**: `AdminFlashSaleController.java`
- **Request Body**:
```json
{
  "reason": "Phát hiện tấn công DDoS và nghi vấn khai thác lỗ hổng giữ kho",
  "rollbackStock": true
}
```
- **Quy tắc & Hành vi kiến trúc chi tiết bắt buộc**:
  1. **Khóa nhận đơn nguyên tử**: Đặt cờ `flashsale:slot:{id}:paused = 1` trên Redis. Lua script `reserve_stock.lua` kiểm tra cờ này ở đầu script; nếu bằng 1 lập tức từ chối request giữ hàng với mã lỗi `SLOT_PAUSED`.
  2. **Hoàn kho nguyên tử bằng Lua**:
     - KHÔNG đọc từng key bằng Java rồi cập nhật rời rạc (gây race condition).
     - Sử dụng Lua script hoặc transaction: Duyệt toàn bộ `flashsale:stock:{itemId}`, đọc số lượng còn lại, sau đó xóa key trên Redis.
     - Dùng câu lệnh SQL nguyên tử `ProductVariantRepository.replenishStockQuantityConditionally` để cộng bù số lượng chưa bán về kho vật lý PostgreSQL.
  3. **Xử lý đơn hàng đang chờ thanh toán (ZaloPay pending)**:
     - Các đơn đã giữ kho thành công TRƯỚC thời điểm pause và đang trong hạn 15 phút thanh toán (`holdSeconds`) **vẫn được phép hoàn tất thanh toán**.
     - Nếu hết 15 phút người dùng không thanh toán, scheduler `FlashSaleTimeoutRollbackJob` sẽ tự động nhả kho về PostgreSQL.
  4. **Chống hoàn kho hai lần (Idempotency)**:
     - Thêm cờ `is_stock_rolled_back BOOLEAN DEFAULT FALSE` trên bảng `flash_sale_slots`.
     - Khi thực hiện emergency rollback, cập nhật `is_stock_rolled_back = TRUE`.
     - Khi scheduler `FlashSaleSlotCloseService` chạy đến cuối khung giờ đóng slot, kiểm tra nếu `is_stock_rolled_back == TRUE` thì **bỏ qua hoàn kho**, tránh việc cộng tồn kho 2 lần cho Seller.
  5. **STOMP Broadcast**: Bắn WebSocket broadcast đến topic `/topic/flash-sale/slot/{slotId}/status` với payload `{ eventType: "SLOT_CLOSED", slotId: 1, reason: "EMERGENCY_PAUSED" }`.
- **Mã lỗi**: `SLOT_NOT_FOUND` (404), `SLOT_NOT_ACTIVE` (400).
- **Màn hình FE sử dụng**: `/admin/flash-sale/monitor` (`src/app/admin/flash-sale/monitor/page.tsx`).
- **Điều kiện nghiệm thu (Acceptance Criteria)**:
  - *Input*: `POST /api/v1/admin/flash-sales/slots/1/emergency-pause` với body `{ "reason": "Bảo trì", "rollbackStock": true }`.
  - *Kỳ vọng*: HTTP 200, slot chuyển `PAUSED`, tồn kho còn lại trên Redis được hoàn trả nguyên vẹn về `product_variants.stock_quantity`, cờ `is_stock_rolled_back = true`.
  - *Lỗi kiểm thử*: Gọi emergency pause lần 2 trên cùng 1 slot đã dừng -> trả về lỗi `SLOT_NOT_ACTIVE` hoặc trả HTTP 200 dạng no-op an toàn (không cộng thêm kho lần nữa).

---

### 1.6. Chuẩn hóa Batch Approve Flash Sale Items
- **Ưu tiên**: P0
- **Chức năng**: Admin phê duyệt hàng loạt SKU Flash Sale từ Seller gửi lên.
- **Method & URL**: `POST /api/v1/admin/flash-sales/items/batch-approve`
- **Role yêu cầu**: `@PreAuthorize("hasRole('ADMIN')")`
- **Hợp đồng Request**: FE **đã chuẩn hóa gửi đúng trường `itemIds`** theo DTO `BatchApproveFlashSaleItemsRequest.java:18`. BE **không cần thêm alias `ids`**:
```json
{
  "itemIds": [101, 102, 103]
}
```
- **Hiện trạng xác minh**: `FlashSaleItemService.java:318-338` hiện đã bắt `try/catch` từng item và điền `failedReasons.add("ID " + itemId + ": " + ex.getMessage())`.
- **Yêu cầu nâng cấp sang Cấu trúc DTO (Structured Failures)**:
  - Thay vì trả về `List<String>` ghép chuỗi, BE chuyển sang trả về mảng object chi tiết:
```json
{
  "success": true,
  "code": 200,
  "message": "Xử lý duyệt hàng loạt hoàn tất",
  "data": {
    "approvedItemIds": [101, 102],
    "failedReasons": [
      {
        "itemId": 103,
        "reason": "Số lượng đăng ký vượt quá tồn kho khả dụng của sản phẩm gốc"
      }
    ]
  },
  "timestamp": "2026-10-18T20:00:00Z"
}
```
  - **Không nuốt exception hệ thống**: Nếu có ngoại lệ nghiêm trọng liên quan đến kết nối DB hoặc deadlock, phải throw lỗi hệ thống thay vì nuốt im lặng.
- **Điều kiện nghiệm thu (Acceptance Criteria)**:
  - *Input*: Mảng `itemIds: [101, 102]` (101 hợp lệ, 102 đã bị duyệt trước đó hoặc hết hàng).
  - *Kỳ vọng*: HTTP 200, `approvedItemIds: [101]`, `failedReasons: [{ itemId: 102, reason: "..." }]`.
  - *Lỗi kiểm thử*: Gửi mảng `itemIds: []` rỗng -> Validation chặn 400 Bad Request (`Danh sách ID không được rỗng`).

---

## 2. NHÓM ƯU TIÊN P1 (QUAN TRỌNG - NGHIỆP VỤ & TÍNH TOÀN VẸN)

### 2.1. Phân trang Danh sách Mã giảm giá của Shop (Seller)
- **Ưu tiên**: P1
- **Chức năng**: Phân trang danh sách voucher do shop phát hành.
- **Method & URL**: `GET /api/v1/seller/vouchers`
- **Role yêu cầu**: `@PreAuthorize("hasRole('SELLER')")`
- **Query Parameters**:
  - `status` (String, optional): `ALL` | `ACTIVE` | `PAUSED` | `EXPIRED`
  - `page` (int, default: 0), `size` (int, default: 10)
- **Hiện trạng**: BE `SellerVoucherController.java:49` đang trả về `List<VoucherResponse>` (mảng thô).
- **Yêu cầu**: Đổi sang `ApiResponse<PageResponse<VoucherResponse>>`.
```json
{
  "success": true,
  "code": 200,
  "message": "Lấy danh sách mã giảm giá thành công",
  "data": {
    "content": [
      {
        "id": 15,
        "code": "SHOPVIP10",
        "storeId": 1,
        "discountType": "PERCENTAGE",
        "discountValue": 10.00,
        "minOrderAmount": 200000.00,
        "maxDiscountAmount": 50000.00,
        "totalQuantity": 500,
        "usedQuantity": 42,
        "status": "ACTIVE",
        "startTime": "2026-10-15T00:00:00Z",
        "endTime": "2026-10-31T23:59:59Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "timestamp": "2026-10-18T20:00:00Z"
}
```

---

### 2.2. Đổi trạng thái Mã giảm giá của Shop (Seller)
- **Ưu tiên**: P1
- **Chức năng**: Chủ shop tạm dừng hoặc mở lại voucher của gian hàng.
- **Method & URL**: `PATCH /api/v1/seller/vouchers/{id}/status`
- **Role yêu cầu**: `@PreAuthorize("hasRole('SELLER')")`
- **Request Body**:
```json
{
  "status": "PAUSED"
}
```
- **Quy tắc nghiệp vụ**:
  - Kiểm tra quyền sở hữu: voucher `{id}` phải thuộc `storeId` của người bán hiện tại.
  - Chỉ cho phép chuyển đổi giữa `ACTIVE` <-> `PAUSED`. Nếu voucher đã `EXPIRED` thì không cho phép mở lại.
- **Mã lỗi**: `VOUCHER_NOT_FOUND` (404), `NOT_STORE_OWNER` (403), `VOUCHER_ALREADY_EXPIRED` (400).
- **Màn hình FE sử dụng**: `/seller/vouchers` (`src/app/seller/vouchers/page.tsx`).

---

### 2.3. Quản trị Voucher toàn sàn & Chỉ số KPIs (Admin)
- **Ưu tiên**: P1
- **Chức năng**: Admin xem toàn bộ voucher trên sàn (cả sàn lẫn shop) và thống kê hiệu quả ngân sách khuyến mãi.
- **Method & URL**:
  - `GET /api/v1/admin/vouchers` (phân trang, lọc theo scope: `PLATFORM` | `STORE`, trạng thái)
  - `GET /api/v1/admin/vouchers/kpis` (tổng ngân sách đã dùng, số lượt sử dụng, tỷ lệ chuyển đổi)
- **Role yêu cầu**: `@PreAuthorize("hasRole('ADMIN')")`
- **Controller**: `AdminVoucherController.java`
- **Response mẫu cho KPIs**:
```json
{
  "success": true,
  "code": 200,
  "message": "Lấy KPIs voucher thành công",
  "data": {
    "totalVouchers": 45,
    "activeVouchers": 28,
    "totalDistributedBudget": 500000000,
    "usedBudget": 182400000,
    "totalUsages": 18420
  },
  "timestamp": "2026-10-18T20:00:00Z"
}
```
- **Màn hình FE sử dụng**: `/admin/vouchers` (`src/app/admin/vouchers/page.tsx`).

---

### 2.4. Chuẩn hóa Toàn diện Luồng Rút tiền Ví Seller (Tài khoản ngân hàng & Quy trình Duyệt Admin)
- **Ưu tiên**: P1
- **Hiện trạng xác minh**:
  - `WalletServiceImpl.java:54-62` đang **hardcode thông tin ngân hàng MB Bank** (`MB Bank`, số tài khoản `"9999" + store.getId() + "888"`).
  - `WalletServiceImpl.java:114-118`: Khi Seller gọi `POST /api/v1/seller/wallet/withdraw`, BE trừ tiền ngay lập tức khỏi `balance` và ghi nhận giao dịch `WITHDRAWAL` mà không đưa qua bước kiểm duyệt.
  - `WalletTransactionMapper.java:14`: Hardcode `status: "SUCCESS"` cho tất cả các giao dịch.
  - Hoàn toàn chưa có `AdminWalletController` hoặc endpoint nào cho Admin duyệt chi chuyển khoản.
  - Không có `Idempotency-Key` bảo vệ, dẫn tới rủi ro double-click tạo 2 lệnh rút liên tiếp nếu số dư đủ.

- **Thiết kế CSDL & Schema Đề xuất**:
  1. **Bảng `store_bank_accounts` (1 Store - 1 Bank Account chính)**:
     ```sql
     CREATE TABLE store_bank_accounts (
         id BIGSERIAL PRIMARY KEY,
         store_id BIGINT NOT NULL UNIQUE REFERENCES stores(id) ON DELETE CASCADE,
         bank_name VARCHAR(100) NOT NULL,
         bank_code VARCHAR(20) NOT NULL, -- Ví dụ: VCB, MB, TCB
         account_number VARCHAR(30) NOT NULL,
         account_holder VARCHAR(100) NOT NULL, -- Tên in hoa không dấu
         branch VARCHAR(150),
         is_verified BOOLEAN NOT NULL DEFAULT FALSE,
         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
     );
     CREATE INDEX idx_store_bank_accounts_store_id ON store_bank_accounts(store_id);
     ```
  2. **Bảng `wallet_withdrawal_requests` (Lệnh rút tiền)**:
     ```sql
     CREATE TABLE wallet_withdrawal_requests (
         id BIGSERIAL PRIMARY KEY,
         request_code VARCHAR(50) NOT NULL UNIQUE, -- WDR-YYMMDD-XXXX
         wallet_id BIGINT NOT NULL REFERENCES wallets(id),
         store_id BIGINT NOT NULL REFERENCES stores(id),
         amount NUMERIC(15, 2) NOT NULL CHECK (amount >= 50000.00),
         status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED, CANCELLED
         bank_name VARCHAR(100) NOT NULL,
         bank_code VARCHAR(20) NOT NULL,
         account_number VARCHAR(30) NOT NULL,
         account_holder VARCHAR(100) NOT NULL,
         branch VARCHAR(150),
         seller_note VARCHAR(255),
         admin_note VARCHAR(255),
         idempotency_key VARCHAR(100) UNIQUE,
         approved_by BIGINT REFERENCES users(id),
         approved_at TIMESTAMP WITH TIME ZONE,
         rejected_by BIGINT REFERENCES users(id),
         rejected_at TIMESTAMP WITH TIME ZONE,
         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
     );
     CREATE INDEX idx_withdrawal_wallet_status ON wallet_withdrawal_requests(wallet_id, status);
     CREATE INDEX idx_withdrawal_status ON wallet_withdrawal_requests(status);
     ```

- **Sơ đồ Trạng thái (State Machine)**:
  ```
                 [Seller Tạo Lệnh Rút]
             (balance -> frozen_balance)
                          │
                          ▼
                     ┌─────────┐
                     │ PENDING │
                     └────┬────┘
           ┌──────────────┴──────────────┐
  [Admin Duyệt Chi]             [Admin Từ Chối Chi]
  (frozen_balance trừ hẳn)      (frozen_balance hoàn về balance)
           │                             │
           ▼                             ▼
     ┌──────────┐                  ┌──────────┐
     │ APPROVED │                  │ REJECTED │
     └──────────┘                  └──────────┘
  ```

- **Đặc tả Chi tiết Danh mục Endpoints**:

  #### 1. Quản lý Tài khoản Ngân hàng Shop
  - **`GET /api/v1/seller/wallet/bank-account`**: Lấy thông tin tài khoản ngân hàng của shop.
    - Role: `@PreAuthorize("hasRole('SELLER')")`
    - Response: `ApiResponse<SellerBankAccountResponse>`
  - **`PUT /api/v1/seller/wallet/bank-account`**: Thêm mới hoặc cập nhật tài khoản ngân hàng.
    - Role: `@PreAuthorize("hasRole('SELLER')")`
    - Request Body:
      ```json
      {
        "bankName": "Ngân hàng Ngoại thương Việt Nam (Vietcombank)",
        "bankCode": "VCB",
        "accountNumber": "102988391204",
        "accountHolder": "NGUYEN VAN BAN",
        "branch": "Chi nhánh Thủ Đức, TP.HCM"
      }
      ```
    - Validation: `accountNumber` từ 6 - 25 ký tự số, `accountHolder` chữ hoa không dấu (`^[A-Z ]+$`).

  #### 2. Tạo Yêu cầu Rút tiền (Seller)
  - **`POST /api/v1/seller/wallet/withdraw`**:
    - Role: `@PreAuthorize("hasRole('SELLER')")`
    - Header: `Idempotency-Key` (UUID, optional/khuyến nghị).
    - Request Body:
      ```json
      {
        "amount": 500000.00,
        "note": "Rút doanh thu đợt 1 tháng 10"
      }
      ```
    - Quy tắc xử lý nguyên tử:
      1. Khóa bi quan hàng ví: `walletRepository.findByStoreIdWithLock(storeId)` (`PESSIMISTIC_WRITE`).
      2. Kiểm tra `store_bank_accounts`: bắt buộc shop đã cấu hình tài khoản ngân hàng.
      3. Kiểm tra hạn mức: `amount >= 50,000 VND` và `amount <= wallet.balance`.
      4. Kiểm tra `idempotency_key` (nếu truyền): nếu đã tồn tại thì trả về thông tin request cũ thay vì trừ tiếp.
      5. Cập nhật số dư:
         - `wallet.balance = wallet.balance - amount`
         - `wallet.frozenBalance = wallet.frozenBalance + amount`
      6. Tạo `wallet_withdrawal_requests` với trạng thái `PENDING`.
      7. Ghi `wallet_transactions`: `type = "WITHDRAWAL_REQUEST"`, `amount = -amount`, `balance_after = wallet.balance`, `status = "PENDING"`.

  #### 3. Danh sách Duyệt Rút tiền (Admin)
  - **`GET /api/v1/admin/wallets/withdrawals`**:
    - Role: `@PreAuthorize("hasRole('ADMIN')")`
    - Query params: `status` (`PENDING`, `APPROVED`, `REJECTED`, `ALL`), `storeId`, `page`, `size`, `sort`.
    - Response: `ApiResponse<PageResponse<AdminWithdrawalResponse>>`.

  #### 4. Phê duyệt Lệnh Rút tiền (Admin)
  - **`PATCH /api/v1/admin/wallets/withdrawals/{id}/approve`**:
    - Role: `@PreAuthorize("hasRole('ADMIN')")`
    - Request Body: `{ "adminNote": "Đã chuyển khoản qua lệnh UNC-9921" }` (optional).
    - Quy tắc xử lý:
      1. Khóa bi quan bản ghi ví tương ứng (`PESSIMISTIC_WRITE`).
      2. Kiểm tra trạng thái lệnh rút phải là `PENDING`.
      3. Trừ số dư đóng băng: `wallet.frozenBalance = wallet.frozenBalance - request.amount`.
      4. Cập nhật `wallet_withdrawal_requests`: `status = "APPROVED"`, `approved_by = adminId`, `approved_at = now()`.
      5. Cập nhật trạng thái `wallet_transactions` liên kết sang `SUCCESS`.

  #### 5. Từ chối Lệnh Rút tiền (Admin)
  - **`PATCH /api/v1/admin/wallets/withdrawals/{id}/reject`**:
    - Role: `@PreAuthorize("hasRole('ADMIN')")`
    - Request Body:
      ```json
      {
        "reason": "Số tài khoản thụ hưởng không tồn tại hoặc sai tên chủ sở hữu tại MB Bank"
      }
      ```
    - Validation: `reason` bắt buộc (tối thiểu 5 ký tự).
    - Quy tắc xử lý:
      1. Khóa bi quan bản ghi ví (`PESSIMISTIC_WRITE`).
      2. Kiểm tra trạng thái lệnh rút phải là `PENDING`.
      3. Hoàn số dư khả dụng:
         - `wallet.frozenBalance = wallet.frozenBalance - request.amount`
         - `wallet.balance = wallet.balance + request.amount`
      4. Cập nhật `wallet_withdrawal_requests`: `status = "REJECTED"`, `rejected_by = adminId`, `rejected_at = now()`, `admin_note = reason`.
      5. Ghi `wallet_transactions` hoàn trả: `type = "WITHDRAWAL_REFUND"`, `amount = +amount`, `balance_after = wallet.balance`, `status = "SUCCESS"`.

- **Mã lỗi đặc thù**:
  - `WALLET_BANK_NOT_CONFIGURED` (400, "Vui lòng cấu hình tài khoản ngân hàng trước khi rút tiền").
  - `WALLET_INSUFFICIENT` (400, "Số dư khả dụng không đủ để thực hiện rút tiền").
  - `MINIMUM_WITHDRAWAL_AMOUNT` (400, "Số tiền rút tối thiểu là 50,000 VND").
  - `WITHDRAWAL_NOT_FOUND` (404, "Không tìm thấy yêu cầu rút tiền").
  - `WITHDRAWAL_ALREADY_PROCESSED` (409, "Yêu cầu rút tiền này đã được xử lý trước đó").

- **Tiêu chí Nghiệm thu (Acceptance Criteria)**:
  - *AC-1*: Seller có số dư 1.000.000 ₫, gọi rút 400.000 ₫ -> `balance` còn 600.000 ₫, `frozenBalance` tăng lên 400.000 ₫, trạng thái lệnh rút là `PENDING`.
  - *AC-2*: Admin duyệt lệnh rút -> `frozenBalance` trừ 400.000 ₫ về 0, `balance` giữ nguyên 600.000 ₫, lệnh chuyển `APPROVED`.
  - *AC-3*: Admin từ chối lệnh rút -> `frozenBalance` trừ 400.000 ₫, `balance` tăng lại lên 1.000.000 ₫, lệnh chuyển `REJECTED`, ghi rõ lý do.
  - *AC-4*: Gửi 2 request rút đồng thời với cùng `Idempotency-Key` -> chỉ tạo 1 lệnh và chỉ trừ tiền 1 lần duy nhất.

---

### 2.5. Chuẩn hóa Phân quyền `StoreController` (`/stores/me/**`) & Vòng đời Cấp vai trò `SELLER`
- **Ưu tiên**: P1
- **Hiện trạng xác minh**:
  - `SecurityConfig.java:94-96`: Chỉ cấu hình phân quyền theo tiền tố URL `/api/v1/seller/**` (hasRole `SELLER`) và `/api/v1/admin/**` (hasRole `ADMIN`). Toàn bộ cụm `/api/v1/stores/**` rơi vào `.anyRequest().authenticated()` (ngoại trừ 2 endpoint GET public `featured` và `{id}`).
  - `StoreController.java`: Ngoại trừ duy nhất `GET /sidebar/summary` (dòng 64) có `@PreAuthorize("hasRole('SELLER')")`, tất cả các endpoint còn lại (`/me`, `/me/addresses`, `/me/addresses/{id}`, `/me/logo`) đều **thiếu annotation phân quyền**.
  - Khi một tài khoản chỉ có role `BUYER` (chưa có store) gọi các endpoint `/me`: request lọt qua filter chain của Spring Security, vào tận `StoreServiceImpl`, gọi `getStoreByUserIdOrThrow` và ném `STORE_NOT_FOUND` (HTTP 404). Hành vi này vi phạm nguyên tắc phòng thủ theo chiều sâu (Defense-in-depth), tạo tải vô ích lên DB và trả mã 404 thay vì 403.
  - Khi Seller A gọi `PUT/DELETE /stores/me/addresses/{id}` với ID địa chỉ của Seller B: được chặn an toàn ở `StoreServiceImpl.java:323-325` với ngoại lệ `STORE_ADDRESS_ACCESS_DENIED` (HTTP 403 Forbidden).

- **Quy tắc Chuẩn hóa Phân quyền**:
  1. **Endpoint `POST /api/v1/stores` (Đăng ký mở shop)**:
     - Giữ nguyên `authenticated()` (Không yêu cầu `hasRole('SELLER')`), để người dùng `BUYER` có thể tự đăng ký gian hàng.
  2. **Endpoint `GET /api/v1/stores/me` (Xem thông tin shop cá nhân)**:
     - Giữ nguyên `authenticated()`. Cho phép chủ shop xem thông tin khi gian hàng đang ở trạng thái `PENDING` (chờ Admin duyệt).
     - Nếu user chưa từng tạo store: trả về HTTP 404 `STORE_NOT_FOUND`.
  3. **Tất cả các endpoint `/me` còn lại**:
     - Bổ sung `@PreAuthorize("hasRole('SELLER')")`:
       - `PUT /api/v1/stores/me` (Cập nhật hồ sơ shop)
       - `GET /api/v1/stores/me/addresses` (Danh sách địa chỉ kho)
       - `POST /api/v1/stores/me/addresses` (Tạo địa chỉ kho)
       - `PUT /api/v1/stores/me/addresses/{addressId}` (Cập nhật địa chỉ kho)
       - `DELETE /api/v1/stores/me/addresses/{addressId}` (Xóa địa chỉ kho)
       - `PATCH /api/v1/stores/me/addresses/{addressId}/default` (Đặt kho mặc định)
       - `POST /api/v1/stores/me/logo` (Upload logo shop)

- **Quy định Vòng đời Cấp / Thu hồi Vai trò `SELLER`**:
  1. **Khi Đăng ký (`registerStore`)**:
     - Gian hàng tạo mới có `status = "PENDING"`.
     - Người dùng **chưa được cấp vai trò `SELLER`** (vẫn chỉ có `ROLE_BUYER`).
  2. **Khi Admin Phê duyệt (`APPROVED`)**:
     - Trong `StoreServiceImpl.java:158-161`: Hệ thống gọi `assignSellerRole(store.getUser())`.
     - Thêm bản ghi vào `user_roles` liên kết với role `SELLER`.
     - Cần thêm cơ chế làm mới thông tin quyền (refresh authentication/JWT context) để người dùng có ngay quyền SELLER mà không cần đăng xuất/đăng nhập lại.
  3. **Khi Admin Khóa hoặc Tạm dừng (`BANNED` / `SUSPENDED`)**:
     - Thu hồi vai trò `SELLER` khỏi người dùng (xóa hoặc vô hiệu hóa bản ghi `user_roles`).
     - Đẩy thông báo thu hồi quyền vào Redis cache / WebSocket để ngắt phiên làm việc Seller Center của tài khoản.

---

## 3. NHÓM ƯU TIÊN P2 (MỞ RỘNG)

### 3.1. Biểu đồ doanh thu Seller Analytics
- **Method & URL**: `GET /api/v1/seller/analytics/revenue-chart?period=7d`
- **Role**: `@PreAuthorize("hasRole('SELLER')")`
- **Mô tả**: Trả về dữ liệu chuỗi thời gian doanh thu và đơn hàng của shop trong 7 ngày / 30 ngày / 12 tháng qua để vẽ biểu đồ tại `/seller`.

### 3.2. Báo cáo Đối soát Tài chính sàn (Reconciliation)
- **Method & URL**: `GET /api/v1/admin/reconciliation/summary`, `POST /api/v1/admin/reconciliation/export`
- **Role**: `@PreAuthorize("hasRole('ADMIN')")`
- **Mô tả**: Báo cáo tổng doanh thu toàn sàn, tổng phí sàn 5% đã thu, số dư cần thanh toán cho các Seller.

### 3.3. Phân quyền Nhân viên Shop (Seller Staff)
- **Method & URL**: `/api/v1/seller/staff/**`
- **Mô tả**: Cho phép chủ shop thêm tài khoản phụ và phân quyền giới hạn (ví dụ nhân viên chỉ xem đơn, không xem ví).

### 3.4. Thống kê số lượng SKU Flash Sale theo Slot (Flash Sale SKU Counts)
- **Ưu tiên**: P2
- **Method & URL**: `GET /api/v1/admin/flash-sales/items/counts?slotId={slotId}`
- **Role yêu cầu**: `@PreAuthorize("hasRole('ADMIN')")`
- **Mô tả**: Trả về số lượng tổng quan các SKU theo trạng thái trong slot (`pending`, `approved`, `rejected`, `all`) thay vì yêu cầu FE phải tự tính cục bộ trên trang dữ liệu đã tải.
- **Response mẫu**:
```json
{
  "success": true,
  "code": 200,
  "data": {
    "all": 45,
    "pending": 12,
    "approved": 28,
    "rejected": 5
  }
}
```

### 3.5. Từ chối hàng loạt SKU Flash Sale (Batch Reject Flash Sale Items)
- **Ưu tiên**: P2
- **Method & URL**: `POST /api/v1/admin/flash-sales/items/batch-reject`
- **Role yêu cầu**: `@PreAuthorize("hasRole('ADMIN')")`
- **Request Body**:
```json
{
  "itemIds": [103, 104, 105],
  "reason": "Sản phẩm không đáp ứng mức chiết khấu tối thiểu của chương trình"
}
```
- **Mô tả**: Cho phép Admin từ chối nhiều SKU cùng lúc kèm lý do chung (tối thiểu 5 ký tự), tránh việc FE phải gọi tuần tự từng request đơn lẻ.

---

## 4. CÁC LỖ HỔNG NGHIỆP VỤ FLASH SALE CẦN KHẮC PHỤC TRÊN BACKEND

| STT | Lỗ hổng phát hiện | Bằng chứng file & dòng | Rủi ro thực tế | Hành vi Backend bắt buộc |
|---|---|---|---|---|
| 1 | **Pre-warm dùng `setIfAbsent` bỏ qua SKU duyệt sau** | `FlashSaleItemService.java:252` | Nếu Admin duyệt thêm SKU sau khi đã chạy pre-warm, lệnh `setIfAbsent` bỏ qua. Nếu slot đang diễn ra, SKU mới duyệt sẽ không bán được trên Redis. | Trong hàm `approveItem`: nếu slot của item đang có trạng thái `ACTIVE` hoặc đã pre-warm, **tự động gọi Redis `SET` để bổ sung tồn kho cho SKU đó ngay lập tức**. |
| 2 | **Thiếu cơ chế hoàn kho khi Tạm dừng Khẩn cấp (Emergency Pause)** | Chưa có code (thiếu chức năng) | Nếu Admin dừng bán khẩn cấp một phiên sale, số lượng hàng tồn ế trên Redis không được hoàn trả về `ProductVariant.stockQuantity` trong PostgreSQL, làm mất hàng của Seller. | Khi dừng khẩn cấp: Đọc số dư tồn kho còn lại trên Redis, hoàn trả về `product_variants.stock_quantity`, xóa key Redis và phát STOMP topic `/topic/flash-sale/slot/{id}/status`. |
| 3 | **Thiếu `@PreAuthorize("hasRole('SELLER')")` tại `StoreController`** | `StoreController.java:53, 73, 101, 110, 121, 132` | Bất kỳ user đã đăng nhập nào (kể cả BUYER) đều gửi được request vào controller, lọt sâu vào tầng Service rồi mới bị chặn bởi query DB (`getStoreByUserIdOrThrow`). Dù không bị lộ dữ liệu nhưng vi phạm Defense-in-depth. | Bổ sung `@PreAuthorize("hasRole('SELLER')")` trên class hoặc từng method của `StoreController` cho các endpoint `/me/**` (ngoại trừ `POST /api/v1/stores` cho đăng ký). |
| 4 | **Duyệt hàng loạt (`batch-approve`) không có Savepoint** | `FlashSaleItemService.java:317-338` | Trong cùng một `@Transactional`, nếu một câu lệnh SQL gây lỗi DB nghiêm trọng (như Foreign Key hoặc Check constraint) thì Transaction có thể bị đánh dấu `RollbackOnly`, khiến toàn bộ các item sau đó bị lỗi dây chuyền. | Sử dụng transaction độc lập cho từng item (`Propagation.REQUIRES_NEW`) trong một service helper riêng để đảm bảo tính cô lập khi xử lý hàng loạt. |
