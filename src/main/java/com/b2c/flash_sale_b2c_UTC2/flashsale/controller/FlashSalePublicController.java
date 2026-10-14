package com.b2c.flash_sale_b2c_UTC2.flashsale.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.PublicFlashSaleSlotResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flash-sales")
@RequiredArgsConstructor
@Tag(name = "Public Flash Sale", description = "APIs công khai xem các phiên Flash Sale và sản phẩm mở bán")
public class FlashSalePublicController {

    private final FlashSaleItemService itemService;

    @GetMapping("/slots")
    @Operation(summary = "Xem danh sách các phiên Flash Sale đang và sắp diễn ra", description = "Trả về thông tin phiên và danh sách sản phẩm kèm tồn kho khả dụng thời gian thực từ Redis")
    public ResponseEntity<ApiResponse<List<PublicFlashSaleSlotResponse>>> getPublicSlots() {
        List<PublicFlashSaleSlotResponse> response = itemService.getPublicSlots();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/items/active")
    @Operation(summary = "Xem danh sách các sản phẩm Flash Sale đang mở bán active cho trang chủ", description = "Trả về danh sách sản phẩm đang trong phiên Flash Sale ACTIVE kèm tồn kho khả dụng thời gian thực")
    public ResponseEntity<ApiResponse<List<PublicFlashSaleSlotResponse.PublicFlashSaleItemResponse>>> getActiveItems() {
        List<PublicFlashSaleSlotResponse.PublicFlashSaleItemResponse> response = itemService.getActiveItems();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
