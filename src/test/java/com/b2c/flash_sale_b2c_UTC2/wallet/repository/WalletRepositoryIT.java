package com.b2c.flash_sale_b2c_UTC2.wallet.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.Wallet;
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
class WalletRepositoryIT extends AbstractPostgresIT {

    @Autowired private WalletRepository walletRepository;
    @PersistenceContext private EntityManager em;

    private Store persistStore(String name) {
        User u = User.builder().email("ws-" + System.nanoTime() + "@x.com").fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        Store s = Store.builder().user(u).storeName(name).status("APPROVED").build();
        em.persist(s);
        em.flush();
        return s;
    }

    @Test
    @DisplayName("findByStoreId returns store wallet")
    void findByStoreId_Success() {
        Store s = persistStore("S-Wallet");
        Wallet w = Wallet.builder().store(s).balance(BigDecimal.ZERO).frozenBalance(BigDecimal.ZERO).build();
        em.persist(w);
        em.flush();

        Optional<Wallet> found = walletRepository.findByStoreId(s.getId());

        assertTrue(found.isPresent());
        assertEquals(w.getId(), found.get().getId());
    }

    @Test
    @DisplayName("Wallet default balance is zero")
    void defaultBalance_Zero() {
        Store s = persistStore("S-Wallet-Default");
        Wallet w = Wallet.builder().store(s).build();
        em.persist(w);
        em.flush();

        Wallet reloaded = walletRepository.findById(w.getId()).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(reloaded.getBalance()));
    }

    @Test
    @DisplayName("findByStoreId empty when store has no wallet")
    void findByStoreId_Empty() {
        Optional<Wallet> found = walletRepository.findByStoreId(99999L);
        assertTrue(found.isEmpty());
    }
}