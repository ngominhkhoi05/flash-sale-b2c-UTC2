package com.b2c.flash_sale_b2c_UTC2.product.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.AdminProductResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.UpdateProductStatusRequest;
import com.b2c.flash_sale_b2c_UTC2.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Products", description = "Quản trị viên kiểm soát và duyệt sản phẩm toàn sàn")
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @Operation(summary = "Lấy danh sách sản phẩm toàn sàn", description = "Admin tra cứu sản phẩm có lọc theo status, categoryId, search và phân trang")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminProductResponse>>> getAdminProducts(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "categoryId", required = false) Integer categoryId,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<AdminProductResponse> response = productService.getAdminProducts(status, categoryId, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm thành công", response));
    }

    @Operation(summary = "Cập nhật trạng thái sản phẩm", description = "Admin duyệt hoặc khóa sản phẩm (ACTIVE, INACTIVE, BANNED)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminProductResponse>> updateProductStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateProductStatusRequest request
    ) {
        AdminProductResponse response = productService.updateProductStatusForAdmin(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái sản phẩm thành công", response));
    }
}
