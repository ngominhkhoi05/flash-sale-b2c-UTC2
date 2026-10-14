# Backend Gap Report – Frontend Integration Blockers

> **From**: FE team (`flash-sale-b2c-fe`)
> **To**: BE team (`flash-sale-b2c-UTC2`)
> **Date**: 2026-10-13
> **Subject**: API gaps sau khi tích hợp Cart + Product Detail

## TL;DR

Sau khi tích hợp **Cart** (`feat/cart-api-integration`) + **Product Detail** (`feat/product-detail-api-integration`), FE phát hiện **5 schema gap** và **18 endpoint thiếu** chặn các tính năng chính (checkout, payment, social login, search, sort, ...).

**Mức độ**: P0 (blocker) = 5 items · P1 (cản UX) = 9 items · P2 (nice-to-have) = 9 items

---

## 1. Schema Gap – P0 (blocker tích hợp hiện tại)

### 1.1 `ProductDetailResponse` – thiếu 11 field

FE đang phải merge với mock data để render đủ UI. Cần BE bổ sung:

```jsonc
{
  // ── Field MỚI cần thêm ────────────────────────────────────
  "slug": "string",                  // URL SEO (/products/ao-thun-xyz)
  "descriptionRich": "string[]",     // HTML/rich text cho tab mô tả
  "specs": {                        // Bảng thông số kỹ thuật
    "category": "string",
    "material": "string",
    "origin": "string",
    "style": "string",
    "sku": "string",
    "careInstructions": "string"
  },
  "features": [                     // 3 ô lợi ích nổi bật
    {
      "icon": "string",             // Material Symbol name
      "iconBgClass": "string",      // Tailwind bg class
      "iconTextClass": "string",    // Tailwind text class
      "title": "string",
      "description": "string"
    }
  ],
  "commitments": [                  // Cam kết (organic, đổi trả, BH)
    { "icon": "string", "iconClass": "string", "label": "string" }
  ],
  "vouchers": [                     // Voucher của shop
    { "id": "int64", "label": "string" }
  ],
  "storeProfile": {                 // Profile shop (sidebar)
    "id": "int64",
    "name": "string",
    "initials": "string",
    "isOfficial": "boolean",
    "ratingAvg": "double",
    "ratingCount": "int32",
    "productCount": "int32",
    "responseRate": "int32",        // 0-100
    "responseTimeLabel": "string",  // "Vài phút"
    "joinedAt": "date-time",
    "lastActiveLabel": "string"     // "Online 5 phút trước"
  },
  "shipping": {                     // Box vận chuyển
    "freeshipBadge": "string",      // "FREESHIP XTRA"
    "freeshipText": "string",
    "shipTo": "string",
    "estimatedDays": "string"       // "1 - 2 ngày"
  },
  "sizeChart": [                    // Bảng size
    {
      "size": "string",
      "height": "string",
      "weight": "string",
      "width": "string",
      "length": "string",
      "isOutOfStock": "boolean"
    }
  ],
  "ratingDistribution": {           // Phân bố số sao (1-5)
    "star5": "int32",
    "star4": "int32",
    "star3": "int32",
    "star2": "int32",
    "star1": "int32"
  },
  "questionCount": "int32",         // Số câu hỏi Q&A
  "relatedSameStore": [             // SP cùng shop
    "ProductSummaryResponse[]"
  ],
  "relatedBestSellers": [           // SP bán chạy
    "ProductSummaryResponse[]"
  ]
}
```

**Impact**: Hiện tại FE merge 12 field mock mỗi lần render. Khi BE thêm → xoá hết mock.

---

### 1.2 `UserResponse` + `AuthResponse` – thiếu `roles[]`

Cần cho middleware phân quyền (`ROLE_SELLER`, `ROLE_ADMIN`, ...).

```jsonc
// UserResponse
{
  "id": "int64",
  "email": "string",
  "fullName": "string",
  "phone": "string",
  "avatarUrl": "string",
  "status": "string",
  "createdAt": "date-time",
  // ── MỚI ──
  "roles": ["string"]              // ["ROLE_CUSTOMER", "ROLE_SELLER", ...]
}

// AuthResponse
{
  "accessToken": "string",
  "refreshToken": "string",
  "tokenType": "string",
  "expiresIn": "int64",
  "user": "UserResponse"           // ← đã có roles nếu BE thêm ở UserResponse
}
```

**Impact**: Middleware chưa chặn được `/seller/*`, `/admin/*`. Đang hardcode role trong mock.

---

### 1.3 `OrderResponse` – thiếu payment fields

Cần cho trang Checkout + ZaloPay + Order Detail.

```jsonc
{
  "id": "int64",
  "orderCode": "string",
  // ... existing fields ...
  // ── MỚI ──
  "paymentMethod": "string",       // "ZALOPAY" | "COD"
  "paymentStatus": "string",       // "PENDING" | "SUCCESS" | "FAILED" | "EXPIRED" | "REFUNDED"
  "paymentUrl": "string",          // ZaloPay redirect URL
  "qrCodeUrl": "string",           // QR code image URL
  "trackingNumber": "string",
  "shippedAt": "date-time",
  "completedAt": "date-time",
  "timeline": [                    // Status timeline
    {
      "status": "string",
      "label": "string",
      "at": "date-time"
    }
  ]
}
```

---

### 1.4 `ProductReviewResponse` – sai tên field

Hiện tại:
```jsonc
{
  "id": "int64",
  "rating": "int32",
  "comment": "string",             // ← tên này
  "userName": "string",
  "variantSku": "string",
  "variantAttributes": "object",
  "album": ["string"],             // ← field ảnh đang là "album"
  "sellerReply": "string",         // ← chỉ là string, không phải object
  "sellerReplyAt": "date-time",
  "createdAt": "date-time"
}
```

FE cần (để match UI design + filter):
```jsonc
{
  // ... existing ...
  "content": "string",             // ← đổi "comment" → "content"
  "mediaUrls": ["string"],         // ← đổi "album" → "mediaUrls"
  "sellerReply": {                 // ← đổi từ string → object
    "content": "string",
    "storeName": "string",
    "repliedAt": "date-time"
  },
  "userAvatarUrl": "string",       // ← thêm (FE cần cho UI)
  "isVerifiedPurchase": "boolean"  // ← thêm (badge "Đã mua hàng")
}
```

---

### 1.5 `/products/recommended` – response shape không match

Hiện tại:
```jsonc
{
  "forYou": [...],
  "bestSellers": [...],
  "hotDeals": [...]
}
```

Câu hỏi: mỗi `tab` query trả về 1 list, hay tất cả gộp trong 1 response?
- Nếu gộp: OK, FE xử lý 3 list riêng.
- Nếu chỉ 1 list theo `tab`: cần endpoint gộp (vd `/products/recommended/all`).

**Hiện tại FE workaround**: lấy tất cả 3 tab trong 1 query (mặc định `tab=all`).

---

## 2. Endpoint thiếu – P0 (blocker)

| # | Method | Path | Mục đích | Impact |
|---|---|---|---|---|
| 1 | GET | `/api/v1/products?slug={slug}` | Lookup product từ slug | 🔴 URL `/products/[slug]` không hoạt động nếu không có mock |
| 2 | GET | `/api/v1/orders/{id}/payment` | Trạng thái thanh toán | 🔴 Order detail page không biết đã trả tiền chưa |
| 3 | POST | `/api/v1/orders/{id}/payment/zalopay` | Tạo ZaloPay QR | 🔴 Không thanh toán được |
| 4 | POST | `/api/v1/orders/{id}/payment/cod` | Confirm COD | 🔴 Không thanh toán COD được |
| 5 | GET | `/api/v1/flash-sales/items/active` | Flash sale items đang active | 🔴 Homepage không show flash sale section |

---

## 3. Endpoint thiếu – P1 (cản UX, không block)

| # | Method | Path | Mục đích |
|---|---|---|---|
| 6 | GET | `/api/v1/products/{id}/related` | Related products (same store + best seller) |
| 7 | GET | `/api/v1/stores/{id}/profile` | Store profile (nếu không embed) |
| 8 | POST | `/api/v1/auth/social/{google,facebook,github}` | Social login |
| 9 | GET | `/api/v1/cart/count` | Cart count (badge header, tránh fetch full cart) |
| 10 | GET | `/api/v1/orders/{id}/timeline` | Order status timeline |
| 11 | GET | `/api/v1/notifications` | Notification list |
| 12 | POST | `/api/v1/auth/forgot-password` | Quên mật khẩu |
| 13 | POST | `/api/v1/auth/verify-email` | Verify email |
| 14 | GET | `/api/v1/search/suggest` | Search autocomplete |

---

## 4. Endpoint thiếu – P2 (nice-to-have)

| # | Method | Path | Mục đích |
|---|---|---|---|
| 15 | GET | `/api/v1/products/{id}/questions` | Q&A list |
| 16 | POST | `/api/v1/products/{id}/questions` | Đặt câu hỏi |
| 17 | POST | `/api/v1/orders/{id}/return` | Yêu cầu hoàn trả |
| 18 | GET | `/api/v1/users/me/notifications` | User notifications |

---

## 5. Query params thiếu – P1

| Endpoint | Param thiếu | Mục đích |
|---|---|---|
| `GET /products` | `sort` (enum: `price_asc`, `price_desc`, `newest`, `best_selling`) | Sắp xếp sản phẩm |
| `GET /products` | `storeId` (int64) | Lọc sản phẩm theo shop |
| `GET /orders` | `status` (enum) | Lọc đơn theo trạng thái (`PENDING_PAYMENT`, `SHIPPING`, ...) |
| `GET /vouchers/platform` | `userId` (int64) | Voucher user đang có (nếu cần filter theo user) |

---

## 6. WebSocket topics thiếu – P1

| Topic | Mục đích | Format message |
|---|---|---|
| `/topic/flash-sale/{itemId}/stock` | Realtime stock update | `{ "availableStock": int32, "soldStock": int32 }` |
| `/topic/orders/{orderId}/status` | Realtime order status | `{ "status": "string", "at": "date-time" }` |
| `/topic/notifications/{userId}` | Realtime notification | `{ "id": int64, "type": "string", "title": "string", "body": "string" }` |
| `/topic/payments/{orderId}/status` | ZaloPay payment callback | `{ "status": "SUCCESS" \| "FAILED" }` |

---

## 7. FE hiện đang workaround (cần BE confirm trước khi release)

| Workaround | Mức ưu tiên | Action |
|---|---|---|
| Merge mock fields vào `ProductDetailResponse` (slug, specs, features, ...) | P0 | BE thêm fields → xoá mock |
| `useProductSlugLookup` tự derive slug từ `name` (chỉ work với mock product) | P0 | BE thêm `slug` field hoặc endpoint lookup |
| Mock store profile, vouchers, commitments, shipping | P1 | BE embed vào response |
| `useProductDetail` fallback về mock khi API fail | OK | Giữ tạm, xoá khi BE stable |
| `useProductDetail` dùng `mockProductDetail` để merge UI fields | OK | Giữ tạm |

---

## 8. Câu hỏi cần BE confirm

1. **Slug tracking**: BE có plan thêm field `slug` cho product không, hay FE tự generate từ `name`?
2. **Embed vs separate endpoints**: Field mới (specs, features, ...) embed trong ProductDetailResponse hay tách endpoint riêng?
3. **ZaloPay integration**: BE dùng sandbox hay production keys? Webhook URL public?
4. **Social login**: Provider nào ưu tiên (Google, Facebook, GitHub)? OAuth callback URL?
5. **WebSocket auth**: Có cần JWT trong STOMP CONNECT headers không?
6. **Refresh token rotation**: Khi `/auth/refresh-token` được gọi, BE có trả `AuthResponse` mới (access + refresh mới) không?
7. **Roles**: Có bao nhiêu role? Có hierarchy không (admin > seller > customer)?

---

## 9. Lịch sử thay đổi

| Date | Mục đích | Người thực hiện |
|---|---|---|
| 2026-10-13 | Tạo gap report từ cart + product detail integration | FE team |

---

## 10. Phụ lục – Endpoints hiện đang hoạt động (sanity check)

Đã verify các endpoint sau trả về đúng schema (10/2026):

- ✅ `GET /api/v1/cart` → `CartResponse`
- ✅ `POST /api/v1/cart/items` → `CartResponse`
- ✅ `PUT /api/v1/cart/items/{id}` → `CartResponse`
- ✅ `DELETE /api/v1/cart/items/{id}` → 200
- ✅ `DELETE /api/v1/cart` → 200
- ✅ `GET /api/v1/products/{id}` → `ProductDetailResponse`
- ✅ `GET /api/v1/products` → `PageResponse<ProductSummaryResponse>`
- ✅ `GET /api/v1/products/recommended` → `RecommendedProductsResponse`
- ✅ `GET /api/v1/products/{id}/reviews` → `ProductReviewsPageResponse`

**Tổng cộng**: 65 endpoints đã có, 23 endpoints thiếu (5 P0 + 9 P1 + 9 P2).
