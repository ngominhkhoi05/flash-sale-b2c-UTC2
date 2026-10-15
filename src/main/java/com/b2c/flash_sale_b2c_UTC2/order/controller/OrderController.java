package com.b2c.flash_sale_b2c_UTC2.order.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.order.dto.CreateOrderRequest;
import com.b2c.flash_sale_b2c_UTC2.order.dto.OrderResponse;
import com.b2c.flash_sale_b2c_UTC2.order.dto.QrOrderDetailResponse;
import com.b2c.flash_sale_b2c_UTC2.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Orders Buyer", description = "Đặt hàng và quản lý đơn hàng của Người mua")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Đặt hàng giỏ hàng (Tách đơn Đa gian hàng)", description = "Đặt mua các sản phẩm từ giỏ hàng, tự động tách thành từng đơn độc lập theo Gian hàng")
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> checkout(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        List<OrderResponse> response = orderService.createOrders(userDetails.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đặt hàng thành công", response));
    }

    @Operation(summary = "Lấy danh sách đơn hàng của tôi", description = "Danh sách đơn đặt hàng của Người mua kèm phân trang")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<OrderResponse> response = orderService.getBuyerOrders(userDetails.getUser().getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng thành công", response));
    }

    @Operation(summary = "Xem chi tiết đơn hàng", description = "Khách hàng xem chi tiết 1 đơn hàng của mình")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        OrderResponse response = orderService.getBuyerOrderDetail(userDetails.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết đơn hàng thành công", response));
    }

    @Operation(summary = "Hủy đơn hàng", description = "Khách hàng hủy đơn hàng chưa thanh toán")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        OrderResponse response = orderService.cancelOrder(userDetails.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Hủy đơn hàng thành công", response));
    }

    @Operation(summary = "Lấy mã QR thanh toán đơn hàng", description = "Lấy QR thanh toán và chi tiết thanh toán cho 1 hoặc nhiều mã đơn hàng (cách nhau bởi dấu phẩy)")
    @GetMapping("/{orderCode}/qr")
    public ResponseEntity<ApiResponse<QrOrderDetailResponse>> getOrderQrDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("orderCode") String orderCode
    ) {
        QrOrderDetailResponse response = orderService.getOrderQrDetail(userDetails.getUser().getId(), orderCode);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin QR thanh toán thành công", response));
    }
}
