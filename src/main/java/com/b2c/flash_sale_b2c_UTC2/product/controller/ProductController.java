package com.b2c.flash_sale_b2c_UTC2.product.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductDetailResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductFilterRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.RecommendedProductsResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchResponse;
import com.b2c.flash_sale_b2c_UTC2.product.enums.ProductSortOption;
import com.b2c.flash_sale_b2c_UTC2.product.enums.SearchCity;
import com.b2c.flash_sale_b2c_UTC2.product.enums.SearchService;
import com.b2c.flash_sale_b2c_UTC2.product.enums.SearchSortKey;
import com.b2c.flash_sale_b2c_UTC2.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Product - Public API", description = "Xem và tìm kiếm danh mục sản phẩm sàn B2C")
public class ProductController {

    private final ProductService productService;
    private final com.b2c.flash_sale_b2c_UTC2.search.service.SearchService searchService;

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
            @Parameter(description = "Lọc theo category ID")
            @RequestParam(value = "categoryId", required = false) Integer categoryId,

            @Parameter(description = "Từ khoá tìm kiếm theo tên sản phẩm")
            @RequestParam(value = "keyword", required = false) String keyword,

            @Parameter(description = "Giá tối thiểu (VND)")
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,

            @Parameter(description = "Giá tối đa (VND)")
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,

            @Parameter(description = "Rating tối thiểu (0-5). Lọc sản phẩm có ratingAvg >= value.")
            @RequestParam(value = "rating", required = false) Integer rating,

            @Parameter(description = "Chỉ sản phẩm đang Flash Sale (true) hoặc tất cả (false/null)")
            @RequestParam(value = "flashSaleOnly", required = false) Boolean flashSaleOnly,

            @Parameter(description = "Chỉ sản phẩm đang giảm giá (true) hoặc tất cả (false/null)")
            @RequestParam(value = "hasDiscount", required = false) Boolean hasDiscount,

            @Parameter(description = "Đã bán tối thiểu (lọc sản phẩm có totalSold >= value)")
            @RequestParam(value = "minSold", required = false) Integer minSold,

            @Parameter(description = "Tuỳ chọn sắp xếp: createdAt_desc | createdAt_asc | price_asc | price_desc | sold_desc | rating_desc",
                    schema = @Schema(implementation = ProductSortOption.class))
            @RequestParam(value = "sortOption", required = false) ProductSortOption sortOption,

            @Parameter(description = "Phân trang (page=0, size=20, sort=createdAt,DESC mặc định)")
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
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm thành công", response));
    }

    @GetMapping("/search")
    @Operation(summary = "Tìm kiếm sản phẩm cho storefront /search (Public)",
               description = "Trả về items + total + facets (subcategories, cities, ratings, services). " +
                             "Sort: relevance (default) | newest | best_selling | price_asc | price_desc. " +
                             "Phân trang bằng cursor opaque (không dùng page). " +
                             "Trả về nextCursor (null = hết kết quả) + hasMore + totalIsExact.")
    public ResponseEntity<ApiResponse<SearchResponse>> searchProducts(
            @Parameter(description = "Từ khoá tìm theo tên sản phẩm (LIKE %q%)")
            @RequestParam(value = "q", required = false) String q,

            @Parameter(description = "Danh sách subcategory key (vd: true-wireless)")
            @RequestParam(value = "subcategory", required = false) List<String> subcategory,

            @Parameter(description = "Danh sách city (HCM | HN | DN | BD) - hiện tại chỉ facet, không filter DB")
            @RequestParam(value = "city", required = false) List<String> city,

            @Parameter(description = "Danh sách service flag (flash-sale | voucher | freeship | official)")
            @RequestParam(value = "service", required = false) List<String> service,

            @Parameter(description = "Giá tối thiểu (VND)")
            @RequestParam(value = "priceMin", required = false) BigDecimal priceMin,

            @Parameter(description = "Giá tối đa (VND)")
            @RequestParam(value = "priceMax", required = false) BigDecimal priceMax,

            @Parameter(description = "Rating tối thiểu (3 | 4 | 5)")
            @RequestParam(value = "rating", required = false) Integer rating,

            @Parameter(description = "Sort key: relevance | newest | best_selling | price_asc | price_desc",
                    schema = @Schema(implementation = SearchSortKey.class))
            @RequestParam(value = "sort", required = false) String sort,

            @Parameter(description = "Cursor opaque cho trang tiếp theo (lấy từ nextCursor của response trước)")
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        List<SearchCity> cities = city == null ? null : city.stream()
                .map(SearchCity::fromKey)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<SearchService> services = service == null ? null : service.stream()
                .map(SearchService::fromKey)
                .filter(java.util.Objects::nonNull)
                .toList();

        SearchRequest req = SearchRequest.builder()
                .q(q)
                .subcategories(subcategory)
                .cities(city)  // raw strings consumed by the service for facet count
                .services(services)
                .priceMin(priceMin)
                .priceMax(priceMax)
                .rating(rating)
                .sort(SearchSortKey.fromValue(sort))
                .build();

        SearchResponse response = searchService.search(req, cursor);
        return ResponseEntity.ok(ApiResponse.success("Tìm kiếm sản phẩm thành công", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết sản phẩm và các biến thể phân loại SKU (Public)",
               description = "Chỉ trả về sản phẩm ACTIVE của cửa hàng APPROVED")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getPublicProductDetail(@PathVariable Long id) {
        ProductDetailResponse response = productService.getPublicProductDetail(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết sản phẩm thành công", response));
    }
}
