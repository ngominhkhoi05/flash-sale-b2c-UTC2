package com.b2c.flash_sale_b2c_UTC2.store.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.store.dto.CreateStoreRequest;
import com.b2c.flash_sale_b2c_UTC2.store.dto.FeaturedStoreResponse;
import com.b2c.flash_sale_b2c_UTC2.store.dto.StoreResponse;
import com.b2c.flash_sale_b2c_UTC2.store.dto.UpdateStoreRequest;
import com.b2c.flash_sale_b2c_UTC2.store.service.StoreService;
import com.b2c.flash_sale_b2c_UTC2.user.dto.AddressResponse;
import com.b2c.flash_sale_b2c_UTC2.user.dto.CreateAddressRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateAddressRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Stores", description = "Quản lý gian hàng của Người bán và sổ địa chỉ kho lấy hàng")
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;
    private final ImageService imageService;

    @Operation(summary = "Đăng ký mở gian hàng mới", description = "Người dùng đăng ký mở gian hàng (mỗi user tối đa 1 gian hàng, trạng thái ban đầu PENDING)")
    @PostMapping
    public ResponseEntity<ApiResponse<StoreResponse>> registerStore(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateStoreRequest request
    ) {
        StoreResponse response = storeService.registerStore(userDetails.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký gian hàng thành công, đang chờ phê duyệt", response));
    }

    @Operation(summary = "Lấy thông tin gian hàng của tôi", description = "Seller lấy thông tin gian hàng do mình sở hữu")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<StoreResponse>> getMyStore(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        StoreResponse response = storeService.getMyStore(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin gian hàng thành công", response));
    }

    @Operation(summary = "Cập nhật thông tin gian hàng của tôi", description = "Seller cập nhật tên, logo, mô tả gian hàng")
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<StoreResponse>> updateMyStore(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateStoreRequest request
    ) {
        StoreResponse response = storeService.updateMyStore(userDetails.getUser().getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin gian hàng thành công", response));
    }

    @Operation(summary = "Lấy danh sách gian hàng nổi bật", description = "Public - lấy danh sách gian hàng APPROVED cho section Vibe Mall trang chủ")
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<FeaturedStoreResponse>>> getFeaturedStores(
            @RequestParam(value = "limit", defaultValue = "4") int limit
    ) {
        List<FeaturedStoreResponse> response = storeService.getFeaturedStores(limit);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách gian hàng nổi bật thành công", response));
    }

    @Operation(summary = "Xem thông tin công khai gian hàng", description = "Khách hàng xem thông tin gian hàng đã được duyệt (APPROVED)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreResponse>> getStoreById(@PathVariable("id") Long id) {
        StoreResponse response = storeService.getStoreById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin gian hàng thành công", response));
    }

    // --- PHÂN HỆ ĐỊA CHỈ KHO LẤY HÀNG CỦA STORE (XOR Constraint) ---

    @Operation(summary = "Lấy danh sách địa chỉ kho của gian hàng", description = "Seller lấy danh sách địa chỉ kho lấy hàng của shop")
    @GetMapping("/me/addresses")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getStoreAddresses(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<AddressResponse> responses = storeService.getStoreAddresses(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách địa chỉ kho thành công", responses));
    }

    @Operation(summary = "Thêm địa chỉ kho cho gian hàng", description = "Seller thêm mới địa chỉ kho lấy hàng")
    @PostMapping("/me/addresses")
    public ResponseEntity<ApiResponse<AddressResponse>> createStoreAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateAddressRequest request
    ) {
        AddressResponse response = storeService.createStoreAddress(userDetails.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm địa chỉ kho thành công", response));
    }

    @Operation(summary = "Cập nhật địa chỉ kho của gian hàng", description = "Seller cập nhật thông tin địa chỉ kho")
    @PutMapping("/me/addresses/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateStoreAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("addressId") Long addressId,
            @Valid @RequestBody UpdateAddressRequest request
    ) {
        AddressResponse response = storeService.updateStoreAddress(userDetails.getUser().getId(), addressId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật địa chỉ kho thành công", response));
    }

    @Operation(summary = "Xóa địa chỉ kho của gian hàng", description = "Seller xóa địa chỉ kho")
    @DeleteMapping("/me/addresses/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteStoreAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("addressId") Long addressId
    ) {
        storeService.deleteStoreAddress(userDetails.getUser().getId(), addressId);
        return ResponseEntity.ok(ApiResponse.success("Xóa địa chỉ kho thành công", null));
    }

    @Operation(summary = "Đặt địa chỉ kho làm mặc định", description = "Đặt một địa chỉ kho làm kho lấy hàng chính")
    @PatchMapping("/me/addresses/{addressId}/default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultStoreAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("addressId") Long addressId
    ) {
        AddressResponse response = storeService.setDefaultStoreAddress(userDetails.getUser().getId(), addressId);
        return ResponseEntity.ok(ApiResponse.success("Đặt địa chỉ kho mặc định thành công", response));
    }

    @Operation(summary = "Upload logo cho gian hàng", description = "Tự động soft-delete logo cũ (1 store = 1 logo ACTIVE).")
    @PostMapping(value = "/me/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageResponse>> uploadLogo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart("file") MultipartFile file
    ) {
        Long currentUserId = userDetails.getUser().getId();
        Long storeId = storeService.getMyStore(currentUserId).getId();
        ImageResponse resp = imageService.attachImage(
                currentUserId, ImageOwnerType.STORE, storeId, file, null, null);
        return ResponseEntity.status(HttpStatusCode.valueOf(201))
                .body(ApiResponse.success("Upload logo thành công", resp));
    }
}
