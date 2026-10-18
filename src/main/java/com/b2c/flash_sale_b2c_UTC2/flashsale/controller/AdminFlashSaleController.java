package com.b2c.flash_sale_b2c_UTC2.flashsale.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.CreateFlashSaleSlotRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.FlashSaleItemResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.FlashSaleSlotResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.UpdateFlashSaleSlotRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleItemService;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleSlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/flash-sales")
@RequiredArgsConstructor
@Tag(name = "Admin Flash Sale", description = "APIs dành cho Quản trị viên quản lý phiên và duyệt mục Flash Sale")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFlashSaleController {

    private final FlashSaleSlotService slotService;
    private final FlashSaleItemService itemService;

    @PostMapping("/slots")
    @Operation(summary = "Tạo phiên Flash Sale mới", description = "Tạo khung giờ Flash Sale mới không được trùng lặp thời gian")
    public ResponseEntity<ApiResponse<FlashSaleSlotResponse>> createSlot(@Valid @RequestBody CreateFlashSaleSlotRequest request) {
        FlashSaleSlotResponse response = slotService.createSlot(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/slots/{id}")
    @Operation(summary = "Cập nhật phiên Flash Sale", description = "Cập nhật thông tin khung giờ Flash Sale")
    public ResponseEntity<ApiResponse<FlashSaleSlotResponse>> updateSlot(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFlashSaleSlotRequest request
    ) {
        FlashSaleSlotResponse response = slotService.updateSlot(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/slots/{id}")
    @Operation(summary = "Lấy chi tiết phiên Flash Sale", description = "Xem thông tin chi tiết một khung giờ")
    public ResponseEntity<ApiResponse<FlashSaleSlotResponse>> getSlotById(@PathVariable Long id) {
        FlashSaleSlotResponse response = slotService.getSlotById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/slots")
    @Operation(summary = "Danh sách toàn bộ phiên Flash Sale", description = "Quản trị viên xem tất cả các khung giờ")
    public ResponseEntity<ApiResponse<List<FlashSaleSlotResponse>>> getAllSlots() {
        List<FlashSaleSlotResponse> response = slotService.getAllSlots();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/items/{id}/approve")
    @Operation(summary = "Duyệt sản phẩm Flash Sale", description = "Duyệt đăng ký sản phẩm, trừ kho gốc biến thể nguyên tử")
    public ResponseEntity<ApiResponse<FlashSaleItemResponse>> approveItem(@PathVariable Long id) {
        FlashSaleItemResponse response = itemService.approveItem(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/slots/{id}/pre-warm")
    @Operation(summary = "Pre-warm nạp kho Redis trước phiên sale", description = "Nạp tồn kho ban đầu lên Redis bằng SETNX")
    public ResponseEntity<ApiResponse<Void>> preWarmSlot(@PathVariable Long id) {
        itemService.preWarmRedisForSlot(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/slots/{slotId}/items")
    @Operation(summary = "Lấy danh sách SKU đăng ký theo slot", description = "Admin xem danh sách các SKU đăng ký trong khung giờ có lọc và phân trang")
    public ResponseEntity<ApiResponse<com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse<FlashSaleItemResponse>>> getSlotItems(
            @PathVariable Long slotId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @org.springframework.data.web.PageableDefault(size = 15, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable pageable
    ) {
        var response = itemService.getAdminSlotItems(slotId, status, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm đăng ký thành công", response));
    }

    @PatchMapping("/items/{id}/reject")
    @Operation(summary = "Từ chối duyệt SKU Flash Sale", description = "Admin từ chối biến thể SKU kèm lý do")
    public ResponseEntity<ApiResponse<FlashSaleItemResponse>> rejectItem(
            @PathVariable Long id,
            @Valid @RequestBody com.b2c.flash_sale_b2c_UTC2.flashsale.dto.RejectFlashSaleItemRequest request
    ) {
        FlashSaleItemResponse response = itemService.rejectItem(id, request);
        return ResponseEntity.ok(ApiResponse.success("Từ chối duyệt sản phẩm Flash Sale thành công", response));
    }

    @PostMapping("/items/batch-approve")
    @Operation(summary = "Duyệt hàng loạt SKU Flash Sale", description = "Admin duyệt hàng loạt danh sách SKU đã chọn")
    public ResponseEntity<ApiResponse<com.b2c.flash_sale_b2c_UTC2.flashsale.dto.BatchApproveFlashSaleItemsResponse>> batchApprove(
            @Valid @RequestBody com.b2c.flash_sale_b2c_UTC2.flashsale.dto.BatchApproveFlashSaleItemsRequest request
    ) {
        var response = itemService.batchApproveItems(request);
        return ResponseEntity.ok(ApiResponse.success("Duyệt hàng loạt sản phẩm Flash Sale thành công", response));
    }
}
