package com.b2c.flash_sale_b2c_UTC2.review.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.image.entity.Image;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.repository.ImageRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ProductReviewResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ProductReviewsPageResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.RatingDistributionDto;
import com.b2c.flash_sale_b2c_UTC2.review.entity.ProductReview;
import com.b2c.flash_sale_b2c_UTC2.review.exception.ReviewErrorCode;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
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
}