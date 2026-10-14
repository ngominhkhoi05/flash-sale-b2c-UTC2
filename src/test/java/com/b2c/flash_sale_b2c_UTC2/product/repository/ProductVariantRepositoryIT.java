package com.b2c.flash_sale_b2c_UTC2.product.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class ProductVariantRepositoryIT extends AbstractPostgresIT {

    @Autowired private ProductVariantRepository variantRepository;
    @PersistenceContext private EntityManager em;

    private User persistUser(String email) {
        User u = User.builder().email(email).fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        return u;
    }

    private Store persistStore(String name, User user) {
        Store s = Store.builder().user(user).storeName(name).status("APPROVED").build();
        em.persist(s);
        em.flush();
        return s;
    }

    private Category persistCategory(String name) {
        Category c = Category.builder().name(name).slug(name.toLowerCase()).build();
        em.persist(c);
        em.flush();
        return c;
    }

    private Product persistProduct(String name, Store store, Category cat) {
        Product p = Product.builder().name(name).store(store).category(cat).status("ACTIVE").build();
        em.persist(p);
        em.flush();
        return p;
    }

    private ProductVariant persistVariant(String sku, int stock) {
        User u = persistUser("s-" + sku + "@x.com");
        Store store = persistStore("Store " + sku, u);
        Category cat = persistCategory("Cat " + sku);
        Product product = persistProduct("P " + sku, store, cat);
        ProductVariant v = ProductVariant.builder()
                .product(product).sku(sku).variantName("V " + sku)
                .originalPrice(new BigDecimal("100000")).stockQuantity(stock)
                .status("ACTIVE").version(0L).build();
        em.persist(v);
        em.flush();
        return v;
    }

    @Test
    @DisplayName("findBySku returns variant when exists")
    void findBySku_Success() {
        ProductVariant v = persistVariant("SKU-FIND", 10);

        Optional<ProductVariant> found = variantRepository.findBySku("SKU-FIND");

        assertTrue(found.isPresent());
        assertEquals(v.getId(), found.get().getId());
    }

    @Test
    @DisplayName("existsBySku true when SKU exists")
    void existsBySku_True() {
        persistVariant("SKU-EXISTS", 5);
        assertTrue(variantRepository.existsBySku("SKU-EXISTS"));
    }

    @Test
    @DisplayName("existsBySkuAndIdNot false for same id")
    void existsBySkuAndIdNot_OwnId() {
        ProductVariant v = persistVariant("SKU-OWN", 5);
        assertFalse(variantRepository.existsBySkuAndIdNot("SKU-OWN", v.getId()));
    }

    @Test
    @DisplayName("existsBySkuAndIdNot true for other id")
    void existsBySkuAndIdNot_OtherId() {
        ProductVariant v = persistVariant("SKU-OTHER", 5);
        assertTrue(variantRepository.existsBySkuAndIdNot("SKU-OTHER", 999L));
    }

    @Test
    @DisplayName("deductStockQuantityConditionally decrements when enough")
    void deductStock_Success() {
        ProductVariant v = persistVariant("SKU-DEDUCT", 10);

        int updated = variantRepository.deductStockQuantityConditionally(v.getId(), 3);

        assertEquals(1, updated);
        em.clear();
        ProductVariant reloaded = variantRepository.findById(v.getId()).orElseThrow();
        assertEquals(7, reloaded.getStockQuantity());
    }

    @Test
    @DisplayName("deductStockQuantityConditionally no-op when not enough")
    void deductStock_Fail_WhenInsufficient() {
        ProductVariant v = persistVariant("SKU-NO", 2);

        int updated = variantRepository.deductStockQuantityConditionally(v.getId(), 10);

        assertEquals(0, updated);
        em.clear();
        ProductVariant reloaded = variantRepository.findById(v.getId()).orElseThrow();
        assertEquals(2, reloaded.getStockQuantity());
    }

    @Test
    @DisplayName("replenishStockQuantityConditionally increments stock")
    void replenishStock_Success() {
        ProductVariant v = persistVariant("SKU-REPLENISH", 5);

        int updated = variantRepository.replenishStockQuantityConditionally(v.getId(), 10);

        assertEquals(1, updated);
        em.clear();
        ProductVariant reloaded = variantRepository.findById(v.getId()).orElseThrow();
        assertEquals(15, reloaded.getStockQuantity());
    }

    @Test
    @DisplayName("findByProductIdInAndStatus returns ACTIVE variants by productIds")
    void findByProductIdInAndStatus_FiltersActive() {
        ProductVariant v1 = persistVariant("SKU-A", 5);
        List<ProductVariant> result = variantRepository.findByProductIdInAndStatus(
                List.of(v1.getProduct().getId()), "ACTIVE");

        assertEquals(1, result.size());
        assertEquals("SKU-A", result.get(0).getSku());
    }
}