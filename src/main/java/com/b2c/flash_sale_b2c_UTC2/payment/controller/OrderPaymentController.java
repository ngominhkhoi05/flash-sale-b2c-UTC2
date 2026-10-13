package com.b2c.flash_sale_b2c_UTC2.payment.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.PaymentResponse;
import com.b2c.flash_sale_b2c_UTC2.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Order Payment", description = "APIs thanh toán đơn hàng: ZaloPay QR, COD và xem trạng thái thanh toán")
@RestController
@RequestMapping("/api/v1/orders/{id}/payment")
@RequiredArgsConstructor
public class OrderPaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Xem trạng thái và thông tin thanh toán của đơn hàng",
               description = "Trả về phương thức thanh toán, số tiền, link thanh toán, mã QR và trạng thái thanh toán")
    @GetMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        PaymentResponse response = paymentService.getOrderPayment(userDetails.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin thanh toán thành công", response));
    }

    @Operation(summary = "Tạo phiên thanh toán ZaloPay (Sinh QR và URL thanh toán)",
               description = "Khởi tạo yêu cầu thanh toán qua cổng ZaloPay cho đơn hàng")
    @PostMapping("/zalopay")
    public ResponseEntity<ApiResponse<PaymentResponse>> createZaloPayPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        PaymentResponse response = paymentService.createZaloPayPayment(userDetails.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Tạo yêu cầu thanh toán ZaloPay thành công", response));
    }

    @Operation(summary = "Xác nhận phương thức thanh toán khi nhận hàng (COD)",
               description = "Xác nhận đơn hàng chọn phương thức COD và chuyển trạng thái đơn hàng sang CONFIRMED")
    @PostMapping("/cod")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirmCodPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        PaymentResponse response = paymentService.confirmCodPayment(userDetails.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Xác nhận thanh toán COD thành công", response));
    }
}
