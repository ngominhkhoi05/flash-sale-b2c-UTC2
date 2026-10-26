package com.b2c.flash_sale_b2c_UTC2.flashsale.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.CancelReservationResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.CreateReservationRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.ReservationResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleReservationFacade;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flash-sales")
@RequiredArgsConstructor
@Tag(name = "Flash Sale Reservation", description = "APIs đặt hàng giữ chỗ Flash Sale chống Over-selling")
public class FlashSaleReservationServiceController {

    private final FlashSaleReservationFacade reservationFacade;
    private final FlashSaleReservationService reservationService;

    @PostMapping("/reserve")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Giữ chỗ Flash Sale (Mục 9.2)", description = "Trừ tồn kho nguyên tử Redis Lua / Redisson, tạo đơn PENDING_PAYMENT, hỗ trợ bù hoàn tự động")
    public ResponseEntity<ApiResponse<ReservationResponse>> reserve(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateReservationRequest request
    ) {
        ReservationResponse response = reservationFacade.reserve(userDetails.getUser().getId(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Giữ chỗ Flash Sale thành công", response));
    }

    @DeleteMapping("/reservations/{reservationId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Hủy giữ chỗ Flash Sale (Mục 9.3)", description = "Hủy đơn giữ chỗ chưa thanh toán và hoàn trả tồn kho nguyên tử")
    public ResponseEntity<ApiResponse<CancelReservationResponse>> cancelReservation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("reservationId") String reservationId
    ) {
        CancelReservationResponse response = reservationService.cancelReservation(userDetails.getUser().getId(), reservationId);
        return ResponseEntity.ok(ApiResponse.success("Hủy giữ chỗ thành công", response));
    }

    @PostMapping("/reservations")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đặt hàng giữ chỗ Flash Sale (Alias tương thích ngược)", description = "Trừ tồn kho nguyên tử Redis Lua / Redisson, tạo đơn PENDING_PAYMENT")
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateReservationRequest request
    ) {
        ReservationResponse response = reservationFacade.reserve(userDetails.getUser().getId(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
