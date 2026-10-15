# Postman Collection cho Flash Sale B2C Backend

> Muc tieu: Tao bo Postman (1 file Environment + 1 file Collection) de import vao Postman
> va test toan bo REST API cua backend `flash-sale-b2c-UTC2`.
>
> Nguyen tac:
> - KHONG sua code backend (chi tao 2 file JSON ngoai repo backend)
> - File o root repo de dev de tim, import 1 lan dung mai.
> - Postman v2.1 schema (tuong thich Postman >= v9).
> - Moi request co Pre-request Script lay token tu env, Tests verify status + schema co ban.

## 1. Pham vi

Tong cong: ~80+ endpoint (chia thanh ~15 folder theo concern). Xem schema trong file collection.

## 2. Cau truc 2 file

```
d:\flash-sale-b2c-UTC2\
├── postman/
│   ├── README.md
│   ├── Flash-Sale-B2C.postman_environment.json   # Environment: dev (local + docker)
│   └── Flash-Sale-B2C.postman_collection.json   # Collection: toan bo API
```

## 3. Cach su dung

```
1. Import 2 file vao Postman:
   - File > Import > Flash-Sale-B2C.postman_environment.json
   - File > Import > Flash-Sale-B2C.postman_collection.json

2. Chon Environment "Flash-Sale-B2C" (goc tren ben phai)

3. Chay theo thu tu (khuyen nghi):
   a. 00_Auth_Setup > POST Register Buyer
   b. 00_Auth_Setup > POST Login (Buyer)
   c. ... theo flow Buyer / Seller / Admin

4. Collection Runner (tuy chon):
   - Right-click collection > Run collection
   - De chay tat ca + xem report
```

## 4. Kich ban test theo Role (3 nhom)

### Buyer flow (full happy path)
```
00_Auth_Setup > POST Register Buyer
00_Auth_Setup > POST Login (Buyer)              -> luu accessToken, userId
01_Public_Catalog > GET Categories
01_Public_Catalog > GET Products
01_Public_Catalog > GET Product Detail
02_Public_FlashSale > GET Active Items
03_User_Profile > GET Me
04_Address_Book > POST Create Address           -> luu addressId
05_Cart > POST Add to Cart
05_Cart > GET My Cart
06_Order_Buyer > POST Checkout                  -> luu orderCode, orderId
07_Payment > POST Confirm COD                   (hoac tao ZaloPay)
06_Order_Buyer > GET Order Detail
08_Voucher_Buyer > GET Platform Vouchers
08_Voucher_Buyer > POST Apply Voucher (test)
09_FlashSale_Reservation > POST Reserve         (voi Idempotency-Key)
01_Public_Catalog > GET Product Reviews
```

### Seller flow
```
00_Auth_Setup > POST Register Seller
00_Auth_Setup > POST Login (Seller)
10_Store_Seller > POST Register Store           -> luu storeId
16_Admin_Store > PATCH Approve Store            (dung admin token)
10_Store_Seller > GET My Store
10_Store_Seller > POST Create Store Address
15_Admin_Category > POST Create Category        (hoac dung category co san)
11_Product_Seller > POST Create Product
11_Product_Seller > POST Upload Product Image
17_Admin_FlashSale > POST Create Slot           (dung admin token)
12_FlashSale_Seller > POST Register Flash Sale Item
17_Admin_FlashSale > PATCH Approve Item         (admin)
17_Admin_FlashSale > POST Pre-warm Redis        (admin)
11_Product_Seller > GET My Products
14_Voucher_Seller > POST Create Store Voucher
19_Admin_Banner > POST Create Banner            (admin)
```

### Admin flow
```
00_Auth_Setup > POST Login (Admin)
15_Admin_Category > POST/PUT/DELETE Category
16_Admin_Store > PATCH Update Store Status
17_Admin_FlashSale > toan bo admin slot/item
18_Admin_Voucher > POST Create Platform Voucher
19_Admin_Banner > toan bo
13_Image_Generic > DELETE Force Image
```

## 5. Test data mac dinh (seed)

Collection uu tien TU TAO qua API (test happy path full stack) - khong phu thuoc seed ngoai.

## 6. Out of scope (KHONG sua code)
- Khong sua backend code.
- Khong them file moi vao source backend.
- Chi tao 2 file JSON o root repo (folder `postman/`).
