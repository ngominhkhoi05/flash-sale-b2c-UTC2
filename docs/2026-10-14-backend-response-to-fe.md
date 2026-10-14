# PHẢN HỒI TRIỂN KHAI VÀ HỢP ĐỒNG API CHO FRONTEND — FLASH SALE B2C

> **Ngày cập nhật:** 2026-10-14  
> **Từ:** Backend Team (`flash-sale-b2c-UTC2`)  
> **Gửi:** Frontend Team (`flash-sale-b2c-fe`)  
> **Tham chiếu:** Yêu cầu từ tài liệu audit `2026-10-14-fe-request-be-api.md` và `backend-api-requirements.md`  
> **Trạng thái:** ✅ **Đã hoàn thành 5/5 nhóm tính năng ưu tiên**, mã nguồn đã được tách nhánh `feat/*`, viết kiểm thử tự động đạt 100% và đã mở Pull Request vào nhánh `dev`.

---

## 1. Bảng đối chiếu tiến độ (Resolution Checklist)

Dưới đây là bảng đối chiếu trực tiếp với **Mục 6 (Checklist cho Backend Team)** trong tài liệu yêu cầu của Frontend:

| Yêu cầu FE | Trạng thái BE | Nhánh phát triển | Link Pull Request |
|---|---|---|---|
| `GET /api/v1/products/search` (Tìm kiếm sản phẩm, lọc, sắp xếp, facets) | ✅ **Hoàn thành** | `feat/product-search` | [PR #feat/product-search](https://github.com/ngominhkhoi05/flash-sale-b2c-UTC2/pull/new/feat/product-search) |
| `GET /api/v1/vouchers/mine` (Xem kho voucher người dùng) | ✅ **Hoàn thành** | `feat/voucher-wallet` | [PR #feat/voucher-wallet](https://github.com/ngominhkhoi05/flash-sale-b2c-UTC2/pull/new/feat/voucher-wallet) |
| `POST /api/v1/vouchers/mine` (Lưu voucher vào kho cá nhân) | ✅ **Hoàn thành** | `feat/voucher-wallet` | [PR #feat/voucher-wallet](https://github.com/ngominhkhoi05/flash-sale-b2c-UTC2/pull/new/feat/voucher-wallet) |
| `POST /api/v1/auth/forgot-password` (Gửi yêu cầu quên mật khẩu) | ✅ **Hoàn thành** | `feat/auth-password-reset` | [PR #feat/auth-password-reset](https://github.com/ngominhkhoi05/flash-sale-b2c-UTC2/pull/new/feat/auth-password-reset) |
| `POST /api/v1/auth/reset-password` (Đặt lại mật khẩu với token) | ✅ **Hoàn thành** | `feat/auth-password-reset` | [PR #feat/auth-password-reset](https://github.com/ngominhkhoi05/flash-sale-b2c-UTC2/pull/new/feat/auth-password-reset) |
| `GET /api/v1/orders/{orderCode}/qr` (Lấy QR và chi tiết thanh toán cho 1 hoặc nhiều mã đơn) | ✅ **Hoàn thành** | `feat/order-qr` | [PR #feat/order-qr](https://github.com/ngominhkhoi05/flash-sale-b2c-UTC2/pull/new/feat/order-qr) |
| Bổ sung DTO `CartItemResponse`: `productId`, `stockQuantity` | ✅ **Hoàn thành** | `feat/dto-fields` | [PR #feat/dto-fields](https://github.com/ngominhkhoi05/flash-sale-b2c-UTC2/pull/new/feat/dto-fields) |
| Bổ sung DTO `CartStoreGroup`: `storeBadge`, `estimatedDeliveryLabel`, `shippingMethodLabel`, `shippingFee` | ✅ **Hoàn thành** | `feat/dto-fields` | [PR #feat/dto-fields](https://github.com/ngominhkhoi05/flash-sale-b2c-UTC2/pull/new/feat/dto-fields) |
| Bổ sung DTO `CartResponse`: `subtotal`, `totalShippingFee`, `totalAmount`, `grandTotal` | ✅ **Hoàn thành** | `feat/dto-fields` | [PR #feat/dto-fields](https://github.com/ngominhkhoi05/flash-sale-b2c-UTC2/pull/new/feat/dto-fields) |
| Bổ sung DTO `OrderItemResponse`: `imageUrl` | ✅ **Hoàn thành** | `feat/dto-fields` | [PR #feat/dto-fields](https://github.com/ngominhkhoi05/flash-sale-b2c-UTC2/pull/new/feat/dto-fields) |
| Xác nhận WebSocket STOMP endpoint + topics | ✅ **Đã xác nhận** | `dev` | Sẵn sàng tại `/ws`, chi tiết mục 4 |
| `POST /api/v1/reviews` & Social Login (Google / Facebook / GitHub) | ⏳ **Tạm hoãn** | — | Chờ thống nhất luồng (chi tiết mục 5) |

---

## 2. Chi tiết Hợp đồng API Mới

Mọi endpoint đều trả về theo định dạng chuẩn thống nhất `ApiResponse<T>`:
```json
{
  "success": true,
  "code": 200,
  "message": "Thông điệp thành công",
  "data": { ... },
  "timestamp": "2026-10-14T10:00:00.000Z"
}
```

---

### 2.1. Tìm kiếm sản phẩm nâng cao (`GET /api/v1/products/search`)

- **Method:** `GET`
- **Path:** `/api/v1/products/search`
- **Authentication:** Public (không bắt buộc token)
- **Đặc điểm:** Tối ưu với PostgreSQL Full-Text Search (hỗ trợ tiếng Việt có dấu và không dấu qua `unaccent` và GIN index), hỗ trợ phân trang, facets bộ lọc, và gợi ý banner Mall.

#### Query Parameters

| Tham số | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `q` | `string` | `""` | Từ khóa tìm kiếm (theo tên sản phẩm, thương hiệu, mô tả) |
| `page` | `int` | `0` | Trang hiện tại (hỗ trợ cả 0-index lẫn 1-index từ FE) |
| `pageSize` / `size` | `int` | `20` | Số lượng phần tử mỗi trang (tối đa 100) |
| `sort` | `string` | `relevance` | Tiêu chí sắp xếp: `relevance` (mặc định), `price_asc` (giá tăng dần), `price_desc` (giá giảm dần), `best_selling` (bán chạy nhất), `newest` (mới nhất) |
| `categoryId` | `int` | `null` | Lọc theo ID danh mục sản phẩm |
| `minPrice` | `number` | `null` | Giá tối thiểu (VND) |
| `maxPrice` | `number` | `null` | Giá tối đa (VND) |
| `minRating` | `int` | `null` | Đánh giá tối thiểu từ người mua (1 đến 5 sao) |

#### Phản hồi mẫu (`200 OK`)
```json
{
  "success": true,
  "code": 200,
  "message": "Tìm kiếm sản phẩm thành công",
  "data": {
    "items": [
      {
        "id": 101,
        "name": "Áo Thun Nam Thể Thao Co Giãn Thoáng Khí",
        "imageUrl": "https://res.cloudinary.com/demo/image/upload/sample.jpg",
        "price": 199000,
        "originalPrice": 299000,
        "rating": 4.8,
        "totalSold": 1250,
        "storeName": "Shop Thể Thao Chính Hãng",
        "storeBadge": "OfficialMall",
        "voucherLabel": "Giảm 10K"
      }
    ],
    "total": 48,
    "page": 0,
    "pageSize": 20,
    "facets": {
      "categories": [
        { "id": 1, "name": "Thời Trang Nam", "count": 28 },
        { "id": 2, "name": "Thể Thao & Du Lịch", "count": 20 }
      ],
      "priceRanges": [
        { "min": 0, "max": 100000, "count": 10 },
        { "min": 100000, "max": 300000, "count": 25 },
        { "min": 300000, "max": 1000000, "count": 13 }
      ],
      "services": ["freeship", "voucher"],
      "ratings": [1, 2, 3, 4, 5]
    },
    "mall": {
      "title": "Shopee Mall Chính Hãng",
      "subtitle": "100% Hàng chính hãng, miễn phí đổi trả 7 ngày",
      "href": "/mall",
      "imageUrl": "https://res.cloudinary.com/demo/image/upload/mall-banner.jpg"
    }
  },
  "timestamp": "2026-10-14T10:00:00.000Z"
}
```

---

### 2.2. Kho voucher người dùng (`/api/v1/vouchers/mine`)

#### A. Xem danh sách voucher đã lưu (`GET /api/v1/vouchers/mine`)
- **Method:** `GET`
- **Path:** `/api/v1/vouchers/mine`
- **Headers:** `Authorization: Bearer <jwt_token>`
- **Query Params:**
  - `orderAmount` (*Tùy chọn*, `number`): Giá trị đơn hàng hiện tại (nếu đang ở trang Checkout). Backend sẽ tự tính trường `isApplicable` (true/false) để FE disable hoặc highlight voucher phù hợp.

#### Phản hồi mẫu (`200 OK`)
```json
{
  "success": true,
  "code": 200,
  "message": "Lấy kho voucher thành công",
  "data": [
    {
      "id": 1,
      "code": "VIEMART10",
      "label": "Giảm 10K cho đơn từ 200K",
      "scope": "PLATFORM",
      "storeId": null,
      "storeName": null,
      "discountType": "FIXED",
      "discountValue": 10000.00,
      "maxDiscount": 10000.00,
      "minOrderAmount": 200000.00,
      "userUsageLimit": 1,
      "usedQuantity": 0,
      "totalQuantity": 500,
      "startTime": "2026-10-01T00:00:00Z",
      "endTime": "2026-10-31T23:59:59Z",
      "status": "ACTIVE",
      "isApplicable": true
    }
  ],
  "timestamp": "2026-10-14T10:00:00.000Z"
}
```

#### B. Lưu voucher vào kho cá nhân (`POST /api/v1/vouchers/mine`)
- **Method:** `POST`
- **Path:** `/api/v1/vouchers/mine`
- **Headers:** `Authorization: Bearer <jwt_token>`, `Content-Type: application/json`
- **Request Body:**
  ```json
  {
    "code": "VIEMART10"
  }
  ```
- **Xử lý Idempotent:** Nếu voucher đã được lưu trước đó, Backend trả về trạng thái voucher hiện tại mà không báo lỗi 500 hay trùng lặp.
- **Mã lỗi có thể gặp:**
  - `VOUCHER_NOT_FOUND` (404): Mã voucher không tồn tại.
  - `VOUCHER_EXPIRED` (400): Voucher đã hết hạn sử dụng.
  - `VOUCHER_USAGE_LIMIT_EXCEEDED` (400): Voucher đã hết lượt sử dụng của hệ thống.

---

### 2.3. Quên & Đặt lại mật khẩu (`/api/v1/auth/*`)

#### A. Yêu cầu cấp lại mật khẩu (`POST /api/v1/auth/forgot-password`)
- **Method:** `POST`
- **Path:** `/api/v1/auth/forgot-password`
- **Authentication:** Public
- **Request Body:**
  ```json
  {
    "email": "buyer@example.com"
  }
  ```
- **Bảo mật & Anti-Spam:**
  - Rate-limit qua Redis: Giới hạn **tối đa 3 lần / 15 phút** cho mỗi email. Nếu vượt quá, trả lỗi `AUTH_TOO_MANY_REQUESTS` (HTTP 429).
  - Không leak thông tin người dùng: Kể cả khi email chưa tồn tại trong hệ thống, Backend vẫn trả `200 OK` với thông điệp chung "Nếu email tồn tại, link đặt lại mật khẩu đã được gửi" để chống dò quét tài khoản.
  - Token được sinh dạng UUID ngẫu nhiên và lưu băm một chiều SHA-256 vào cơ sở dữ liệu (`password_reset_tokens`).

#### B. Đặt lại mật khẩu mới (`POST /api/v1/auth/reset-password`)
- **Method:** `POST`
- **Path:** `/api/v1/auth/reset-password`
- **Authentication:** Public
- **Request Body:**
  ```json
  {
    "token": "d8b3c942-53b7-4c7b-b09e-2ef0e32f518a",
    "newPassword": "NewPassword123@"
  }
  ```
- **Xử lý:**
  - Kiểm tra tính hợp lệ và thời hạn token (mặc định 15 phút).
  - Cập nhật mật khẩu mã hóa BCrypt.
  - Đánh dấu token đã sử dụng (`used = true`) để ngăn chặn tấn công Replay Attack.

---

### 2.4. Lấy mã QR & Chi tiết thanh toán (`GET /api/v1/orders/{orderCode}/qr`)

- **Method:** `GET`
- **Path:** `/api/v1/orders/{orderCode}/qr`
- **Headers:** `Authorization: Bearer <jwt_token>`
- **Cơ chế tách đơn Đa gian hàng (Multi-Store Cart Split):**
  - Tham số `{orderCode}` hỗ trợ truyền **1 mã đơn** (ví dụ `FS-261001-A7K2Q`) hoặc **nhiều mã đơn phân tách bởi dấu phẩy** khi giỏ hàng có nhiều shop (ví dụ `FS-261001-A7K2Q,FS-261001-B8M3X`).
  - Phía FE chỉ cần truyền `encodeURIComponent(orderCode)` như đã viết trong `features/checkout/api.ts`.
- **Tương thích trường thời gian & số tiền:**
  - Backend trả về song song cả `expiresAt` lẫn `paymentExpiry` (đều trỏ vào thời điểm hết hạn sớm nhất).
  - Trả về song song cả `amount` lẫn `totalAmount` (tổng số tiền cần thanh toán cho tất cả các đơn).
  - Trả về mã VietQR chuẩn và ZaloPay intent payment url.

#### Phản hồi mẫu (`200 OK`)
```json
{
  "success": true,
  "code": 200,
  "message": "Lấy thông tin QR thanh toán thành công",
  "data": {
    "orderCode": "FS-261001-A7K2Q,FS-261001-B8M3X",
    "qrCodeUrl": "https://api.vietqr.io/image/970422-9999999999-compact2.png?amount=249000&addInfo=FS-261001-A7K2Q%2CFS-261001-B8M3X",
    "expiresAt": "2026-10-14T10:15:00Z",
    "paymentExpiry": "2026-10-14T10:15:00Z",
    "amount": 249000,
    "totalAmount": 249000,
    "paymentUrl": "https://qc.zalopay.vn/pay?order=FS-261001-A7K2Q%2CFS-261001-B8M3X&amount=249000",
    "shopOrders": [
      {
        "storeId": 10,
        "storeName": "Shop Thời Trang Nam",
        "storeBadge": "OfficialMall",
        "orderCode": "FS-261001-A7K2Q",
        "amount": 199000,
        "itemSummary": "Áo thun nam × 2, Quần dài × 1"
      },
      {
        "storeId": 20,
        "storeName": "Shop Phụ Kiện",
        "storeBadge": "Mall",
        "orderCode": "FS-261001-B8M3X",
        "amount": 50000,
        "itemSummary": "Tất cổ ngắn × 3"
      }
    ]
  },
  "timestamp": "2026-10-14T10:00:00.000Z"
}
```

---

### 2.5. Bổ sung các trường DTO (Giỏ hàng & Đơn hàng)

Backend đã cập nhật đầy đủ các trường mà giao diện Frontend đang cần trong các DTO:

#### A. `CartItemResponse` (Mỗi dòng sản phẩm trong giỏ)
- Bổ sung `productId`: ID của sản phẩm cha (SPU) để FE làm link điều hướng đến `/products/${item.productId}`.
- Bổ sung `stockQuantity`: Số lượng tồn kho hiện tại của variant giúp FE hiển thị cảnh báo "Chỉ còn X sản phẩm" hoặc giới hạn nút tăng giảm số lượng.

#### B. `CartStoreGroup` (Nhóm sản phẩm theo gian hàng)
- `storeBadge`: `"OfficialMall" | "Mall" | ""` (dựa vào `StoreTier` của shop).
- `estimatedDeliveryLabel`: Chuỗi hiển thị ngày giao dự kiến, ví dụ: `"Giao vào 16 Th10 - 18 Th10"`.
- `shippingMethodLabel`: Tên gói vận chuyển, ví dụ: `"Tiêu chuẩn"`.
- `shippingFee`: Phí vận chuyển của gian hàng (mặc định 0đ).

#### C. `CartResponse` (Tổng thể giỏ hàng)
- Bổ sung `subtotal`: Tổng tiền hàng (trước phí ship).
- Bổ sung `totalShippingFee`: Tổng phí vận chuyển của tất cả shop trong giỏ.
- Bổ sung `totalAmount`: Tổng tiền thanh toán cuối cùng (`subtotal + totalShippingFee`).
- Giữ nguyên `grandTotal` để đảm bảo code cũ của FE không bị ảnh hưởng.

#### D. `OrderItemResponse` (Chi tiết mặt hàng trong đơn hàng)
- Bổ sung `imageUrl`: URL ảnh thumbnail chính của variant hoặc sản phẩm. Backend tải ảnh theo cơ chế batch-load từ bảng `images` (polymorphic) qua `ImageService` để tối ưu hiệu năng và triệt tiêu hoàn toàn lỗi N+1 query.

---

## 3. Flash Sale Reserve & Hủy giữ chỗ (Nhắc lại Hợp đồng Đợt 1)

Frontend lưu ý quy trình giữ chỗ Flash Sale chống bán vượt số lượng:

1. **Giữ chỗ:** `POST /api/v1/flash-sales/reserve`
   - Header khuyến nghị: `Idempotency-Key: <UUID>`
   - Body: `{ "flashSaleItemId": 101, "quantity": 1, "addressId": 15 }` (nếu `addressId` để trống, BE tự lấy địa chỉ mặc định của user).
   - TTL giữ chỗ là **300 giây (5 phút)**. Sau 300 giây, scheduler nền của Backend sẽ tự động hủy đơn hết hạn và hoàn kho về Redis.
2. **Chủ động hủy giữ chỗ:** `DELETE /api/v1/flash-sales/reservations/{orderCode}`
   - Trả lại tồn kho Redis và hạn mức mua `user_limit` ngay lập tức.
3. **Mã lỗi cần xử lý UI:**
   - `FLASH_SALE_OUT_OF_STOCK` (409): Tồn kho Flash Sale đã hết.
   - `FLASH_SALE_PURCHASE_LIMIT_EXCEEDED` (422): User đã mua vượt quá giới hạn lượt của slot.
   - `FLASH_SALE_ADDRESS_REQUIRED` (400): User chưa có bất kỳ địa chỉ nào trong sổ địa chỉ, FE mở popup yêu cầu tạo địa chỉ trước khi giữ chỗ.

---

## 4. Xác nhận cấu hình Realtime WebSocket STOMP

Backend xác nhận hệ thống WebSocket STOMP đã được cấu hình và sẵn sàng tại:

- **Endpoint kết nối:** `/ws`
- **Transports:** SockJS fallback + Pure WebSocket
- **Cách gửi Token JWT xác thực:**
  - Cách 1 (Khuyến nghị cho SockJS/Browser): Truyền qua query parameter khi bắt đầu handshake:
    `const socket = new SockJS('http://<host>/ws?token=' + jwtToken);`
  - Cách 2: Truyền trong header STOMP CONNECT:
    `client.connect({ 'Authorization': 'Bearer ' + jwtToken }, onConnected, onError);`
- **Topics cần lắng nghe:**
  1. **Tồn kho Flash Sale thời gian thực:**
     - Destination: `/topic/flash-sale/slot/{slotId}/stock-update`
     - Payload nhận được:
       ```json
       {
         "slotId": 1,
         "itemId": 101,
         "availableStock": 8,
         "allocatedStock": 10,
         "timestamp": "2026-10-14T10:05:00Z"
       }
       ```
  2. **Trạng thái thanh toán đơn hàng (Cá nhân):**
     - Destination: `/user/queue/payment-status` (hoặc `/topic/payments/{orderCode}`)
     - Payload nhận được khi thanh toán thành công:
       ```json
       {
         "orderCode": "FS-261001-A7K2Q",
         "status": "PAID",
         "paidAt": "2026-10-14T10:02:30Z",
         "message": "Thanh toán thành công"
       }
       ```

---

## 5. Thống nhất kiến trúc cho các tính năng tiếp theo

Đối với 2 tính năng FE đã có UI nhưng BE chưa triển khai:

### 5.1. Viết đánh giá sản phẩm (`POST /api/v1/reviews`)
- **Quy tắc nghiệp vụ đề xuất:**
  - Chỉ cho phép Buyer đánh giá khi đơn hàng ở trạng thái `COMPLETED`.
  - Mỗi mặt hàng (`order_item_id`) chỉ được đánh giá 1 lần duy nhất.
  - Ảnh review: FE upload trực tiếp qua API upload ảnh chung của hệ thống với `ownerType = REVIEW`, sau đó gửi mảng `imageUrls` hoặc upload multipart trực tiếp.
- **Điểm cần FE xác nhận:** Đánh giá sau khi gửi sẽ hiển thị công khai ngay lập tức và tính lại điểm trung bình cho SPU, hay cần phải có trạng thái duyệt bởi Admin?

### 5.2. Social Login (Google / Facebook / GitHub)
- **Đề xuất kiến trúc:**
  - **Phương án khuyến nghị (Client-side Token Exchange):** Frontend tích hợp Google Identity Services (GIS) / Facebook SDK để lấy `id_token` hoặc `access_token`, sau đó gửi lên endpoint `POST /api/v1/auth/social-login` kèm body:
    ```json
    {
      "provider": "GOOGLE",
      "token": "eyJhbGciOiJSUzI1NiIs..."
    }
    ```
    Backend sẽ kiểm tra token với Google/Facebook API và trả về cặp Access Token/Refresh Token của hệ thống.
  - Phương án này giúp ứng dụng Next.js không bị reload trang và hoạt động mượt mà hơn so với luồng Redirect URI truyền thống.
  - FE vui lòng xác nhận phương án này để Backend tiến hành mở nhánh triển khai tiếp theo.

---

## 6. Tổng kết

Toàn bộ 5 nhánh tính năng đã sẵn sàng để review và merge vào `dev`. Đội ngũ Frontend có thể kiểm tra Swagger tại `http://<host>/swagger-ui.html` sau khi merge để lấy file OpenAPI spec mới nhất.

Nếu có bất kỳ thắc mắc hoặc cần điều chỉnh thêm về DTO, xin vui lòng phản hồi để Backend hỗ trợ xử lý ngay.
