package com.b2c.flash_sale_b2c_UTC2.search.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchReindexResponse;
import com.b2c.flash_sale_b2c_UTC2.search.exception.SearchErrorCode;
import com.b2c.flash_sale_b2c_UTC2.search.service.SearchIndexScheduler;
import com.b2c.flash_sale_b2c_UTC2.search.service.SearchIndexService;
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

/**
 * Quản trị chỉ mục tìm kiếm toàn văn (OpenSearch).
 *
 * <p>Cần thiết vì bootstrap index chỉ tạo index/alias — <b>không</b> đồng bộ
 * document. Sau khi deploy hoặc sau khi sửa mapping, admin phải gọi reindex để
 * nạp lại toàn bộ sản phẩm vào index.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/search")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Search", description = "Quản trị chỉ mục tìm kiếm toàn văn (OpenSearch)")
public class AdminSearchController {

    private final SearchIndexScheduler searchIndexScheduler;
    private final SearchIndexService searchIndexService;

    @PostMapping("/reindex")
    @Operation(summary = "Đồng bộ toàn bộ sản phẩm vào index tìm kiếm",
            description = "Quét lại toàn bộ sản phẩm ACTIVE (store APPROVED) và ghi vào index OpenSearch. "
                    + "Thao tác idempotent — chạy lại vẫn cho kết quả giống nhau. "
                    + "Trả về số document đã ghi thành công + số document thực sự đang có trong index.")
    public ResponseEntity<ApiResponse<SearchReindexResponse>> reindex() {
        log.info("[AdminSearchController] bat dau full reindex index tim kiem");
        int indexedCount = searchIndexScheduler.fullReindex();
        long indexCount = searchIndexService.countDocuments();
        log.info("[AdminSearchController] full reindex xong: indexed={} indexCount={}",
                indexedCount, indexCount);

        // OpenSearch không truy vấn được -> báo 503 thay vì trả 200 với số liệu bịa.
        if (indexCount < 0) {
            throw new BusinessException(SearchErrorCode.SEARCH_INDEX_UNAVAILABLE);
        }
        // Đã gọi reindex nhưng không document nào ghi được và index vẫn rỗng ->
        // toàn bộ document bị OpenSearch reject (vd: sai format field).
        if (indexedCount == 0 && indexCount == 0) {
            throw new BusinessException(SearchErrorCode.SEARCH_REINDEX_FAILED);
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Dong bo chi muc tim kiem thanh cong",
                SearchReindexResponse.builder()
                        .indexedCount(indexedCount)
                        .indexCount(indexCount)
                        .build()));
    }

    @GetMapping("/status")
    @Operation(summary = "Trang thai chi muc tim kiem",
            description = "Tra ve so document dang co trong index va trang thai index/alias")
    public ResponseEntity<ApiResponse<SearchReindexResponse>> status() {
        long indexCount = searchIndexService.countDocuments();
        // OpenSearch không truy vấn được -> 503, không phải 200 với indexCount = -1.
        if (indexCount < 0) {
            throw new BusinessException(SearchErrorCode.SEARCH_INDEX_UNAVAILABLE);
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Lay trang thai chi muc tim kiem thanh cong",
                SearchReindexResponse.builder()
                        .indexedCount(0)
                        .indexCount(indexCount)
                        .build()));
    }
}
