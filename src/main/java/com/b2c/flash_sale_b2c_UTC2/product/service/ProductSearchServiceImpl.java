package com.b2c.flash_sale_b2c_UTC2.product.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.product.dto.*;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.exception.ProductErrorCode;
import com.b2c.flash_sale_b2c_UTC2.product.repository.CategoryRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.review.entity.ProductReview;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.enums.StoreTier;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.Voucher;
import com.b2c.flash_sale_b2c_UTC2.voucher.repository.VoucherRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImpl implements ProductSearchService {

    @PersistenceContext
    private final EntityManager entityManager;

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductReviewRepository productReviewRepository;
    private final StoreRepository storeRepository;
    private final VoucherRepository voucherRepository;
    private final ImageService imageService;

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "relevance", "price_asc", "price_desc", "best_selling", "newest"
    );

    @Override
    @Transactional(readOnly = true)
    public ProductSearchResponse searchProducts(ProductSearchRequest request) {
        // 1. Validate tham số đầu vào
        validateSearchRequest(request);

        int page = request.getPage() != null && request.getPage() > 0 ? request.getPage() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 20;
        String sort = request.getSort() != null && !request.getSort().isBlank() ? request.getSort().trim().toLowerCase() : "relevance";

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // 2. Đếm tổng số lượng bản ghi thỏa mãn điều kiện
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Product> countRoot = countQuery.from(Product.class);
        List<Predicate> countPredicates = buildSearchPredicates(cb, countRoot, countQuery, request);
        countQuery.select(cb.countDistinct(countRoot)).where(countPredicates.toArray(new Predicate[0]));
        Long totalElements = entityManager.createQuery(countQuery).getSingleResult();

        if (totalElements == null || totalElements == 0) {
            return ProductSearchResponse.builder()
                    .items(List.of())
                    .total(0L)
                    .page(page)
                    .pageSize(pageSize)
                    .facets(buildEmptyFacets())
                    .mall(null)
                    .build();
        }

        // 3. Truy vấn danh sách Product theo phân trang và sắp xếp
        CriteriaQuery<Product> query = cb.createQuery(Product.class);
        Root<Product> root = query.from(Product.class);
        root.fetch("store", JoinType.INNER);
        root.fetch("category", JoinType.INNER);

        List<Predicate> predicates = buildSearchPredicates(cb, root, query, request);
        query.where(predicates.toArray(new Predicate[0])).distinct(true);

        applySorting(cb, root, query, sort);

        TypedQuery<Product> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((page - 1) * pageSize);
        typedQuery.setMaxResults(pageSize);
        List<Product> products = typedQuery.getResultList();

        // 4. Batch loading dữ liệu phụ trợ để triệt tiêu hoàn toàn N+1
        List<Long> productIds = products.stream().map(Product::getId).toList();
        List<Long> storeIds = products.stream().map(p -> p.getStore().getId()).distinct().toList();

        // Batch load variants
        Map<Long, List<ProductVariant>> variantsMap = productVariantRepository.findByProductIdInAndStatus(productIds, "ACTIVE")
                .stream().collect(Collectors.groupingBy(v -> v.getProduct().getId()));

        // Batch load primary images
        Map<Long, String> primaryImageMap = imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.PRODUCT, productIds)
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getUrl()));

        // Batch load ratings trung bình
        Map<Long, Double> ratingsMap = new HashMap<>();
        try {
            List<Object[]> ratingRows = productReviewRepository.findAvgRatingByProductIdIn(productIds);
            for (Object[] row : ratingRows) {
                if (row[0] != null && row[1] != null) {
                    ratingsMap.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
                }
            }
        } catch (Exception e) {
            log.warn("Không thể tải ratings cho search: {}", e.getMessage());
        }

        // Batch load vouchers của các store để tạo voucherLabel
        Map<Long, String> storeVoucherLabelMap = new HashMap<>();
        try {
            for (Long sId : storeIds) {
                List<Voucher> storeVouchers = voucherRepository.findByStoreId(sId);
                if (storeVouchers != null && !storeVouchers.isEmpty()) {
                    Voucher topVoucher = storeVouchers.stream()
                            .filter(v -> "ACTIVE".equalsIgnoreCase(v.getStatus()) && v.getEndTime().isAfter(Instant.now()))
                            .findFirst().orElse(null);
                    if (topVoucher != null) {
                        String label = "PERCENT".equalsIgnoreCase(topVoucher.getDiscountType())
                                ? "Giảm " + topVoucher.getDiscountValue().stripTrailingZeros().toPlainString() + "%"
                                : "Giảm " + formatCurrencyVnd(topVoucher.getDiscountValue());
                        storeVoucherLabelMap.put(sId, label);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Không thể tải voucher cho search: {}", e.getMessage());
        }

        // 5. Ánh xạ sang danh sách ProductSearchItemDto
        List<ProductSearchItemDto> items = products.stream().map(p -> {
            List<ProductVariant> vars = variantsMap.getOrDefault(p.getId(), List.of());
            BigDecimal minPrice = vars.stream()
                    .map(ProductVariant::getOriginalPrice)
                    .filter(Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            BigDecimal maxPrice = vars.stream()
                    .map(ProductVariant::getOriginalPrice)
                    .filter(Objects::nonNull)
                    .max(BigDecimal::compareTo)
                    .orElse(minPrice);

            String storeBadge = "";
            if (p.getStore().getTier() == StoreTier.OFFICIAL_MALL) {
                storeBadge = "OfficialMall";
            } else if (p.getStore().getTier() == StoreTier.PREFERRED) {
                storeBadge = "Mall";
            }

            Double avgRating = ratingsMap.getOrDefault(p.getId(), 5.0);
            Double roundedRating = BigDecimal.valueOf(avgRating)
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();

            return ProductSearchItemDto.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .imageUrl(primaryImageMap.get(p.getId()))
                    .price(minPrice)
                    .originalPrice(maxPrice)
                    .rating(roundedRating)
                    .totalSold(p.getTotalSold() != null ? p.getTotalSold() : 0)
                    .storeName(p.getStore().getStoreName())
                    .storeBadge(storeBadge)
                    .voucherLabel(storeVoucherLabelMap.get(p.getStore().getId()))
                    .build();
        }).toList();

        // 6. Tính toán Facets
        ProductSearchFacetsDto facets = computeFacets(cb, request);

        // 7. Tìm Mall Store nổi bật nếu có
        ProductSearchMallDto mall = findMatchingMall(request.getQ());

        return ProductSearchResponse.builder()
                .items(items)
                .total(totalElements)
                .page(page)
                .pageSize(pageSize)
                .facets(facets)
                .mall(mall)
                .build();
    }

    private void validateSearchRequest(ProductSearchRequest request) {
        if (request.getPageSize() != null && request.getPageSize() > 50) {
            throw new BusinessException(ProductErrorCode.PAGE_SIZE_EXCEEDED);
        }
        if (request.getPageSize() != null && request.getPageSize() <= 0) {
            throw new BusinessException(ProductErrorCode.INVALID_SEARCH_PARAMS);
        }
        if (request.getPage() != null && request.getPage() <= 0) {
            throw new BusinessException(ProductErrorCode.INVALID_SEARCH_PARAMS);
        }
        if (request.getSort() != null && !request.getSort().isBlank()) {
            String s = request.getSort().trim().toLowerCase();
            if (!ALLOWED_SORTS.contains(s)) {
                throw new BusinessException(ProductErrorCode.INVALID_SORT_OPTION);
            }
        }
        if (request.getMinPrice() != null && request.getMinPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ProductErrorCode.INVALID_SEARCH_PARAMS);
        }
        if (request.getMaxPrice() != null && request.getMaxPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ProductErrorCode.INVALID_SEARCH_PARAMS);
        }
        if (request.getMinPrice() != null && request.getMaxPrice() != null
                && request.getMinPrice().compareTo(request.getMaxPrice()) > 0) {
            throw new BusinessException(ProductErrorCode.INVALID_SEARCH_PARAMS);
        }
        if (request.getMinRating() != null && (request.getMinRating() < 1 || request.getMinRating() > 5)) {
            throw new BusinessException(ProductErrorCode.INVALID_SEARCH_PARAMS);
        }
    }

    private List<Predicate> buildSearchPredicates(CriteriaBuilder cb, Root<Product> root, CriteriaQuery<?> query, ProductSearchRequest request) {
        List<Predicate> predicates = new ArrayList<>();

        // Điều kiện bắt buộc: Sản phẩm ACTIVE và Store APPROVED
        predicates.add(cb.equal(root.get("status"), "ACTIVE"));
        predicates.add(cb.equal(root.get("store").get("status"), "APPROVED"));

        // Lọc từ khóa q chống injection bằng cách escape ký tự đặc biệt
        if (request.getQ() != null && !request.getQ().isBlank()) {
            String escaped = escapeLikePattern(request.getQ()).toLowerCase();
            String pattern = "%" + escaped + "%";
            predicates.add(cb.like(cb.lower(root.get("name")), pattern, '\\'));
        }

        // Lọc theo Category
        if (request.getCategoryId() != null && request.getCategoryId() > 0) {
            predicates.add(cb.equal(root.get("category").get("id"), request.getCategoryId()));
        }

        // Lọc theo khoảng giá biến thể (EXISTS subquery tránh nhân đôi bản ghi)
        if (request.getMinPrice() != null && request.getMinPrice().compareTo(BigDecimal.ZERO) >= 0) {
            Subquery<Integer> minPriceSub = query.subquery(Integer.class);
            Root<ProductVariant> pv = minPriceSub.from(ProductVariant.class);
            minPriceSub.select(cb.literal(1))
                    .where(cb.equal(pv.get("product"), root),
                            cb.equal(pv.get("status"), "ACTIVE"),
                            cb.greaterThanOrEqualTo(pv.get("originalPrice"), request.getMinPrice()));
            predicates.add(cb.exists(minPriceSub));
        }

        if (request.getMaxPrice() != null && request.getMaxPrice().compareTo(BigDecimal.ZERO) >= 0) {
            Subquery<Integer> maxPriceSub = query.subquery(Integer.class);
            Root<ProductVariant> pv = maxPriceSub.from(ProductVariant.class);
            maxPriceSub.select(cb.literal(1))
                    .where(cb.equal(pv.get("product"), root),
                            cb.equal(pv.get("status"), "ACTIVE"),
                            cb.lessThanOrEqualTo(pv.get("originalPrice"), request.getMaxPrice()));
            predicates.add(cb.exists(maxPriceSub));
        }

        // Lọc theo minRating
        if (request.getMinRating() != null && request.getMinRating() > 0) {
            Subquery<Long> ratingSub = query.subquery(Long.class);
            Root<ProductReview> r = ratingSub.from(ProductReview.class);
            ratingSub.select(r.get("product").get("id"))
                    .where(cb.equal(r.get("status"), "VISIBLE"))
                    .groupBy(r.get("product").get("id"))
                    .having(cb.ge(cb.avg(r.get("rating")), request.getMinRating().doubleValue()));
            predicates.add(root.get("id").in(ratingSub));
        }

        return predicates;
    }

    private void applySorting(CriteriaBuilder cb, Root<Product> root, CriteriaQuery<Product> query, String sort) {
        switch (sort) {
            case "newest" -> query.orderBy(cb.desc(root.get("createdAt")));
            case "best_selling" -> query.orderBy(cb.desc(root.get("totalSold")), cb.desc(root.get("createdAt")));
            case "price_asc" -> {
                Subquery<BigDecimal> minPriceSub = query.subquery(BigDecimal.class);
                Root<ProductVariant> pv = minPriceSub.from(ProductVariant.class);
                minPriceSub.select(cb.min(pv.get("originalPrice")))
                        .where(cb.equal(pv.get("product"), root), cb.equal(pv.get("status"), "ACTIVE"));
                query.orderBy(cb.asc(minPriceSub), cb.desc(root.get("createdAt")));
            }
            case "price_desc" -> {
                Subquery<BigDecimal> maxPriceSub = query.subquery(BigDecimal.class);
                Root<ProductVariant> pv = maxPriceSub.from(ProductVariant.class);
                maxPriceSub.select(cb.max(pv.get("originalPrice")))
                        .where(cb.equal(pv.get("product"), root), cb.equal(pv.get("status"), "ACTIVE"));
                query.orderBy(cb.desc(maxPriceSub), cb.desc(root.get("createdAt")));
            }
            default -> // relevance
                    query.orderBy(cb.desc(root.get("totalSold")), cb.desc(root.get("createdAt")));
        }
    }

    private ProductSearchFacetsDto computeFacets(CriteriaBuilder cb, ProductSearchRequest request) {
        // Categories Facet
        List<CategoryFacetDto> categoryFacets = new ArrayList<>();
        try {
            CriteriaQuery<Object[]> catQuery = cb.createQuery(Object[].class);
            Root<Product> catRoot = catQuery.from(Product.class);

            List<Predicate> basePredicates = new ArrayList<>();
            basePredicates.add(cb.equal(catRoot.get("status"), "ACTIVE"));
            basePredicates.add(cb.equal(catRoot.get("store").get("status"), "APPROVED"));
            if (request.getQ() != null && !request.getQ().isBlank()) {
                String pattern = "%" + escapeLikePattern(request.getQ()).toLowerCase() + "%";
                basePredicates.add(cb.like(cb.lower(catRoot.get("name")), pattern, '\\'));
            }

            catQuery.multiselect(
                    catRoot.get("category").get("id"),
                    catRoot.get("category").get("name"),
                    cb.countDistinct(catRoot)
            ).where(basePredicates.toArray(new Predicate[0]))
             .groupBy(catRoot.get("category").get("id"), catRoot.get("category").get("name"));

            List<Object[]> catResults = entityManager.createQuery(catQuery).getResultList();
            for (Object[] row : catResults) {
                if (row[0] != null && row[1] != null && row[2] != null) {
                    categoryFacets.add(CategoryFacetDto.builder()
                            .id(((Number) row[0]).intValue())
                            .name((String) row[1])
                            .count(((Number) row[2]).longValue())
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Không thể tính category facets: {}", e.getMessage());
        }

        // Price ranges: [0-100k, 100k-300k, 300k-500k, 500k-1M, >1M]
        List<PriceRangeFacetDto> priceRanges = List.of(
                PriceRangeFacetDto.builder().min(BigDecimal.ZERO).max(new BigDecimal("100000")).count(countPriceRange(cb, request, BigDecimal.ZERO, new BigDecimal("100000"))).build(),
                PriceRangeFacetDto.builder().min(new BigDecimal("100000")).max(new BigDecimal("300000")).count(countPriceRange(cb, request, new BigDecimal("100000"), new BigDecimal("300000"))).build(),
                PriceRangeFacetDto.builder().min(new BigDecimal("300000")).max(new BigDecimal("500000")).count(countPriceRange(cb, request, new BigDecimal("300000"), new BigDecimal("500000"))).build(),
                PriceRangeFacetDto.builder().min(new BigDecimal("500000")).max(new BigDecimal("1000000")).count(countPriceRange(cb, request, new BigDecimal("500000"), new BigDecimal("1000000"))).build(),
                PriceRangeFacetDto.builder().min(new BigDecimal("1000000")).max(null).count(countPriceRange(cb, request, new BigDecimal("1000000"), null)).build()
        );

        return ProductSearchFacetsDto.builder()
                .categories(categoryFacets)
                .priceRanges(priceRanges)
                .services(List.of("voucher", "freeship"))
                .ratings(List.of(1, 2, 3, 4, 5))
                .build();
    }

    private long countPriceRange(CriteriaBuilder cb, ProductSearchRequest request, BigDecimal min, BigDecimal max) {
        try {
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<Product> root = query.from(Product.class);

            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("status"), "ACTIVE"));
            preds.add(cb.equal(root.get("store").get("status"), "APPROVED"));

            if (request.getQ() != null && !request.getQ().isBlank()) {
                String pattern = "%" + escapeLikePattern(request.getQ()).toLowerCase() + "%";
                preds.add(cb.like(cb.lower(root.get("name")), pattern, '\\'));
            }

            Subquery<Integer> pvSub = query.subquery(Integer.class);
            Root<ProductVariant> pv = pvSub.from(ProductVariant.class);
            List<Predicate> pvPreds = new ArrayList<>();
            pvPreds.add(cb.equal(pv.get("product"), root));
            pvPreds.add(cb.equal(pv.get("status"), "ACTIVE"));
            if (min != null) {
                pvPreds.add(cb.greaterThanOrEqualTo(pv.get("originalPrice"), min));
            }
            if (max != null) {
                pvPreds.add(cb.lessThanOrEqualTo(pv.get("originalPrice"), max));
            }
            pvSub.select(cb.literal(1)).where(pvPreds.toArray(new Predicate[0]));
            preds.add(cb.exists(pvSub));

            query.select(cb.countDistinct(root)).where(preds.toArray(new Predicate[0]));
            return entityManager.createQuery(query).getSingleResult();
        } catch (Exception e) {
            return 0L;
        }
    }

    private ProductSearchMallDto findMatchingMall(String keyword) {
        if (keyword == null || keyword.isBlank() || keyword.trim().length() < 2) {
            return null;
        }
        try {
            String escaped = escapeLikePattern(keyword).toLowerCase();
            List<Store> mallStores = entityManager.createQuery(
                    "SELECT s FROM Store s WHERE s.tier = :tier AND s.status = 'APPROVED' AND LOWER(s.storeName) LIKE :kw",
                    Store.class
            ).setParameter("tier", StoreTier.OFFICIAL_MALL)
             .setParameter("kw", "%" + escaped + "%")
             .setMaxResults(1)
             .getResultList();

            if (!mallStores.isEmpty()) {
                Store s = mallStores.get(0);
                return ProductSearchMallDto.builder()
                        .title(s.getStoreName() + " Official Mall")
                        .subtitle("Gian hàng chính hãng")
                        .href("/store/" + s.getId())
                        .imageUrl(null)
                        .build();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private ProductSearchFacetsDto buildEmptyFacets() {
        return ProductSearchFacetsDto.builder()
                .categories(List.of())
                .priceRanges(List.of())
                .services(List.of("voucher", "freeship"))
                .ratings(List.of(1, 2, 3, 4, 5))
                .build();
    }

    private String escapeLikePattern(String input) {
        if (input == null || input.isBlank()) return "";
        return input.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private String formatCurrencyVnd(BigDecimal amount) {
        if (amount == null) return "0đ";
        long val = amount.longValue();
        if (val >= 1000) {
            return (val / 1000) + "K";
        }
        return val + "đ";
    }
}
