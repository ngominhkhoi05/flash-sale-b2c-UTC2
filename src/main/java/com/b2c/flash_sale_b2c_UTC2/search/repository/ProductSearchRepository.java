package com.b2c.flash_sale_b2c_UTC2.search.repository;

import com.b2c.flash_sale_b2c_UTC2.product.dto.RecommendedProductResponse;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hydrate kết quả tìm kiếm từ PostgreSQL theo danh sách id do OpenSearch trả về.
 *
 * <p>Đây là lớp "defense layer 2" của mô hình <b>search-then-hydrate</b>:
 * <ul>
 *   <li>Chỉ 1 truy vấn {@code IN (...)} cho toàn bộ id → không N+1
 *       (rule {@code 20-n-plus-one.mdc}).</li>
 *   <li>Lọc {@code status = 'ACTIVE'} + store {@code APPROVED} ngay trong SQL —
 *       sản phẩm bị ẩn/xoá giữa chừng sẽ không xuất hiện.</li>
 *   <li>Giữ <b>đúng thứ tự relevance</b> của OpenSearch bằng {@link LinkedHashMap}
 *       — không sắp xếp lại trong SQL (sẽ phá thứ tự).</li>
 * </ul>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {

    private final ProductRepository productRepository;

    /**
     * Load sản phẩm theo danh sách id, giữ nguyên thứ tự đầu vào.
     *
     * @param ids danh sách product id theo thứ tự relevance.
     * @return map {@code id -> product} với thứ tự khớp {@code ids};
     *         id không tồn tại / không hiển thị được bị loại bỏ.
     */
    @Transactional(readOnly = true)
    public Map<Long, Product> loadByIdsInOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Product> products = productRepository.findAllActiveByIdInWithStoreAndCategory(ids);
        Map<Long, Product> byId = new LinkedHashMap<>();
        for (Product product : products) {
            byId.put(product.getId(), product);
        }
        return byId;
    }

    /**
     * Load sản phẩm theo danh sách id, giữ nguyên thứ tự đầu vào và bỏ các id
     * không hydrate được.
     *
     * @param ids danh sách product id theo thứ tự relevance.
     * @return list sản phẩm đã compact, giữ đúng thứ tự của {@code ids}.
     */
    @Transactional(readOnly = true)
    public List<Product> loadOrderedProducts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = loadByIdsInOrder(ids);
        List<Product> ordered = new ArrayList<>(byId.size());
        for (Long id : ids) {
            Product product = byId.get(id);
            if (product != null) {
                ordered.add(product);
            }
        }
        return ordered;
    }

    /**
     * Hydrate ra shape {@link RecommendedProductResponse} (giống
     * {@code GET /api/v1/products/recommended}) để FE không phải map lại.
     *
     * @param ids danh sách product id theo thứ tự relevance.
     * @param variantPrices map {@code productId -> (minPrice, maxPrice, totalStock)}.
     * @param primaryImages map {@code productId -> imageUrl}.
     * @return list response đã compact, giữ đúng thứ tự của {@code ids}.
     */
    public List<RecommendedProductResponse> hydrate(List<Long> ids,
                                                    Map<Long, PriceRange> variantPrices,
                                                    Map<Long, String> primaryImages) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = loadByIdsInOrder(ids);
        List<RecommendedProductResponse> result = new ArrayList<>(byId.size());
        for (Long id : ids) {
            Product product = byId.get(id);
            if (product == null) {
                // ID có trong index nhưng DB không trả về (bị xoá/ẩn) -> loại bỏ,
                // không để lại ô rỗng trong response.
                continue;
            }
            PriceRange range = variantPrices != null ? variantPrices.get(id) : null;
            result.add(RecommendedProductResponse.builder()
                    .id(product.getId())
                    .storeId(product.getStore() != null ? product.getStore().getId() : null)
                    .storeName(product.getStore() != null ? product.getStore().getStoreName() : null)
                    .name(product.getName())
                    .imageUrl(primaryImages != null ? primaryImages.get(id) : null)
                    .minPrice(range != null ? range.minPrice() : null)
                    .maxPrice(range != null ? range.maxPrice() : null)
                    .totalStock(range != null ? range.totalStock() : 0)
                    .totalSold(product.getTotalSold() != null ? product.getTotalSold() : 0)
                    .label(null)
                    .build());
        }
        return result;
    }

    /**
     * Tổng hợp giá + tồn kho của 1 sản phẩm từ các variant.
     */
    public record PriceRange(java.math.BigDecimal minPrice,
                             java.math.BigDecimal maxPrice,
                             int totalStock) {
    }
}
