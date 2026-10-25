package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.dto;

import com.b2c.flash_sale_b2c_UTC2.product.dto.RecommendedProductResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Đóng gói 1 sản phẩm tìm thấy kèm điểm tương đồng (similarity score).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageSearchProductResponse {

    private RecommendedProductResponse product;

    /** Độ tương đồng ước tính (từ 0.0 đến 1.0, ví dụ 0.92 = 92% khớp). */
    private double similarityScore;

    /** Khoảng cách Cosine Distance từ pgvector (<=> operator). */
    private double distance;
}
