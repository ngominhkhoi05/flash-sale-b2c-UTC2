package com.b2c.flash_sale_b2c_UTC2.image.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse;
import com.b2c.flash_sale_b2c_UTC2.image.entity.Image;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.event.CloudinaryCleanupEvent;
import com.b2c.flash_sale_b2c_UTC2.image.exception.ImageErrorCode;
import com.b2c.flash_sale_b2c_UTC2.image.mapper.ImageMapper;
import com.b2c.flash_sale_b2c_UTC2.image.repository.ImageRepository;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.review.entity.ProductReview;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai business logic cho quản lý ảnh đa đối tượng (polymorphic).
 * Tuân thủ theo docs/04_architecture_analysis.md mục 5.4 và docs/api/api-document.md mục 28.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";

    private final ImageRepository imageRepository;
    private final CloudinaryService cloudinaryService;
    private final ImageMapper imageMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductReviewRepository productReviewRepository;

    // ====================== attach ======================

    @Override
    @Transactional
    public ImageResponse attachImage(Long currentUserId, ImageOwnerType ownerType, Long ownerId,
                                     MultipartFile file, Boolean isPrimary, Integer displayOrder) {
        validateOwnership(currentUserId, ownerType, ownerId);
        int order = displayOrder != null ? displayOrder : 0;
        boolean wantPrimary = Boolean.TRUE.equals(isPrimary);

        // USER/STORE là 1-1: phải soft-delete ảnh cũ trước khi insert ảnh mới
        // để không vi phạm unique index uq_images_user_one / uq_images_store_one.
        if (ownerType == ImageOwnerType.USER || ownerType == ImageOwnerType.STORE) {
            replaceExistingOneToOne(ownerType, ownerId, currentUserId);
        }

        // PRODUCT/VARIANT: nếu isPrimary=true thì unset primary cũ
        if (wantPrimary && (ownerType == ImageOwnerType.PRODUCT || ownerType == ImageOwnerType.VARIANT)) {
            imageRepository.findFirstByOwnerTypeAndOwnerIdAndIsPrimaryTrueAndStatus(
                            ownerType, ownerId, STATUS_ACTIVE)
                    .ifPresent(existing -> {
                        imageRepository.clearOtherPrimary(ownerType, ownerId, existing.getId(), Instant.now());
                    });
        }

        String folder = resolveFolder(ownerType, ownerId);
        CloudinaryService.CloudinaryUploadResult upload = cloudinaryService.upload(file, folder);

        Image image = Image.builder()
                .ownerType(ownerType)
                .ownerId(ownerId)
                .url(upload.url())
                .cloudinaryPublicId(upload.publicId())
                .displayOrder(order)
                .isPrimary(wantPrimary && (ownerType == ImageOwnerType.PRODUCT || ownerType == ImageOwnerType.VARIANT))
                .status(STATUS_ACTIVE)
                .cloudinaryDeleted(false)
                .build();
        Image saved = imageRepository.save(image);
        log.info("User {} đã gắn ảnh mới cho {}/{} (imageId={})", currentUserId, ownerType, ownerId, saved.getId());
        return imageMapper.toResponse(saved);
    }

    // ====================== list / get ======================

    @Override
    @Transactional(readOnly = true)
    public List<ImageResponse> listImages(ImageOwnerType ownerType, Long ownerId) {
        return imageRepository
                .findByOwnerTypeAndOwnerIdAndStatusOrderByDisplayOrderAsc(ownerType, ownerId, STATUS_ACTIVE)
                .stream()
                .map(imageMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ImageResponse getPrimaryImage(ImageOwnerType ownerType, Long ownerId) {
        return imageRepository
                .findFirstByOwnerTypeAndOwnerIdAndIsPrimaryTrueAndStatus(ownerType, ownerId, STATUS_ACTIVE)
                .map(imageMapper::toResponse)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ImageResponse> getPrimaryImagesByOwnerIds(ImageOwnerType ownerType, List<Long> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Image> images = imageRepository.findPrimaryImagesByOwnerIds(ownerType, ownerIds);
        return images.stream()
                .map(imageMapper::toResponse)
                .collect(Collectors.toMap(ImageResponse::getOwnerId, Function.identity()));
    }

    // ====================== delete ======================

    @Override
    @Transactional
    public void detachImage(Long currentUserId, Long imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ImageErrorCode.IMAGE_NOT_FOUND));
        if (!STATUS_ACTIVE.equals(image.getStatus())) {
            return; // idempotent: đã soft-delete rồi
        }
        validateOwnership(currentUserId, image.getOwnerType(), image.getOwnerId());
        image.setStatus(STATUS_INACTIVE);
        image.setUpdatedAt(Instant.now());
        imageRepository.save(image);
        // Phát event async sau khi transaction commit để gọi Cloudinary destroy
        eventPublisher.publishEvent(new CloudinaryCleanupEvent(image.getId(), image.getCloudinaryPublicId()));
    }

    @Override
    @Transactional
    public void forceDeleteImage(Long imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ImageErrorCode.IMAGE_NOT_FOUND));
        // Gọi Cloudinary destroy đồng bộ ngay
        if (image.getCloudinaryPublicId() != null && !image.getCloudinaryPublicId().isBlank()) {
            cloudinaryService.destroy(image.getCloudinaryPublicId());
        }
        imageRepository.delete(image);
    }

    // ====================== primary / order ======================

    @Override
    @Transactional
    public ImageResponse setPrimary(Long currentUserId, Long imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ImageErrorCode.IMAGE_NOT_FOUND));
        ImageOwnerType t = image.getOwnerType();
        if (t != ImageOwnerType.PRODUCT && t != ImageOwnerType.VARIANT) {
            throw new BusinessException(ImageErrorCode.INVALID_PRIMARY_FOR_OWNER);
        }
        validateOwnership(currentUserId, t, image.getOwnerId());

        // Partial unique index cho phép 1 owner có 1 ảnh primary ACTIVE;
        // unset ảnh primary khác (nếu có) trước khi set primary mới.
        imageRepository.findFirstByOwnerTypeAndOwnerIdAndIsPrimaryTrueAndStatus(t, image.getOwnerId(), STATUS_ACTIVE)
                .filter(other -> !Objects.equals(other.getId(), imageId))
                .ifPresent(other -> imageRepository.clearOtherPrimary(t, image.getOwnerId(), other.getId(), Instant.now()));

        image.setIsPrimary(true);
        image.setUpdatedAt(Instant.now());
        return imageMapper.toResponse(imageRepository.save(image));
    }

    @Override
    @Transactional
    public ImageResponse setDisplayOrder(Long currentUserId, Long imageId, int newOrder) {
        if (newOrder < 0) {
            throw new BusinessException(ImageErrorCode.INVALID_ORDER_FOR_OWNER);
        }
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ImageErrorCode.IMAGE_NOT_FOUND));
        if (image.getOwnerType() != ImageOwnerType.REVIEW) {
            throw new BusinessException(ImageErrorCode.INVALID_ORDER_FOR_OWNER);
        }
        validateOwnership(currentUserId, image.getOwnerType(), image.getOwnerId());
        image.setDisplayOrder(newOrder);
        image.setUpdatedAt(Instant.now());
        return imageMapper.toResponse(imageRepository.save(image));
    }

    // ====================== ownership helpers ======================

    /**
     * Validate owner tồn tại và user hiện tại sở hữu owner.
     * Với USER, owner chính là currentUser.
     * Với STORE: store.user.id == currentUserId.
     * Với PRODUCT: product.store.user.id == currentUserId.
     * Với VARIANT: variant.product.store.user.id == currentUserId.
     * Với REVIEW: review.user.id == currentUserId.
     */
    private void validateOwnership(Long currentUserId, ImageOwnerType ownerType, Long ownerId) {
        boolean owned = switch (ownerType) {
            case USER -> userRepository.findById(ownerId)
                    .map(u -> ownerId.equals(currentUserId))
                    .orElse(false);
            case STORE -> storeRepository.findById(ownerId)
                    .map(s -> currentUserId.equals(s.getUser().getId()))
                    .orElse(false);
            case PRODUCT -> productRepository.findById(ownerId)
                    .map(p -> currentUserId.equals(p.getStore().getUser().getId()))
                    .orElse(false);
            case VARIANT -> productVariantRepository.findById(ownerId)
                    .map(v -> currentUserId.equals(v.getProduct().getStore().getUser().getId()))
                    .orElse(false);
            case REVIEW -> productReviewRepository.findById(ownerId)
                    .map(r -> currentUserId.equals(r.getUser().getId()))
                    .orElse(false);
        };
        if (!owned) {
            throw new BusinessException(ImageErrorCode.NOT_OWNER);
        }
    }

    /** Soft-delete ảnh ACTIVE 1-1 của owner, phát event cleanup Cloudinary. */
    private void replaceExistingOneToOne(ImageOwnerType ownerType, Long ownerId, Long currentUserId) {
        imageRepository.findFirstByOwnerTypeAndOwnerIdAndStatus(ownerType, ownerId, STATUS_ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(STATUS_INACTIVE);
                    existing.setUpdatedAt(Instant.now());
                    imageRepository.save(existing);
                    eventPublisher.publishEvent(
                            new CloudinaryCleanupEvent(existing.getId(), existing.getCloudinaryPublicId()));
                });
    }

    /** Resolve folder Cloudinary theo ownerType. */
    private String resolveFolder(ImageOwnerType ownerType, Long ownerId) {
        return switch (ownerType) {
            case USER -> "users/" + ownerId + "/avatar";
            case STORE -> "stores/" + ownerId + "/logo";
            case PRODUCT -> "products/" + ownerId;
            case VARIANT -> "products/" + ownerId + "/variants";
            case REVIEW -> "reviews/" + ownerId;
        };
    }

    // Suppress unused warning for User/Store/Product/ProductVariant (chỉ dùng qua reflection trong helper)
    @SuppressWarnings("unused")
    private static final Class<?>[] KEEP_REFS = { User.class, Store.class, Product.class, ProductVariant.class, ProductReview.class };
}