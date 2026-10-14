package com.b2c.flash_sale_b2c_UTC2.voucher.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.Voucher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class VoucherRepositoryIT extends AbstractPostgresIT {

    @Autowired private VoucherRepository voucherRepository;
    @PersistenceContext private EntityManager em;

    private Voucher persistVoucher(String code, Store store, String status) {
        Instant now = Instant.now();
        Voucher v = Voucher.builder()
                .code(code).store(store)
                .discountType("FIXED").discountValue(new BigDecimal("10000"))
                .totalQuantity(100).usedQuantity(0).userUsageLimit(1)
                .startTime(now).endTime(now.plus(30, ChronoUnit.DAYS))
                .status(status).createdAt(now).build();
        em.persist(v);
        em.flush();
        return v;
    }

    private Store createStore() {
        User u = User.builder().email("vs-" + System.nanoTime() + "@x.com").fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        Store s = Store.builder().user(u).storeName("S-" + System.nanoTime()).status("APPROVED").build();
        em.persist(s);
        em.flush();
        return s;
    }

    @Test
    @DisplayName("findByCode returns matching voucher")
    void findByCode_Success() {
        Voucher v = persistVoucher("V-FIND", null, "ACTIVE");

        Optional<Voucher> found = voucherRepository.findByCode("V-FIND");

        assertTrue(found.isPresent());
        assertEquals(v.getId(), found.get().getId());
    }

    @Test
    @DisplayName("existsByCode true when exists")
    void existsByCode_True() {
        persistVoucher("V-EXISTS", null, "ACTIVE");
        assertTrue(voucherRepository.existsByCode("V-EXISTS"));
    }

    @Test
    @DisplayName("existsByCode false when not exists")
    void existsByCode_False() {
        assertFalse(voucherRepository.existsByCode("V-NOT-EXISTS"));
    }

    @Test
    @DisplayName("findByStoreId returns store vouchers")
    void findByStoreId_ReturnsStoreVouchers() {
        Store s = createStore();
        persistVoucher("V-S1", s, "ACTIVE");

        List<Voucher> result = voucherRepository.findByStoreId(s.getId());

        assertEquals(1, result.size());
        assertEquals("V-S1", result.get(0).getCode());
    }

    @Test
    @DisplayName("findByStoreIdIsNull returns platform vouchers")
    void findByStoreIdIsNull_ReturnsPlatform() {
        persistVoucher("V-PLAT-1", null, "ACTIVE");
        Store s = createStore();
        persistVoucher("V-S-1", s, "ACTIVE");

        List<Voucher> result = voucherRepository.findByStoreIdIsNull();

        assertEquals(1, result.size());
        assertEquals("V-PLAT-1", result.get(0).getCode());
        assertNull(result.get(0).getStore());
    }
}