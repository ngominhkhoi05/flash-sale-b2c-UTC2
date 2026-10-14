# KẾ HOẠCH DỰ ÁN (PROJECT PLANNING DOCUMENT)
## Đề tài: Xây dựng Hệ thống Sàn Flash Sale B2C Chống Over-selling bằng Distributed Lock trên Redis

---

* **Học phần:** Đồ án Kỹ thuật Phần mềm / Hệ thống Phân tán
* **Cơ sở đào tạo:** Trường Đại học Giao thông Vận tải Phân hiệu tại TP.HCM (UTC2)
* **Giai đoạn:** Bước 1 - Lập kế hoạch dự án (SDLC Phase 1: Planning & Feasibility Study)
* **Mô hình kiến trúc:** Sàn Thương mại Điện tử B2C Đa người bán (Multi-Vendor Marketplace)
* **Tác giả / Nhóm thực hiện:** Nhóm phát triển Flash Sale B2C

---

## 1. Bối cảnh & Đặt vấn đề (Context & Problem Statement)

### 1.1. Bối cảnh thị trường B2C
Trong các sàn thương mại điện tử hiện đại (Shopee, Lazada, TikTok Shop), các sự kiện **Flash Sale** (Giờ vàng giá sốc) luôn là đòn bẩy tiếp thị quan trọng nhất để kích cầu mua sắm.
* Người tiêu dùng có tâm lý FOMO (Fear of Missing Out - sợ bỏ lỡ deal hời), thường tập trung canh đồng hồ đếm ngược và đồng loạt nhấn nút **"Mua ngay"** tại giây thứ 0.
* Lưu lượng truy cập (traffic) đột biến tăng vọt gấp hàng trăm, thậm chí hàng nghìn lần so với ngày thường (traffic spike) chỉ trong vài giây đến vài phút.

### 1.2. Thách thức kỹ thuật cốt lõi
1. **Tranh chấp tài nguyên đồng thời cao (High-Concurrency Race Condition):**
   * Giả sử có một sản phẩm hot chỉ còn **100 đơn vị tồn kho**, nhưng có đến **5.000 yêu cầu mua hàng** gửi đến cùng một tích tắc.
   * Nếu toàn bộ request gọi thẳng xuống cơ sở dữ liệu quan hệ (RDBMS như PostgreSQL) và thực hiện đọc-kiểm tra-trừ kho (`SELECT stock` -> `UPDATE stock = stock - 1`), cơ chế khóa dòng (Row-level Locking) sẽ làm cạn kiệt Database Connection Pool.
   * Nguy hiểm nhất là hiện tượng **Over-selling (Bán âm kho)**: 1 sản phẩm bị bán cho nhiều người cùng lúc, dẫn đến việc doanh nghiệp phải hủy đơn hàng, bồi thường và chịu khủng hoảng truyền thông nghiêm trọng.
2. **Trải nghiệm thời gian thực (Real-time User Experience):**
   * Nếu người tiêu dùng phải tải lại trang (F5) liên tục để kiểm tra xem sản phẩm còn hàng hay không, máy chủ web sẽ phải chịu tải khổng lồ từ các HTTP request tĩnh vô nghĩa.
   * Cần một cơ chế truyền tin hai chiều thời gian thực (Realtime WebSocket) để cập nhật đồng hồ đếm ngược và đẩy số lượng hàng tồn đang giảm dần xuống giao diện mà không cần reload trang.
3. **Mô hình Đa bên & Dòng tiền Minh bạch (Multi-Vendor & Commission):**
   * Hệ thống vận hành theo cơ chế sàn: **Admin mở Slot** $\rightarrow$ **Người bán đăng ký sản phẩm** $\rightarrow$ **Admin duyệt** $\rightarrow$ **Người mua đặt hàng**.
   * Xử lý dòng tiền hoa hồng sàn linh hoạt (mặc định 5%) và quản lý ví doanh thu cho từng người bán một cách tự động, chính xác.
4. **Banner Homepage & Theo dõi Gian hàng:**
   * Trang chủ hiển thị các banner khuyến mãi (Hero, Freeship, Voucher mới, Promotion) với khả năng lên lịch hiển thị.
   * Người mua có thể theo dõi gian hàng yêu thích để nhận thông báo khi có sản phẩm mới, Flash Sale, hoặc khuyến mãi.

---

## 2. Mục tiêu dự án (Project Objectives - SMART)

* **S (Specific):** Xây dựng hệ thống Sàn Flash Sale B2C hoàn chỉnh với 3 đối tượng (Admin, Người bán, Người mua), kiểm soát và ngăn ngừa lỗi Over-selling, hỗ trợ giữ kho có thời hạn (TTL 5 phút), thanh toán qua **Quét mã QR ZaloPay thật** & **COD**, và đăng nhập đa nền tảng (OAuth2 Google, Facebook, GitHub).
* **M (Measurable):**
  * **Kiểm soát Over-selling:** Đảm bảo số lượng hàng bán ra không vượt quá tồn kho mở bán dù chịu tải đồng thời hàng nghìn request/giây.
  * **Độ trễ phản hồi (Response Time P99):** Dưới **500ms** trong điều kiện chịu tải đỉnh của API giữ chỗ.
  * **Độ trễ đồng bộ tồn kho Realtime:** Dưới **100ms** thông qua WebSocket (STOMP).
* **A (Achievable):** Ứng dụng các công nghệ tiêu chuẩn công nghiệp: In-Memory Database (Redis), Lua Scripting, Redisson Distributed Lock, Next.js, Spring Boot và ZaloPay API Sandbox.
* **R (Relevant):** Giải quyết trực tiếp các bài toán kiến trúc phân tán thực tế của ngành thương mại điện tử.
* **T (Time-bound):** Kế hoạch triển khai chia làm 2 giai đoạn rõ ràng:
  * **Giai đoạn Giữa kì (Midterm):** 8 tuần đầu (Tập trung vào lõi xử lý đồng thời, luồng mua giữ chỗ, ZaloPay/COD và kiểm thử tải).
  * **Giai đoạn Cuối kì (Final):** 7 tuần tiếp theo (Mở rộng Kubernetes, AI Gemini Dynamic Pricing, Flutter Mobile Dark Mode và Redis Streams).

---

## 3. Phạm vi dự án (Project Scope)

### 3.1. Phạm vi thực hiện (In-Scope)

#### A. Giai đoạn Giữa kì (Midterm - Concurrency Core & Marketplace MVP):
1. **Xác thực, Địa chỉ GPS & Phân quyền Ma trận (Auth, Addresses & Matrix RBAC):**
   * Đăng nhập Local (Email/Password) và Đăng nhập xã hội OAuth2 (**Google, Facebook, GitHub**).
   * Phân quyền đa vai trò qua bảng `roles` và `user_roles` (`ROLE_ADMIN`, `ROLE_SELLER`, `ROLE_BUYER`).
   * Phân quyền hạt mịn độc lập qua **Nhóm quyền (`permission_groups`)** và **Quyền nguyên tử (`permissions`)**.
   * Cơ chế **Feature Toggle (Cờ `is_active`)**: Khóa tức thì các tính năng chưa hoàn thiện.
   * Quản lý **Sổ địa chỉ Chuẩn hóa 2 Khóa Ngoại Vật Lý (`addresses`)** hỗ trợ tọa độ GPS (`latitude`, `longitude`) cho cả Người mua (nhiều địa chỉ giao hàng) và Gian hàng (kho lấy hàng của shop). Ràng buộc `CHECK` loại trừ XOR đảm bảo toàn vẹn tham chiếu cấp RDBMS.
2. **Quản trị Sàn & Phê duyệt Chiến dịch (Admin Portal):**
   * Admin tạo các khung giờ Flash Sale (Slot).
   * Duyệt hoặc từ chối sản phẩm do Seller gửi tham gia Flash Sale (khấu trừ trực tiếp từ kho gốc khi duyệt).
   * Cấu hình tỷ lệ hoa hồng linh hoạt (mặc định 5%, có thể điều chỉnh theo shop).
3. **Phân hệ Người bán (Seller Portal):**
   * Quản lý sản phẩm kho gốc của gian hàng theo mô hình SPU - SKU.
   * Đăng ký sản phẩm vào khung giờ Flash Sale (chọn giá sốc, số lượng mở bán, giới hạn mua mỗi khách).
   * Quản lý đơn hàng của shop và theo dõi ví doanh thu sau khi sàn đã trừ hoa hồng.
4. **Trang Bán hàng Khách hàng (User Storefront):**
   * Giao diện hiển thị sự kiện Flash Sale, đếm ngược thời gian thực (Countdown Timer).
   * Thanh tiến trình tồn kho realtime (WebSocket STOMP).
   * Nút "Mua ngay" với cơ chế **Giữ kho có thời hạn (Reservation TTL 5 phút)**.
5. **Xử lý Đơn hàng, Tách đơn & Thanh toán Đa Cổng (Orders, Order Splitting & Payments):**
   * Cơ chế **Tách đơn Đa gian hàng (Order Splitting):** Nếu giỏ hàng có sản phẩm từ nhiều Shop, hệ thống tự động tách thành các `orders` riêng biệt theo từng `store_id`.
   * Tích hợp **Cổng ZaloPay thật (Quét mã QR)** qua API & Webhook Callback Idempotent.
   * Hỗ trợ thanh toán tiền mặt khi nhận hàng (**COD**).
   * Cơ chế **Tự động Hoàn kho (Auto-Rollback):** Hoàn trả chính xác số lượng mặt hàng đã giữ chỗ (`quantity`) và giải phóng giới hạn mua khi khách không thanh toán trong 5 phút hoặc hủy đơn.
6. **Lõi xử lý kiểm soát Over-selling (Backend Concurrency Core):**
   * Triển khai và so sánh 2 giải pháp xử lý tranh chấp trên Redis:
     * **Phương án 1 - Redis Lua Script:** Trừ tồn kho nguyên tử (Atomic Reservation), phân định Reservation TTL (300s) và Purchase Limit TTL (sống theo khung giờ sale).
     * **Phương án 2 - Redisson Distributed Lock:** Khóa phân tán với Watchdog tự gia hạn lease time.
   * Cơ chế **Try-Catch Compensation:** Hoàn trả kho Redis tức thì nếu bước tạo đơn trong PostgreSQL thất bại.
7. **Phân hệ Khuyến mãi & Voucher (Voucher Subsystem):**
   * Quản lý mã giảm giá của Sàn (Platform Voucher) và của Người bán (Shop Voucher).
   * Kiểm tra điều kiện áp dụng (đơn tối thiểu, giảm %, giảm cố định, số lượt dùng tối đa, quota toàn sàn).
   * Hỗ trợ hoàn lại voucher tự động nếu đơn hàng bị hủy hoặc timeout thanh toán.
8. **Kiểm thử tải & Báo cáo Benchmark (Load Testing):**
   * Sử dụng công cụ **k6 / JMeter** viết kịch bản mô phỏng 1.000 - 5.000 người dùng đồng thời nhấn mua tại giây thứ 0.
   * Đánh giá khả năng kiểm soát tồn kho không bị bán âm và so sánh hiệu năng 2 cơ chế khóa.
9. **Đóng gói triển khai:**
   * Viết `docker-compose.yml` chạy tự động toàn bộ dịch vụ (PostgreSQL, Redis, Backend Spring Boot, Frontend Next.js).
10. **Quản lý Ảnh Đa đối tượng (Polymorphic Storage với Cloudinary) — Đã refactor 07/10/2026:**
    * Thay thế 5 cột ảnh rải rác (`users.avatar_url`, `stores.logo_url`, `products.image_url`, `product_variants.image_url`, `product_reviews.image_urls` JSONB) bằng **bảng `images`** duy nhất với cặp khóa `(owner_type, owner_id)`.
    * 5 owner types: `PRODUCT`, `VARIANT`, `USER`, `STORE`, `REVIEW`.
    * Partial Unique Index: 1 user = 1 avatar, 1 store = 1 logo, 1 product/variant = 1 ảnh primary.
    * Cloudinary lưu ảnh vật lý (CDN). Pattern: Soft delete DB → async Cloudinary destroy → flag `cloudinary_deleted=true` → scheduled job dọn record >30 ngày.
    * Migration `V3` (tạo bảng `images`) và `V4` (drop 5 cột cũ) đã được apply vào DB PostgreSQL.
    * **Cảnh báo quan trọng:** App hiện KHÔNG boot được do Hibernate schema validation fail (entity Java còn giữ 5 field ảnh cũ). Code Java Service/Entity/Repository cần được migrate sang đọc/ghi qua bảng `images` trước khi tiếp tục (task kế tiếp).

#### B. Giai đoạn Cuối kì (Final - Scale, AI & Multi-platform):
1. **Kiến trúc Bất đồng bộ (Asynchronous Order Processing):**
   * Dùng **Redis Streams** tiếp nhận đơn hàng đỉnh điểm, worker ghi DB định kỳ, Spring Retry + Dead Letter Queue.
2. **Bảo vệ hệ thống B2C:**
   * Rate Limiting (**Bucket4j**) chống bot/spam click, Circuit Breaker (**Resilience4j**).
3. **AI Định giá động (Gemini API Dynamic Pricing):**
   * Tích hợp Gemini API phân tích tốc độ bán (Sales Velocity) đề xuất tăng/giảm giá (có admin duyệt).
4. **Ứng dụng Quản trị Di động (Mobile Admin App):**
   * Phát triển ứng dụng bằng **Flutter** (hỗ trợ Dark Mode & High Contrast cho thủ kho).
5. **Hạ tầng Mở rộng & Giám sát:**
   * Multi-instance Spring Boot + Nginx, Kubernetes (Deployment, Service, HPA autoscaling).
   * Giám sát thời gian thực với **Prometheus + Grafana**.
   * Load test quy mô **10.000+ Virtual Users**.

### 3.2. Ngoài phạm vi (Out-of-Scope)
* Đơn vị vận chuyển logistics liên tỉnh kết nối API bên thứ 3 (GHN, Viettel Post).
* Hệ thống khiếu nại, đổi trả bảo hành nhiều bước.

---

## 4. Nghiên cứu tính khả thi (Feasibility Analysis)

| Hạng mục | Đánh giá | Chi tiết giải pháp & Cơ sở kỹ thuật |
| :--- | :---: | :--- |
| **Tính khả thi Kỹ thuật (Technical Feasibility)** | **Cực kỳ khả thi** | • **Redis In-Memory & Lua:** Tốc độ xử lý hàng trăm nghìn ops/giây, bộ nhớ đệm RAM loại bỏ nút thắt cổ chai I/O của DB.<br>• **Redisson:** Framework chuẩn của Java, tự động quản lý lock, chống deadlock qua Watchdog.<br>• **ZaloPay API:** Cung cấp môi trường Sandbox miễn phí, tài liệu API tiếng Việt đầy đủ.<br>• **OAuth2:** Spring Security hỗ trợ cấu hình Google/GitHub client tiện lợi. |
| **Tính khả thi Kinh tế (Economic Feasibility)** | **Chi phí 0 VNĐ** | • Toàn bộ công nghệ chính (Spring Boot, Next.js, PostgreSQL, Redis, Docker, k6, Prometheus, Grafana) đều là mã nguồn mở.<br>• ZaloPay Sandbox và Gemini API Free Tier không tốn phí. |
| **Tính khả thi Vận hành (Operational Feasibility)** | **Thuận tiện & Trực quan** | • Khởi chạy 1 lệnh qua Docker Compose.<br>• Web Next.js có portal riêng biệt cho Seller và Admin theo quyền RBAC. |

---

## 5. Phân chia công việc & Lộ trình (WBS & Timeline Roadmap)

```mermaid
gantt
    title Kế hoạch Triển khai Dự án Flash Sale B2C (15 tuần)
    dateFormat  YYYY-MM-DD
    section GIAI ĐOẠN 1: GIỮA KÌ
    Bước 1: Lập kế hoạch & Khảo sát           :done,    b1, 2026-09-01, 7d
    Bước 2: Phân tích yêu cầu (SRS)           :done,    b2, 2026-09-08, 7d
    Bước 3: Thiết kế Hệ thống & DB & Lock     :active,  b3, 2026-09-15, 10d
    Bước 4: Lập trình Backend Spring Boot     :         b4, 2026-09-25, 14d
    Bước 4a: Refactor ảnh đa đối tượng (Polymorphic) : done, b41_img, 2026-10-07, 2d
    Bước 4b: Lập trình Frontend Next.js       :         b5, 2026-10-02, 12d
    Bước 5: Load Test k6 & Báo cáo Giữa kì    :         b6, 2026-10-14, 10d
    section GIAI ĐOẠN 2: CUỐI KÌ
    Nâng cấp Redis Streams & Rate Limiting    :         b7, 2026-10-24, 14d
    Tích hợp Gemini API Dynamic Pricing       :         b8, 2026-11-07, 10d
    Phát triển Mobile App Flutter             :         b9, 2026-11-17, 14d
    Kubernetes, Prometheus/Grafana & Test 10k :         b10, 2026-12-01, 14d
    Nghiệm thu & Báo cáo Cuối kì              :         b11, 2026-12-15, 7d
```

---

## 6. Các mốc nghiệm thu quan trọng (Key Milestones)

| Mốc | Thời điểm | Tên mốc nghiệm thu | Deliverables & Tiêu chí nghiệm thu |
| :---: | :---: | :--- | :--- |
| **M1** | Tuần 3 | **Hồ sơ Thiết kế & Kiến trúc** | SRS đầy đủ, ERD PostgreSQL 27 bảng, Sequence Diagram, API Swagger specs. |
| **M2** | Tuần 6 | **Luồng tích hợp thông suốt (End-to-End MVP)** | Docker Compose chạy đủ DB, Redis, Backend, Frontend. Luồng tạo slot $\rightarrow$ đăng ký $\rightarrow$ duyệt $\rightarrow$ đặt hàng giữ chỗ $\rightarrow$ quét QR ZaloPay / COD $\rightarrow$ tính hoa hồng. |
| **M3** | **Tuần 8** | **🏆 NGHIỆM THU GIỮA KÌ (Lõi Kiểm soát Over-selling)** | **1. Kiểm soát Over-selling:** Bắn tải 1.000 - 5.000 request cùng lúc, kiểm soát số lượng bán không vượt quá tồn kho mở bán, bảo đảm tính nhất quán dữ liệu.<br>**2. Báo cáo đối sánh định lượng:** So sánh hiệu năng Lua Script vs Redisson Lock. |
| **M4** | Tuần 11 | **Async Queue & AI Dynamic Pricing** | Redis Streams xử lý đơn hàng bất đồng bộ; Gemini API phân tích nhịp bán và đề xuất đổi giá trên Admin Portal. |
| **M5** | **Tuần 15** | **🏆 NGHIỆM THU CUỐI KÌ (Toàn diện Đồ án)** | Hệ thống triển khai trên cụm K8s (HPA autoscaling); App Flutter Mobile (Dark Mode); Grafana Dashboard giám sát tải 10.000+ VU. |

---

## 7. Ma trận Quản lý Rủi ro (Risk Management Matrix)

| ID | Rủi ro tiềm ẩn | Mức độ | Khả năng | Chiến lược phòng ngừa / Ứng phó |
| :---: | :--- | :---: | :---: | :--- |
| **R1** | **Deadlock hoặc Treo Lock** khi dùng Redisson nếu luồng xử lý bị crash. | Cao | Thấp | Cài đặt `waitTime` giới hạn (vd: 2s) và `leaseTime` tự giải phóng. Bọc khối `try-finally` luôn đảm bảo gọi `lock.unlock()` an toàn. |
| **R2** | **Lệch dữ liệu** giữa Redis (đã trừ) và PostgreSQL khi có sự cố. | Rất cao | Trung bình | Triển khai cơ chế bù hoàn Try-Catch Compensation ngay trong Service (hoàn kho Redis nếu ghi DB lỗi). Hướng mở rộng dài hạn: Quét đối soát định kỳ (Reconciliation Job). |
| **R3** | **Khách không thanh toán làm giam kho** trong Flash Sale. | Cao | Cao | Áp dụng cơ chế **TTL Reservation (5 phút)**: Tự động hủy đơn và hoàn trả đúng số lượng (`quantity`) trên Redis, giải phóng giới hạn mua tương ứng nếu quá hạn. |
| **R4** | **Bot/Spam mua hàng vét kho** làm khách hàng thực không mua được. | Cao | Cao | Bổ sung Rate Limiter theo Token Bucket (Bucket4j) theo User ID/IP; chặn giới hạn mua mỗi User trên Redis. |
| **R5** | **Lỗi Callback từ Cổng thanh toán ZaloPay** do rớt mạng hoặc gửi chậm. | Trung bình | Trung bình | Xây dựng cơ chế Idempotent Webhook (xác thực chữ ký HMAC SHA256) + Scheduler chủ động gọi API Query Order Status của ZaloPay để đối soát. |

---

## 8. Tiêu chuẩn Hoàn thành Bước 1 (Definition of Done - DoD)

- [x] Làm rõ bối cảnh, mô hình Sàn Đa người bán và bài toán kiểm soát Over-selling.
- [x] Xác lập mục tiêu định lượng cụ thể (Kiểm soát Over-selling, P99 < 500ms).
- [x] Định ranh giới phạm vi: Tích hợp ZaloPay QR thật, COD, OAuth2, Hoa hồng sàn 5%.
- [x] Đánh giá đầy đủ tính khả thi (Kỹ thuật, Kinh tế 0 VNĐ, Vận hành).
- [x] Lập sơ đồ WBS và tiến độ Gantt 15 tuần.
- [x] Lập 5 mốc nghiệm thu kỹ thuật và ma trận phòng ngừa rủi ro.