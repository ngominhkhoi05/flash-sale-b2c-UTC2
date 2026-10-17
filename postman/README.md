# Postman Collection - Flash Sale B2C

Collection + environment để test toàn bộ REST API của backend `flash-sale-b2c-UTC2`.

## Files

| File | Mô tả |
|---|---|
| `Flash-Sale-B2C.postman_collection.json` | Collection chính — 25 folder, ~150 requests |
| `Flash-Sale-B2C.postman_environment.json` | Environment — 50+ biến (auth tokens, IDs, test data) |
| `extract-paths-v3.js` | Script Node.js — extract toàn bộ API paths từ `@RequestMapping` controllers |
| `verify-final.js` | Script Node.js — verify collection vs code |
| `package.json` / `package-lock.json` | Newman runner lockfile |

## 3 Role thật đã đăng ký trong DB

```text
Buyer:  khoi22092021@gmail.com / password123
Seller: seller1@demo.local     / password123
Admin:  admin@flashsale.local   / password123
```

Đã được seed sẵn trong `data-dev.sql`. Dùng 3 tài khoản này để test các flow Buyer / Seller / Admin mà không cần tự register mới (tiết kiệm thời gian + đảm bảo role mapping đúng).

## Import

1. Mở Postman → Import → chọn 2 file trên.
2. Chọn environment `Flash-Sale-B2C` (góc trên bên phải).
3. (Tùy chọn) Sửa `baseUrl` nếu backend chạy port khác 8080.

## Cấu trúc folder (25 folders)

| # | Folder | Mô tả |
|---|---|---|
| 00 | Auth_Setup | Register / Login (Buyer/Seller/Admin) / Refresh / Logout / Google |
| 01 | Public_Catalog | Categories, products, stores, reviews, banners |
| 02 | Public_FlashSale | List slot Flash Sale + items active |
| 03 | User_Profile | Me, update, change password, avatar, round-trip |
| 04 | Address_Book | CRUD + default address (ownership asserted) |
| 05 | Cart | CRUD + 3 NEG edge cases (no token, qty=0, no variant) |
| 06 | Order_Buyer | Checkout, list, detail, cancel, QR + multi-vendor |
| 07 | Payment | ZaloPay, COD, get payment status |
| 08 | Voucher_Buyer | Platform, store, mine, save, apply, calculate |
| 09 | FlashSale_Reservation | Reserve + idempotency replay + idempotency conflict |
| 10 | Store_Seller | Create store, update, addresses, logo, sidebar |
| 11 | Product_Seller | CRUD product, variants, status, images |
| 11b | Seller_Orders | List store orders, update status |
| 12 | FlashSale_Seller | Register Flash Sale item |
| 13 | Image_Generic | Upload, list, set primary, order, delete, force |
| 14 | Voucher_Seller | Create, list store voucher |
| 15 | Admin_Category | Create, update, delete |
| 16 | Admin_Store | Approve/reject store |
| 17 | Admin_FlashSale | Slot CRUD, approve item, pre-warm Redis |
| 18 | Admin_Voucher | Create platform voucher |
| 19 | Admin_Banner | CRUD + status banner |
| 20 | Negative_Tests | 401/403/404/409/422 |
| 20 | Review_Public | GET product reviews (public, no auth) |
| 21 | Wallet | Placeholder — backend chưa có controller |
| 20b | Negative_Tests_Auth | 400/401/404/405/409/502 cho auth |
| 20c | Negative_Tests_Expanded | Invalid token, expired token, role-boundary, wrong method |

## Luồng test theo role (dùng 3 role thật)

### Buyer (7 bước)

1. Folder `00`: `POST Login (Buyer)` (token tự save vào `accessToken`)
2. Folder `04`: `POST Create Address` (lưu `addressId`)
3. Folder `01`: `GET All Products` (lưu `productId`, `variantId`)
4. Folder `05`: `POST Add to Cart`
5. Folder `08`: `POST Save Voucher` (nếu có) → `POST Apply Voucher` (lưu `voucherCode`)
6. Folder `06`: `POST Checkout` (lưu `orderId`, `orderCode`)
7. Folder `07`: `POST COD` hoặc `POST ZaloPay`

### Seller (5 bước)

1. Folder `00`: `POST Login (Seller)` (lưu `sellerId`)
2. Folder `10`: `POST Register Store` → `POST Create Store Address` (lưu `storeId`)
3. Folder `11`: `POST Create Product` (lưu `productId`, `variantId`)
4. Folder `12`: `POST Register FS Item`
5. Folder `11b`: `GET List Store Orders` → `PATCH Update Order Status`

### Admin (3 bước)

1. Folder `00`: `POST Login (Admin)`
2. Folder `16`: `PATCH Update Store Status` (chấp nhận seller đăng ký store)
3. Folder `17`: `PATCH Approve Item` → `POST Pre-warm Redis`

### Chuyển role trong collection

Mỗi folder `00` Login riêng (Buyer / Seller / Admin) sẽ ghi đè `accessToken`. Để chuyển role → chạy đúng Login tương ứng trước khi chạy các request cần role đó.

## Biến môi trường tự động cập nhật

Script trong test tự động lưu vào env:

| Biến | Lưu từ |
|---|---|
| `accessToken`, `refreshToken` | Login response |
| `userId`, `userRole`, `buyerId` / `sellerId` / `adminId` | Login response |
| `addressId` | Create address response |
| `productId`, `variantId` | Create product response |
| `storeId` | Create store / sidebar response |
| `flashSaleSlotId`, `flashSaleItemId` | Admin create slot / register item |
| `orderId`, `orderCode` | Checkout response |
| `reservationId` | Reserve response |
| `imageId`, `bannerId`, `voucherCode` | Response tương ứng |
| `sellerOrderId`, `sellerOrderCode` | Seller list orders response |
| `idempotencyKey`, `idempotencyKeyReplay`, `idempotencyKeyConflict` | Pre-request script generate UUID |
| `idempotentReplayed` | Set `true` nếu server trả header `Idempotent-Replayed` |

## Biến cần set thủ công (nếu test bước riêng lẻ)

- `baseUrl` — default `http://localhost:8080`
- `testEmail`, `testPassword` — cho buyer test (đã có sẵn `khoi22092021@gmail.com`)
- `sellerEmail`, `sellerPassword` — cho seller test (đã có sẵn `seller1@demo.local`)
- `adminEmail`, `adminPassword` — cho admin test (đã có sẵn `admin@flashsale.local`)
- `nonexistentProductId` — default `999999999`

## Chạy bằng Newman CLI

```bash
# Cài newman (một lần)
npm install -g newman

# Chạy full collection (cần env)
newman run Flash-Sale-B2C.postman_collection.json \
  -e Flash-Sale-B2C.postman_environment.json \
  --reporters cli,html \
  --reporter-html-export report.html

# Chỉ chạy 1 folder
newman run Flash-Sale-B2C.postman_collection.json \
  -e Flash-Sale-B2C.postman_environment.json \
  --folder "00_Auth_Setup"

# Chạy với iteration (test idempotency replay)
newman run Flash-Sale-B2C.postman_collection.json \
  -e Flash-Sale-B2C.postman_environment.json \
  --folder "09_FlashSale_Reservation" \
  --iteration-count 2
```

> Xem `package.json` để biết thêm scripts tiện ích (`npm run newman`, `npm run newman:auth`, ...).

## Verify sau khi sửa backend

```bash
# Extract tất cả API paths từ controllers
node extract-paths-v3.js

# So sánh với collection (báo MISSING / EXTRA)
node verify-final.js
```

## Ghi chú kỹ thuật

- **Path prefix `/api/v1`** đã được hard-code trong tất cả URL — khớp với `@RequestMapping` trong controllers.
- **Idempotency-Key** tự động thêm vào `POST /api/v1/flash-sales/reserve` theo mục #34 AGENTS.md.
- **ApiResponse contract** (rule 38) — mọi response đều có `{ success, code, message, data, timestamp }`:
  - Success → `code === "SUCCESS"`.
  - Error → `code` có prefix theo module (`AUTH_4xx`, `PROD_404`, `FS_409`, `WALLET_xxx`, ...).
  - Folder `20c` (Negative_Tests_Expanded) assert `j.success === false` + `j.code` prefix đúng.
- **Multipart upload** (avatar/logo/image): chọn file trực tiếp trong Postman UI — body để trống.
- **Bodies** đã khớp với DTO trong code. Nếu backend thay đổi field, sửa lại body trong collection.
- **Bearer token** dùng biến `{{accessToken}}`; sau Login nó tự động được lưu.
- **Wallet module** (folder 21) hiện là placeholder — backend chỉ có `Wallet` entity + `WalletTransactionRepository` nhưng chưa có `WalletController`. Sẽ bổ sung khi backend mở rộng.

## Audit history

- **2026-10-17** (`feature/postman-audit-2026-10-17`): 17 module commits — sửa thiếu test scripts, thêm NEG edge cases, chuẩn hóa assertions theo `ApiResponse` contract (rule 38), URL sync với controller thật (không theo rule docs sai).
