# PHẢN HỒI TRIỂN KHAI VÀ HỢP ĐỒNG API CHO FRONTEND — FLASH SALE B2C

> **Ngày:** 2026-10-14  
> **Từ:** Backend Team (`flash-sale-b2c-UTC2`)  
> **Gửi:** Frontend Team (`flash-sale-b2c-fe`)  
> **Tham chiếu:** Yêu cầu từ file `backend-api-requirements.md` (Mục 9 & 15)  
> **Nhánh Backend triển khai:** `feat/flash-sale-reserve` (PR vào `dev`)  
> **Trạng thái:** ✅ Đã hoàn thành Đợt 1 (Flash Sale Reserve & Cancel Reservation) — Test tích hợp thực tế (100 luồng đồng thời) đạt 100%.

---

## 1. Tổng quan & Lộ trình triển khai

Để đảm bảo chất lượng kỹ thuật, chống bán vượt số lượng (over-selling) và giữ tính đồng bộ với repo, Backend tách việc triển khai nhóm tính năng ưu tiên cao thành 3 đợt theo nhánh riêng biệt:

1. **Đợt 1 (Hiện tại - Đã xong):** Nhánh `feat/flash-sale-reserve`
   - `POST /api/v1/flash-sales/reserve` (Mục 9.2)
   - `DELETE /api/v1/flash-sales/reservations/{reservationId}` (Mục 9.3)
   - Lõi kiểm soát tồn kho Redis nguyên tử bằng Lua Script, tự động đối soát phục hồi sự cố.
2. **Đợt 2 (Kế tiếp):** Nhánh `feat/order-list-detail-cancel`
   - `GET /api/v1/orders` (Mục 5.3)
   - `GET /api/v1/orders/{code}` (Mục 5.4 - Tra cứu bằng `orderCode` chuỗi)
   - `POST /api/v1/orders/{code}/cancel` (Mục 5.6)
3. **Đợt 3:** Nhánh `feat/ws-stock-payment`
   - WebSocket Broadcast tồn kho Flash Sale: `/topic/flash-sale/slot/{slotId}/stock-update` (Mục 14.1)
   - WebSocket User Queue trạng thái thanh toán: `/user/queue/payment-status` (Mục 14.2)
4. **Các nhóm phụ (Banner, Featured Stores, Reviews, ...):** Sẽ triển khai tiếp theo sau khi chốt hoàn tất 3 luồng lõi trên.

---

## 2. Chi tiết Hợp đồng API Đợt 1 (Sẵn sàng tích hợp)

### 2.1. Giữ chỗ Flash Sale (Reserve Item)

- **Method:** `POST`
- **Path:** `/api/v1/flash-sales/reserve`
- **Mục đích:** Giữ chỗ và chốt giá Flash Sale trong vòng 300 giây (5 phút). Tạo đơn hàng ở trạng thái `PENDING_PAYMENT`. Trừ tồn kho phân bổ trên Redis nguyên tử bằng Lua Script chống bán vượt.
- **Headers:**
  ```http
  Authorization: Bearer <jwt_token>
  Content-Type: application/json
  Idempotency-Key: <UUID> (Khuyến nghị gửi từ FE để chống lặp đơn khi mạng lag)
  ```

#### Request Body
```json
{
  "flashSaleItemId": 101,
  "quantity": 1,
  "addressId": 15
}
```

*Quy tắc nghiệp vụ cho FE cần chú ý:*
1. `flashSaleItemId`: Bắt buộc. ID của mặt hàng trong phiên Flash Sale (`flash_sale_items.id`).
2. `quantity`: Bắt buộc. Số lượng muốn mua (mặc định 1). Nếu vượt quá `userPurchaseLimit` của tài khoản trong phiên, API sẽ từ chối với mã lỗi rõ ràng.
3. `addressId`: **Tùy chọn (Optional)**.
   - Nếu FE truyền `addressId`: Backend kiểm tra quyền sở hữu của Buyer.
   - Nếu FE **không truyền** `addressId`: Backend tự động lấy địa chỉ mặc định (hoặc địa chỉ đầu tiên) trong sổ địa chỉ của tài khoản.
   - **Trường hợp tài khoản chưa có bất kỳ địa chỉ nào**: Backend trả lỗi `FLASH_SALE_ADDRESS_REQUIRED` (HTTP 400). FE hãy bắt mã lỗi này để hiển thị modal yêu cầu người dùng thêm địa chỉ trước khi giữ chỗ.

#### Response Body thành công (`200 OK`)
Toàn bộ phản hồi được bọc trong chuẩn `ApiResponse<T>`:
```json
{
  "success": true,
  "code": 200,
  "message": "Giữ chỗ Flash Sale thành công",
  "data": {
    "reservationId": "FS-1791963512929-3365",
    "orderCode": "FS-1791963512929-3365",
    "slotId": 1,
    "flashSaleItemId": 101,
    "quantity": 1,
    "flashSalePrice": 99000.00,
    "totalAmount": 99000.00,
    "expiresAt": "2026-10-14T14:40:00.000Z"
  },
  "timestamp": "2026-10-14T14:35:00.000Z"
}
```

*Ghi chú các trường trả về:*
- `reservationId` và `orderCode`: Đồng nhất là chuỗi mã đơn hiển thị (ví dụ `FS-...`). FE có thể dùng trường nào tùy ý để điều hướng sang trang thanh toán.
- `flashSalePrice`: Giá Flash Sale chốt cứng tại thời điểm giữ chỗ, không bị ảnh hưởng nếu đợt sale sau đó thay đổi giá.
- `expiresAt`: Mốc thời gian ISO-8601 hết hạn giữ chỗ (300 giây sau thời điểm gọi). FE dùng trường này để hiển thị đồng hồ đếm ngược thanh toán.

---

### 2.2. Hủy giữ chỗ Flash Sale (Cancel Reservation)

- **Method:** `DELETE`
- **Path:** `/api/v1/flash-sales/reservations/{reservationId}`
- **Mục đích:** Khách hàng chủ động hủy giữ chỗ khi đổi ý trước khi thanh toán. Trả lại tồn kho phân bổ trên Redis & Database, phục hồi hạn mức mua cho khách hàng.
- **Path Variable:**
  - `reservationId`: Chuỗi mã đơn hàng cần hủy (`orderCode`, ví dụ `FS-1791963512929-3365`).
- **Headers:**
  ```http
  Authorization: Bearer <jwt_token>
  ```

#### Response Body thành công (`200 OK`)
```json
{
  "success": true,
  "code": 200,
  "message": "Hủy giữ chỗ Flash Sale thành công",
  "data": {
    "success": true,
    "message": "Khách hàng userId=5 đã hủy thành công giữ chỗ reservationId=FS-1791963512929-3365"
  },
  "timestamp": "2026-10-14T14:35:05.000Z"
}
```

*Lưu ý cho FE:*
- Endpoint này chỉ hủy các đơn đang ở trạng thái `PENDING_PAYMENT`.
- Thao tác là **Idempotent**: Nếu FE gửi 2 lần liên tiếp do double-click hoặc retry mạng, Backend xử lý an toàn (không bị cộng dồn hoàn kho 2 lần và không báo lỗi crash).

---

## 3. Bảng mã lỗi (Error Codes) cho Frontend

FE có thể dựa vào trường `code` (hoặc thông báo trong `message`) để hiển thị Toast / Alert thích hợp cho người dùng:

| HTTP Status | Mã lỗi Backend | Ý nghĩa / Nguyên nhân | Gợi ý xử lý trên UI FE |
|---|---|---|---|
| **409 Conflict** | `FLASH_SALE_OUT_OF_STOCK` | Mặt hàng đã hết sạch tồn kho phân bổ trên Redis | Hiển thị badge "Hết hàng", chuyển nút sang disable |
| **422 Unprocessable** | `FLASH_SALE_PURCHASE_LIMIT_EXCEEDED` | Khách hàng đã đạt giới hạn số lượng mua tối đa trong phiên này | Báo Toast: "Bạn đã vượt quá số lượng mua tối đa cho sản phẩm này trong khung giờ sale" |
| **400 Bad Request** | `FLASH_SALE_SLOT_NOT_ACTIVE` | Phiên Flash Sale chưa tới giờ mở bán hoặc không ở trạng thái ACTIVE | Disable nút mua, hiển thị đồng hồ đếm ngược tới giờ mở |
| **400 Bad Request** | `FLASH_SALE_SLOT_ALREADY_ENDED` | Phiên Flash Sale đã kết thúc | Hiển thị nhãn "Khung giờ sale đã kết thúc" |
| **400 Bad Request** | `FLASH_SALE_ADDRESS_REQUIRED` | Khách hàng chưa thiết lập bất kỳ địa chỉ nhận hàng nào | Mở popup/modal: "Vui lòng thêm địa chỉ nhận hàng để tiếp tục" |
| **400 Bad Request** | `FLASH_SALE_ADDRESS_NOT_FOUND_OR_NOT_OWNED` | `addressId` gửi lên không tồn tại hoặc không thuộc quyền sở hữu của user | Nhắc người dùng chọn lại địa chỉ trong danh sách |
| **404 Not Found** | `FLASH_SALE_RESERVATION_NOT_FOUND` | Không tìm thấy mã đơn/giữ chỗ tương ứng | Báo lỗi: "Không tìm thấy thông tin đơn giữ chỗ" |
| **400 Bad Request** | `FLASH_SALE_CANNOT_CANCEL_NON_PENDING_RESERVATION` | Đơn hàng không còn ở trạng thái `PENDING_PAYMENT` (đã thanh toán hoặc đã hủy) | Không cho hủy, báo Toast: "Đơn hàng không ở trạng thái có thể hủy giữ chỗ" |
| **401 Unauthorized** | `AUTH_TOKEN_INVALID` / `AUTH_UNAUTHORIZED` | Token hết hạn hoặc chưa đăng nhập | Điều hướng người dùng tới trang Login |

---

## 4. Quản lý Vòng đời Giữ chỗ (Reservation Lifecycle & TTL)

1. **Thời hạn 300 giây:**
   - Sau khi giữ chỗ thành công, người dùng có 300 giây để hoàn tất thanh toán.
   - Phía Backend đã kích hoạt background scheduler tự động quét mỗi 15 giây. Khi quá hạn `expiresAt`, đơn hàng tự động chuyển sang `CANCELLED_TIMEOUT` và tự động hoàn trả tồn kho về slot flash sale.
2. **Idempotency Key:**
   - FE nên tạo UUID v4 gán vào header `Idempotency-Key` mỗi lần user bấm nút "Mua ngay / Giữ chỗ".
   - Backend cache kết quả trong 10 phút. Nếu mạng bị gián đoạn và FE gửi lại cùng key, hệ thống trả về ngay đơn hàng cũ mà không tạo thêm đơn mới và không trừ thêm kho.

---

## 5. Lộ trình tích hợp tiếp theo

Ngay sau khi nhánh `feat/flash-sale-reserve` được review và merge, Backend sẽ mở tiếp nhánh `feat/order-list-detail-cancel` để cung cấp:
- `GET /api/v1/orders` (lọc theo `status=PENDING_PAYMENT`, phân trang `page`, `size`)
- `GET /api/v1/orders/{code}` (xem chi tiết đơn hàng giữ chỗ theo mã hiển thị `orderCode`)
- `POST /api/v1/orders/{code}/cancel` (hủy đơn hàng thống nhất)

Mọi thắc mắc trong quá trình tích hợp, team FE hãy gửi phản hồi để team BE hỗ trợ điều chỉnh kịp thời!
