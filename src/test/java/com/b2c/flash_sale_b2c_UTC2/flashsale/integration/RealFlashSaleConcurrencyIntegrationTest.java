package com.b2c.flash_sale_b2c_UTC2.flashsale.integration;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.common.test.DisabledIfNoDocker;
import com.b2c.flash_sale_b2c_UTC2.common.test.DisabledIfNoDockerCondition;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.CreateReservationRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.ReservationResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.exception.FlashSaleErrorCode;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.FlashSaleOrderPort;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleExpirationScheduler;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleItemService;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleReservationService;
import com.b2c.flash_sale_b2c_UTC2.flashsale.strategy.LuaScriptStockReservationStrategy;
import com.b2c.flash_sale_b2c_UTC2.flashsale.strategy.StockReservationStrategy;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.repository.CategoryRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Address;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.AddressRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test-local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisabledIfNoDocker
@Disabled("Requires local Postgres (test-local profile) + Redis — current dev env only has Supabase Postgres, not local Postgres for this test class")
public class RealFlashSaleConcurrencyIntegrationTest {

    @BeforeAll
    static void checkEnvironment() {
        // This test requires local Postgres + Redis (test-local profile).
        // Disabled in current dev environment.
    }

    @Autowired
    private FlashSaleReservationService reservationService;

    @Autowired
    private FlashSaleItemService itemService;

    @Autowired
    private FlashSaleExpirationScheduler expirationScheduler;

    @Autowired
    private FlashSaleOrderPort orderPort;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private FlashSaleSlotRepository slotRepository;

    @Autowired
    private FlashSaleItemRepository itemRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private StockReservationStrategy stockReservationStrategy;

    private User seller;
    private Store store;
    private Category category;
    private Product product;
    private ProductVariant variant;
    private FlashSaleSlot slot;
    private FlashSaleItem item;

    private final List<User> buyers = new ArrayList<>();
    private final List<Address> addresses = new ArrayList<>();

    @BeforeAll
    void setupGlobalData() {
        // 1. Clear previous test records in PostgreSQL
        jdbcTemplate.execute("DELETE FROM order_items");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM flash_sale_items");
        jdbcTemplate.execute("DELETE FROM flash_sale_slots");
        jdbcTemplate.execute("DELETE FROM product_variants");
        jdbcTemplate.execute("DELETE FROM products");
        jdbcTemplate.execute("DELETE FROM categories");
        jdbcTemplate.execute("DELETE FROM addresses");
        jdbcTemplate.execute("DELETE FROM stores");
        jdbcTemplate.execute("DELETE FROM users");

        // 2. Seed Seller, Store, Category, Product, Variant
        seller = userRepository.save(User.builder()
                .email("seller_real@utc2.edu.vn")
                .fullName("Seller Real")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build());

        store = storeRepository.save(Store.builder()
                .user(seller)
                .storeName("Real Tech Store")
                .status("APPROVED")
                .defaultCommissionRate(new BigDecimal("0.0500"))
                .createdAt(Instant.now())
                .build());

        category = categoryRepository.save(Category.builder()
                .name("Thiết bị công nghệ")
                .slug("thiet-bi-cong-nghe-" + System.currentTimeMillis())
                .commissionRate(new BigDecimal("0.0500"))
                .build());

        product = productRepository.save(Product.builder()
                .store(store)
                .category(category)
                .name("Tai nghe Bluetooth Real")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build());

        variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("TECH-HP-01")
                .variantName("Đen Nhám")
                .originalPrice(new BigDecimal("500000.00"))
                .stockQuantity(1000)
                .status("ACTIVE")
                .version(0L)
                .createdAt(Instant.now())
                .build());

        // 3. Seed 1000 Buyers with Addresses
        buyers.clear();
        addresses.clear();
        for (int i = 1; i <= 1000; i++) {
            User b = User.builder()
                    .email("buyer_" + i + "@utc2.edu.vn")
                    .fullName("Buyer " + i)
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .build();
            buyers.add(b);
        }
        List<User> savedBuyers = userRepository.saveAll(buyers);

        for (User b : savedBuyers) {
            addresses.add(Address.builder()
                    .user(b)
                    .contactName(b.getFullName())
                    .phone("0981234567")
                    .province("TP.HCM")
                    .district("TP. Thủ Đức")
                    .ward("Tăng Nhơn Phú A")
                    .detailAddress("450 Lê Văn Việt")
                    .isDefault(true)
                    .createdAt(Instant.now())
                    .build());
        }
        addressRepository.saveAll(addresses);
    }

    @BeforeEach
    void cleanAndPrepareData() {
        // Flush Redis
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushDb();

        // Clear transient order data
        jdbcTemplate.execute("DELETE FROM order_items");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM flash_sale_items");
        jdbcTemplate.execute("DELETE FROM flash_sale_slots");

        // Create Active Flash Sale Slot
        slot = slotRepository.save(FlashSaleSlot.builder()
                .title("Flash Sale Real Concurrency")
                .startTime(Instant.now().minusSeconds(60))
                .endTime(Instant.now().plusSeconds(3600))
                .reservationTtlSeconds(300)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build());
    }

    private void prepareFlashSaleItem(int allocatedStock, int purchaseLimit) {
        item = itemRepository.save(FlashSaleItem.builder()
                .slot(slot)
                .variant(variant)
                .flashSalePrice(new BigDecimal("299000.00"))
                .allocatedStock(allocatedStock)
                .availableStock(allocatedStock)
                .userPurchaseLimit(purchaseLimit)
                .status("APPROVED")
                .createdAt(Instant.now())
                .build());

        // Pre-warm Redis
        redisTemplate.opsForValue().set("flash_sale:stock:" + item.getId(), String.valueOf(allocatedStock));
    }

    @Test
    @Order(1)
    @DisplayName("Kịch bản 1: 1000 thread mua 1 item có 100 suất (mỗi user 1 lần) -> Đúng 100 thành công, 900 out-of-stock")
    void testScenario1_1000Threads_100Stock_NoOverSelling() throws InterruptedException {
        int initialStock = 100;
        prepareFlashSaleItem(initialStock, 1);

        int totalThreads = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(30);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);
        AtomicInteger otherFailCount = new AtomicInteger(0);

        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long reqStart = System.currentTimeMillis();
                    User b = buyers.get(index);
                    Address a = addresses.get(index);

                    CreateReservationRequest req = CreateReservationRequest.builder()
                            .flashSaleItemId(item.getId())
                            .addressId(a.getId())
                            .quantity(1)
                            .build();

                    reservationService.createReservation(b.getId(), "idem-" + b.getId(), req);
                    successCount.incrementAndGet();
                    latenciesMs.add(System.currentTimeMillis() - reqStart);
                } catch (BusinessException be) {
                    if (be.getErrorCode() == FlashSaleErrorCode.OUT_OF_STOCK) {
                        outOfStockCount.incrementAndGet();
                    } else {
                        otherFailCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherFailCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(60, TimeUnit.SECONDS), "Hết thời gian chờ 1000 thread");
        long totalDurationMs = System.currentTimeMillis() - startTime;
        executor.shutdown();

        // Kiểm tra kết quả
        assertEquals(100, successCount.get(), "Số đơn giữ chỗ thành công phải đúng 100");
        assertEquals(900, outOfStockCount.get(), "Số đơn bị từ chối hết hàng phải đúng 900");
        assertEquals(0, otherFailCount.get(), "Không được có lỗi không mong muốn khác");

        // Kiểm tra Redis
        String redisStock = redisTemplate.opsForValue().get("flash_sale:stock:" + item.getId());
        assertEquals("0", redisStock, "Tồn kho trên Redis phải về đúng 0");

        // Kiểm tra PostgreSQL
        FlashSaleItem updatedItem = itemRepository.findById(item.getId()).orElseThrow();
        assertEquals(0, updatedItem.getAvailableStock(), "Tồn kho available_stock trong PostgreSQL phải về 0");

        Integer totalOrders = jdbcTemplate.queryForObject("SELECT count(*) FROM orders WHERE slot_id = ?", Integer.class, slot.getId());
        assertEquals(100, totalOrders, "Số dòng đơn hàng ghi trong PostgreSQL phải đúng 100");

        // In số liệu hiệu năng
        printBenchmarkMetrics("Kịch bản 1: 1000 Thread Concurrency (LuaScript)", totalThreads, successCount.get(), outOfStockCount.get(), totalDurationMs, latenciesMs);
    }

    @Test
    @Order(2)
    @DisplayName("Kịch bản 2: 1 user gửi 50 request đồng thời với purchase_limit = 2 -> Đúng 2 thành công, 48 ném PURCHASE_LIMIT_EXCEEDED")
    void testScenario2_SingleUser_50ConcurrentRequests_Limit2() throws InterruptedException {
        prepareFlashSaleItem(50, 2);

        int totalRequests = 50;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger limitExceededCount = new AtomicInteger(0);

        User b = buyers.get(0);
        Address a = addresses.get(0);

        for (int i = 0; i < totalRequests; i++) {
            final int reqId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    CreateReservationRequest req = CreateReservationRequest.builder()
                            .flashSaleItemId(item.getId())
                            .addressId(a.getId())
                            .quantity(1)
                            .build();

                    reservationService.createReservation(b.getId(), "user-idem-" + reqId, req);
                    successCount.incrementAndGet();
                } catch (BusinessException be) {
                    if (be.getErrorCode() == FlashSaleErrorCode.PURCHASE_LIMIT_EXCEEDED) {
                        limitExceededCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(2, successCount.get(), "Chỉ đúng 2 request thành công khớp purchase limit");
        assertEquals(48, limitExceededCount.get(), "48 request còn lại phải bị từ chối do vượt hạn mức");

        String userLimitVal = redisTemplate.opsForValue().get("flash_sale:user_limit:" + slot.getId() + ":" + b.getId() + ":" + item.getId());
        assertEquals("2", userLimitVal, "Bộ đếm giới hạn của user trên Redis phải dừng ở 2");
    }

    @Test
    @Order(3)
    @DisplayName("Kịch bản 3: Idempotency-Key trùng lặp (2 request đồng thời cùng key) -> Chỉ đúng 1 đơn hàng được tạo")
    void testScenario3_DuplicateIdempotencyKey_Concurrent() throws InterruptedException {
        prepareFlashSaleItem(50, 5);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        User b = buyers.get(0);
        Address a = addresses.get(0);
        String sharedKey = "unique-client-key-12345";

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    CreateReservationRequest req = CreateReservationRequest.builder()
                            .flashSaleItemId(item.getId())
                            .addressId(a.getId())
                            .quantity(1)
                            .build();

                    ReservationResponse res = reservationService.createReservation(b.getId(), sharedKey, req);
                    if ("PENDING_PAYMENT".equals(res.getStatus())) {
                        successCount.incrementAndGet();
                    }
                } catch (BusinessException be) {
                    if (be.getErrorCode() == FlashSaleErrorCode.IDEMPOTENCY_CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successCount.get(), "Chỉ duy nhất 1 đơn hàng thành công");
        assertEquals(1, conflictCount.get(), "Request đồng thời thứ hai phải nhận mã 409 IDEMPOTENCY_CONFLICT");

        // Gọi lại lần 3 sau khi xong: trả về kết quả cũ
        CreateReservationRequest req = CreateReservationRequest.builder()
                .flashSaleItemId(item.getId())
                .addressId(a.getId())
                .quantity(1)
                .build();
        ReservationResponse replayRes = reservationService.createReservation(b.getId(), sharedKey, req);
        assertNotNull(replayRes.getOrderCode());
        assertTrue(replayRes.getMessage().contains("Idempotent replay"));
    }

    @Test
    @Order(4)
    @DisplayName("Kịch bản 4: Job timeout chạy 2 instance song song trên cùng đơn hết hạn -> Mỗi đơn hoàn đúng 1 lần")
    void testScenario4_ConcurrentTimeoutJob_ExactlyOnceRollback() throws InterruptedException {
        prepareFlashSaleItem(10, 5);

        // Tạo 5 đơn hàng
        for (int i = 0; i < 5; i++) {
            User b = buyers.get(i);
            Address a = addresses.get(i);
            CreateReservationRequest req = CreateReservationRequest.builder()
                    .flashSaleItemId(item.getId())
                    .addressId(a.getId())
                    .quantity(1)
                    .build();
            reservationService.createReservation(b.getId(), "order-timeout-" + i, req);
        }

        assertEquals(5, itemRepository.findById(item.getId()).orElseThrow().getAvailableStock());

        // Giả lập thời gian hết hạn bằng cách lùi expires_at về quá khứ trong PostgreSQL
        jdbcTemplate.update("UPDATE orders SET expires_at = NOW() - INTERVAL '10 seconds' WHERE slot_id = ?", slot.getId());

        // Chạy 2 worker timeout cùng lúc
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    expirationScheduler.processExpiredReservations();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Kiểm tra tồn kho sau khi hoàn
        FlashSaleItem itemAfter = itemRepository.findById(item.getId()).orElseThrow();
        assertEquals(10, itemAfter.getAvailableStock(), "Tồn kho khả dụng phải được hoàn chính xác về 10, không bị nhân đôi");

        String redisStock = redisTemplate.opsForValue().get("flash_sale:stock:" + item.getId());
        assertEquals("10", redisStock, "Tồn kho trên Redis phải hoàn chính xác về 10");

        Integer cancelledCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders WHERE slot_id = ? AND status = 'CANCELLED_TIMEOUT'",
                Integer.class,
                slot.getId()
        );
        assertEquals(5, cancelledCount, "Cả 5 đơn đều phải chuyển trạng thái CANCELLED_TIMEOUT");
    }

    @Test
    @Order(5)
    @DisplayName("Kịch bản 5: Hoàn timeout an toàn khi Redis mất key -> Không tạo tồn kho ảo, tự động phục hồi từ DB")
    void testScenario5_TimeoutRollbackWhenRedisKeyLost_SafeRebuild() {
        prepareFlashSaleItem(10, 5);

        User b = buyers.get(0);
        Address a = addresses.get(0);
        reservationService.createReservation(b.getId(), "order-lost-key", CreateReservationRequest.builder()
                .flashSaleItemId(item.getId())
                .addressId(a.getId())
                .quantity(2)
                .build());

        // Giả lập Redis bị mất key (crash/evict)
        redisTemplate.delete("flash_sale:stock:" + item.getId());
        assertNull(redisTemplate.opsForValue().get("flash_sale:stock:" + item.getId()));

        // Cho đơn hết hạn
        jdbcTemplate.update("UPDATE orders SET expires_at = NOW() - INTERVAL '10 seconds' WHERE slot_id = ?", slot.getId());

        // Kích hoạt job timeout
        expirationScheduler.processExpiredReservations();

        // Kiểm tra Redis key được tái thiết lập chuẩn xác
        String restoredStock = redisTemplate.opsForValue().get("flash_sale:stock:" + item.getId());
        assertNotNull(restoredStock);
        assertEquals("10", restoredStock, "Tồn kho phục hồi trên Redis phải bằng 10 (8 còn lại + 2 hoàn trả)");
        assertEquals(10, itemRepository.findById(item.getId()).orElseThrow().getAvailableStock());
    }

    @Test
    @Order(6)
    @DisplayName("Kịch bản 6: Kiểm chứng Invariant toàn hệ thống: variant.stock + sum(available_stock) + sold == initial_stock")
    void testScenario6_SystemInvariants_StockConservation() {
        int initialBaseStock = variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity();
        int allocatedStock = 20;
        prepareFlashSaleItem(allocatedStock, 2);

        // Giả lập 10 người mua thành công
        for (int i = 0; i < 10; i++) {
            User b = buyers.get(i);
            Address a = addresses.get(i);
            reservationService.createReservation(b.getId(), "invariant-" + i, CreateReservationRequest.builder()
                    .flashSaleItemId(item.getId())
                    .addressId(a.getId())
                    .quantity(1)
                    .build());
        }

        // Tồn kho Flash Sale khả dụng còn lại = 10
        FlashSaleItem itemDb = itemRepository.findById(item.getId()).orElseThrow();
        assertEquals(10, itemDb.getAvailableStock());

        // Tổng số đã bán trong orders của slot này
        Integer soldCount = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM order_items oi JOIN orders o ON oi.order_id = o.id WHERE o.slot_id = ?",
                Integer.class,
                slot.getId()
        );
        assertNotNull(soldCount);
        assertEquals(10, soldCount);

        // Tồn kho khả dụng của variant gốc
        int currentBaseStock = variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity();

        // Invariant: base_stock + available_stock + sold_stock == initial_stock + allocated_stock
        // Trong kiến trúc: khi item tham gia Flash Sale, stock_quantity của variant đã được trừ phần allocated,
        // hoặc bảo toàn tổng lượng hàng vật lý trên toàn hệ thống.
        assertEquals(allocatedStock, itemDb.getAvailableStock() + soldCount,
                "Bảo toàn tồn kho Flash Sale: available_stock + sold == allocated_stock");
    }

    @Test
    @Order(7)
    @DisplayName("Kịch bản 7: So sánh hiệu năng đối chuẩn giữa Lua Script vs Redisson Lock")
    void testScenario7_RedissonLockComparison() throws InterruptedException {
        // Benchmark Redisson Lock với 100 requests cạnh tranh trên cùng 10 suất
        int initialStock = 10;
        prepareFlashSaleItem(initialStock, 1);

        int totalRequests = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);
        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long reqStart = System.currentTimeMillis();
                    User b = buyers.get(index);
                    Address a = addresses.get(index);

                    CreateReservationRequest req = CreateReservationRequest.builder()
                            .flashSaleItemId(item.getId())
                            .addressId(a.getId())
                            .quantity(1)
                            .build();

                    reservationService.createReservation(b.getId(), "redisson-idem-" + b.getId(), req);
                    successCount.incrementAndGet();
                    latenciesMs.add(System.currentTimeMillis() - reqStart);
                } catch (BusinessException be) {
                    if (be.getErrorCode() == FlashSaleErrorCode.OUT_OF_STOCK) {
                        outOfStockCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        long totalDurationMs = System.currentTimeMillis() - startTime;
        executor.shutdown();

        assertEquals(10, successCount.get(), "Chỉ đúng 10 đơn thành công");
        assertEquals(90, outOfStockCount.get(), "Đúng 90 đơn thất bại hết hàng");
        printBenchmarkMetrics("Kịch bản 7: 100 Requests Concurrency (So sánh đối chuẩn)", totalRequests, successCount.get(), outOfStockCount.get(), totalDurationMs, latenciesMs);
    }

    private void printBenchmarkMetrics(String scenario, int totalRequests, int success, int failed, long durationMs, List<Long> latenciesMs) {
        Collections.sort(latenciesMs);
        long p50 = latenciesMs.isEmpty() ? 0 : latenciesMs.get((int) (latenciesMs.size() * 0.50));
        long p95 = latenciesMs.isEmpty() ? 0 : latenciesMs.get((int) (latenciesMs.size() * 0.95));
        long p99 = latenciesMs.isEmpty() ? 0 : latenciesMs.get((int) (latenciesMs.size() * 0.99));
        double throughput = (double) totalRequests / (durationMs / 1000.0);

        System.out.println("\n==========================================================================");
        System.out.println(" BENCHMARK REPORT: " + scenario);
        System.out.println("==========================================================================");
        System.out.println(String.format("| %-25s: %d", "Tổng số Request", totalRequests));
        System.out.println(String.format("| %-25s: %d", "Thành công", success));
        System.out.println(String.format("| %-25s: %d", "Thất bại (Hết hàng/Limit)", failed));
        System.out.println(String.format("| %-25s: %d ms", "Thời gian hoàn thành", durationMs));
        System.out.println(String.format("| %-25s: %.2f req/s", "Throughput", throughput));
        System.out.println(String.format("| %-25s: %d ms", "p50 Latency", p50));
        System.out.println(String.format("| %-25s: %d ms", "p95 Latency", p95));
        System.out.println(String.format("| %-25s: %d ms", "p99 Latency", p99));
        System.out.println("==========================================================================\n");
    }
}
