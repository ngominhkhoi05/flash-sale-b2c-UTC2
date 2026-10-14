package com.b2c.flash_sale_b2c_UTC2.flashsale.integration;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.CancelReservationResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.CreateReservationRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.ReservationResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.exception.FlashSaleErrorCode;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleReservationService;
import com.b2c.flash_sale_b2c_UTC2.flashsale.strategy.StockReservationStrategy;
import org.junit.jupiter.api.Order;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderItemRepository;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
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
import org.springframework.test.context.ActiveProfiles;

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
public class FlashSaleReservationRealInfrastructureIT {

    @Autowired
    private FlashSaleReservationService reservationService;

    @Autowired
    private FlashSaleSlotRepository slotRepository;

    @Autowired
    private FlashSaleItemRepository itemRepository;

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
    private AddressRepository addressRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private StockReservationStrategy stockReservationStrategy;

    private User seller;
    private Store store;
    private Category category;
    private Product product;
    private ProductVariant variant;
    private FlashSaleSlot activeSlot;
    private final List<User> buyers = new ArrayList<>();
    private final List<Address> addresses = new ArrayList<>();

    @BeforeAll
    void setupGlobalData() {
        // Xóa data cũ
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        slotRepository.deleteAllInBatch();

        // Xóa toàn bộ keys Redis cũ liên quan đến flash sale
        Set<String> oldKeys = redisTemplate.keys("flash_sale:*");
        if (oldKeys != null && !oldKeys.isEmpty()) {
            redisTemplate.delete(oldKeys);
        }

        seller = userRepository.findByEmail("seller_reserve_it@utc2.edu.vn").orElseGet(() ->
                userRepository.save(User.builder()
                        .email("seller_reserve_it@utc2.edu.vn")
                        .fullName("Seller Reserve IT")
                        .status("ACTIVE")
                        .createdAt(Instant.now())
                        .build())
        );

        store = storeRepository.findByUserId(seller.getId()).orElseGet(() ->
                storeRepository.save(Store.builder()
                        .user(seller)
                        .storeName("Store Reserve IT")
                        .status("APPROVED")
                        .defaultCommissionRate(new BigDecimal("0.0500"))
                        .createdAt(Instant.now())
                        .build())
        );

        category = categoryRepository.findByName("IT Category").orElseGet(() ->
                categoryRepository.save(Category.builder()
                        .name("IT Category")
                        .slug("it-cat-" + UUID.randomUUID())
                        .commissionRate(new BigDecimal("0.0500"))
                        .build())
        );

        product = productRepository.save(Product.builder()
                .store(store)
                .category(category)
                .name("Tai nghe Test Reserve IT")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build());

        variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("SKU-RES-" + System.currentTimeMillis())
                .variantName("Bản Đen")
                .originalPrice(new BigDecimal("200000.00"))
                .stockQuantity(1000)
                .status("ACTIVE")
                .version(0L)
                .createdAt(Instant.now())
                .build());

        // Chuẩn bị 100 buyers kèm địa chỉ
        buyers.clear();
        addresses.clear();
        for (int i = 1; i <= 100; i++) {
            final int buyerNum = i;
            String email = "buyer_res_" + buyerNum + "@utc2.edu.vn";
            User b = userRepository.findByEmail(email).orElseGet(() ->
                    userRepository.save(User.builder()
                            .email(email)
                            .fullName("Buyer Reserve " + buyerNum)
                            .status("ACTIVE")
                            .createdAt(Instant.now())
                            .build())
            );
            buyers.add(b);

            List<Address> userAddrs = addressRepository.findByUserId(b.getId());
            Address a;
            if (userAddrs == null || userAddrs.isEmpty()) {
                a = addressRepository.save(Address.builder()
                        .user(b)
                        .contactName(b.getFullName())
                        .phone("0901234567")
                        .province("TP.HCM")
                        .district("Thủ Đức")
                        .ward("Hiệp Phú")
                        .detailAddress("123 Lê Văn Việt")
                        .isDefault(true)
                        .createdAt(Instant.now())
                        .build());
            } else {
                a = userAddrs.get(0);
            }
            addresses.add(a);
        }

        // Tạo Slot ACTIVE
        activeSlot = slotRepository.save(FlashSaleSlot.builder()
                .title("Flash Sale Reserve Active Slot")
                .startTime(Instant.now().minusSeconds(120))
                .endTime(Instant.now().plusSeconds(3600))
                .reservationTtlSeconds(300)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build());
    }

    private FlashSaleItem createFlashSaleItem(int allocatedStock, int purchaseLimit) {
        ProductVariant var = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("SKU-RES-" + UUID.randomUUID())
                .variantName("Bản " + UUID.randomUUID().toString().substring(0, 5))
                .originalPrice(new BigDecimal("200000.00"))
                .stockQuantity(1000)
                .status("ACTIVE")
                .version(0L)
                .createdAt(Instant.now())
                .build());

        FlashSaleItem item = itemRepository.save(FlashSaleItem.builder()
                .slot(activeSlot)
                .variant(var)
                .flashSalePrice(new BigDecimal("99000.00"))
                .allocatedStock(allocatedStock)
                .availableStock(allocatedStock)
                .userPurchaseLimit(purchaseLimit)
                .status("APPROVED")
                .createdAt(Instant.now())
                .build());

        // Pre-warm Redis
        redisTemplate.opsForValue().set("flash_sale:stock:" + item.getId(), String.valueOf(allocatedStock));
        return item;
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: 100 luồng đồng thời tranh mua 10 sản phẩm -> Đúng 10 thành công, 90 hết hàng, tồn kho bằng 0")
    void test1_Concurrency100Threads_10Stock_ExactLimit() throws InterruptedException {
        int initialStock = 10;
        FlashSaleItem item = createFlashSaleItem(initialStock, 1);

        int totalThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(25);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);
        AtomicInteger otherFailCount = new AtomicInteger(0);

        String runId = UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < totalThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    User b = buyers.get(index);
                    CreateReservationRequest req = CreateReservationRequest.builder()
                            .flashSaleItemId(item.getId())
                            .quantity(1)
                            .build();

                    reservationService.createReservation(b.getId(), "idem-concurrency-" + runId + "-" + b.getId(), req);
                    successCount.incrementAndGet();
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
        assertTrue(doneLatch.await(60, TimeUnit.SECONDS), "Timeout 100 threads");
        executor.shutdown();

        // Assert số lượng kết quả
        assertEquals(10, successCount.get(), "Chính xác 10 request giữ chỗ thành công");
        assertEquals(90, outOfStockCount.get(), "Chính xác 90 request nhận OUT_OF_STOCK");
        assertEquals(0, otherFailCount.get(), "Không có lỗi bất thường khác");

        // Assert tồn kho Redis
        String redisStockStr = redisTemplate.opsForValue().get("flash_sale:stock:" + item.getId());
        assertNotNull(redisStockStr);
        assertEquals(0, Integer.parseInt(redisStockStr), "Tồn kho trên Redis phải chính xác bằng 0");

        // Assert tồn kho DB
        FlashSaleItem dbItem = itemRepository.findById(item.getId()).orElseThrow();
        assertEquals(0, dbItem.getAvailableStock(), "Tồn kho available_stock trong DB phải bằng 0");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Cùng 1 user gửi 5 request song song (giới hạn mua = 1) -> Đúng 1 thành công, 4 PURCHASE_LIMIT_EXCEEDED")
    void test2_SameUserMultipleParallelRequests_LimitExceeded() throws InterruptedException {
        FlashSaleItem item = createFlashSaleItem(50, 1);
        User buyer = buyers.get(0);

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger limitExceededCount = new AtomicInteger(0);

        String test2Idem = "idem-limit-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    CreateReservationRequest req = CreateReservationRequest.builder()
                            .flashSaleItemId(item.getId())
                            .quantity(1)
                            .build();

                    reservationService.createReservation(buyer.getId(), test2Idem + idx, req);
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
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successCount.get(), "Chỉ đúng 1 request thành công");
        assertEquals(4, limitExceededCount.get(), "4 request còn lại phải nhận PURCHASE_LIMIT_EXCEEDED");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Gửi lại cùng Idempotency-Key -> Trả về kết quả cũ, không tạo thêm đơn và không trừ thêm kho")
    void test3_IdempotencyReplay() {
        FlashSaleItem item = createFlashSaleItem(20, 2);
        User buyer = buyers.get(1);

        CreateReservationRequest req = CreateReservationRequest.builder()
                .flashSaleItemId(item.getId())
                .quantity(1)
                .build();

        String idempotencyKey = "idem-replay-key-" + UUID.randomUUID();
        ReservationResponse firstRes = reservationService.createReservation(buyer.getId(), idempotencyKey, req);
        assertNotNull(firstRes);
        assertNotNull(firstRes.getOrderCode());

        // Gửi lại lần 2 cùng idempotency key
        ReservationResponse secondRes = reservationService.createReservation(buyer.getId(), idempotencyKey, req);
        assertNotNull(secondRes);
        assertEquals(firstRes.getOrderCode(), secondRes.getOrderCode(), "OrderCode phải giống nhau (Idempotent replay)");

        // Tồn kho chỉ bị trừ đúng 1 lần
        String redisStock = redisTemplate.opsForValue().get("flash_sale:stock:" + item.getId());
        assertEquals("19", redisStock);
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Hủy giữ chỗ 2 lần -> Lần 1 thành công hoàn kho, lần 2 an toàn không hoàn kho lần 2")
    void test4_CancelReservationTwice_RestoresStockOnce() {
        FlashSaleItem item = createFlashSaleItem(20, 2);
        User buyer = buyers.get(2);

        CreateReservationRequest req = CreateReservationRequest.builder()
                .flashSaleItemId(item.getId())
                .quantity(1)
                .build();

        String cancelIdemKey = "idem-cancel-test-" + UUID.randomUUID();
        ReservationResponse res = reservationService.createReservation(buyer.getId(), cancelIdemKey, req);
        assertNotNull(res);

        // Ban đầu trừ 1 còn 19
        assertEquals("19", redisTemplate.opsForValue().get("flash_sale:stock:" + item.getId()));

        // Hủy lần 1
        CancelReservationResponse cancel1 = reservationService.cancelReservation(buyer.getId(), res.getOrderCode());
        assertTrue(cancel1.isSuccess());
        assertEquals("20", redisTemplate.opsForValue().get("flash_sale:stock:" + item.getId()), "Kho đã được hoàn về 20");

        // Hủy lần 2
        CancelReservationResponse cancel2 = reservationService.cancelReservation(buyer.getId(), res.getOrderCode());
        assertTrue(cancel2.isSuccess(), "Hủy lần 2 vẫn trả về thành công an toàn (idempotent)");
        assertEquals("20", redisTemplate.opsForValue().get("flash_sale:stock:" + item.getId()), "Kho KHÔNG bị tăng khống lên 21");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Giả lập sập giữa 2 bước -> Job đối soát tự hoàn kho và quota sau grace period")
    void test5_CrashSimulation_ReconciliationRestoresStockAndQuota() {
        FlashSaleItem item = createFlashSaleItem(30, 2);
        User buyer = buyers.get(3);

        String crashedOrderCode = "FS-CRASH-" + System.currentTimeMillis();

        // Bước 1: Trừ Redis thành công qua Lua script (tạo bản ghi pending_res và index)
        long result = stockReservationStrategy.reserveStock(
                activeSlot.getId(),
                buyer.getId(),
                item.getId(),
                2,
                item.getUserPurchaseLimit(),
                3600,
                crashedOrderCode
        );
        assertEquals(1, result, "Trừ Redis bước 1 thành công");

        // Kho Redis bị trừ 2 còn 28, user limit tăng 2
        assertEquals("28", redisTemplate.opsForValue().get("flash_sale:stock:" + item.getId()));
        String userLimitKey = "flash_sale:user_limit:" + activeSlot.getId() + ":" + buyer.getId() + ":" + item.getId();
        assertEquals("2", redisTemplate.opsForValue().get(userLimitKey));

        // Giả lập sập tiến trình: KHÔNG gọi ghi DB!
        // Bản ghi flash_sale:pending_res:{crashedOrderCode} tồn tại trong Redis.
        // Để kiểm tra job đối soát, ta chỉnh score trong ZSET về quá khứ (cách đây 20s > 15s grace period)
        long expiredScore = Instant.now().getEpochSecond() - 20;
        redisTemplate.opsForZSet().add("flash_sale:pending_res_index", crashedOrderCode, expiredScore);

        // Chạy job đối soát
        reservationService.reconcilePendingReservations();

        // Kiểm tra sau khi đối soát: tồn kho Redis được hoàn lại 30, user limit được trừ về null/0
        assertEquals("30", redisTemplate.opsForValue().get("flash_sale:stock:" + item.getId()), "Tồn kho đã được đối soát tự động hoàn lại 30");
        assertNull(redisTemplate.opsForValue().get(userLimitKey), "User limit đã được xóa/trả lại");
        assertNull(redisTemplate.opsForValue().get("flash_sale:pending_res:" + crashedOrderCode), "Pending key đã được dọn dẹp");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Giả lập DB bị lỗi/timeout -> Tồn kho Redis được hoàn đúng một lần, không có đơn rác trong DB")
    void test6_DbFailure_RollbackStockImmediately() {
        FlashSaleItem item = createFlashSaleItem(15, 1);
        User buyer = buyers.get(4);

        // Cố tình đặt địa chỉ không tồn tại hoặc lỗi để DB throw exception
        CreateReservationRequest req = CreateReservationRequest.builder()
                .flashSaleItemId(item.getId())
                .addressId(999999L) // Địa chỉ không tồn tại
                .quantity(1)
                .build();

        String dbErrIdem = "idem-db-err-" + UUID.randomUUID();
        assertThrows(BusinessException.class, () ->
                reservationService.createReservation(buyer.getId(), dbErrIdem, req)
        );

        // Tồn kho Redis phải giữ nguyên là 15 (không bị mất kho)
        assertEquals("15", redisTemplate.opsForValue().get("flash_sale:stock:" + item.getId()));
    }
}
