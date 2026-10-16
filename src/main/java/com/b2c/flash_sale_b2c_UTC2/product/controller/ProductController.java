package com.b2c.flash_sale_b2c_UTC2.product.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductDetailResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductFilterRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.RecommendedProductsResponse;
import com.b2c.flash_sale_b2c_UTC2.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Product - Public API", description = "Xem và tìm kiếm danh mục sản phẩm sàn B2C")
public class ProductController {

    private final ProductService productService;

    @GetMapping("/recommended")
    @Operation(summary = "Lay san pham recommended (for_you, best_seller, hot_deal) - Public",
               description = "Tra ve danh sach san pham theo tab. Tab=all (mac dinh): tra ve ca 3 danh sach. Tab=for_you|best_seller|hot_deal: tra ve 1 danh sach. Limit mac dinh=10.")
    public ResponseEntity<ApiResponse<RecommendedProductsResponse>> getRecommendedProducts(
            @Parameter(description = "Tab: all, for_you, best_seller, hot_deal")
            @RequestParam(value = "tab", required = false, defaultValue = "all") String tab,
            @Parameter(description = "Loc theo categoryId")
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @Parameter(description = "So luong san pham moi tab")
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit
    ) {
        RecommendedProductsResponse response = productService.getRecommendedProducts(tab, categoryId, limit);
        return ResponseEntity.ok(ApiResponse.success("Lay san pham recommended thanh cong", response));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Xem chi tiet san pham theo slug SEO (Public)",
               description = "Chi tra ve san pham ACTIVE cua cua hang APPROVED")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getPublicProductDetailBySlug(@PathVariable String slug) {
        ProductDetailResponse response = productService.getPublicProductDetailBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success("Lay chi tiet san pham thanh cong", response));
    }

    @GetMapping
    @Operation(summary = "Tìm kiếm & lọc danh sách sản phẩm sàn (Public)",
               description = "Hỗ trợ lọc theo categoryId, từ khoá keyword, khoang giá minPrice-maxPrice, phân trang PageResponse. " +
                             "Để xem chi tiết sản phẩm theo ID dùng GET /api/v1/products/{id}, theo slug dùng GET /api/v1/products/slug/{slug}.")
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> getPublicProducts(
            @ModelAttribute ProductFilterRequest filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<ProductSummaryResponse> response = productService.getPublicProducts(filter, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm thành công", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết sản phẩm và các biến thể phân loại SKU (Public)",
               description = "Chỉ trả về sản phẩm ACTIVE của cửa hàng APPROVED")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getPublicProductDetail(@PathVariable Long id) {
        ProductDetailResponse response = productService.getPublicProductDetail(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết sản phẩm thành công", response));
    }
}
