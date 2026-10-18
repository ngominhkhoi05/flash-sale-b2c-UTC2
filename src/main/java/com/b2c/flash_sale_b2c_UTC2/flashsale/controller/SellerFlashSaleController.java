package com.b2c.flash_sale_b2c_UTC2.flashsale.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.FlashSaleItemResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.RegisterFlashSaleItemRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/flash-sales")
@RequiredArgsConstructor
@Tag(name = "Seller Flash Sale", description = "APIs dành cho Người bán đăng ký sản phẩm tham gia Flash Sale")
@PreAuthorize("hasRole('SELLER')")
public class SellerFlashSaleController {

    private final FlashSaleItemService itemService;

    @PostMapping("/items")
    @Operation(summary = "Đăng ký biến thể tham gia Flash Sale", description = "Seller nộp đơn đăng ký biến thể SKU vào một khung giờ Flash Sale")
    public ResponseEntity<ApiResponse<FlashSaleItemResponse>> registerItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RegisterFlashSaleItemRequest request
    ) {
        FlashSaleItemResponse response = itemService.registerItem(userDetails.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/items")
    @Operation(summary = "Danh sách sản phẩm Flash Sale của shop", description = "Lấy danh sách các biến thể SKU đã nộp đơn đăng ký Flash Sale")
    public ResponseEntity<ApiResponse<PageResponse<FlashSaleItemResponse>>> getMyItems(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long slotId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<FlashSaleItemResponse> response = itemService.getSellerItems(
                userDetails.getUser().getId(),
                slotId,
                status,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đăng ký Flash Sale thành công", response));
    }

    @DeleteMapping("/items/{id}")
    @Operation(summary = "Hủy đăng ký biến thể Flash Sale", description = "Hủy đơn đăng ký khi biến thể còn ở trạng thái chờ duyệt (PENDING_APPROVAL)")
    public ResponseEntity<ApiResponse<Void>> cancelItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        itemService.cancelRegistration(userDetails.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Hủy đăng ký Flash Sale thành công", null));
    }
}
