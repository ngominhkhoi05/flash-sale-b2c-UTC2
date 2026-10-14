package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.mapper.FlashSaleItemMapper;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlashSaleItemServiceTest {

    @Mock private FlashSaleSlotRepository slotRepository;
    @Mock private FlashSaleItemRepository itemRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private FlashSaleItemMapper itemMapper;
    @Mock private ImageService imageService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    // Service with null redisTemplate by default (no Spring context)
    private FlashSaleItemService itemServiceWithoutRedis;
    private FlashSaleItem item;

    @BeforeEach
    void setUp() {
        Store store = Store.builder().id(10L).status("APPROVED").storeName("S").build();
        FlashSaleSlot slot = FlashSaleSlot.builder().id(500L).status("ACTIVE").title("Slot").build();
        Product product = Product.builder().id(100L).store(store).name("P").build();
        ProductVariant variant = ProductVariant.builder()
                .id(1000L)
                .product(product)
                .sku("SKU-1")
                .variantName("V1")
                .originalPrice(new BigDecimal("100000"))
                .stockQuantity(50)
                .build();
        item = FlashSaleItem.builder()
                .id(1L)
                .slot(slot)
                .variant(variant)
                .flashSalePrice(new BigDecimal("50000"))
                .allocatedStock(10)
                .availableStock(10)
                .status("PENDING_APPROVAL")
                .createdAt(Instant.now())
                .build();

        // Build service WITHOUT redisTemplate
        itemServiceWithoutRedis = new FlashSaleItemService(
                slotRepository, itemRepository, variantRepository,
                storeRepository, itemMapper, imageService);
    }

    @Test
    @DisplayName("getRealtimeStock: fallback DB khi redisTemplate null")
    void getRealtimeStock_FallbackDB_WhenRedisNull() {
        // redisTemplate is null on itemServiceWithoutRedis -> falls back to item.getAvailableStock()
        int stock = itemServiceWithoutRedis.getRealtimeStock(item);
        assertEquals(10, stock);
    }

    @Test
    @DisplayName("getRealtimeStock: fallback DB khi Redis exception")
    void getRealtimeStock_FallbackDB_WhenRedisFails() {
        FlashSaleItemService svc = new FlashSaleItemService(
                slotRepository, itemRepository, variantRepository,
                storeRepository, itemMapper, imageService) {
            // Subclass to override redisTemplate field via reflection helper
        };
        // Manually inject redisTemplate mock using reflection
        try {
            java.lang.reflect.Field f = FlashSaleItemService.class.getDeclaredField("redisTemplate");
            f.setAccessible(true);
            f.set(svc, redisTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Configure redisTemplate.opsForValue() to throw
        doThrow(new RuntimeException("Redis down")).when(redisTemplate).opsForValue();

        int stock = svc.getRealtimeStock(item);
        assertEquals(10, stock);
    }

    @Test
    @DisplayName("getRealtimeStock: fallback DB khi Redis value null")
    void getRealtimeStock_FallbackDB_WhenRedisValueNull() {
        FlashSaleItemService svc = new FlashSaleItemService(
                slotRepository, itemRepository, variantRepository,
                storeRepository, itemMapper, imageService);
        try {
            java.lang.reflect.Field f = FlashSaleItemService.class.getDeclaredField("redisTemplate");
            f.setAccessible(true);
            f.set(svc, redisTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        doReturn(valueOperations).when(redisTemplate).opsForValue();
        when(valueOperations.get(any())).thenReturn(null);

        int stock = svc.getRealtimeStock(item);
        assertEquals(10, stock);
    }

    @Test
    @DisplayName("getRealtimeStock: tra ve gia tri tu Redis khi co")
    void getRealtimeStock_FromRedis() {
        FlashSaleItemService svc = new FlashSaleItemService(
                slotRepository, itemRepository, variantRepository,
                storeRepository, itemMapper, imageService);
        try {
            java.lang.reflect.Field f = FlashSaleItemService.class.getDeclaredField("redisTemplate");
            f.setAccessible(true);
            f.set(svc, redisTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        doReturn(valueOperations).when(redisTemplate).opsForValue();
        when(valueOperations.get(FlashSaleItemService.STOCK_KEY_PREFIX + 1L)).thenReturn("42");

        int stock = svc.getRealtimeStock(item);
        assertEquals(42, stock);
    }
}