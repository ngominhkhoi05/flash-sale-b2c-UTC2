package com.b2c.flash_sale_b2c_UTC2.product.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class CategoryRepositoryIT extends AbstractPostgresIT {

    @Autowired private CategoryRepository categoryRepository;
    @PersistenceContext private EntityManager em;

    private Category persistCategory(String name, String slug) {
        Category c = Category.builder().name(name).slug(slug).build();
        em.persist(c);
        em.flush();
        return c;
    }

    @Test
    @DisplayName("findBySlug returns category by slug")
    void findBySlug_Success() {
        persistCategory("Electronics", "electronics");

        Optional<Category> found = categoryRepository.findBySlug("electronics");

        assertTrue(found.isPresent());
        assertEquals("Electronics", found.get().getName());
    }

    @Test
    @DisplayName("existsBySlug true when duplicate slug")
    void existsBySlug_True() {
        persistCategory("Fashion", "fashion");
        assertTrue(categoryRepository.existsBySlug("fashion"));
    }

    @Test
    @DisplayName("existsByName true when duplicate name")
    void existsByName_True() {
        persistCategory("Beauty", "beauty");
        assertTrue(categoryRepository.existsByName("Beauty"));
    }

    @Test
    @DisplayName("Custom commission rate is persisted")
    void customCommissionRate_Persisted() {
        Category c = Category.builder()
                .name("Luxury").slug("luxury")
                .commissionRate(new BigDecimal("0.1500")).build();
        em.persist(c);
        em.flush();

        Category found = categoryRepository.findById(c.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("0.1500").compareTo(found.getCommissionRate()));
    }
}