package com.b2c.flash_sale_b2c_UTC2.flashsale.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class FlashSaleItemRepositoryIT extends AbstractPostgresIT {

    @Autowired private FlashSaleItemRepository itemRepository;
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
        Category c = Category.builder().name(name).slug("c-" + System.nanoTime()).build();
        em.persist(c);
        em.flush();
        return c;
    }

    private Product persistProduct(String name, Store store, Category cat) {
        Product p = Product.builder().store(store).category(cat).name(name).build();
        em.persist(p);
        em.flush();
        return p;
    }

    private ProductVariant persistVariant(String sku, int stock) {
        User u = persistUser("u-" + System.nanoTime() + "@x.com");
        Store store = persistStore("S-" + System.nanoTime(), u);
        Category cat = persistCategory("C-" + System.nanoTime());
        Product product = persistProduct("P-" + System.nanoTime(), store, cat);
        ProductVariant v = ProductVariant.builder()
                .product(product).sku(sku).variantName("V")
                .originalPrice(new BigDecimal("100000")).stockQuantity(stock)
                .status("ACTIVE").version(0L).build();
        em.persist(v);
        em.flush();
        return v;
    }

    private FlashSaleSlot persistSlot(String title, String status) {
        FlashSaleSlot s = FlashSaleSlot.builder()
                .title(title).startTime(Instant.now()).endTime(Instant.now().plus(2, ChronoUnit.HOURS))
                .reservationTtlSeconds(300).status(status).createdAt(Instant.now()).build();
        em.persist(s);
        em.flush();
        return s;
    }

    private FlashSaleItem persistItem(String slotStatus, String itemStatus, int allocated, int available) {
        ProductVariant variant = persistVariant("SKU-" + System.nanoTime(), 100);
        FlashSaleSlot slot = persistSlot("Slot-" + System.nanoTime(), slotStatus);

        FlashSaleItem item = FlashSaleItem.builder()
                .slot(slot).variant(variant)
                .flashSalePrice(new BigDecimal("50000"))
                .allocatedStock(allocated).availableStock(available)
                .userPurchaseLimit(2).status(itemStatus).createdAt(Instant.now()).build();
        em.persist(item);
        em.flush();
        return item;
    }

    @Test
    @DisplayName("findBySlotIdAndVariantId returns matching item")
    void findBySlotIdAndVariantId_Success() {
        FlashSaleItem item = persistItem("UPCOMING", "APPROVED", 10, 10);

        Optional<FlashSaleItem> found = itemRepository.findBySlotIdAndVariantId(
                item.getSlot().getId(), item.getVariant().getId());

        assertTrue(found.isPresent());
        assertEquals(item.getId(), found.get().getId());
    }

    @Test
    @DisplayName("findBySlotId returns all items in slot")
    void findBySlotId_ReturnsAll() {
        FlashSaleItem i1 = persistItem("UPCOMING", "APPROVED", 10, 10);

        FlashSaleSlot slot = i1.getSlot();
        ProductVariant variant = persistVariant("SKU2-" + System.nanoTime(), 50);
        FlashSaleItem item2 = FlashSaleItem.builder()
                .slot(slot).variant(variant)
                .flashSalePrice(new BigDecimal("50000"))
                .allocatedStock(20).availableStock(20)
                .userPurchaseLimit(1).status("PENDING_APPROVAL").createdAt(Instant.now()).build();
        em.persist(item2);
        em.flush();

        List<FlashSaleItem> result = itemRepository.findBySlotId(slot.getId());

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("findBySlotIdAndStatus filters by status")
    void findBySlotIdAndStatus_Filters() {
        FlashSaleItem i1 = persistItem("UPCOMING", "APPROVED", 10, 10);
        FlashSaleSlot slot = i1.getSlot();
        ProductVariant variant = persistVariant("SKUP-" + System.nanoTime(), 50);
        FlashSaleItem item2 = FlashSaleItem.builder()
                .slot(slot).variant(variant)
                .flashSalePrice(new BigDecimal("50000"))
                .allocatedStock(5).availableStock(5)
                .userPurchaseLimit(1).status("PENDING_APPROVAL").createdAt(Instant.now()).build();
        em.persist(item2);
        em.flush();

        List<FlashSaleItem> approved = itemRepository.findBySlotIdAndStatus(slot.getId(), "APPROVED");

        assertEquals(1, approved.size());
        assertEquals("APPROVED", approved.get(0).getStatus());
    }

    @Test
    @DisplayName("existsBySlotIdAndVariantId returns true when exists")
    void existsBySlotIdAndVariantId_True() {
        FlashSaleItem item = persistItem("UPCOMING", "APPROVED", 10, 10);
        assertTrue(itemRepository.existsBySlotIdAndVariantId(item.getSlot().getId(), item.getVariant().getId()));
    }

    @Test
    @DisplayName("deductAvailableStockConditionally decrements when enough")
    void deduct_Success() {
        FlashSaleItem item = persistItem("ACTIVE", "APPROVED", 10, 10);

        int updated = itemRepository.deductAvailableStockConditionally(item.getId(), 3);

        assertEquals(1, updated);
        em.clear();
        FlashSaleItem reloaded = itemRepository.findById(item.getId()).orElseThrow();
        assertEquals(7, reloaded.getAvailableStock());
    }

    @Test
    @DisplayName("deductAvailableStockConditionally fails when not enough")
    void deduct_Fail_WhenInsufficient() {
        FlashSaleItem item = persistItem("ACTIVE", "APPROVED", 10, 2);

        int updated = itemRepository.deductAvailableStockConditionally(item.getId(), 5);

        assertEquals(0, updated);
    }

    @Test
    @DisplayName("replenishAvailableStockConditionally increments stock")
    void replenish_Success() {
        FlashSaleItem item = persistItem("ACTIVE", "APPROVED", 10, 5);

        int updated = itemRepository.replenishAvailableStockConditionally(item.getId(), 3);

        assertEquals(1, updated);
        em.clear();
        FlashSaleItem reloaded = itemRepository.findById(item.getId()).orElseThrow();
        assertEquals(8, reloaded.getAvailableStock());
    }

    @Test
    @DisplayName("replenishAvailableStockConditionally rejects when exceeds allocatedStock")
    void replenish_Fail_WhenExceedsAllocated() {
        FlashSaleItem item = persistItem("ACTIVE", "APPROVED", 10, 9);

        int updated = itemRepository.replenishAvailableStockConditionally(item.getId(), 5);

        assertEquals(0, updated);
    }

    @Test
    @DisplayName("isVariantInActiveFlashSale true when variant in ACTIVE slot")
    void isVariantInActiveFlashSale_True() {
        FlashSaleItem item = persistItem("ACTIVE", "APPROVED", 10, 10);
        assertTrue(itemRepository.isVariantInActiveFlashSale(item.getVariant().getId()));
    }

    @Test
    @DisplayName("isVariantInActiveFlashSale false when slot UPCOMING")
    void isVariantInActiveFlashSale_False_WhenSlotNotActive() {
        FlashSaleItem item = persistItem("UPCOMING", "APPROVED", 10, 10);
        assertFalse(itemRepository.isVariantInActiveFlashSale(item.getVariant().getId()));
    }
}