# ⚡ Flash Sale B2C - Backend Engine (UTC2)

> **Hệ thống Backend Thương Mại Điện Tử B2C Đa Gian Hàng (Multi-Vendor Marketplace) Chuyên Biệt Cho Các Chiến Dịch Flash Sale Lưu Lượng Cao.**

[![Java 25](https://img.shields.io/badge/Java-25-orange.svg?logo=openjdk)](https://jdk.java.net/25/)
[![Spring Boot 4.1.1](https://img.shields.io/badge/Spring_Boot-4.1.1-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL 17](https://img.shields.io/badge/PostgreSQL-17-blue.svg?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-Lettuce_%2B_Redisson-red.svg?logo=redis)](https://redis.io/)
[![OpenSearch](https://img.shields.io/badge/OpenSearch-3.10-005FD3.svg?logo=opensearch)](https://opensearch.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Database_Migration-CC0200.svg?logo=flyway)](https://flywaydb.org/)
[![Swagger](https://img.shields.io/badge/OpenAPI_3-Swagger_UI-85EA2D.svg?logo=swagger)](http://localhost:8080/swagger-ui/index.html)

---

## 📖 1. Giới thiệu Tổng quan (Overview)

**Flash Sale B2C - UTC2** là giải pháp Backend hiệu năng cao giải quyết bài toán giao dịch bùng nổ (Traffic Spikes) trong các khung giờ Flash Sale. Hệ thống đảm bảo tính toàn vẹn dữ liệu, triệt tiêu hoàn toàn hiện tượng **Over-selling (bán vượt tồn kho)**, hỗ trợ tìm kiếm sản phẩm ngữ nghĩa, tích hợp thanh toán mã QR trực tuyến và đẩy dữ liệu biến động tức thời qua WebSocket.

### 🌟 Điểm nổi bật kỹ thuật (Key Highlights)
- 🔒 **Chống Over-selling tuyệt đối**: Kết hợp **Redis Lua Script** (trừ kho nguyên tử độ trễ ~0ms) và **Redisson Distributed Lock** nhằm bảo vệ kho hàng Flash Sale ở mức hàng chục nghìn lượt đặt mua đồng thời.
- ⏱️ **Tự động hoàn kho khi quá hạn (Timeout Rollback Scheduler)**: Người mua giữ chỗ (reservation) tối đa 15 phút. Nếu không thanh toán, hệ thống tự động hoàn kho Flash Sale và kho tổng của biến thể sản phẩm.
- 🔍 **Tìm kiếm Full-Text & Hình ảnh bằng OpenSearch 3.10**: Tìm kiếm mờ (fuzzy search), phân trang con trỏ (cursor search), bộ lọc danh mục và tìm kiếm sản phẩm bằng ảnh đa chiều (Image Embedding).
- ⚡ **Realtime WebSocket STOMP + SockJS**: Đẩy biến động kho Flash Sale theo thời gian thực tới toàn bộ khách hàng và bắn thông báo đơn hàng mới tới riêng kênh của từng chủ gian hàng (`/topic/stores/{storeId}/orders`).
- 💳 **Tích hợp Cổng thanh toán ZaloPay**: Tạo mã thanh toán động VietQR / ZaloPay QR bằng thư viện ZXing, xử lý Webhook IPN an toàn bằng mã hóa chữ ký HMAC SHA256.
- 🛡️ **Bảo mật chuẩn Doanh nghiệp**: Xác thực JWT kép (Access Token ngắn hạn + Refresh Token xoay vòng), đăng nhập Google OAuth2 (xác thực chữ ký offline qua Google JWKS), phân quyền chặt chẽ RBAC (`BUYER`, `SELLER`, `ADMIN`).
- 💰 **Ví điện tử & Đối soát tài chính (Reconciliation)**: Tính toán tự động hoa hồng sàn, quyết toán doanh thu ví cho người bán sau khi đơn hoàn tất và hỗ trợ yêu cầu rút tiền.

---

## 🛠️ 2. Công nghệ Sử dụng (Tech Stack)

| Thành phần | Công nghệ / Thư viện | Vai trò |
|---|---|---|
| **Core Framework** | **Java 25**, **Spring Boot 4.1.1** | Nền tảng thực thi chính, tận dụng Virtual Threads & tối ưu compiler |
| **Database** | **PostgreSQL 17** (Supabase / Self-hosted) | Lưu trữ quan hệ ACID, hỗ trợ chuẩn 3NF với 24 bảng dữ liệu |
| **Migration** | **Flyway Database Migration** | Quản lý phiên bản Schema database tự động (`V1` $\rightarrow$ `V17`) |
| **Caching & Locking** | **Redis** (Lettuce + Redisson 3.45.0) | Bộ đệm tốc độ cao, Distributed Lock & thực thi Lua Scripting |
| **Search Engine** | **OpenSearch Java Client 3.10.0** | Tìm kiếm sản phẩm toàn văn, gợi ý từ khóa và đối sánh vector |
| **Realtime** | **Spring WebSocket + STOMP + SockJS** | Giao tiếp 2 chiều đẩy biến động kho và thông báo đơn hàng |
| **Security** | **Spring Security 6+**, **JJWT 0.12.6** | Xác thực phân quyền, ký và xác thực JWT token |
| **OAuth2 Social** | **Google API Client 2.7.2** | Kiểm tra tính hợp lệ của Google ID Token trực tiếp từ Google JWKS |
| **Image Storage** | **Cloudinary SDK 2.4.0** | Quản lý lưu trữ ảnh đa hình (sản phẩm, avatar, banner, review) |
| **QR Code** | **ZXing Core 3.5.3** | Sinh mã ZaloPay QR trực tiếp trên máy chủ |
| **Mapping & Boilerplate**| **MapStruct 1.6.3**, **Lombok** | Tự động sinh code Mapper DTO và getter/setter sạch sẽ |
| **Documentation** | **SpringDoc OpenAPI 3.1.0** (Swagger UI) | Đặc tả và thử nghiệm API trực quan |

---

## 📂 3. Cấu trúc Dự án (Project Architecture)

Hệ thống tuân thủ kiến trúc phân tầng chuẩn (**Layered Clean Architecture**), phân tách rõ ràng giữa Controller, Service, Repository và Entity:

```
flash-sale-b2c-UTC2/
├── docs/                               # Toàn bộ tài liệu SDLC (Planning, SRS, Design, API)
├── src/main/java/com/b2c/flash_sale_b2c_UTC2/
│   ├── auth/                           # Đăng ký, đăng nhập JWT, Google OAuth2, Refresh Token
│   ├── banner/                         # Quản lý banner quảng cáo trang chủ
│   ├── cart/                           # Giỏ hàng người mua & kiểm tra tồn kho
│   ├── common/                         # Exception handler toàn cục, Base Response, Utils
│   ├── config/                         # Cấu hình Security, Redis, OpenSearch, CORS, WebSocket
│   ├── dashboard/                      # Thống kê KPI, doanh thu theo chu kỳ cho Admin & Seller
│   ├── flashsale/                      # Quản lý slot, đăng ký duyệt, Lua script trừ kho
│   ├── image/                          # Xử lý upload ảnh đa hình lên Cloudinary
│   ├── loadtest/                       # API endpoint nội bộ hỗ trợ kịch bản test tải
│   ├── order/                          # Vòng đời đơn hàng, hủy đơn, timeline giao hàng
│   ├── payment/                        # Tích hợp ZaloPay Sandbox, webhook IPN, tạo QR Code
│   ├── permission/                     # Quản lý phân quyền và nhân viên gian hàng
│   ├── product/                        # Quản lý SPU / SKU (ma trận biến thể 2 tầng), danh mục
│   ├── reconciliation/                 # Đối soát dòng tiền và hoa hồng sàn
│   ├── review/                         # Đánh giá sản phẩm kèm ảnh và phản hồi của shop
│   ├── search/                         # Đồng bộ OpenSearch và tìm kiếm theo text / hình ảnh
│   ├── store/                          # Đăng ký mở gian hàng, hồ sơ shop, xét duyệt shop
│   ├── user/                           # Quản lý tài khoản người dùng, sổ địa chỉ nhận hàng
│   ├── voucher/                        # Mã giảm giá toàn sàn & shop, bộ đếm atomic
│   ├── wallet/                         # Ví người bán, lịch sử giao dịch và yêu cầu rút tiền
│   └── FlashSaleB2cUtc2Application.java# Điểm khởi chạy ứng dụng
└── src/main/resources/
    ├── application.yaml                # Cấu hình gốc mặc định
    ├── application-local.yaml          # Cấu hình môi trường dev cục bộ
    ├── application-prod.yaml           # Cấu hình môi trường triển khai Production
    ├── db/migration/                   # 17 tệp SQL Flyway Migration từ V1 đến V17
    └── lua/                            # Kịch bản Lua script nguyên tử hóa cho Redis
```

---

## ⚙️ 4. Yêu cầu Môi trường (Prerequisites)

Trước khi khởi chạy dự án, hãy đảm bảo máy tính của bạn đã cài đặt:
- **Java Development Kit (JDK)**: Phiên bản **Java 25** (bắt buộc theo chuẩn toolchain dự án).
- **PostgreSQL**: Phiên bản 16 hoặc 17 (hoặc tài khoản cơ sở dữ liệu Supabase).
- **Redis Server**: Phiên bản 7.x trở lên.
- **OpenSearch**: Phiên bản 2.x hoặc 3.x (cần thiết cho module tìm kiếm nâng cao).

---

## 🚀 5. Hướng dẫn Khởi chạy Cục bộ (Getting Started)

### Bước 1: Chuẩn bị tệp cấu hình
Nhân bản tệp cấu hình môi trường phát triển:
```bash
cd flash-sale-b2c-UTC2/src/main/resources
cp application-local.yaml.example application-local.yaml
```

### Bước 2: Biên dịch và chạy ứng dụng

**Trên hệ điều hành Linux / macOS:**
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

**Trên hệ điều hành Windows (PowerShell / Command Prompt):**
```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

Khi máy chủ khởi động hoàn tất, cổng mặc định sẽ là **`http://localhost:8080`**.

---

## 🧪 6. Kiểm thử Dự án (Testing)

### Chạy Unit Test và Integration Test
Dự án được bao phủ bằng bộ test toàn diện (JUnit 5 + Mockito + Testcontainers):
```bash
# Linux / macOS
./gradlew test

# Windows
.\gradlew.bat test
```

### Kiểm thử Hiệu năng & Tải cao (Load Testing)
Thư mục `loadtest/` và các cấu hình profile `application-loadtest-lua.yaml` hỗ trợ kiểm thử đối sánh:
1. **Chiến lược Redis Lua Script**: Đảm bảo tốc độ trừ kho nhanh nhất không gây thắt cổ chai database.
2. **Chiến lược Redisson Lock**: Kiểm tra độ an toàn phân tán giữa nhiều node dịch vụ.

---

## 📑 7. Tài liệu API & Tích hợp (API Documentation)

Sau khi khởi chạy ứng dụng thành công, truy cập Swagger UI để kiểm tra và dùng thử toàn bộ hợp đồng API:

- 🔗 **Giao diện Swagger UI:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- 📄 **Đặc tả OpenAPI JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Danh mục Endpoint Chính:

| Phân hệ | Đường dẫn cơ sở | Mô tả chính |
|---|---|---|
| **Xác thực** | `/api/v1/auth/**` | Đăng ký, đăng nhập JWT, Google OAuth2, Refresh Token, Đổi mật khẩu |
| **Người dùng** | `/api/v1/users/**`, `/api/v1/addresses/**` | Thông tin cá nhân, quản lý sổ địa chỉ nhận hàng |
| **Gian hàng** | `/api/v1/stores/**` | Đăng ký cửa hàng, hồ sơ shop, nhân viên gian hàng |
| **Sản phẩm** | `/api/v1/products/**`, `/api/v1/seller/products/**` | Danh mục, quản lý SPU/SKU biến thể 2 tầng, ma trận tồn kho |
| **Flash Sale** | `/api/v1/flash-sales/**` | Khung giờ Flash Sale, đăng ký sản phẩm, đặt mua trừ kho siêu tốc |
| **Giỏ hàng** | `/api/v1/cart/**` | Thêm, cập nhật, xóa sản phẩm giỏ hàng |
| **Đơn hàng** | `/api/v1/orders/**`, `/api/v1/seller/orders/**` | Đặt hàng, duyệt đơn, chuẩn bị hàng, giao hàng, hủy đơn |
| **Thanh toán** | `/api/v1/payments/**` | Tạo đơn ZaloPay QR, nhận Webhook callback thanh toán |
| **Khuyến mãi** | `/api/v1/vouchers/**` | Tra cứu voucher toàn sàn và voucher shop |
| **Ví điện tử** | `/api/v1/wallets/**` | Số dư khả dụng, lịch sử giao dịch ví người bán |
| **Tìm kiếm** | `/api/v1/search/**` | Tìm kiếm sản phẩm qua OpenSearch và đối sánh hình ảnh |
| **Đánh giá** | `/api/v1/reviews/**` | Đánh giá đơn hàng kèm ảnh, người bán phản hồi |

---

## 📡 8. Kênh Realtime WebSocket (STOMP Endpoints)

- **WebSocket Handshake URL**: `ws://localhost:8080/ws` (hoặc HTTP fallback SockJS: `http://localhost:8080/ws`)
- **Query Authentication**: `?token=<JWT_ACCESS_TOKEN>`

| Destination | Kiểu | Mục đích |
|---|---|---|
| `/topic/flash-sale/stock-updates` | Public Broadcast | Đồng bộ số lượng tồn kho còn lại của các SKU Flash Sale đang diễn ra |
| `/topic/stores/{storeId}/orders` | Private Channel | Bắn thông báo đơn hàng mới và trạng thái đơn tức thời cho gian hàng |
| `/user/queue/orders` | User Private | Báo trạng thái đơn hàng thay đổi tới khách hàng |

---

## 📚 9. Tài liệu Kỹ thuật Chi tiết (SDLC Documentation)

Xem thêm các tài liệu phân tích và thiết kế chuyên sâu trong thư mục `docs/`:
- 📋 [01. Kế hoạch Dự án & Nghiên cứu Khả thi (Project Planning)](docs/01_project_planning.md)
- 📋 [02. Phân tích Yêu cầu Phần mềm (SRS)](docs/02_requirement_analysis.md)
- 📋 [03. Thiết kế Kiến trúc Hệ thống & CSDL 3NF (System Design)](docs/03_system_design.md)
- 📋 [04. Báo cáo Đánh giá Kiến trúc Chống Over-selling](docs/04_architecture_analysis.md)
- 📋 [05. Kiến trúc Tìm kiếm Mở rộng OpenSearch](docs/opensearch-search-architecture.md)

---

## 👥 10. Giấy phép & Tác giả (License & Team)

Dự án được xây dựng phục vụ nghiên cứu và phát triển đồ án chuyên ngành Công nghệ Thông tin - **Đại học Giao Thông Vận Tải Phân Hiệu Tại TP. Hồ Chí Minh (UTC2)**.
