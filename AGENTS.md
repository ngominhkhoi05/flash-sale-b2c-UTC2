# AGENTS.md - Mục lục Chỉ mục (Index)

> **Nguồn chân lý (Source of Truth)** cho mọi AI agent / Cursor session làm việc trên repo `flash-sale-b2c-UTC2`.
> Repo này là **Spring Boot backend** của hệ thống Flash Sale B2C Multi-Vendor Marketplace.
> Repo `flash-sale-b2c-fe` là **Next.js frontend**. Repo `deploy-b2c-utc2` chứa **docker-compose, Nginx, scripts deploy**.
> Mọi endpoint dưới đây trừ khi có ghi chú riêng đều thuộc về backend này.

> **Quan trọng**: Các quy tắc chi tiết đã được phân tách thành 48 file `.mdc` độc lập trong thư mục `.cursor/rules/`. File này đóng vai trò **chỉ mục ngắn gọn (Index)** giúp agent và nhà phát triển nhanh chóng tra cứu rule áp dụng cho từng module và tác vụ.

## Mục lục Chỉ mục

1. [Tổng quan Dự án (Project Overview)](#1-project-overview)
2. [Nguồn chân lý (Source of Truth)](#2-source-of-truth)
3. [Quy trình làm việc của Agent (Agent Workflow)](#3-agent-workflow)
4. [Phiên bản Java 25 bắt buộc (Java 25 - Mandatory)](#4-java-version---mandatory)
5. [Cấu trúc Dự án (Project Structure)](#5-project-structure)
6. [Quy tắc Kiến trúc Phân tầng (Architecture Rules)](#6-architecture-rules)
7. [Quy ước API chung (Common API Rules)](#7-common-api-rules)
8. [Cơ sở Dữ liệu (Database)](#8-database)
9. [Tính Toàn vẹn Dữ liệu (Database Integrity)](#9-database-integrity)
10. [Thiết kế 24 Bảng Cốt lõi (24-Table Design)](#10-24-table-design)
11. [Đơn hàng Đa Cửa hàng (Multi-Vendor Order)](#11-multi-vendor-order)
12. [Quyền Sở hữu Địa chỉ (Address Ownership)](#12-address-ownership)
13. [Kiến trúc Flash Sale (Flash Sale Architecture)](#13-flash-sale-architecture)
14. [Mô hình Tồn kho Flash Sale (Flash Sale Stock Model)](#14-flash-sale-stock-model)
15. [Giới hạn Lượt mua Flash Sale (Flash Sale Purchase Limit)](#15-flash-sale-purchase-limit)
16. [Hoàn kho khi Quá hạn (Timeout Rollback)](#16-timeout-rollback)
17. [Bù trừ Redis sang Database (Redis to Database Compensation)](#17-redis--database-compensation)
18. [Tính Nhất quán Flash Sale Item sang Order Item](#18-flash-sale-item--order-item-consistency)
19. [Vòng đời Flash Sale (Flash Sale Lifecycle)](#19-flash-sale-lifecycle)
20. [Thanh toán (Payment)](#20-payment)
21. [Ví điện tử (Wallet)](#21-wallet)
22. [Mã giảm giá (Voucher)](#22-voucher)
23. [Ảnh chụp nhanh Đơn hàng (Order Snapshots)](#23-order-snapshots)
24. [Đánh giá Sản phẩm (Product Review)](#24-product-review)
25. [Module Hình ảnh Đa hình (Polymorphic Image Module)](#25-image-module-polymorphic)
26. [Quản lý Giao dịch (Transaction)](#26-transaction)
27. [Xử lý Đồng thời (Concurrency)](#27-concurrency)
28. [Vấn đề N+1 Truy vấn (N+1 Query)](#28-n1)
29. [Quy tắc Phân trang (Pagination)](#29-pagination)
30. [Bảo mật & Phân quyền (Security)](#30-security)
31. [Quy ước MapStruct + Lombok](#31-mapstruct--lombok)
32. [Kiểm tra Dữ liệu (Validation)](#32-validation)
33. [Quy ước Chuẩn REST API (API Convention)](#33-api-convention)
34. [Quy ước Tính Idempotent (Idempotency Convention)](#34-idempotency-convention)
35. [Tác vụ Định kỳ Chạy nền (Background Jobs / Schedulers)](#35-background-jobs--schedulers)
36. [WebSocket Thời gian thực (STOMP + SockJS)](#36-realtime-websocket-stomp--sockjs)
37. [Cấu hình CORS & Chuỗi Bảo mật](#37-cors--security-wiring)
38. [Quy ước Mã lỗi (Error Code Convention)](#38-error-code-convention)
39. [Công cụ Xây dựng & Môi trường (Build & Toolchain)](#39-build--toolchain)
40. [Quy chuẩn Kiểm thử (Testing)](#40-testing)
41. [Quy trình Git (Git Workflow)](#41-git-workflow)
42. [Không Thiết kế Vượt Nhu cầu (No Premature Over-Engineering)](#42-no-premature-over-engineering)
43. [Bố cục Liên kết Đa Repository (Cross-Repo Layout)](#43-cross-repo-layout)
44. [Danh sách Kiểm tra Thực thi Task (Task Execution Checklist)](#44-task-execution-checklist)
45. [Định dạng Báo cáo Kết quả (Required Final Response Format)](#45-required-final-response-format)
46. [Danh sách Hợp đồng API các Module](#46-auth-user-store-product-flash-sale-endpoints)
47. [Quy trình Đồng bộ Tài liệu & Mã nguồn (Documentation & Code Sync)](#47-documentation--code-sync-checklist)
48. [Ma trận Tác động của Migration (Migration Impact Matrix)](#48-migration-impact-matrix)
49. [Xóa Liên hoàn (Cascade Delete)](#49-cascade-delete)

---

## 1. Project Overview
Xem `.cursor/rules/01-project-overview.mdc`.

## 2. Source of Truth
Xem `.cursor/rules/02-source-of-truth.mdc`.

## 3. Agent Workflow
Xem `.cursor/rules/03-agent-workflow.mdc`.

## 4. Java Version - Mandatory
Xem `.cursor/rules/04-java-25.mdc`.

## 5. Project Structure
Xem `.cursor/rules/05-project-structure.mdc`.

## 6. Architecture Rules
Xem `.cursor/rules/06-architecture-layers.mdc`.

## 7. Common API Rules
Xem `.cursor/rules/07-api-conventions.mdc` + `.cursor/rules/08-error-codes.mdc`.

## 8. Database
Xem `.cursor/rules/09-database.mdc`.

## 9. Database Integrity
Xem `.cursor/rules/09-database.mdc` (phần 3 loại quy tắc) + `.cursor/rules/10-24-table-design.mdc`.

## 10. 24-Table Design
Xem `.cursor/rules/10-24-table-design.mdc`.

## 11. Multi-Vendor Order
Xem `.cursor/rules/11-multi-vendor-order.mdc`.

## 12. Address Ownership
Xem `.cursor/rules/12-address-ownership.mdc`.

## 13. Flash Sale Architecture
Xem `.cursor/rules/13-flashsale-architecture.mdc`.

## 14. Flash Sale Stock Model
Xem `.cursor/rules/14-flashsale-stock.mdc`.

## 15. Flash Sale Purchase Limit
Xem `.cursor/rules/15-flashsale-purchase-limit.mdc`.

## 16. Timeout Rollback
Xem `.cursor/rules/16-flashsale-rollback.mdc`.

## 17. Redis to Database Compensation
Xem `.cursor/rules/16-flashsale-rollback.mdc` (phần bù trừ) + `.cursor/rules/26-transaction.mdc`.

## 18. Flash Sale Item to Order Item Consistency
Xem `.cursor/rules/13-flashsale-architecture.mdc` (ranh giới FlashSaleOrderPort).

## 19. Flash Sale Lifecycle
Xem `.cursor/rules/17-flashsale-lifecycle.mdc` + `.cursor/rules/18-concurrency.mdc` + `.cursor/rules/19-schedulers.mdc`.

## 20. Payment
Xem `.cursor/rules/35-payment.mdc` + `.cursor/rules/47-endpoints-payment-wallet-review.mdc`.

## 21. Wallet
Xem `.cursor/rules/36-wallet.mdc` + `.cursor/rules/47-endpoints-payment-wallet-review.mdc`.

## 22. Voucher
Xem `.cursor/rules/37-voucher.mdc` + `.cursor/rules/46-endpoints-cart-order-voucher-banner.mdc`.

## 23. Order Snapshots
Xem `.cursor/rules/38-order-snapshots.mdc`.

## 24. Product Review
Xem `.cursor/rules/39-review.mdc` + `.cursor/rules/47-endpoints-payment-wallet-review.mdc`.

## 25. Image Module (Polymorphic)
Xem `.cursor/rules/40-image-module.mdc` + `.cursor/rules/44-endpoints-product-image.mdc`.

## 26. Transaction
Xem `.cursor/rules/26-transaction.mdc`.

## 27. Concurrency
Xem `.cursor/rules/18-concurrency.mdc`.

## 28. N+1
Xem `.cursor/rules/20-n-plus-one.mdc`.

## 29. Pagination
Xem `.cursor/rules/21-pagination.mdc`.

## 30. Security
Xem `.cursor/rules/22-security.mdc`.

## 31. MapStruct + Lombok
Xem `.cursor/rules/23-mapstruct-lombok.mdc`.

## 32. Validation
Xem `.cursor/rules/24-validation.mdc`.

## 33. API Convention
Xem `.cursor/rules/07-api-conventions.mdc`.

## 34. Idempotency Convention
Xem `.cursor/rules/25-idempotency.mdc`.

## 35. Background Jobs / Schedulers
Xem `.cursor/rules/19-schedulers.mdc`.

## 36. Realtime WebSocket (STOMP + SockJS)
Xem `.cursor/rules/28-websocket.mdc`.

## 37. CORS and Security Wiring
Xem `.cursor/rules/22-security.mdc` (phần CORS + chuỗi filter).

## 38. Error Code Convention
Xem `.cursor/rules/08-error-codes.mdc`.

## 39. Build and Toolchain
Xem `.cursor/rules/04-java-25.mdc` + `.cursor/rules/29-build-toolchain.mdc`.

## 40. Testing
Xem `.cursor/rules/27-testing.mdc`.

## 41. Git Workflow
Xem `.cursor/rules/30-git-workflow.mdc`.

## 42. No Premature Over-Engineering
Xem `.cursor/rules/31-no-overengineering.mdc`.

## 43. Cross-Repo Layout
Xem `.cursor/rules/32-cross-repo.mdc`.

## 44. Task Execution Checklist
Xem `.cursor/rules/33-task-checklist.mdc`.

## 45. Required Final Response Format
Xem `.cursor/rules/34-response-format.mdc`.

## 46. Auth, User, Store, Product, Flash Sale Endpoints
- Auth / User / Address / Store: `.cursor/rules/43-endpoints-auth-user-store.mdc`.
- Category / Product / Image: `.cursor/rules/44-endpoints-product-image.mdc`.
- Flash Sale: `.cursor/rules/45-endpoints-flashsale.mdc`.
- Cart / Order / Voucher / Banner: `.cursor/rules/46-endpoints-cart-order-voucher-banner.mdc`.
- Payment / Wallet / Review: `.cursor/rules/47-endpoints-payment-wallet-review.mdc`.

## 47. Documentation and Code Sync Checklist
Xem `.cursor/rules/41-docs-sync.mdc`.

## 48. Migration Impact Matrix
Xem `.cursor/rules/42-migration-impact-matrix.mdc`.

## 49. Cascade Delete
Xem `.cursor/rules/48-cascade-delete.mdc`.

---

## Ghi chú Quan trọng

- File này là **bản chỉ mục rút gọn** (~ 280 dòng) thay thế cho tài liệu `AGENTS.md` cũ (2290 dòng).
- Mọi quy tắc kỹ thuật và nghiệp vụ chi tiết đã được tách thành các file `.mdc` riêng biệt trong `.cursor/rules/`.
- Khi agent mở hoặc đọc file code (Java / SQL / YAML), Cursor sẽ tự động nạp các rule `.mdc` có `globs` tương ứng.
- Khi chỉnh sửa nội dung một rule `.mdc`, KHÔNG cần sửa `AGENTS.md` (chỉ cần sửa file rule tương ứng).
- Khi bổ sung rule mới, cập nhật bảng chỉ mục trong `AGENTS.md` và tạo file `.mdc` mới.

## Lịch sử Thay đổi

- v1: `AGENTS.md` gốc gồm 2290 dòng, 48 mục, gom trong 1 file duy nhất.
- v2: Tách thành 48 file `.mdc` trong `.cursor/rules/`, `AGENTS.md` đóng vai trò bản chỉ mục ~ 280 dòng.
- v3: Chuẩn hóa toàn bộ nội dung sang tiếng Việt có dấu đầy đủ, chuẩn ngữ pháp kỹ thuật và mã hóa UTF-8 no BOM.
