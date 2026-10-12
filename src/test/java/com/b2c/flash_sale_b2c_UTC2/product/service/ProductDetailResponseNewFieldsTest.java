package com.b2c.flash_sale_b2c_UTC2.product.service;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductDetailResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductVariantResponse;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.mapper.ProductVariantMapper;
import com.b2c.flash_sale_b2c_UTC2.product.mapper.ProductVariantMapperImpl;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProductServiceImpl.buildProductDetailResponse() new fields.
 * Subplan 2: ratingAvg, ratingCount, soldCount, minPrice, maxPrice, discountPercent, images.
 */
@ExtendWith(MockitoExtension.class)
class ProductDetailResponseNewFieldsTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductReviewRepository productReviewRepository;

    @Mock
    private FlashSaleItemRepository flashSaleItemRepository;

    @Mock
    private ImageService imageService;

    // Real mapper (no runtime deps)
    private final ProductVariantMapper productVariantMapper = new ProductVariantMapperImpl();

    // Manually instantiated service with all dependencies
    private ProductServiceImpl productService;

    private Store approvedStore;
    private Category category;

    @BeforeEach
    void setUp() {
        User seller = User.builder().id(10L).email("seller@test.com").build();
        approvedStore = Store.builder()
                .id(100L)
                .user(seller)
                .storeName("Test Shop")
                .status("APPROVED")
                .build();
        category = Category.builder().id(1).name("Test Category").slug("test").build();

        productService = new ProductServiceImpl(
                productRepository,
                productVariantRepository,
                null, // categoryRepository not needed for getPublicProductDetail
                null, // storeRepository not needed
                null, // orderItemRepository not needed
                flashSaleItemRepository,
                productReviewRepository,
                productVariantMapper,
                imageService
        );
    }

    private Product makeProduct(Long id, Integer totalSold) {
        return Product.builder()
                .id(id)
                .store(approvedStore)
                .category(category)
                .name("Test Product " + id)
                .description("Description")
                .status("ACTIVE")
                .totalSold(totalSold)
                .createdAt(Instant.now())
                .build();
    }

    private ProductVariant makeVariant(Long id, Long productId, BigDecimal price) {
        Product p = makeProduct(productId, 150);
        return ProductVariant.builder()
                .id(id)
                .product(p)
                .sku("SKU-" + id)
                .variantName("Variant " + id)
                .originalPrice(price)
                .stockQuantity(100)
                .status("ACTIVE")
                .build();
    }

    private void defaultMocks(Long productId) {
        when(productReviewRepository.avgRatingByProductId(productId)).thenReturn(null);
        when(productReviewRepository.countByProductIdAndVisible(productId)).thenReturn(0L);
        when(productRepository.findPriceRangeByProductId(productId)).thenReturn(null);
        when(flashSaleItemRepository.findActiveByProductId(productId)).thenReturn(List.of());
        when(imageService.getPrimaryImage(any(), eq(productId))).thenReturn(null);
        when(imageService.listImages(any(), eq(productId))).thenReturn(List.of());
    }

    // NOTE: Since buildProductDetailResponse is private, we test through getPublicProductDetail.
    // To avoid NPE in buildProductDetailResponse from variant-image map (variant.id=null in test),
    // we mock productVariantRepository.findByProductIdAndStatus to return empty list
    // and instead pass an empty list to buildProductDetailResponse via getPublicProductDetail flow.

    @Nested
    @DisplayName("ratingAvg and ratingCount")
    class RatingFields {

        @Test
        @DisplayName("Happy: product has reviews -> ratingAvg=4.5, ratingCount=10")
        void withReviews_PopulatesRatingFields() {
            Long productId = 200L;
            Product product = makeProduct(productId, 150);
            when(productRepository.findByIdWithStoreAndCategory(productId))
                    .thenReturn(java.util.Optional.of(product));
            when(productVariantRepository.findByProductIdAndStatus(productId, "ACTIVE"))
                    .thenReturn(List.of());
            when(productReviewRepository.avgRatingByProductId(productId)).thenReturn(4.5);
            when(productReviewRepository.countByProductIdAndVisible(productId)).thenReturn(10L);
            when(productRepository.findPriceRangeByProductId(productId)).thenReturn(null);
            when(flashSaleItemRepository.findActiveByProductId(productId)).thenReturn(List.of());
            when(imageService.getPrimaryImage(any(), eq(productId))).thenReturn(null);
            when(imageService.listImages(any(), eq(productId))).thenReturn(List.of());

            ProductDetailResponse result = productService.getPublicProductDetail(productId);

            assertNotNull(result);
            assertEquals(4.5, result.getRatingAvg());
            assertEquals(10, result.getRatingCount());
            assertEquals(150, result.getSoldCount());
        }

        @Test
        @DisplayName("Edge: no reviews -> ratingAvg=0.0, ratingCount=0")
        void noReviews_DefaultsToZero() {
            Long productId = 200L;
            Product product = makeProduct(productId, 50);
            when(productRepository.findByIdWithStoreAndCategory(productId))
                    .thenReturn(java.util.Optional.of(product));
            when(productVariantRepository.findByProductIdAndStatus(productId, "ACTIVE"))
                    .thenReturn(List.of());
            defaultMocks(productId);
            // avgRating returns null -> service should default to 0.0
            when(productReviewRepository.avgRatingByProductId(productId)).thenReturn(null);
            when(productReviewRepository.countByProductIdAndVisible(productId)).thenReturn(0L);

            ProductDetailResponse result = productService.getPublicProductDetail(productId);

            assertNotNull(result);
            assertEquals(0.0, result.getRatingAvg());
            assertEquals(0, result.getRatingCount());
            assertEquals(50, result.getSoldCount());
        }
    }

    @Nested
    @DisplayName("minPrice and maxPrice")
    class PriceRangeFields {

        @Test
        @DisplayName("Happy: 3 ACTIVE variants priced 100k/200k/300k -> minPrice=100k, maxPrice=300k")
        void threeActiveVariants_PriceRangeCorrect() {
            Long productId = 200L;
            Product product = makeProduct(productId, 0);
            when(productRepository.findByIdWithStoreAndCategory(productId))
                    .thenReturn(java.util.Optional.of(product));
            when(productVariantRepository.findByProductIdAndStatus(productId, "ACTIVE"))
                    .thenReturn(List.of());
            when(productReviewRepository.avgRatingByProductId(productId)).thenReturn(null);
            when(productReviewRepository.countByProductIdAndVisible(productId)).thenReturn(0L);
            when(productRepository.findPriceRangeByProductId(productId))
                    .thenReturn(new Object[]{new BigDecimal("100000"), new BigDecimal("300000")});
            when(flashSaleItemRepository.findActiveByProductId(productId)).thenReturn(List.of());
            when(imageService.getPrimaryImage(any(), eq(productId))).thenReturn(null);
            when(imageService.listImages(any(), eq(productId))).thenReturn(List.of());

            ProductDetailResponse result = productService.getPublicProductDetail(productId);

            assertNotNull(result);
            assertEquals(new BigDecimal("100000"), result.getMinPrice());
            assertEquals(new BigDecimal("300000"), result.getMaxPrice());
        }
    }

    @Nested
    @DisplayName("discountPercent")
    class DiscountPercentField {

        @Test
        @DisplayName("Happy: 1 ACTIVE FlashSaleItem at 80k, original 200k -> discountPercent=60")
        void withActiveFlashSale_DiscountCorrect() {
            Long productId = 200L;
            Product product = makeProduct(productId, 0);
            ProductVariant v = makeVariant(5L, productId, new BigDecimal("200000"));
            FlashSaleSlot activeSlot = FlashSaleSlot.builder().id(1L).status("ACTIVE").build();
            FlashSaleItem fsItem = FlashSaleItem.builder()
                    .id(10L)
                    .variant(v)
                    .flashSalePrice(new BigDecimal("80000"))
                    .status("APPROVED")
                    .slot(activeSlot)
                    .build();

            when(productRepository.findByIdWithStoreAndCategory(productId))
                    .thenReturn(java.util.Optional.of(product));
            when(productVariantRepository.findByProductIdAndStatus(productId, "ACTIVE"))
                    .thenReturn(List.of());
            defaultMocks(productId);
            when(flashSaleItemRepository.findActiveByProductId(productId)).thenReturn(List.of(fsItem));

            ProductDetailResponse result = productService.getPublicProductDetail(productId);

            assertNotNull(result);
            // (200000 - 80000) / 200000 * 100 = 60
            assertEquals(60, result.getDiscountPercent());
        }

        @Test
        @DisplayName("Edge: no ACTIVE FlashSaleItem -> discountPercent=null")
        void noActiveFlashSale_DiscountNull() {
            Long productId = 200L;
            Product product = makeProduct(productId, 0);
            when(productRepository.findByIdWithStoreAndCategory(productId))
                    .thenReturn(java.util.Optional.of(product));
            when(productVariantRepository.findByProductIdAndStatus(productId, "ACTIVE"))
                    .thenReturn(List.of());
            defaultMocks(productId);
            when(flashSaleItemRepository.findActiveByProductId(productId)).thenReturn(List.of());

            ProductDetailResponse result = productService.getPublicProductDetail(productId);

            assertNotNull(result);
            assertNull(result.getDiscountPercent());
        }
    }

    @Nested
    @DisplayName("images")
    class ImagesField {

        @Test
        @DisplayName("Edge: no images -> images=[]")
        void noImages_EmptyList() {
            Long productId = 200L;
            Product product = makeProduct(productId, 0);
            when(productRepository.findByIdWithStoreAndCategory(productId))
                    .thenReturn(java.util.Optional.of(product));
            when(productVariantRepository.findByProductIdAndStatus(productId, "ACTIVE"))
                    .thenReturn(List.of());
            defaultMocks(productId);
            when(imageService.listImages(ImageOwnerType.PRODUCT, productId)).thenReturn(List.of());

            ProductDetailResponse result = productService.getPublicProductDetail(productId);

            assertNotNull(result);
            assertNotNull(result.getImages());
            assertEquals(0, result.getImages().size());
        }
    }
}
