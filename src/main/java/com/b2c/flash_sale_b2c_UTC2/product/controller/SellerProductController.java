package com.b2c.flash_sale_b2c_UTC2.product.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.CreateProductRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductDetailResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.UpdateProductRequest;
import com.b2c.flash_sale_b2c_UTC2.product.service.ProductService;
import com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/seller/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - Product Management API", description = "Quản lý sản phẩm SPU và biến thể phân loại SKU dành cho Người bán")
public class SellerProductController {

    private final ProductService productService;
    private final ImageService imageService;

    @PostMapping
    @Operation(summary = "Đăng bán sản phẩm mới (SPU-SKU)", description = "Yêu cầu cửa hàng phải ở trạng thái APPROVED và khớp cấu hình biến thể")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductDetailResponse response = productService.createProduct(userDetails.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng bán sản phẩm thành công", response));
    }

    @GetMapping
    @Operation(summary = "Danh sách sản phẩm của người bán", description = "Xem danh sách sản phẩm thuộc gian hàng của seller hiện tại")
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> getSellerProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<ProductSummaryResponse> response = productService.getSellerProducts(
                userDetails.getUser().getId(),
                status,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm gian hàng thành công", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết sản phẩm của người bán", description = "Xem chi tiết SPU và toàn bộ biến thể SKU thuộc gian hàng")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getSellerProductDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        ProductDetailResponse response = productService.getSellerProductDetail(userDetails.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết sản phẩm thành công", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật sản phẩm và biến thể", description = "Cập nhật thông tin SPU và SKU (chống sửa giá/stock khi đang Flash Sale ACTIVE)")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        ProductDetailResponse response = productService.updateProduct(userDetails.getUser().getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin sản phẩm thành công", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa sản phẩm (Tự động Soft/Hard Delete)", description = "Soft delete nếu đã có đơn hàng/flash sale; Hard delete nếu chưa phát sinh")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        productService.deleteProduct(userDetails.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Xóa sản phẩm thành công", null));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Đổi trạng thái sản phẩm (ACTIVE/INACTIVE/OUT_OF_STOCK)", description = "Thay đổi nhanh trạng thái hiển thị của sản phẩm")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProductStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestParam String status
    ) {
        ProductDetailResponse response = productService.updateProductStatus(userDetails.getUser().getId(), id, status);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái sản phẩm thành công", response));
    }

    @Operation(summary = "Upload ảnh mới cho sản phẩm SPU)",
            description = "Một sản phẩm có thể có nhiều ảnh gallery; partial unique index đảm bảo tối đa 1 ảnh primary ACTIVE.")
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageResponse>> uploadProductImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "isPrimary", required = false) Boolean isPrimary,
            @RequestParam(value = "displayOrder", required = false) Integer displayOrder
    ) {
        ImageResponse resp = imageService.attachImage(
                userDetails.getUser().getId(), ImageOwnerType.PRODUCT, id, file, isPrimary, displayOrder);
        return ResponseEntity.status(HttpStatusCode.valueOf(201))
                .body(ApiResponse.success("Upload ảnh sản phẩm thành công", resp));
    }

    @Operation(summary = "Upload ảnh mới cho một biến thể SKU)",
            description = "Mỗi biến thể có thể có nhiều ảnh; partial unique index đảm bảo tối đa 1 ảnh primary ACTIVE.")
    @PostMapping(value = "/{productId}/variants/{variantId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageResponse>> uploadVariantImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "isPrimary", required = false) Boolean isPrimary,
            @RequestParam(value = "displayOrder", required = false) Integer displayOrder
    ) {
        // productId giữ để endpoint semantic rõ ràng; variant id thuộc cùng store nên ownership check đã đảm bảo.
        ImageResponse resp = imageService.attachImage(
                userDetails.getUser().getId(), ImageOwnerType.VARIANT, variantId, file, isPrimary, displayOrder);
        return ResponseEntity.status(HttpStatusCode.valueOf(201))
                .body(ApiResponse.success("Upload ảnh biến thể thành công", resp));
    }
}
