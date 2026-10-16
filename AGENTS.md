# AGENTS.md - Index

> **Source of truth** cho moi AI agent / Cursor session lam viec tren repo `flash-sale-b2c-UTC2`.
> Repo nay la **Spring Boot backend** cua he thong Flash Sale B2C Multi-Vendor Marketplace.
> Repo `flash-sale-b2c` la **Next.js frontend**. Repo `deploy-b2c-utc2` chua **docker-compose, nginx, scripts deploy**.
> Moi endpoint duoi day tru khi ghi chu la thuoc backend nay.

> **Quan trong**: Quy tac chi tiet da duoc tach ra thanh cac file `.mdc` trong `.cursor/rules/`. File nay chi la **index ngan gon** de agent biet rule nao ap dung cho file nao.

## Muc luc

1. [Project Overview](#1-project-overview)
2. [Source of Truth](#2-source-of-truth)
3. [Agent Workflow](#3-agent-workflow)
4. [Java Version - Mandatory](#4-java-version---mandatory)
5. [Project Structure](#5-project-structure)
6. [Architecture Rules](#6-architecture-rules)
7. [Common API Rules](#7-common-api-rules)
8. [Database](#8-database)
9. [Database Integrity](#9-database-integrity)
10. [24-Table Design](#10-24-table-design)
11. [Multi-Vendor Order](#11-multi-vendor-order)
12. [Address Ownership](#12-address-ownership)
13. [Flash Sale Architecture](#13-flash-sale-architecture)
14. [Flash Sale Stock Model](#14-flash-sale-stock-model)
15. [Flash Sale Purchase Limit](#15-flash-sale-purchase-limit)
16. [Timeout Rollback](#16-timeout-rollback)
17. [Redis to Database Compensation](#17-redis--database-compensation)
18. [Flash Sale Item to Order Item Consistency](#18-flash-sale-item--order-item-consistency)
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
37. [CORS and Security Wiring](#37-cors--security-wiring)
38. [Error Code Convention](#38-error-code-convention)
39. [Build and Toolchain](#39-build--toolchain)
40. [Testing](#40-testing)
41. [Git Workflow](#41-git-workflow)
42. [No Premature Over-Engineering](#42-no-premature-over-engineering)
43. [Cross-Repo Layout](#43-cross-repo-layout)
44. [Task Execution Checklist](#44-task-execution-checklist)
45. [Required Final Response Format](#45-required-final-response-format)
46. [Auth, User, Store, Product, Flash Sale Endpoints](#46-auth-user-store-product-flash-sale-endpoints)
47. [Documentation and Code Sync Checklist](#47-documentation--code-sync-checklist)
48. [Migration Impact Matrix](#48-migration-impact-matrix)
49. [Cascade Delete](#49-cascade-delete)

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

Xem `.cursor/rules/09-database.mdc` (phan 3 loai rule) + `.cursor/rules/10-24-table-design.mdc`.

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

Xem `.cursor/rules/16-flashsale-rollback.mdc` (compensation) + `.cursor/rules/26-transaction.mdc` (Redis != DB).

## 18. Flash Sale Item to Order Item Consistency

Xem `.cursor/rules/13-flashsale-architecture.mdc` (FlashSaleOrderPort boundary).

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

Xem `.cursor/rules/26-transaction.mdc` (dat o vi tri 26 theo AGENTS.md goc).

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

Xem `.cursor/rules/22-security.mdc` (phan CORS + filter chain).

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

- Auth/User/Store: `.cursor/rules/43-endpoints-auth-user-store.mdc`.
- Category/Product/Image: `.cursor/rules/44-endpoints-product-image.mdc`.
- Flash Sale: `.cursor/rules/45-endpoints-flashsale.mdc`.
- Cart/Order/Voucher/Banner: `.cursor/rules/46-endpoints-cart-order-voucher-banner.mdc`.
- Payment/Wallet/Review: `.cursor/rules/47-endpoints-payment-wallet-review.mdc`.

## 47. Documentation and Code Sync Checklist

Xem `.cursor/rules/41-docs-sync.mdc`.

## 48. Migration Impact Matrix

Xem `.cursor/rules/42-migration-impact-matrix.mdc`.

## 49. Cascade Delete

Xem `.cursor/rules/48-cascade-delete.mdc`.

---

## Ghi chu quan trong

- File nay chi la **index ngan gon** (~ 350 dong) thay the cho AGENTS.md goc (2290 dong).
- Moi quy tac chi tiet da duoc tach ra thanh file `.mdc` rieng trong `.cursor/rules/`.
- Khi agent doc file code (Java/SQL/YAML), Cursor se tu dong load rule `.mdc` co `globs` tuong ung.
- Khi sua rule `.mdc`, KHONG can sua AGENTS.md (chi can sua rule tuong ung).
- Khi them rule moi, cap nhat index trong AGENTS.md + tao file `.mdc` moi.

## Lich su

- v1: AGENTS.md goc 2290 dong, 48 muc, 1 file.
- v2: Tach thanh ~ 48 file `.mdc` trong `.cursor/rules/`, AGENTS.md con ~ 350 dong lam index.