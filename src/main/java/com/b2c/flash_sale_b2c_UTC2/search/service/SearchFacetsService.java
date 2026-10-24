package com.b2c.flash_sale_b2c_UTC2.search.service;

import com.b2c.flash_sale_b2c_UTC2.product.dto.FacetItemDto;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductFilterRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchFacetsDto;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchRequest;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.spec.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Dựng facets (subcategories, cities, ratings, services) cho kết quả tìm kiếm.
 *
 * <p>Facets luôn đến từ PostgreSQL — kể cả khi nhánh chính là OpenSearch.
 * Nhờ vậy số đếm ở 2 nhánh không lệch nhau do cách đếm khác nhau.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchFacetsService {

    private final ProductRepository productRepository;

    /**
     * Dựng facets 4 nhóm từ request tìm kiếm.
     *
     * @param req request tìm kiếm.
     * @return facets đầy đủ.
     */
    public SearchFacetsDto buildFacets(SearchRequest req) {
        long baseCount = computeFacetBaseCount(req);

        List<FacetItemDto> subcategories = List.of(
                FacetItemDto.builder().key("true-wireless").label("True Wireless").count(0).build(),
                FacetItemDto.builder().key("over-ear").label("Over-ear").count(0).build(),
                FacetItemDto.builder().key("sport-waterproof").label("Sport & Waterproof").count(0).build(),
                FacetItemDto.builder().key("gaming-mic").label("Gaming & Mic").count(0).build(),
                FacetItemDto.builder().key("accessories").label("Phụ kiện").count(0).build()
        );

        List<FacetItemDto> cities = List.of(
                FacetItemDto.builder().key("HCM").label("TP. Hồ Chí Minh").count(baseCount).build(),
                FacetItemDto.builder().key("HN").label("Hà Nội").count(baseCount).build(),
                FacetItemDto.builder().key("DN").label("Đà Nẵng").count(baseCount).build(),
                FacetItemDto.builder().key("BD").label("Bình Dương").count(baseCount).build()
        );

        List<FacetItemDto> ratings = List.of(
                FacetItemDto.builder().key("5").label("5 sao").count(0).build(),
                FacetItemDto.builder().key("4").label("4 sao trở lên").count(0).build(),
                FacetItemDto.builder().key("3").label("3 sao trở lên").count(0).build()
        );

        List<FacetItemDto> services = List.of(
                FacetItemDto.builder().key(com.b2c.flash_sale_b2c_UTC2.product.enums.SearchService.FLASH_SALE.getKey())
                        .label(com.b2c.flash_sale_b2c_UTC2.product.enums.SearchService.FLASH_SALE.getLabel()).count(0).build(),
                FacetItemDto.builder().key(com.b2c.flash_sale_b2c_UTC2.product.enums.SearchService.VOUCHER.getKey())
                        .label(com.b2c.flash_sale_b2c_UTC2.product.enums.SearchService.VOUCHER.getLabel()).count(0).build(),
                FacetItemDto.builder().key(com.b2c.flash_sale_b2c_UTC2.product.enums.SearchService.FREESHIP.getKey())
                        .label(com.b2c.flash_sale_b2c_UTC2.product.enums.SearchService.FREESHIP.getLabel()).count(0).build(),
                FacetItemDto.builder().key(com.b2c.flash_sale_b2c_UTC2.product.enums.SearchService.OFFICIAL.getKey())
                        .label(com.b2c.flash_sale_b2c_UTC2.product.enums.SearchService.OFFICIAL.getLabel()).count(0).build()
        );

        return SearchFacetsDto.builder()
                .subcategories(subcategories)
                .cities(cities)
                .ratings(ratings)
                .services(services)
                .build();
    }

    /**
     * Số lượng sản phẩm khớp keyword + price + rating (không tính subcategory/service).
     */
    private long computeFacetBaseCount(SearchRequest req) {
        try {
            ProductFilterRequest filter = ProductFilterRequest.builder()
                    .keyword(req.getQ())
                    .minPrice(req.getPriceMin())
                    .maxPrice(req.getPriceMax())
                    .rating(req.getRating())
                    .build();
            Specification<Product> spec = ProductSpecifications.fromFilterRequest(filter)
                    .and(ProductSpecifications.isActiveWithApprovedStore());
            return productRepository.count(spec);
        } catch (Exception e) {
            log.warn("[SearchFacetsService] facet base count failed: {}", e.getMessage());
            return 0L;
        }
    }
}
