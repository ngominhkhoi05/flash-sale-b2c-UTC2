package com.b2c.flash_sale_b2c_UTC2.review.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.image.entity.Image;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.order.entity.OrderItem;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ProductReviewResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ProductReviewsPageResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.RatingDistributionDto;
import com.b2c.flash_sale_b2c_UTC2.review.entity.ProductReview;
import com.b2c.flash_sale_b2c_UTC2.review.exception.ReviewErrorCode;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ProductReviewRepository productReviewRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private com.b2c.flash_sale_b2c_UTC2.image.repository.ImageRepository imageRepository;

    @Mock
    private com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository userRepository;

    private com.b2c.flash_sale_b2c_UTC2.review.service.ReviewServiceImpl reviewService;

    private Product activeProduct;
    private ProductVariant variant;
    private OrderItem orderItem1;
    private OrderItem orderItem2;
    private OrderItem orderItem3;
    private User user1;
    private User user2;
    private User user3;

    @BeforeEach
    void setUp() {
        // Manually instantiate to avoid InjectMocks issues
        reviewService = new com.b2c.flash_sale_b2c_UTC2.review.service.ReviewServiceImpl(
                productReviewRepository, productRepository, imageRepository, userRepository);

        user1 = User.builder().id(1L).fullName("User One").build();
        user2 = User.builder().id(2L).fullName("User Two").build();
        user3 = User.builder().id(3L).fullName("User Three").build();

        activeProduct = Product.builder().id(100L).name("Test Product").status("ACTIVE").build();

        variant = ProductVariant.builder()
                .id(10L).sku("SKU-001").variantName("Size M")
                .attributes(Map.of("Size", "M"))
                .originalPrice(new BigDecimal("150000")).build();

        orderItem1 = OrderItem.builder().id(101L).variant(variant).productName("Test").variantName("Size M").build();
        orderItem2 = OrderItem.builder().id(102L).variant(variant).productName("Test").variantName("Size M").build();
        orderItem3 = OrderItem.builder().id(103L).variant(variant).productName("Test").variantName("Size M").build();
    }

    @Nested
    @DisplayName("Happy path: 3 reviews with 5/4/3 star, 1 has image")
    class HappyPath {

        @Test
        @DisplayName("Should return reviews with correct ratingDistribution and mediaCount")
        void shouldReturnReviewsWithCorrectRatingDistribution() {
            ProductReview review1 = ProductReview.builder()
                    .id(1L).product(activeProduct).user(user1).orderItem(orderItem1)
                    .rating((short) 5).comment("Excellent!").status("VISIBLE").createdAt(Instant.now()).build();
            ProductReview review2 = ProductReview.builder()
                    .id(2L).product(activeProduct).user(user2).orderItem(orderItem2)
                    .rating((short) 4).comment("Good").status("VISIBLE").createdAt(Instant.now()).build();
            ProductReview review3 = ProductReview.builder()
                    .id(3L).product(activeProduct).user(user3).orderItem(orderItem3)
                    .rating((short) 3).comment("OK").status("VISIBLE").createdAt(Instant.now()).build();

            List<ProductReview> reviews = List.of(review1, review2, review3);
            Page<ProductReview> page = new PageImpl<>(reviews, PageRequest.of(0, 10), 3);

            List<Object[]> distribution = new ArrayList<>();
            distribution.add(new Object[]{(short) 5, 1L});
            distribution.add(new Object[]{(short) 4, 1L});
            distribution.add(new Object[]{(short) 3, 1L});

            when(productRepository.existsByIdAndStatus(100L, "ACTIVE")).thenReturn(true);
            when(productReviewRepository.findByProductIdAndStatusAndRatingAndHasMedia(eq(100L), eq("VISIBLE"), any(), any(), any()))
                    .thenReturn(page);
            when(productReviewRepository.countByRatingGrouped(100L)).thenReturn(distribution);
            when(productReviewRepository.countByProductIdWithMedia(100L)).thenReturn(1L);
            when(imageRepository.findByOwnerTypeAndOwnerIdIn(eq(ImageOwnerType.REVIEW), anyList()))
                    .thenReturn(List.of(
                            Image.builder().id(1L).ownerType(ImageOwnerType.REVIEW).ownerId(1L)
                                    .url("https://example.com/img1.jpg").build()
                    ));
            when(userRepository.findAllById(anyList())).thenReturn(List.of(user1, user2, user3));

            ProductReviewsPageResponse response = reviewService.getProductReviews(100L, null, null, PageRequest.of(0, 10));

            assertNotNull(response);
            assertEquals(3, response.getItems().size());
            assertEquals(3, response.getTotalElements());

            RatingDistributionDto dist = response.getRatingDistribution();
            assertEquals(1L, dist.getStars5());
            assertEquals(1L, dist.getStars4());
            assertEquals(1L, dist.getStars3());
            assertEquals(0L, dist.getStars2());
            assertEquals(0L, dist.getStars1());

            assertEquals(1L, response.getMediaCount());

            ProductReviewResponse item = response.getItems().get(0);
            assertEquals("User One", item.getUserName());
            assertEquals(5, item.getRating());
        }
    }

    @Nested
    @DisplayName("Filter: rating=5")
    class FilterByRating {

        @Test
        @DisplayName("Should return only 5-star reviews but ratingDistribution still shows all levels")
        void shouldFilterByRatingButShowFullDistribution() {
            ProductReview review1 = ProductReview.builder()
                    .id(1L).product(activeProduct).user(user1).orderItem(orderItem1)
                    .rating((short) 5).comment("Excellent!").status("VISIBLE").createdAt(Instant.now()).build();
            Page<ProductReview> page = new PageImpl<>(List.of(review1), PageRequest.of(0, 10), 1);

            List<Object[]> distribution = new ArrayList<>();
            distribution.add(new Object[]{(short) 5, 1L});
            distribution.add(new Object[]{(short) 4, 1L});
            distribution.add(new Object[]{(short) 3, 1L});

            when(productRepository.existsByIdAndStatus(100L, "ACTIVE")).thenReturn(true);
            when(productReviewRepository.findByProductIdAndStatusAndRatingAndHasMedia(eq(100L), eq("VISIBLE"), eq(5), any(), any()))
                    .thenReturn(page);
            when(productReviewRepository.countByRatingGrouped(100L)).thenReturn(distribution);
            when(productReviewRepository.countByProductIdWithMedia(100L)).thenReturn(1L);
            when(imageRepository.findByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(Collections.emptyList());
            when(userRepository.findAllById(anyList())).thenReturn(List.of(user1));

            ProductReviewsPageResponse response = reviewService.getProductReviews(100L, 5, null, PageRequest.of(0, 10));

            assertEquals(1, response.getItems().size());
            assertEquals(5, response.getItems().get(0).getRating());
            assertEquals(1, response.getTotalElements());

            RatingDistributionDto dist = response.getRatingDistribution();
            assertEquals(1L, dist.getStars5());
            assertEquals(1L, dist.getStars4());
            assertEquals(1L, dist.getStars3());
            assertEquals(0L, dist.getStars2());
            assertEquals(0L, dist.getStars1());
        }
    }

    @Nested
    @DisplayName("Filter: hasMedia=true")
    class FilterByHasMedia {

        @Test
        @DisplayName("Should return only reviews with images")
        void shouldFilterByHasMedia() {
            ProductReview review1 = ProductReview.builder()
                    .id(1L).product(activeProduct).user(user1).orderItem(orderItem1)
                    .rating((short) 5).comment("With photo").status("VISIBLE").createdAt(Instant.now()).build();
            Page<ProductReview> page = new PageImpl<>(List.of(review1), PageRequest.of(0, 10), 1);

            List<Object[]> distribution = new ArrayList<>();
            distribution.add(new Object[]{(short) 5, 1L});

            when(productRepository.existsByIdAndStatus(100L, "ACTIVE")).thenReturn(true);
            when(productReviewRepository.findByProductIdAndStatusAndRatingAndHasMedia(eq(100L), eq("VISIBLE"), any(), eq(true), any()))
                    .thenReturn(page);
            when(productReviewRepository.countByRatingGrouped(100L)).thenReturn(distribution);
            when(productReviewRepository.countByProductIdWithMedia(100L)).thenReturn(1L);
            when(imageRepository.findByOwnerTypeAndOwnerIdIn(eq(ImageOwnerType.REVIEW), anyList()))
                    .thenReturn(List.of(
                            Image.builder().id(1L).ownerType(ImageOwnerType.REVIEW).ownerId(1L)
                                    .url("https://example.com/img1.jpg").build()
                    ));
            when(userRepository.findAllById(anyList())).thenReturn(List.of(user1));

            ProductReviewsPageResponse response = reviewService.getProductReviews(100L, null, true, PageRequest.of(0, 10));

            assertEquals(1, response.getItems().size());
            assertEquals(1L, response.getMediaCount());
        }
    }

    @Nested
    @DisplayName("Edge: no reviews")
    class EdgeNoReviews {

        @Test
        @DisplayName("Should return empty items with zero distribution")
        void shouldReturnEmptyWithZeroDistribution() {
            Page<ProductReview> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

            when(productRepository.existsByIdAndStatus(100L, "ACTIVE")).thenReturn(true);
            when(productReviewRepository.findByProductIdAndStatusAndRatingAndHasMedia(eq(100L), eq("VISIBLE"), any(), any(), any()))
                    .thenReturn(emptyPage);
            when(productReviewRepository.countByRatingGrouped(100L)).thenReturn(Collections.emptyList());
            when(productReviewRepository.countByProductIdWithMedia(100L)).thenReturn(0L);

            ProductReviewsPageResponse response = reviewService.getProductReviews(100L, null, null, PageRequest.of(0, 10));

            assertNotNull(response);
            assertTrue(response.getItems().isEmpty());
            assertEquals(0, response.getTotalElements());
            assertEquals(0L, response.getMediaCount());

            RatingDistributionDto dist = response.getRatingDistribution();
            assertEquals(0L, dist.getStars5());
            assertEquals(0L, dist.getStars4());
            assertEquals(0L, dist.getStars3());
            assertEquals(0L, dist.getStars2());
            assertEquals(0L, dist.getStars1());
        }
    }

    @Nested
    @DisplayName("Edge: product not found")
    class EdgeProductNotFound {

        @Test
        @DisplayName("Should throw REVIEW_PRODUCT_NOT_FOUND")
        void shouldThrowProductNotFound() {
            when(productRepository.existsByIdAndStatus(999L, "ACTIVE")).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    reviewService.getProductReviews(999L, null, null, PageRequest.of(0, 10)));

            assertEquals(ReviewErrorCode.REVIEW_PRODUCT_NOT_FOUND, ex.getErrorCode());
        }
    }
}