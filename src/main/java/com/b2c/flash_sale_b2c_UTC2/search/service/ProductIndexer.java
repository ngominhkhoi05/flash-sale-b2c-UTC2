package com.b2c.flash_sale_b2c_UTC2.search.service;

import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchIndexDocument;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dựng document OpenSearch từ dữ liệu PostgreSQL và bulk-upsert vào index.
 *
 * <p>Nguyên tắc (xem {@code docs/opensearch-search-architecture.md}):
 * <ul>
 *   <li>Index chỉ chứa field phục vụ <b>filter + sort + match</b>. Dữ liệu hiển thị
 *       luôn được hydrate từ PostgreSQL (mô hình search-then-hydrate).</li>
 *   <li>Bulk upsert là <b>idempotent</b> — dùng {@code index} action ghi đè theo
 *       {@code _id}, chạy lại nhiều lần cho kết quả giống nhau.</li>
 *   <li>Không gọi OpenSearch trong transaction DB (tránh dual-write hazard) —
 *       việc gọi index nằm ở listener {@code AFTER_COMMIT} hoặc scheduler.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexer {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductReviewRepository productReviewRepository;
    private final FlashSaleItemRepository flashSaleItemRepository;
    private final SearchIndexService searchIndexService;

    /**
     * Index lại một sản phẩm.
     *
     * <p>Idempotent — gọi lại với cùng dữ liệu cho kết quả giống nhau.
     *
     * @param productId ID sản phẩm.
     * @return {@code true} nếu document đã được index, {@code false} nếu sản phẩm
     *         không còn tồn tại hoặc không đủ điều kiện hiển thị.
     */
    @Transactional(readOnly = true)
    public boolean indexProduct(Long productId) {
        if (productId == null) {
            return false;
        }
        Product product = productRepository.findByIdWithStoreAndCategory(productId).orElse(null);
        if (product == null) {
            // Sản phẩm đã bị hard delete -> dọn document mồ côi khỏi index.
            searchIndexService.delete(productId);
            return false;
        }
        if (!isIndexable(product)) {
            // Sản phẩm không ACTIVE hoặc store chưa APPROVED -> không hiển thị trong
            // kết quả tìm kiếm. Xoá document để index không giữ dữ liệu ẩn.
            searchIndexService.delete(productId);
            return false;
        }

        List<SearchIndexDocument> documents = buildDocuments(List.of(product));
        if (documents.isEmpty()) {
            return false;
        }
        return searchIndexService.bulkUpsert(documents) > 0;
    }

    /**
     * Index lại danh sách sản phẩm theo id (batch).
     *
     * @param productIds danh sách product id.
     * @return số document đã index thành công.
     */
    @Transactional(readOnly = true)
    public int indexProducts(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return 0;
        }
        List<Product> products = productRepository
                .findAllByIdInWithStoreAndCategory(new java.util.HashSet<>(productIds))
                .stream()
                .filter(this::isIndexable)
                .toList();
        if (products.isEmpty()) {
            return 0;
        }
        return searchIndexService.bulkUpsert(buildDocuments(products));
    }

    /**
     * Xoá document của sản phẩm khỏi index.
     *
     * @param productId ID sản phẩm.
     */
    public void removeProduct(Long productId) {
        if (productId != null) {
            searchIndexService.delete(productId);
        }
    }

    /**
     * Dựng danh sách document từ danh sách product đã load (kèm store + category).
     *
     * <p>Tất cả truy vấn aggregate chạy dạng batch (1 query cho N sản phẩm) —
     * tránh N+1 (rule {@code 20-n-plus-one.mdc}).
     *
     * @param products danh sách product.
     * @return danh sách document sẵn sàng bulk-upsert.
     */
    public List<SearchIndexDocument> buildDocuments(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = products.stream().map(Product::getId).toList();

        Map<Long, PriceStock> priceStockMap = loadPriceStock(productIds);
        Map<Long, RatingAgg> ratingMap = loadRating(productIds);
        Set<Long> flashSaleProductIds = new HashSet<>(
                flashSaleItemRepository.findProductIdsWithActiveFlashSale(productIds));

        List<SearchIndexDocument> documents = new ArrayList<>(products.size());
        for (Product product : products) {
            documents.add(toDocument(product, priceStockMap.get(product.getId()),
                    ratingMap.get(product.getId()), flashSaleProductIds.contains(product.getId())));
        }
        return documents;
    }

    private SearchIndexDocument toDocument(Product product, PriceStock priceStock,
                                          RatingAgg rating, boolean isFlashSale) {
        Store store = product.getStore();
        return SearchIndexDocument.builder()
                .id(product.getId())
                .name(product.getName())
                .brand(null)
                .storeId(store != null ? store.getId() : null)
                .storeTier(store != null && store.getTier() != null ? store.getTier().name() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categorySlug(product.getCategory() != null ? product.getCategory().getSlug() : null)
                .minPrice(priceStock != null ? priceStock.minPrice() : BigDecimal.ZERO)
                .maxPrice(priceStock != null ? priceStock.maxPrice() : BigDecimal.ZERO)
                .totalStock(priceStock != null ? priceStock.totalStock() : 0)
                .totalSold(product.getTotalSold() != null ? product.getTotalSold() : 0)
                .ratingAvg(rating != null ? rating.avg() : 0.0d)
                .ratingCount(rating != null ? rating.count() : 0)
                .isFlashSale(isFlashSale)
                .hasDiscount(false)
                .hasVoucher(false)
                .freeship(false)
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt() != null ? product.getUpdatedAt() : product.getCreatedAt())
                .build();
    }

    private Map<Long, PriceStock> loadPriceStock(List<Long> productIds) {
        Map<Long, PriceStock> result = new HashMap<>();
        for (Object[] row : productVariantRepository.aggregatePriceAndStockByProductIds(productIds)) {
            Long productId = (Long) row[0];
            BigDecimal min = (BigDecimal) row[1];
            BigDecimal max = (BigDecimal) row[2];
            Number stock = (Number) row[3];
            result.put(productId, new PriceStock(
                    min != null ? min : BigDecimal.ZERO,
                    max != null ? max : BigDecimal.ZERO,
                    stock != null ? stock.intValue() : 0));
        }
        return result;
    }

    private Map<Long, RatingAgg> loadRating(List<Long> productIds) {
        Map<Long, RatingAgg> result = new HashMap<>();
        for (Object[] row : productReviewRepository.aggregateRatingByProductIds(productIds)) {
            Long productId = (Long) row[0];
            Number avg = (Number) row[1];
            Number count = (Number) row[2];
            result.put(productId, new RatingAgg(
                    avg != null ? avg.doubleValue() : 0.0d,
                    count != null ? count.intValue() : 0));
        }
        return result;
    }

    /**
     * Sản phẩm chỉ được index khi ACTIVE và thuộc store APPROVED — khớp với
     * điều kiện hiển thị của storefront (public listing).
     */
    private boolean isIndexable(Product product) {
        if (product == null) {
            return false;
        }
        boolean productActive = "ACTIVE".equalsIgnoreCase(product.getStatus());
        boolean storeApproved = product.getStore() != null
                && "APPROVED".equalsIgnoreCase(product.getStore().getStatus());
        return productActive && storeApproved;
    }

    /** Tổng hợp giá + tồn kho của một sản phẩm. */
    private record PriceStock(BigDecimal minPrice, BigDecimal maxPrice, int totalStock) {
    }

    /** Tổng hợp đánh giá của một sản phẩm. */
    private record RatingAgg(double avg, int count) {
    }
}
