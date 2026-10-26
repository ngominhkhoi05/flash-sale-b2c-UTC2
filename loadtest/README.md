# Flash Sale Load Testing (k6 + Redisson Distributed Lock)

Thư mục này chứa toàn bộ kịch bản và công cụ thực hiện kiểm thử tải (Load Test 1000 req/s) cho luồng mua hàng Flash Sale chống Over-selling.

---

## 1. Cấu trúc thư mục
- `tokens.json`: Danh sách 1000 user test kèm `addressId` và Bearer JWT access token (được sinh tự động).
- `purchase.js`: Kịch bản load test k6 theo mô hình `constant-arrival-rate` 1000 req/s trong 10s (tổng 10.000 requests), VUs: 500 - 2000.
- `reset.sh` / `reset.ps1`: Script đặt lại tồn kho DB & Redis, kích hoạt slot sale, và xóa các đơn hàng cũ của item test.
- `verify.sh` / `verify.ps1`: Script đối soát số lượng đơn hàng trong DB, tồn kho còn lại trong DB và Redis, phát hiện mua trùng (duplicate orders).

---

## 2. Hướng dẫn chạy

### Bước 1: Khởi động Backend với profile `loadtest`
Profile `loadtest` kích hoạt Redisson Distributed Lock (`redisson.enabled=true`, `flashsale.reservation.strategy=redisson`, `flashsale.lock.wait-ms=200`).

```powershell
./gradlew.bat bootJar
java -Dspring.profiles.active=loadtest -jar build/libs/flash-sale-b2c-UTC2-0.0.1-SNAPSHOT.jar
```

*(Hoặc để kiểm tra baseline với Lua Script mặc định: `java -jar build/libs/flash-sale-b2c-UTC2-0.0.1-SNAPSHOT.jar`)*

### Bước 2: Reset tồn kho về mức cần test
Mặc định test ItemId = 1, số lượng tồn kho = 100 (hoặc 10, 1000):

Trên Windows PowerShell:
```powershell
.\loadtest\reset.ps1 -Stock 100 -ItemId 1
```

Trên Bash / Linux:
```bash
./loadtest/reset.sh 100 1
```

### Bước 3: Chạy k6 load test
```powershell
k6 run -e BASE_URL="http://localhost:8080" -e ITEM_ID=1 loadtest/purchase.js
```

### Bước 4: Đối soát dữ liệu (Verify)
```powershell
.\loadtest\verify.ps1 -ItemId 1
```

---

## 3. Cách đọc kết quả kiểm thử
- `count_200_success`: Số request đặt hàng giữ chỗ thành công (HTTP 200/201). **Phải bằng đúng tồn kho ban đầu**.
- `count_409_out_of_stock`: Số request bị từ chối do hết hàng (HTTP 409).
- `count_429_lock_timeout`: Số request không lấy được lock do quá tải / hàng đợi khóa bận (HTTP 429).
- `count_5xx_server_error`: Số lỗi 5xx của server (**phải bằng 0**).
- `total_orders` trong DB: **phải bằng đúng số 200 từ k6 và bằng tồn kho ban đầu**.
- `db_available_stock` và `Redis Stock`: **phải bằng 0 và khớp nhau hoàn toàn** (khi số request > tồn kho ban đầu).
- `duplicate_orders_count`: **phải bằng 0** (không có user nào mua vượt giới hạn).
