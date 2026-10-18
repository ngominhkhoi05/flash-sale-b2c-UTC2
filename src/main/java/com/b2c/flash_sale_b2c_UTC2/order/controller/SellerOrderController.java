package com.b2c.flash_sale_b2c_UTC2.order.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.order.dto.OrderResponse;
import com.b2c.flash_sale_b2c_UTC2.order.dto.UpdateOrderStatusRequest;
import com.b2c.flash_sale_b2c_UTC2.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orders Seller", description = "Quản lý đơn hàng thuộc gian hàng của Người bán")
@RestController
@RequestMapping("/api/v1/seller/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerOrderController {

    private final OrderService orderService;

    @Operation(summary = "Lấy danh sách đơn hàng của shop", description = "Seller xem danh sách đơn hàng thuộc shop của mình kèm phân trang và lọc trạng thái")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyStoreOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<OrderResponse> response = orderService.getSellerOrders(userDetails.getUser().getId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng gian hàng thành công", response));
    }

    @Operation(summary = "Lấy chi tiết đơn hàng của shop", description = "Seller xem chi tiết đơn hàng thuộc shop của mình")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        OrderResponse response = orderService.getSellerOrderDetail(userDetails.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin đơn hàng thành công", response));
    }

    @Operation(summary = "Cập nhật trạng thái đơn hàng", description = "Seller cập nhật trạng thái đơn hàng (ví dụ: SHIPPED, COMPLETED, CANCELLED)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        OrderResponse response = orderService.updateOrderStatusBySeller(userDetails.getUser().getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái đơn hàng thành công", response));
    }
}
