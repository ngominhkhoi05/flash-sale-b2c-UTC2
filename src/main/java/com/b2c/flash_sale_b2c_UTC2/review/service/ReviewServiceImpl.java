package com.b2c.flash_sale_b2c_UTC2.review.service;

import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.image.entity.Image;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.repository.ImageRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ProductReviewResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ProductReviewsPageResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.RatingDistributionDto;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ReplyReviewRequest;
import com.b2c.flash_sale_b2c_UTC2.review.dto.SellerReplyDto;
import com.b2c.flash_sale_b2c_UTC2.review.dto.SellerReviewItemResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.SellerReviewSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.review.entity.ProductReview;
import com.b2c.flash_sale_b2c_UTC2.review.exception.ReviewErrorCode;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.exception.StoreErrorCode;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private final ProductReviewRepository productReviewRepository;
    private final ProductRepository productRepository;
    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;

    @Override
    public ProductReviewsPageResponse getProductReviews(Long productId, Integer rating, Boolean hasMedia, Pageable pageable) {
        if (!productRepository.existsByIdAndStatus(productId, "ACTIVE")) {
            throw new BusinessException(ReviewErrorCode.REVIEW_PRODUCT_NOT_FOUND);
        }

        Page<ProductReview> reviewPage = productReviewRepository
                .findByProductIdAndStatusAndRatingAndHasMedia(productId, "VISIBLE", rating, hasMedia, pageable);

        List<ProductReview> reviews = reviewPage.getContent();

        Map<Long, List<Image>> reviewImagesMap;
        if (reviews.isEmpty()) {
            reviewImagesMap = Collections.emptyMap();
        } else {
            List<Long> reviewIds = reviews.stream().map(ProductReview::getId).collect(Collectors.toList());
            List<Image> allImages = imageRepository.findByOwnerTypeAndOwnerIdIn(ImageOwnerType.REVIEW, reviewIds);
            reviewImagesMap = allImages.stream().collect(Collectors.groupingBy(Image::getOwnerId));
        }

        Map<Long, User> userMap;
        Map<Long, String> userAvatarMap;
        if (reviews.isEmpty()) {
            userMap = Collections.emptyMap();
            userAvatarMap = Collections.emptyMap();
        } else {
            List<Long> userIds = reviews.stream()
                    .map(r -> r.getUser().getId())
                    .distinct()
                    .collect(Collectors.toList());
            userMap = userRepository.findAllById(userIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            userAvatarMap = imageRepository.findPrimaryImagesByOwnerIds(ImageOwnerType.USER, userIds).stream()
                    .collect(Collectors.toMap(Image::getOwnerId, Image::getUrl, (a, b) -> a));
        }

        List<ProductReviewResponse> items = reviews.stream()
                .map(review -> mapToResponse(review, reviewImagesMap, userMap, userAvatarMap))
                .collect(Collectors.toList());

        RatingDistributionDto ratingDistribution = aggregateRatingDistribution(productId);
        long mediaCount = productReviewRepository.countByProductIdWithMedia(productId);

        return ProductReviewsPageResponse.builder()
                .items(items)
                .ratingDistribution(ratingDistribution)
                .mediaCount(mediaCount)
                .totalElements((int) reviewPage.getTotalElements())
                .totalPages(reviewPage.getTotalPages())
                .page(reviewPage.getNumber())
                .size(reviewPage.getSize())
                .build();
    }

    private ProductReviewResponse mapToResponse(
            ProductReview review,
            Map<Long, List<Image>> reviewImagesMap,
            Map<Long, User> userMap, Map<Long, String> userAvatarMap) {

        String variantSku = null;
        Map<String, String> variantAttributes = null;
        if (review.getOrderItem() != null && review.getOrderItem().getVariant() != null) {
            variantSku = review.getOrderItem().getVariant().getSku();
            variantAttributes = review.getOrderItem().getVariant().getAttributes();
        }

        String userName = userMap.getOrDefault(review.getUser().getId(), review.getUser()).getFullName();

        List<String> album = reviewImagesMap.getOrDefault(review.getId(), Collections.emptyList())
                .stream()
                .map(Image::getUrl)
                .collect(Collectors.toList());

                com.b2c.flash_sale_b2c_UTC2.review.dto.SellerReplyDto sellerReplyDto = null;
        if (review.getSellerReply() != null && !review.getSellerReply().isBlank()) {
            String storeName = (review.getProduct() != null && review.getProduct().getStore() != null)
                    ? review.getProduct().getStore().getStoreName() : null;
            sellerReplyDto = com.b2c.flash_sale_b2c_UTC2.review.dto.SellerReplyDto.builder()
                    .content(review.getSellerReply())
                    .storeName(storeName)
                    .repliedAt(review.getSellerReplyAt())
                    .build();
        }
        List<String> urls = album != null ? album : Collections.emptyList();

        String userAvatarUrl = userAvatarMap.get(review.getUser().getId());

        return ProductReviewResponse.builder()
                .mediaUrls(urls)
                .isVerifiedPurchase(Boolean.TRUE)
                .userAvatarUrl(userAvatarUrl != null ? userAvatarUrl : "")
                .id(review.getId())
                .rating(review.getRating().intValue())
                .comment(review.getComment() != null ? review.getComment() : "")
                .userName(userName != null ? userName : "")
                .variantSku(variantSku)
                .variantAttributes(variantAttributes != null ? variantAttributes : Collections.emptyMap())
                .album(urls)
                .sellerReply(sellerReplyDto)
                .sellerReplyAt(review.getSellerReplyAt())
                .createdAt(review.getCreatedAt())
                .build();
    }

    private RatingDistributionDto aggregateRatingDistribution(Long productId) {
        List<Object[]> rawData = productReviewRepository.countByRatingGrouped(productId);

        long[] counts = {0L, 0L, 0L, 0L, 0L};

        for (Object[] row : rawData) {
            Short ratingValue = (Short) row[0];
            Long count = (Long) row[1];
            if (ratingValue != null) {
                int idx = 5 - ratingValue.intValue();
                if (idx >= 0 && idx < 5) {
                    counts[idx] = count;
                }
            }
        }

        return RatingDistributionDto.builder()
                .stars5(counts[0])
                .stars4(counts[1])
                .stars3(counts[2])
                .stars2(counts[3])
                .stars1(counts[4])
                .build();
    }

    @Override
    public SellerReviewSummaryResponse getSellerReviewSummary(Long sellerUserId) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        long totalReviews = productReviewRepository.countByStoreId(store.getId());
        Double avgRating = productReviewRepository.avgRatingByStoreId(store.getId());
        long repliedCount = productReviewRepository.countRepliedByStoreId(store.getId());
        long unrepliedCount = Math.max(0L, totalReviews - repliedCount);
        double responseRate = totalReviews > 0 ? (double) repliedCount / totalReviews : 0.0;

        List<Object[]> rawGrouped = productReviewRepository.countByRatingGroupedForStore(store.getId());
        Map<Integer, Long> ratingBreakdown = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            ratingBreakdown.put(i, 0L);
        }
        for (Object[] row : rawGrouped) {
            Short r = (Short) row[0];
            Long cnt = (Long) row[1];
            if (r != null) {
                ratingBreakdown.put(r.intValue(), cnt);
            }
        }

        return SellerReviewSummaryResponse.builder()
                .averageRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0)
                .totalReviews(totalReviews)
                .responseRate(Math.round(responseRate * 100.0) / 100.0)
                .unrepliedCount(unrepliedCount)
                .ratingBreakdown(ratingBreakdown)
                .build();
    }

    @Override
    public PageResponse<SellerReviewItemResponse> getSellerReviews(Long sellerUserId, Integer rating, String replyStatus, Pageable pageable) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        String sanitizedReplyStatus = (replyStatus != null && !"ALL".equalsIgnoreCase(replyStatus.trim()))
                ? replyStatus.trim().toUpperCase() : null;

        Page<ProductReview> reviewPage = productReviewRepository.findByStoreIdAndFilters(
                store.getId(), rating, sanitizedReplyStatus, pageable
        );

        List<ProductReview> reviews = reviewPage.getContent();
        Map<Long, List<Image>> reviewImagesMap;
        if (reviews.isEmpty()) {
            reviewImagesMap = Collections.emptyMap();
        } else {
            List<Long> reviewIds = reviews.stream().map(ProductReview::getId).toList();
            List<Image> images = imageRepository.findByOwnerTypeAndOwnerIdIn(ImageOwnerType.REVIEW, reviewIds);
            reviewImagesMap = images.stream().collect(Collectors.groupingBy(Image::getOwnerId));
        }

        List<SellerReviewItemResponse> items = reviews.stream().map(r -> {
            List<String> mediaUrls = reviewImagesMap.getOrDefault(r.getId(), Collections.emptyList())
                    .stream().map(Image::getUrl).toList();

            SellerReviewItemResponse.SellerReviewReplyDto replyDto = null;
            if (r.getSellerReply() != null) {
                replyDto = SellerReviewItemResponse.SellerReviewReplyDto.builder()
                        .id(r.getId())
                        .replyText(r.getSellerReply())
                        .repliedAt(r.getSellerReplyAt())
                        .build();
            }

            return SellerReviewItemResponse.builder()
                    .id(r.getId())
                    .productId(r.getProduct() != null ? r.getProduct().getId() : null)
                    .productName(r.getProduct() != null ? r.getProduct().getName() : "")
                    .productImage(null)
                    .variantSku(r.getOrderItem() != null && r.getOrderItem().getVariant() != null
                            ? r.getOrderItem().getVariant().getSku() : null)
                    .variantName(r.getOrderItem() != null && r.getOrderItem().getVariant() != null
                            ? r.getOrderItem().getVariant().getVariantName() : null)
                    .orderCode(r.getOrderItem() != null && r.getOrderItem().getOrder() != null
                            ? r.getOrderItem().getOrder().getOrderCode() : null)
                    .buyerName(r.getUser() != null ? r.getUser().getFullName() : "Khách hàng")
                    .rating(r.getRating() != null ? r.getRating().intValue() : 5)
                    .comment(r.getComment() != null ? r.getComment() : "")
                    .mediaUrls(mediaUrls)
                    .sellerReply(replyDto)
                    .createdAt(r.getCreatedAt())
                    .build();
        }).toList();

        return PageResponse.of(reviewPage, items);
    }

    @Override
    @Transactional
    public SellerReviewItemResponse replySellerReview(Long sellerUserId, Long reviewId, ReplyReviewRequest request) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        ProductReview review = productReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

        if (!review.getProduct().getStore().getId().equals(store.getId())) {
            throw new BusinessException(ReviewErrorCode.REVIEW_ACCESS_DENIED);
        }

        review.setSellerReply(request.getReplyText().trim());
        review.setSellerReplyAt(Instant.now());
        review.setUpdatedAt(Instant.now());
        ProductReview saved = productReviewRepository.save(review);
        log.info("Seller user ID {} replied to review ID {}", sellerUserId, reviewId);

        SellerReviewItemResponse.SellerReviewReplyDto replyDto = SellerReviewItemResponse.SellerReviewReplyDto.builder()
                .id(saved.getId())
                .replyText(saved.getSellerReply())
                .repliedAt(saved.getSellerReplyAt())
                .build();

        return SellerReviewItemResponse.builder()
                .id(saved.getId())
                .productId(saved.getProduct() != null ? saved.getProduct().getId() : null)
                .productName(saved.getProduct() != null ? saved.getProduct().getName() : "")
                .buyerName(saved.getUser() != null ? saved.getUser().getFullName() : "Khách hàng")
                .rating(saved.getRating() != null ? saved.getRating().intValue() : 5)
                .comment(saved.getComment() != null ? saved.getComment() : "")
                .sellerReply(replyDto)
                .createdAt(saved.getCreatedAt())
                .build();
    }
}