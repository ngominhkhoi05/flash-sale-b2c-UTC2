package com.b2c.flash_sale_b2c_UTC2.search.service;

import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchFacetsDto;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchRequest;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.repository.CategoryRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchFacetsServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private SearchFacetsService searchFacetsService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("buildFacets returns real subcategories with counts and ratings")
    void buildFacets_ShouldReturnRealSubcategoriesAndRatings() {
        SearchRequest req = SearchRequest.builder()
                .q("tai nghe")
                .build();

        Object[] row1 = new Object[]{1, "Tai Nghe & Âm Thanh", "tai-nghe", 15L};
        when(productRepository.countProductsByCategoryWithKeyword("tai nghe"))
                .thenReturn(List.<Object[]>of(row1));

        Category cat1 = Category.builder().id(1).name("Tai Nghe & Âm Thanh").slug("tai-nghe").build();
        Category cat2 = Category.builder().id(2).name("Điện Thoại & Phụ Kiện").slug("dien-thoai").build();
        when(categoryRepository.findAll()).thenReturn(List.of(cat1, cat2));

        when(productRepository.count(any(Specification.class))).thenReturn(5L);

        SearchFacetsDto facets = searchFacetsService.buildFacets(req);

        assertNotNull(facets);
        assertEquals(2, facets.getSubcategories().size());

        assertEquals("tai-nghe", facets.getSubcategories().get(0).getKey());
        assertEquals("Tai Nghe & Âm Thanh", facets.getSubcategories().get(0).getLabel());
        assertEquals(15L, facets.getSubcategories().get(0).getCount());

        assertEquals("dien-thoai", facets.getSubcategories().get(1).getKey());
        assertEquals("Điện Thoại & Phụ Kiện", facets.getSubcategories().get(1).getLabel());
        assertEquals(0L, facets.getSubcategories().get(1).getCount());

        assertEquals(3, facets.getRatings().size());
        assertEquals("5", facets.getRatings().get(0).getKey());
        assertEquals("4", facets.getRatings().get(1).getKey());
        assertEquals("3", facets.getRatings().get(2).getKey());

        // Cities & Services are omitted/empty per user requirements
        assertTrue(facets.getCities().isEmpty());
        assertTrue(facets.getServices().isEmpty());
    }
}
