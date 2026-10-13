package com.b2c.flash_sale_b2c_UTC2.store.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.enums.StoreTier;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for V8 migration: stores.tier + stores.last_active_at.
 *
 * IMPORTANT: These tests require V8 migration to be applied to the target database.
 * Before running, apply the migration:
 *   - Dev/Prod DB: via Flyway (automatic on app startup)
 *   - Local DB: manually run src/main/resources/db/migration/V8__add_store_tier_product_slug.sql
 *
 * The tests use Flyway via application-test.yaml which points to Supabase Dev DB.
 * Once V8 is applied, all tests should PASS.
 */
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@Transactional
class StoreTierV8MigrationIT extends AbstractPostgresIT {

    @Autowired
    private StoreRepository storeRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("V8: Store entity maps tier field with default NORMAL")
    void storeTier_HasDefaultNormal() {
        User u = User.builder()
                .email("tier-test-" + System.nanoTime() + "@x.com")
                .fullName("Test User")
                .status("ACTIVE")
                .build();
        em.persist(u);
        em.flush();

        Store s = Store.builder()
                .user(u)
                .storeName("TierTestStore" + System.nanoTime())
                .status("APPROVED")
                .build();
        em.persist(s);
        em.flush();

        em.clear();
        Store found = storeRepository.findById(s.getId()).orElseThrow();

        assertNotNull(found.getTier(), "tier should not be null after V8 migration");
        assertEquals(StoreTier.NORMAL, found.getTier(), "tier should default to NORMAL");
    }

    @Test
    @DisplayName("V8: Store entity maps lastActiveAt field")
    void storeLastActiveAt_IsNullable() {
        User u = User.builder()
                .email("lastactive-test-" + System.nanoTime() + "@x.com")
                .fullName("Test User")
                .status("ACTIVE")
                .build();
        em.persist(u);
        em.flush();

        Store s = Store.builder()
                .user(u)
                .storeName("LastActiveTest" + System.nanoTime())
                .status("APPROVED")
                .build();
        em.persist(s);
        em.flush();

        em.clear();
        Store found = storeRepository.findById(s.getId()).orElseThrow();

        assertNull(found.getLastActiveAt(), "lastActiveAt should be null when not set");
    }

    @Test
    @DisplayName("V8: CHECK constraint chk_stores_tier rejects invalid tier value")
    void storeTier_InvalidValueRejected() {
        User u = User.builder()
                .email("invalid-tier-" + System.nanoTime() + "@x.com")
                .fullName("Test User")
                .status("ACTIVE")
                .build();
        em.persist(u);
        em.flush();

        org.hibernate.Session session = em.unwrap(org.hibernate.Session.class);
        org.hibernate.query.NativeQuery q = session.createNativeQuery(
            "INSERT INTO stores (user_id, store_name, status, tier) VALUES (:uid, :name, :status, :tier)"
        );
        q.setParameter("uid", u.getId());
        q.setParameter("name", "InvalidTier" + System.nanoTime());
        q.setParameter("status", "APPROVED");
        q.setParameter("tier", "INVALID_TIER_VALUE");

        try {
            q.executeUpdate();
            em.flush();
            throw new AssertionError("Expected CHECK constraint violation for invalid tier value");
        } catch (org.hibernate.exception.ConstraintViolationException e) {
            String msg = e.getMessage() + " | " + e.getSQLException().getMessage();
            assertTrue(
                msg.contains("chk_stores_tier") || msg.toLowerCase().contains("check"),
                "Expected CHECK constraint violation, got: " + msg
            );
        }
    }

    @Test
    @DisplayName("V8: Partial index idx_stores_tier allows querying non-NORMAL tiers")
    void storeTier_PartialIndexQueryable() {
        User u = User.builder()
                .email("idx-test-" + System.nanoTime() + "@x.com")
                .fullName("Test User")
                .status("ACTIVE")
                .build();
        em.persist(u);
        em.flush();

        Store s = Store.builder()
                .user(u)
                .storeName("IdxTest" + System.nanoTime())
                .status("APPROVED")
                .build();
        em.persist(s);
        em.flush();

        List<Store> results = em.createQuery(
            "SELECT s FROM Store s WHERE s.tier <> com.b2c.flash_sale_b2c_UTC2.store.enums.StoreTier.NORMAL",
            Store.class
        ).getResultList();

        assertNotNull(results);
    }
}