package com.b2c.flash_sale_b2c_UTC2.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Document được index vào OpenSearch.
 *
 * <p>Chỉ chứa field phục vụ **filter + sort + match**. KHÔNG chứa field hiển thị
 * (description, imageUrl, storeName) — dữ liệu hiển thị luôn được hydrate từ
 * PostgreSQL (mô hình search-then-hydrate) nên không bao giờ stale.
 *
 * <p>Field denormalize ({@code minPrice}, {@code maxPrice}, {@code totalStock},
 * {@code ratingAvg}, {@code ratingCount}) được tính từ variants + reviews rồi ghi
 * vào index, vì sort/filter không thể join DB lúc query.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchIndexDocument {

    /** Product ID — cũng là {@code _id} của document trong OpenSearch. */
    private Long id;

    /** Tên sản phẩm — field full-text chính. */
    private String name;

    /** Thương hiệu. */
    private String brand;

    /** Store ID. */
    private Long storeId;

    /** Tier của store (NORMAL | OFFICIAL_MALL | PREFERRED). */
    private String storeTier;

    /** Category ID. */
    private Integer categoryId;

    /** Category slug. */
    private String categorySlug;

    /** Giá thấp nhất trong các variant ACTIVE. */
    private BigDecimal minPrice;

    /** Giá cao nhất trong các variant ACTIVE. */
    private BigDecimal maxPrice;

    /** Tổng tồn kho của các variant ACTIVE. */
    private Integer totalStock;

    /** Tổng số lượng đã bán. */
    private Integer totalSold;

    /** Điểm đánh giá trung bình (0-5). */
    private Double ratingAvg;

    /** Số lượng đánh giá. */
    private Integer ratingCount;

    /** Sản phẩm đang có flash sale. */
    private Boolean isFlashSale;

    /** Sản phẩm đang có giảm giá. */
    private Boolean hasDiscount;

    /** Sản phẩm có voucher. */
    private Boolean hasVoucher;

    /** Sản phẩm được freeship. */
    private Boolean freeship;

    /** Trạng thái sản phẩm (ACTIVE | ...). */
    private String status;

    /** Thời điểm tạo. */
    private Instant createdAt;

    /** Thời điểm cập nhật gần nhất. */
    private Instant updatedAt;
}
