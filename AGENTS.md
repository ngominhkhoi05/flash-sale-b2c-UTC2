# AGENTS.md

> **Source of truth** cho mọi AI agent / Cursor session làm việc trên repo `flash-sale-b2c-UTC2`.
> Repo này là **Spring Boot backend** của hệ thống Flash Sale B2C Multi-Vendor Marketplace.
> Repo `flash-sale-b2c` là **Next.js frontend**. Repo `deploy-b2c-utc2` chứa **docker-compose, nginx, scripts deploy**.
> Mọi endpoint dưới đây trừ khi ghi chú là thuộc backend này.

---

## Mục lục

1. [Project Overview](#1-project-overview)
2. [Source of Truth](#2-source-of-truth)
3. [Agent Workflow](#3-agent-workflow)
4. [Java Version - Mandatory](#4-java-version---mandatory)
5. [Project Structure](#5-project-structure)
6. [Architecture Rules](#6-architecture-rules)
7. [Common API Rules](#7-common-api-rules)
8. [Database](#8-database)
9. [Database Integrity](#9-database-integrity)
10. [24-Table Design](#24-table-design)
11. [Multi-Vendor Order](#11-multi-vendor-order)
12. [Address Ownership](#12-address-ownership)
13. [Flash Sale Architecture](#13-flash-sale-architecture)
14. [Flash Sale Stock Model](#14-flash-sale-stock-model)
15. [Flash Sale Purchase Limit](#15-flash-sale-purchase-limit)
16. [Timeout Rollback](#16-timeout-rollback)
17. [Redis → Database Compensation](#17-redis--database-compensation)
18. [Flash Sale Item → Order Item Consistency](#18-flash-sale-item--order-item-consistency)
19. [Flash Sale Lifecycle](#19-flash-sale-lifecycle)
20. [Payment](#20-payment)
21. [Wallet](#21-wallet)
22. [Voucher](#22-voucher)
23. [Order Snapshots](#23-order-snapshots)
24. [Product Review](#24-product-review)
25. [Image Module (Polymorphic)](#25-image-module-polymorphic)
26. [Transaction](#26-transaction)
27. [Concurrency](#27-concurrency)
28. [N+1](#28-n1)
29. [Pagination](#29-pagination)
30. [Security](#30-security)
31. [MapStruct + Lombok](#31-mapstruct--lombok)
32. [Validation](#32-validation)
33. [API Convention](#33-api-convention)
34. [Idempotency Convention](#34-idempotency-convention)
35. [Background Jobs / Schedulers](#35-background-jobs--schedulers)
36. [Realtime WebSocket (STOMP + SockJS)](#36-realtime-websocket-stomp--sockjs)
37. [CORS & Security Wiring](#37-cors--security-wiring)
38. [Error Code Convention](#38-error-code-convention)
39. [Build & Toolchain](#39-build--toolchain)
40. [Testing](#40-testing)
41. [Git Workflow](#41-git-workflow)
42. [No Premature Over-Engineering](#42-no-premature-over-engineering)
43. [Cross-Repo Layout](#43-cross-repo-layout)
44. [Task Execution Checklist](#44-task-execution-checklist)
45. [Required Final Response Format](#45-required-final-response-format)
46. [Auth, User, Store, Product, Flash Sale Endpoints](#46-auth-user-store-product-flash-sale-endpoints)
47. [Documentation & Code Sync Checklist](#47-documentation--code-sync-checklist)
48. [Migration Impact Matrix](#48-migration-impact-matrix)

---

## 1. Project Overview

### Project
**Flash Sale B2C - UTC2**

Đây là project B2C Multi-Vendor Marketplace kết hợp Flash Sale, phục vụ mục đích đồ án.

### Technology Stack

- Java 25
- Spring Boot
- Gradle (Kotlin DSL `build.gradle.kts`)
- PostgreSQL
- Spring Data JPA / Hibernate (`ddl-auto: validate`)
- Flyway
- Spring Security
- JWT (HS256)
- Redis
- Redis Lua Script + Redisson (optional strategy)
- MapStruct
- Lombok
- Springdoc OpenAPI / Swagger
- Spring WebSocket + STOMP + SockJS (realtime)
- Cloudinary (image storage, polymorphic)

### Current Development Phase

Đã hoàn thành:
1. Planning
2. Requirement
3. Design

Đang bước vào:
4. Implementation

---

## 2. Source of Truth

Agent phải đọc và tuân thủ (ưu tiên từ trên xuống):

1. Code hiện tại (luôn là ground truth cuối cùng).
2. File `AGENTS.md` này.
3. Các tài liệu trong `docs/` (`01_project_planning.md`, `02_requirement_analysis.md`, `03_system_design.md`, `04_architecture_analysis.md`, `api/api-document.md`, `flashsale_order_contract.md`).
4. Migration SQL trong `src/main/resources/db/migration/`.
5. Convention và cấu trúc hiện có trong codebase.

### Quy tắc `docs/`

- `docs/` chứa tài liệu thiết kế đã được chốt.
- **Không tự ý sửa, xóa, đổi tên hoặc format lại file trong `docs/`.**
- Chỉ sửa `docs/` khi user yêu cầu rõ ràng.
- Nếu code khác tài liệu, không tự ý thay đổi thiết kế lớn; báo lại trước.

### Khi phát hiện mâu thuẫn

- Code ≠ `AGENTS.md` → báo user, **không tự ý sửa `AGENTS.md`** (mục #46).
- Code ≠ `docs/` → báo user, **không tự ý sửa `docs/`**.
- `AGENTS.md` ≠ `docs/` → ưu tiên `docs/`, cập nhật `AGENTS.md` cho khớp (nếu user yêu cầu).
- Phát hiện docs lỗi thời khi đang implement → dùng **bảng Migration Impact Matrix (mục #48)** để biết file nào cần sync, **báo danh sách** cho user trước khi sửa.

---

## 3. Agent Workflow

Mỗi task nên thực hiện:

1. **Understand** — đọc lại yêu cầu, xác định phạm vi (module nào, layer nào, có đụng DB / Redis / WS không).
2. **Inspect** — đọc code hiện tại của module liên quan + `AGENTS.md` + mục docs liên quan.
3. **Read relevant docs** — chỉ đọc phần docs thực sự liên quan.
4. **Plan** — liệt kê file sẽ tạo/sửa, migration (nếu có), side-effect (Redis key, WS event, scheduled job).
5. **Implement** — đúng layer, đúng convention.
6. **Validate** — `gradlew compileJava` (nhanh), rồi `gradlew test` (module liên quan), `gradlew build` (cuối).
7. **Review diff** — tự review lại các thay đổi.
8. **Report** — báo cáo theo format mục #45.

Không nhảy trực tiếp vào code nếu task liên quan architecture, database, concurrency hoặc business flow.

---

## 4. Java Version - Mandatory

- Project sử dụng **Java 25**.
- Toolchain phải khai báo trong `build.gradle.kts`:

  ```kotlin
  java {
      toolchain {
          languageVersion = JavaLanguageVersion.of(25)
      }
  }
  ```

- Máy development có thể mặc định Java 21 — phải dùng `java25` (alias trong PATH) trước khi build/test/run.
- KHÔNG dùng `--enable-preview` features trừ khi user yêu cầu rõ.
- Gradle wrapper tự động download JDK 25 qua toolchain; không hardcode JAVA_HOME trong code.

---

## 5. Project Structure

### Main package

```text
src/main/java/com/b2c/flash_sale_b2c_UTC2/
```

### Modules (15 module nghiệp vụ + 2 common)

```text
auth/        # JWT, login, register, refresh token, RBAC seeding
user/        # User profile, address book
store/       # Store, warehouse address
product/     # Category, Product SPU, ProductVariant SKU
image/       # Polymorphic Image Management (Cloudinary)
flashsale/   # Flash Sale core (slot, item, reservation, Lua)
cart/        # Cart, CartItem
order/       # Order, OrderItem (single-vendor per Order)
payment/     # Payment + callback
voucher/     # Voucher + VoucherUsage
wallet/      # Wallet + WalletTransaction
review/      # ProductReview

common/      # ApiResponse, PageResponse, BusinessException, ErrorCode, GlobalExceptionHandler
config/      # Security, JWT, Redis, Redisson, Cors, OpenApi, Cloudinary, realtime
```

### Config breakdown (`config/`)

```text
config/
├── SecurityConfig.java
├── JwtProperties.java
├── RedisConfig.java
├── RedissonConfig.java
├── CorsConfig.java
├── OpenApiConfig.java
├── CloudinaryConfig.java
├── CloudinaryProperties.java
└── realtime/
    ├── WebSocketConfig.java         # Broker /topic, /user; heartbeat 10s
    ├── HandshakeAuthInterceptor.java # JWT qua query param ?token=
    ├── WebSocketAuthConfig.java     # ChannelInterceptor reload UserDetails
    └── WsSessionEventListener.java  # Log lifecycle + đếm active session
```

> **Không tạo** `WebSocketSchedulerConfig.java` riêng. `WebSocketConfig` dùng `ObjectProvider<TaskScheduler>` lookup `messageBrokerTaskScheduler` mặc định của Spring. **Không tạo** `application-websocket.yaml` riêng — config WS nằm trong `application.yaml` chung (đoạn `spring.websocket.*` và broker prefix).

### Layer convention cho mỗi module

```text
controller/    # @RestController, nhận request, validate, gọi service
service/       # Business logic, @Transactional boundary, ownership check
repository/    # Spring Data JPA, query method, custom JPQL
entity/        # @Entity mapping table
dto/           # request/response, không expose Entity
mapper/        # MapStruct @Mapper(componentModel = "spring")
exception/     # *ErrorCode enum (extends CommonErrorCode)
config/        # Bean riêng module (chỉ khi cần)
scheduler/     # @Scheduled jobs (chỉ module có job)
strategy/      # Strategy pattern (chỉ module cần, vd: flashsale)
port/          # Inbound/Outbound port (chỉ module cần, vd: flashsale)
realtime/      # WS event/destination/broadcaster (chỉ module cần)
event/         # Spring event (chỉ module cần, vd: image cleanup)
listener/      # @EventListener (chỉ module cần)
enums/         # enum nghiệp vụ module
```

Không tạo package/module chỉ để làm code phức tạp hơn nếu chưa có nhu cầu.

---

## 6. Architecture Rules

Project sử dụng kiến trúc phân tầng (layered).

### Controller
- Nhận HTTP request.
- Validate input (Jakarta Validation).
- Gọi Service.
- Trả HTTP response (wrap trong `ApiResponse<T>`).
- **Không chứa business logic phức tạp.**

### Service
- Business logic.
- `@Transactional` boundary.
- Ownership check.
- State validation.
- Điều phối Repository, Redis, external client.
- Chỉ gọi WS thông qua `FlashSaleWsBroadcaster` (mục #36), không gọi `SimpMessagingTemplate` trực tiếp.

### Repository
- Database access.
- Query.
- Lock khi thực sự cần (`@Lock(PESSIMISTIC_WRITE)` chỉ cho flow cần).
- **Không chứa business logic.**

### Entity
- Mapping database.
- Quan hệ JPA.
- Constraint mapping khi phù hợp (`@Column(nullable = false)`, `@Enumerated`).
- **Không đặt business workflow phức tạp vào Entity.**

### DTO
- API input/output.
- **Không expose Entity trực tiếp** nếu không có lý do rõ ràng.

### Mapper
- MapStruct: `@Mapper(componentModel = "spring")`.
- Mapper phải được Spring quản lý (injection qua constructor).
- **Không commit generated files** (`*/target/generated-sources/` đã có trong `.gitignore`).

---

## 7. Common API Rules

### Response wrapper

Mọi API (kể cả lỗi) đều wrap trong `ApiResponse<T>`:

```json
{
  "success": true,
  "code": 200,
  "message": "Lấy danh sách thành công",
  "data": { ... },
  "timestamp": "2026-10-07T10:00:00.000Z"
}
```

### Pagination

`PageResponse<T>` chuẩn, dùng cho mọi list API có phân trang. Mặc định `page=0`, `size=20`, `MAX_PAGE_SIZE=100`, sort `createdAt DESC`.

### Exception

Xử lý tập trung qua:

```text
common/exception/
├── BusinessException.java        # RuntimeException + ErrorCode
├── ErrorCode.java                # interface { String getCode(); HttpStatus getStatus(); String getMessageKey(); }
├── CommonErrorCode.java          # enum implements ErrorCode, prefix COMMON_
└── GlobalExceptionHandler.java   # @RestControllerAdvice
```

**Mỗi module có 1 `*ErrorCode` enum riêng**, prefix theo module:

| Module | Prefix | Ví dụ |
|---|---|---|
| auth | `AUTH_` | `AUTH_INVALID_CREDENTIALS` |
| user | `USER_` | `USER_NOT_FOUND` |
| store | `STORE_` | `STORE_NOT_APPROVED` |
| product | `PRODUCT_` | `PRODUCT_OUT_OF_STOCK` |
| image | `IMAGE_` | `IMAGE_OWNER_FORBIDDEN` |
| flashsale | `FLASH_SALE_` | `FLASH_SALE_OUT_OF_STOCK` |
| cart | `CART_` | `CART_EMPTY` |
| order | `ORDER_` | `ORDER_NOT_OWNER` |
| payment | `PAYMENT_` | `PAYMENT_DUPLICATE` |
| voucher | `VOUCHER_` | `VOUCHER_EXPIRED` |
| wallet | `WALLET_` | `WALLET_INSUFFICIENT` |
| review | `REVIEW_` | `REVIEW_ALREADY_EXISTS` |

- `GlobalExceptionHandler` trả 401 (invalid token), 403 (không đủ quyền), 404, 409, 422, 500 theo `ErrorCode`.
- **Không trả stack trace** hoặc exception nội bộ trực tiếp cho client.
- Field-level validation error trả trong `data` dạng `{ "field": "email", "message": "..." }`.

---

## 8. Database

### Engine

```text
PostgreSQL
```

### Migration

- **Flyway** là nguồn quản lý schema duy nhất.
- Location: `classpath:db/migration`.
- Naming: `V{n}__{description}.sql` (V1, V2, ...).
- **Không sửa migration đã áp dụng** trong môi trường dùng chung nếu không có lý do đặc biệt.
- Nếu phải sửa, dùng `repair` của Flyway + báo user.

### Migration hiện tại

```text
V1__init_schema.sql                 # 24 bảng
V2__add_version_to_product_variants.sql  # Optimistic lock
V3__create_images_table.sql         # Polymorphic image
V4__drop_legacy_image_columns.sql   # Bỏ 5 cột ảnh cũ
```

### Hibernate

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

- **Không dùng** `create` / `create-drop` / `update` làm cơ chế quản lý schema chính.
- `open-in-view: false` (đã cấu hình).

### Init data

- Có thể dùng `data.sql` (Spring Boot) cho seed data **chỉ trong profile `local`**.
- Không commit seed data production vào repo.

---

## 9. Database Integrity

Giữ các constraint đã thiết kế:

- Primary Key
- Foreign Key (`ON DELETE` chỉ định rõ: CASCADE / RESTRICT / SET NULL)
- UNIQUE (kể cả partial unique index)
- NOT NULL
- CHECK
- Index (cho mọi cột hay filter / join)
- Default value (cho cột timestamp, status, version)

### Phân biệt rõ 3 loại rule

### Database Constraint
- Đảm bảo data integrity ở database.
- Constraint vi phạm → exception SQL, không được "nuốt" ở service.

### Service Business Rule
- Đảm bảo nghiệp vụ.
- Validation phức tạp (multi-table, multi-step) đặt ở Service.

### Redis Concurrency Rule
- Đảm bảo atomicity/concurrency của Flash Sale.
- Source of truth runtime cho stock / purchase limit.

---

## 10. 24-Table Design

Giữ nguyên kiến trúc **24 bảng** đã thiết kế (xem `V1__init_schema.sql`):

```text
roles, users, permission_groups, permissions, group_permissions, user_roles,
auth_accounts, stores, addresses, wallets, categories, products, product_variants,
flash_sale_slots, flash_sale_items, vouchers, orders, order_items, payments,
wallet_transactions, voucher_usages, carts, cart_items, product_reviews
```

> **Migration V3 thêm bảng `images` (polymorphic) — bảng thứ 25, không tính vào "24 bảng nghiệp vụ"**. Đây là module phụ trợ, không thay đổi 24 bảng cốt lõi.

Không tự ý:
- Thêm Parent Order.
- Thêm Seller Order.
- Tách microservices.
- Thêm Saga.
- Thêm 2PC.
- Thay đổi mô hình dữ liệu lớn.

Chỉ thay đổi schema khi có yêu cầu rõ ràng hoặc phát hiện lỗi thiết kế thực sự.

---

## 11. Multi-Vendor Order

Một `Order` thuộc về **một Store** (single-vendor per Order).

Nếu Cart có nhiều Store:

```text
Cart
 ├── Store A → Order A
 ├── Store B → Order B
 └── Store C → Order C
```

Checkout phải **split cart theo Store trước khi tạo Order**. Mỗi `Order` có `store_id` riêng, tính phí ship / voucher / commission độc lập.

Không tạo Parent Order / Seller Order chỉ để xử lý multi-vendor.

---

## 12. Address Ownership

Khi Buyer tạo Order phải kiểm tra:

```text
address.user_id == current_user.id
```

- Foreign Key hợp lệ không đồng nghĩa address thuộc Buyer hiện tại.
- Snapshot địa chỉ trong Order phải được giữ (xem mục #23) để đảm bảo lịch sử.
- Mọi thao tác `GET / PUT / DELETE / PATCH default` trên address đều check ownership; nếu không khớp ném `*ErrorCode.ADDRESS_ACCESS_DENIED` (HTTP 403).

### XOR Constraint

Bảng `addresses` có CHECK constraint `chk_address_owner_xor`:

```text
(user_id IS NOT NULL AND store_id IS NULL)
HOẶC
(user_id IS NULL  AND store_id IS NOT NULL)
```

- Address của **Buyer** → `user_id = currentUser`, `store_id = NULL`.
- Address **kho Store** → `store_id = currentStore`, `user_id = NULL`.

### Single Default Address

Khi đánh dấu một địa chỉ làm mặc định (`is_default = true`), toàn bộ địa chỉ khác của cùng owner phải chuyển về `is_default = false`. Thực hiện trong transaction.

---

## 13. Flash Sale Architecture

Flash Sale sử dụng:

```text
PostgreSQL    # nguồn bền vững (durable source of truth)
+
Redis         # runtime stock / purchase limit
+
Lua Script    # atomic reservation
+
Redisson      # strategy dự phòng (so sánh/benchmark)
```

- Redis xử lý stock/concurrency trong thời điểm Flash Sale.
- PostgreSQL là nguồn dữ liệu bền vững.
- **Không coi Redis là database chính** thay thế PostgreSQL.
- **Không coi WS là source of truth** — chỉ là notification (mục #36).

### Boundary rule

- Module `flashsale` được phép **đọc + ghi** flash sale domain tables + Redis.
- Các module `order`, `store`, `voucher`, `cart`, `payment`, `wallet` là **read-only** đối với flash sale domain.
- Tích hợp giữa `flashsale` và `order` thông qua interface `FlashSaleOrderPort` (in-module port, default impl `InMemoryFlashSaleOrderPort`).

---

## 14. Flash Sale Stock Model

Có thể tồn tại 3 mức stock:

```text
product_variants.stock_quantity     # base stock (seller quản lý)
flash_sale_items.allocated_stock    # đã allocate cho slot
flash_sale_items.available_stock    # còn bán được trong slot (Redis mirror)
```

Đây là **operational/materialized state** phục vụ Flash Sale và không tự động được xem là vi phạm 3NF.

### Invariants

```text
0 <= available_stock <= allocated_stock
allocated_stock <= product_variants.stock_quantity (tại thời điểm approve)
```

- Seller **không được giảm** base stock xuống dưới phần stock đang allocate cho Flash Sale đang `ACTIVE`.
- Optimistic lock `@Version` trên `ProductVariant` bảo vệ update đồng thời.

---

## 15. Flash Sale Purchase Limit

Phải phân biệt rõ **2 cơ chế**:

### Reservation TTL

- Dùng để **giới hạn thời gian giữ stock** của reservation/order chưa hoàn tất.
- Key: `reservation:{orderId}` (Redis).
- TTL mặc định: **300 giây (5 phút)**.
- Sau khi hết TTL → job `processExpiredReservations` cancel order + restore stock.

### Purchase Limit Lifecycle

- Dùng để **kiểm soát giới hạn mua của user** trong Flash Sale (vd: tối đa 2 sp/user/slot).
- Key: `flash_sale:user_limit:{slotId}:{userId}:{itemId}`.
- **Có lifecycle riêng** — sống đến khi slot ENDED, không bị xóa theo reservation TTL.

### Quy tắc

- **Không dùng** reservation TTL 300s làm lifetime của purchase limit.
- Mỗi reservation phải check purchase limit trước khi DECR stock.
- Rollback phải DECRBY purchase limit tương ứng.

---

## 16. Timeout Rollback

Khi reservation timeout/cancel phải trả lại **đúng số lượng đã reserve**.

```text
Sai:  INCRBY stock 1
Đúng: INCRBY stock {reservedQuantity}
```

- Nếu reserve 5 sản phẩm thì phải trả lại 5.
- Rollback phải **idempotent** (xem mục #34) để tránh trả stock nhiều lần.
- Dùng Redis marker `rollback:{orderId}:{itemId}` set khi rollback lần đầu, các lần sau skip.

### Lua script

File `src/main/resources/lua/rollback_stock.lua` thực hiện:
- Check marker idempotency.
- INCRBY `flash_sale:stock:{itemId}` đúng `reservedQuantity`.
- DECRBY `flash_sale:user_limit:{slotId}:{userId}:{itemId}` đúng `reservedQuantity`.
- SET marker `rollback:{orderId}:{itemId}` với TTL 1 ngày.

---

## 17. Redis → Database Compensation

Redis decrement thành công nhưng DB order creation thất bại có thể gây inconsistency.

### Flow happy

```text
Redis reserve (Lua atomic)
   ↓
Create DB Order (PENDING_PAYMENT)
   ↓
Success
```

### Flow fail

```text
Redis reserve
   ↓
DB fail (constraint / timeout / deadlock)
   ↓
Compensate Redis (rollback_stock.lua)
   ↓
Restore exact reserved quantity
```

### Quy tắc

- **Không triển khai Saga hoặc 2PC** cho scope hiện tại.
- Try-Catch compensation **phải tránh double compensation** (dùng marker idempotency mục #16).
- Cả 2 phía (Redis + DB) đều phải có marker riêng:
  - Redis: `rollback:{orderId}:{itemId}` (TTL 1 ngày).
  - DB: cột `order.status` transition hợp lệ (`PENDING_PAYMENT` → `CANCELLED_TIMEOUT` chỉ 1 lần).

---

## 18. Flash Sale Item → Order Item Consistency

**Không tin tưởng hoàn toàn các ID Flash Sale do client gửi.**

Service nên load:

```text
FlashSaleItem
```

từ database rồi derive:

```text
slot_id
variant_id
product_id
price (flash_sale_price)
```

từ `FlashSaleItem` (không từ client).

### Tránh

```text
client gửi: { slot_id, flash_sale_item_id, variant_id } không nhất quán
```

### Quy tắc

- Client chỉ gửi `flash_sale_item_id` (hoặc `slot_id + item_id`).
- Service load `FlashSaleItem` rồi lấy `variant_id` từ đó.
- Validate slot đang `ACTIVE` và nằm trong `[start_time, end_time]`.
- Không over-engineer bằng composite FK nếu Service validation đủ cho scope.

---

## 19. Flash Sale Lifecycle

Các operation quan trọng nên **idempotent** (xem mục #34):

- Pre-warm Redis.
- Timeout rollback.
- Sale-end settlement.
- Payment callback.

### Invariants

1. `0 <= available_stock <= allocated_stock`.
2. `allocated_stock` không vượt stock có thể allocate.
3. Seller không phá allocated stock khi cập nhật base stock.
4. Timeout trả đúng reserved quantity.
5. Sale-end settlement chỉ tạo một hiệu ứng (dùng `slot.status` transition + job lock).
6. Pre-warm không nhân đôi stock Redis (dùng `SETNX`).
7. Redis/DB failure có compensation phù hợp (mục #17).
8. Purchase limit có lifecycle riêng với reservation TTL (mục #15).

### Job chạy nền

| Job | Cadence | Trách nhiệm |
|---|---|---|
| `processExpiredReservations` | 15s | Quét `Order` `PENDING_PAYMENT` quá 300s, chuyển `CANCELLED_TIMEOUT`, hoàn trả Redis + DB |
| `processEndedSlotsAndReturnUnsoldStock` | 30s | Đóng phiên hết hạn, hoàn trả `available_stock` về `product_variants.stock_quantity` |

---

## 20. Payment

### Invariant

- Một `Order` **không được có nhiều payment `SUCCESS`**.
- Partial unique index:

  ```sql
  CREATE UNIQUE INDEX uq_one_success_payment_per_order
  ON payments(order_id)
  WHERE status = 'SUCCESS';
  ```

- `transaction_code` phải **UNIQUE** (constraint DB).

### Quy tắc

- Payment callback **phải idempotent** (mục #34).
- Service nhận callback → check `payment.status` hiện tại:
  - Nếu đã `SUCCESS` → return ngay, KHÔNG xử lý lại.
  - Nếu `PENDING` → chuyển `SUCCESS` + trigger cộng wallet/giải phóng stock (nếu có).
- Tất cả transition status đều ghi log + audit.

### Endpoint (placeholder, sẽ cập nhật khi implement)

```text
POST /api/v1/payments/{orderCode}        # Buyer tạo payment intent
POST /api/v1/payments/callback           # Gateway callback (public, signed)
GET  /api/v1/payments/{id}               # Buyer xem payment
```

> Lưu ý: hiện chưa có `PaymentController` — module `payment/controller/` chỉ có `.gitkeep`. Khi implement phải tuân thủ rule trên và bổ sung API list vào mục #46.

---

## 21. Wallet

### Audit

- Wallet transaction phải có khả năng audit.
- **Không sửa/xóa** lịch sử giao dịch tùy tiện.

### Field nhất quán

Mọi `WalletTransaction` phải có:

```text
balance_after    # BigDecimal, snapshot sau khi cộng/trừ
amount           # BigDecimal, dương = cộng, âm = trừ
transaction_type # enum: TOPUP, WITHDRAW, ORDER_PAYMENT, ORDER_REFUND, COMMISSION, ...
order_id         # nullable, FK orders
payment_id       # nullable, FK payments
status           # SUCCESS / PENDING / FAILED
created_at       # timestamp
```

### Quy tắc

- Mỗi transaction phải đi qua 1 `WalletService` method duy nhất (single entry point).
- `@Transactional` đảm bảo `wallet.balance` update + `wallet_transactions` insert atomic.
- Kiểm tra `balance >= amount` trước khi trừ.

### Endpoint (placeholder)

```text
GET  /api/v1/wallets/me                  # Xem ví của tôi
GET  /api/v1/wallets/me/transactions     # Lịch sử (paging)
POST /api/v1/wallets/me/topup           # Tạo yêu cầu nạp
POST /api/v1/wallets/me/withdraw        # Tạo yêu cầu rút
```

---

## 22. Voucher

### Invariant store

```text
vouchers.store_id IS NOT NULL
   →  checkout phải kiểm tra voucher.store_id == order.store_id
vouchers.store_id IS NULL
   →  platform voucher (áp dụng mọi store)
```

### Invariant usage

```text
used_quantity <= total_quantity
```

CHECK constraint tương ứng ở DB.

### Voucher percentage

```text
discount_value <= 100
```

CHECK constraint ở DB.

### Quy tắc

- Áp dụng voucher ở **service checkout** (sau khi split cart theo store, mục #11).
- Mỗi Order chỉ áp dụng tối đa 1 store-voucher + 1 platform-voucher.
- Voucher usage ghi vào `voucher_usages` (FK `voucher_id`, `order_id`, `user_id`).
- Rollback khi order cancel: xóa `voucher_usage` + decrement `used_quantity`.

### Endpoint (đã có)

```text
GET    /api/v1/vouchers
POST   /api/v1/vouchers                  # Buyer claim voucher vào ví
GET    /api/v1/seller/vouchers           # Seller quản lý voucher store
POST   /api/v1/seller/vouchers
PUT    /api/v1/seller/vouchers/{id}
DELETE /api/v1/seller/vouchers/{id}
GET    /api/v1/admin/vouchers            # Admin quản lý
```

---

## 23. Order Snapshots

Giữ các historical snapshot trong `Order` / `OrderItem`:

```text
price_at_purchase       # BigDecimal
product_name            # String
variant_name            # String
recipient_name          # String
recipient_phone         # String
shipping_address        # String (snapshot full địa chỉ)
commission_rate         # BigDecimal
platform_fee            # BigDecimal
seller_amount           # BigDecimal
```

### Quy tắc

- Snapshot là **historical record**, không xem là lỗi 3NF chỉ vì dữ liệu có thể tồn tại ở bảng khác.
- Snapshot quan trọng **không nên thay đổi** sau khi Order được tạo.
- Cập nhật Order sau create chỉ nên đổi `status`, `tracking_code`, `cancel_reason` — không đổi snapshot giá/địa chỉ.
- Nếu user update address sau khi order đã tạo → KHÔNG ảnh hưởng order hiện tại.

---

## 24. Product Review

Khi tạo Review phải kiểm tra:

1. User sở hữu Order (`order.user_id == currentUserId`).
2. Order đã `COMPLETED` theo nghiệp vụ.
3. `order_item_id` thuộc Order của user.
4. Product của Review khớp Product trong OrderItem.
5. Một OrderItem không bị review nhiều lần (UNIQUE constraint trên `order_item_id`).

`product_id` có thể được giữ như **intentional denormalization / read optimization**.

### Endpoint (Subplan 3 implemented: GET public)

```text
POST   /api/v1/products/{productId}/reviews    # Authenticated (placeholder)
GET    /api/v1/products/{productId}/reviews    # Public, paging, filter by rating/hasMedia, ratingDistribution + mediaCount (Subplan 3)
GET    /api/v1/users/me/reviews                # Buyer xem review của mình (placeholder)
DELETE /api/v1/reviews/{id}                    # Owner hoặc Admin (placeholder)
```

> `review/controller/ProductReviewPublicController.java` đã implement GET public endpoint (Subplan 3). Review album ảnh dùng bảng `images` (owner_type=`REVIEW`) — không còn `product_reviews.image_urls JSONB` (đã xóa V4).

---

## 25. Image Module (Polymorphic)

Module `image/` quản lý ảnh cho **mọi entity** (User avatar, Store logo, Product gallery, Variant gallery, Review album) qua cơ chế **polymorphic FK**.

### Schema (V3)

```text
images (
  id BIGSERIAL PK,
  owner_type VARCHAR(32) NOT NULL,   -- enum: USER, STORE, PRODUCT, VARIANT, REVIEW
  owner_id   BIGINT      NOT NULL,
  public_id  VARCHAR(255) NOT NULL,  -- Cloudinary public_id
  secure_url TEXT NOT NULL,
  is_primary BOOLEAN NOT NULL DEFAULT false,
  display_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
)

-- Index:
CREATE INDEX idx_images_owner ON images(owner_type, owner_id);
CREATE UNIQUE INDEX uq_images_one_primary_per_owner
  ON images(owner_type, owner_id) WHERE is_primary = true;
```

### Invariants

- Một owner chỉ có **đúng 1** ảnh `is_primary = true` (partial unique index).
- `display_order >= 0`.
- Xóa owner (User/Store/Product/Variant/Review) phải cascade xóa ảnh liên quan (xử lý qua `image/listener` + `image/event`).
- Khi xóa 1 ảnh primary, tự động set ảnh có `display_order` nhỏ nhất còn lại làm primary.

### Ownership rule

- Mọi thao tác `GET / DELETE / PATCH (primary, order)` trên image phải check:

  ```text
  resolveImageOwner(image) == currentPrincipal
  ```

- Nếu không khớp → `IMAGE_OWNER_FORBIDDEN` (HTTP 403).
- Admin có thể force-delete (`forceDeleteImage`) cho moderation.

### Cloudinary

- Config qua `application.yaml`:
  ```yaml
  cloudinary:
    cloud-name: ${CLOUDINARY_CLOUD_NAME:}
    api-key:    ${CLOUDINARY_API_KEY:}
    api-secret: ${CLOUDINARY_API_SECRET:}
    folder:     ${CLOUDINARY_FOLDER:flash-sale-b2c/local}
  ```
- Folder mặc định: `flash-sale-b2c/{profile}`.
- Multipart limit: `max-file-size: 10MB`, `max-request-size: 50MB`.

### Migration legacy

- V4 đã drop 5 cột ảnh cũ: `users.avatar_url`, `stores.logo_url`, `products.image_url`, `product_variants.image_url`, `product_reviews.image_urls`.
- **Không thêm lại** các cột này — luôn dùng bảng `images`.

### Endpoint (đã có)

```text
POST   /api/v1/images?ownerType=...&ownerId=...   # Upload
GET    /api/v1/images?ownerType=...&ownerId=...   # List
PATCH  /api/v1/images/{id}/primary                 # Set primary (owner only)
PATCH  /api/v1/images/{id}/order                   # Set display_order (owner only)
DELETE /api/v1/images/{id}                         # Delete (owner only)
DELETE /api/v1/admin/images/{id}                   # Force delete (Admin)
```

---

## 26. Transaction

### Database

- Dùng `@Transactional` cho business operation cần atomicity ở database.
- Default propagation: `REQUIRED`.
- Read-only query method: `@Transactional(readOnly = true)`.
- Không đặt `@Transactional` ở Controller.

### Redis ≠ DB

```text
PostgreSQL transaction
≠
Redis transaction
```

- `@Transactional` **không rollback được Redis**.
- Nếu operation liên quan PostgreSQL + Redis, **phải có compensation/retry/idempotency** (mục #17, #34).
- Redis Lua script cung cấp atomicity cho phía Redis.

---

## 27. Concurrency

### Không dùng

```text
read stock
→ if stock > 0
→ decrement
```

trong high-concurrency Flash Sale.

### Ưu tiên

```text
Redis Lua Script
```

cho operation cần atomicity.

### Pluggable strategy

Module `flashsale/strategy/` hỗ trợ 2 strategy:

- `LuaScriptStockReservationStrategy` (mặc định).
- `RedissonLockStockReservationStrategy` (so sánh/benchmark).

Chọn qua property (vd: `flashsale.strategy=lua` / `redisson`).

### Quy tắc

- Không thêm distributed lock phức tạp nếu Redis/Lua đã giải quyết đúng vấn đề.
- Không dùng `synchronized` Java cho cross-instance state.
- DB lock (`@Lock(PESSIMISTIC_WRITE)`) chỉ dùng khi thật cần (vd: settle wallet 2 user cùng lúc).

---

## 28. N+1

### Phát hiện

- Bật `spring.jpa.show-sql: true` (profile `local`) để quan sát query.
- Test với data lớn (>1000 records).

### Giải pháp

- `@EntityGraph(attributePaths = {...})`.
- `JOIN FETCH` trong JPQL.
- Projection (interface-based DTO).
- `@BatchSize(size = 50)` hoặc `hibernate.batch_size: 50` (đã cấu hình).
- 2 queries thay vì 1 join phức tạp (khi collection lớn).

### Quy tắc

- **Không dùng** `EAGER` bừa bãi để che N+1.
- Mọi list API có quan hệ (Order → Items, Product → Variants, Cart → Items) **phải** test không N+1 trước khi merge.

---

## 29. Pagination

- List API có dữ dữ liệu lớn **phải** hỗ trợ pagination.
- Dùng `PageResponse<T>` chuẩn (mục #7).
- Mặc định: `page=0`, `size=20`, `MAX_PAGE_SIZE=100`.
- Sort mặc định: `createdAt DESC` (hoặc theo nghiệp vụ từng entity).
- **Không load** toàn bộ bảng vào memory nếu không cần.
- Aggregate (count, sum) dùng DB function, không load + sum ở Java.

---

## 30. Security

### Không hard-code

- JWT secret.
- Database password.
- Redis password.
- Payment secret / API key.
- Cloudinary secret.

Dùng **environment variables** hoặc **secret configuration** (qua `application-{profile}.yaml`).

### Quy tắc

- JWT authentication/authorization xử lý tại **Security layer** (`SecurityConfig` + `JwtAuthenticationFilter`).
- **Không bypass Security** chỉ để test feature.
- 401 cho token invalid/expired; 403 cho thiếu quyền.
- Mỗi `@PreAuthorize` phải reference **permission code** (`order:create`, `product:read`) hoặc **role** (`hasRole('SELLER')`).

### Auth endpoint

- `/api/v1/auth/**` → public.
- `/api/v1/admin/**` → `hasRole('ADMIN')`.
- `/api/v1/seller/**` → `hasRole('SELLER')`.
- `/api/v1/**` (còn lại) → authenticated.
- WebSocket: `/ws/**` → `permitAll()` (auth trong `HandshakeAuthInterceptor`).

---

## 31. MapStruct + Lombok

### MapStruct

```java
@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductResponse toResponse(Product entity);
    List<ProductResponse> toResponseList(List<Product> entities);
}
```

- Mapper **phải được Spring quản lý** (`componentModel = "spring"`).
- Dùng `@Mapping(target = "x", source = "y")` cho field khác tên.
- Dùng `nullValuePropertyMappingStrategy = IGNORE` cho update DTO.

### Lombok

- `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j`.
- **Không dùng** `@Data` cho entity JPA (gây vấn đề với `@EqualsAndHashCode` + lazy loading).
- Entity dùng `@Getter` riêng + `@Setter(AccessLevel.PROTECTED)` cho field có thể thay đổi.

### Annotation processing

- Lombok + MapStruct annotation processor phải cấu hình đúng trong `build.gradle.kts`:

  ```kotlin
  plugins {
      java
      id("org.springframework.boot") version "..."
      id("io.spring.dependency-management") version "..."
      id("org.mapstruct") version "..."
  }

  dependencies {
      implementation("org.mapstruct:mapstruct:...")
      annotationProcessor("org.mapstruct:mapstruct-processor:...")
      annotationProcessor("org.projectlombok:lombok:...")
      // Nếu Lombok trước MapStruct:
      annotationProcessor("org.projectlombok:lombok-mapstruct-binding:...")
  }
  ```

- **Không commit** generated files (`target/` đã có trong `.gitignore`).

---

## 32. Validation

### API input

Dùng Jakarta Validation khi phù hợp:

```java
@NotNull
@NotBlank
@NotEmpty
@Positive
@PositiveOrZero
@Negative
@Min(0)  @Max(100)
@Size(min = 1, max = 255)
@Email
@Pattern(regexp = "...")
@Valid   // cho nested object
```

- Validation annotation trên DTO request.
- `@Valid` trên `@RequestBody` để trigger.
- Field-level error trả trong `data` của `ApiResponse` (mục #7).

### Business validation

- Validation phức tạp (multi-table, multi-step) nằm trong **Service**, throw `BusinessException(*ErrorCode)`.
- Không throw `RuntimeException` thuần — luôn kèm `ErrorCode`.

---

## 33. API Convention

REST API sử dụng HTTP method đúng mục đích:

| Method | Mục đích | Idempotent |
|---|---|---|
| `GET` | Đọc resource | Yes |
| `POST` | Tạo mới / action không idempotent | No |
| `PUT` | Replace toàn bộ resource | Yes |
| `PATCH` | Partial update | No* |
| `DELETE` | Xóa resource | Yes |

\* PATCH idempotent nếu field update không phụ thuộc state hiện tại.

### Quy tắc

- **Không dùng POST** cho mọi operation nếu HTTP semantic có method phù hợp.
- URL resource dùng danh từ số nhiều: `/api/v1/orders`, `/api/v1/products`.
- Action đặc biệt dùng sub-resource: `/api/v1/orders/{id}/cancel`.
- Versioning qua URL prefix `/api/v1/`.

---

## 34. Idempotency Convention

Áp dụng cho mọi operation có retry / có thể bị client gọi lại do network.

### Khi nào BẮT BUỘC

- Payment callback.
- Stock rollback.
- Flash Sale settlement.
- Redis pre-warm.
- Order creation trong flow có retry.
- Flash Sale reservation.

### HTTP header

```
Idempotency-Key: <uuid-v4 hoặc client-gen string, max 255 chars>
```

- Client **phải** gửi kèm `Idempotency-Key` cho endpoint hỗ trợ.
- Server phải reject 400 nếu thiếu key ở endpoint yêu cầu.

### Storage

- Redis key: `idempotency:{key}`.
- Value: JSON snapshot gồm:
  ```json
  {
    "userId": 123,
    "requestHash": "sha256-of-body",
    "responseSnapshot": { ... },
    "status": "IN_PROGRESS | COMPLETED | FAILED",
    "expiresAt": "2026-10-08T10:00:00Z"
  }
  ```
- TTL: **24 giờ**.

### Flow

```text
1. Nhận request + Idempotency-Key
2. SETNX idempotency:{key} = { status: IN_PROGRESS, ... } TTL 24h
   - Nếu đã tồn tại:
     - Lấy record cũ
     - Nếu COMPLETED → trả responseSnapshot
     - Nếu IN_PROGRESS → trả 409 IDEMPOTENCY_IN_PROGRESS
     - Nếu FAILED → cho retry, set lại IN_PROGRESS
3. Xử lý business
4. Update record: status = COMPLETED, responseSnapshot = response
5. Trả response cho client
```

### Quy tắc

- `requestHash` phải match — nếu client gọi lại với body khác → 422 `IDEMPOTENCY_KEY_MISMATCH`.
- Compensation (mục #17) **không dùng** `Idempotency-Key` của client, mà dùng marker riêng (vd `rollback:{orderId}:{itemId}`).

---

## 35. Background Jobs / Schedulers

### Quy tắc chung

- Có mục đích rõ ràng.
- Có **cơ chế tránh duplicate** nếu cần.
- Có log (start, end, số record xử lý, error).
- Có **idempotency** (mục #34).
- **Không âm thầm** thay đổi dữ liệu ngoài business rule.

### Multi-instance safety

- Hiện tại chưa dùng ShedLock (verified: codebase không có `@SchedulerLock` / `shedlock`).
- Mỗi job **phải tự đảm bảo idempotency** để an toàn khi chạy 2+ instance.
- Khi scale horizontal, **phải** thêm ShedLock hoặc dùng DB row lock (vd: `SELECT ... FOR UPDATE SKIP LOCKED`).

### Job hiện tại

| Job | Cadence | Module | Trách nhiệm |
|---|---|---|---|
| `processExpiredReservations` | 15s | `flashsale` | Quét Order `PENDING_PAYMENT` quá 300s, chuyển `CANCELLED_TIMEOUT` |
| `processEndedSlotsAndReturnUnsoldStock` | 30s | `flashsale` | Đóng phiên ENDED, hoàn trả `available_stock` về `product_variants.stock_quantity` |
| (image cleanup) | daily | `image` | Xóa ảnh orphaned (chưa có owner) — placeholder |

> Tất cả scheduler method dùng `@Scheduled(fixedDelayString = "${...}")` để cadence configurable.

### Cách viết

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleExpirationScheduler {

    @Scheduled(fixedDelayString = "${flashsale.scheduler.expiration-delay-ms:15000}")
    public void processExpiredReservations() {
        long start = System.currentTimeMillis();
        int n = service.expirePendingOrders();
        log.info("processExpiredReservations done, processed={}, elapsedMs={}", n, System.currentTimeMillis() - start);
    }
}
```

---

## 36. Realtime WebSocket (STOMP + SockJS)

### Tổng quan

Realtime channel phục vụ push event cho client (stock update, slot status, order result). **Không thay thế HTTP REST** — chỉ bổ sung lớp notification.

**Stack**: Spring WebSocket + STOMP + SockJS.

**Endpoint handshake**: `ws://host/ws` (SockJS fallback: `http://host/ws/...`).

### Cấu hình chính

| File | Vai trò |
|---|---|
| `config/realtime/WebSocketConfig.java` | Broker prefix `/topic`, `/user`; heartbeat 10s; `ObjectProvider<TaskScheduler>` |
| `config/realtime/HandshakeAuthInterceptor.java` | Đọc JWT từ `?token=` query param |
| `config/realtime/WebSocketAuthConfig.java` | Reload authorities qua `UserDetailsService` tại CONNECT |
| `config/realtime/WsSessionEventListener.java` | Log lifecycle events + đếm active sessions |
| `config/CorsConfig.java` | CORS mapping `/ws/**` |
| `config/SecurityConfig.java` | `permitAll()` cho `/ws/**` (auth xử lý trong interceptor) |

> **Không tạo** `WebSocketSchedulerConfig.java` riêng. **Không tạo** `application-websocket.yaml` riêng — config trong `application.yaml` chung.

### Auth flow (browser → server)

```text
Browser:  new WebSocket('/ws?token=<JWT>')
           │
           ▼
HandshakeAuthInterceptor
           │ validate JWT, set Principal vào session attributes
           ▼
WebSocket connected → STOMP CONNECT frame
           │
           ▼
WebSocketAuthConfig (ChannelInterceptor preSend)
           │ reload UserDetails, gán vào STOMP accessor
           ▼
Authenticated session — sẵn sàng SUBSCRIBE / SEND
```

Lý do dùng query param thay vì header: browser không cho set `Authorization` khi upgrade WebSocket.

### WS Destinations (single source of truth)

Mọi URL pattern **phải build qua class `flashsale.realtime.WsDestinations`**. Không hard-code URL rải rác trong service.

#### Public topic (broadcast, ai cũng subscribe được):

| Method | Destination | Mô tả |
|---|---|---|
| `itemStock(itemId)` | `/topic/flash-sale/item/{itemId}/stock` | Stock realtime cho 1 SKU |
| `slotStockUpdate(slotId)` | `/topic/flash-sale/slot/{slotId}/stock-update` | Stock thay đổi trong slot |
| `slotStatus(slotId)` | `/topic/flash-sale/slot/{slotId}/status` | Slot chuyển trạng thái |

#### Private queue (per-user, Spring prefix `/user/{username}`):

| Method | Destination (resolved) | Mô tả |
|---|---|---|
| `reservationResult()` | `/user/{username}/queue/flash-sale/reservation-result` | Kết quả reservation riêng user |
| `orderUpdates(orderCode)` | `/user/{username}/queue/flash-sale/orders/{orderCode}/updates` | Update riêng cho 1 đơn |

### Payload format

Tất cả event đều wrap trong `ApiResponse<T>` chung của project. Body payload là `FlashSaleWsEvent`:

```java
FlashSaleWsEvent {
  EventType eventType;          // STOCK_DECREMENTED, ORDER_RESERVED, ...
  Long slotId, flashSaleItemId, orderId, userId;
  Integer availableStock, allocatedStock, quantity, restoredQuantity;
  Long totalAmount;
  String orderCode, slotStatus;
  Instant expiresAt, occurredAt;
}

enum EventType {
  STOCK_DECREMENTED,
  STOCK_RESTORED,
  STOCK_RETURNED_UNSOLD,
  SLOT_ACTIVATED,
  SLOT_CLOSED,
  ORDER_RESERVED,
  ORDER_CANCELLED_TIMEOUT
}
```

### Broadcaster layer

`FlashSaleWsBroadcaster` là service **duy nhất** gọi `SimpMessagingTemplate`. Mọi service khác muốn push realtime phải inject broadcaster, **không** gọi trực tiếp `convertAndSend`.

Các method chính:
- `broadcastStockUpdate(slotId, itemId, availableStock)`
- `broadcastStockRestored(slotId, itemId, availableStock, restoredQuantity)`
- `broadcastUnsoldStockReturned(slotId, itemId, availableStock)`
- `broadcastSlotStatus(slotId, newStatus)`
- `sendReservationResultToUser(username, event)`
- `sendOrderCancelledToUser(username, event)`

### Quy tắc khi dùng WS trong business flow

- WS **chỉ là thông báo**. **Không** để client quyết định logic dựa trên WS message.
- Stock đếm vẫn lấy từ Redis (`GET flash_sale:stock:{itemId}`) làm source of truth.
- DB **không phụ thuộc** WS — nếu WS message bị miss (network), client vẫn get đúng stock qua REST.
- **Không queue message** khi user offline (`SimpMessagingTemplate` mặc định).
- Heartbeat 10s cả 2 chiều, dedicated scheduler pool (qua `messageBrokerTaskScheduler` mặc định).

---

## 37. CORS & Security Wiring

### CORS

- `CorsConfig` mapping `/ws/**` cho WebSocket.
- API REST `/api/v1/**` cũng cần CORS nếu frontend gọi cross-origin.
- Whitelist origin qua property `app.cors.allowed-origins` (mặc định `http://localhost:3000` cho dev).

### Public route

- `/api/v1/auth/**`
- `/api/v1/products/**` (public)
- `/api/v1/flash-sales/slots` (public)
- `/api/v1/categories/**` (public)
- `/api/v1/stores/{id}` (public)
- `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`
- `/actuator/health`
- `/ws/**`

### Stateless

- `SessionCreationPolicy.STATELESS`.
- `JwtAuthenticationFilter` đặt **trước** `UsernamePasswordAuthenticationFilter`.
- Custom `AuthenticationEntryPoint` trả `ApiResponse` 401.
- Custom `AccessDeniedHandler` trả `ApiResponse` 403.

### Filter chain order

```text
1. CorsFilter
2. JwtAuthenticationFilter
3. UsernamePasswordAuthenticationFilter (disabled)
4. AuthorizationFilter
```

---

## 38. Error Code Convention

Xem chi tiết mục #7. Tóm tắt:

| HTTP | ErrorCode status | Ý nghĩa |
|---|---|---|
| 400 | `*_BAD_REQUEST` | Validation fail |
| 401 | `*_UNAUTHORIZED` / `AUTH_INVALID_TOKEN` | Token invalid/expired |
| 403 | `*_FORBIDDEN` / `*_ACCESS_DENIED` | Không đủ quyền |
| 404 | `*_NOT_FOUND` | Resource không tồn tại |
| 409 | `*_CONFLICT` / `*_ALREADY_EXISTS` | State conflict |
| 422 | `*_UNPROCESSABLE` | Business rule fail |
| 500 | `*_INTERNAL_ERROR` | Server error (không expose stack) |

### Format message

- `message` field trong `ApiResponse` là **tiếng Việt**, dùng để hiển thị cho user.
- Có thể dùng `MessageSource` cho i18n sau, hiện tại hard-code tiếng Việt trong `*ErrorCode` enum.
- Error code string format: `MODULE_REASON` (UPPER_SNAKE).

---

## 39. Build & Toolchain

### Java 25 (xem mục #4)

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

### Gradle

- Wrapper: `./gradlew` (đã commit `gradle/wrapper/gradle-wrapper.jar` + `.properties`).
- Các task thường dùng:

  ```cmd
  gradlew compileJava        # nhanh, không test
  gradlew test               # chỉ test
  gradlew build              # full build + test
  gradlew bootRun            # chạy app với profile active
  gradlew bootJar            # build jar
  ```

### Lombok + MapStruct (xem mục #31)

### Profile

- `local` — dev (mặc định khi không set env).
- `prod` — production.
- Active qua `SPRING_PROFILES_ACTIVE` env var hoặc `spring.profiles.active` trong `application.yaml`.

### Environment variables bắt buộc cho `local`

```text
JWT_SECRET                     # >= 32 ký tự
CLOUDINARY_CLOUD_NAME
CLOUDINARY_API_KEY
CLOUDINARY_API_SECRET
DATASOURCE_URL                 # jdbc:postgresql://...
DATASOURCE_USERNAME
DATASOURCE_PASSWORD
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD                 # nếu có
```

`application-local.yaml.example` là template — copy thành `application-local.yaml` và fill giá trị thật.

### Không commit

- `.env`
- `application-local.yaml` (có thể chứa secret thật)
- `build/`, `target/`
- IDE files: `.idea/`, `*.iml`, `.vscode/`
- `*.log`

---

## 40. Testing

Sau mỗi task:

1. Build project.
2. Chạy test liên quan.
3. Nếu liên quan DB, kiểm tra migration.
4. Nếu liên quan Redis/concurrency, kiểm tra atomicity và edge cases.
5. Review diff.

### BẮT BUỘC

Trước build/test/run:

```cmd
java25
java -version
```

Sau đó:

```cmd
gradlew clean build
```

hoặc:

```cmd
gradlew test
```

Nếu `java -version` không phải Java 25 thì **dừng và báo lỗi**.

### Test layer

- **Unit test**: Service logic, không cần Spring context. Dùng Mockito.
- **Integration test**: `@SpringBootTest` + Testcontainers (Postgres + Redis) cho flow DB/Redis.
- **Concurrency test**: Test Lua script với nhiều thread mô phỏng race condition.
- **WebSocket test**: `@SpringBootTest` + `WebSocketStompClient` cho event flow.

### Quy tắc

- Mỗi bug fix phải có test reproduce trước, sau đó fix.
- Mỗi feature mới phải có unit test cơ bản.
- Coverage mục tiêu: **>= 60%** cho service layer, **>= 40%** tổng.

### Bắt buộc viết test

Mọi task implement feature / fix bug **PHẢI** kèm test trong cùng PR (commit riêng nếu cần):

- **Scope bắt buộc**: service layer của module đang thay đổi.
  - Controller không bắt buộc (có thể mock service) trừ khi task chủ yếu ở controller layer.
  - Repository không bắt buộc test riêng (đã được cover qua integration test).
- **Loại test**:
  - Unit test cho business logic thuần (validation, ownership check, state machine).
  - Integration test cho flow có **read/write DB**, **Redis**, hoặc **external client**.
- **Quy tắc phạm vi**:
  - Cover happy path + ít nhất 1 edge case (boundary, null/empty, invalid input).
  - Test cho error path khi service throw `BusinessException(*ErrorCode)`.
- **Task chỉ đổi config / docs / convention không có code nghiệp vụ** → KHÔNG bắt buộc viết test (vd: refactor không đổi behavior, chỉnh sửa migration, đổi tên hằng).
- Báo cáo cuối task (mục #45) phải ghi rõ:
  - Số test thêm/sửa.
  - Kết quả `gradlew test` (passed/failed + tổng số).
  - Coverage delta nếu có công cụ đo (Jacoco / IntelliJ).

### Rollback DB sau integration test

Integration test có thể làm thay đổi dữ liệu DB thật (qua Testcontainers hoặc H2 embedded). **PHẢI** đảm bảo DB được đưa về trạng thái ban đầu sau khi test xong.

#### Cơ chế mặc định (bắt buộc theo thứ tự ưu tiên)

1. **`@Transactional` trên test method** (Spring Test).
 - Spring sẽ tự rollback sau khi method kết thúc (kể cả khi test pass hay fail).
 - Phù hợp khi test chỉ thực hiện write trong transaction duy nhất.
 - **KHÔNG dùng** nếu test cần commit thật (vd: test trigger, test async job, test scheduler).

2. **`@Sql` script cleanup** (cuối method).
 - Dùng khi `@Transactional` không phù hợp (vd: cần commit để verify trigger / constraint / scheduler).
 - Đặt script rollback (`TRUNCATE TABLE ...`, `DELETE FROM ...`) trong `src/test/resources/cleanup.sql` và reference bằng `@Sql(scripts = "/cleanup.sql", executionPhase = AFTER_TEST_METHOD)`.

3. **Testcontainers** (mặc định cho integration test backend).
 - Mỗi test class dùng container Postgres riêng (qua `@Container` + `@Testcontainers`).
 - Container bị huỷ sau khi test class kết thúc → DB sạch cho class sau.
 - Tận dụng `@DynamicPropertySource` để wire `spring.datasource.*` tới container.

4. **Manual cleanup trong `@AfterEach`** (chỉ khi 3 cách trên không khả thi).
 - Dùng `JdbcTemplate` hoặc `TestEntityManager` để xóa data inserted.
 - **KHÔNG khuyến khích** vì dễ sót và không cover được lỗi giữa chừng.

#### Quy tắc chung

- Test **không được** để lại data rác trong DB sau khi chạy.
- Test **không được** phụ thuộc vào data có sẵn trong DB thật (dev / staging).
- Nếu test phải dùng data seed cố định → seed trong `@BeforeEach` + cleanup trong `@AfterEach`.
- Khi test fail giữa chừng → cleanup **vẫn phải chạy** (JUnit đảm bảo `@AfterEach` luôn chạy, kể cả khi `@Before` fail).
- Báo cáo cuối task ghi rõ cơ chế rollback nào đang dùng cho integration test của task.

#### Ví dụ template

```java
@DataJpaTest
@Testcontainers
class FlashSaleItemRepositoryIT {
 @Container
 static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

 @DynamicPropertySource
 static void datasourceProps(DynamicPropertyRegistry registry) {
 registry.add("spring.datasource.url", postgres::getJdbcUrl);
 registry.add("spring.datasource.username", postgres::getUsername);
 registry.add("spring.datasource.password", postgres::getPassword);
 }

 @Autowired FlashSaleRepository repo;

 @Test
 void findActiveItems_shouldReturnOnlyActive() {
 // seed + assert
 // container tự huỷ sau test class → DB sạch
 }
}
```

---

## 41. Git Workflow

### Branching model

```text
main       # production-ready, được bảo vệ, chỉ merge qua PR
dev        # integration branch, base cho mọi branch mới
feature/<shorter-desc>    # vd: feature/order-module, feature/flashsale-reservation
fix/<shorter-desc>        # vd: fix/auth-401-loop, fix/stock-rollback-quantity
hotfix/<shorter-desc>     # vd: hotfix/payment-callback-timeout — sửa khẩn cấp, branch từ main, merge cả main + dev
refactor/<shorter-desc>   # vd: refactor/extract-flashsale-order-port
chore/<shorter-desc>      # vd: chore/init-project-structure
docs/<shorter-desc>       # vd: docs/sync-api-document.md
```

Khi nào dùng `hotfix/` thay cho `fix/`:

- `fix/<x>` — bug thường, làm trong sprint, branch từ `dev`, merge vào `dev`.
- `hotfix/<x>` — bug **khẩn cấp** trên production, branch từ `main`, merge thẳng vào `main` rồi cherry-pick / merge ngược về `dev`.

### Quy trình BẮT BUỘC trước mỗi task

```text
1. git checkout dev
2. git pull origin dev
3. git checkout -b <type>/<shorter-desc>      # tạo nhánh mới từ dev
4. ... làm task ...
5. git add .                                   # stage toàn bộ thay đổi
6. git commit -m "<type>: <mô tả ngắn>"
7. DỪNG — KHÔNG push (xem mục "Không push" bên dưới)
```

> **Lưu ý**: Mỗi `shorter-desc` phải mô tả đúng tính năng đang làm, theo chuẩn đặt tên nhánh của doanh nghiệp. Không đặt tên chung chung kiểu `feature/new`, `fix/bug`, `chore/update`.

### Commit message

Ngắn gọn, đúng thay đổi, conventional commit (tiếng Anh):

```text
feat: implement flash sale reservation
fix: restore exact reserved stock on timeout
chore: initialize project structure
docs: update API endpoint for image module
refactor: extract FlashSaleOrderPort interface
hotfix: patch payment callback signature mismatch
```

- 1 commit = 1 concern (không gộp `feat` + `fix` trong cùng commit).
- Nếu có issue / task ID: `feat: implement cart (#12)`.
- Body (nếu cần) giải thích **tại sao**, không lặp lại **cái gì**.

### Quy tắc commit & add

- Dùng `git add .` để đưa toàn bộ file đã sửa vào staging area, sau đó commit.
- KHÔNG cần (và không khuyến khích) `git add <file>` thủ công từng file.
- Commit CHỈ những gì đã thực sự thay đổi trong task hiện tại — không commit file lạ (vd file generated, file tạm).

### KHÔNG push

- Agent **TUYỆT ĐỐI KHÔNG** chạy `git push` dưới mọi hình thức (`git push`, `git push origin`, `git push -u`, `git push --force`, ...).
- Việc push lên GitHub là của **người dùng** — đây là quy tắc bắt buộc.
- Sau khi commit xong → báo cáo cho user (theo format mục #45), user tự quyết định push & tạo PR / merge theo workflow của họ.
- Nếu user yêu cầu agent push → agent **vẫn từ chối** và nhắc lại rule này, trừ khi user gỡ rule rõ ràng.

### Conflict resolution

Khi `git pull`, `git merge`, hoặc `git rebase` xảy ra conflict:

1. Agent chạy `git status` để liệt kê file bị conflict.
2. Agent **KHÔNG tự ý** resolve bằng:
   - `git checkout --theirs <path>`
   - `git checkout --ours <path>`
   - `git add <path>` trước khi user duyệt
   - Sửa nội dung file conflict mà chưa hỏi user
3. Agent **đọc từng file conflict**, tóm tắt cho user:
   - Dòng nào đang bị conflict.
   - Hai phiên bản (local vs remote) khác nhau điểm nào.
   - Hệ quả nếu giữ theirs / ours / kết hợp.
4. Agent dùng `AskQuestion` để user chọn hướng xử lý:
   - Giữ phiên bản của mình (ours).
   - Giữ phiên bản từ remote (theirs).
   - Kết hợp thủ công (agent sẽ trình bày diff để user chỉnh).
5. Sau khi user duyệt → agent mới sửa file + `git add .` + commit.

### Không commit

- `.env`
- `application-local.yaml` (chỉ commit `.example`)
- secret, password, API key
- IDE files không cần thiết
- build output (`build/`, `target/`)

### PR

- 1 PR = 1 concern.
- Mô tả ngắn gọn: thay đổi gì, tại sao, ảnh hưởng gì.
- Nếu có migration: ghi rõ version + ảnh hưởng rollback.
- Nếu có breaking change API: ghi rõ trong PR body.

---

## 42. No Premature Over-Engineering

Không tự ý thêm:

- Microservices.
- Kafka.
- Saga.
- 2PC.
- CQRS.
- Event Sourcing.
- Kubernetes.
- Distributed Lock phức tạp (ngoài Redisson nếu đã có).
- Message broker.
- Parent Order.
- Seller Order.
- Service mesh.

nếu chưa có requirement rõ ràng.

Mục tiêu là hệ thống Flash Sale B2C có kiến trúc rõ ràng, đúng nghiệp vụ và triển khai/test được trong scope đồ án.

---

## 43. Cross-Repo Layout

Workspace hiện có 3 repo:

| Repo | Path | Vai trò |
|---|---|---|
| `flash-sale-b2c-UTC2` | `d:\flash-sale-b2c-UTC2` | **Spring Boot backend** (repo này) |
| `flash-sale-b2c` | `d:\flash-sale-b2c` | **Next.js frontend** (consume API backend) |
| `deploy-b2c-utc2` | `d:\deploy-b2c-utc2` | **Docker compose, nginx, scripts deploy** (multi-service orchestration) |

### Quy tắc

- Repo này **chỉ chứa** backend Java + config + SQL + lua script.
- Không sửa code frontend trong repo backend.
- Không commit docker-compose / nginx vào repo backend (đã có repo deploy).
- File `.env` quản lý riêng ở repo deploy.
- Nếu cần thay đổi API contract → sửa `docs/api/api-document.md` (sau khi user duyệt) + báo team frontend.

### Deploy repo (`deploy-b2c-utc2`)

Các file đáng chú ý:
- `docker-compose.yml` — orchestrate backend, frontend, postgres, redis, nginx.
- `docker/backend.Dockerfile` — build image backend.
- `docker/frontend.Dockerfile` — build image frontend.
- `nginx/default.conf` — reverse proxy `/api` → backend, `/` → frontend, `/ws` → backend WS.
- `scripts/check-health.sh` — health check sau deploy.
- `scripts/deploy.sh` — entry point deploy.

---

## 44. Task Execution Checklist

### Understanding
- [ ] Đã đọc requirement liên quan.
- [ ] Đã đọc docs liên quan.
- [ ] Đã đọc phần `AGENTS.md` liên quan.
- [ ] Đã kiểm tra code hiện tại (không chỉ tin docs).

### Implementation
- [ ] Đúng module.
- [ ] Đúng layer.
- [ ] Không business logic trong Controller.
- [ ] Không business logic trong Repository.
- [ ] Mapper đúng convention.
- [ ] Error code đúng prefix module.
- [ ] Nếu cần WS → qua `FlashSaleWsBroadcaster` (mục #36).
- [ ] Nếu cần idempotency → theo mục #34.

### Database
- [ ] Constraint đúng (FK, UNIQUE, NOT NULL, CHECK, Index).
- [ ] Migration mới nếu cần (V{n}__...sql).
- [ ] Không sửa migration cũ.
- [ ] Không sửa `docs/`.

### Flash Sale
- [ ] Redis operation atomic (Lua).
- [ ] Reservation TTL tách purchase limit.
- [ ] Rollback trả đúng quantity (compensation idempotent).
- [ ] Có compensation khi DB fail.
- [ ] Lifecycle operation idempotent.

### Image
- [ ] Owner resolve đúng (USER / STORE / PRODUCT / VARIANT / REVIEW).
- [ ] Ownership check trước mọi PATCH/DELETE.
- [ ] Có cascade xóa khi owner bị xóa.
- [ ] Chỉ 1 ảnh primary/owner.

### Security
- [ ] Không hard-code secret.
- [ ] Authorization đúng (role/permission).
- [ ] Ownership check đúng.
- [ ] Không bypass Security.

### Validation
- [ ] Input validation (Jakarta).
- [ ] Business validation (Service).
- [ ] Edge cases (null, empty, max boundary, concurrent).

### Documentation Sync
- [ ] Đã grep nội dung liên quan trong `docs/`.
- [ ] Đã tra **bảng Migration Impact Matrix (#48)** cho migration vừa tạo (nếu có).
- [ ] Đã báo user danh sách file docs cần sync.
- [ ] Đã update docs sau khi user duyệt (hoặc ghi rõ "chưa sync" trong PR).

### Build/Test
- [ ] Đã chạy `java25` trước build/test/run.
- [ ] `java -version` xác nhận Java 25.
- [ ] Build/test thành công hoặc đã báo rõ lỗi.
- [ ] Đã review diff.

---

## 45. Required Final Response Format

Sau khi hoàn thành task:

```text
## Changes
- ...

## Implementation
- ...

## Database
- ...

## Tests
- Java version: ...
- Command: ...
- Result: ...

## Notes
- ...
```

Nếu có lỗi:

```text
## Issues
- ...
```

### Quy tắc báo cáo

- **Không tuyên bố**:
  - "perfect"
  - "100% safe"
  - "0% overselling"
  - "guaranteed"
  - "10/10"
- Phải mô tả chính xác phạm vi đã kiểm tra, kết quả test và limitation còn lại.
- Nếu có thay đổi docs, ghi rõ file nào và lý do.

---

## 46. Auth, User, Store, Product, Flash Sale Endpoints

> Phần này là **API contract** cho frontend. Mọi thay đổi endpoint phải cập nhật `docs/api/api-document.md` (sau khi user duyệt) + mục này.

### 46.1 JWT Configuration & Security
- **Header format**: `Authorization: Bearer <access_token>`
- **Signing Algorithm**: HMAC-SHA256 (HS256).
- **Secret requirements**: Tối thiểu 32 ký tự (256-bit). Cấu hình qua `jwt.secret` (env `JWT_SECRET`).
- **Profile local**: secret 64 ký tự trong `application-local.yaml`.
- **Expiration**:
  - Access Token: `jwt.expiration-ms` (mặc định 3,600,000 ms = 1 giờ).
  - Refresh Token: `jwt.refresh-expiration-ms` (mặc định 604,800,000 ms = 7 ngày).
- **Stateless**: `JwtAuthenticationFilter`, 401 invalid/expired, 403 thiếu quyền.

### 46.2 RBAC Authorities
- **Role Authority**: `ROLE_<ROLE_NAME>` (vd `ROLE_BUYER`, `ROLE_SELLER`, `ROLE_ADMIN`).
- **Permission Authority**: Atomic code (vd `order:create`, `product:read`).
- **Feature Flag**: Chỉ cấp permission nếu `permissions.is_active == true`.
- **Principal**: `CustomUserDetails` chứa `AuthAccount` (id, username, email). Lấy ID qua `customUserDetails.getId()`.

### 46.3 Auth Endpoints (`/api/v1/auth`)

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | Đăng ký Buyer (`username`, `email`, `password`, `fullName`, `phoneNumber`) |
| `POST` | `/api/v1/auth/login` | Public | Login (`usernameOrEmail` + `password`) → access/refresh token + user info + roles |
| `POST` | `/api/v1/auth/refresh-token` | Public | Cấp lại accessToken từ refreshToken |
| `POST` | `/api/v1/auth/logout` | Public | Logout client-side |

### 46.4 User Profile (`/api/v1/users`)

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/users/me` | Authenticated | Profile hiện tại |
| `PUT` | `/api/v1/users/me` | Authenticated | Cập nhật profile (`fullName`, `phoneNumber`, `avatarUrl`) |
| `PUT` | `/api/v1/users/me/change-password` | Authenticated | Đổi mật khẩu (BCrypt check old) |

### 46.5 User Address (`/api/v1/users/addresses`)

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/users/addresses` | Authenticated | List address của tôi |
| `POST` | `/api/v1/users/addresses` | Authenticated | Tạo address |
| `GET` | `/api/v1/users/addresses/{id}` | Owner | Chi tiết (ownership check) |
| `PUT` | `/api/v1/users/addresses/{id}` | Owner | Cập nhật (ownership check) |
| `DELETE` | `/api/v1/users/addresses/{id}` | Owner | Xóa (ownership check) |
| `PATCH` | `/api/v1/users/addresses/{id}/default` | Owner | Set default (reset các cái khác) |

### 46.6 Store & Warehouse Address (`/api/v1/stores` & `/api/v1/admin/stores`)

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/stores` | Authenticated | Tạo Store (status `PENDING`, tier `NORMAL`, tạo Wallet 1-1) |
| `GET` | `/api/v1/stores/me` | Seller | Store của tôi |
| `PUT` | `/api/v1/stores/me` | Seller | Cập nhật Store |
| `GET` | `/api/v1/stores/sidebar/summary` | Seller | Tổng hợp metrics cho sidebar dashboard (totalProducts, activeFlashSales, pendingOrders, todayOrders, todayRevenue, monthRevenue, ratingAvg, ratingCount, lowStockProducts, storeStatus, tier) |
| `GET` | `/api/v1/stores/{id}` | Public | Store public |
| `PATCH` | `/api/v1/admin/stores/{id}/status` | Admin | Duyệt/Khóa. APPROVED → tự gán `ROLE_SELLER` |
| `GET` | `/api/v1/stores/me/addresses` | Seller | List kho |
| `POST` | `/api/v1/stores/me/addresses` | Seller | Tạo kho (XOR: user=null) |
| `PUT` | `/api/v1/stores/me/addresses/{id}` | Seller | Cập nhật kho |
| `DELETE` | `/api/v1/stores/me/addresses/{id}` | Seller | Xóa kho |
| `PATCH` | `/api/v1/stores/me/addresses/{id}/default` | Seller | Set kho mặc định |

> **V8 fields**: `Store.tier` (`NORMAL`, `OFFICIAL_MALL`, `PREFERRED`) + `Store.lastActiveAt` (Instant, nullable) — dùng cho Store Extended Response (Subplan 5).

### 46.7 Category (`/api/v1/categories` & `/api/v1/admin/categories`)

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/categories` | Public | Cây ngành hàng |
| `GET` | `/api/v1/categories/{id}` | Public | Chi tiết |
| `POST` | `/api/v1/admin/categories` | Admin | Tạo |
| `PUT` | `/api/v1/admin/categories/{id}` | Admin | Cập nhật |
| `DELETE` | `/api/v1/admin/categories/{id}` | Admin | Xóa (chặn nếu có con) |

### 46.8 Product SPU-SKU (`/api/v1/products` & `/api/v1/seller/products`)

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/products` | Public | Tìm/filter (categoryId, keyword, minPrice, maxPrice, rating, flashSaleOnly, hasDiscount, minSold), paging. Chỉ product `ACTIVE` của store `APPROVED`. Hỗ trợ sort: createdAt_desc (default), createdAt_asc, price_asc, price_desc, sold_desc, rating_desc. Batch fetch variants (không N+1). |
| `GET` | `/api/v1/products/{id}` | Public | Chi tiết SPU + SKU `ACTIVE` |
| `POST` | `/api/v1/seller/products` | Seller | Tạo SPU-SKU. Validate store APPROVED, giá > 0, tồn kho >= 0, SKU unique, số SKU khớp `tier_variation_configs` |
| `GET` | `/api/v1/seller/products` | Seller | List sản phẩm của tôi, paging, filter status |
| `GET` | `/api/v1/seller/products/{id}` | Seller | Chi tiết + toàn bộ SKU (ownership check) |
| `PUT` | `/api/v1/seller/products/{id}` | Seller | Cập nhật SPU + SKU. Chặn sửa giá/giảm tồn kho nếu SKU trong Flash Sale `ACTIVE`. Optimistic lock (`@Version`). |
| `DELETE` | `/api/v1/seller/products/{id}` | Seller | Soft delete nếu có order/flash_sale reference, hard delete nếu chưa |
| `PATCH` | `/api/v1/seller/products/{id}/status` | Seller | Đổi status `ACTIVE` / `INACTIVE` / `OUT_OF_STOCK` |

> **V8 field**: `Product.slug` (`VARCHAR(255)`, nullable, unique partial index) — dùng cho SEO URL, backfill từ `name`.
>
> **`ProductDetailResponse` new fields** (Subplan 2 + Subplan 4):
> - `Double ratingAvg` — trung bình rating từ reviews VISIBLE (default 0.0)
> - `Integer ratingCount` — tổng review VISIBLE
> - `Integer soldCount` — từ `products.total_sold` (V7)
> - `BigDecimal minPrice` — MIN variant original_price WHERE ACTIVE
> - `BigDecimal maxPrice` — MAX variant original_price WHERE ACTIVE
> - `Integer discountPercent` — `round((original - flashSale) / original * 100)` từ lowest-price active FlashSaleItem; `null` nếu không có
> - `List<ProductImageDto> images` — ảnh từ `ImageService.listImages(PRODUCT, productId)`, alt = product name
> - **`List<ActiveFlashSaleDto> flashSales`** (Subplan 4) — danh sách FlashSaleItem ACTIVE của product. Gồm: `slotId`, `slotStartTime`, `slotEndTime`, `itemId`, `variantId`, `originalPrice`, `flashSalePrice`, `stockAvailable` (Redis → fallback DB), `slotStatus`. Sorted by `flashSalePrice ASC`. Empty list nếu không có.

### 46.9 Image (`/api/v1/images` & `/api/v1/admin/images`)

Xem chi tiết mục #25. Tóm tắt:

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/images?ownerType=...&ownerId=...` | Owner | Upload + attach |
| `GET` | `/api/v1/images?ownerType=...&ownerId=...` | Owner / Public (tùy owner) | List ảnh |
| `PATCH` | `/api/v1/images/{id}/primary` | Owner | Set primary (reset primary khác) |
| `PATCH` | `/api/v1/images/{id}/order` | Owner | Set display_order |
| `DELETE` | `/api/v1/images/{id}` | Owner | Xóa ảnh (cả Cloudinary) |
| `DELETE` | `/api/v1/admin/images/{id}` | Admin | Force delete |

### 46.10 Flash Sale

#### Boundary rule
- Chỉ module `flashsale` ghi flash sale domain.
- Tích hợp `order` qua `FlashSaleOrderPort`.

#### Concurrency Architecture
- Lua script `reserve_stock.lua` — key `flash_sale:stock:{itemId}` + `flash_sale:user_limit:{slotId}:{userId}:{itemId}`.
- Pluggable `StockReservationStrategy` (Lua mặc định, Redisson optional).
- Dual-write compensation: Redis rollback (`INCRBY stock`, `DECRBY user_limit`) + DB (`replenishAvailableStockConditionally`).

#### Admin (`/api/v1/admin/flash-sales`)

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/admin/flash-sales/slots` | Admin | Tạo slot (chống overlap) |
| `PUT` | `/api/v1/admin/flash-sales/slots/{id}` | Admin | Cập nhật slot (chặn nếu ENDED, check overlap) |
| `GET` | `/api/v1/admin/flash-sales/slots/{id}` | Admin | Chi tiết slot |
| `GET` | `/api/v1/admin/flash-sales/slots` | Admin | List slot |
| `PATCH` | `/api/v1/admin/flash-sales/items/{id}/approve` | Admin | Duyệt SKU, trừ kho gốc atomic |
| `POST` | `/api/v1/admin/flash-sales/slots/{id}/pre-warm` | Admin | Pre-warm Redis (SETNX, idempotent, tính pending) |

#### Seller (`/api/v1/seller/flash-sales`)

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/seller/flash-sales/items` | Seller | Đăng ký SKU (`flash_sale_price < original_price`, `allocated_stock <= stock_quantity`) |

#### Public & Reservation (`/api/v1/flash-sales`)

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/flash-sales/slots` | Public | List slot đang/sắp diễn ra + stock realtime từ Redis |
| `POST` | `/api/v1/flash-sales/reservations` | Authenticated | Reservation với `Idempotency-Key` (mục #34). Lua atomic + tạo Order `PENDING_PAYMENT` qua `FlashSaleOrderPort` |

### 46.11 Cart (`/api/v1/cart`)

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/cart` | Buyer | Cart hiện tại |
| `POST` | `/api/v1/cart/items` | Buyer | Thêm item |
| `PUT` | `/api/v1/cart/items/{id}` | Buyer | Cập nhật số lượng |
| `DELETE` | `/api/v1/cart/items/{id}` | Buyer | Xóa item |
| `DELETE` | `/api/v1/cart` | Buyer | Clear cart |
| `POST` | `/api/v1/cart/checkout` | Buyer | Split cart theo store → tạo Orders (mục #11) |

### 46.12 Order Buyer (`/api/v1/orders`) & Seller (`/api/v1/seller/orders`)

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/orders` | Buyer | Tạo order (qua checkout flow) |
| `GET` | `/api/v1/orders/me` | Buyer | List order của tôi, paging |
| `GET` | `/api/v1/orders/{id}` | Buyer | Chi tiết (ownership check) |
| `PATCH` | `/api/v1/orders/{id}/cancel` | Buyer | Cancel (chỉ khi `PENDING_PAYMENT` hoặc `CONFIRMED` chưa ship) |
| `GET` | `/api/v1/seller/orders` | Seller | List order thuộc store mình |
| `GET` | `/api/v1/seller/orders/{id}` | Seller | Chi tiết (ownership check store) |
| `PATCH` | `/api/v1/seller/orders/{id}/ship` | Seller | Đánh dấu đã giao hàng |
| `PATCH` | `/api/v1/seller/orders/{id}/complete` | Seller | Đánh dấu hoàn thành |

### 46.13 Voucher (`/api/v1/vouchers`, `/api/v1/seller/vouchers`, `/api/v1/admin/vouchers`)

Xem chi tiết mục #22. Tóm tắt:

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/vouchers` | Buyer | List voucher available |
| `POST` | `/api/v1/vouchers` | Buyer | Claim voucher |
| `GET` | `/api/v1/seller/vouchers` | Seller | List voucher store |
| `POST` | `/api/v1/seller/vouchers` | Seller | Tạo voucher store |
| `PUT` | `/api/v1/seller/vouchers/{id}` | Seller | Cập nhật |
| `DELETE` | `/api/v1/seller/vouchers/{id}` | Seller | Xóa |
| `GET` | `/api/v1/admin/vouchers` | Admin | List platform voucher |
| `POST` | `/api/v1/admin/vouchers` | Admin | Tạo platform voucher |

### 46.13 Banner (`/api/v1/banners`, `/api/v1/admin/banners`)

Xem chi tiết mục #25 (Image Module). Tóm tắt:

| Method | Endpoint | Access | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/banners/home` | Public | Banner trang chủ (mainSlides HERO + sideBanners) |
| `GET` | `/api/v1/banners/position/{position}` | Public | Banner ACTIVE theo vị trí |
| `GET` | `/api/v1/admin/banners` | Admin | List banner (paging, filter type/isActive) |
| `POST` | `/api/v1/admin/banners` | Admin | Tạo banner |
| `PUT` | `/api/v1/admin/banners/{id}` | Admin | Cập nhật banner |
| `PATCH` | `/api/v1/admin/banners/{id}/status` | Admin | Đổi isActive (validate time window) |
| `DELETE` | `/api/v1/admin/banners/{id}` | Admin | Soft delete (isActive=false) |

Error codes: `BANNER_NOT_FOUND`, `BANNER_INVALID_DATE_RANGE`, `BANNER_INVALID_STATUS`, `BANNER_IMAGE_URL_BLANK`.

### 46.14 Payment (`/api/v1/payments`)

> Module `payment/controller/` chưa có — placeholder. Sẽ cập nhật khi implement.

Xem mục #20.

### 46.15 Wallet (`/api/v1/wallets`)

> Module `wallet/controller/` chưa có — placeholder. Sẽ cập nhật khi implement.

Xem mục #21.

### 46.16 Review (`/api/v1/...`)

> Module `review/controller/` chưa có — placeholder. Sẽ cập nhật khi implement.

Xem mục #24.

---

## 47. Documentation & Code Sync Checklist

### 47.1 Nguyên tắc

Code, migration, docs **PHẢI đồng bộ**. Mọi thay đổi schema hoặc API contract phải kéo theo cập nhật docs **TRONG CÙNG PR** (không tách riêng, không để sau).

Quy tắc này tồn tại vì 3 lý do:

1. Docs lỗi thời khiến FE team implement sai contract → mất công sửa.
2. Mỗi migration thường kéo theo 3–5 file docs cần sync (verified bằng grep `docs/`).
3. Nếu không có checklist, agent sẽ quên, để docs drift dần.

### 47.2 Khi nào phải sync

| Trigger | Phải sync |
|---|---|
| Thêm/sửa `db/migration/V{n}__*.sql` | **Bắt buộc** (xem #48) |
| Thêm/sửa field trong Entity | OpenAPI annotation + DTO + `api-document.md` |
| Thêm/sửa endpoint mới | `api-document.md` + `AGENTS.md` mục #46 |
| Đổi status enum / error code | `api-document.md` + `AGENTS.md` mục #7, #38 |
| Thêm module mới | `AGENTS.md` mục #5 + `docs/03_system_design.md` |
| Thay đổi Redis key/TTL | `AGENTS.md` mục #15–#17 + `docs/03_system_design.md` |
| Thay đổi Lua script | `docs/03_system_design.md` + `AGENTS.md` mục #13, #16 |
| Thay đổi WS event/destination | `api-document.md` mục 25 + `AGENTS.md` mục #36 |
| Thay đổi contract `FlashSaleOrderPort` | `flashsale_order_contract.md` + `AGENTS.md` mục #13 |

### 47.3 Quy tắc rà so sửa sau khi tạo migration mới

Mỗi lần tạo `src/main/resources/db/migration/V{n}__*.sql`, agent **PHẢI**:

1. **Mở bảng matrix #48** để xác định file docs bị ảnh hưởng.
2. **Đọc lại** các file đó để biết vị trí cần sửa.
3. **Báo cáo cho user** danh sách file cần sync **TRƯỚC** khi sửa (vì mục #2 cấm tự ý sửa docs).
4. Sau khi user duyệt → sửa theo thứ tự:
   - ERD / bảng column (`docs/03_system_design.md`)
   - Quy ước chung (`docs/api/api-document.md` mục 1)
   - Endpoint list (`docs/api/api-document.md` mục tương ứng)
   - Error code table (`docs/api/api-document.md` mục 26)
   - `AGENTS.md` mục liên quan
5. Nếu thay đổi ảnh hưởng user frontend → ghi rõ trong PR body, ping team FE.

### 47.4 Quy tắc cho mỗi loại file

#### `docs/01_project_planning.md`
- Cập nhật khi: thay đổi milestone (M1/M2/M3), phạm vi đồ án, kế hoạch tuần.
- **KHÔNG** thêm chi tiết kỹ thuật vào đây (chi tiết ở 02, 03).
- Lệnh kiểm tra nhanh: `grep "M[123]\|Tuần" docs/01_project_planning.md`.

#### `docs/02_requirement_analysis.md`
- Cập nhật khi: thêm/sửa user story, use case, business rule.
- Mỗi rule thay đổi phải có ID (US-001, BR-014) để reference.

#### `docs/03_system_design.md`
- Cập nhật khi: thay đổi schema, ERD, sequence diagram, Redis key, Lua script.
- Mục "Bảng X" phải sync với migration 1:1 (tên cột, type, constraint, FK).
- ERD Mermaid phải render được (mở preview trong Cursor).
- Lệnh kiểm tra nhanh: `grep "CREATE TABLE\|flash_sale:\|reserve_stock" docs/03_system_design.md`.

#### `docs/04_architecture_analysis.md`
- Cập nhật khi: thay đổi kiến trúc lớn (thêm pattern mới, đổi persistence).
- **KHÔNG** trùng lặp với 03 (03 chi tiết hơn).

#### `docs/api/api-document.md`
- Cập nhật khi: thêm/sửa endpoint, request/response shape, error code.
- Mỗi endpoint có: Method, URL, Access, Request schema, Response schema, Error codes.
- Mục 26 (Error Code) phải đầy đủ, sort theo alphabet.
- Lệnh kiểm tra nhanh: `grep "/api/v1/\|status.*[0-9]\{3\}" docs/api/api-document.md`.

#### `docs/flashsale_order_contract.md`
- Cập nhật khi: thay đổi contract giữa flashsale module ↔ order module.
- Ảnh hưởng: `FlashSaleOrderPort` interface, `CreateFlashSaleOrderCommand`, `OrderRef`, `ExpiredOrderRef`.

#### `AGENTS.md` (file này)
- Cập nhật khi: thêm convention mới, đổi rule nghiệp vụ, mở rộng API list.
- **KHÔNG tự ý sửa** bởi agent khi phát hiện sai — báo user (mục #2).

### 47.5 Checklist đính kèm mỗi PR

Trong mô tả PR, tác giả **PHẢI** list:

```text
## Docs sync
- [ ] docs/01_project_planning.md (N/A nếu không đụng)
- [ ] docs/02_requirement_analysis.md (N/A nếu không đụng)
- [ ] docs/03_system_design.md (N/A nếu không đụng)
- [ ] docs/04_architecture_analysis.md (N/A nếu không đụng)
- [ ] docs/api/api-document.md (N/A nếu không đụng)
- [ ] docs/flashsale_order_contract.md (N/A nếu không đụng)
- [ ] AGENTS.md (N/A nếu không đụng)
```

Nếu file không cần sửa → ghi rõ "N/A (lý do)".

### 47.6 Khi phát hiện docs ≠ code

- **Code = source of truth cuối cùng** (mục #2).
- Báo user, **không tự ý** sửa docs.
- Nếu user duyệt → tạo **1 commit riêng** `"docs: sync X với code"` để dễ review.
- Commit docs KHÔNG để riêng 1 tháng sau commit code.

### 47.7 Khi có thay đổi breaking API

Ngoài việc sync docs, agent phải:

1. Đánh version mới cho endpoint (vd `/api/v2/...`).
2. Giữ endpoint cũ 1 release cycle (vd 1 tháng) trước khi xóa.
3. Ghi rõ trong `api-document.md` mục "Deprecated endpoints".
4. Báo team FE bằng note trong PR.

---

## 48. Migration Impact Matrix

Bảng tra cứu nhanh: **"Khi sửa bảng/cột X thì rà soát file nào"**.

### 48.1 Ma trận theo bảng

| Bảng bị ảnh hưởng | docs/01 | docs/02 | docs/03 | docs/04 | api-doc (mục) | AGENTS.md (mục) |
|---|---|---|---|---|---|---|
| `users` / `user_roles` | — | US-001..010 | ERD, mục 1 (Users) | — | 6, 7 | #5, #46.4 |
| `auth_accounts` | — | US-001 | ERD | — | 6 | #5, #46.3 |
| `addresses` | — | BR-Address | ERD, mục 6 | — | 8 | #5, #12, #46.5, #46.6 |
| `stores` | — | US-Store | ERD, mục 7 | — | 9, 10 | #5, #46.6 |
| `categories` | — | US-006 | ERD | — | 11, 12 | #5, #46.7 |
| `products` / `product_variants` | M1 | US-011..015 | ERD, mục 12–13 | — | 13, 14 | #5, #14, #46.8 |
| `flash_sale_slots` / `flash_sale_items` | M2 | US-020..030 | ERD, mục 14–15, Redis keys, Lua | Kiến trúc Flash Sale | 15, 16, 17, 18 | #5, #13–#19, #46.10 |
| `vouchers` / `voucher_usages` | — | US-040 | ERD, mục 19 | — | 22, 23, 24 | #5, #22, #46.13 |
| `orders` / `order_items` | M2 | US-035 | ERD, mục 16–17, Snapshots | Saga/compensation | 20, 21 | #5, #11, #23, #46.12 |
| `payments` | — | US-038 | ERD, mục 18 | — | (chưa có) | #5, #20 |
| `wallets` / `wallet_transactions` | — | US-039 | ERD, mục 20 | — | (chưa có) | #5, #21 |
| `carts` / `cart_items` | — | US-033 | ERD | — | 19 | #5, #46.11 |
| `product_reviews` | — | US-050 | ERD, mục 21 | — | (chưa có) | #5, #24 |
| `images` (V3+) | — | US-Image | ERD, mục 22 (Images) | — | 28, mục 1 | #5, #25, #46.9 |
| `permissions` / `permission_groups` / `group_permissions` | — | US-002 | ERD | — | 27 | #5, #30, #46.2 |
| `roles` | — | US-002 | ERD | — | 27 | #5, #30, #46.2 |

> **Ghi chú**: cột "US-XXX" / "BR-XXX" là reference ID trong `docs/02_requirement_analysis.md`. Khi migration mới không thuộc ID có sẵn → thêm ID mới trước khi sửa docs.

### 48.2 Ma trận theo file docs

| File docs | Trigger phải sync | Lệnh kiểm tra nhanh |
|---|---|---|
| `docs/01_project_planning.md` | Đổi milestone M1/M2/M3, thay đổi phạm vi | `grep -n "M[123]\|Tuần" docs/01_project_planning.md` |
| `docs/02_requirement_analysis.md` | Thêm/sửa US-XXX, BR-XXX | Review mục tương ứng |
| `docs/03_system_design.md` | Thêm/sửa migration, đổi ERD, đổi Redis key | `grep -n "CREATE TABLE\|flash_sale:\|reserve_stock" docs/03_system_design.md` |
| `docs/04_architecture_analysis.md` | Đổi kiến trúc lớn (Saga, pattern mới) | Review toàn file |
| `docs/api/api-document.md` | Thêm/sửa endpoint, đổi response, đổi error code | `grep -n "/api/v1/\|status.*[0-9]\{3\}" docs/api/api-document.md` |
| `docs/flashsale_order_contract.md` | Đổi `FlashSaleOrderPort` interface | So sánh với `flashsale/port/*.java` |
| `AGENTS.md` | Thêm rule, đổi convention, mở rộng API list | So sánh với code mới nhất |

### 48.3 Quy trình sync mỗi lần đổi migration

```text
1. Tạo V{n}__*.sql
2. Tra bảng 48.1 → list file docs bị ảnh hưởng
3. grep nội dung liên quan trong từng file docs (xem bảng 48.2)
4. Liệt kê diff cần sửa (KHÔNG sửa ngay)
5. Báo user list file + diff preview
6. User duyệt → mới sửa docs
7. Commit code + docs trong CÙNG PR
   - Ưu tiên: 1 commit "feat: ..." + 1 commit "docs: sync ..."
   - Có thể gộp 1 commit nếu thay đổi nhỏ
8. Cập nhật PR description theo checklist mục #47.5
```

### 48.4 Anti-pattern

- ❌ Sửa migration + implement code NHƯNG quên update docs.
- ❌ Tạo migration mới, hỏi user "có cần update docs không?" — **PHẢI** chủ động đề xuất dựa trên bảng 48.1.
- ❌ Sửa docs mà không reference code hiện tại.
- ❌ Commit docs riêng nhiều ngày sau commit code.
- ❌ Copy-paste JSON example cũ (vd có `imageUrl` cũ) khi đã migrate sang `images` (V3/V4).
- ❌ Sửa `docs/` không báo user → vi phạm mục #2.

### 48.5 Ví dụ cụ thể

#### Case 1: Migration V5 thêm cột `orders.coupon_code VARCHAR(50)`

1. Tra bảng 48.1 → `orders / order_items` ảnh hưởng: `docs/03_system_design.md` (mục 16), `docs/api/api-document.md` (mục 20), `AGENTS.md` (mục #11, #23, #46.12).
2. `docs/02_requirement_analysis.md` cần thêm BR-Coupon.
3. `docs/01_project_planning.md` không liên quan (không đổi milestone) → N/A.
4. Diff preview:
     - `03_system_design.md`: thêm dòng trong bảng `orders`.
     - `api-document.md`: thêm field `couponCode` trong `OrderResponse` + thêm BR-XXX vào mục "Business Rule".
     - `AGENTS.md`: cập nhật mục #23 (Order Snapshots) nếu snapshot thêm field.
5. Báo user → user duyệt → commit.

#### Case 2: Migration V6 thêm bảng `notifications`

1. Tra bảng 48.1 → bảng mới không có sẵn → **thêm row mới** vào bảng 48.1.
2. `docs/03_system_design.md` → thêm ERD, thêm mục "Bảng X".
3. `docs/api/api-document.md` → thêm endpoint mục mới (vd mục 29).
4. `AGENTS.md` → thêm module mới vào mục #5 (nếu module mới) + endpoint vào mục #46.
5. Báo user → duyệt → commit.

---

> **Ghi chú cuối**: File này là **living document**. Cập nhật khi:
> - Thêm module mới.
> - Thay đổi API contract (cùng với `docs/api/api-document.md`).
> - Phát hiện thêm invariant mới.
> - Cập nhật convention.
>
> **Không tự ý sửa** bởi agent — chỉ user hoặc khi user yêu cầu rõ.
