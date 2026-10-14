package com.b2c.flash_sale_b2c_UTC2.flashsale.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class FlashSaleSlotRepositoryIT extends AbstractPostgresIT {

    @Autowired private FlashSaleSlotRepository slotRepository;
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

    private FlashSaleSlot persistSlot(String title, Instant start, Instant end, String status) {
        FlashSaleSlot s = FlashSaleSlot.builder()
                .title(title).startTime(start).endTime(end)
                .reservationTtlSeconds(300).status(status)
                .createdAt(Instant.now()).build();
        em.persist(s);
        em.flush();
        return s;
    }

    @Test
    @DisplayName("findByStatusOrderByStartTimeAsc sorts by start_time ASC")
    void findByStatusOrderByStartTimeAsc_Sorts() {
        Instant now = Instant.now();
        persistSlot("S2", now.plus(2, ChronoUnit.HOURS), now.plus(3, ChronoUnit.HOURS), "UPCOMING");
        persistSlot("S1", now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), "UPCOMING");

        List<FlashSaleSlot> result = slotRepository.findByStatusOrderByStartTimeAsc("UPCOMING");

        assertEquals(2, result.size());
        assertEquals("S1", result.get(0).getTitle());
        assertEquals("S2", result.get(1).getTitle());
    }

    @Test
    @DisplayName("findByStatusInOrderByStartTimeAsc supports multiple statuses")
    void findByStatusInOrderByStartTimeAsc_MultipleStatuses() {
        Instant now = Instant.now();
        persistSlot("ACTIVE-1", now, now.plus(1, ChronoUnit.HOURS), "ACTIVE");
        persistSlot("UPCOMING-1", now.plus(2, ChronoUnit.HOURS), now.plus(3, ChronoUnit.HOURS), "UPCOMING");

        List<FlashSaleSlot> result = slotRepository.findByStatusInOrderByStartTimeAsc(List.of("ACTIVE", "UPCOMING"));

        assertEquals(2, result.size());
        assertEquals("ACTIVE-1", result.get(0).getTitle());
    }

    @Test
    @DisplayName("existsOverlappingSlot true when time range overlaps")
    void existsOverlappingSlot_True() {
        Instant now = Instant.now();
        persistSlot("EXIST", now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), "UPCOMING");

        boolean overlap = slotRepository.existsOverlappingSlot(
                null,
                now.plus(1, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES),
                now.plus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES));

        assertTrue(overlap);
    }

    @Test
    @DisplayName("existsOverlappingSlot false when no overlap")
    void existsOverlappingSlot_False() {
        Instant now = Instant.now();
        persistSlot("EXIST", now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), "UPCOMING");

        boolean overlap = slotRepository.existsOverlappingSlot(
                null,
                now.plus(3, ChronoUnit.HOURS),
                now.plus(4, ChronoUnit.HOURS));

        assertFalse(overlap);
    }

    @Test
    @DisplayName("existsOverlappingSlot ignores ENDED slots")
    void existsOverlappingSlot_IgnoresEnded() {
        Instant now = Instant.now();
        persistSlot("ENDED", now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), "ENDED");

        boolean overlap = slotRepository.existsOverlappingSlot(
                null,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(2, ChronoUnit.HOURS));

        assertFalse(overlap);
    }

    @Test
    @DisplayName("existsOverlappingSlot excludeId for update")
    void existsOverlappingSlot_ExcludeId() {
        Instant now = Instant.now();
        FlashSaleSlot s = persistSlot("SELF", now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), "UPCOMING");

        boolean overlap = slotRepository.existsOverlappingSlot(
                s.getId(),
                now.plus(1, ChronoUnit.HOURS),
                now.plus(2, ChronoUnit.HOURS));

        assertFalse(overlap);
    }

    @Test
    @DisplayName("findSlotsToActivate returns UPCOMING slots past start_time")
    void findSlotsToActivate_ReturnsDueSlots() {
        Instant now = Instant.now();
        persistSlot("ACTIVATABLE", now.minus(5, ChronoUnit.MINUTES), now.plus(1, ChronoUnit.HOURS), "UPCOMING");
        persistSlot("FUTURE", now.plus(2, ChronoUnit.HOURS), now.plus(3, ChronoUnit.HOURS), "UPCOMING");

        List<FlashSaleSlot> result = slotRepository.findSlotsToActivate(Instant.now());

        assertEquals(1, result.size());
        assertEquals("ACTIVATABLE", result.get(0).getTitle());
    }

    @Test
    @DisplayName("findSlotsToEnd returns ACTIVE slots past end_time")
    void findSlotsToEnd_ReturnsExpiredActiveSlots() {
        Instant now = Instant.now();
        persistSlot("TO_END", now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS), "ACTIVE");
        persistSlot("STILL_ACTIVE", now.minus(30, ChronoUnit.MINUTES), now.plus(1, ChronoUnit.HOURS), "ACTIVE");

        List<FlashSaleSlot> result = slotRepository.findSlotsToEnd(Instant.now());

        assertEquals(1, result.size());
        assertEquals("TO_END", result.get(0).getTitle());
    }
}