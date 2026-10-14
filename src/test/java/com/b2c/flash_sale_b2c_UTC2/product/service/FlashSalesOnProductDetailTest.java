package com.b2c.flash_sale_b2c_UTC2.product.service;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderItemRepository;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductDetailResponse;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.mapper.ProductVariantMapper;
import com.b2c.flash_sale_b2c_UTC2.product.mapper.ProductVariantMapperImpl;
import com.b2c.flash_sale_b2c_UTC2.product.repository.CategoryRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlashSalesOnProductDetailTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private FlashSaleItemRepository flashSaleItemRepository;
    @Mock private ProductReviewRepository productReviewRepository;
    @Mock private ImageService imageService;
    @Mock private StringRedisTemplate stringRedisTemplate;

    private ProductServiceImpl productService;

    private Store store;
    private Category category;
    private Product product;
    private ProductVariant variant1;
    private ProductVariant variant2;
    private FlashSaleSlot slot;

    @BeforeEach
    void setUp() {
        ProductVariantMapper mapper = new ProductVariantMapperImpl();

        // Use doReturn to avoid triggering mock method during stubbing setup.
        // opsForValue() returns null from mock by default, which is fine for Redis-miss tests.
        doReturn(null).when(stringRedisTemplate).opsForValue();

        // All image lookups return null (no images in tests)
        doReturn(null).when(imageService).getPrimaryImage(any(ImageOwnerType.class), anyLong());

        productService = new ProductServiceImpl(
                productRepository,
                productVariantRepository,
                categoryRepository,
                storeRepository,
                orderItemRepository,
                flashSaleItemRepository,
                productReviewRepository,
                mapper,
                imageService,
                stringRedisTemplate
        );

        store = Store.builder().id(1L).storeName("Test Shop").status("APPROVED").build();
        category = Category.builder().id(1).name("Fashion").build();
        product = Product.builder()
                .id(100L).store(store).category(category)
                .name("Men T-Shirt").description("High quality cotton t-shirt")
                .status("ACTIVE").totalSold(50).createdAt(Instant.now())
                .tierVariationConfigs(Collections.emptyList()).build();
        variant1 = ProductVariant.builder()
                .id(10L).product(product).sku("AO-M").variantName("Size M")
                .originalPrice(new BigDecimal("200000")).stockQuantity(30)
                .status("ACTIVE").attributes(Collections.emptyMap())
                .createdAt(Instant.now()).version(0L).build();
        variant2 = ProductVariant.builder()
                .id(11L).product(product).sku("AO-L").variantName("Size L")
                .originalPrice(new BigDecimal("220000")).stockQuantity(20)
                .status("ACTIVE").attributes(Collections.emptyMap())
                .createdAt(Instant.now()).version(0L).build();
        slot = FlashSaleSlot.builder()
                .id(5L).title("Flash Sale 12.12")
                .startTime(Instant.now().minusSeconds(3600))
                .endTime(Instant.now().plusSeconds(3600))
                .status("ACTIVE").createdAt(Instant.now()).build();
    }

    @Test
    @DisplayName("Part A: Product has 2 active FlashSale items -> flashSales.size() == 2, sorted by flashSalePrice ASC")
    void buildProductDetailResponse_FlashSales_SortedByPrice() {
        FlashSaleItem item1 = FlashSaleItem.builder()
                .id(1L).slot(slot).variant(variant1)
                .flashSalePrice(new BigDecimal("150000"))
                .allocatedStock(30).availableStock(25)
                .userPurchaseLimit(2).status("APPROVED").createdAt(Instant.now()).build();
        FlashSaleItem item2 = FlashSaleItem.builder()
                .id(2L).slot(slot).variant(variant2)
                .flashSalePrice(new BigDecimal("180000"))
                .allocatedStock(20).availableStock(18)
                .userPurchaseLimit(2).status("APPROVED").createdAt(Instant.now()).build();
        List<FlashSaleItem> activeItems = List.of(item1, item2);

        when(productRepository.findByIdWithStoreAndCategory(100L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProductIdAndStatus(100L, "ACTIVE")).thenReturn(List.of(variant1, variant2));
        when(flashSaleItemRepository.findActiveByProductId(100L)).thenReturn(activeItems);
        when(productReviewRepository.avgRatingByProductId(100L)).thenReturn(4.5);
        when(productReviewRepository.countByProductIdAndVisible(100L)).thenReturn(10L);
        when(productRepository.findPriceRangeByProductId(100L)).thenReturn(new Object[]{new BigDecimal("200000"), new BigDecimal("220000")});

        ProductDetailResponse response = productService.getPublicProductDetail(100L);

        assertNotNull(response);
        assertNotNull(response.getFlashSales());
        assertEquals(2, response.getFlashSales().size());
        assertEquals(new BigDecimal("150000"), response.getFlashSales().get(0).getFlashSalePrice());
        assertEquals(new BigDecimal("180000"), response.getFlashSales().get(1).getFlashSalePrice());
        assertEquals(5L, response.getFlashSales().get(0).getSlotId());
        assertEquals("ACTIVE", response.getFlashSales().get(0).getSlotStatus());
        assertNotNull(response.getFlashSales().get(0).getSlotStartTime());
        assertNotNull(response.getFlashSales().get(0).getSlotEndTime());
        assertEquals(10L, response.getFlashSales().get(0).getVariantId());
        assertEquals(new BigDecimal("200000"), response.getFlashSales().get(0).getOriginalPrice());
        assertEquals(25, response.getFlashSales().get(0).getStockAvailable());
    }

    @Test
    @DisplayName("Part A: Edge - no active flash sale -> flashSales = empty list")
    void buildProductDetailResponse_NoActiveFlashSale_EmptyList() {
        when(productRepository.findByIdWithStoreAndCategory(100L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProductIdAndStatus(100L, "ACTIVE")).thenReturn(List.of(variant1));
        when(flashSaleItemRepository.findActiveByProductId(100L)).thenReturn(Collections.emptyList());
        when(productReviewRepository.avgRatingByProductId(100L)).thenReturn(0.0);
        when(productReviewRepository.countByProductIdAndVisible(100L)).thenReturn(0L);
        when(productRepository.findPriceRangeByProductId(100L)).thenReturn(new Object[]{new BigDecimal("200000"), new BigDecimal("200000")});

        ProductDetailResponse response = productService.getPublicProductDetail(100L);

        assertNotNull(response);
        assertNotNull(response.getFlashSales());
        assertEquals(0, response.getFlashSales().size());
    }

    @Test
    @DisplayName("Part A: Edge - Redis unavailable -> fallback to DB available_stock")
    void buildProductDetailResponse_RedisMiss_FallsBackToDbStock() {
        FlashSaleItem item1 = FlashSaleItem.builder()
                .id(3L).slot(slot).variant(variant1)
                .flashSalePrice(new BigDecimal("150000"))
                .allocatedStock(30).availableStock(5)
                .userPurchaseLimit(1).status("APPROVED").createdAt(Instant.now()).build();
        List<FlashSaleItem> activeItems = List.of(item1);

        when(productRepository.findByIdWithStoreAndCategory(100L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProductIdAndStatus(100L, "ACTIVE")).thenReturn(List.of(variant1));
        when(flashSaleItemRepository.findActiveByProductId(100L)).thenReturn(activeItems);
        when(productReviewRepository.avgRatingByProductId(100L)).thenReturn(0.0);
        when(productReviewRepository.countByProductIdAndVisible(100L)).thenReturn(0L);
        when(productRepository.findPriceRangeByProductId(100L)).thenReturn(new Object[]{new BigDecimal("200000"), new BigDecimal("200000")});

        ProductDetailResponse response = productService.getPublicProductDetail(100L);

        assertNotNull(response);
        assertNotNull(response.getFlashSales());
        assertEquals(1, response.getFlashSales().size());
        assertEquals(5, response.getFlashSales().get(0).getStockAvailable());
    }
}