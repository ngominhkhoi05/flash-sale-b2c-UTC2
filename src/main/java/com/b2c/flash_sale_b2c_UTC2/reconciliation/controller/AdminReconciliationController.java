package com.b2c.flash_sale_b2c_UTC2.reconciliation.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.reconciliation.dto.LockReconciliationRequest;
import com.b2c.flash_sale_b2c_UTC2.reconciliation.dto.ReconciliationKpisResponse;
import com.b2c.flash_sale_b2c_UTC2.reconciliation.dto.ReconciliationOrderResponse;
import com.b2c.flash_sale_b2c_UTC2.reconciliation.dto.ReconciliationRefundResponse;
import com.b2c.flash_sale_b2c_UTC2.reconciliation.service.ReconciliationService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Reconciliation", description = "Quản lý đối soát tài chính toàn sàn cho Admin")
@RestController
@RequestMapping("/api/v1/admin/reconciliation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReconciliationController {

    private final ReconciliationService reconciliationService;

    @Operation(summary = "Lấy KPIs đối soát tài chính toàn sàn", description = "Admin xem tổng GMV, hoa hồng, tiền quyết toán shop và hoàn tiền")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ReconciliationKpisResponse>> getReconciliationSummary(
            @RequestParam(name = "period", required = false) String period
    ) {
        ReconciliationKpisResponse response = reconciliationService.getReconciliationKpis(period);
        return ResponseEntity.ok(ApiResponse.success("Lấy số liệu tổng quan đối soát thành công", response));
    }

    @Operation(summary = "Lấy danh sách đơn hàng đối soát", description = "Admin xem danh sách đơn hàng kèm hoa hồng sàn và số tiền shop nhận")
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PageResponse<ReconciliationOrderResponse>>> getReconciliationOrders(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "storeId", required = false) Long storeId,
            @RequestParam(name = "paymentMethod", required = false) String paymentMethod,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "period", required = false) String period,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<ReconciliationOrderResponse> response = reconciliationService.getReconciliationOrders(
                search, storeId, paymentMethod, status, period, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng đối soát thành công", response));
    }

    @Operation(summary = "Lấy danh sách hoàn tiền đối soát", description = "Admin xem danh sách các đơn hàng đã hoàn tiền cho khách")
    @GetMapping("/refunds")
    public ResponseEntity<ApiResponse<List<ReconciliationRefundResponse>>> getReconciliationRefunds(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "period", required = false) String period
    ) {
        List<ReconciliationRefundResponse> response = reconciliationService.getReconciliationRefunds(search, period);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách hoàn tiền đối soát thành công", response));
    }

    @Operation(summary = "Khóa sổ kỳ đối soát", description = "Admin chốt khóa kỳ đối soát sau khi hoàn tất đối chiếu doanh thu")
    @PostMapping("/lock")
    public ResponseEntity<ApiResponse<Void>> lockReconciliationPeriod(
            @Valid @RequestBody LockReconciliationRequest request
    ) {
        reconciliationService.lockReconciliationPeriod(request);
        return ResponseEntity.ok(ApiResponse.success("Khóa sổ kỳ đối soát thành công", null));
    }
}
