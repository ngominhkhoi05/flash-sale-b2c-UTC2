package com.b2c.flash_sale_b2c_UTC2.image.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse;
import com.b2c.flash_sale_b2c_UTC2.image.dto.UpdateImageOrderRequest;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controller cho endpoint chung của Image Management Module (docs/api/api-document.md mục 28.2).
 */
@Tag(name = "Images", description = "Quản lý ảnh đa đối tượng (polymorphic) tích hợp Cloudinary")
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @Operation(summary = "Upload ảnh mới và gắn vào owner", description = "Body multipart/form-data gồm file + metadata (ownerType, ownerId, isPrimary?).")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageResponse>> upload(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart("file") MultipartFile file,
            @RequestParam("ownerType") ImageOwnerType ownerType,
            @RequestParam("ownerId") Long ownerId,
            @RequestParam(value = "isPrimary", required = false) Boolean isPrimary,
            @RequestParam(value = "displayOrder", required = false) Integer displayOrder
    ) {
        ImageResponse resp = imageService.attachImage(
                userDetails.getId(), ownerType, ownerId, file, isPrimary, displayOrder);
        return ResponseEntity.status(HttpStatusCode.valueOf(201))
                .body(ApiResponse.success("Upload ảnh thành công", resp));
    }

    @Operation(summary = "Lấy danh sách ảnh ACTIVE của một owner")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ImageResponse>>> list(
            @RequestParam("ownerType") ImageOwnerType ownerType,
            @RequestParam("ownerId") Long ownerId
    ) {
        List<ImageResponse> list = imageService.listImages(ownerType, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách ảnh thành công", list));
    }

    @Operation(summary = "Đánh dấu ảnh là primary", description = "Chỉ áp dụng cho PRODUCT/VARIANT.")
    @PatchMapping("/{id}/primary")
    public ResponseEntity<ApiResponse<ImageResponse>> setPrimary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        ImageResponse resp = imageService.setPrimary(userDetails.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Đặt ảnh đại diện chính thành công", resp));
    }

    @Operation(summary = "Cập nhật thứ tự hiển thị", description = "Chỉ áp dụng cho REVIEW (gallery 1-N).")
    @PatchMapping("/{id}/order")
    public ResponseEntity<ApiResponse<ImageResponse>> setOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateImageOrderRequest request
    ) {
        ImageResponse resp = imageService.setDisplayOrder(userDetails.getId(), id, request.getDisplayOrder());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thứ tự hiển thị thành công", resp));
    }

    @Operation(summary = "Soft delete ảnh", description = "Đặt status=INACTIVE và phát event async để xóa trên Cloudinary.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        imageService.detachImage(userDetails.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Xóa ảnh thành công", null));
    }

    @Operation(summary = "Hard delete ảnh (Admin only)", description = "Xóa cả record DB + đồng bộ xóa trên Cloudinary ngay.")
    @DeleteMapping("/{id}/force")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> forceDelete(@PathVariable("id") Long id) {
        imageService.forceDeleteImage(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa cứng ảnh thành công", null));
    }
}