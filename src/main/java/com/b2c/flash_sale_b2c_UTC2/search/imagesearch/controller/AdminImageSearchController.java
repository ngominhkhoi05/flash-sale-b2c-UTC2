package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.client.ImageEmbeddingGateway;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.dto.ImageReindexResponse;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.repository.ImageEmbeddingRepository;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.service.ImageSearchIndexerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller quản trị vector chỉ mục tìm kiếm bằng hình ảnh (pgvector).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/image-search")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Image Search", description = "Quản trị vector chỉ mục tìm kiếm bằng hình ảnh (pgvector)")
public class AdminImageSearchController {

    private final ImageSearchIndexerService imageSearchIndexerService;
    private final ImageEmbeddingRepository imageEmbeddingRepository;
    private final ImageEmbeddingGateway imageEmbeddingGateway;

    @PostMapping("/reindex")
    @Operation(summary = "Đồng bộ vector cho toàn bộ ảnh sản phẩm",
            description = "Quét lại toàn bộ ảnh sản phẩm ACTIVE và trích xuất vector embedding ghi vào pgvector. "
                    + "Thao tác mang tính idempotent — chạy lại nhiều lần an toàn.")
    public ResponseEntity<ApiResponse<ImageReindexResponse>> reindex() {
        log.info("[AdminImageSearchController] Yeu cau reindex toan bo vector anh san pham");
        ImageReindexResponse response = imageSearchIndexerService.reindexAllProductImages();
        return ResponseEntity.ok(ApiResponse.success("Dong bo vector anh san pham thanh cong", response));
    }

    @GetMapping("/status")
    @Operation(summary = "Kiểm tra trạng thái hệ thống vector search",
            description = "Trả về trạng thái kết nối tới AI Service và tổng số vector hiện có trong database.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        boolean aiHealthy = imageEmbeddingGateway.isHealthy();
        long totalVectors = imageEmbeddingRepository.count();

        Map<String, Object> status = Map.of(
                "aiServiceHealthy", aiHealthy,
                "totalVectorsInDb", totalVectors
        );
        return ResponseEntity.ok(ApiResponse.success("Lay trang thai thanh cong", status));
    }
}
