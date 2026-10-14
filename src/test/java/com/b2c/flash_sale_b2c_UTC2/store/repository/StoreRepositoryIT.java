package com.b2c.flash_sale_b2c_UTC2.store.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class StoreRepositoryIT extends AbstractPostgresIT {

    @Autowired private StoreRepository storeRepository;
    @PersistenceContext private EntityManager em;

    private Store persistStore(String name, String status) {
        User u = User.builder()
                .email("store-" + name + "@x.com").fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        Store s = Store.builder().user(u).storeName(name).status(status).build();
        em.persist(s);
        em.flush();
        return s;
    }

    @Test
    @DisplayName("findByUserId returns store of user")
    void findByUserId_Success() {
        Store s = persistStore("S-1", "APPROVED");

        Optional<Store> found = storeRepository.findByUserId(s.getUser().getId());

        assertTrue(found.isPresent());
        assertEquals(s.getId(), found.get().getId());
    }

    @Test
    @DisplayName("findByStoreName returns store by name")
    void findByStoreName_Success() {
        Store s = persistStore("S-Find", "PENDING");

        Optional<Store> found = storeRepository.findByStoreName("S-Find");

        assertTrue(found.isPresent());
        assertEquals(s.getId(), found.get().getId());
    }

    @Test
    @DisplayName("existsByStoreName true when duplicate name")
    void existsByStoreName_True() {
        persistStore("S-Dup", "PENDING");
        assertTrue(storeRepository.existsByStoreName("S-Dup"));
    }

    @Test
    @DisplayName("existsByUserId true when user has store")
    void existsByUserId_True() {
        Store s = persistStore("S-User-Exists", "PENDING");
        assertTrue(storeRepository.existsByUserId(s.getUser().getId()));
    }

    @Test
    @DisplayName("existsByUserId false when user has no store")
    void existsByUserId_False() {
        assertFalse(storeRepository.existsByUserId(99999L));
    }
}