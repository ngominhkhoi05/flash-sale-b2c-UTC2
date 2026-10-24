package com.b2c.flash_sale_b2c_UTC2.search.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Đặc tả truy vấn tìm kiếm — đầu vào đã chuẩn hoá cho tầng gateway.
 *
 * <p>Tách khỏi DTO của tầng HTTP ({@code SearchRequest}) để gateway không phụ
 * thuộc vào web layer và dễ test.
 *
 * @param keyword      từ khoá full-text (null/rỗng → không có điều kiện match).
 * @param categoryIds  danh sách category id để filter.
 * @param priceMin     giá thấp nhất (null → không filter).
 * @param priceMax     giá cao nhất (null → không filter).
 * @param ratingMin    rating tối thiểu (null → không filter).
 * @param flashSaleOnly chỉ sản phẩm đang flash sale.
 * @param sortKey      sort key đã chuẩn hoá.
 * @param size         số item mỗi trang.
 * @param pitId        PIT hiện tại (null → cần tạo PIT mới).
 * @param searchAfter  mảng sort values của hit cuối (null → trang đầu).
 */
public record SearchQuerySpec(
        String keyword,
        List<Integer> categoryIds,
        BigDecimal priceMin,
        BigDecimal priceMax,
        Integer ratingMin,
        Boolean flashSaleOnly,
        String sortKey,
        int size,
        String pitId,
        List<Object> searchAfter) {
}
