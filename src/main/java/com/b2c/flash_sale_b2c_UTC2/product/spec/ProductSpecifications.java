package com.b2c.flash_sale_b2c_UTC2.product.spec;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductFilterRequest;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.review.entity.ProductReview;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring Data JPA Specification builders for Product filters.
 * Each method returns a reusable Specification for composition.
 */
public class ProductSpecifications {

    private ProductSpecifications() {}

    /**
     * Product must be ACTIVE and belong to APPROVED store.
     */
    public static Specification<Product> isActiveWithApprovedStore() {
        return (root, query, cb) -> {
            Join<Object, Object> store = root.join("store");
            return cb.and(
                    cb.equal(root.get("status"), "ACTIVE"),
                    cb.equal(store.get("status"), "APPROVED")
            );
        };
    }

    /**
     * Filter by categoryId.
     */
    public static Specification<Product> categoryEquals(Integer categoryId) {
        return (root, query, cb) -> {
            if (categoryId == null || categoryId < 0) {
                return cb.conjunction();
            }
            Join<Object, Object> category = root.join("category");
            return cb.equal(category.get("id"), categoryId);
        };
    }

    /**
     * Filter by category slug or id list.
     */
    public static Specification<Product> categoryIn(List<String> categories) {
        return (root, query, cb) -> {
            if (categories == null || categories.isEmpty()) {
                return cb.conjunction();
            }
            Join<Object, Object> category = root.join("category");
            List<String> slugs = new ArrayList<>();
            List<Integer> ids = new ArrayList<>();
            for (String c : categories) {
                if (c == null || c.isBlank()) continue;
                try {
                    ids.add(Integer.parseInt(c.trim()));
                } catch (NumberFormatException ignored) {
                    slugs.add(c.trim());
                }
            }
            List<Predicate> predicates = new ArrayList<>();
            if (!slugs.isEmpty()) {
                predicates.add(category.get("slug").in(slugs));
            }
            if (!ids.isEmpty()) {
                predicates.add(category.get("id").in(ids));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return predicates.size() == 1 ? predicates.get(0) : cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by keyword (case-insensitive LIKE on product name).
     */
    public static Specification<Product> nameContains(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%");
        };
    }

    /**
     * Filter: at least one variant with price >= minPrice.
     *
     * NOTE: Cannot use root.join("variants") because the Product entity has
     * no @OneToMany back-reference to ProductVariant (we own the relationship
     * from the variant side via ProductVariant.product). The implementation
     * uses an EXISTS subquery against ProductVariant instead, which is the
     * same pattern used by hasActiveFlashSale() and hasDiscount() in this class.
     */
    public static Specification<Product> hasVariantWithPriceGte(BigDecimal minPrice) {
        return (root, query, cb) -> {
            if (minPrice == null || minPrice.compareTo(BigDecimal.ZERO) < 0) {
                return cb.conjunction();
            }
            Subquery<Long> sq = query.subquery(Long.class);
            var v = sq.from(ProductVariant.class);
            sq.select(v.get("id"));
            sq.where(cb.and(
                    cb.equal(v.get("product"), root),
                    cb.equal(v.get("status"), "ACTIVE"),
                    cb.greaterThanOrEqualTo(v.get("originalPrice"), minPrice)
            ));
            return cb.exists(sq);
        };
    }

    /**
     * Filter: at least one variant with price <= maxPrice.
     *
     * See hasVariantWithPriceGte for why this uses a subquery instead of a join.
     */
    public static Specification<Product> hasVariantWithPriceLte(BigDecimal maxPrice) {
        return (root, query, cb) -> {
            if (maxPrice == null || maxPrice.compareTo(BigDecimal.ZERO) < 0) {
                return cb.conjunction();
            }
            Subquery<Long> sq = query.subquery(Long.class);
            var v = sq.from(ProductVariant.class);
            sq.select(v.get("id"));
            sq.where(cb.and(
                    cb.equal(v.get("product"), root),
                    cb.equal(v.get("status"), "ACTIVE"),
                    cb.lessThanOrEqualTo(v.get("originalPrice"), maxPrice)
            ));
            return cb.exists(sq);
        };
    }

    /**
     * Filter: at least one ACTIVE variant has price between minPrice and maxPrice.
     * Uses a single unified EXISTS subquery to prevent false positives when one
     * variant satisfies minPrice and another variant satisfies maxPrice.
     */
    public static Specification<Product> hasVariantWithPriceBetween(BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            boolean hasMin = minPrice != null && minPrice.compareTo(BigDecimal.ZERO) >= 0;
            boolean hasMax = maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) >= 0;
            if (!hasMin && !hasMax) {
                return cb.conjunction();
            }
            Subquery<Long> sq = query.subquery(Long.class);
            var v = sq.from(ProductVariant.class);
            sq.select(v.get("id"));

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(v.get("product"), root));
            predicates.add(cb.equal(v.get("status"), "ACTIVE"));
            if (hasMin) {
                predicates.add(cb.greaterThanOrEqualTo(v.get("originalPrice"), minPrice));
            }
            if (hasMax) {
                predicates.add(cb.lessThanOrEqualTo(v.get("originalPrice"), maxPrice));
            }
            sq.where(predicates.toArray(new Predicate[0]));
            return cb.exists(sq);
        };
    }

    /**
     * Filter by minimum sold count.
     */
    public static Specification<Product> soldCountGte(Integer minSold) {
        return (root, query, cb) -> {
            if (minSold == null || minSold <= 0) {
                return cb.conjunction();
            }
            return cb.greaterThanOrEqualTo(root.get("totalSold"), minSold);
        };
    }

    /**
     * Filter by minimum rating average (from product_reviews aggregate).
     * Uses a subquery to compute AVG rating for each product.
     */
    public static Specification<Product> ratingAverageGte(Integer minRating) {
        return (root, query, cb) -> {
            if (minRating == null || minRating <= 0) {
                return cb.conjunction();
            }
            if (query.getResultType() != Long.class) {
                query.groupBy(root.get("id"));
            }
            Subquery<Double> subquery = query.subquery(Double.class);
            var reviewRoot = subquery.from(com.b2c.flash_sale_b2c_UTC2.review.entity.ProductReview.class);
            subquery.select(cb.avg(reviewRoot.get("rating")));
            subquery.where(cb.and(
                    cb.equal(reviewRoot.get("product"), root),
                    cb.equal(reviewRoot.get("status"), "VISIBLE")
            ));
            return cb.greaterThanOrEqualTo(subquery, (double) minRating);
        };
    }

    /**
     * Filter: product has at least one active flash sale item.
     */
    public static Specification<Product> hasActiveFlashSale() {
        return (root, query, cb) -> {
            query.distinct(true);
            Subquery<Long> subquery = query.subquery(Long.class);
            var fsiRoot = subquery.from(com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem.class);
            subquery.select(fsiRoot.get("id"));
            subquery.where(cb.and(
                    cb.equal(fsiRoot.get("variant").get("product"), root),
                    cb.equal(fsiRoot.get("status"), "APPROVED"),
                    cb.equal(fsiRoot.get("slot").get("status"), "ACTIVE")
            ));
            return cb.exists(subquery);
        };
    }

    /**
     * Filter: product where minPrice < maxPrice (i.e., on sale or discounted).
     * Uses subquery to check if there exists a variant with different price.
     */
    public static Specification<Product> hasDiscount() {
        return (root, query, cb) -> {
            query.distinct(true);
            Subquery<BigDecimal> minPriceSub = query.subquery(BigDecimal.class);
            var vMin = minPriceSub.from(com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant.class);
            minPriceSub.select(cb.min(vMin.get("originalPrice")));
            minPriceSub.where(cb.and(
                    cb.equal(vMin.get("product"), root),
                    cb.equal(vMin.get("status"), "ACTIVE")
            ));
            Subquery<BigDecimal> maxPriceSub = query.subquery(BigDecimal.class);
            var vMax = maxPriceSub.from(com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant.class);
            maxPriceSub.select(cb.max(vMax.get("originalPrice")));
            maxPriceSub.where(cb.and(
                    cb.equal(vMax.get("product"), root),
                    cb.equal(vMax.get("status"), "ACTIVE")
            ));
            return cb.lessThan(minPriceSub, maxPriceSub);
        };
    }

    /**
     * Sắp xếp theo giá biến thể thực tế (dành cho DB fallback khi sort theo price_asc hoặc price_desc).
     */
    public static Specification<Product> withSort(String sortKey) {
        return (root, query, cb) -> {
            if (sortKey == null || query.getResultType() != Product.class) {
                return cb.conjunction();
            }
            if ("price_asc".equals(sortKey)) {
                Subquery<BigDecimal> minPriceSub = query.subquery(BigDecimal.class);
                var vMin = minPriceSub.from(ProductVariant.class);
                minPriceSub.select(cb.min(vMin.get("originalPrice")));
                minPriceSub.where(cb.and(
                        cb.equal(vMin.get("product"), root),
                        cb.equal(vMin.get("status"), "ACTIVE")
                ));
                query.orderBy(cb.asc(minPriceSub), cb.asc(root.get("id")));
            } else if ("price_desc".equals(sortKey)) {
                Subquery<BigDecimal> minPriceSub = query.subquery(BigDecimal.class);
                var vMin = minPriceSub.from(ProductVariant.class);
                minPriceSub.select(cb.min(vMin.get("originalPrice")));
                minPriceSub.where(cb.and(
                        cb.equal(vMin.get("product"), root),
                        cb.equal(vMin.get("status"), "ACTIVE")
                ));
                query.orderBy(cb.desc(minPriceSub), cb.asc(root.get("id")));
            }
            return cb.conjunction();
        };
    }

    /**
     * Build combined Specification from ProductFilterRequest.
     */
    public static Specification<Product> fromFilterRequest(ProductFilterRequest filter) {
        Specification<Product> spec = Specification.where(isActiveWithApprovedStore())
                .and(categoryEquals(filter.getCategoryId()))
                .and(categoryIn(filter.getCategories()))
                .and(nameContains(filter.getKeyword()))
                .and(hasVariantWithPriceBetween(filter.getMinPrice(), filter.getMaxPrice()))
                .and(soldCountGte(filter.getMinSold()))
                .and(ratingAverageGte(filter.getRating()));

        if (filter.getFlashSaleOnly() != null && filter.getFlashSaleOnly()) {
            spec = spec.and(hasActiveFlashSale());
        }
        if (filter.getHasDiscount() != null && filter.getHasDiscount()) {
            spec = spec.and(hasDiscount());
        }
        return spec;
    }
}