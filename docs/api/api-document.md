# Tài Liệu API – Flash Sale B2C (UTC2)

> Tài liệu tổng hợp tất cả REST API và WebSocket realtime của backend **flash-sale-b2c-UTC2**, phục vụ team Frontend tích hợp vào giao diện.
>
> - **Base URL (local)**: `http://localhost:8080`
> - **Prefix chung**: `/api/v1`
> - **WebSocket endpoint**: `ws://localhost:8080/ws` (SockJS fallback: `http://localhost:8080/ws`)
> - **OpenAPI/Swagger UI**: `http://localhost:8080/swagger-ui.html`
> - **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

---

## Mục Lục

1. [Quy Ước Chung](#1-quy-ước-chung)
2. [Cấu Trúc Response Chuẩn](#2-cấu-trúc-response-chuẩn)
3. [Cấu Trúc Phân Trang](#3-cấu-trúc-phân-trang)
4. [Auth Header và Token](#4-auth-header-và-token)
5. [Danh Mục API](#5-danh-mục-api)
6. [Auth Module](#6-auth-module--apiauth)
7. [User Profile Module](#7-user-profile-module--apiusers)
8. [Address Book Module](#8-address-book-module--apiusersaddresses)
9. [Store Module](#9-store-module--apistores)
10. [Admin Store Module](#10-admin-store-module--apiadminstores)
11. [Category Module](#11-category-module--apicategories)
12. [Admin Category Module](#12-admin-category-module--apiadmincategories)
13. [Product Public Module](#13-product-public-module--apiproducts)
14. [Seller Product Module](#14-seller-product-module--apisellerproducts)
14b. [Product Review Module](#14b-product-review-module--apiproductsproductidreviews)
15. [Flash Sale Public Module](#15-flash-sale-public-module--apiflashsalesslots)
16. [Flash Sale Reservation Module](#16-flash-sale-reservation-module--apiflashsalesreservations)
17. [Admin Flash Sale Module](#17-admin-flash-sale-module--apiadminflashsales)
18. [Seller Flash Sale Module](#18-seller-flash-sale-module--apisellerflashsales)
19. [Cart Module](#19-cart-module--apicart)
20. [Order Buyer Module](#20-order-buyer-module--apiorders)
21. [Seller Order Module](#21-seller-order-module--apisellerorders)
22. [Voucher Module](#22-voucher-module--apivouchers)
23. [Seller Voucher Module](#23-seller-voucher-module--apisellervouchers)
24. [Admin Voucher Module](#24-admin-voucher-module--apiadminvouchers)
24b. [Wallet Module (Seller Draft)](#24b-wallet-module-seller-draft)
25. [WebSocket Realtime](#25-websocket-realtime)
26. [Bảng Mã Lỗi Tổng Hợp](#26-bảng-mã-lỗi-tổng-hợp)
    - [26.11 Image Errors](#2611-image-errors)
    - [26.12 Wallet Errors](#2612-wallet-errors-draft)
27. [Phân Quyền Truy Cập Theo Role](#27-phân-quyền-truy-cập-theo-role)
28. [Image Management Module (Polymorphic Storage - Cloudinary)](#28-image-management-module-polymorphic-storage---cloudinary)
29. [Banner Module](#29-banner-module--apibanners-apibanners-admin-apibanners) (Public + Admin CRUD)

---

## 1. Quy Ước Chung

| Mục | Mô tả |
|---|---|
| **HTTP Status Codes** | `200 OK`, `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`, `422 Unprocessable`, `500 Internal Server Error` |
| **Format thời gian** | ISO-8601 với timezone: `yyyy-MM-ddTHH:mm:ss.SSSXXX` (UTC mặc định) |
| **Currency** | VND, dùng kiểu `BigDecimal` cho tiền tệ (tránh sai số float) |
| **ID dạng số** | `Long` cho hầu hết entity, `Integer` cho `categoryId` |
| **Ngôn ngữ** | Tiếng Việt (message trả về) |
| **Authentication** | Stateless JWT (HS256) |
| **Phân trang mặc định** | `page=0`, `size=20`, `MAX_PAGE_SIZE=100` |
| **Tên role trong Spring Security** | `ROLE_BUYER`, `ROLE_SELLER`, `ROLE_ADMIN` |
| **Mặc định phân trang sort** | `createdAt DESC` |
| **Quản lý ảnh** | Tất cả ảnh (avatar User, logo Store, gallery Product/Variant, album Review) được quản lý qua bảng `images` Polymorphic (xem Mục 28). 5 cột ảnh cũ (`users.avatar_url`, `stores.logo_url`, `products.image_url`, `product_variants.image_url`, `product_reviews.image_urls`) đã bị xóa bởi migration V4. |

---

## 2. Cấu Trúc Response Chuẩn

Mọi API (kể cả lỗi) đều trả về cấu trúc `ApiResponse<T>` đồng nhất:

```json
{
  "success": true,
  "code": 200,
  "message": "Lấy danh sách thành công",
  "data": { /* payload tuỳ endpoint */ },
  "timestamp": "2026-10-03T14:22:35.123Z"
}
```

| Field | Type | Mô tả |
|---|---|---|
| `success` | `boolean` | `true` nếu thành công, `false` nếu lỗi |
| `code` | `int` | HTTP status code (200, 400, 401, …) |
| `message` | `string` | Thông điệp tiếng Việt, dùng để hiển thị cho user |
| `data` | `object` hoặc `null` | Payload trả về (null nếu lỗi) |
| `timestamp` | `string` | Thời điểm server trả response (ISO-8601 UTC) |

### Ví dụ Response Lỗi

```json
{
  "success": false,
  "code": 400,
  "message": "Email này đã được đăng ký trên hệ thống",
  "data": null,
  "timestamp": "2026-10-03T14:22:35.123Z"
}
```

### Trường hợp đặc biệt: Lỗi 401/403 từ Spring Security

Cấu trúc giống nhưng không có field `data` (do Security filter tự viết):

```json
{
  "success": false,
  "code": 401,
  "message": "Chưa xác thực hoặc token không hợp lệ",
  "timestamp": "2026-10-03T14:22:35.123Z"
}
```

---

## 3. Cấu Trúc Phân Trang

Các API list dùng `PageResponse<T>`:

```json
{
  "items": [ /* danh sách object */ ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 152,
  "totalPages": 8,
  "isFirst": true,
  "isLast": false,
  "hasNext": true,
  "hasPrevious": false
}
```

| Field | Type | Mô tả |
|---|---|---|
| `items` | `array` | Danh sách phần tử trong trang hiện tại |
| `pageNumber` | `int` | Trang hiện tại (zero-index) |
| `pageSize` | `int` | Kích thước trang |
| `totalElements` | `long` | Tổng số phần tử toàn bộ dataset |
| `totalPages` | `int` | Tổng số trang |
| `isFirst` / `isLast` | `boolean` | Có phải trang đầu/cuối không |
| `hasNext` / `hasPrevious` | `boolean` | Có trang tiếp theo/trước không |

**Query params chuẩn cho list APIs**: `?page=0&size=20&sort=createdAt,desc` (tuỳ endpoint)

---

## 4. Auth Header và Token

### Header yêu cầu cho API cần xác thực

```http
Authorization: Bearer <access_token>
```

### Lấy token

| Endpoint | Loại token trả về |
|---|---|
| `POST /api/v1/auth/login` | `accessToken` + `refreshToken` mới |
| `POST /api/v1/auth/register` | `accessToken` + `refreshToken` mới |
| `POST /api/v1/auth/refresh-token` | `accessToken` + `refreshToken` mới (rotation — RT cũ bị thu hồi) |
| `POST /api/v1/auth/google` | `accessToken` + `refreshToken` mới |

### Thời hạn token

| Loại | Mặc định | Cấu hình | Trả về trong `AuthResponse` |
|---|---|---|---|
| Access Token | 1 giờ (3,600,000 ms) | `jwt.expiration-ms` | `expiresIn` = **giây** (`expirationMs / 1000`) |
| Refresh Token | 7 ngày (604,800,000 ms) | `jwt.refresh-expiration-ms` | Không trả trực tiếp — FE dùng giá trị RT để gọi `/refresh-token` |

> **Lưu ý quan trọng cho FE**: Field `expiresIn` trong `AuthResponse.data` là **giây** (ví dụ `3600`), không phải millisecond. Khi set timer refresh tự động, dùng `expiresIn * 1000` để quy đổi.

### Cấu trúc JWT claim

| Claim | Kiểu | Ý nghĩa |
|---|---|---|
| `sub` | string | Email user (subject) |
| `type` | string | `ACCESS` hoặc `REFRESH` |
| `authorities` | array[string] | Chỉ có trong Access Token, ví dụ `["ROLE_BUYER"]` |
| `jti` | string (UUID) | Chỉ có trong Refresh Token — định danh RT trong Redis whitelist |
| `userId` | number (Long) | Chỉ có trong Refresh Token — id user |
| `iat` | number | Thời điểm phát hành (epoch seconds) |
| `exp` | number | Thời điểm hết hạn (epoch seconds) |

### Refresh Token Rotation

Backend áp dụng **rotation** cho Refresh Token:

1. FE gọi `POST /api/v1/auth/refresh-token` với RT hiện tại.
2. BE xác thực: kiểm tra signature, kiểm tra `type=REFRESH`, kiểm tra `exp`, kiểm tra Redis key `auth:refresh:{userId}:{jti}` tồn tại.
3. Nếu hợp lệ: BE **xóa Redis key của RT cũ**, cấp cặp Access Token + Refresh Token mới (với `jti` mới), lưu key mới vào Redis với TTL = RT expiration.
4. FE nhận về cặp token mới → phải **ghi đè** RT cũ trong storage. Nếu FE gửi lại RT cũ (đã bị thu hồi) → trả 401 `AUTH_401_REFRESH_TOKEN`.

### Khi nào nhận 401

- Không có header `Authorization`
- Token hết hạn / không hợp lệ / bị chỉnh sửa
- User bị `LOCKED` / `BANNED` (status != ACTIVE) — `CustomUserDetails.isAccountNonLocked() = false`

### Khi nào nhận 403

- Có token nhưng không đủ role (`BUYER` cố gọi `/api/v1/seller/...`)
- Có token nhưng không phải chủ sở hữu resource (vd địa chỉ của người khác)
- Tài khoản `ACTIVE` thành công nhưng Spring Security trả 403 do status check trong `CustomUserDetails.isAccountNonLocked()`

---

## 5. Danh Mục API

Tổng cộng **83 endpoints** được nhóm theo 24 controller:

| Module | Public | Buyer | Seller | Admin | Tổng |
|---|---|---|---|---|---|
| Auth | 6 | – | – | – | 6 |
| User Profile | – | 4 | – | – | 4 |
| Address Book | – | 6 | – | – | 6 |
| Store | 2 | – | 10 | – | 12 |
| Admin Store | – | – | – | 1 | 1 |
| Category | 3 | – | – | – | 3 |
| Admin Category | – | – | – | 3 | 3 |
| Product (Public) | 3 | – | – | – | 3 |
| Product Review (Public GET) | 1 | – | – | – | 1 |
| Seller Product | – | – | 8 | – | 8 |
| Flash Sale Public | 1 | – | – | – | 1 |
| Flash Sale Reservation | – | 1 | – | – | 1 |
| Admin Flash Sale | – | – | – | 6 | 6 |
| Seller Flash Sale | – | – | 1 | – | 1 |
| Cart | – | 5 | – | – | 5 |
| Order (Buyer) | – | 4 | – | – | 4 |
| Seller Order | – | – | 2 | – | 2 |
| Voucher | 3 | – | – | – | 3 |
| Seller Voucher | – | – | 2 | – | 2 |
| Admin Voucher | – | – | – | 1 | 1 |
| Banner Public | 1 | – | – | – | 1 |
| Banner Admin | – | – | – | 5 | 5 |
| Image Management | – | 4 | 1 | 1 | 6 |
| **Tổng** | **16** | **24** | **24** | **19** | **83** |

---
## 6. Auth Module `/api/v1/auth`

> **Auth controller**: `AuthController.java` · **Tag Swagger**: `Authentication`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | Đăng ký tài khoản mới (mặc định role BUYER) |
| `POST` | `/api/v1/auth/login` | Public | Đăng nhập bằng email + password |
| `POST` | `/api/v1/auth/refresh-token` | Public | Làm mới access token từ refresh token |
| `POST` | `/api/v1/auth/logout` | Public | Đăng xuất + thu hồi Refresh Token (nếu body có) |
| `GET`  | `/api/v1/auth/google/url` | Public | Lấy Google consent screen URL (scope: openid + email + profile) |
| `POST` | `/api/v1/auth/google` | Public | Đăng nhập/đăng ký qua Google OAuth2 (Authorization Code flow) |

### Vai trò (Role) và cách FE chọn

| Role trong DB | Role trong JWT | Quyền truy cập |
|---|---|---|
| `BUYER` | `ROLE_BUYER` | Mặc định khi đăng ký. Được gọi Buyer API (`/api/v1/cart`, `/api/v1/orders`, `/api/v1/flash-sales/reservations`, ...). |
| `SELLER` | `ROLE_SELLER` | Truyền `role: "SELLER"` trong body đăng ký. Được gọi Seller API (`/api/v1/seller/**`). |
| `ADMIN` | `ROLE_ADMIN` | Không thể tự đăng ký. Được cấp qua seed data hoặc DB trực tiếp. Được gọi Admin API (`/api/v1/admin/**`). |

> **Lưu ý cho FE**: Role trả về trong `AuthResponse.data.roles` LUÔN có prefix `ROLE_` (vd `["ROLE_BUYER"]`). Khi gọi các API cần role, Spring Security tự động match — FE không cần kiểm tra, chỉ cần gửi `Authorization: Bearer <accessToken>` đúng.

### `AuthResponse` schema (dùng chung cho cả 6 endpoint)

| Field | Type | Mô tả |
|---|---|---|
| `accessToken` | string (JWT) | Access Token, dùng cho header `Authorization` |
| `refreshToken` | string (JWT) | Refresh Token, dùng cho `/refresh-token` |
| `tokenType` | string | Luôn là `"Bearer"` |
| `expiresIn` | long | Thời gian sống của Access Token, **tính bằng giây** (vd `3600`) |
| `user` | UserResponse | Thông tin user (xem schema dưới) |
| `roles` | array[string] | Danh sách role có prefix `ROLE_` (vd `["ROLE_BUYER"]`) |

### `UserResponse` schema (object `user` lồng trong `AuthResponse.data.user`)

| Field | Type | Mô tả |
|---|---|---|
| `id` | Long | ID user trong DB |
| `email` | string | Email (đã lowercase) |
| `fullName` | string | Họ tên |
| `phone` | string \| null | Số ĐT (có thể null nếu user Google không cung cấp) |
| `avatarUrl` | string \| null | URL ảnh đại diện (managed bởi Image module) |
| `status` | string | `ACTIVE` \| `LOCKED` \| `BANNED` — chỉ `ACTIVE` mới đăng nhập được |
| `createdAt` | string (ISO-8601) | Thời điểm tạo tài khoản |
| `roles` | array[string] | Roles của user (có prefix `ROLE_`) |

---

### 6.1 `POST /api/v1/auth/register`

Đăng ký tài khoản mới. Tự động đăng nhập (trả về `AuthResponse` luôn).

**Quyền truy cập**: Public (không cần JWT).

**Headers (bắt buộc):**

| Header | Value |
|---|---|
| `Content-Type` | `application/json` |

**Request Body:**

| Field | Type | Required | Validation | Mô tả |
|---|---|---|---|---|
| `email` | `string` | Có | `@NotBlank`, `@Email` | Email đăng nhập. BE tự `trim().toLowerCase()` |
| `password` | `string` | Có | `@NotBlank`, `@Size(min=6)` | Mật khẩu (BE hash bằng BCrypt, không lưu plain text) |
| `fullName` | `string` | Có | `@NotBlank`, `@Size(max=100)` | Họ và tên hiển thị |
| `phone` | `string` | Không | `@Size(max=15)` | Số điện thoại (tùy chọn). Nếu có → check unique |
| `role` | `string` | Không (mặc định `BUYER`) | Không validation cụ thể | Giá trị: `BUYER` hoặc `SELLER` (không kèm `ROLE_` prefix). Nếu truyền `SELLER` → user tạo với role SELLER, có thể mở Store sau |

**HTTP Status:** `201 Created`

**Lỗi thường gặp (response bọc trong `ApiResponse`):**

| HTTP | `ApiResponse.code` | ErrorCode enum | Khi nào xảy ra |
|---|---|---|---|
| 400 | 400 | (validation) | Field `@NotBlank` / `@Email` / `@Size` fail. `message` chứa: `"Email không được để trống"` / `"Định dạng email không hợp lệ"` / `"Mật khẩu phải chứa ít nhất 6 ký tự"` / `"Họ và tên không được để trống"` / `"Số điện thoại không được vượt quá 15 ký tự"` |
| 409 | 409 | `AUTH_409_EMAIL` | `email` đã tồn tại trong DB |
| 409 | 409 | `AUTH_409_PHONE` | `phone` (nếu có) đã tồn tại trong DB |
| 500 | 500 | `SYS_500` | Lỗi hệ thống (DB down, Redis down, ...) |

**Response 201 (success):**

```json
{
  "success": true,
  "code": 201,
  "message": "Đăng ký tài khoản thành công",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "roles": ["ROLE_BUYER"],
    "user": {
      "id": 1,
      "email": "buyer@example.com",
      "fullName": "Nguyễn Văn A",
      "phone": "0987654321",
      "avatarUrl": null,
      "status": "ACTIVE",
      "createdAt": "2026-10-16T08:00:00Z",
      "roles": ["ROLE_BUYER"]
    }
  },
  "timestamp": "2026-10-16T08:00:00Z"
}
```

**FE cần gửi gì / nhận gì:**

- **Gửi**: JSON body như trên. Không cần header `Authorization`.
- **Nhận**: Lưu `data.accessToken` vào `localStorage` key `fs_access_token`, `data.refreshToken` vào `fs_refresh_token` (xem `auth-jwt.mdc` ở FE repo).
- **Bước tiếp theo**: Thêm header `Authorization: Bearer <accessToken>` cho mọi request sau. Nếu nhận 401 → thử `/refresh-token` để lấy cặp token mới, nếu vẫn 401 → logout, redirect về `/login`.

**Ví dụ Postman body:**

```json
{
  "email": "buyer.test@flash-sale.local",
  "password": "Test@123456",
  "fullName": "Test Buyer",
  "phone": "0900000001",
  "role": "BUYER"
}
```

---

### 6.2 `POST /api/v1/auth/login`

Đăng nhập bằng **email hoặc số điện thoại** + mật khẩu. BE tự nhận diện: nếu input chứa `@` → tra theo email, ngược lại tra theo phone.

**Quyền truy cập**: Public.

**Headers (bắt buộc):**

| Header | Value |
|---|---|
| `Content-Type` | `application/json` |

**Request Body:**

| Field | Type | Required | Validation | Mô tả |
|---|---|---|---|---|
| `usernameOrEmail` | `string` | Có | `@NotBlank`, `@Size(max=100)` | Email hoặc số điện thoại. BE tự phân loại dựa trên ký tự `@` |
| `password` | `string` | Có | `@NotBlank` | Mật khẩu (so sánh bằng BCrypt match) |

**HTTP Status:** `200 OK`

**Lỗi thường gặp:**

| HTTP | `ApiResponse.code` | ErrorCode enum | Khi nào xảy ra |
|---|---|---|---|
| 400 | 400 | (validation) | `usernameOrEmail` hoặc `password` rỗng |
| 401 | 401 | `AUTH_401_CREDENTIALS` | Sai mật khẩu. **Lưu ý**: BE cũng trả mã này khi email/phone không tồn tại (tránh lộ thông tin user enumeration) |
| 404 | 404 | `AUTH_404_USER` | Trong một số nhánh code, BE trả 404 khi user không tồn tại (xem `AuthServiceImpl.login`) |
| 403 | 403 | `AUTH_403_USER_DISABLED` | User tồn tại nhưng `status != ACTIVE` (vd `LOCKED`, `BANNED`) |
| 500 | 500 | `SYS_500` | Lỗi hệ thống |

**Response 200 (success):** Cùng schema `AuthResponse` như 6.1.

**FE cần gửi gì / nhận gì:**

- **Gửi**: Form login — để user nhập 1 trong 2: email hoặc SĐT (tùy UI). Truyền vào `usernameOrEmail`.
- **Nhận**: Lưu `data.accessToken`, `data.refreshToken` như 6.1. Đồng thời lưu `data.user.id` (cần cho các request sau), `data.roles[0]` (để phân luồng UI theo role).

**Ví dụ Postman body:**

```json
{
  "usernameOrEmail": "buyer.test@flash-sale.local",
  "password": "Test@123456"
}
```

---

### 6.3 `POST /api/v1/auth/refresh-token`

Làm mới cặp Access Token + Refresh Token mới. **Refresh Token Rotation được áp dụng** — RT cũ sẽ bị thu hồi ngay khi cấp RT mới.

**Quyền truy cập**: Public (vì RT đã là bằng chứng xác thực).

**Headers (bắt buộc):**

| Header | Value |
|---|---|
| `Content-Type` | `application/json` |

**Request Body:**

| Field | Type | Required | Validation | Mô tả |
|---|---|---|---|---|
| `refreshToken` | `string` | Có | `@NotBlank` | Refresh Token hiện tại (còn hạn và còn trong Redis whitelist) |

**HTTP Status:** `200 OK`

**Lỗi thường gặp:**

| HTTP | `ApiResponse.code` | ErrorCode enum | Khi nào xảy ra |
|---|---|---|---|
| 400 | 400 | (validation) | `refreshToken` rỗng |
| 401 | 401 | `AUTH_401_REFRESH_TOKEN` | (1) Sai signature / (2) `type != REFRESH` / (3) hết hạn (`exp` < now) / (4) Không có trong Redis whitelist (đã bị thu hồi bởi rotation trước đó, hoặc chưa từng được cấp) / (5) User `status != ACTIVE` |

**Response 200 (success):** Cùng schema `AuthResponse`. `data.refreshToken` sẽ là RT mới (khác RT cũ) — FE **BẮT BUỘC** phải ghi đè.

**FE cần gửi gì / nhận gì:**

- **Gửi**: `refreshToken` hiện tại (lưu trong localStorage / cookie).
- **Nhận**: Cặp token mới → ghi đè `fs_access_token` và `fs_refresh_token`. KHÔNG dùng lại RT cũ.
- **Khi nào gọi**: Khi `apiFetch` nhận 401 (AT hết hạn), thử refresh 1 lần. Nếu refresh cũng 401 → logout.
- **Idempotency**: An toàn khi gọi lại với RT MỚI (vì nó đã rotate). KHÔNG gọi lại với RT cũ (sẽ fail).

**Ví dụ Postman body:**

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

---

### 6.4 `POST /api/v1/auth/logout`

Đăng xuất — server-side thu hồi Refresh Token (nếu body có), client-side xóa tokens.

**Quyền truy cập**: Public (lý do: cho phép client gọi logout ngay cả khi AT đã hết hạn, miễn có RT hợp lệ).

**Headers:**

| Header | Value | Bắt buộc? |
|---|---|---|
| `Content-Type` | `application/json` | Có (nếu có body) |

**Request Body (optional):**

| Field | Type | Required | Mô tả |
|---|---|---|---|
| `refreshToken` | `string` | Không | Nếu có → BE parse `userId` + `jti`, xóa Redis key `auth:refresh:{userId}:{jti}`. Nếu body rỗng hoặc thiếu field → BE chỉ log, không lỗi |

**HTTP Status:** `200 OK` (kể cả khi body rỗng hoặc RT không hợp lệ)

**Lỗi thường gặp:** Không có — endpoint luôn trả 200. Lỗi parse RT chỉ được log warning, không throw exception.

**Response 200:**

```json
{
  "success": true,
  "code": 200,
  "message": "Đăng xuất thành công",
  "data": null,
  "timestamp": "2026-10-16T08:00:00Z"
}
```

**FE cần gửi gì / nhận gì:**

- **Gửi**: Optional body `{ refreshToken }`. Nên gửi để revoke RT phía server.
- **Nhận**: 200 → xóa `fs_access_token`, `fs_refresh_token` khỏi localStorage. Redirect về `/login`.
- **Idempotency**: An toàn khi gọi nhiều lần (luôn trả 200).

**Ví dụ Postman body (khuyến nghị):**

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

---

### 6.5 `GET /api/v1/auth/google/url`

Lấy URL Google consent screen để FE redirect user. Scope yêu cầu: `openid + email + profile`.

**Quyền truy cập**: Public.

**Headers:** Không yêu cầu.

**Query Params:** Không có.

**HTTP Status:** `200 OK`

**Lỗi thường gặp:**

| HTTP | `ApiResponse.code` | ErrorCode enum | Khi nào xảy ra |
|---|---|---|---|
| 500 | 500 | `AUTH_500_GOOGLE_CONFIG` | Server chưa cấu hình `google.oauth2.clientId` / `clientSecret` / `redirectUri` (đọc từ `application.yaml`, set qua env `GOOGLE_OAUTH2_CLIENT_ID` / `GOOGLE_OAUTH2_CLIENT_SECRET` / `GOOGLE_OAUTH2_REDIRECT_URI`) |

**Response 200 (success):**

```json
{
  "success": true,
  "code": 200,
  "message": "Lấy Google consent URL thành công",
  "data": {
    "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fauth%2Fgoogle%2Fcallback&response_type=code&scope=openid+email+profile&access_type=offline&prompt=consent",
    "state": "9b8e1f3a-4c5d-4e7a-8f2b-1d3c5e7f9a0b"
  },
  "timestamp": "2026-10-16T08:00:00Z"
}
```

**Schema `GoogleConsentUrlResponse`:**

| Field | Type | Mô tả |
|---|---|---|
| `authorizationUrl` | string | URL đầy đủ để redirect user đến Google consent screen (chứa `client_id`, `redirect_uri`, `scope=openid email profile`, `access_type=offline`, `prompt=consent`) |
| `state` | string (UUID) | Token ngẫu nhiên mà BE sinh cho mỗi request — FE có thể dùng để CSRF protection (lưu localStorage trước khi redirect, verify khi Google redirect về) |

**FE cần gửi gì / nhận gì:**

- **Gửi**: GET request, không body.
- **Nhận**: `data.authorizationUrl` → dùng `window.location.href = data.authorizationUrl` để redirect user sang Google.
- **Sau khi user consent**: Google redirect về `redirect_uri` kèm query `?code=...&state=...`. FE lấy `code` và gọi endpoint 6.6.

---

### 6.6 `POST /api/v1/auth/google`

Hoàn tất Google OAuth2 flow: FE gửi `code` (authorization code) + `redirectUri` lên BE, BE tự đổi code lấy `id_token`, verify signature, upsert user, cấp JWT.

**Quyền truy cập**: Public.

**Headers (bắt buộc):**

| Header | Value |
|---|---|
| `Content-Type` | `application/json` |

**Request Body:**

| Field | Type | Required | Validation | Mô tả |
|---|---|---|---|---|
| `code` | `string` | Có | `@NotBlank` | Authorization code từ Google redirect (one-time use, TTL ~10 phút) |
| `redirectUri` | `string` | Có | `@NotBlank` | Redirect URI mà FE đã dùng khi gọi 6.5. **Phải khớp** với Authorized redirect URI đã đăng ký ở Google Cloud Console, nếu không Google từ chối đổi code |

**HTTP Status:** `200 OK`

**Lỗi thường gặp:**

| HTTP | `ApiResponse.code` | ErrorCode enum | Khi nào xảy ra |
|---|---|---|---|
| 400 | 400 | (validation) | `code` hoặc `redirectUri` rỗng |
| 401 | 401 | `AUTH_401_GOOGLE_TOKEN` | (1) Google trả error khi đổi code (invalid/expired/redirect_uri mismatch) / (2) `id_token` signature sai / audience mismatch / expired |
| 401 | 401 | `AUTH_401_GOOGLE_EMAIL_UNVERIFIED` | Google user có email nhưng `email_verified = false` |
| 502 | 502 | `AUTH_502_GOOGLE_EXCHANGE` | (1) Network error khi gọi Google token endpoint / (2) Google trả response không hợp lệ (thiếu `id_token`) / (3) Code đã dùng rồi |
| 500 | 500 | `AUTH_500_GOOGLE_CONFIG` | Thiếu `clientId` / `clientSecret` / `redirectUri` ở server config |

**Response 200 (success):** Cùng schema `AuthResponse` như 6.1.

**Luồng xử lý phía BE (4 bước):**

1. **Đổi code lấy id_token**: gọi `POST https://oauth2.googleapis.com/token` với `code`, `client_id`, `client_secret`, `redirect_uri`, `grant_type=authorization_code`. Response chứa `id_token` (JWT do Google ký).
2. **Verify id_token**: dùng `GoogleIdTokenVerifier` (JWKS cache offline) — kiểm tra signature, audience (phải là `clientId` của mình), issuer (`https://accounts.google.com`), expiration. Trích `sub` (Google user ID), `email`, `email_verified`, `name`, `picture`.
3. **Upsert User + AuthAccount**:
   - Tìm `AuthAccount` theo `(provider=GOOGLE, providerAccountId=sub)`. Nếu có → dùng user đó.
   - Nếu không → tìm `User` theo email. Nếu có → gắn `AuthAccount` mới (link tài khoản local với Google). Nếu không có → tạo user mới (`password=null`, `status=ACTIVE`, role mặc định `BUYER`).
4. **Cấp JWT**: giống login — sinh access + refresh token, lưu RT vào Redis whitelist.

**FE cần gửi gì / nhận gì:**

- **Bước 1**: Gọi 6.5 → nhận `authorizationUrl` → `window.location.href = authorizationUrl`.
- **Bước 2**: Google redirect về `redirect_uri?code=...&state=...`. FE parse `code` từ URL.
- **Bước 3**: Gọi 6.6 với body `{ code, redirectUri }`. Nhận về `AuthResponse` → lưu token như 6.1.

**Ví dụ Postman body:**

```json
{
  "code": "4/0AeaYSHB-rGc...long-google-code...",
  "redirectUri": "http://localhost:3000/auth/google/callback"
}
```

---

---
## 7. User Profile Module `/api/v1/users`

> Yêu cầu: **Authentication (Bearer JWT)** · Mọi endpoint dưới đây đều yêu cầu `Authorization: Bearer <access_token>`.  
> **Tag Swagger**: `User Profile`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/users/me` | Authenticated (mọi role) | Lấy hồ sơ cá nhân (gồm `avatarUrl` + danh sách `roles`) |
| `PUT` | `/api/v1/users/me` | Authenticated (mọi role) | Cập nhật họ tên, số điện thoại |
| `PUT` | `/api/v1/users/me/change-password` | Authenticated (mọi role) | Đổi mật khẩu (đồng thời thu hồi toàn bộ refresh token trong Redis) |
| `POST` | `/api/v1/users/me/avatar` | Authenticated (mọi role) | Upload avatar (multipart, soft-delete avatar cũ qua partial unique index) |

### 7.1 `GET /api/v1/users/me` — Xem hồ sơ cá nhân

| Mục | Giá trị |
|---|---|
| Method | `GET` |
| Path | `/api/v1/users/me` |
| Auth | **BẮT BUỘC** `Authorization: Bearer <access_token>` |
| Role | Bất kỳ (BUYER, SELLER, ADMIN) |
| Request body | Không |
| Request query | Không |

**Response `200 OK`** (`UserResponse`):

```json
{
  "success": true,
  "code": 200,
  "message": "Operation completed successfully",
  "data": {
    "id": 1,
    "email": "buyer@example.com",
    "fullName": "Nguyễn Văn A",
    "phone": "0987654321",
    "avatarUrl": "https://res.cloudinary.com/.../avatar.jpg",
    "status": "ACTIVE",
    "createdAt": "2026-10-01T10:00:00Z",
    "roles": ["ROLE_BUYER"]
  }
}
```

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 401 | `COMMON_401` (`UNAUTHORIZED`) | Thiếu/không hợp lệ token |
| 404 | `USER_404_NOT_FOUND` | User không tồn tại (token hợp lệ nhưng user bị xóa) |

### 7.2 `PUT /api/v1/users/me` — Cập nhật hồ sơ

**Request Body** (`UpdateProfileRequest` — `application/json`):

| Field | Type | Required | Validation | Ghi chú |
|---|---|---|---|---|
| `fullName` | `string` | **Có** | `@NotBlank`, `@Size(max=100)`, trim | Họ và tên hiển thị |
| `phone` | `string` | Không | `@Size(max=15)`, trim; nếu trống → giữ nguyên | Nếu đổi sang số đã tồn tại của user khác → lỗi `AUTH_409_PHONE` |

> `email`, `password`, `status`, `roles` KHÔNG cho phép cập nhật qua endpoint này.

**Response `200 OK`** trả về `UserResponse` (giống mục 7.1).

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu `fullName` hoặc vượt quá max length |
| 401 | `COMMON_401` (`UNAUTHORIZED`) | Thiếu/không hợp lệ token |
| 404 | `USER_404_NOT_FOUND` | User không tồn tại |
| 409 | `AUTH_409_PHONE` (`PHONE_ALREADY_EXISTS`) | Số điện thoại đã thuộc về user khác |

### 7.3 `PUT /api/v1/users/me/change-password` — Đổi mật khẩu

**Request Body** (`ChangePasswordRequest` — `application/json`):

| Field | Type | Required | Validation |
|---|---|---|---|
| `oldPassword` | `string` | **Có** | `@NotBlank` |
| `newPassword` | `string` | **Có** | `@NotBlank`, `@Size(min=6)` |

**Response `200 OK`** với `data = null`, message `"Đổi mật khẩu thành công"`.  
**Side effect**: BE tự động **xóa toàn bộ refresh token** của user trong Redis (`auth:user_tokens:{userId}` + `auth:refresh:{userId}:*`) → mọi phiên đăng nhập khác phải đăng nhập lại.

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `oldPassword` hoặc `newPassword` rỗng / dưới 6 ký tự |
| 400 | `USER_400_OLD_PASSWORD` | Mật khẩu hiện tại không chính xác |
| 401 | `COMMON_401` (`UNAUTHORIZED`) | Thiếu token |
| 404 | `USER_404_NOT_FOUND` | User không tồn tại |

### 7.4 `POST /api/v1/users/me/avatar` — Upload avatar

**Request** (`multipart/form-data`):

| Field | Type | Required | Validation |
|---|---|---|---|
| `file` | `binary` | **Có** | `@RequestPart("file") MultipartFile`; chỉ nhận jpg/jpeg/png/webp; max 10MB |

**HTTP Status**: `201 Created`

**Response** (`ImageResponse` — tham chiếu `image/dto/ImageResponse.java`):

```json
{
  "id": 42,
  "ownerType": "USER",
  "ownerId": 5,
  "url": "https://res.cloudinary.com/.../avatar.jpg",
  "publicId": "abc123",
  "isPrimary": true,
  "displayOrder": 0,
  "status": "ACTIVE",
  "createdAt": "2026-10-12T12:00:00Z"
}
```

> **Side effect**: avatar cũ (nếu có) bị soft-delete tự động nhờ partial unique index `(owner_type, owner_id) WHERE is_primary = true` ở DB (xem `image/polymorphic` rule).

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 400 | `IMAGE_400_INVALID_INPUT` | Thiếu `file` hoặc `file` rỗng |
| 400 | `IMAGE_400_FILE_TOO_LARGE` | File > 10MB |
| 400 | `IMAGE_400_UNSUPPORTED_MEDIA` | Không phải jpg/jpeg/png/webp |
| 401 | `COMMON_401` (`UNAUTHORIZED`) | Thiếu token |
| 409 | `IMAGE_409_UPLOAD_FAILED` | Cloudinary upload thất bại |
| 500 | `IMAGE_500_INTERNAL` | Lỗi nội bộ xử lý ảnh |

### 7.5 Postman Test Cases — `03_User_Profile`

| # | Test | Endpoint | Setup | Expect |
|---|---|---|---|---|
| 1 | Lấy hồ sơ thành công | `GET /me` | Token BUYER/SELLER/ADMIN hợp lệ | `200` + `data.id`, `data.email`, `data.fullName`, `data.roles` (mảng string) |
| 2 | Thiếu token | `GET /me` | Không gửi Authorization | `401` + `code = COMMON_401` |
| 3 | Token không hợp lệ | `GET /me` | `Bearer <token-sai>` | `401` + `code = AUTH_401_INVALID_TOKEN` |
| 4 | Cập nhật fullName hợp lệ | `PUT /me` | Body `{fullName, phone}` hợp lệ | `200` + `data.fullName` phản ánh giá trị mới |
| 5 | Thiếu fullName | `PUT /me` | Body `{}` hoặc `{phone}` | `400` + `code = VALIDATION_ERROR` |
| 6 | Phone trùng user khác | `PUT /me` | Phone thuộc về 1 user khác trong DB | `409` + `code = AUTH_409_PHONE` |
| 7 | Đổi mật khẩu thành công | `PUT /me/change-password` | Đúng `oldPassword` + `newPassword >= 6 ký tự` | `200` + `data = null` |
| 8 | Sai mật khẩu cũ | `PUT /me/change-password` | `oldPassword` sai | `400` + `code = USER_400_OLD_PASSWORD` |
| 9 | New password quá ngắn | `PUT /me/change-password` | `newPassword = "abc"` (< 6) | `400` + `code = VALIDATION_ERROR` |
| 10 | Upload avatar hợp lệ | `POST /me/avatar` | Form-data với `file` JPG/PNG | `201` + `data.url` |
| 11 | Upload file > 10MB | `POST /me/avatar` | File 11MB | `400` + `code = IMAGE_400_FILE_TOO_LARGE` |
| 12 | Upload sai định dạng | `POST /me/avatar` | File `.gif` | `400` + `code = IMAGE_400_UNSUPPORTED_MEDIA` |
| 13 | Upload không kèm file | `POST /me/avatar` | Form-data rỗng | `400` + `code = IMAGE_400_INVALID_INPUT` |

---


## 8. Address Book Module `/api/v1/users/addresses`

> Yêu cầu: **Authentication (Bearer JWT)** · Chỉ chủ sở hữu mới thấy / sửa / xóa được địa chỉ của mình.  
> **Tag Swagger**: `Address Book`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/users/addresses` | Authenticated | Danh sách địa chỉ của user (mặc định xếp đầu) |
| `POST` | `/api/v1/users/addresses` | Authenticated | Tạo địa chỉ mới (địa chỉ đầu tiên → tự động làm mặc định) |
| `GET` | `/api/v1/users/addresses/{id}` | Authenticated | Xem chi tiết 1 địa chỉ |
| `PUT` | `/api/v1/users/addresses/{id}` | Authenticated | Cập nhật địa chỉ |
| `DELETE` | `/api/v1/users/addresses/{id}` | Authenticated | Xóa địa chỉ (nếu xóa địa chỉ mặc định → tự động set 1 địa chỉ còn lại làm mặc định) |
| `PATCH` | `/api/v1/users/addresses/{id}/default` | Authenticated | Đặt địa chỉ này làm mặc định (1 user chỉ có 1 địa chỉ mặc định) |

> **Ràng buộc XOR (DB-level CHECK)**: `user_id XOR store_id` (1 trong 2 phải NOT NULL, 1 cái còn lại NULL). Với user address → `store_id = null`.

### 8.1 `GET /api/v1/users/addresses` — Danh sách địa chỉ

| Mục | Giá trị |
|---|---|
| Method | `GET` |
| Path | `/api/v1/users/addresses` |
| Auth | **BẮT BUỘC** `Authorization: Bearer <access_token>` |
| Role | Bất kỳ (BUYER, SELLER, ADMIN) |
| Request body / query | Không |

**Response `200 OK`** — `List<AddressResponse>`:

```json
{
  "success": true,
  "code": 200,
  "message": "OK",
  "data": [
    {
      "id": 12,
      "contactName": "Nguyễn Văn A",
      "phone": "0987654321",
      "province": "Hồ Chí Minh",
      "district": "Quận 1",
      "ward": "Phường Bến Nghé",
      "detailAddress": "12 Đường Lê Lợi",
      "latitude": 10.77200000,
      "longitude": 106.70000000,
      "isDefault": true,
      "createdAt": "2026-10-01T10:00:00Z",
      "updatedAt": "2026-10-12T12:00:00Z"
    }
  ]
}
```

> Sort: `isDefault DESC, id DESC` (mặc định trước, mới nhất sau).

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 401 | `COMMON_401` | Thiếu/không hợp lệ token |

### 8.2 `POST /api/v1/users/addresses` — Tạo địa chỉ mới

**Request Body** (`CreateAddressRequest` — `application/json`):

| Field | Type | Required | Validation | Ghi chú |
|---|---|---|---|---|
| `contactName` | `string` | **Có** | `@NotBlank`, `@Size(max=100)`, trim | Tên người nhận |
| `phone` | `string` | **Có** | `@NotBlank`, `@Size(max=15)`, trim | SĐT người nhận |
| `province` | `string` | **Có** | `@NotBlank`, `@Size(max=100)`, trim | Tỉnh/TP |
| `district` | `string` | **Có** | `@NotBlank`, `@Size(max=100)`, trim | Quận/Huyện |
| `ward` | `string` | **Có** | `@NotBlank`, `@Size(max=100)`, trim | Phường/Xã |
| `detailAddress` | `string` | **Có** | `@NotBlank`, `@Size(max=255)`, trim | Số nhà, đường |
| `latitude` | `BigDecimal` | Không | decimal(10,8) | GPS lat (optional) |
| `longitude` | `BigDecimal` | Không | decimal(11,8) | GPS lng (optional) |
| `isDefault` | `boolean` | Không | default `false` | Nếu `true` và đã có địa chỉ mặc định → BE sẽ unset cái cũ, set cái này |

**HTTP Status**: `201 Created`

**Response** trả về `AddressResponse` (giống mục 8.1).

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu field bắt buộc hoặc vượt max length |
| 401 | `COMMON_401` | Thiếu token |

### 8.3 `GET /api/v1/users/addresses/{id}` — Xem chi tiết 1 địa chỉ

| Mục | Giá trị |
|---|---|
| Method | `GET` |
| Path | `/api/v1/users/addresses/{id}` |
| Auth | **BẮT BUỘC** `Authorization: Bearer <access_token>` |
| Path param | `id` (Long, required) |

**Response `200 OK`** — `AddressResponse`.

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 401 | `COMMON_401` | Thiếu token |
| 403 | `USER_403_ADDRESS_DENIED` (`ADDRESS_ACCESS_DENIED`) | Địa chỉ này thuộc user khác |
| 404 | `USER_404_ADDRESS` (`ADDRESS_NOT_FOUND`) | Không tìm thấy address với `id` này |

### 8.4 `PUT /api/v1/users/addresses/{id}` — Cập nhật địa chỉ

**Request Body** (`UpdateAddressRequest` — `application/json`):  
Tất cả field đều **optional** (partial update). Field nào gửi sẽ được cập nhật.

| Field | Type | Required | Validation |
|---|---|---|---|
| `contactName` | `string` | Không | `@Size(max=100)`, nếu blank thì bỏ qua |
| `phone` | `string` | Không | `@Size(max=15)`, nếu blank thì bỏ qua |
| `province` | `string` | Không | `@Size(max=100)`, nếu blank thì bỏ qua |
| `district` | `string` | Không | `@Size(max=100)`, nếu blank thì bỏ qua |
| `ward` | `string` | Không | `@Size(max=100)`, nếu blank thì bỏ qua |
| `detailAddress` | `string` | Không | `@Size(max=255)`, nếu blank thì bỏ qua |
| `latitude` | `BigDecimal` | Không | decimal(10,8) |
| `longitude` | `BigDecimal` | Không | decimal(11,8) |
| `isDefault` | `boolean` | Không | Nếu `true` và địa chỉ này chưa phải mặc định → BE tự unset cái cũ |

**Response `200 OK`** — `AddressResponse` (giá trị mới nhất).

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Field nào đó vượt max length |
| 401 | `COMMON_401` | Thiếu token |
| 403 | `USER_403_ADDRESS_DENIED` | Không phải chủ sở hữu |
| 404 | `USER_404_ADDRESS` | Không tìm thấy |

### 8.5 `DELETE /api/v1/users/addresses/{id}` — Xóa địa chỉ

| Mục | Giá trị |
|---|---|
| Method | `DELETE` |
| Path | `/api/v1/users/addresses/{id}` |
| Auth | **BẮT BUỘC** `Authorization: Bearer <access_token>` |
| Path param | `id` (Long, required) |

**Response `200 OK`** với `data = null`, message `"Xóa địa chỉ thành công"`.

> **Side effect**: Nếu xóa địa chỉ mặc định và còn ≥ 1 địa chỉ khác → BE tự động set địa chỉ còn lại **đầu tiên** (theo `id ASC`) làm mặc định. Nếu đó là địa chỉ cuối cùng → sau xóa user không còn địa chỉ nào, địa chỉ mới tạo tiếp theo sẽ tự động làm mặc định.

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 401 | `COMMON_401` | Thiếu token |
| 403 | `USER_403_ADDRESS_DENIED` | Không phải chủ sở hữu |
| 404 | `USER_404_ADDRESS` | Không tìm thấy |

### 8.6 `PATCH /api/v1/users/addresses/{id}/default` — Đặt làm mặc định

| Mục | Giá trị |
|---|---|
| Method | `PATCH` |
| Path | `/api/v1/users/addresses/{id}/default` |
| Auth | **BẮT BUỘC** `Authorization: Bearer <access_token>` |
| Path param | `id` (Long, required) |

**Response `200 OK`** — `AddressResponse` (với `isDefault = true`).

> **Side effect**: BE tự động unset địa chỉ mặc định cũ (set `isDefault=false` cho địa chỉ trước đó), rồi set địa chỉ này thành `isDefault=true`.

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 401 | `COMMON_401` | Thiếu token |
| 403 | `USER_403_ADDRESS_DENIED` | Không phải chủ sở hữu |
| 404 | `USER_404_ADDRESS` | Không tìm thấy |

### 8.7 Postman Test Cases — `04_Address_Book`

| # | Test | Endpoint | Setup | Expect |
|---|---|---|---|---|
| 1 | List địa chỉ của tôi | `GET /addresses` | Token BUYER | `200` + `data[]` (mảng), item đầu có `isDefault=true` |
| 2 | List rỗng | `GET /addresses` | User mới chưa có address | `200` + `data = []` |
| 3 | Tạo địa chỉ hợp lệ (đầu tiên) | `POST /addresses` | Body đầy đủ 6 field + 2 GPS | `201` + `isDefault=true` (vì là địa chỉ đầu tiên) |
| 4 | Tạo địa chỉ thứ 2 với `isDefault=true` | `POST /addresses` | Body có `isDefault=true` | `201` + địa chỉ cũ sẽ bị unset, địa chỉ mới `isDefault=true` |
| 5 | Tạo địa chỉ thiếu `contactName` | `POST /addresses` | Body thiếu `contactName` | `400` + `code = VALIDATION_ERROR` |
| 6 | Tạo địa chỉ phone > 15 ký tự | `POST /addresses` | `phone = "0".repeat(20)` | `400` + `code = VALIDATION_ERROR` |
| 7 | Xem chi tiết 1 địa chỉ của mình | `GET /addresses/{id}` | id thuộc về user | `200` + `data.id = id` |
| 8 | Xem địa chỉ của user khác | `GET /addresses/{id}` | id không thuộc về user | `403` + `code = USER_403_ADDRESS_DENIED` |
| 9 | Xem địa chỉ không tồn tại | `GET /addresses/999999` | id không có trong DB | `404` + `code = USER_404_ADDRESS` |
| 10 | Update 1 phần (chỉ `phone`) | `PUT /addresses/{id}` | Body `{phone: "0999999999"}` | `200` + `data.phone` mới, các field khác giữ nguyên |
| 11 | Update với field rỗng | `PUT /addresses/{id}` | `{contactName: ""}` | `200` + `contactName` giữ nguyên (bỏ qua, không lỗi) |
| 12 | Set default | `PATCH /addresses/{id}/default` | id là địa chỉ phụ | `200` + `isDefault=true` |
| 13 | Set default cho id không thuộc về mình | `PATCH /addresses/{id}/default` | id của user khác | `403` |
| 14 | Xóa địa chỉ thường | `DELETE /addresses/{id}` | id là địa chỉ phụ | `200` + message thành công |
| 15 | Xóa địa chỉ mặc định (còn địa chỉ khác) | `DELETE /addresses/{id}` | id là default, còn ≥ 1 địa chỉ khác | `200` + BE tự động set 1 địa chỉ còn lại làm default |
| 16 | Xóa địa chỉ cuối cùng | `DELETE /addresses/{id}` | chỉ còn 1 địa chỉ | `200` + `data` rỗng; lần tạo sau sẽ tự default |

---


## 9. Store Module `/api/v1/stores`

> **Tag Swagger**: `Stores`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/stores` | Authenticated | Đăng ký mở gian hàng mới |
| `GET` | `/api/v1/stores/me` | Authenticated (Seller) | Lấy store của tôi |
| `PUT` | `/api/v1/stores/me` | Authenticated (Seller) | Cập nhật store của tôi |
| `GET` | `/api/v1/stores/sidebar/summary` | Seller | Tổng hợp metrics sidebar dashboard |
| `GET` | `/api/v1/stores/featured` | **Public** | DS gian hàng nổi bật cho trang chủ (Vibe Mall) |
| `GET` | `/api/v1/stores/{id}` | **Public** | Xem thông tin công khai |
| `GET` | `/api/v1/stores/me/addresses` | Seller | DS địa chỉ kho |
| `POST` | `/api/v1/stores/me/addresses` | Seller | Thêm địa chỉ kho |
| `PUT` | `/api/v1/stores/me/addresses/{addressId}` | Seller | Cập nhật địa chỉ kho |
| `DELETE` | `/api/v1/stores/me/addresses/{addressId}` | Seller | Xóa địa chỉ kho |
| `PATCH` | `/api/v1/stores/me/addresses/{addressId}/default` | Seller | Đặt địa chỉ kho mặc định |
| `POST` | `/api/v1/stores/me/logo` | Seller | Upload logo (multipart, soft-delete logo cũ) |

> Lưu ý: Tất cả `/api/v1/seller/**` yêu cầu role `SELLER`. Store endpoints nằm tại `/api/v1/stores/me` dùng chung pattern này.

### 9.1 `POST /api/v1/stores`

**Request Body:**

| Field | Type | Required | Validation |
|---|---|---|---|
| `storeName` | `string` | Có | 2-150 ký tự |
| `description` | `string` | Không | – |

> **Lưu ý**: `CreateStoreRequest` không bao gồm `logoUrl`. Logo được upload riêng qua endpoint `POST /api/v1/stores/me/logo` (mục 9.4c) sau khi tạo xong store.

**HTTP Status:** 201 Created

**Hành vi:** Store mặc định trạng thái `PENDING`, tự động tạo Wallet 1-1.

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 409 | `STORE_409_EXISTS` | Bạn đã có gian hàng trên hệ thống, không thể đăng ký thêm |
| 409 | `STORE_409_NAME_EXISTS` | Tên gian hàng này đã được sử dụng |

**Response (`StoreResponse`):**

```json
{
  "id": 1,
  "userId": 5,
  "storeName": "Shop ABC",
  "logoUrl": null,
  "description": null,
  "defaultCommissionRate": 0.1000,
  "status": "PENDING",
  "createdAt": "2026-10-03T..."
}
```

### 9.4 `GET /api/v1/stores/{id}` — **Public**

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 404 | `STORE_404_NOT_FOUND` | Không tìm thấy thông tin gian hàng |

### 9.4a `GET /api/v1/stores/featured` — **Public**

Lấy danh sách gian hàng nổi bật (APPROVED) cho section "Vibe Mall" trên trang chủ.

**Query params:**

| Field | Type | Required | Mặc định | Mô tả |
|---|---|---|---|---|
| `limit` | `integer` | Không | `4` | Số lượng store trả về (cap theo business) |

**Response item (`FeaturedStoreResponse`):**

```json
{
  "id": 1,
  "name": "Shop ABC",
  "logoUrl": "https://res.cloudinary.com/.../logo.jpg",
  "verified": true,
  "rating": 4.7,
  "totalRatings": 320,
  "followers": 1500,
  "productThumbnails": [
    "https://.../p1.jpg",
    "https://.../p2.jpg"
  ],
  "totalProducts": 120,
  "createdAt": "2026-10-01T..."
}
```

### 9.4b `GET /api/v1/stores/sidebar/summary` — **Seller**

Trả về tổng hợp metrics cho sidebar dashboard của người bán. Chỉ yêu cầu quyền `SELLER`.

**Access:** `@PreAuthorize("hasRole('SELLER')")`

**Response (`SidebarSummaryResponse`):**

```json
{
  "success": true,
  "data": {
    "totalProducts": 42,
    "activeFlashSales": 2,
    "pendingOrders": 7,
    "unpaidOrders": 7,
    "todayOrders": 12,
    "todayRevenue": 5290000,
    "monthRevenue": 98000000,
    "ratingAvg": 4.6,
    "ratingCount": 183,
    "lowStockProducts": 3,
    "storeStatus": "APPROVED",
    "tier": "PREFERRED"
  }
}
```

**Field description:**

| Field | Type | Mô tả |
|---|---|---|
| `totalProducts` | `long` | Số sản phẩm ACTIVE của store |
| `activeFlashSales` | `long` | Số slot Flash Sale đang ACTIVE cho sản phẩm của store |
| `pendingOrders` | `long` | Số đơn hàng trạng thái `PENDING_PAYMENT` |
| `unpaidOrders` | `long` | Giống `pendingOrders` (aliased) |
| `todayOrders` | `long` | Số đơn hàng tạo trong ngày hiện tại |
| `todayRevenue` | `BigDecimal` | Tổng doanh thu hôm nay (đơn CONFIRMED + COMPLETED) |
| `monthRevenue` | `BigDecimal` | Tổng doanh thu tháng hiện tại |
| `ratingAvg` | `double` | TB điểm đánh giá sản phẩm (0.0 nếu chưa có review) |
| `ratingCount` | `long` | Tổng review VISIBLE của toàn bộ sản phẩm |
| `lowStockProducts` | `long` | Số variant tồn kho < 10 |
| `storeStatus` | `string` | Trạng thái store (`PENDING`, `APPROVED`, ...) |
| `tier` | `string` | Hạng store (`NORMAL`, `OFFICIAL_MALL`, `PREFERRED`) |

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 404 | `STORE_404_NOT_FOUND` | Gian hàng không tồn tại |

### 9.4c `POST /api/v1/stores/me/logo` — Seller

Upload logo mới cho gian hàng. Tự động soft-delete logo cũ (partial unique index đảm bảo 1 store chỉ có 1 logo ACTIVE).

**Request Body (`multipart/form-data`):**

| Field | Type | Required | Mô tả |
|---|---|---|---|
| `file` | `binary` | Có | File ảnh (jpg/png/webp), max 10MB |

**HTTP Status:** 201 Created

**Response:** `ImageResponse` (xem mục 28.4).

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 404 | `STORE_404_NOT_FOUND` | Người dùng hiện tại chưa có gian hàng |
| 400 | `IMAGE_400` | File không hợp lệ (>10MB hoặc sai định dạng) |
| 502 | `IMAGE_502` | Cloudinary upload thất bại |

### 9.5–9.9 Store Address endpoints

Địa chỉ kho của Store dùng chung `CreateAddressRequest` / `UpdateAddressRequest` / `AddressResponse` như User Address. Lưu ý:

- `user=null`, `store=<storeId>` (constraint XOR).
- Cùng pattern setDefault – chỉ một địa chỉ mặc định.

---

## 10. Admin Store Module `/api/v1/admin/stores`

> Yêu cầu: **Role ADMIN**

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `PATCH` | `/api/v1/admin/stores/{id}/status` | Admin | Duyệt / Khóa store |

### 10.1 `PATCH /api/v1/admin/stores/{id}/status`

**Request Body:**

| Field | Type | Required | Validation |
|---|---|---|---|
| `status` | `string` | Có | `APPROVED` hoặc `BANNED` hoặc `SUSPENDED` hoặc `PENDING` |

**Hành vi quan trọng:** Khi `APPROVED` → tự động gán role `SELLER` cho chủ shop.

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 404 | `STORE_404_NOT_FOUND` | Không tìm thấy thông tin gian hàng |
| 400 | `STORE_400_INVALID_STATUS` | Trạng thái phê duyệt gian hàng không hợp lệ |

---

## 11. Category Module `/api/v1/categories`

> **Public API** · **Tag Swagger**: `Categories`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/categories` | Public | Lấy tất cả ngành hàng |
| `GET` | `/api/v1/categories/{id}` | Public | Chi tiết theo ID |
| `GET` | `/api/v1/categories/slug/{slug}` | Public | Chi tiết theo slug |

### 11.1 `GET /api/v1/categories`

**Response item (`CategoryResponse`):**

```json
{
  "id": 1,
  "name": "Thời trang nam",
  "slug": "thoi-trang-nam",
  "commissionRate": 0.10,
  "description": "Quần áo, phụ kiện nam"
}
```

### 11.2 `GET /api/v1/categories/{id}` và `/slug/{slug}`

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 404 | `CAT_404_NOT_FOUND` | Không tìm thấy ngành hàng yêu cầu |

---

## 12. Admin Category Module `/api/v1/admin/categories`

> Yêu cầu: **Role ADMIN**

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/admin/categories` | Admin | Tạo ngành hàng |
| `PUT` | `/api/v1/admin/categories/{id}` | Admin | Cập nhật |
| `DELETE` | `/api/v1/admin/categories/{id}` | Admin | Xóa |

### 12.1 `POST /api/v1/admin/categories`

**Request Body:**

| Field | Type | Required | Validation |
|---|---|---|---|
| `name` | `string` | Có | max 100 |
| `slug` | `string` | Có | max 100 |
| `commissionRate` | `decimal` | Có | 0.0000 → 1.0000 (tỷ lệ hoa hồng) |
| `description` | `string` | Không | max 255 |

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 409 | `CAT_409_NAME_EXISTS` | Tên ngành hàng đã tồn tại |
| 409 | `CAT_409_SLUG_EXISTS` | Đường dẫn slug của ngành hàng đã tồn tại |

### 12.3 `DELETE /api/v1/admin/categories/{id}`

Chặn nếu ngành hàng đang có ngành hàng con.

---
## 13. Product Public Module `/api/v1/products`

> **Public API** · **Tag Swagger**: `Product - Public API`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/products/recommended` | Public | SP đề xuất (For You / Best Seller / Hot Deal) cho trang chủ |
| `GET` | `/api/v1/products` | Public | Tìm kiếm, lọc, phân trang |
| `GET` | `/api/v1/products/search` | Public | Tìm kiếm sản phẩm toàn sàn kèm bộ lọc facets và phân trang kép |
| `GET` | `/api/v1/products/{id}` | Public | Chi tiết sản phẩm + SKU |

### 13.1 `GET /api/v1/products/recommended`

Lấy danh sách sản phẩm đề xuất theo 3 tab cho trang chủ (For You / Best Seller / Hot Deal). Hỗ trợ lọc theo categoryId.

**Query params:**

| Field | Type | Required | Mặc định | Mô tả |
|---|---|---|---|---|
| `tab` | `string` | Không | `all` | `all` / `for_you` / `best_seller` / `hot_deal`. `all` trả cả 3 danh sách, các giá trị còn lại trả 1 danh sách tương ứng |
| `categoryId` | `long` | Không | – | Lọc theo categoryId (optional) |
| `limit` | `integer` | Không | `10` | Số lượng sản phẩm tối đa mỗi tab |

**Response (`RecommendedProductsResponse`):**

```json
{
  "forYou": [
    {
      "id": 1,
      "storeId": 2,
      "storeName": "Shop ABC",
      "name": "Áo thun nam",
      "imageUrl": "https://...",
      "minPrice": 99000,
      "maxPrice": 199000,
      "totalStock": 50,
      "totalSold": 320,
      "label": "For You"
    }
  ],
  "bestSellers": [
    { "...": "..." }
  ],
  "hotDeals": [
    { "...": "..." }
  ]
}
```

**Lọc ngầm:** Chỉ trả sản phẩm `ACTIVE` thuộc store `APPROVED`.

### 13.2 `GET /api/v1/products`

**Query params (`ProductFilterRequest`):**

| Field | Type | Mô tả |
|---|---|---|
| `categoryId` | `integer` | Lọc theo ngành hàng |
| `keyword` | `string` | Tìm theo tên |
| `minPrice` | `decimal` | Giá tối thiểu |
| `maxPrice` | `decimal` | Giá tối đa |
| `rating` | `integer` | Lọc sản phẩm có ratingAvg >= rating (1-5) |
| `flashSaleOnly` | `boolean` | Lọc sản phẩm có flash sale đang ACTIVE |
| `hasDiscount` | `boolean` | Lọc sản phẩm đang giảm giá (minPrice < maxPrice gốc) |
| `minSold` | `integer` | Lọc sản phẩm có soldCount >= minSold |
| `sort` | `string` | Sắp xếp: `createdAt_desc` (default), `createdAt_asc`, `price_asc`, `price_desc`, `sold_desc`, `rating_desc` |
| `page`, `size` | – | Phân trang chuẩn Spring (`page=0&size=20`) |

**Lọc ngầm:** Chỉ trả về sản phẩm `ACTIVE` thuộc store `APPROVED`. Batch fetch variants tránh N+1.

**Response item (`ProductSummaryResponse`):**

```json
{
  "id": 1,
  "storeId": 2,
  "storeName": "Shop ABC",
  "categoryId": 1,
  "categoryName": "Thời trang nam",
  "name": "Áo thun nam",
  "imageUrl": "https://...",
  "minPrice": 99000,
  "maxPrice": 199000,
  "totalStock": 50,
  "status": "ACTIVE",
  "createdAt": "2026-10-01T..."
}
```

### 13.3 `GET /api/v1/products/{id}`

**Response (`ProductDetailResponse`):**

| Field | Type | Mô tả |
|---|---|---|
| `id` | `Long` | |
| `storeId` | `Long` | |
| `storeName` | `String` | |
| `categoryId` | `Integer` | |
| `categoryName` | `String` | |
| `name` | `String` | |
| `imageUrl` | `String` | Ảnh đại diện (từ bảng `images`, sort theo `displayOrder` lấy ảnh đầu) |
| `description` | `String` | |
| `tierVariationConfigs` | `Array` | Cấu hình biến thể cấp SPU |
| `status` | `String` | |
| `createdAt` | `ISO DateTime` | |
| `ratingAvg` | `Double` | Trung bình rating từ reviews VISIBLE (mặc định `0.0` nếu không có review) |
| `ratingCount` | `Integer` | Tổng số review VISIBLE |
| `soldCount` | `Integer` | Tổng số đã bán (từ `products.total_sold` V7) |
| `minPrice` | `Decimal` | Giá thấp nhất trong các variant ACTIVE |
| `maxPrice` | `Decimal` | Giá cao nhất trong các variant ACTIVE |
| `discountPercent` | `Integer` | `%` giảm giá Flash Sale. `null` nếu sản phẩm không có FlashSaleItem ACTIVE. Tính: `round((original - flashSale) / original * 100)` |
| `flashSales` | `Array<ActiveFlashSaleDto>` | Danh sách Flash Sale ACTIVE của sản phẩm này. Mỗi item gồm: `slotId`, `slotStartTime`, `slotEndTime`, `itemId`, `variantId`, `originalPrice`, `flashSalePrice`, `stockAvailable` (từ Redis, fallback DB), `slotStatus`. Sorted by `flashSalePrice ASC`. Rỗng nếu không có. |
| `images` | `Array<ProductImageDto>` | Danh sách ảnh sản phẩm (từ bảng `images`, sort theo `displayOrder`) |
| `slug` | `String` | URL SEO thân thiện (/products/ao-thun-nam-cotton) |
| `descriptionRich` | `Object` | Dữ liệu mô tả bài viết rich text / HTML mở rộng (V10) |
| `specs` | `Object` | Bảng thông số kỹ thuật (category, material, origin, style, sku, careInstructions) (V10) |
| `features` | `Array` | 3 khối tính năng / lợi ích nổi bật trên storefront (icon, class, tiêu đề, mô tả) (V10) |
| `commitments` | `Array` | Danh sách cam kết chính sách gian hàng (chính hãng, đổi trả, bảo hành) (V10) |
| `sizeChart` | `Array` | Bảng kích cỡ chọn size cho thời trang (V10) |
| `storeProfile` | `Object` | Hồ sơ tóm tắt gian hàng (id, name, isOfficial) |
| `shipping` | `Object` | Thông tin vận chuyển (freeshipBadge, freeshipText, estimatedDays) |
| `ratingDistribution` | `RatingDistributionDto` | Phân bố đánh giá từ 1 đến 5 sao |
| `variants` | `Array` | Danh sách SKU |

```json
{
  "id": 1,
  "storeId": 2,
  "storeName": "Shop ABC",
  "categoryId": 1,
  "categoryName": "Thời trang nam",
  "name": "Áo thun nam Cotton",
  "slug": "ao-thun-nam-cotton",
  "imageUrl": "https://...",
  "description": "Mô tả cơ bản của sản phẩm...",
  "descriptionRich": { "html": "<p>Chi tiết bài viết...</p>" },
  "specs": {
    "material": "100% Cotton",
    "origin": "Việt Nam",
    "style": "Basic",
    "careInstructions": "Giặt máy chế độ nhẹ"
  },
  "features": [
    { "icon": "eco", "title": "Chất liệu Organic", "description": "100% sợi bông tự nhiên thoáng mát" }
  ],
  "commitments": [
    { "icon": "verified", "label": "Cam kết chính hãng 100%" }
  ],
  "sizeChart": [
    { "size": "M", "height": "1m60-1m68", "weight": "50-60kg" },
    { "size": "L", "height": "1m68-1m75", "weight": "60-70kg" }
  ],
  "storeProfile": {
    "id": 2,
    "name": "Shop ABC",
    "isOfficial": true
  },
  "shipping": {
    "freeshipBadge": "FREESHIP XTRA",
    "freeshipText": "Miễn phí vận chuyển",
    "estimatedDays": "1 - 2 ngày"
  },
  "tierVariationConfigs": [
    { "name": "Màu sắc", "options": ["Đen", "Trắng"] },
    { "name": "Size", "options": ["M", "L", "XL"] }
  ],
  "status": "ACTIVE",
  "ratingAvg": 4.5,
  "ratingCount": 128,
  "soldCount": 320,
  "minPrice": 99000,
  "maxPrice": 199000,
  "discountPercent": 30,
  "flashSales": [
    {
      "slotId": 1,
      "slotStartTime": "2026-10-15T00:00:00.000Z",
      "slotEndTime": "2026-10-15T23:59:59.000Z",
      "itemId": 10,
      "variantId": 10,
      "originalPrice": 99000,
      "flashSalePrice": 69000,
      "stockAvailable": 50,
      "slotStatus": "ACTIVE"
    }
  ],
  "images": [
    {
      "id": 10,
      "url": "https://res.cloudinary.com/.../image1.jpg",
      "isPrimary": true,
      "displayOrder": 0,
      "alt": "Áo thun nam"
    }
  ],
  "variants": [
    {
      "id": 10,
      "productId": 1,
      "sku": "AT-DEN-M",
      "variantName": "Đen, Size M",
      "attributes": { "Màu sắc": "Đen", "Size": "M" },
      "originalPrice": 99000,
      "stockQuantity": 25,
      "imageUrl": null,
      "status": "ACTIVE",
      "version": 0,
      "createdAt": "2026-10-01T..."
    }
  ],
  "createdAt": "2026-10-01T..."
}
```

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 404 | `PROD_404_NOT_FOUND` | Không tìm thấy sản phẩm |

---

### 13.4 `GET /api/v1/products/slug/{slug}` (hoặc `GET /api/v1/products?slug={slug}`)

> **Public API** — Tra cứu chi tiết sản phẩm theo SEO `slug`. Hệ thống hỗ trợ cả dạng Path Variable `/slug/{slug}` lẫn Query Param `?slug=`.

**Response:** `ProductDetailResponse` (bao gồm đầy đủ các trường `specs`, `features`, `commitments`, `sizeChart`, `shipping`, `storeProfile`, `vouchers`, `descriptionRich`, `ratingDistribution`).

---

### 13.5 `GET /api/v1/products/search`

> **Public API** — Tìm kiếm sản phẩm toàn sàn dành cho storefront `/search?q=...`. Hỗ trợ tìm kiếm theo từ khóa (OpenSearch + PostgreSQL fallback), bộ lọc đa danh mục, khoảng giá, mức đánh giá sao, và phân trang kép (Cursor-based kết hợp Page-based).

**Query params:**

| Tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
|---|---|---|---|
| `q` | `String` | Không | Từ khóa tìm kiếm theo tên sản phẩm |
| `subcategory` | `List<String>` | Không | Danh sách slug hoặc ID danh mục (hỗ trợ multi-select) |
| `priceMin` | `BigDecimal` | Không | Giá tối thiểu (VND) |
| `priceMax` | `BigDecimal` | Không | Giá tối đa (VND) |
| `rating` | `Integer` | Không | Đánh giá tối thiểu (3, 4, 5) |
| `sort` | `String` | Không | `relevance` (mặc định), `newest`, `best_selling`, `price_asc`, `price_desc` |
| `page` | `Integer` | Không | Số trang (0-based) dành cho phân trang số |
| `size` | `Integer` | Không | Kích thước trang (mặc định 24/20) |
| `cursor` | `String` | Không | Token opaque phân trang cursor |

**Response:** `SearchResponse`

```json
{
  "success": true,
  "message": "Tìm kiếm sản phẩm thành công",
  "data": {
    "items": [
      {
        "id": 101,
        "storeId": 1,
        "storeName": "Sony Audio Official Store",
        "name": "Tai nghe Bluetooth Sony WF-C500 Chính Hãng",
        "imageUrl": "https://...",
        "minPrice": 990000,
        "maxPrice": 1520000,
        "totalStock": 100,
        "totalSold": 1800,
        "label": "freeship"
      }
    ],
    "total": 120,
    "size": 24,
    "page": 0,
    "pageSize": 24,
    "nextCursor": "eyJ2IjoxLCJtb2RlIjoiT1NfUElUIn0...",
    "hasMore": true,
    "totalIsExact": true,
    "facets": {
      "subcategories": [
        { "key": "thoi-trang-nam", "label": "Thời Trang Nam", "count": 42 },
        { "key": "dien-thoai", "label": "Điện Thoại & Phụ Kiện", "count": 28 }
      ],
      "ratings": [
        { "key": "5", "label": "5 sao", "count": 15 },
        { "key": "4", "label": "4 sao trở lên", "count": 68 },
        { "key": "3", "label": "3 sao trở lên", "count": 110 }
      ],
      "cities": [],
      "services": []
    }
  }
}
```

---

## 14b. Product Review Module `/api/v1/products/{productId}/reviews`

> **Public API** · **Tag Swagger**: `Product Review - Public API`

> **Trạng thái hiện tại**: chỉ `GET` public đã được implement (Subplan 3). Các endpoint còn lại (POST, GET my reviews, DELETE) đang là placeholder — sẽ bổ sung ở subplan sau (xem `AGENTS.md` mục #24).

### Endpoint

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/products/{productId}/reviews` | Public | Danh sách review + rating distribution (Subplan 3) — **Đã implement** |
| `POST` | `/api/v1/products/{productId}/reviews` | Authenticated | Tạo review (placeholder) |

### 13b.1 `GET /api/v1/products/{productId}/reviews`

Lấy danh sách review công khai của sản phẩm. Hỗ trợ lọc theo `rating` và `hasMedia`. Trả về kèm `ratingDistribution` (5 mức) và `mediaCount` (tổng số review có ảnh). Cả `ratingDistribution` và `mediaCount` luôn trả về full count — không bị ảnh hưởng bởi filter.

**Path params:**

| Field | Type | Required | Mô tả |
|---|---|---|---|
| `productId` | `long` | Có | ID sản phẩm |

**Query params:**

| Field | Type | Required | Mặc định | Mô tả |
|---|---|---|---|---|
| `rating` | `integer` | Không | – | Lọc theo số sao: `1`–`5` |
| `hasMedia` | `boolean` | Không | – | `true` = chỉ review có ảnh |
| `page` | `integer` | Không | `0` | Trang (0-indexed) |
| `size` | `integer` | Không | `10` | Số review mỗi trang (max 100) |
| `sort` | `string` | Không | `createdAt,desc` | Sắp xếp: `createdAt,desc` hoặc `createdAt,asc` |

**Response (`ProductReviewsPageResponse`):**

```json
{
  "items": [
    {
      "id": 10,
      "rating": 5,
      "comment": "Chất lượng áo rất tốt, vải mát đúng mô tả!",
      "userName": "Nguyễn Văn A",
      "userAvatarUrl": "https://res.cloudinary.com/.../avatar.jpg",
      "isVerifiedPurchase": true,
      "variantSku": "AT-DEN-M",
      "variantAttributes": { "Màu sắc": "Đen", "Size": "M" },
      "mediaUrls": [
        "https://res.cloudinary.com/.../review1.jpg",
        "https://res.cloudinary.com/.../review2.jpg"
      ],
      "sellerReply": {
        "content": "Cảm ơn bạn đã tin tưởng và ủng hộ shop!",
        "storeName": "Shop ABC",
        "repliedAt": "2026-10-05T10:00:00.000Z"
      },
      "sellerReplyAt": "2026-10-05T10:00:00.000Z",
      "createdAt": "2026-10-03T08:30:00.000Z"
    }
  ],
  "ratingDistribution": {
    "stars5": 120,
    "stars4": 45,
    "stars3": 20,
    "stars2": 8,
    "stars1": 5
  },
  "mediaCount": 95,
  "totalElements": 198,
  "totalPages": 20,
  "page": 0,
  "size": 10
}
```

**Response fields:**

| Field | Type | Mô tả |
|---|---|---|
| `items` | `Array<ProductReviewResponse>` | Danh sách review |
| `ratingDistribution` | `RatingDistributionDto` | Phân bố số sao (luôn đủ 5 mức, giá trị 0 nếu không có review sao đó) |
| `mediaCount` | `long` | Tổng số review có ảnh (album), luôn full count |
| `totalElements` | `long` | Tổng số review thỏa filter |
| `totalPages` | `int` | Tổng số trang |
| `page` | `int` | Trang hiện tại (0-indexed) |
| `size` | `int` | Kích thước trang |

**`ProductReviewResponse` fields:**

| Field | Type | Mô tả |
|---|---|---|
| `id` | `long` | ID bản ghi review |
| `rating` | `int` | Số sao đánh giá: 1–5 |
| `comment` | `String` | Nội dung bình luận / đánh giá (chuẩn DB cột `comment`) |
| `userName` | `String` | Tên hiển thị của người mua |
| `userAvatarUrl` | `String` | URL ảnh đại diện của người mua |
| `isVerifiedPurchase` | `Boolean` | Xác thực đã mua hàng (true) |
| `variantSku` | `String` | SKU của variant đã mua (nullable) |
| `variantAttributes` | `Map<String, String>` | Các thuộc tính của variant (nullable) |
| `mediaUrls` / `album` | `List<String>` | Danh sách ảnh chụp thực tế sản phẩm |
| `sellerReply` | `SellerReplyDto` | Phản hồi từ người bán dạng object (`content`, `storeName`, `repliedAt`) |
| `sellerReplyAt` | `Instant` | Thời gian seller phản hồi (nullable) |
| `createdAt` | `Instant` | Thời gian tạo review |

**`RatingDistributionDto` fields:**

| Field | Type | Mô tả |
|---|---|---|
| `stars5` | `long` | Số review 5 sao |
| `stars4` | `long` | Số review 4 sao |
| `stars3` | `long` | Số review 3 sao |
| `stars2` | `long` | Số review 2 sao |
| `stars1` | `long` | Số review 1 sao |

**Lỗi:**

| HTTP | Code | Message |
|---|---|---|
| 404 | `REVIEW_404_PRODUCT_NOT_FOUND` | Sản phẩm không tồn tại hoặc chưa được duyệt |

---

## 14. Seller Product Module `/api/v1/seller/products`

> Yêu cầu: **Role SELLER** · **Tag Swagger**: `Seller - Product Management API`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/seller/products` | Seller | Đăng bán sản phẩm SPU-SKU mới |
| `GET` | `/api/v1/seller/products` | Seller | DS sản phẩm của tôi |
| `GET` | `/api/v1/seller/products/{id}` | Seller | Chi tiết sản phẩm của tôi |
| `PUT` | `/api/v1/seller/products/{id}` | Seller | Cập nhật SPU + SKU |
| `DELETE` | `/api/v1/seller/products/{id}` | Seller | Xóa (Soft/Hard delete) |
| `PATCH` | `/api/v1/seller/products/{id}/status` | Seller | Đổi trạng thái |
| `POST` | `/api/v1/seller/products/{id}/images` | Seller | Upload ảnh gallery cho SPU (multipart) |
| `POST` | `/api/v1/seller/products/{productId}/variants/{variantId}/images` | Seller | Upload ảnh gallery cho 1 SKU (multipart) |

### 14.1 `POST /api/v1/seller/products`

**Request Body (`CreateProductRequest`):**

| Field | Type | Required | Validation |
|---|---|---|---|
| `categoryId` | `integer` | Có | không null |
| `name` | `string` | Có | max 255 |
| `description` | `string` | Không | – |
| `tierVariationConfigs` | `array<TierVariationConfigDto>` | Không | Cấu hình phân loại |
| `variants` | `array<CreateProductVariantRequest>` | Có | Min 1 phần tử |

> **Lưu ý**: `CreateProductRequest` không bao gồm `imageUrl`. Ảnh sản phẩm được upload riêng qua endpoint `POST /api/v1/seller/products/{id}/images` (mục 14.7) sau khi tạo xong SPU.

**`TierVariationConfigDto`:**

| Field | Type | Required |
|---|---|---|
| `name` | `string` | Có |
| `options` | `array<string>` | Có min 1 |

**`CreateProductVariantRequest`:**

| Field | Type | Required | Validation |
|---|---|---|---|
| `sku` | `string` | Có | max 50, unique toàn hệ thống |
| `variantName` | `string` | Có | max 150 |
| `attributes` | `object<string,string>` | Không | VD: `{"Màu sắc":"Đen"}` |
| `originalPrice` | `decimal` | Có | > 0 (`@DecimalMin("0.01")`) |
| `stockQuantity` | `integer` | Có | >= 0 |

> **Lưu ý**: `CreateProductVariantRequest` không bao gồm `imageUrl`. Ảnh SKU được upload riêng qua endpoint `POST /api/v1/seller/products/{productId}/variants/{variantId}/images` (mục 14.8) sau khi tạo xong SKU.

**HTTP Status:** 201 Created

**Validate ngầm:**

- Store phải ở trạng thái `APPROVED`.
- Số lượng SKU phải khớp với `tier_variation_configs`.
- Không trùng SKU trong cùng request.

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 403 | `PROD_403_STORE_NOT_ELIGIBLE` | Gian hàng chưa được phê duyệt hoặc không đủ điều kiện đăng bán |
| 400 | `PROD_400_INVALID_TIER_VARIATION` | Số lượng biến thể không khớp với cấu hình phân loại tier_variation_configs |
| 409 | `PROD_409_SKU_EXISTS` | Mã SKU này đã tồn tại trong hệ thống |
| 400 | `PROD_400_INVALID_PRICE` | Giá bán của biến thể phải lớn hơn 0 |
| 400 | `PROD_400_INVALID_STOCK` | Số lượng tồn kho không được nhỏ hơn 0 |
| 400 | `PROD_400_DUPLICATE_SKU` | Mã SKU không được trùng lặp trong cùng sản phẩm |

### 14.2 `GET /api/v1/seller/products`

**Query params:** `?status=ACTIVE|INACTIVE|OUT_OF_STOCK` (optional), pagination mặc định.

### 14.4 `PUT /api/v1/seller/products/{id}`

**Request Body (`UpdateProductRequest`):** giống Create nhưng `variants` là `UpdateProductVariantRequest`, có thêm field `id` (null = SKU mới) và `status`.

**Quy tắc đặc biệt:** **Không** được sửa giá hoặc **giảm** tồn kho nếu SKU đang trong Flash Sale `ACTIVE`. Bảo vệ bằng Optimistic Lock (`@Version`).

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 409 | `PROD_409_VARIANT_IN_ACTIVE_FLASH_SALE` | Không thể chỉnh sửa giá hoặc tồn kho của biến thể đang tham gia Flash Sale đang hoạt động |

### 14.5 `DELETE /api/v1/seller/products/{id}`

- **Soft delete** (`status=INACTIVE`) nếu SKU đã xuất hiện trong `order_items` hoặc `flash_sale_items`.
- **Hard delete** nếu chưa phát sinh đơn.

### 14.6 `PATCH /api/v1/seller/products/{id}/status?status=...`

**Query:** `status=ACTIVE|INACTIVE|OUT_OF_STOCK`

### 14.7 `POST /api/v1/seller/products/{id}/images`

Upload ảnh mới vào gallery SPU. Một SPU có thể có nhiều ảnh; partial unique index đảm bảo tối đa 1 ảnh `isPrimary=true` ACTIVE.

**Request Body (`multipart/form-data`):**

| Field | Type | Required | Mô tả |
|---|---|---|---|
| `file` | `binary` | Có | File ảnh (jpg/png/webp), max 10MB |
| `isPrimary` | `boolean` | Không | Đánh dấu ảnh chính (mặc định `false`) |
| `displayOrder` | `integer` | Không | Thứ tự hiển thị (mặc định `0`) |

**HTTP Status:** 201 Created

**Response:** `ImageResponse` (xem mục 28.4).

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 404 | `PROD_404_NOT_FOUND` | Không tìm thấy sản phẩm |
| 403 | `PROD_403_ACCESS_DENIED` | Bạn không có quyền upload ảnh cho sản phẩm này |
| 400 | `IMAGE_400` | File không hợp lệ (>10MB hoặc sai định dạng) |
| 502 | `IMAGE_502` | Cloudinary upload thất bại |

### 14.8 `POST /api/v1/seller/products/{productId}/variants/{variantId}/images`

Upload ảnh mới vào gallery của một SKU cụ thể. Partial unique index áp dụng tương tự SPU.

**Request Body (`multipart/form-data`):** giống 14.7.

**HTTP Status:** 201 Created

**Lỗi:** giống 14.7 (404 cho `variantId` dùng `PROD_404_VARIANT_NOT_FOUND`).

---
## 15. Flash Sale Public Module `/api/v1/flash-sales/slots`

> **Public API** · **Tag Swagger**: `Public Flash Sale`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/flash-sales/slots` | Public | DS phiên Flash Sale đang/sắp diễn ra + tồn kho realtime |
| `GET` | `/api/v1/flash-sales/items/active` | Public | DS sản phẩm Flash Sale đang active phục vụ trang chủ (Realtime stock) |

### 15.1 `GET /api/v1/flash-sales/slots`

**Response item (`PublicFlashSaleSlotResponse`):**

```json
[
  {
    "id": 1,
    "title": "Flash Sale 12h Trưa",
    "startTime": "2026-10-04T04:00:00Z",
    "endTime": "2026-10-04T05:00:00Z",
    "reservationTtlSeconds": 300,
    "status": "UPCOMING",
    "items": [
      {
        "id": 7,
        "slotId": 1,
        "variantId": 42,
        "sku": "AT-DEN-M",
        "variantName": "Đen, Size M",
        "productName": "Áo thun nam",
        "imageUrl": "https://...",
        "originalPrice": 199000,
        "flashSalePrice": 99000,
        "allocatedStock": 100,
        "availableStock": 87,
        "userPurchaseLimit": 2,
        "status": "APPROVED"
      }
    ]
  }
]
```

**`availableStock`** được lấy **realtime từ Redis** (không phải DB). DB chỉ là nguồn bền vững, Redis là source-of-truth tạm thời cho tồn kho Flash Sale.

---

### 15.2 `GET /api/v1/flash-sales/items/active`

> **Public API** — Lấy danh sách toàn bộ các sản phẩm Flash Sale đang ACTIVE trong các khung giờ hiện tại phục vụ hiển thị section Flash Sale trên trang chủ (không yêu cầu đăng nhập).

**Response:** `List<PublicFlashSaleItemResponse>`

```json
[
  {
    "id": 7,
    "slotId": 1,
    "variantId": 42,
    "sku": "AT-DEN-M",
    "variantName": "Đen, Size M",
    "productName": "Áo thun nam Cotton",
    "imageUrl": "https://res.cloudinary.com/.../shirt.jpg",
    "originalPrice": 199000,
    "flashSalePrice": 99000,
    "allocatedStock": 100,
    "availableStock": 87,
    "userPurchaseLimit": 2,
    "status": "APPROVED"
  }
]
```

---

## 16. Flash Sale Reservation Module `/api/v1/flash-sales/reservations`

> Yêu cầu: **Authentication**

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/flash-sales/reservations` | Authenticated | Đặt hàng giữ chỗ Flash Sale |

### 16.1 `POST /api/v1/flash-sales/reservations`

**Headers:**

```http
Authorization: Bearer <access_token>
Idempotency-Key: <uuid-v4>          # Khuyến nghị: luôn gửi để retry an toàn
```

**Request Body (`CreateReservationRequest`):**

| Field | Type | Required | Validation | Mô tả |
|---|---|---|---|---|
| `flashSaleItemId` | `long` | Có | không null | ID mục Flash Sale (lấy từ `/flash-sales/slots`) |
| `addressId` | `long` | Có | không null | ID địa chỉ nhận hàng (phải là của user hiện tại) |
| `quantity` | `integer` | Có | > 0, mặc định 1 | Số lượng muốn mua |

**HTTP Status:** 201 Created

**Hành vi ngầm (Lua script):**

1. Trừ stock nguyên tử trên Redis (`flash_sale:stock:{itemId}`).
2. Check purchase limit theo user (`flash_sale:user_limit:{slotId}:{userId}:{itemId}`).
3. Tạo đơn `PENDING_PAYMENT` qua `FlashSaleOrderPort`.
4. Nếu DB fail → **compensation tự động** trả lại đúng số lượng về Redis + giảm `available_stock` trong DB.

**Response (`ReservationResponse`):**

```json
{
  "success": true,
  "code": 201,
  "message": "Operation completed successfully",
  "data": {
    "orderId": 123,
    "orderCode": "ORD-20261003-00001",
    "flashSaleItemId": 7,
    "quantity": 1,
    "totalAmount": 99000,
    "status": "PENDING_PAYMENT",
    "expiresAt": "2026-10-03T14:27:35Z",
    "message": "Đặt hàng thành công, vui lòng thanh toán trong 5 phút"
  },
  "timestamp": "..."
}
```

**Lỗi thường gặp:**

| Code | ErrorCode | Message |
|---|---|---|
| 404 | `FS_404_ITEM_NOT_FOUND` | Không tìm thấy sản phẩm trong Flash Sale |
| 404 | `USER_404_ADDRESS` | Không tìm thấy địa chỉ yêu cầu |
| 403 | `FS_403_ADDRESS_INVALID` | Địa chỉ giao hàng không hợp lệ hoặc không thuộc về người dùng |
| 409 | `FS_409_OUT_OF_STOCK` | Sản phẩm Flash Sale đã hết hàng tồn kho |
| 409 | `FS_409_PURCHASE_LIMIT_EXCEEDED` | Bạn đã vượt quá giới hạn số lượng mua cho sản phẩm này trong phiên Flash Sale |
| 400 | `FS_400_SLOT_NOT_ACTIVE` | Khung giờ Flash Sale hiện không hoạt động |
| 400 | `FS_400_SLOT_ALREADY_ENDED` | Khung giờ Flash Sale đã kết thúc |
| 400 | `FS_400_IDEMPOTENCY_KEY_MISSING` | Yêu cầu thiếu Idempotency-Key (tuỳ chính sách) |
| 409 | `FS_409_IDEMPOTENCY_CONFLICT` | Yêu cầu với Idempotency-Key này đang được xử lý, vui lòng không gửi lặp lại |
| 500 | `FS_500_ORDER_FAILED` | Hệ thống bận khi khởi tạo đơn hàng Flash Sale |

> **Quan trọng cho Frontend**:
> - Sau khi reservation thành công, đơn ở trạng thái `PENDING_PAYMENT` trong `expiresAt` (mặc định 300s). Hết hạn sẽ tự động hủy và hoàn stock.
> - Luôn gửi `Idempotency-Key` để retry an toàn khi mạng lỗi.

---

## 17. Admin Flash Sale Module `/api/v1/admin/flash-sales`

> Yêu cầu: **Role ADMIN** · **Tag Swagger**: `Admin Flash Sale`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/admin/flash-sales/slots` | Admin | Tạo phiên Flash Sale |
| `PUT` | `/api/v1/admin/flash-sales/slots/{id}` | Admin | Cập nhật phiên |
| `GET` | `/api/v1/admin/flash-sales/slots/{id}` | Admin | Chi tiết phiên |
| `GET` | `/api/v1/admin/flash-sales/slots` | Admin | DS tất cả phiên |
| `PATCH` | `/api/v1/admin/flash-sales/items/{id}/approve` | Admin | Duyệt SKU Flash Sale |
| `POST` | `/api/v1/admin/flash-sales/slots/{id}/pre-warm` | Admin | Pre-warm nạp Redis |

### 17.1 `POST /api/v1/admin/flash-sales/slots`

**Request Body (`CreateFlashSaleSlotRequest`):**

| Field | Type | Required | Validation |
|---|---|---|---|
| `title` | `string` | Có | không blank |
| `startTime` | `instant` | Có | < endTime |
| `endTime` | `instant` | Có | > startTime |
| `reservationTtlSeconds` | `integer` | Không | mặc định `300` (5 phút), > 0 |

**HTTP Status:** 201 Created

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 400 | `FS_400_SLOT_TIME_INVALID` | Thời gian bắt đầu phải trước thời gian kết thúc |
| 409 | `FS_409_SLOT_OVERLAP` | Khung giờ Flash Sale bị trùng lặp với khung giờ đã tồn tại |

### 17.2 `PUT /api/v1/admin/flash-sales/slots/{id}`

**Request Body (`UpdateFlashSaleSlotRequest`):** giống Create + field `status`.

Chặn update nếu slot đã `ENDED`.

### 17.3 `GET /api/v1/admin/flash-sales/slots/{id}`

**Response (`FlashSaleSlotResponse`):**

```json
{
  "id": 1,
  "title": "Flash Sale 12h Trưa",
  "startTime": "2026-10-04T04:00:00Z",
  "endTime": "2026-10-04T05:00:00Z",
  "reservationTtlSeconds": 300,
  "status": "UPCOMING",
  "createdAt": "2026-10-03T..."
}
```

### 17.5 `PATCH /api/v1/admin/flash-sales/items/{id}/approve`

Duyệt đăng ký SKU Flash Sale → khấu trừ kho gốc `product_variants.stock_quantity` nguyên tử.

### 17.6 `POST /api/v1/admin/flash-sales/slots/{id}/pre-warm`

Pre-warm nạp tồn kho ban đầu lên Redis bằng `SETNX` (idempotent, tính cả đơn PENDING hiện có). Gọi trước khi phiên `ACTIVE`.

---

## 18. Seller Flash Sale Module `/api/v1/seller/flash-sales`

> Yêu cầu: **Role SELLER** · **Tag Swagger**: `Seller Flash Sale`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/seller/flash-sales/items` | Seller | Đăng ký SKU Flash Sale |

### 18.1 `POST /api/v1/seller/flash-sales/items`

**Request Body (`RegisterFlashSaleItemRequest`):**

| Field | Type | Required | Validation | Mô tả |
|---|---|---|---|---|
| `slotId` | `long` | Có | – | ID phiên Flash Sale |
| `variantId` | `long` | Có | – | ID biến thể SKU |
| `flashSalePrice` | `decimal` | Có | > 0 và < originalPrice | Giá Flash Sale |
| `allocatedStock` | `integer` | Có | > 0 và ≤ stockQuantity gốc | Số lượng phân bổ |
| `userPurchaseLimit` | `integer` | Không | > 0, mặc định 1 | Giới hạn mua/user |
| `commissionRateOverride` | `decimal` | Không | 0-1 | Override hoa hồng danh mục |

**HTTP Status:** 201 Created

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 403 | `FS_403_STORE_NOT_APPROVED` | Gian hàng chưa được duyệt, không thể tham gia Flash Sale |
| 403 | `FS_403_NOT_STORE_OWNER` | Bạn không có quyền quản lý sản phẩm hoặc gian hàng này |
| 400 | `FS_400_INVALID_PRICE` | Giá Flash Sale phải lớn hơn 0 và nhỏ hơn giá gốc của biến thể |
| 400 | `FS_400_INVALID_ALLOCATED_STOCK` | Số lượng tồn kho phân bổ cho Flash Sale không hợp lệ hoặc vượt quá tồn kho gốc |
| 400 | `FS_400_INVALID_PURCHASE_LIMIT` | Giới hạn mua của người dùng phải lớn hơn 0 |
| 409 | `FS_409_ITEM_ALREADY_REGISTERED` | Biến thể sản phẩm này đã được đăng ký trong khung giờ |
| 409 | `FS_409_INSUFFICIENT_BASE_STOCK` | Tồn kho gốc của sản phẩm không đủ để phân bổ cho Flash Sale |

**Response (`FlashSaleItemResponse`):**

```json
{
  "id": 7,
  "slotId": 1,
  "variantId": 42,
  "flashSalePrice": 99000,
  "allocatedStock": 100,
  "availableStock": 100,
  "userPurchaseLimit": 2,
  "commissionRateOverride": null,
  "status": "PENDING",
  "createdAt": "2026-10-03T..."
}
```

---
## 19. Cart Module `/api/v1/cart`

> Yêu cầu: **Authentication** (Bearer JWT) · **Tag Swagger**: `Cart` · **Phân quyền thực tế**: `ROLE_BUYER` (filter chỉ cho phép buyer/seller có quyền tạo đơn; user chưa đăng nhập → `401 AUTH_401`)

| Method | Endpoint | Access | HTTP Status | Mô tả | FE cần gửi |
|---|---|---|---|---|---|
| `GET` | `/api/v1/cart` | `ROLE_BUYER` | `200 OK` | Xem giỏ hàng hiện tại (gom nhóm theo `storeId`, tính `subtotal`/`totalItems`/`grandTotal`) | `Authorization: Bearer <accessToken>` |
| `POST` | `/api/v1/cart/items` | `ROLE_BUYER` | `201 Created` | Thêm 1 biến thể sản phẩm vào giỏ. Nếu variant đã có → cộng dồn `quantity`. Trả về `CartResponse` mới nhất. | `Authorization` + JSON `AddToCartRequest` (xem 19.2) |
| `PUT` | `/api/v1/cart/items/{itemId}` | `ROLE_BUYER` | `200 OK` | Cập nhật số lượng tuyệt đối của 1 item trong giỏ. Trả về `CartResponse` mới nhất. | `Authorization` + path `itemId` + JSON `UpdateCartItemRequest` (xem 19.3) |
| `DELETE` | `/api/v1/cart/items/{itemId}` | `ROLE_BUYER` | `200 OK` | Xóa 1 item khỏi giỏ | `Authorization` + path `itemId` |
| `DELETE` | `/api/v1/cart` | `ROLE_BUYER` | `200 OK` | Xóa toàn bộ item trong giỏ (giữ lại bản ghi `Cart`) | `Authorization` |

> **Lưu ý FE**: Mỗi lần add / update / remove / clear, server trả về `CartResponse` đầy đủ → FE nên dùng luôn `response.data` để re-render, không cần gọi lại `GET /api/v1/cart`.

### 19.1 `GET /api/v1/cart`

Lấy giỏ hàng hiện tại của user đang đăng nhập. Service sẽ tự tạo `Cart` rỗng nếu user chưa có. Kết quả được nhóm theo từng `storeId` để chuẩn bị cho flow checkout đa cửa hàng.

**Request:** Không có body, không có query param.

**Response (`ApiResponse<CartResponse>`, HTTP `200`):**

```json
{
  "success": true,
  "code": 200,
  "message": "Lấy thông tin giỏ hàng thành công",
  "data": {
    "id": 1,
    "userId": 5,
    "storeGroups": [
      {
        "storeId": 2,
        "storeName": "Shop ABC",
        "storeBadge": "Mall",
        "estimatedDeliveryLabel": "Giao vào 18 Th10 - 20 Th10",
        "shippingMethodLabel": "Tiêu chuẩn",
        "shippingFee": 0,
        "storeSubtotal": 198000,
        "items": [
          {
            "id": 11,
            "productId": 42,
            "variantId": 42,
            "sku": "AT-DEN-M",
            "productName": "Áo thun nam",
            "variantName": "Đen, Size M",
            "imageUrl": "https://res.cloudinary.com/.../variant-42.jpg",
            "price": 99000,
            "stockQuantity": 25,
            "quantity": 2,
            "itemSubtotal": 198000,
            "storeId": 2,
            "storeName": "Shop ABC"
          }
        ]
      }
    ],
    "totalItems": 2,
    "subtotal": 198000,
    "totalShippingFee": 0,
    "totalAmount": 198000,
    "grandTotal": 198000
  },
  "timestamp": "2026-10-16T..."
}
```

**Schema `CartResponse` (8 field, tham chiếu `cart/dto/CartResponse.java`):**

| Field | Type | Mô tả |
|---|---|---|
| `id` | `Long` | ID bản ghi `Cart` |
| `userId` | `Long` | ID chủ sở hữu giỏ |
| `storeGroups` | `List<CartStoreGroupResponse>` | DS nhóm sản phẩm theo cửa hàng |
| `totalItems` | `Integer` | Tổng số lượng sản phẩm (cộng tất cả `quantity`) |
| `subtotal` | `BigDecimal` | Tổng tiền hàng (sum `itemSubtotal`) |
| `totalShippingFee` | `BigDecimal` | Tổng phí ship (hiện mặc định `0` vì `shippingFee` hard-code) |
| `totalAmount` | `BigDecimal` | `subtotal + totalShippingFee` |
| `grandTotal` | `BigDecimal` | Bằng `subtotal` (giữ để tương thích flow UI cũ) |

**Schema `CartStoreGroupResponse` (8 field):**

| Field | Type | Mô tả |
|---|---|---|
| `storeId` | `Long` | ID cửa hàng |
| `storeName` | `String` | Tên cửa hàng |
| `storeBadge` | `String` | `"OfficialMall"` (Mall chính hãng) / `"Mall"` (Mall ưu tiên) / `""` (còn lại) |
| `estimatedDeliveryLabel` | `String` | Hard-code: `"Giao vào <dd ThM> - <dd ThM>"` (today + 2 → today + 4) |
| `shippingMethodLabel` | `String` | Hard-code: `"Tiêu chuẩn"` |
| `shippingFee` | `BigDecimal` | Mặc định `0` (chưa tính phí theo khu vực) |
| `storeSubtotal` | `BigDecimal` | Tổng tiền hàng của riêng store này |
| `items` | `List<CartItemResponse>` | DS sản phẩm thuộc store |

**Schema `CartItemResponse` (13 field):**

| Field | Type | Mô tả |
|---|---|---|
| `id` | `Long` | ID `CartItem` |
| `productId` | `Long` | ID `Product` |
| `variantId` | `Long` | ID `ProductVariant` |
| `sku` | `String` | SKU biến thể |
| `productName` | `String` | Tên sản phẩm |
| `variantName` | `String` | Tên biến thể (vd "Đen, Size M") |
| `imageUrl` | `String` | Ảnh variant (ưu tiên) → fallback ảnh product → `null` |
| `price` | `BigDecimal` | Đơn giá hiện tại (`variant.originalPrice`) |
| `stockQuantity` | `Integer` | Tồn kho hiện tại của variant |
| `quantity` | `Integer` | Số lượng trong giỏ |
| `itemSubtotal` | `BigDecimal` | `price * quantity` |
| `storeId` | `Long` | ID cửa hàng (trùng với group) |
| `storeName` | `String` | Tên cửa hàng (trùng với group) |

**Lỗi:**

| HTTP | ErrorCode | Message | Nguyên nhân |
|---|---|---|---|
| 401 | `AUTH_401` | Chưa xác thực hoặc token không hợp lệ | Thiếu/sai `Authorization` header |
| 404 | `USER_404_NOT_FOUND` | Không tìm thấy thông tin người dùng | `userId` từ JWT không tồn tại trong DB |

### 19.2 `POST /api/v1/cart/items`

Thêm 1 variant vào giỏ. Nếu variant đã có trong giỏ thì cộng dồn `quantity` mới vào `quantity` cũ (kiểm tra stock lại sau khi cộng). Trả về `CartResponse` mới nhất.

**Request Body (`AddToCartRequest`, validate bằng Jakarta Bean Validation):**

| Field | Type | Required | Constraint (annotation) | Mô tả FE gửi |
|---|---|---|---|---|
| `variantId` | `Long` | Có | `@NotNull(message = "ID biến thể sản phẩm không được để trống")` | ID của `ProductVariant` (lấy từ API product detail) |
| `quantity` | `Integer` | Có | `@NotNull` + `@Positive` | Số lượng muốn thêm (lớn hơn 0) |

**Response:** `ApiResponse<CartResponse>` với HTTP `201 Created`, message `"Thêm sản phẩm vào giỏ hàng thành công"`. Body `data` giống cấu trúc 19.1.

**Lỗi:**

| HTTP | ErrorCode | Message | Nguyên nhân |
|---|---|---|---|
| 400 | `SYS_422` (validation) | `ID biến thể sản phẩm không được để trống` / `Số lượng phải lớn hơn 0` | `variantId` null hoặc `quantity <= 0` |
| 400 | `CART_400_1` (`INSUFFICIENT_STOCK`) | Số lượng tồn kho sản phẩm không đủ | `variant.stockQuantity < (existing.quantity + request.quantity)` |
| 400 | `CART_400_2` (`INVALID_QUANTITY`) | Số lượng sản phẩm thêm vào giỏ phải lớn hơn 0 | Service check `request.quantity <= 0` (dư thừa với Bean Validation) |
| 404 | `CART_ITEM_404` | Không tìm thấy sản phẩm trong giỏ hàng | `variantId` không tồn tại (re-use error code) |
| 401 | `AUTH_401` | Chưa xác thực hoặc token không hợp lệ | Thiếu/sai `Authorization` header |
| 404 | `USER_404_NOT_FOUND` | Không tìm thấy thông tin người dùng | `userId` từ JWT không tồn tại |

### 19.3 `PUT /api/v1/cart/items/{itemId}`

Cập nhật số lượng tuyệt đối (không phải cộng dồn) cho 1 item. Service kiểm tra `cartItem.cart.id == currentUser.cart.id` để chống thao tác chéo giỏ.

**Path Variable:** `itemId` (Long) — ID của `CartItem`.

**Request Body (`UpdateCartItemRequest`):**

| Field | Type | Required | Constraint | Mô tả FE gửi |
|---|---|---|---|---|
| `quantity` | `Integer` | Có | `@NotNull` + `@Positive` | Số lượng mới (lớn hơn 0) |

**Response:** `ApiResponse<CartResponse>` với HTTP `200 OK`, message `"Cập nhật số lượng thành công"`.

**Lỗi:**

| HTTP | ErrorCode | Message | Nguyên nhân |
|---|---|---|---|
| 400 | `SYS_422` (validation) | `Số lượng phải lớn hơn 0` | `quantity <= 0` |
| 400 | `CART_400_1` (`INSUFFICIENT_STOCK`) | Số lượng tồn kho sản phẩm không đủ | `variant.stockQuantity < request.quantity` |
| 400 | `CART_400_2` (`INVALID_QUANTITY`) | Số lượng sản phẩm thêm vào giỏ phải lớn hơn 0 | Service check dư thừa |
| 403 | `CART_403` (`CART_ITEM_ACCESS_DENIED`) | Bạn không có quyền thao tác trên mục giỏ hàng này | `item.cart.id != currentUser.cart.id` |
| 404 | `CART_ITEM_404` | Không tìm thấy sản phẩm trong giỏ hàng | `itemId` không tồn tại |
| 401 | `AUTH_401` | Chưa xác thực hoặc token không hợp lệ | Thiếu/sai `Authorization` header |
| 404 | `USER_404_NOT_FOUND` | Không tìm thấy thông tin người dùng | `userId` từ JWT không tồn tại |

### 19.4 `DELETE /api/v1/cart/items/{itemId}`

Xóa 1 item khỏi giỏ. Service kiểm tra ownership trước khi xóa.

**Path Variable:** `itemId` (Long) — ID của `CartItem`.

**Response:** `ApiResponse<Void>` với HTTP `200 OK`, message `"Xóa sản phẩm khỏi giỏ hàng thành công"`, `data = null`.

**Lỗi:**

| HTTP | ErrorCode | Message | Nguyên nhân |
|---|---|---|---|
| 403 | `CART_403` (`CART_ITEM_ACCESS_DENIED`) | Bạn không có quyền thao tác trên mục giỏ hàng này | `item.cart.id != currentUser.cart.id` |
| 404 | `CART_ITEM_404` | Không tìm thấy sản phẩm trong giỏ hàng | `itemId` không tồn tại |
| 401 | `AUTH_401` | Chưa xác thực hoặc token không hợp lệ | Thiếu/sai `Authorization` header |
| 404 | `USER_404_NOT_FOUND` | Không tìm thấy thông tin người dùng | `userId` từ JWT không tồn tại |

### 19.5 `DELETE /api/v1/cart`

Xóa toàn bộ `CartItem` của user (giữ lại bản ghi `Cart` rỗng để lần sau add thêm không phải tạo mới). Thực hiện bằng `cartItemRepository.deleteAllByCartId(cartId)`.

**Response:** `ApiResponse<Void>` với HTTP `200 OK`, message `"Xóa toàn bộ giỏ hàng thành công"`, `data = null`.

**Lỗi:**

| HTTP | ErrorCode | Message | Nguyên nhân |
|---|---|---|---|
| 401 | `AUTH_401` | Chưa xác thực hoặc token không hợp lệ | Thiếu/sai `Authorization` header |
| 404 | `USER_404_NOT_FOUND` | Không tìm thấy thông tin người dùng | `userId` từ JWT không tồn tại |

### 19.6 Cart Error Codes (tổng hợp)

Tham chiếu `cart/exception/CartErrorCode.java`. Có **5 mã lỗi** riêng của module Cart:

| HTTP | Code | Ý nghĩa |
|---|---|---|
| 404 | `CART_404` | Không tìm thấy giỏ hàng của người dùng (hiện service tự tạo nên không phát sinh) |
| 404 | `CART_ITEM_404` | Không tìm thấy `CartItem` theo `id` hoặc `variantId` không tồn tại |
| 403 | `CART_403` | Không có quyền thao tác trên `CartItem` (không thuộc giỏ của user hiện tại) |
| 400 | `CART_400_1` (`INSUFFICIENT_STOCK`) | Tồn kho `ProductVariant` không đủ cho số lượng yêu cầu (khi add hoặc update) |
| 400 | `CART_400_2` (`INVALID_QUANTITY`) | `quantity <= 0` (dư thừa cùng Bean Validation) |

> **Note (2026-10-16)**: Endpoint `POST /api/v1/cart/checkout` được liệt kê trong rule `.cursor/rules/46-endpoints-cart-order-voucher-banner.mdc` (mục "Giỏ hàng") nhưng **CHƯA được implement** trong `CartController.java`. Flow tách giỏ theo store hiện do `OrderBuyerController` xử lý qua `POST /api/v1/orders/checkout` (xem section 20.1) — FE vẫn dùng endpoint Order làm chuẩn.

---

## 20. Order Buyer Module `/api/v1/orders`

> Yêu cầu: **Authentication** · **Tag Swagger**: `Orders Buyer`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/orders/checkout` | Authenticated | Đặt hàng từ giỏ (tách đơn đa gian hàng) |
| `GET` | `/api/v1/orders` | Authenticated | DS đơn của tôi (phân trang) |
| `GET` | `/api/v1/orders/{id}` | Authenticated (Owner) | Chi tiết đơn hàng |
| `PATCH` | `/api/v1/orders/{id}/cancel` | Authenticated (Owner) | Hủy đơn (chưa thanh toán) |

### 20.1 `POST /api/v1/orders/checkout`

**Request Body (`CreateOrderRequest`):**

| Field | Type | Required | Validation |
|---|---|---|---|
| `shippingAddressId` | `long` | Có | không null |
| `storeOrders` | `array<CheckoutStoreOrderRequest>` | Có | min 1, tách theo store |

**`CheckoutStoreOrderRequest`:**

| Field | Type | Required | Mô tả |
|---|---|---|---|
| `storeId` | `long` | Có | ID gian hàng |
| `items` | `array<CheckoutItemRequest>` | Có | DS sản phẩm |
| `voucherCode` | `string` | Không | Mã voucher của store (optional) |

**`CheckoutItemRequest`:**

| Field | Type | Required | Validation |
|---|---|---|---|
| `variantId` | `long` | Có | không null |
| `quantity` | `integer` | Có | > 0 |

**Hành vi:** Cart có nhiều store → tách thành nhiều Order độc lập theo store. Không tạo Parent Order (đúng thiết kế 24 bảng).

**HTTP Status:** 201 Created

**Response:** Array các `OrderResponse` (mỗi store một Order):

```json
{
  "success": true,
  "code": 201,
  "message": "Đặt hàng thành công",
  "data": [
    {
      "id": 100,
      "orderCode": "ORD-20261003-00001",
      "buyerId": 5,
      "storeId": 2,
      "storeName": "Shop ABC",
      "slotId": null,
      "voucherId": 3,
      "recipientName": "Nguyễn Văn A",
      "recipientPhone": "0987654321",
      "shippingAddressText": "Nguyễn Văn A - 0987654321 - 12 Nguyễn Huệ, ...",
      "subtotalAmount": 198000,
      "voucherDiscountAmount": 10000,
      "totalAmount": 188000,
      "commissionRate": 0.10,
      "platformFee": 18800,
      "sellerAmount": 169200,
      "status": "PENDING_PAYMENT",
      "paymentMethod": "ZALOPAY",
      "paymentStatus": "PENDING",
      "paymentUrl": "https://zalopay.vn/...",
      "qrCodeUrl": "https://...",
      "trackingNumber": "VNPOST12345678",
      "shippedAt": null,
      "completedAt": null,
      "timeline": [
        {
          "status": "PENDING_PAYMENT",
          "label": "Chờ thanh toán",
          "at": "2026-10-03T10:00:00.000Z"
        }
      ],
      "expiresAt": null,
      "createdAt": "2026-10-03T...",
      "updatedAt": "2026-10-03T...",
      "items": [
        {
          "id": 200,
          "variantId": 42,
          "flashSaleItemId": null,
          "productName": "Áo thun nam",
          "variantName": "Đen, Size M",
          "priceAtPurchase": 99000,
          "quantity": 2,
          "itemSubtotal": 198000
        }
      ]
    }
  ]
}
```

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 403 | `USER_403_ADDRESS_DENIED` | Địa chỉ không thuộc user hiện tại |
| 400 | `ORDER_400_2` (`INSUFFICIENT_VARIANT_STOCK`) | Số lượng tồn kho sản phẩm không đủ để đặt hàng |
| 400 | `ORDER_400_4` (`STORE_NOT_APPROVED`) | Gian hàng chưa được duyệt hoặc đang bị khóa, không thể đặt hàng |

### 20.2 `GET /api/v1/orders`

**Query:** `?page=0&size=10` (mặc định sort `createdAt DESC`)

### 20.4 `PATCH /api/v1/orders/{id}/cancel`

Hủy đơn khi chưa thanh toán.

**Lỗi:**

| Code | ErrorCode | Message |
|---|---|---|
| 404 | `ORDER_404` | Không tìm thấy đơn hàng |
| 403 | `ORDER_403` | Bạn không có quyền truy cập hoặc thao tác trên đơn hàng này |
| 400 | `ORDER_400_3` (`INVALID_STATUS_TRANSITION`) | Trạng thái chuyển đổi của đơn hàng không hợp lệ |

---

### 20.5 `GET /api/v1/orders/{id}/payment`

> Yêu cầu: **Authentication** (người mua đơn hàng).

Lấy thông tin và trạng thái thanh toán hiện tại của đơn hàng.

**Response (`PaymentResponse`):** `orderId`, `orderCode`, `paymentMethod`, `amount`, `status`, `paymentUrl`, `qrCodeUrl`, `transactionCode`, `paidAt`, `createdAt`.

### 20.6 `POST /api/v1/orders/{id}/payment/zalopay`

> Yêu cầu: **Authentication** (người mua đơn hàng).

Khởi tạo giao dịch ZaloPay và lấy QR Code thanh toán cho đơn hàng.

**Response:** `PaymentResponse` chứa `paymentUrl` và `qrCodeUrl`.

### 20.7 `POST /api/v1/orders/{id}/payment/cod`

> Yêu cầu: **Authentication** (người mua đơn hàng).

Xác nhận thanh toán COD (tiền mặt khi nhận hàng) và cập nhật đơn hàng sang trạng thái `CONFIRMED`.

**Response:** `PaymentResponse` với `paymentMethod: "COD"`.

---

## 21. Seller Order Module `/api/v1/seller/orders`

> Yêu cầu: **Role SELLER**

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/seller/orders` | Seller | DS đơn của shop (phân trang) |
| `PATCH` | `/api/v1/seller/orders/{id}/status` | Seller | Cập nhật trạng thái đơn |

### 21.2 `PATCH /api/v1/seller/orders/{id}/status`

**Request Body (`UpdateOrderStatusRequest`):**

| Field | Type | Required |
|---|---|---|
| `status` | `string` | Có |

**Các giá trị `status` hợp lệ** (tham khảo từ design): `PENDING_PAYMENT`, `CONFIRMED`, `SHIPPED`, `COMPLETED`, `CANCELLED`, `CANCELLED_TIMEOUT`. Seller chỉ có thể chuyển các trạng thái phù hợp với quy trình nghiệp vụ.

---
## 22. Voucher Module `/api/v1/vouchers`

> Module quản lý mã giảm giá ở phía người dùng (Buyer). Bao gồm: xem danh sách voucher Platform/Shop, lưu voucher vào ví cá nhân, kiểm tra áp dụng mã trước khi checkout.  
> **Tag Swagger**: `Vouchers Public`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/vouchers/platform` | **Public** | Danh sách voucher toàn sàn (storeId = null) |
| `GET` | `/api/v1/vouchers/store/{storeId}` | **Public** | Danh sách voucher của 1 gian hàng |
| `GET` | `/api/v1/vouchers/mine` | Authenticated | Voucher trong ví cá nhân |
| `POST` | `/api/v1/vouchers/mine` | Authenticated | Lưu voucher vào ví cá nhân |
| `POST` | `/api/v1/vouchers/apply` | Optional auth | Tính toán số tiền giảm khi áp mã (preview) |

### 22.1 `GET /api/v1/vouchers/platform` — Danh sách voucher toàn sàn

| Mục | Giá trị |
|---|---|
| Method | `GET` |
| Path | `/api/v1/vouchers/platform` |
| Auth | **Không bắt buộc** (Public) |
| Role | Public — ai cũng xem được |
| Request body / query | Không |

**Response `200 OK`** — `List<VoucherResponse>`:

```json
{
  "success": true,
  "code": 200,
  "message": "Lấy danh sách voucher sàn thành công",
  "data": [
    {
      "id": 1,
      "code": "PLATFORM10",
      "storeId": null,
      "discountType": "PERCENT",
      "discountValue": 10.00,
      "minOrderAmount": 200000.00,
      "maxDiscountAmount": 50000.00,
      "totalQuantity": 1000,
      "usedQuantity": 0,
      "userUsageLimit": 1,
      "startTime": "2026-10-01T00:00:00Z",
      "endTime": "2026-12-31T23:59:59Z",
      "status": "ACTIVE",
      "createdAt": "2026-10-01T00:00:00Z"
    }
  ]
}
```

> BE lọc `vouchers WHERE store_id IS NULL`. Trả về tất cả (kể cả `INACTIVE` / quá hạn — FE tự lọc theo `status` + `endTime`).

### 22.2 `GET /api/v1/vouchers/store/{storeId}` — Danh sách voucher của 1 shop

| Mục | Giá trị |
|---|---|
| Method | `GET` |
| Path | `/api/v1/vouchers/store/{storeId}` |
| Path param | `storeId` (Long, required) |
| Auth | **Không bắt buộc** (Public) |

**Response `200 OK`** — `List<VoucherResponse>` (cấu trúc giống 22.1 nhưng `storeId` khác `null`).

### 22.3 `GET /api/v1/vouchers/mine` — Voucher trong ví của tôi

| Mục | Giá trị |
|---|---|
| Method | `GET` |
| Path | `/api/v1/vouchers/mine` |
| Auth | **BẮT BUỘC** `Authorization: Bearer <access_token>` |
| Role | Bất kỳ (BUYER, SELLER, ADMIN) |
| Request body / query | Không |

**Response `200 OK`** — `List<UserVoucherResponse>`:

```json
{
  "success": true,
  "code": 200,
  "message": "Lấy danh sách kho voucher thành công",
  "data": [
    {
      "id": 1,
      "code": "PLATFORM10",
      "label": "Giảm 10% tối đa 50K đơn từ 200K",
      "scope": "PLATFORM",
      "storeId": null,
      "storeName": null,
      "discountType": "PERCENT",
      "discountValue": 10.00,
      "maxDiscount": 50000.00,
      "minOrderAmount": 200000.00,
      "userUsageLimit": 1,
      "usedQuantity": 0,
      "totalQuantity": 1000,
      "startTime": "2026-10-01T00:00:00Z",
      "endTime": "2026-12-31T23:59:59Z",
      "status": "ACTIVE"
    }
  ]
}
```

> `status` = `ACTIVE` | `USED` | `EXPIRED` (BE tự tính từ `endTime` + `usedQuantity` của user này).

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 401 | `COMMON_401` | Thiếu token |

### 22.4 `POST /api/v1/vouchers/mine` — Lưu voucher vào ví

**Request Body** (`SaveVoucherRequest` — `application/json`):

| Field | Type | Required | Validation |
|---|---|---|---|
| `code` | `string` | **Có** | `@NotBlank`, `@Size(max=30)`; BE `trim()` + lookup `findByCodeIgnoreCase` |

**Response `200 OK`** — `UserVoucherResponse`.

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `code` rỗng hoặc > 30 ký tự |
| 400 | `VOUCHER_400_1` (`VOUCHER_EXPIRED`) | Voucher đã hết hạn |
| 400 | `VOUCHER_400_2` (`VOUCHER_NOT_STARTED`) | Chưa tới `startTime` |
| 400 | `VOUCHER_400_3` (`VOUCHER_OUT_OF_STOCK`) | Hết lượt (`usedQuantity >= totalQuantity`) |
| 400 | `VOUCHER_400_4` (`VOUCHER_USER_LIMIT_EXCEEDED`) | User đã dùng đủ `userUsageLimit` |
| 400 | `VOUCHER_400_6` (`VOUCHER_INACTIVE`) | Voucher đang tạm khóa |
| 401 | `COMMON_401` | Thiếu token |
| 404 | `VOUCHER_404` (`VOUCHER_NOT_FOUND`) | Không tìm thấy voucher theo `code` |
| 409 | `VOUCHER_409_SAVED` (`VOUCHER_ALREADY_SAVED`) | Mã đã có trong ví của user này rồi |

### 22.5 `POST /api/v1/vouchers/apply` — Preview áp dụng voucher

**Request Body** (`ApplyVoucherRequest` — `application/json`):

| Field | Type | Required | Validation | Ghi chú |
|---|---|---|---|---|
| `code` | `string` | **Có** | `@NotBlank` | Mã voucher |
| `storeId` | `Long` | Không | — | ID store của đơn hàng (bắt buộc nếu áp mã shop; bỏ trống nếu áp mã platform) |
| `subtotalAmount` | `BigDecimal` | **Có** | `@NotNull`, `@Positive` | Tổng tiền hàng (chưa gồm phí ship, chưa giảm) |

**Response `200 OK`** — `VoucherCalculationResponse`:

```json
{
  "success": true,
  "code": 200,
  "message": "Áp dụng mã giảm giá thành công",
  "data": {
    "voucherId": 1,
    "code": "PLATFORM10",
    "discountAmount": 50.00,
    "finalAmount": 450.00
  }
}
```

> Tính toán:
> - `FIXED_AMOUNT`: `discount = discountValue`
> - `PERCENT`: `discount = subtotal * discountValue / 100`, sau đó cap bởi `maxDiscountAmount` (nếu có)
> - `finalAmount = max(0, subtotal - discount)` (làm tròn HALF_UP, 2 chữ số thập phân)

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu `code` / `subtotalAmount` |
| 400 | `VOUCHER_400_1` (`VOUCHER_EXPIRED`) | Hết hạn |
| 400 | `VOUCHER_400_2` (`VOUCHER_NOT_STARTED`) | Chưa bắt đầu |
| 400 | `VOUCHER_400_3` (`VOUCHER_OUT_OF_STOCK`) | Hết lượt |
| 400 | `VOUCHER_400_4` (`VOUCHER_USER_LIMIT_EXCEEDED`) | User dùng đủ lần |
| 400 | `VOUCHER_400_5` (`VOUCHER_MIN_AMOUNT_NOT_MET`) | `subtotalAmount < minOrderAmount` |
| 400 | `VOUCHER_400_6` (`VOUCHER_INACTIVE`) | Tạm khóa |
| 403 | `VOUCHER_403` (`VOUCHER_STORE_MISMATCH`) | Mã shop nhưng `storeId` đơn hàng khác store của voucher |
| 404 | `VOUCHER_404` (`VOUCHER_NOT_FOUND`) | Không tìm thấy mã |

### 22.6 Postman Test Cases — `08_Voucher_Buyer`

| # | Test | Endpoint | Setup | Expect |
|---|---|---|---|---|
| 1 | List voucher platform | `GET /vouchers/platform` | Public | `200` + `data[]` (mỗi item có `storeId = null`) |
| 2 | List voucher của 1 shop | `GET /vouchers/store/{storeId}` | `storeId` tồn tại | `200` + `data[].storeId = storeId` |
| 3 | List voucher shop không tồn tại | `GET /vouchers/store/999999` | — | `200` + `data = []` (BE không check storeId có tồn tại) |
| 4 | List kho voucher của tôi | `GET /vouchers/mine` | Token BUYER đã lưu vài voucher | `200` + `data[]` |
| 5 | Lưu voucher hợp lệ | `POST /vouchers/mine` | Body `{code: "PLATFORM10"}` (ACTIVE) | `200` + `data.code = "PLATFORM10"`, `scope = "PLATFORM"` |
| 6 | Lưu voucher không tồn tại | `POST /vouchers/mine` | Body `{code: "FAKECODE"}` | `404` + `code = VOUCHER_404` |
| 7 | Lưu voucher đã hết hạn | `POST /vouchers/mine` | `code` của voucher quá `endTime` | `400` + `code = VOUCHER_400_1` |
| 8 | Lưu trùng voucher | `POST /vouchers/mine` | `code` đã có trong ví | `409` + `code = VOUCHER_409_SAVED` |
| 9 | Apply mã percent hợp lệ | `POST /vouchers/apply` | Body `{code, storeId=null, subtotalAmount: 500000}` | `200` + `data.discountAmount = 50000` (10% cap 50K) |
| 10 | Apply mã fixed | `POST /vouchers/apply` | Body `{code, subtotalAmount}` | `200` + `data.discountAmount = discountValue` |
| 11 | Apply mã shop sai store | `POST /vouchers/apply` | `code` là voucher shop, `storeId` ≠ store voucher | `403` + `code = VOUCHER_403` |
| 12 | Apply không đủ minOrderAmount | `POST /vouchers/apply` | `subtotalAmount < minOrderAmount` | `400` + `code = VOUCHER_400_5` |
| 13 | Apply thiếu code | `POST /vouchers/apply` | Body `{}` | `400` + `code = VALIDATION_ERROR` |
| 14 | Apply với subtotalAmount âm | `POST /vouchers/apply` | `subtotalAmount: -1` | `400` + `code = VALIDATION_ERROR` |

---


## 23. Seller Voucher Module `/api/v1/seller/vouchers`

> Quản lý voucher cho gian hàng của seller. **BẮT BUỘC** `@PreAuthorize("hasRole('SELLER')")`.  
> **Tag Swagger**: `Seller Vouchers`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/seller/vouchers` | **Role `SELLER`** | Tạo voucher mới cho gian hàng mình sở hữu |
| `GET` | `/api/v1/seller/vouchers` | **Role `SELLER`** | Danh sách voucher do gian hàng mình phát hành |

### 23.1 `POST /api/v1/seller/vouchers` — Tạo voucher cho shop

**Auth**: **BẮT BUỘC** `Authorization: Bearer <access_token>` + **Role `SELLER`**.

**Request Body** (`CreateVoucherRequest` — `application/json`):

| Field | Type | Required | Validation | Ghi chú |
|---|---|---|---|---|
| `code` | `string` | **Có** | `@NotBlank`, `@Size(max=30)` | BE tự `trim().toUpperCase()`; phải unique trong toàn bảng `vouchers` |
| `storeId` | `Long` | Không | — | Nếu gửi → BE check seller phải sở hữu store này. **Khuyến nghị bỏ trống** (BE tự lấy store của seller qua `findByUserId`) |
| `discountType` | `string` | **Có** | `@NotBlank` | `FIXED_AMOUNT` hoặc `PERCENT` (case-insensitive, BE tự uppercase) |
| `discountValue` | `BigDecimal` | **Có** | `@NotNull`, `@Positive` | Nếu `PERCENT` → phải ≤ 100 |
| `minOrderAmount` | `BigDecimal` | Không | `@PositiveOrZero`, default `0` | Đơn hàng tối thiểu để áp mã |
| `maxDiscountAmount` | `BigDecimal` | Không | `@Positive` | Cap giảm tối đa (chỉ áp dụng cho `PERCENT`) |
| `totalQuantity` | `Integer` | **Có** | `@NotNull`, `@Positive` | Tổng số lượng phát hành |
| `userUsageLimit` | `Integer` | Không | `@Positive`, default `1` | Mỗi user được dùng tối đa mấy lần |
| `startTime` | `Instant` | **Có** | `@NotNull` (ISO-8601) | Bắt đầu hiệu lực |
| `endTime` | `Instant` | **Có** | `@NotNull`, `@Future` | Kết thúc (phải ở tương lai + sau `startTime`) |

**HTTP Status**: `201 Created`

**Response** — `VoucherResponse` (giống mục 22.1).

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Field required bị thiếu, sai validation, `endTime <= startTime` |
| 400 | `VOUCHER_400_7` (`INVALID_VOUCHER_DATES`) | `endTime <= startTime` |
| 400 | `VOUCHER_400_8` (`INVALID_DISCOUNT_PERCENT`) | `discountType=PERCENT` nhưng `discountValue > 100` |
| 401 | `COMMON_401` | Thiếu token |
| 403 | `AUTH_403_FORBIDDEN_ROLE` | Token không có role SELLER |
| 403 | `STORE_403_FORBIDDEN` (`STORE_ACCESS_DENIED`) | Gửi `storeId` không thuộc về seller này |
| 404 | `STORE_404_NOT_FOUND` | `storeId` không tồn tại |
| 409 | `VOUCHER_409` (`VOUCHER_CODE_EXISTS`) | Mã code đã tồn tại |

### 23.2 `GET /api/v1/seller/vouchers` — Voucher do shop tôi phát hành

**Auth**: **BẮT BUỘC** `Authorization: Bearer <access_token>` + **Role `SELLER`**.

**Response `200 OK`** — `List<VoucherResponse>` (lọc theo store của seller hiện tại).

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 401 | `COMMON_401` | Thiếu token |
| 403 | `AUTH_403_FORBIDDEN_ROLE` | Không phải SELLER |
| 404 | `STORE_404_NOT_FOUND` | Seller chưa tạo store |

### 23.3 Postman Test Cases — `14_Voucher_Seller`

| # | Test | Endpoint | Setup | Expect |
|---|---|---|---|---|
| 1 | Tạo voucher shop thành công | `POST /seller/vouchers` | Token SELLER, body đầy đủ hợp lệ, `code` unique, `endTime` tương lai | `201` + `data.code` uppercase, `data.storeId = id shop của seller` |
| 2 | Tạo với code đã tồn tại | `POST /seller/vouchers` | `code` đã có trong bảng | `409` + `code = VOUCHER_409` |
| 3 | Tạo với `endTime <= startTime` | `POST /seller/vouchers` | `endTime = startTime` hoặc trước | `400` + `code = VOUCHER_400_7` |
| 4 | Tạo với `discountValue=150%` | `POST /seller/vouchers` | `discountType=PERCENT`, `discountValue=150` | `400` + `code = VOUCHER_400_8` |
| 5 | Tạo với `endTime` trong quá khứ | `POST /seller/vouchers` | `endTime` = hôm qua | `400` + `code = VALIDATION_ERROR` (`@Future`) |
| 6 | Tạo thiếu `discountType` | `POST /seller/vouchers` | Body không có `discountType` | `400` + `code = VALIDATION_ERROR` |
| 7 | Tạo với `storeId` của seller khác | `POST /seller/vouchers` | `storeId` không thuộc seller hiện tại | `403` + `code = STORE_403_FORBIDDEN` |
| 8 | Tạo bằng token BUYER | `POST /seller/vouchers` | Token BUYER | `403` + `code = AUTH_403_FORBIDDEN_ROLE` |
| 9 | List voucher shop tôi | `GET /seller/vouchers` | Token SELLER có store | `200` + `data[].storeId = store của seller` |
| 10 | List bằng token BUYER | `GET /seller/vouchers` | Token BUYER | `403` + `code = AUTH_403_FORBIDDEN_ROLE` |

---


## 24. Admin Voucher Module `/api/v1/admin/vouchers`

> Quản lý voucher toàn sàn (Platform Voucher, `storeId = null`). **BẮT BUỘC** `@PreAuthorize("hasRole('ADMIN')")`.  
> **Tag Swagger**: `Admin Vouchers`

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/admin/vouchers` | **Role `ADMIN`** | Tạo platform voucher (`storeId` bị bỏ qua, ép về `null`) |

### 24.1 `POST /api/v1/admin/vouchers` — Tạo platform voucher

**Auth**: **BẮT BUỘC** `Authorization: Bearer <access_token>` + **Role `ADMIN`**.

**Request Body** (`CreateVoucherRequest` — `application/json`):  
**Cấu trúc giống mục 23.1** nhưng `storeId` bị **ép về `null`** (BE gọi `request.setStoreId(null)` trước khi tạo).

| Field | Type | Required | Validation |
|---|---|---|---|
| `code` | `string` | **Có** | `@NotBlank`, `@Size(max=30)`, uppercase |
| `storeId` | `Long` | — | **BỎ QUA** — BE ép `null` |
| `discountType` | `string` | **Có** | `FIXED_AMOUNT` hoặc `PERCENT` |
| `discountValue` | `BigDecimal` | **Có** | `@NotNull`, `@Positive` (nếu `PERCENT` → ≤ 100) |
| `minOrderAmount` | `BigDecimal` | Không | `@PositiveOrZero` |
| `maxDiscountAmount` | `BigDecimal` | Không | `@Positive` |
| `totalQuantity` | `Integer` | **Có** | `@NotNull`, `@Positive` |
| `userUsageLimit` | `Integer` | Không | `@Positive`, default `1` |
| `startTime` | `Instant` | **Có** | `@NotNull` |
| `endTime` | `Instant` | **Có** | `@NotNull`, `@Future` |

**HTTP Status**: `201 Created`

**Response** — `VoucherResponse` (với `storeId = null`).

**Lỗi**:

| HTTP | Code | Message |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu field, sai validation |
| 400 | `VOUCHER_400_7` (`INVALID_VOUCHER_DATES`) | `endTime <= startTime` |
| 400 | `VOUCHER_400_8` (`INVALID_DISCOUNT_PERCENT`) | `discountType=PERCENT` nhưng `discountValue > 100` |
| 401 | `COMMON_401` | Thiếu token |
| 403 | `AUTH_403_FORBIDDEN_ROLE` | Không phải ADMIN |
| 409 | `VOUCHER_409` (`VOUCHER_CODE_EXISTS`) | `code` đã tồn tại |

### 24.2 Postman Test Cases — `18_Admin_Voucher`

| # | Test | Endpoint | Setup | Expect |
|---|---|---|---|---|
| 1 | Tạo platform voucher thành công | `POST /admin/vouchers` | Token ADMIN, body hợp lệ, `code` unique | `201` + `data.storeId = null` |
| 2 | Tạo với `storeId` được gửi (bị bỏ) | `POST /admin/vouchers` | Body có `storeId = 5` | `201` + `data.storeId = null` (BE ép null) |
| 3 | Tạo với `code` đã tồn tại | `POST /admin/vouchers` | `code` đã tồn tại (cả shop hay platform) | `409` + `code = VOUCHER_409` |
| 4 | Tạo với `endTime <= startTime` | `POST /admin/vouchers` | `endTime = startTime - 1h` | `400` + `code = VOUCHER_400_7` |
| 5 | Tạo với `discountType=PERCENT, value=200` | `POST /admin/vouchers` | `value > 100` | `400` + `code = VOUCHER_400_8` |
| 6 | Tạo bằng token BUYER | `POST /admin/vouchers` | Token BUYER | `403` + `code = AUTH_403_FORBIDDEN_ROLE` |
| 7 | Tạo thiếu `code` | `POST /admin/vouchers` | Body không có `code` | `400` + `code = VALIDATION_ERROR` |

---


## 24b. Wallet Module (Seller Draft) `/api/v1/wallets` & `/api/v1/seller/wallets`

> **Trạng thái: DRAFT (chưa có Controller/Service/DTO/ErrorCode)**  
> Hiện chỉ có 2 entity `Wallet` + `WalletTransaction` và 2 repository. Tài liệu này mô tả **thiết kế dự kiến** dựa trên entity + các rule nghiệp vụ trong `.cursor/rules/36-wallet.mdc`.

### 24b.1 Tổng quan & Thiết kế

| Mục | Chi tiết |
|---|---|
| Mục đích | Lưu số dư + lịch sử biến động số dư cho SELLER (sau này có thể mở rộng cho BUYER) |
| Entity chính | `Wallet` (1-1 với `Store`), `WalletTransaction` (ledger) |
| Endpoint dự kiến | `GET /api/v1/seller/wallets/me` (balance), `GET /api/v1/seller/wallets/me/transactions` (lịch sử) |
| Role truy cập | `ROLE_SELLER` (chỉ seller của store đó xem được ví của mình) |
| Đơn vị tiền | `BigDecimal` VND (lưu DB); FE format qua `formatVND` |
| Trạng thái ví | `ACTIVE`, `FROZEN` (admin đóng băng), `CLOSED` |

### 24b.2 Entity `Wallet` (tham khảo)

| Field | Type | Null | Mô tả |
|---|---|---|---|
| `id` | `BIGINT PK AUTO_INCREMENT` | NO | Khóa chính |
| `store_id` | `BIGINT FK -> stores.id UNIQUE` | NO | 1 store chỉ có 1 ví |
| `balance` | `DECIMAL(18,2) NOT NULL DEFAULT 0` | NO | Số dư hiện tại (VND) |
| `currency` | `VARCHAR(8) NOT NULL DEFAULT 'VND'` | NO | Đơn vị tiền (cố định VND) |
| `status` | `ENUM('ACTIVE','FROZEN','CLOSED') NOT NULL DEFAULT 'ACTIVE'` | NO | Trạng thái ví |
| `version` | `BIGINT` (Optimistic Lock `@Version`) | NO | Chống race condition khi cộng/trừ |
| `created_at` | `DATETIME NOT NULL` | NO | Ngày tạo |
| `updated_at` | `DATETIME NOT NULL` | NO | Ngày cập nhật |

### 24b.3 Entity `WalletTransaction` (ledger – chỉ append)

| Field | Type | Null | Mô tả |
|---|---|---|---|
| `id` | `BIGINT PK AUTO_INCREMENT` | NO | Khóa chính |
| `wallet_id` | `BIGINT FK -> wallets.id` | NO | Ví bị ảnh hưởng |
| `type` | `ENUM('CREDIT','DEBIT','HOLD','RELEASE','REFUND','WITHDRAW')` | NO | Loại biến động |
| `amount` | `DECIMAL(18,2) NOT NULL` | NO | Số tiền (luôn dương) |
| `balance_after` | `DECIMAL(18,2) NOT NULL` | NO | Số dư sau giao dịch (snapshot) |
| `reference_type` | `VARCHAR(32)` (vd: `ORDER`, `PAYOUT`, `ADJUSTMENT`) | YES | Loại tham chiếu |
| `reference_id` | `VARCHAR(64)` | YES | ID tham chiếu (vd: orderId) |
| `note` | `VARCHAR(255)` | YES | Ghi chú nội bộ |
| `created_at` | `DATETIME NOT NULL` | NO | Thời điểm ghi |
| `created_by` | `BIGINT FK -> users.id` | NO | Người/actor tạo giao dịch |

> **Quy tắc cứng**: bảng `wallet_transactions` chỉ INSERT, không UPDATE/DELETE. Cân bằng kế toán: `SUM(amount WHERE type='CREDIT') - SUM(amount WHERE type='DEBIT') == balance` tại mọi thời điểm.

### 24b.4 Endpoint dự kiến

#### 24b.4.1 `GET /api/v1/seller/wallets/me` — Lấy thông tin ví của seller đang đăng nhập

| Mục | Giá trị |
|---|---|
| Method | `GET` |
| Path | `/api/v1/seller/wallets/me` |
| Auth | **BẮT BUỘC** `Authorization: Bearer <access_token>` |
| Role | `ROLE_SELLER` |
| Request body | Không có |
| Request query | Không có |
| Response `200 OK` (ví dụ) | `{"code":"SUCCESS","message":"OK","data":{"id":1,"storeId":12,"balance":"1500000.00","currency":"VND","status":"ACTIVE","createdAt":"2026-10-16T10:00:00Z","updatedAt":"2026-10-16T12:30:00Z"}}` |
| Lỗi dự kiến | `401` (`AUTH_401_INVALID_TOKEN`), `403` (`AUTH_403_FORBIDDEN_ROLE`), `404` (`WALLET_404_NOT_FOUND` nếu seller chưa được cấp ví) |

#### 24b.4.2 `GET /api/v1/seller/wallets/me/transactions` — Lịch sử biến động số dư

| Mục | Giá trị |
|---|---|
| Method | `GET` |
| Path | `/api/v1/seller/wallets/me/transactions` |
| Auth | **BẮT BUỘC** `Authorization: Bearer <access_token>` |
| Role | `ROLE_SELLER` |
| Request query | `type` (optional, enum), `fromDate` (ISO, optional), `toDate` (ISO, optional), `page` (default 0), `size` (default 20, max 100), `sort` (default `createdAt,desc`) |
| Response `200 OK` | `{"code":"SUCCESS","message":"OK","data":{"content":[{"id":501,"type":"CREDIT","amount":"250000.00","balanceAfter":"1500000.00","referenceType":"ORDER","referenceId":"ORD-20261016-0001","note":"Thanh toán đơn hàng","createdAt":"2026-10-16T12:30:00Z","createdBy":7}], "page":0,"size":20,"totalElements":1,"totalPages":1}}` |
| Lỗi dự kiến | `400` (`VALIDATION_ERROR`), `401`, `403` |

### 24b.5 Quy tắc nghiệp vụ (dự kiến áp dụng khi implement)

- Ví `FROZEN` → endpoint trả về ví nhưng **từ chối** mọi giao dịch ghi (CREDIT/DEBIT) — tạm thời chỉ cho xem.
- Ví `CLOSED` → không truy cập được (coi như không tồn tại, trả 404).
- `WITHDRAW` (rút tiền) chỉ áp dụng khi `status='ACTIVE'` và `balance >= amount`.
- Mọi thay đổi `balance` **BẮT BUỘC** tạo `WalletTransaction` tương ứng trong cùng transaction DB.
- Dùng `@Version` (Optimistic Lock) cho `Wallet` để chống race condition khi 2 luồng cùng cộng/trừ.
- Cân bằng kế toán được verify bằng batch job định kỳ (xem `.cursor/rules/36-wallet.mdc` mục 5).

### 24b.6 Test cases dự kiến cho Postman

| # | Tình huống | Endpoint | Kỳ vọng |
|---|---|---|---|
| 1 | Seller đã có ví → xem ví của mình | `GET /api/v1/seller/wallets/me` | `200` + `data.balance` là chuỗi số thập phân |
| 2 | Buyer gọi endpoint seller | `GET /api/v1/seller/wallets/me` (token BUYER) | `403 AUTH_403_FORBIDDEN_ROLE` |
| 3 | Không có token | `GET /api/v1/seller/wallets/me` | `401 AUTH_401_MISSING_TOKEN` |
| 4 | Seller chưa được cấp ví | `GET /api/v1/seller/wallets/me` (seller mới) | `404 WALLET_404_NOT_FOUND` |
| 5 | Xem lịch sử giao dịch | `GET /api/v1/seller/wallets/me/transactions` | `200` + `data.content[]` có field `type`, `amount`, `balanceAfter`, `createdAt` |
| 6 | Phân trang lịch sử | `?page=0&size=10&sort=createdAt,desc` | `200` + `data.page=0`, `data.size=10` |
| 7 | Lọc theo loại giao dịch | `?type=CREDIT` | `200` + chỉ trả giao dịch `CREDIT` |

> **Lưu ý**: Khi chưa implement controller, Postman sẽ trả `404 NOT_FOUND` cho các URL trên. Hãy đánh dấu request là "skipped / pending implementation".

---
## 25. WebSocket Realtime

### 25.1 Kết nối

**Endpoint:** `ws://host/ws` (SockJS fallback: `http://host/ws/...`)

**Browser → Server**: dùng query param vì header `Authorization` không set được khi upgrade WS:

```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);
stompClient.connect(
  { token: accessToken },  // STOMP CONNECT headers
  () => { /* connected */ },
  (err) => { /* error */ }
);
```

Server sẽ đọc `token` từ query param `?token=` ở handshake, validate và set Principal.

### 25.2 Destinations (Single source of truth)

Build qua `WsDestinations` Java class – **không hard-code URL rải rác**.

#### Public topic (broadcast)

| Subscribe URL | Mô tả |
|---|---|
| `/topic/flash-sale/item/{itemId}/stock` | Stock realtime cho 1 SKU |
| `/topic/flash-sale/slot/{slotId}/stock-update` | Stock thay đổi trong slot |
| `/topic/flash-sale/slot/{slotId}/status` | Slot chuyển trạng thái |

#### Private queue (per-user, Spring prefix `/user/{username}`)

| Subscribe URL | Mô tả |
|---|---|
| `/user/queue/flash-sale/reservation-result` | Kết quả reservation riêng user |
| `/user/queue/flash-sale/orders/{orderCode}/updates` | Update riêng cho 1 đơn |

### 25.3 Event Payload

Mọi event được wrap trong `ApiResponse<T>` chung của project. Body là `FlashSaleWsEvent`:

```typescript
interface FlashSaleWsEvent {
  eventType:
    | 'STOCK_DECREMENTED'
    | 'STOCK_RESTORED'
    | 'STOCK_RETURNED_UNSOLD'
    | 'SLOT_ACTIVATED'
    | 'SLOT_CLOSED'
    | 'ORDER_RESERVED'
    | 'ORDER_CANCELLED_TIMEOUT';

  // Context
  slotId?: number;
  flashSaleItemId?: number;
  orderId?: number;
  userId?: number;

  // Payload (tuỳ event)
  availableStock?: number;
  allocatedStock?: number;
  totalAmount?: number;
  quantity?: number;
  orderCode?: string;
  expiresAt?: string;          // ISO-8601
  slotStatus?: string;          // 'UPCOMING' | 'ACTIVE' | 'ENDED'
  restoredQuantity?: number;

  occurredAt: string;          // ISO-8601
}
```

### 25.4 Ví dụ Subscribe (SockJS + StompJS)

```javascript
// Public: subscribe stock realtime cho 1 SKU
stompClient.subscribe(
  '/topic/flash-sale/item/7/stock',
  (message) => {
    const event = JSON.parse(message.body);
    console.log('Stock giảm còn:', event.data.availableStock);
  }
);

// Private: nhận kết quả reservation
stompClient.subscribe(
  '/user/queue/flash-sale/reservation-result',
  (message) => {
    const event = JSON.parse(message.body);
    if (event.data.eventType === 'ORDER_RESERVED') {
      router.push(`/orders/${event.data.orderCode}`);
    }
  }
);

// Private: update cho 1 đơn cụ thể
stompClient.subscribe(
  `/user/queue/flash-sale/orders/${orderCode}/updates`,
  (message) => {
    const event = JSON.parse(message.body);
    if (event.data.eventType === 'ORDER_CANCELLED_TIMEOUT') {
      toast.error('Đơn đã hết hạn giữ chỗ');
    }
  }
);
```

### 25.5 Quy Tắc Quan Trọng Cho Frontend

1. **WS chỉ là thông báo** – không dựa vào WS để quyết định logic, luôn reload qua REST khi cần.
2. **Stock count lấy từ REST** (`GET /api/v1/flash-sales/slots`) là source-of-truth.
3. **Nếu WS bị miss (mạng lỗi, reconnect)**, REST vẫn trả đúng stock – không cần đồng bộ lại.
4. **Không cần queue offline** – `SimpMessagingTemplate` không queue khi user offline.
5. **Heartbeat 10s cả 2 chiều**, dedicated scheduler pool.

---
## 26. Bảng Mã Lỗi Tổng Hợp

### 26.1 Common Error Codes

| HTTP | Code | Message | Nguyên nhân |
|---|---|---|---|
| 400 | `SYS_400` | Dữ liệu yêu cầu không hợp lệ | Body sai format |
| 401 | `AUTH_401` | Chưa xác thực hoặc token không hợp lệ | Thiếu/sai token |
| 403 | `AUTH_403` | Không có quyền truy cập tài nguyên | Không đủ role |
| 404 | `SYS_404` | Không tìm thấy tài nguyên yêu cầu | URL sai |
| 405 | `SYS_405` | Phương thức HTTP không được hỗ trợ | GET thay vì POST |
| 409 | `SYS_409` | Xảy ra xung đột tài nguyên | Trùng unique key |
| 422 | `SYS_422` | Dữ liệu không vượt qua ràng buộc xác thực | Validation fail |
| 500 | `SYS_500` | Lỗi máy chủ nội bộ không xác định | Bug server |

> Lưu ý: GlobalExceptionHandler trả `400 BAD_REQUEST` (không phải 422) cho cả `MethodArgumentNotValidException` và `ConstraintViolationException`. Mã `SYS_422` hiện chỉ dùng cho constant.

### 26.2 Auth Errors

| HTTP | Code | Message |
|---|---|---|
| 401 | `AUTH_401_CREDENTIALS` | Email hoặc mật khẩu không chính xác |
| 401 | `AUTH_401_REFRESH_TOKEN` | Refresh token không hợp lệ hoặc đã hết hạn |
| 401 | `AUTH_401_GOOGLE_TOKEN` | Google ID token không hợp lệ hoặc đã hết hạn |
| 401 | `AUTH_401_GOOGLE_EMAIL_UNVERIFIED` | Email Google chưa được xác minh |
| 403 | `AUTH_403_USER_DISABLED` | Tài khoản của bạn đã bị khóa hoặc tạm ngưng |
| 404 | `AUTH_404_ROLE` | Vai trò yêu cầu không tồn tại trên hệ thống |
| 404 | `AUTH_404_USER` | Tài khoản không tồn tại trên hệ thống |
| 409 | `AUTH_409_EMAIL` | Email này đã được đăng ký trên hệ thống |
| 409 | `AUTH_409_PHONE` | Số điện thoại này đã được sử dụng |
| 502 | `AUTH_502_GOOGLE_EXCHANGE` | Không thể trao đổi authorization code với Google |
| 500 | `AUTH_500_GOOGLE_CONFIG` | Thiếu cấu hình Google OAuth2 trên server |

### 26.3 User Errors

> Module `user` đã được implement (xem Section 7 + 8). Mã lỗi dưới đây khai báo trong `UserErrorCode.java`.

| HTTP | Code | Message | Ngữ cảnh |
|---|---|---|---|
| 400 | `USER_400_OLD_PASSWORD` (`OLD_PASSWORD_INCORRECT`) | Mật khẩu hiện tại không chính xác | `PUT /me/change-password` |
| 400 | `USER_400_DELETE_DEFAULT` (`CANNOT_DELETE_DEFAULT_ADDRESS`) | Không thể xóa địa chỉ mặc định khi còn địa chỉ khác. Vui lòng đặt địa chỉ khác làm mặc định trước khi xóa | `DELETE /addresses/{id}` |
| 403 | `USER_403_ADDRESS_DENIED` (`ADDRESS_ACCESS_DENIED`) | Bạn không có quyền truy cập hoặc chỉnh sửa địa chỉ này | `GET/PUT/DELETE/PATCH /addresses/{id}` |
| 404 | `USER_404_NOT_FOUND` (`USER_NOT_FOUND`) | Không tìm thấy thông tin người dùng | `GET/PUT /me`, khi token hợp lệ nhưng user bị xóa |
| 404 | `USER_404_ADDRESS` (`ADDRESS_NOT_FOUND`) | Không tìm thấy địa chỉ yêu cầu | `GET/PUT/DELETE/PATCH /addresses/{id}` với id không tồn tại |

### 26.4 Store Errors

| HTTP | Code | Message |
|---|---|---|
| 400 | `STORE_400_INVALID_STATUS` | Trạng thái phê duyệt gian hàng không hợp lệ |
| 400 | `STORE_400_NOT_APPROVED` | Gian hàng chưa được phê duyệt hoặc đang bị tạm khóa |
| 403 | `STORE_403_ACCESS_DENIED` | Bạn không có quyền quản lý gian hàng này |
| 403 | `STORE_403_ADDRESS_ACCESS_DENIED` | Địa chỉ kho không thuộc quyền sở hữu của gian hàng này |
| 404 | `STORE_404_NOT_FOUND` | Không tìm thấy thông tin gian hàng |
| 404 | `STORE_404_ADDRESS_NOT_FOUND` | Không tìm thấy địa chỉ kho của gian hàng |
| 409 | `STORE_409_EXISTS` | Bạn đã có gian hàng trên hệ thống, không thể đăng ký thêm |
| 409 | `STORE_409_NAME_EXISTS` | Tên gian hàng này đã được sử dụng |

### 26.5 Product / Category Errors

| HTTP | Code | Message |
|---|---|---|
| 400 | `PROD_400_VARIANTS_REQUIRED` | Sản phẩm phải có ít nhất một biến thể phân loại (SKU) |
| 400 | `PROD_400_INVALID_TIER_VARIATION` | Số lượng biến thể không khớp với cấu hình phân loại tier_variation_configs |
| 400 | `PROD_400_INVALID_PRICE` | Giá bán của biến thể phải lớn hơn 0 |
| 400 | `PROD_400_INVALID_STOCK` | Số lượng tồn kho không được nhỏ hơn 0 |
| 400 | `PROD_400_DUPLICATE_SKU` | Mã SKU không được trùng lặp trong cùng sản phẩm |
| 403 | `PROD_403_ACCESS_DENIED` | Bạn không có quyền quản lý sản phẩm này |
| 403 | `PROD_403_STORE_NOT_ELIGIBLE` | Gian hàng chưa được phê duyệt hoặc không đủ điều kiện đăng bán |
| 404 | `PROD_404_NOT_FOUND` | Không tìm thấy sản phẩm |
| 404 | `PROD_404_VARIANT_NOT_FOUND` | Không tìm thấy biến thể phân loại sản phẩm |
| 409 | `PROD_409_SKU_EXISTS` | Mã SKU này đã tồn tại trong hệ thống |
| 409 | `PROD_409_VARIANT_IN_ACTIVE_FLASH_SALE` | Không thể chỉnh sửa giá hoặc tồn kho của biến thể đang tham gia Flash Sale đang hoạt động |
| 404 | `CAT_404_NOT_FOUND` | Không tìm thấy ngành hàng yêu cầu |
| 409 | `CAT_409_NAME_EXISTS` | Tên ngành hàng đã tồn tại |
| 409 | `CAT_409_SLUG_EXISTS` | Đường dẫn slug của ngành hàng đã tồn tại |

### 26.6 Flash Sale Errors

| HTTP | Code | Message |
|---|---|---|
| 400 | `FS_400_SLOT_TIME_INVALID` | Thời gian bắt đầu phải trước thời gian kết thúc |
| 400 | `FS_400_SLOT_NOT_ACTIVE` | Khung giờ Flash Sale hiện không hoạt động |
| 400 | `FS_400_SLOT_ALREADY_ENDED` | Khung giờ Flash Sale đã kết thúc |
| 400 | `FS_400_ITEM_NOT_PENDING` | Mục Flash Sale không ở trạng thái chờ duyệt |
| 400 | `FS_400_INVALID_PRICE` | Giá Flash Sale phải lớn hơn 0 và nhỏ hơn giá gốc của biến thể |
| 400 | `FS_400_INVALID_ALLOCATED_STOCK` | Số lượng tồn kho phân bổ cho Flash Sale không hợp lệ hoặc vượt quá tồn kho gốc |
| 400 | `FS_400_INVALID_PURCHASE_LIMIT` | Giới hạn mua của người dùng phải lớn hơn 0 |
| 400 | `FS_400_IDEMPOTENCY_KEY_MISSING` | Yêu cầu thiếu Idempotency-Key |
| 403 | `FS_403_STORE_NOT_APPROVED` | Gian hàng chưa được duyệt, không thể tham gia Flash Sale |
| 403 | `FS_403_NOT_STORE_OWNER` | Bạn không có quyền quản lý sản phẩm hoặc gian hàng này |
| 403 | `FS_403_ADDRESS_INVALID` | Địa chỉ giao hàng không hợp lệ hoặc không thuộc về người dùng |
| 404 | `FS_404_SLOT_NOT_FOUND` | Không tìm thấy khung giờ Flash Sale |
| 404 | `FS_404_ITEM_NOT_FOUND` | Không tìm thấy sản phẩm trong Flash Sale |
| 409 | `FS_409_SLOT_OVERLAP` | Khung giờ Flash Sale bị trùng lặp với khung giờ đã tồn tại |
| 409 | `FS_409_ITEM_ALREADY_REGISTERED` | Biến thể sản phẩm này đã được đăng ký trong khung giờ |
| 409 | `FS_409_INSUFFICIENT_BASE_STOCK` | Tồn kho gốc của sản phẩm không đủ để phân bổ cho Flash Sale |
| 409 | `FS_409_OUT_OF_STOCK` | Sản phẩm Flash Sale đã hết hàng tồn kho |
| 409 | `FS_409_PURCHASE_LIMIT_EXCEEDED` | Bạn đã vượt quá giới hạn số lượng mua cho sản phẩm này trong phiên Flash Sale |
| 409 | `FS_409_IDEMPOTENCY_CONFLICT` | Yêu cầu với Idempotency-Key này đang được xử lý, vui lòng không gửi lặp lại |
| 500 | `FS_500_ORDER_FAILED` | Hệ thống bận khi khởi tạo đơn hàng Flash Sale |

### 26.7 Cart Errors

| HTTP | Code | Message |
|---|---|---|
| 400 | `CART_400_1` (`INSUFFICIENT_STOCK`) | Số lượng tồn kho sản phẩm không đủ |
| 400 | `CART_400_2` (`INVALID_QUANTITY`) | Số lượng sản phẩm thêm vào giỏ phải lớn hơn 0 |
| 403 | `CART_403` (`CART_ITEM_ACCESS_DENIED`) | Bạn không có quyền thao tác trên mục giỏ hàng này |
| 404 | `CART_404` (`CART_NOT_FOUND`) | Không tìm thấy giỏ hàng của người dùng |
| 404 | `CART_ITEM_404` (`CART_ITEM_NOT_FOUND`) | Không tìm thấy sản phẩm trong giỏ hàng |

### 26.8 Order Errors

| HTTP | Code | Message |
|---|---|---|
| 400 | `ORDER_400_1` (`EMPTY_ORDER_ITEMS`) | Đơn hàng phải chứa ít nhất một sản phẩm |
| 400 | `ORDER_400_2` (`INSUFFICIENT_VARIANT_STOCK`) | Số lượng tồn kho sản phẩm không đủ để đặt hàng |
| 400 | `ORDER_400_3` (`INVALID_STATUS_TRANSITION`) | Trạng thái chuyển đổi của đơn hàng không hợp lệ |
| 400 | `ORDER_400_4` (`STORE_NOT_APPROVED`) | Gian hàng chưa được duyệt hoặc đang bị khóa, không thể đặt hàng |
| 403 | `ORDER_403` (`ORDER_ACCESS_DENIED`) | Bạn không có quyền truy cập hoặc thao tác trên đơn hàng này |
| 404 | `ORDER_404` (`ORDER_NOT_FOUND`) | Không tìm thấy đơn hàng |

### 26.9 Voucher Errors

> Module `voucher` đã được implement đầy đủ (Buyer + Seller + Admin). Mã lỗi khai báo trong `VoucherErrorCode.java`.

| HTTP | Code | Message | Ngữ cảnh |
|---|---|---|---|
| 400 | `VOUCHER_400_1` (`VOUCHER_EXPIRED`) | Mã giảm giá đã hết hạn sử dụng | `apply`, `save` |
| 400 | `VOUCHER_400_2` (`VOUCHER_NOT_STARTED`) | Mã giảm giá chưa đến thời gian hiệu lực | `apply`, `save` |
| 400 | `VOUCHER_400_3` (`VOUCHER_OUT_OF_STOCK`) | Mã giảm giá đã hết lượt sử dụng | `apply`, `save` |
| 400 | `VOUCHER_400_4` (`VOUCHER_USER_LIMIT_EXCEEDED`) | Bạn đã dùng hết lượt cho phép đối với mã giảm giá này | `apply`, `save` |
| 400 | `VOUCHER_400_5` (`VOUCHER_MIN_AMOUNT_NOT_MET`) | Giá trị đơn hàng chưa đạt mức tối thiểu áp dụng mã | `apply` |
| 400 | `VOUCHER_400_6` (`VOUCHER_INACTIVE`) | Mã giảm giá đang bị tạm khóa | `apply`, `save` |
| 400 | `VOUCHER_400_7` (`INVALID_VOUCHER_DATES`) | Thời gian kết thúc phải sau thời gian bắt đầu | Tạo voucher (Seller/Admin) |
| 400 | `VOUCHER_400_8` (`INVALID_DISCOUNT_PERCENT`) | Tỷ lệ giảm giá theo % không được vượt quá 100% | Tạo voucher `PERCENT` > 100 |
| 403 | `VOUCHER_403` (`VOUCHER_STORE_MISMATCH`) | Mã giảm giá của gian hàng không áp dụng cho đơn hàng gian hàng khác | `apply` mã shop với sai `storeId` |
| 404 | `VOUCHER_404` (`VOUCHER_NOT_FOUND`) | Không tìm thấy mã giảm giá | `apply` / `save` với mã không tồn tại |
| 409 | `VOUCHER_409` (`VOUCHER_CODE_EXISTS`) | Mã giảm giá đã tồn tại trên hệ thống | Tạo voucher trùng `code` |
| 409 | `VOUCHER_409_SAVED` (`VOUCHER_ALREADY_SAVED`) | Mã giảm giá đã có trong kho voucher của bạn | `POST /vouchers/mine` lưu trùng |

### 26.10 Review Errors

> Module `review` chỉ mới implement `GET /api/v1/products/{productId}/reviews` (Subplan 3). Mã lỗi dưới đây đã được khai báo trong `ReviewErrorCode.java` và sẽ được sử dụng khi các endpoint POST/GET-my-reviews/DELETE được implement ở subplan sau (xem `AGENTS.md` mục #24).

| HTTP | Code | Message |
|---|---|---|
| 400 | `REVIEW_400_ORDER_NOT_COMPLETED` | Đơn hàng chưa hoàn tất, không thể đánh giá |
| 403 | `REVIEW_403_ACCESS_DENIED` | Bạn không có quyền thao tác với đánh giá này |
| 403 | `REVIEW_403_FORBIDDEN` | Không thể thực hiện thao tác này |
| 404 | `REVIEW_404_NOT_FOUND` | Không tìm thấy đánh giá |
| 404 | `REVIEW_404_PRODUCT_NOT_FOUND` | Không tìm thấy sản phẩm để xem đánh giá (cũng dùng cho trường hợp product không ACTIVE) |
| 409 | `REVIEW_409_ALREADY_EXISTS` | Bạn đã đánh giá sản phẩm này rồi (1 orderItem chỉ review 1 lần) |

### 26.11 Image Errors

> Module `image` đã được implement (xem Section 28). Mã lỗi dưới đây được khai báo trong `ImageErrorCode.java`.

| HTTP | Code | Message |
|---|---|---|
| 400 | `IMAGE_400_INVALID_INPUT` | Tham số upload không hợp lệ (thiếu file, sai content-type, owner type/id rỗng) |
| 400 | `IMAGE_400_FILE_TOO_LARGE` | Dung lượng file vượt quá giới hạn cho phép |
| 400 | `IMAGE_400_UNSUPPORTED_MEDIA` | Định dạng ảnh không được hỗ trợ (chỉ chấp nhận jpg/jpeg/png/webp) |
| 403 | `IMAGE_403_FORBIDDEN` | Bạn không có quyền thao tác ảnh của owner này |
| 404 | `IMAGE_404_NOT_FOUND` | Không tìm thấy ảnh |
| 409 | `IMAGE_409_UPLOAD_FAILED` | Upload lên Cloudinary thất bại |
| 500 | `IMAGE_500_INTERNAL` | Lỗi nội bộ khi xử lý ảnh |

### 26.12 Wallet Errors (DRAFT)

> Module `wallet` hiện **chưa có Controller/Service/DTO/ErrorCode**. Mã lỗi dưới đây là **dự kiến** dựa trên rule nghiệp vụ trong `.cursor/rules/36-wallet.mdc`. Khi implement cần tạo `WalletErrorCode` tương ứng.

| HTTP | Code | Message | Ngữ cảnh |
|---|---|---|---|
| 400 | `WALLET_400_INVALID_AMOUNT` | Số tiền không hợp lệ (phải > 0) | WITHDRAW / ADJUSTMENT |
| 400 | `WALLET_400_INVALID_TYPE` | Loại giao dịch không hợp lệ | Lệch enum |
| 400 | `WALLET_400_INVALID_DATE_RANGE` | `fromDate` phải trước `toDate` | Filter lịch sử |
| 401 | `WALLET_401_NOT_AUTHENTICATED` | Cần đăng nhập | Mất token |
| 403 | `WALLET_403_FORBIDDEN_ROLE` | Endpoint chỉ dành cho SELLER | Role khác gọi |
| 403 | `WALLET_403_WALLET_FROZEN` | Ví đang bị đóng băng, không thể ghi giao dịch | status=FROZEN |
| 404 | `WALLET_404_NOT_FOUND` | Không tìm thấy ví cho store này | Seller chưa cấp ví |
| 409 | `WALLET_409_INSUFFICIENT_BALANCE` | Số dư không đủ để thực hiện giao dịch | WITHDRAW > balance |
| 409 | `WALLET_409_VERSION_CONFLICT` | Xung đột optimistic lock, vui lòng thử lại | 2 transaction cùng ghi |
| 422 | `WALLET_422_LEDGER_MISMATCH` | Sai lệch kế toán (SUM != balance) | Bù trừ sai |
| 500 | `WALLET_500_INTERNAL` | Lỗi nội bộ khi xử lý ví | Lỗi hệ thống |

---
## 27. Phân Quyền Truy Cập Theo Role

### 27.1 Public Endpoints (không cần đăng nhập)

| Endpoint |
|---|
| `POST /api/v1/auth/register` |
| `POST /api/v1/auth/login` |
| `POST /api/v1/auth/refresh-token` |
| `POST /api/v1/auth/logout` |
| `GET /api/v1/categories` |
| `GET /api/v1/categories/{id}` |
| `GET /api/v1/categories/slug/{slug}` |
| `GET /api/v1/products` |
| `GET /api/v1/products/recommended` |
| `GET /api/v1/products/{id}` |
| `GET /api/v1/stores/{id}` |
| `GET /api/v1/stores/featured` |
| `GET /api/v1/flash-sales/slots` |
| `GET /api/v1/vouchers/platform` |
| `GET /api/v1/vouchers/store/{storeId}` |
| `GET /api/v1/banners/home` |
| `WS /ws/**` (auth xử lý ở interceptor) |
| `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` |

### 27.2 Authenticated (cần JWT hợp lệ, không yêu cầu role cụ thể)

| Endpoint |
|---|
| `/api/v1/users/**` |
| `/api/v1/users/addresses/**` |
| `POST /api/v1/cart/**` |
| `GET /api/v1/cart` |
| `/api/v1/orders/**` |
| `POST /api/v1/flash-sales/reservations` |
| `POST /api/v1/vouchers/apply` (optional auth) |

### 27.3 Role `SELLER`

| Endpoint |
|---|
| `/api/v1/seller/**` |
| `/api/v1/stores/**` (POST/GET/PATCH/DELETE my store + my addresses) |

### 27.4 Role `ADMIN`

| Endpoint |
|---|
| `/api/v1/admin/**` |

### 27.5 Security Filter Chain

- CORS: mặc định enable (`CorsConfig` riêng)
- CSRF: disable (stateless API)
- Session: STATELESS (không lưu session trên server)
- BCrypt password encoder
- JWT filter chạy trước `UsernamePasswordAuthenticationFilter`
- 401 → trả JSON `ApiResponse` format chuẩn
- 403 → trả JSON `ApiResponse` format chuẩn (khác với 401 do Spring Security tự viết)

---

## Ghi Chú Cho Team Frontend

### Khi tích hợp vào UI

1. **Luôn kiểm tra `success`** trước khi đọc `data`. Khi `success=false`, `data` thường là `null`.

2. **Hiển thị `message`** trực tiếp cho user – message đã được Việt hoá tự nhiên, phù hợp toast / alert.

3. **Phân biệt `code` (HTTP) và ErrorCode**:
   - `code` trong body là HTTP status (200, 400, ...).
   - ErrorCode business nằm trong `message` (xem bảng trên để biết ý nghĩa).

4. **Pagination**: dùng `pageNumber`, `pageSize`, `hasNext` để build infinite scroll hoặc "Xem thêm".

5. **Flash Sale Reservation**:
   - Gửi `Idempotency-Key` (UUID v4) cho mỗi lần click "Đặt hàng".
   - Reconnect WS khi mất kết nối, nhưng REST là source of truth.

6. **Cart & Order**:
   - Cart được gom nhóm theo store – mỗi `storeGroups` sẽ thành 1 Order riêng khi checkout.
   - Không gọi `/orders/checkout` với items từ nhiều store mà không truyền `storeOrders` (sẽ fail validation).

7. **Multi-vendor**: KHÔNG có Parent Order – mỗi store là một Order độc lập.

8. **Address ownership**: luôn kiểm tra `address.user_id == currentUser.id` (server đã làm, nhưng frontend nên ẩn action trên address không thuộc user).

9. **Token expiry**: khi nhận 401 với message "token không hợp lệ", gọi `/auth/refresh-token` để lấy access token mới. Nếu refresh cũng fail → redirect về trang login.

10. **Swagger**: truy cập `http://localhost:8080/swagger-ui.html` để test API trực tiếp (khi dev).

11. **CORS**: backend đã cấu hình `CorsConfig` cho phép các origin dev. Kiểm tra `application-*.yaml` để biết danh sách origin được phép.

12. **Tiền tệ (BigDecimal)**: KHÔNG dùng float để tính toán tiền. Dùng thư viện decimal chuyên dụng (vd `decimal.js`, `bignumber.js`) khi cần tính toán phía client.

13. **Optimistic UI cho Stock**: subscribe `/topic/flash-sale/item/{id}/stock` để cập nhật số tồn kho realtime trên UI, nhưng LUÔN tin tưởng giá trị trả về từ REST khi thực hiện thao tác (thêm giỏ, reservation).

---

## 28. Image Management Module (Polymorphic Storage - Cloudinary)

> Module `image` quản lý ảnh cho **mọi entity** (User avatar, Store logo, Product gallery, Variant gallery, Review album) qua cơ chế **polymorphic FK** (`owner_type`, `owner_id`).

### 28.1 Nguyên tắc

- Tất cả ảnh của **5 loại đối tượng** (User avatar, Store logo, Product gallery, Variant gallery, Review album) đều quản lý qua **bảng `images`** với cặp khóa `(owner_type, owner_id)`.
- Ảnh vật lý lưu trên **Cloudinary** (CDN). Bảng lưu `secure_url` (hiển thị) và `public_id` (để xóa).
- 1 user chỉ có 1 avatar ACTIVE; 1 store chỉ có 1 logo ACTIVE; 1 product/variant chỉ có 1 ảnh primary ACTIVE.
- **Xóa ảnh**: `DELETE /api/v1/images/{id}` đặt `status='INACTIVE'` và phát event async để xóa trên Cloudinary.

### 28.2 Endpoints (ImageController)

| Method | Endpoint | Access | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/images/upload` | Owner (Authenticated) | Upload 1 ảnh mới. `multipart/form-data` gồm `file` + query params `ownerType`, `ownerId`, `isPrimary?`, `displayOrder?`. Trả `ImageResponse`. |
| `GET` | `/api/v1/images` | Authenticated | DS ảnh ACTIVE của 1 owner. Query: `ownerType` (bắt buộc) + `ownerId` (bắt buộc). Trả `List<ImageResponse>` sắp xếp theo `displayOrder ASC`. |
| `PATCH` | `/api/v1/images/{id}/primary` | Owner | Đánh dấu ảnh là primary (chỉ áp dụng cho PRODUCT/VARIANT). Tự động bỏ primary của ảnh cũ (partial unique index). |
| `PATCH` | `/api/v1/images/{id}/order` | Owner | Cập nhật `displayOrder` (chỉ áp dụng cho 1-N gallery như REVIEW). Body: `{"displayOrder": <int>}`. |
| `DELETE` | `/api/v1/images/{id}` | Owner | Soft delete (`status='INACTIVE'`). Phát event async để xóa trên Cloudinary. |
| `DELETE` | `/api/v1/images/{id}/force` | Admin | Hard delete (xóa cả record DB + gọi Cloudinary destroy ngay). |

### 28.3 Upload chuyên dụng (endpoint theo owner)

Ngoài endpoint chung `POST /api/v1/images/upload` ở trên, các controller dưới đây expose endpoint upload chuyên biệt — tự resolve `ownerId` từ principal, tự động soft-delete ảnh cũ (1 owner = 1 ảnh ACTIVE).

| Method | Endpoint | Controller | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/users/me/avatar` | `UserController` | Upload avatar. `ownerType=USER`, `ownerId=currentUserId`. |
| `POST` | `/api/v1/stores/me/logo` | `StoreController` | Upload logo. `ownerType=STORE`, `ownerId=myStoreId`. |
| `POST` | `/api/v1/seller/products/{id}/images` | `SellerProductController` | Upload ảnh gallery cho SPU. `ownerType=PRODUCT`, `ownerId={id}`. |
| `POST` | `/api/v1/seller/products/{productId}/variants/{variantId}/images` | `SellerProductController` | Upload ảnh cho 1 SKU. `ownerType=VARIANT`, `ownerId={variantId}`. |

> Endpoint cho Review album (`/api/v1/reviews/{orderItemId}/images`) sẽ được bổ sung khi implement module review (xem `AGENTS.md` mục #24).

### 28.4 Response mẫu (`ImageResponse`)

```json
{
  "success": true,
  "code": 200,
  "message": "Lấy danh sách ảnh thành công",
  "data": [
    {
      "id": 42,
      "ownerType": "PRODUCT",
      "ownerId": 100,
      "url": "https://res.cloudinary.com/demo/image/upload/v1234567890/abc.jpg",
      "publicId": "abc",
      "displayOrder": 1,
      "isPrimary": true,
      "status": "ACTIVE",
      "createdAt": "2026-10-07T09:00:00+07:00"
    }
  ],
  "timestamp": "2026-10-07T09:00:01+07:00"
}
```

### 28.5 Error codes (`ImageErrorCode`)

Mapping theo `image/exception/ImageErrorCode.java`:

| HTTP | Code | Message |
| :---: | :--- | :--- |
| 413 | `IMAGE_413_FILE_TOO_LARGE` | Tệp ảnh vượt quá kích thước cho phép (tối đa 5MB) |
| 400 | `IMAGE_400_INVALID_FORMAT` | Định dạng tệp ảnh không hợp lệ (chỉ chấp nhận JPG/PNG/WebP) |
| 400 | `IMAGE_400_EMPTY_FILE` | Tệp ảnh rỗng, vui lòng chọn tệp khác |
| 400 | `IMAGE_400_INVALID_OWNER_TYPE` | `ownerType` không hợp lệ |
| 400 | `IMAGE_400_PRIMARY_NOT_ALLOWED` | Chỉ `PRODUCT`/`VARIANT` mới hợp lệ với ảnh đại diện chính |
| 400 | `IMAGE_400_ORDER_NOT_ALLOWED` | Chỉ `REVIEW` mới hợp lệ với cập nhật thứ tự hiển thị |
| 403 | `IMAGE_403_NOT_OWNER` | Bạn không có quyền quản lý ảnh thuộc đối tượng này (vd: seller A upload ảnh cho product của seller B) |
| 404 | `IMAGE_404_NOT_FOUND` | Không tìm thấy ảnh |
| 404 | `IMAGE_404_OWNER_NOT_FOUND` | Không tìm thấy đối tượng sở hữu ảnh |
| 409 | `IMAGE_409_PRIMARY_EXISTS` | Đối tượng đã có ảnh đại diện chính ACTIVE khác |
| 502 | `IMAGE_502_CLOUDINARY_FAILED` | Cloudinary API thất bại, sẽ được thử lại bởi tác vụ định kỳ |

> **Lưu ý giới hạn kích thước**: `FILE_TOO_LARGE` trả về HTTP 413 (Payload Too Large) thay vì 400 để phân biệt với validation format. Hiện tại backend đang cấu hình giới hạn 5MB; xem `AGENTS.md` mục #25 (ghi 10MB) để biết inconsistency cần đối chiếu khi đồng bộ config.

---

## 29. Banner Module `/api/v1/banners` (Public) — `/api/v1/admin/banners` (Admin)

> **Public API** · **Tag Swagger**: `Banners` (public) và `Admin Banners` (admin)

### 29.1 Public Endpoints

> **Trạng thái hiện tại**: chỉ `GET /api/v1/banners/home` đã được implement. `GET /api/v1/banners/position/{position}` đang ở dạng placeholder trong docs, chưa có controller thật (sẽ bổ sung khi cần).

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/banners/home` | Public | Lấy danh sách banner cho trang chủ (main slides + side banners) |
| `GET` | `/api/v1/banners/position/{position}` | Public | Lấy banner ACTIVE theo vị trí `position`, sắp xếp theo `displayOrder` *(placeholder, chưa implement)* |

#### 29.1.1 `GET /api/v1/banners/home`

Lấy tất cả banner ACTIVE trong khoảng thời gian hiện tại cho trang chủ. Service lọc theo 3 điều kiện (`BannerRepository.findActiveBanners`):
- `isActive = true`
- `startDate IS NULL OR startDate <= now` (đã bắt đầu)
- `endDate IS NULL OR endDate >= now` (chưa kết thúc)

Kết quả được chia thành 2 nhóm:
- `mainSlides`: các banner `type = HERO` (slide chính đầu trang chủ)
- `sideBanners`: các banner còn lại (`FREESHIP`, `VOUCHER_NEW_SHOP`, `PROMOTION`) — sort theo `position` ASC

**Request:** Không có body, không có query param. Endpoint **public** (không cần `Authorization`).

**Response (`ApiResponse<HomeBannersResponse>`, HTTP `200 OK`):**

```json
{
  "success": true,
  "code": 200,
  "message": "Get banners successfully",
  "data": {
    "mainSlides": [
      {
        "id": 1,
        "type": "HERO",
        "position": 0,
        "imageUrl": "https://res.cloudinary.com/.../hero1.jpg",
        "imageAlt": "Flash Sale 12h Trưa",
        "ctaHref": "/flash-sales/slots",
        "ctaText": "Xem ngay",
        "isActive": true,
        "startDate": "2026-10-01T00:00:00.000Z",
        "endDate": "2026-12-31T23:59:59.000Z",
        "createdAt": "2026-09-25T08:00:00.000Z"
      }
    ],
    "sideBanners": [
      {
        "id": 2,
        "type": "FREESHIP",
        "position": 0,
        "imageUrl": "https://res.cloudinary.com/.../freeship.jpg",
        "imageAlt": "Miễn phí vận chuyển",
        "ctaHref": "/products",
        "ctaText": "Khám phá",
        "isActive": true,
        "startDate": null,
        "endDate": null,
        "createdAt": "2026-09-25T08:00:00.000Z"
      },
      {
        "id": 3,
        "type": "VOUCHER_NEW_SHOP",
        "position": 1,
        "imageUrl": "https://res.cloudinary.com/.../voucher.jpg",
        "imageAlt": "Voucher cho khách mới",
        "ctaHref": "/vouchers",
        "ctaText": "Nhận voucher",
        "isActive": true,
        "startDate": null,
        "endDate": null,
        "createdAt": "2026-09-25T08:00:00.000Z"
      }
    ]
  },
  "timestamp": "2026-10-16T..."
}
```

> **Lưu ý FE**: Các trường `position` / `isActive` / `startDate` / `endDate` / `createdAt` vẫn được trả về đầy đủ. FE có thể bỏ qua nếu không cần — chỉ dùng `id`, `imageUrl`, `ctaHref`, `ctaText` để render carousel.

**`BannerType` enum** (tham chiếu `banner/enums/BannerType.java`):

| Value | Mô tả |
|---|---|
| `HERO` | Slide chính đầu trang chủ |
| `FREESHIP` | Banner quảng bá freeship |
| `VOUCHER_NEW_SHOP` | Banner voucher cho khách hàng mới / shop mới |
| `PROMOTION` | Banner khuyến mãi chung |

> **Note**: Subplan 4 (2026-10-12) đã implement Admin CRUD cho Banner qua `/api/v1/admin/banners`. Banner được quản lý qua API, không còn seed thủ công.

### 29.2 Admin Endpoints (`/api/v1/admin/banners`)

> Yêu cầu: **Authentication** (Bearer JWT) + **Role `ROLE_ADMIN`** (enforce qua `@PreAuthorize("hasRole('ADMIN')")` ở class-level). Mọi request tới `/api/v1/admin/**` mà không có token hoặc không đúng role đều trả `401 AUTH_401` / `403 AUTH_403`.

| Method | Endpoint | Access | HTTP Status | Mô tả | FE cần gửi |
|---|---|---|---|---|---|
| `GET` | `/api/v1/admin/banners` | `ROLE_ADMIN` | `200 OK` | List banner (phân trang, filter theo `type` và `isActive`). Service hiện sort cứng theo `createdAt DESC` (không nhận param `sort`) | `Authorization` + query `type`/`isActive`/`page`/`size` (xem 29.2.1) |
| `POST` | `/api/v1/admin/banners` | `ROLE_ADMIN` | `201 Created` | Tạo banner mới. Service validate `imageUrl` không blank + `startDate < endDate` | `Authorization` + JSON `BannerRequest` (xem 29.2.2) |
| `PUT` | `/api/v1/admin/banners/{id}` | `ROLE_ADMIN` | `200 OK` | Cập nhật toàn bộ field của banner. Field nào null sẽ giữ nguyên giá trị cũ. | `Authorization` + path `id` + JSON `BannerRequest` (xem 29.2.3) |
| `PATCH` | `/api/v1/admin/banners/{id}/status` | `ROLE_ADMIN` | `200 OK` | Đổi `isActive` (kích hoạt / vô hiệu hóa). Khi `isActive=true`, validate `startDate` không trong tương lai + `endDate` không trong quá khứ. | `Authorization` + path `id` + query `?isActive=true\|false` (xem 29.2.4) |
| `DELETE` | `/api/v1/admin/banners/{id}` | `ROLE_ADMIN` | `200 OK` | **Soft delete**: chỉ set `isActive = false`, không xóa row. Banner sẽ tự ẩn khỏi public API | `Authorization` + path `id` |

#### 29.2.1 `GET /api/v1/admin/banners`

List tất cả banner. Service nạp cả trang bằng `findAll(pageable)`, sau đó filter trong Java theo `type` (convert sang `BannerType` enum) và `isActive`. Lưu ý: do filter chạy trong bộ nhớ, `totalElements` trả về tổng số row trong DB trước khi filter (hiện chưa tối ưu).

**Query params:**

| Param | Type | Default | Required | Mô tả |
|---|---|---|---|---|
| `type` | `String` | — | Không | Filter theo `BannerType` enum. Gửi `HERO` / `FREESHIP` / `VOUCHER_NEW_SHOP` / `PROMOTION` (case-insensitive do service gọi `valueOf(type.toUpperCase())`). Gửi sai enum sẽ ném `IllegalArgumentException` → trả `500` |
| `isActive` | `Boolean` | — | Không | Filter theo trạng thái (`true` / `false`) |
| `page` | `Integer` | `0` | Không | Số trang, 0-based |
| `size` | `Integer` | `20` | Không | Số phần tử / trang. `MAX_PAGE_SIZE = 100` theo `AppConstants` |

**Response:** `ApiResponse<PageResponse<BannerListItem>>` với HTTP `200 OK`, message `"Danh sach banner"`. Cấu trúc `PageResponse` xem section 3. Mỗi `BannerListItem` xem 29.4.

**Lỗi:**

| HTTP | ErrorCode | Message | Nguyên nhân |
|---|---|---|---|
| 401 | `AUTH_401` | Chưa xác thực hoặc token không hợp lệ | Thiếu/sai `Authorization` |
| 403 | `AUTH_403` | Không có quyền truy cập tài nguyên | Token không có role `ADMIN` |
| 500 | `SYS_500` | Lỗi máy chủ nội bộ không xác định | `type` không hợp lệ (`IllegalArgumentException`) |

#### 29.2.2 `POST /api/v1/admin/banners`

Tạo banner mới. Service kiểm tra `imageUrl` không blank + `startDate <= endDate` trước khi lưu. Mặc định `isActive = false` nếu không truyền.

**Request Body (`BannerRequest`, validate bằng Jakarta Bean Validation):**

| Field | Type | Required | Constraint (annotation) | Mô tả FE gửi |
|---|---|---|---|---|
| `type` | `BannerType` (enum: `HERO`, `FREESHIP`, `VOUCHER_NEW_SHOP`, `PROMOTION`) | Có | `@NotNull(message = "Loai banner khong duoc de trong")` | Loại banner (ảnh hưởng nhóm `mainSlides` vs `sideBanners` ở API public) |
| `position` | `Integer` | Không | `@PositiveOrZero(message = "Vi tri hien thi phai >= 0")` | Thứ tự hiển thị, mặc định `0` (càng nhỏ hiển thị trước) |
| `imageUrl` | `String` | Có | `@NotBlank` + `@Size(max = 500)` | URL ảnh (Cloudinary hoặc CDN khác) |
| `imageAlt` | `String` | Không | `@Size(max = 255)` | Alt text ảnh (a11y) |
| `ctaHref` | `String` | Không | `@Size(max = 255)` | URL khi user click vào banner |
| `ctaText` | `String` | Không | `@Size(max = 50)` | Text hiển thị trên nút CTA |
| `isActive` | `Boolean` | Không | (không validate) | Mặc định `false` (SCHEDULED). Đổi sang `true` qua `PATCH /status` |
| `startDate` | `ISO DateTime` (Instant) | Không | (không validate) | Thời điểm bắt đầu hiển thị (null = luôn hiển thị khi active) |
| `endDate` | `ISO DateTime` (Instant) | Không | (không validate) | Thời điểm kết thúc (null = không bao giờ hết hạn) |

**Response:** `ApiResponse<BannerListItem>` với HTTP `201 Created`, message `"Tao banner thanh cong"`.

**Lỗi:**

| HTTP | ErrorCode | Message | Nguyên nhân |
|---|---|---|---|
| 400 | `BANNER_400_IMAGE_URL_BLANK` | URL hình ảnh banner không được để trống | `imageUrl` blank/null (service check dư thừa sau Bean Validation) |
| 400 | `BANNER_400_INVALID_DATE_RANGE` | Ngày bắt đầu phải trước ngày kết thúc | `startDate > endDate` |
| 400 | `SYS_422` (validation) | `Loai banner khong duoc de trong` / `Vi tri hien thi phai >= 0` | `type` null hoặc `position < 0` |
| 401 | `AUTH_401` | Chưa xác thực hoặc token không hợp lệ | Thiếu/sai `Authorization` |
| 403 | `AUTH_403` | Không có quyền truy cập tài nguyên | Token không có role `ADMIN` |

#### 29.2.3 `PUT /api/v1/admin/banners/{id}`

Cập nhật banner. Service kiểm tra tồn tại trước. Field nào `null` trong request sẽ giữ nguyên giá trị cũ (vd: không truyền `isActive` → giữ nguyên). **Không thay đổi `createdAt`**.

**Path Variable:** `id` (Long) — ID banner cần cập nhật.

**Request Body (`BannerRequest`):** giống 29.2.2, tất cả field đều optional (vì partial update).

**Response:** `ApiResponse<BannerListItem>` với HTTP `200 OK`, message `"Cap nhat banner thanh cong"`.

**Lỗi:**

| HTTP | ErrorCode | Message | Nguyên nhân |
|---|---|---|---|
| 400 | `BANNER_400_IMAGE_URL_BLANK` | URL hình ảnh banner không được để trống | Nếu gửi `imageUrl` rỗng |
| 400 | `BANNER_400_INVALID_DATE_RANGE` | Ngày bắt đầu phải trước ngày kết thúc | `startDate > endDate` |
| 400 | `SYS_422` (validation) | `Vi tri hien thi phai >= 0` | `position < 0` |
| 404 | `BANNER_404_NOT_FOUND` | Không tìm thấy banner | `id` không tồn tại |
| 401 | `AUTH_401` | Chưa xác thực hoặc token không hợp lệ | Thiếu/sai `Authorization` |
| 403 | `AUTH_403` | Không có quyền truy cập tài nguyên | Token không có role `ADMIN` |

#### 29.2.4 `PATCH /api/v1/admin/banners/{id}/status`

Đổi trạng thái `isActive` của banner.

> **Quan trọng**: Endpoint này dùng **query param** (không phải request body). Cú pháp: `PATCH /api/v1/admin/banners/{id}/status?isActive=true` hoặc `?isActive=false`. Tham chiếu `BannerAdminController.java:75-77` (`@RequestParam Boolean isActive`).

**Path Variable:** `id` (Long) — ID banner cần đổi trạng thái.

**Query Param:** `isActive` (Boolean, bắt buộc — không có default).

**Response:** `ApiResponse<BannerListItem>` với HTTP `200 OK`, message `"Doi trang thai banner thanh cong"`.

**Validation (chỉ chạy khi `isActive = true`):**
- `banner.startDate` không được trong tương lai (banner chưa đến giờ phát sóng).
- `banner.endDate` không được trong quá khứ (banner đã hết hạn không kích hoạt lại được).

**Lỗi:**

| HTTP | ErrorCode | Message | Nguyên nhân |
|---|---|---|---|
| 400 | `SYS_400` | Dữ liệu yêu cầu không hợp lệ | Thiếu query `isActive` (Spring sẽ ném `MissingServletRequestParameterException`) |
| 404 | `BANNER_404_NOT_FOUND` | Không tìm thấy banner | `id` không tồn tại |
| 422 | `BANNER_422_INVALID_STATUS` | Trạng thái banner không hợp lệ cho thao tác này | Kích hoạt nhưng `startDate` tương lai hoặc `endDate` quá khứ |
| 401 | `AUTH_401` | Chưa xác thực hoặc token không hợp lệ | Thiếu/sai `Authorization` |
| 403 | `AUTH_403` | Không có quyền truy cập tài nguyên | Token không có role `ADMIN` |

#### 29.2.5 `DELETE /api/v1/admin/banners/{id}`

Xóa banner theo cơ chế **soft delete**: service tạo bản ghi mới với `isActive = false` rồi `save` (giữ nguyên `id`, `createdAt`, các field khác). Banner sẽ tự ẩn khỏi các public API vì chúng filter theo `isActive = true`.

**Path Variable:** `id` (Long) — ID banner cần xóa.

**Response:** `ApiResponse<Void>` với HTTP `200 OK`, message `"Xoa banner thanh cong"`, `data = null`.

**Lỗi:**

| HTTP | ErrorCode | Message | Nguyên nhân |
|---|---|---|---|
| 404 | `BANNER_404_NOT_FOUND` | Không tìm thấy banner | `id` không tồn tại |
| 401 | `AUTH_401` | Chưa xác thực hoặc token không hợp lệ | Thiếu/sai `Authorization` |
| 403 | `AUTH_403` | Không có quyền truy cập tài nguyên | Token không có role `ADMIN` |

### 29.3 Banner Error Codes (tổng hợp)

Tham chiếu `banner/exception/BannerErrorCode.java`. Có **4 mã lỗi** riêng của module Banner:

| HTTP | Code | Ý nghĩa |
|---|---|---|
| 404 | `BANNER_404_NOT_FOUND` | Banner không tồn tại (theo `id`) |
| 422 | `BANNER_422_INVALID_STATUS` | Trạng thái banner không hợp lệ cho thao tác: phát sinh khi `PATCH /status?isActive=true` nhưng `startDate` tương lai hoặc `endDate` quá khứ |
| 400 | `BANNER_400_INVALID_DATE_RANGE` | `startDate > endDate` (validate khi POST/PUT banner) |
| 400 | `BANNER_400_IMAGE_URL_BLANK` | `imageUrl` null hoặc blank (validate khi POST/PUT banner) |

> **Note (2026-10-16)**: Endpoint `GET /api/v1/banners/position/{position}` được liệt kê trong rule `.cursor/rules/46-endpoints-cart-order-voucher-banner.mdc` (mục "Banner Quảng cáo") nhưng **CHƯA được implement** trong `BannerPublicController.java`. Method `getActiveBannersByPosition(Integer position)` đã có sẵn trong `BannerService` interface nhưng chưa expose qua HTTP. Hiện tại FE chỉ có 1 public endpoint là `GET /api/v1/banners/home`.

### 29.4 Banner DTO Summary

`BannerListItem` (dùng cho list/admin response) và `BannerResponse` (dùng cho public `GET /home` response) có cùng cấu trúc 11 field:

| Field | Type | Mô tả |
|---|---|---|
| `id` | `Long` | ID banner |
| `type` | `BannerType` (enum) | Loại banner (`HERO` / `FREESHIP` / `VOUCHER_NEW_SHOP` / `PROMOTION`) |
| `position` | `Integer` | Thứ tự hiển thị |
| `imageUrl` | `String` | URL ảnh |
| `imageAlt` | `String` | Alt text |
| `ctaHref` | `String` | URL khi click |
| `ctaText` | `String` | Text nút CTA |
| `isActive` | `Boolean` | Trạng thái kích hoạt |
| `startDate` | `Instant` | Thời điểm bắt đầu |
| `endDate` | `Instant` | Thời điểm kết thúc |
| `createdAt` | `Instant` | Thời điểm tạo (do `@PrePersist`) |

**`HomeBannersResponse`** (chỉ dùng ở `GET /api/v1/banners/home`):

| Field | Type | Mô tả |
|---|---|---|
| `mainSlides` | `List<BannerResponse>` | Các banner `type = HERO` (slide chính đầu trang chủ) |
| `sideBanners` | `List<BannerResponse>` | Các banner còn lại (`FREESHIP` / `VOUCHER_NEW_SHOP` / `PROMOTION`) — sort theo `position` ASC |

**`BannerType` enum** (tham chiếu `banner/enums/BannerType.java`):

| Value | Mô tả |
|---|---|
| `HERO` | Slide chính đầu trang chủ → `mainSlides` |
| `FREESHIP` | Banner quảng bá freeship → `sideBanners` |
| `VOUCHER_NEW_SHOP` | Banner voucher cho khách hàng mới / shop mới → `sideBanners` |
| `PROMOTION` | Banner khuyến mãi chung → `sideBanners` |

**`BannerRequest`** (dùng cho POST/PUT `/api/v1/admin/banners`): cấu trúc tương tự `BannerListItem` (11 field, không có `id` và `createdAt`) + thêm validation. Chi tiết ở 29.2.2.

---

**Phiên bản tài liệu**: 1.5 · **Cập nhật lần cuối**: 2026-10-16 · **Nguồn**: Backend `flash-sale-b2c-UTC2`

**Thay đổi trong phiên bản 1.5 (2026-10-16):**
- Mục 6 (Auth Module): bổ sung 2 endpoint mới `GET /api/v1/auth/google/url` (6.5) và `POST /api/v1/auth/google` (6.6) cho Google OAuth2 Authorization Code flow.
- Mục 6.1 (`/register`): thêm validation tiếng Việt đầy đủ + ghi chú về role `BUYER` / `SELLER`. Sửa `expiresIn` từ ms sang giây.
- Mục 6.2 (`/login`): sửa field name từ `email` → `usernameOrEmail` (BE tự nhận diện email/phone theo ký tự `@`).
- Mục 6.3 (`/refresh-token`): bổ sung giải thích Refresh Token Rotation (RT cũ bị thu hồi khi cấp RT mới, lưu Redis whitelist `auth:refresh:{userId}:{jti}`).
- Mục 6.4 (`/logout`): bổ sung giải thích body optional + idempotency.
- Mục 4 (Auth Header): thêm schema JWT claim (`type`, `jti`, `userId`, `authorities`) + giải thích rotation flow. Sửa đơn vị `expiresIn` = giây.
- Mục 5 (Danh Mục API): tăng tổng Auth từ 4 → 6 endpoint.
- Mục 26.2 (Auth Errors): bổ sung 4 mã lỗi Google OAuth2 — `AUTH_401_GOOGLE_TOKEN`, `AUTH_401_GOOGLE_EMAIL_UNVERIFIED`, `AUTH_502_GOOGLE_EXCHANGE`, `AUTH_500_GOOGLE_CONFIG`. Tổng cộng 11 mã Auth.
- Bổ sung schema `AuthResponse` và `UserResponse` ở đầu mục 6.
- Bổ sung bảng "Vai trò (Role) và cách FE chọn" để giải thích `BUYER` / `SELLER` / `ADMIN` và quyền truy cập tương ứng.

---

