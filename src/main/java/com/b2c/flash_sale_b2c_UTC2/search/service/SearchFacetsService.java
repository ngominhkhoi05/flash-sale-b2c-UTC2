package com.b2c.flash_sale_b2c_UTC2.search.service;

import com.b2c.flash_sale_b2c_UTC2.product.dto.FacetItemDto;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductFilterRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchFacetsDto;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchRequest;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.repository.CategoryRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.spec.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dựng facets (subcategories, ratings) cho kết quả tìm kiếm.
 *
 * <p>Facets luôn đến từ PostgreSQL — kể cả khi nhánh chính là OpenSearch.
 * Nhờ vậy số đếm ở 2 nhánh không lệch nhau do cách đếm khác nhau.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchFacetsService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Dựng facets các nhóm từ request tìm kiếm.
     *
     * @param req request tìm kiếm.
     * @return facets đầy đủ.
     */
    public SearchFacetsDto buildFacets(SearchRequest req) {
        String keyword = req.getQ() != null ? req.getQ().trim() : "";

        // 1. Subcategories facet: Lấy danh mục thật từ DB kèm số lượng sản phẩm khớp keyword
        List<FacetItemDto> subcategories = new ArrayList<>();
        try {
            List<Object[]> rows = productRepository.countProductsByCategoryWithKeyword(keyword);
            Map<String, FacetItemDto> found = new LinkedHashMap<>();
            for (Object[] row : rows) {
                Integer id = (Integer) row[0];
                String name = (String) row[1];
                String slug = (String) row[2];
                Number count = (Number) row[3];
                String key = slug != null && !slug.isBlank() ? slug : String.valueOf(id);
                found.put(key, FacetItemDto.builder()
                        .key(key)
                        .label(name != null ? name : key)
                        .count(count != null ? count.longValue() : 0L)
                        .build());
            }

            // Bổ sung các categories còn lại trong DB để người dùng thấy đầy đủ các ngành hàng
            List<Category> allCategories = categoryRepository.findAll();
            for (Category c : allCategories) {
                String key = c.getSlug() != null && !c.getSlug().isBlank() ? c.getSlug() : String.valueOf(c.getId());
                if (!found.containsKey(key)) {
                    found.put(key, FacetItemDto.builder()
                            .key(key)
                            .label(c.getName() != null ? c.getName() : key)
                            .count(0L)
                            .build());
                }
            }
            subcategories.addAll(found.values());
        } catch (Exception e) {
            log.warn("[SearchFacetsService] subcategories facet failed: {}", e.getMessage());
        }

        // 2. Ratings facet: 5 sao, 4 sao trở lên, 3 sao trở lên
        List<FacetItemDto> ratings = List.of(
                FacetItemDto.builder().key("5").label("5 sao").count(countByRating(req, 5)).build(),
                FacetItemDto.builder().key("4").label("4 sao trở lên").count(countByRating(req, 4)).build(),
                FacetItemDto.builder().key("3").label("3 sao trở lên").count(countByRating(req, 3)).build()
        );

        // Cities & Services: Lược bỏ theo yêu cầu, trả về danh sách rỗng
        return SearchFacetsDto.builder()
                .subcategories(subcategories)
                .cities(List.of())
                .ratings(ratings)
                .services(List.of())
                .build();
    }

    private long countByRating(SearchRequest req, int minRating) {
        try {
            ProductFilterRequest filter = ProductFilterRequest.builder()
                    .keyword(req.getQ())
                    .categories(req.getSubcategories())
                    .minPrice(req.getPriceMin())
                    .maxPrice(req.getPriceMax())
                    .rating(minRating)
                    .build();
            Specification<Product> spec = ProductSpecifications.fromFilterRequest(filter);
            return productRepository.count(spec);
        } catch (Exception e) {
            log.warn("[SearchFacetsService] countByRating failed: {}", e.getMessage());
            return 0L;
        }
    }
}
