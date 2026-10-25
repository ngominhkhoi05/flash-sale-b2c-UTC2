package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.dto.ImageSearchResult;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.service.ImageSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoint công khai cho người dùng tìm kiếm sản phẩm bằng hình ảnh (Visual Search).
 */
@RestController
@RequestMapping("/api/v1/products/search/image")
@RequiredArgsConstructor
@Tag(name = "Product Image Search", description = "Tìm kiếm sản phẩm bằng hình ảnh qua AI CLIP & pgvector")
public class ProductImageSearchController {

    private final ImageSearchService imageSearchService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tìm kiếm sản phẩm bằng file ảnh tải lên",
            description = "Người dùng tải file ảnh (JPEG, PNG, WebP) để tìm các sản phẩm có kiểu dáng tương tự.")
    public ResponseEntity<ApiResponse<ImageSearchResult>> searchByImageFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "limit", required = false) Integer limit) {

        ImageSearchResult result = imageSearchService.searchByImage(file, limit);
        return ResponseEntity.ok(ApiResponse.success("Tim kiem bang hinh anh thanh cong", result));
    }

    @PostMapping(path = "/url", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Tìm kiếm sản phẩm bằng đường dẫn ảnh (URL)",
            description = "Người dùng cung cấp URL của ảnh trên mạng để tìm sản phẩm tương tự.")
    public ResponseEntity<ApiResponse<ImageSearchResult>> searchByImageUrl(
            @RequestBody ImageUrlRequest request,
            @RequestParam(value = "limit", required = false) Integer limit) {

        ImageSearchResult result = imageSearchService.searchByImageUrl(request.getUrl(), limit);
        return ResponseEntity.ok(ApiResponse.success("Tim kiem bang duong dan anh thanh cong", result));
    }

    @Getter
    @Setter
    public static class ImageUrlRequest {
        @NotBlank(message = "Đường dẫn ảnh không được để trống")
        private String url;
    }
}
