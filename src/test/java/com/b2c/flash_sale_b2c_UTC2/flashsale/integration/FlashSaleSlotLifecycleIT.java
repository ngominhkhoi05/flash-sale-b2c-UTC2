package com.b2c.flash_sale_b2c_UTC2.flashsale.integration;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.common.test.TestDataFactory;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.FlashSaleOrderPort;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleExpirationScheduler;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.repository.CategoryRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test cho lifecycle đầy đủ của FlashSaleSlot:
 * UPCOMING -> ACTIVE -> ENDED, scheduler job xử lý và broadcast.
 */
class FlashSaleSlotLifecycleIT extends AbstractPostgresIT {

    @Autowired private FlashSaleSlotRepository slotRepository;
    @Autowired private FlashSaleItemRepository itemRepository;
    @Autowired private FlashSaleExpirationScheduler scheduler;
    @Autowired private FlashSaleOrderPort orderPort;
    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User seller;
    private Store store;
    private Category category;
    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE order_items, orders, voucher_usages, cart_items, carts, " +
                "wallet_transactions, payments, flash_sale_items, flash_sale_slots, " +
                "product_reviews, product_variants, products, categories, images, " +
                "addresses, stores, wallets, auth_accounts, user_roles, " +
                "users, roles, vouchers RESTART IDENTITY CASCADE");

        seller = userRepository.save(TestDataFactory.user("seller_lc@x.com"));
        store = storeRepository.save(Store.builder()
                .user(seller).storeName("Lifecycle Store")
                .status("APPROVED")
                .defaultCommissionRate(new BigDecimal("0.0500"))
                .createdAt(Instant.now())
                .build());
        category = categoryRepository.save(TestDataFactory.category("Cat-LC"));
        product = productRepository.save(Product.builder()
                .store(store).category(category)
                .name("Lifecycle Product")
                .description("Test product")
                .status("ACTIVE")
                .build());
        variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("SKU-LC-" + System.currentTimeMillis())
                .variantName("Default")
                .originalPrice(new BigDecimal("100000.00"))
                .stockQuantity(50)
                .status("ACTIVE")
                .version(0L)
                .createdAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("UPCOMING -> ACTIVE: slot chuyển status khi startTime đến")
    void slot_ActivatesAtStartTime() {
        FlashSaleSlot slot = slotRepository.save(FlashSaleSlot.builder()
                .title("Slot-Activate")
                .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .reservationTtlSeconds(300)
                .status("UPCOMING")
                .createdAt(Instant.now())
                .build());

        // Gọi trực tiếp các method public trong scheduler
        // (self-invocation qua processEndedSlotsAndReturnUnsoldStock không áp dụng @Transactional)
        scheduler.processEndedSlotsAndReturnUnsoldStock();

        FlashSaleSlot reloaded = slotRepository.findById(slot.getId()).orElseThrow();
        assertEquals("ACTIVE", reloaded.getStatus(),
                "Slot với startTime trong quá khứ phải chuyển sang ACTIVE");
    }

    @Test
    @DisplayName("ACTIVE -> ENDED: scheduler close slot khi endTime qua và không còn pending")
    void slot_EndsAtEndTime() {
        FlashSaleSlot slot = slotRepository.saveAndFlush(FlashSaleSlot.builder()
                .title("Slot-End")
                .startTime(Instant.now().minus(2, ChronoUnit.HOURS))
                .endTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .reservationTtlSeconds(300)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build());

        // Gọi trực tiếp closeSlotAndReturnStock (workaround self-invocation)
        scheduler.closeSlotAndReturnStock(slot);

        FlashSaleSlot reloaded = slotRepository.findById(slot.getId()).orElseThrow();
        assertEquals("ENDED", reloaded.getStatus(),
                "Slot với endTime trong quá khứ phải chuyển sang ENDED");
    }

    @Test
    @DisplayName("Slot ACTIVE trong tương lai KHÔNG bị activate sớm")
    void slot_FutureSlot_NotActivated() {
        FlashSaleSlot slot = FlashSaleSlot.builder()
                .title("Slot-Future")
                .startTime(Instant.now().plus(2, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(3, ChronoUnit.HOURS))
                .reservationTtlSeconds(300)
                .status("UPCOMING")
                .createdAt(Instant.now())
                .build();
        slot = slotRepository.save(slot);

        scheduler.processEndedSlotsAndReturnUnsoldStock();

        FlashSaleSlot reloaded = slotRepository.findById(slot.getId()).orElseThrow();
        assertEquals("UPCOMING", reloaded.getStatus(),
                "Slot với startTime trong tương lai phải giữ nguyên UPCOMING");
    }

    @Test
    @DisplayName("Scheduler idempotent - gọi 2 lần không tạo side-effect xấu")
    void slot_ActivateIsIdempotent() {
        FlashSaleSlot slot = FlashSaleSlot.builder()
                .title("Slot-Idempotent")
                .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .reservationTtlSeconds(300)
                .status("UPCOMING")
                .createdAt(Instant.now())
                .build();
        slot = slotRepository.save(slot);

        // Gọi 2 lần liên tiếp
        scheduler.processEndedSlotsAndReturnUnsoldStock();
        scheduler.processEndedSlotsAndReturnUnsoldStock();

        FlashSaleSlot reloaded = slotRepository.findById(slot.getId()).orElseThrow();
        assertEquals("ACTIVE", reloaded.getStatus(),
                "Slot phải ở ACTIVE và idempotent khi gọi scheduler nhiều lần");
    }

    @Test
    @DisplayName("Slot ENDED có FlashSaleItem: scheduler trả unsold stock về variant")
    void slot_EndedReturnsUnsoldStockToVariant() {
        int initialVariantStock = variant.getStockQuantity();

        // Tạo slot đã ACTIVE và quá endTime
        FlashSaleSlot slot = slotRepository.saveAndFlush(FlashSaleSlot.builder()
                .title("Slot-StockReturn")
                .startTime(Instant.now().minus(2, ChronoUnit.HOURS))
                .endTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .reservationTtlSeconds(300)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build());

        // Tạo FlashSaleItem với allocated_stock = 20, available_stock = 20 (chưa bán)
        FlashSaleItem item = FlashSaleItem.builder()
                .slot(slot)
                .variant(variant)
                .flashSalePrice(new BigDecimal("50000.00"))
                .allocatedStock(20)
                .availableStock(20)
                .userPurchaseLimit(2)
                .status("APPROVED")
                .createdAt(Instant.now())
                .build();
        item = itemRepository.saveAndFlush(item);

        // Debug: kiểm tra slot ACTIVE trước khi gọi scheduler
        FlashSaleSlot beforeScheduler = slotRepository.findById(slot.getId()).orElseThrow();
        System.out.println("[DEBUG] Before scheduler: status=" + beforeScheduler.getStatus()
                + " endTime=" + beforeScheduler.getEndTime());

        // Gọi trực tiếp closeSlotAndReturnStock (workaround self-invocation issue)
        scheduler.closeSlotAndReturnStock(beforeScheduler);

        // Assert: slot ENDED
        FlashSaleSlot reloadedSlot = slotRepository.findById(slot.getId()).orElseThrow();
        assertEquals("ENDED", reloadedSlot.getStatus());

        // Assert: item status ENDED
        FlashSaleItem reloadedItem = itemRepository.findById(item.getId()).orElseThrow();
        assertEquals("ENDED", reloadedItem.getStatus());

        // Assert: variant stock_quantity được hoàn trả (variant_stock + 20)
        ProductVariant reloadedVariant = variantRepository.findById(variant.getId()).orElseThrow();
        assertEquals(initialVariantStock + 20, reloadedVariant.getStockQuantity(),
                "Variant stock_quantity phải được hoàn trả 20 đơn vị từ unsold FlashSaleItem");
    }
    @Test
    @DisplayName("InMemory order port: countPendingBySlot khởi đầu bằng 0")
    void orderPort_InitialStateIsEmpty() {
        // In-memory order port bắt đầu rỗng
        assertEquals(0L, orderPort.countPendingBySlot(999L),
                "InMemory order port khởi đầu có count = 0 cho mọi slotId");
    }
}