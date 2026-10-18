# Plan: Swagger Flatten `@ModelAttribute` cho Pagination Endpoints

> **Date**: 2026-10-16
> **Branch**: `refactor/swagger-flatten-product-filter`
> **Author**: Cursor Agent
> **Workflow ref**: `.cursor/rules/30-git-workflow.mdc` (9 bước bắt buộc) + `.cursor/rules/33-task-checklist.mdc`

---

## 1. Scope

### Mục tiêu

Hiện tại Swagger UI render **2 khung JSON tĩnh** (Schema) thay vì các ô input cho query params khi dùng pattern `@ModelAttribute SomeRequest` + `Pageable`. Nguyên nhân là springdoc-openapi 3.1.0 có behavior khác với Spring Boot 4.1.1 khi render `@ModelAttribute` nested object — nó gom thành 1 schema object chứ không flatten thành từng query param có input riêng.

**Fix**: Tách các field của DTO filter thành `@RequestParam` riêng biệt trên controller, build DTO ở method body trước khi gọi service. Service signature **giữ nguyên**.

### File trong scope

| File | Action | Lý do |
|---|---|---|
| `src/main/java/com/b2c/flash_sale_b2c_UTC2/product/controller/ProductController.java` | **Sửa** | Method `getPublicProducts` dùng `@ModelAttribute ProductFilterRequest` |
| `src/main/java/com/b2c/flash_sale_b2c_UTC2/review/controller/ProductReviewPublicController.java` | **Sửa** | Tuy dùng `@RequestParam` riêng nhưng convention hiện tại chưa thêm `@Parameter` description cho Swagger, làm cho doc kém. Thêm description + format đồng nhất. |
| `src/main/java/com/b2c/flash_sale_b2c_UTC2/product/controller/SellerProductController.java` | **Sửa** | Tương tự review controller — thêm `@Parameter` description cho Swagger. |
| `src/main/java/com/b2c/flash_sale_b2c_UTC2/product/dto/ProductFilterRequest.java` | **KHÔNG đụng** | DTO vẫn dùng cho service, giữ nguyên. |
| `src/main/java/com/b2c/flash_sale_b2c_UTC2/product/service/ProductService.java` | **KHÔNG đụng** | Service signature giữ nguyên. |
| `src/main/java/com/b2c/flash_sale_b2c_UTC2/review/service/ReviewService.java` | **KHÔNG đụng** | Tương tự. |
| `src/test/java/.../product/service/ProductServiceTest.java` | **KHÔNG sửa** (chỉ chạy verify) | Logic filter không đổi. |
| `src/test/java/.../product/controller/ProductControllerTest.java` (nếu tồn tại) | **Chạy verify** | Đảm bảo binding param vẫn đúng. |
| `src/test/java/.../review/controller/ProductReviewPublicControllerTest.java` | **Chạy verify** | Đảm bảo description mới không break. |

### Endpoint được cải thiện Swagger render

| Method | URL | Trước | Sau |
|---|---|---|---|
| `GET` | `/api/v1/products` | 2 khung JSON: `filter` + `pageable` (Schema only) | 12 ô input riêng biệt (9 filter + 3 pageable) |
| `GET` | `/api/v1/products/{id}/reviews` | 4 ô input + 1 Pageable (đã OK nhưng thiếu description) | 4 ô input có description chi tiết + 1 Pageable |
| `GET` | `/api/v1/seller/products` | 2 ô input + 1 Pageable (đã OK nhưng thiếu description) | 2 ô input có description chi tiết + 1 Pageable |

---

## 2. Tham khảo (Source of Truth)

- `.cursor/rules/30-git-workflow.mdc` — 9 bước bắt buộc + hard gate không commit lên dev.
- `.cursor/rules/33-task-checklist.mdc` — checklist 9 mục.
- `.cursor/rules/27-testing.mdc` — TDD workflow.
- `.cursor/rules/26-transaction.mdc` — không liên quan, bỏ qua.
- `.cursor/rules/22-security.mdc` — vì endpoint là public, không cần auth check.
- `src/main/java/com/b2c/flash_sale_b2c_UTC2/config/OpenApiConfig.java` — config global của Swagger.
- Commit gần đây `3657fdd` (16/10/2026) — đã fix return type của `getPublicProducts` theo convention `06-architecture-layers.mdc`, cần giữ nguyên.

---

## 3. Thay đổi chi tiết

### 3.1 `ProductController.getPublicProducts`

**Trước:**
```java
@GetMapping
@Operation(summary = "...", description = "...")
public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> getPublicProducts(
    @ModelAttribute ProductFilterRequest filter,
    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
) {
    PageResponse<ProductSummaryResponse> response = productService.getPublicProducts(filter, pageable);
    return ResponseEntity.ok(ApiResponse.success("...", response));
}
```

**Sau:**
```java
@GetMapping
@Operation(summary = "...", description = "...")
public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> getPublicProducts(
    @Parameter(description = "Lọc theo category ID")
    @RequestParam(required = false) Integer categoryId,
    @Parameter(description = "Từ khoá tìm kiếm (tên sản phẩm)")
    @RequestParam(required = false) String keyword,
    @Parameter(description = "Giá tối thiểu (VND)")
    @RequestParam(required = false) BigDecimal minPrice,
    @Parameter(description = "Giá tối đa (VND)")
    @RequestParam(required = false) BigDecimal maxPrice,
    @Parameter(description = "Rating tối thiểu (0-5)")
    @RequestParam(required = false) Integer rating,
    @Parameter(description = "Chỉ sản phẩm đang Flash Sale")
    @RequestParam(required = false) Boolean flashSaleOnly,
    @Parameter(description = "Chỉ sản phẩm đang giảm giá")
    @RequestParam(required = false) Boolean hasDiscount,
    @Parameter(description = "Đã bán tối thiểu")
    @RequestParam(required = false) Integer minSold,
    @Parameter(description = "Sắp xếp: createdAt_desc | createdAt_asc | price_asc | price_desc | sold_desc | rating_desc",
               schema = @Schema(implementation = ProductSortOption.class))
    @RequestParam(required = false) ProductSortOption sortOption,
    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
) {
    ProductFilterRequest filter = ProductFilterRequest.builder()
        .categoryId(categoryId)
        .keyword(keyword)
        .minPrice(minPrice)
        .maxPrice(maxPrice)
        .rating(rating)
        .flashSaleOnly(flashSaleOnly)
        .hasDiscount(hasDiscount)
        .minSold(minSold)
        .sortOption(sortOption)
        .build();
    PageResponse<ProductSummaryResponse> response = productService.getPublicProducts(filter, pageable);
    return ResponseEntity.ok(ApiResponse.success("...", response));
}
```

Cần thêm import: `io.swagger.v3.oas.annotations.media.Schema` + `com.b2c.flash_sale_b2c_UTC2.product.enums.ProductSortOption`.

### 3.2 `ProductReviewPublicController.getProductReviews`

**Trước:**
```java
public ResponseEntity<...> getProductReviews(
    @PathVariable Long productId,
    @RequestParam(required = false) Integer rating,
    @RequestParam(required = false) Boolean hasMedia,
    @PageableDefault(size = 10) Pageable pageable) { ... }
```

**Sau:** thêm `@Parameter(description = ...)` cho `rating` và `hasMedia`.

### 3.3 `SellerProductController.getSellerProducts`

**Trước:**
```java
public ResponseEntity<...> getSellerProducts(
    @AuthenticationPrincipal CustomUserDetails userDetails,
    @RequestParam(required = false) String status,
    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
) { ... }
```

**Sau:** thêm `@Parameter(description = "Trạng thái sản phẩm: ACTIVE | INACTIVE | OUT_OF_STOCK")` cho `status`.

---

## 4. Test Strategy

### Phương pháp: Integration test bằng curl (theo lựa chọn của user)

| Bước | Lệnh | Verify |
|---|---|---|
| 1. Boot app | `gradlew bootRun` (background) | App start ở port 8080 |
| 2. Wait | `curl --retry 30 --retry-delay 1 --retry-connrefused http://localhost:8080/actuator/health` | HTTP 200 |
| 3. Test `getPublicProducts` | `curl "http://localhost:8080/api/v1/products?categoryId=5&sortOption=rating_desc&page=0&size=20"` | 200 + JSON page |
| 4. Test `getPublicProducts` với `flashSaleOnly=true` | `curl "http://localhost:8080/api/v1/products?flashSaleOnly=true"` | 200 + filter apply |
| 5. Test `getProductReviews` | `curl "http://localhost:8080/api/v1/products/1/reviews?rating=5&page=0&size=10"` | 200 + JSON |
| 6. Test `getSellerProducts` (cần auth token) | `curl -H "Authorization: Bearer <token>" "http://localhost:8080/api/v1/seller/products?status=ACTIVE"` | 200 + JSON |
| 7. Verify Swagger JSON | `curl http://localhost:8080/v3/api-docs > /tmp/api-spec.json` | OpenAPI spec có 12 query params riêng cho `/api/v1/products` |
| 8. Stop app | Kill background bootRun | — |

### Verify Swagger render

Mở `http://localhost:8080/swagger-ui.html`, xem:
- GET `/api/v1/products` → có 12 ô input (9 filter + 3 pageable).
- GET `/api/v1/products/{id}/reviews` → có 5 ô input (productId, rating, hasMedia, page, size) + description.
- GET `/api/v1/seller/products` → có 4 ô input (status, page, size, sort) + description.

---

## 5. Rủi ro và giảm thiểu

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| `@RequestParam` Spring binding khác với `@ModelAttribute` cho Boolean | Thấp | Spring xử lý tương đương cho cả 2 cách. Test với `?flashSaleOnly=true`, `?flashSaleOnly=false`, và không truyền param. |
| Build DTO trong controller có thể sai field nào đó (vd `null` vs absent) | Trung bình | Builder pattern với từng field set rõ ràng. Test verify `null` cho field không truyền. |
| Test cũ fail do thay đổi signature | Thấp | Test cũ test service, không test controller binding. Controller test nếu có sẽ chạy lại. |
| Default value của enum `sortOption` bị mất khi truyền empty string | Thấp | Spring tự chuyển `?sortOption=` empty → `null`. Builder truyền `null` → service đã có fallback `ProductSortOption.CREATED_AT_DESC`. |
| `Boolean flashSaleOnly` nhận string khác (`yes`, `1`) | Thấp | Spring chỉ chấp nhận `true`/`false`. Nếu FE cần `1`/`0`, đã có FE/Flash Sale ở repo `flash-sale-b2c-fe` — không thuộc scope. |

---

## 6. Subplan

Không cần chia subplan — chỉ 1 method + 1 test manual + 1 commit. Subagent không cần thiết (rule `33-task-checklist.mdc` mục "Khi nào KHÔNG cần subagent").

---

## 7. Tham chiếu commit gần đây (đã check ở Bước 0.0)

- `3657fdd` (16/10/2026 18:44) — `refactor(product): remove 2-in-1 GET /api/v1/products`. Đã sửa `ProductController.java`. **Refactor hiện tại dựa trên nền commit này** — không conflict vì chỉ thêm param, không xoá method.
- `c9f48ea` (15/10/2026) — `fix(product): replace root.join('variants') with EXISTS subquery`. Không ảnh hưởng controller.
- `15eddcb` (15/10/2026) — `fix(flashsale): extract slot-close logic`. Không liên quan.

---

## 8. Definition of Done

- [ ] Tạo nhánh `refactor/swagger-flatten-product-filter` từ `origin/dev`.
- [ ] Sửa 3 file controller.
- [ ] `gradlew compileJava` PASS.
- [ ] `gradlew test` PASS (existing tests không break).
- [ ] `gradlew bootRun` + curl 7 endpoint trên đều 200.
- [ ] `curl http://localhost:8080/v3/api-docs` có 12 query params cho `/api/v1/products` (thay vì 2 object schema).
- [ ] Auto-commit với message tiếng Anh + Conventional Commit format.
- [ ] Báo cáo theo format `34-response-format.mdc`.
- [ ] **KHÔNG push** (chờ user duyệt).

---

## 9. Out of Scope

- Không sửa `ProductFilterRequest.java` (DTO giữ nguyên cho service).
- Không sửa `application.yaml` (Cách 1 — config) — vì user đã chọn Cách 2.
- Không sửa FE repo `flash-sale-b2c-fe` (URL giữ nguyên, không ảnh hưởng).
- Không tạo test mới trong `src/test/java/...` (chỉ chạy curl integration test).
- Không thêm `@Parameter` cho các endpoint khác (chỉ 3 trong scope).
