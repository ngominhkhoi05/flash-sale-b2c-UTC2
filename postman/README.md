# Postman Collection - Flash Sale B2C

Collection + environment de test toan bo REST API cua backend `flash-sale-b2c-UTC2`.

## Files

| File | Mo ta |
|---|---|
| `Flash-Sale-B2C.postman_collection.json` | Collection chinh - 22 folder, 100 requests |
| `Flash-Sale-B2C.postman_environment.json` | Environment - 47 bien (auth tokens, IDs, test data) |
| `extract-paths-v3.js` | Script Node.js - extract toan bo API paths tu `@RequestMapping` controllers |
| `verify-final.js` | Script Node.js - verify collection vs code (92 endpoints) |

## Import

1. Mo Postman -> Import -> chon 2 file tren.
2. Chon environment `Flash-Sale-B2C` (goc tren ben phai).
3. (Tuy chon) Sua `baseUrl` neu backend chay port khac 8080.

## Cau truc folder (22 folders, 100 requests)

| # | Folder | Requests | Mo ta |
|---|---|---|---|
| 00 | Auth_Setup | 7 | Register / Login (Buyer/Seller/Admin) / Refresh / Logout |
| 01 | Public_Catalog | 11 | List/filter products, categories, store public |
| 02 | Public_FlashSale | 2 | List slot Flash Sale + items active |
| 03 | User_Profile | 4 | Me, update profile, change password, upload avatar |
| 04 | Address_Book | 6 | CRUD user addresses, set default |
| 05 | Cart | 5 | View, add, update, remove, clear |
| 06 | Order_Buyer | 5 | Checkout, list, detail, cancel, QR |
| 07 | Payment | 3 | ZaloPay, COD, get payment status |
| 08 | Voucher_Buyer | 5 | Platform, store, mine, save, apply |
| 09 | FlashSale_Reservation | 3 | Reserve (Lua), reserve alias, cancel |
| 10 | Store_Seller | 10 | Create store, update, addresses, logo, sidebar |
| 11 | Product_Seller | 8 | CRUD product, variants, status, images |
| 11b | Seller_Orders | 2 | List store orders, update status |
| 12 | FlashSale_Seller | 1 | Register Flash Sale item |
| 13 | Image_Generic | 6 | Upload, list, set primary, order, delete, force |
| 14 | Voucher_Seller | 2 | Create, list store voucher |
| 15 | Admin_Category | 3 | Create, update, delete |
| 16 | Admin_Store | 1 | Approve/reject store |
| 17 | Admin_FlashSale | 6 | Slot CRUD, approve item, pre-warm Redis |
| 18 | Admin_Voucher | 1 | Create platform voucher |
| 19 | Admin_Banner | 5 | CRUD + status banner |
| 20 | Negative_Tests | 5 | 401/403/404/409/422 |

## Luong test theo role

### Buyer (7 buoc)
1. Folder `00`: `POST Register Buyer` -> `POST Login (Buyer)` (token tu save vao `accessToken`)
2. Folder `04`: `POST Create Address` (luu `addressId`)
3. Folder `01`: `GET All Products` (luu `productId`, `variantId` tu response)
4. Folder `05`: `POST Add to Cart`
5. Folder `08`: `POST Save Voucher` (neu co) -> `POST Apply Voucher` (luu `voucherCode`)
6. Folder `06`: `POST Checkout` (luu `orderId`, `orderCode`)
7. Folder `07`: `POST COD` hoac `POST ZaloPay`

### Seller (5 buoc)
1. Folder `00`: `POST Register Seller` -> `POST Login (Seller)` (luu `sellerId`)
2. Folder `10`: `POST Register Store` -> `POST Create Store Address` (luu `storeId`)
3. Folder `11`: `POST Create Product` (luu `productId`, `variantId`)
4. Folder `12`: `POST Register FS Item`
5. Folder `11b`: `GET List Store Orders` -> `PATCH Update Order Status`

### Admin (3 buoc)
1. Folder `00`: `POST Login (Admin)`
2. Folder `16`: `PATCH Update Store Status` (chap nhan seller dang ky store)
3. Folder `17`: `PATCH Approve Item` -> `POST Pre-warm Redis`

## Bien moi truong tu dong cap nhat

Script trong test tu dong luu vao env:

| Bien | Luu tu |
|---|---|
| `accessToken`, `refreshToken` | Login response |
| `userId`, `userRole`, `buyerId`/`sellerId`/`adminId` | Login response |
| `addressId` | Create address response |
| `productId`, `variantId` | Create product response |
| `storeId` | Create store / sidebar response |
| `flashSaleSlotId`, `flashSaleItemId` | Admin create slot / register item |
| `orderId`, `orderCode` | Checkout response |
| `reservationId` | Reserve response |
| `imageId`, `bannerId`, `voucherCode` | Response tuong ung |
| `sellerOrderId`, `sellerOrderCode` | Seller list orders response |
| `idempotencyKey` | Pre-request script tu generate UUID neu chua co |

## Bien can set thu cong (neu test buoc rieng le)

- `baseUrl` - default `http://localhost:8080`
- `testEmail`, `testPassword` - cho buyer (da co san)
- `sellerEmail` - cho seller (da co san)
- `adminEmail`, `adminPassword` - cho admin (da co san)

## Verify sau khi sua backend

```bash
# Extract tat ca API paths tu controllers
node extract-paths-v3.js

# So sanh voi collection (bao MISSING/EXTRA)
node verify-final.js
```

## Ghi chu

- **Path prefix `/api/v1`** da duoc hard-code trong tat ca URL - dung khop voi `@RequestMapping` trong controllers.
- **Idempotency-Key** tu dong them vao `POST /api/v1/flash-sales/reserve` va `POST /api/v1/flash-sales/reservations` theo muc #34 AGENTS.md.
- **Multipart upload** (avatar/logo/image): chon file truc tiep trong Postman UI - body de trong.
- **Bodies** da khop voi DTO trong code. Neu backend thay doi field, sua lai body trong collection.
