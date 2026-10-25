package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Kết quả tìm kiếm sản phẩm bằng hình ảnh trả về cho Client.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageSearchResult {

    private List<ImageSearchProductResponse> items;

    private int total;

    private long searchDurationMs;
}
