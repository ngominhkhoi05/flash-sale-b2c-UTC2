package com.b2c.flash_sale_b2c_UTC2.product.spec;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleExpirationScheduler;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleReservationService;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductFilterRequest;
import com.b2c.flash_sale_b2c_UTC2.product.enums.ProductSortOption;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

/**
 * Integration test (real Postgres) for the bug:
 *   PathElementException: Could not resolve attribute 'variants' of 'Product'.
 *
 * This test goes through the exact code path that the live
 * GET /api/v1/products endpoint uses:
 *   1. Build Specification<Product> from ProductFilterRequest
 *   2. Execute via ProductRepository.findAll(spec, pageable)
 *   3. Hibernate's CriteriaQuery tries to resolve all path expressions
 *
 * Before the fix, this test failed with PathElementException on the
 * root.join("variants") call in ProductSpecifications.hasVariantWithPriceGte/Lte.
 *
 * After the fix, the call must succeed and return a Page.
 *
 * Test scheduling is disabled via @TestConfiguration that replaces
 * the scheduler beans with mocks, so background @Scheduled jobs do
 * not race for the same DB rows during this test.
 */
@Import(ProductSpecificationsIT.MockSchedulerConfig.class)
class ProductSpecificationsIT extends AbstractPostgresIT {

    @TestConfiguration
    static class MockSchedulerConfig {
        @Bean
        @Primary
        FlashSaleExpirationScheduler flashSaleExpirationScheduler() {
            return mock(FlashSaleExpirationScheduler.class);
        }

        @Bean
        @Primary
        FlashSaleReservationService flashSaleReservationService() {
            return mock(FlashSaleReservationService.class);
        }
    }

    @Autowired private ProductRepository productRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDb() {
        jdbcTemplate.execute("TRUNCATE TABLE order_items, orders, voucher_usages, cart_items, carts, " +
                "user_vouchers, payments, wallet_transactions, wallets, product_reviews, " +
                "flash_sale_items, flash_sale_slots, product_variants, products, categories, " +
                "images, addresses, stores, auth_accounts, user_roles, users, banners, " +
                "permissions, group_permissions, permission_groups, roles, vouchers " +
                "RESTART IDENTITY CASCADE");
    }

    @Test
    @Transactional
    @DisplayName("minPrice filter: real Postgres query runs without PathElementException (regression)")
    void minPrice_filter_runsAgainstRealDb() {
        ProductFilterRequest f = new ProductFilterRequest();
        f.setMinPrice(new BigDecimal("1000"));
        f.setSortOption(ProductSortOption.CREATED_AT_DESC);

        Specification<com.b2c.flash_sale_b2c_UTC2.product.entity.Product> spec =
                ProductSpecifications.fromFilterRequest(f);

        Page<com.b2c.flash_sale_b2c_UTC2.product.entity.Product> result = assertDoesNotThrow(
                () -> productRepository.findAll(spec, PageRequest.of(0, 20)),
                "Repository with minPrice filter must not throw PathElementException for 'variants'"
        );

        // We do not assert content (we did not seed data) — only that the SQL
        // build phase succeeds. On the buggy code this throws before any DB call.
        org.junit.jupiter.api.Assertions.assertNotNull(result);
    }

    @Test
    @Transactional
    @DisplayName("maxPrice filter: real Postgres query runs without PathElementException (regression)")
    void maxPrice_filter_runsAgainstRealDb() {
        ProductFilterRequest f = new ProductFilterRequest();
        f.setMaxPrice(new BigDecimal("999999"));
        f.setSortOption(ProductSortOption.CREATED_AT_DESC);

        Specification<com.b2c.flash_sale_b2c_UTC2.product.entity.Product> spec =
                ProductSpecifications.fromFilterRequest(f);

        Page<com.b2c.flash_sale_b2c_UTC2.product.entity.Product> result = assertDoesNotThrow(
                () -> productRepository.findAll(spec, PageRequest.of(0, 20)),
                "Repository with maxPrice filter must not throw PathElementException for 'variants'"
        );

        org.junit.jupiter.api.Assertions.assertNotNull(result);
    }
}
