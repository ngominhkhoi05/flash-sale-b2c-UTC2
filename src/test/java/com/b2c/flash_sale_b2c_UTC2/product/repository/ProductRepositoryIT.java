package com.b2c.flash_sale_b2c_UTC2.product.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class ProductRepositoryIT extends AbstractPostgresIT {

    @Autowired private ProductRepository productRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @PersistenceContext private EntityManager em;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.execute("TRUNCATE TABLE order_items, orders, voucher_usages, cart_items, carts, " +
                "wallet_transactions, payments, flash_sale_items, flash_sale_slots, " +
                "product_reviews, product_variants, products, categories, images, " +
                "addresses, stores, wallets, auth_accounts, user_roles, " +
                "users, roles, vouchers RESTART IDENTITY CASCADE");
    }

    private Product persistProduct(String name, String status, Store store, Category cat) {
        Product p = Product.builder()
                .store(store).category(cat).name(name).status(status).build();
        em.persist(p);
        em.flush();
        return p;
    }

    private Store createStore() {
        User u = User.builder()
                .email("ps-" + System.nanoTime() + "@x.com").fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        Store s = Store.builder().user(u).storeName("S-" + System.nanoTime()).status("APPROVED").build();
        em.persist(s);
        em.flush();
        return s;
    }

    private Category createCategory(String name) {
        Category c = Category.builder().name(name).slug(name + "-slug").build();
        em.persist(c);
        em.flush();
        return c;
    }

    @Test
    @DisplayName("findPublicProducts no filter returns ACTIVE products of APPROVED store")
    void findPublicProducts_NoFilter() {
        Store s = createStore();
        Category c = createCategory("C1");
        persistProduct("P1", "ACTIVE", s, c);
        persistProduct("P2", "INACTIVE", s, c);

        Page<Product> page = productRepository.findPublicProducts(
                -1, "", new BigDecimal("-1"), new BigDecimal("-1"),
                PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("P1", page.getContent().get(0).getName());
    }

    @Test
    @DisplayName("findPublicProducts filters by keyword")
    void findPublicProducts_ByKeyword() {
        Store s = createStore();
        Category c = createCategory("C2");
        persistProduct("iPhone 15", "ACTIVE", s, c);
        persistProduct("Samsung Galaxy", "ACTIVE", s, c);

        Page<Product> page = productRepository.findPublicProducts(
                -1, "iPhone", new BigDecimal("-1"), new BigDecimal("-1"),
                PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("iPhone 15", page.getContent().get(0).getName());
    }

    @Test
    @DisplayName("findPublicProducts filters by categoryId")
    void findPublicProducts_ByCategory() {
        Store s = createStore();
        Category c1 = createCategory("C-A");
        Category c2 = createCategory("C-B");
        persistProduct("PA", "ACTIVE", s, c1);
        persistProduct("PB", "ACTIVE", s, c2);

        Page<Product> page = productRepository.findPublicProducts(
                c1.getId().intValue(), "", new BigDecimal("-1"), new BigDecimal("-1"),
                PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("PA", page.getContent().get(0).getName());
    }

    @Test
    @DisplayName("findPublicProducts skips products of non-APPROVED store")
    void findPublicProducts_SkipsUnapprovedStore() {
        User u = User.builder().email("pu@x.com").fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        Store pendingStore = Store.builder().user(u).storeName("Pending").status("PENDING").build();
        em.persist(pendingStore);
        em.flush();
        Category c = createCategory("C-Pend");
        persistProduct("Hidden", "ACTIVE", pendingStore, c);

        Page<Product> page = productRepository.findPublicProducts(
                -1, "", new BigDecimal("-1"), new BigDecimal("-1"),
                PageRequest.of(0, 10));

        assertEquals(0, page.getTotalElements());
    }

    @Test
    @DisplayName("findSellerProducts returns products of store by status")
    void findSellerProducts_ByStatus() {
        Store s = createStore();
        Category c = createCategory("C-S");
        persistProduct("Active", "ACTIVE", s, c);
        persistProduct("Inactive", "INACTIVE", s, c);

        Page<Product> activePage = productRepository.findSellerProducts(
                s.getId(), "ACTIVE", PageRequest.of(0, 10));
        Page<Product> inactivePage = productRepository.findSellerProducts(
                s.getId(), "INACTIVE", PageRequest.of(0, 10));

        assertEquals(1, activePage.getTotalElements());
        assertEquals("Active", activePage.getContent().get(0).getName());
        assertEquals(1, inactivePage.getTotalElements());
    }

    @Test
    @DisplayName("findByIdWithStoreAndCategory returns product with fetch join")
    void findByIdWithStoreAndCategory() {
        Store s = createStore();
        Category c = createCategory("C-Fetch");
        Product p = persistProduct("Fetched", "ACTIVE", s, c);

        Optional<Product> found = productRepository.findByIdWithStoreAndCategory(p.getId());

        assertTrue(found.isPresent());
        assertNotNull(found.get().getStore());
        assertNotNull(found.get().getCategory());
    }
}