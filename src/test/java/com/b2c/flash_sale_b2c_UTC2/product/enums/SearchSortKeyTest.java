package com.b2c.flash_sale_b2c_UTC2.product.enums;

import com.b2c.flash_sale_b2c_UTC2.product.enums.ProductSortOption;
import com.b2c.flash_sale_b2c_UTC2.product.enums.SearchSortKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchSortKeyTest {

    @Test
    @DisplayName("fromValue: 'relevance' -> RELEVANCE")
    void fromValue_relevance_returnsRelevance() {
        assertEquals(SearchSortKey.RELEVANCE, SearchSortKey.fromValue("relevance"));
    }

    @Test
    @DisplayName("fromValue: 'newest' -> NEWEST")
    void fromValue_newest_returnsNewest() {
        assertEquals(SearchSortKey.NEWEST, SearchSortKey.fromValue("newest"));
    }

    @Test
    @DisplayName("fromValue: 'best_selling' -> BEST_SELLING")
    void fromValue_bestSelling_returnsBestSelling() {
        assertEquals(SearchSortKey.BEST_SELLING, SearchSortKey.fromValue("best_selling"));
    }

    @Test
    @DisplayName("fromValue: 'price_asc' -> PRICE_ASC")
    void fromValue_priceAsc_returnsPriceAsc() {
        assertEquals(SearchSortKey.PRICE_ASC, SearchSortKey.fromValue("price_asc"));
    }

    @Test
    @DisplayName("fromValue: 'price_desc' -> PRICE_DESC")
    void fromValue_priceDesc_returnsPriceDesc() {
        assertEquals(SearchSortKey.PRICE_DESC, SearchSortKey.fromValue("price_desc"));
    }

    @Test
    @DisplayName("fromValue: null -> RELEVANCE (default)")
    void fromValue_null_returnsRelevance() {
        assertEquals(SearchSortKey.RELEVANCE, SearchSortKey.fromValue(null));
    }

    @Test
    @DisplayName("fromValue: blank -> RELEVANCE (default)")
    void fromValue_blank_returnsRelevance() {
        assertEquals(SearchSortKey.RELEVANCE, SearchSortKey.fromValue("  "));
    }

    @Test
    @DisplayName("fromValue: unknown -> RELEVANCE (lenient fallback)")
    void fromValue_unknown_returnsRelevance() {
        assertEquals(SearchSortKey.RELEVANCE, SearchSortKey.fromValue("unknown_key"));
    }

    @Test
    @DisplayName("toProductSortOption: RELEVANCE -> CREATED_AT_DESC")
    void toProductSortOption_relevance_returnsCreatedAtDesc() {
        assertEquals(ProductSortOption.CREATED_AT_DESC, SearchSortKey.RELEVANCE.toProductSortOption());
    }

    @Test
    @DisplayName("toProductSortOption: NEWEST -> CREATED_AT_DESC")
    void toProductSortOption_newest_returnsCreatedAtDesc() {
        assertEquals(ProductSortOption.CREATED_AT_DESC, SearchSortKey.NEWEST.toProductSortOption());
    }

    @Test
    @DisplayName("toProductSortOption: BEST_SELLING -> SOLD_DESC")
    void toProductSortOption_bestSelling_returnsSoldDesc() {
        assertEquals(ProductSortOption.SOLD_DESC, SearchSortKey.BEST_SELLING.toProductSortOption());
    }

    @Test
    @DisplayName("toProductSortOption: PRICE_ASC -> PRICE_ASC")
    void toProductSortOption_priceAsc_returnsPriceAsc() {
        assertEquals(ProductSortOption.PRICE_ASC, SearchSortKey.PRICE_ASC.toProductSortOption());
    }

    @Test
    @DisplayName("toProductSortOption: PRICE_DESC -> PRICE_DESC")
    void toProductSortOption_priceDesc_returnsPriceDesc() {
        assertEquals(ProductSortOption.PRICE_DESC, SearchSortKey.PRICE_DESC.toProductSortOption());
    }
}
