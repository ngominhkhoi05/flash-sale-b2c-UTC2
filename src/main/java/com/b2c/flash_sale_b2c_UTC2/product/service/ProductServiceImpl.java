package com.b2c.flash_sale_b2c_UTC2.product.service;

import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.ActiveFlashSaleDto;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderItemRepository;
import com.b2c.flash_sale_b2c_UTC2.product.dto.CreateProductRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.CreateProductVariantRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductDetailResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductFilterRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductImageDto;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.RecommendedProductResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.RecommendedProductsResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductVariantResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.TierVariationConfigDto;
import com.b2c.flash_sale_b2c_UTC2.product.dto.UpdateProductRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.UpdateProductVariantRequest;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.enums.ProductSortOption;
import com.b2c.flash_sale_b2c_UTC2.product.exception.ProductErrorCode;
import com.b2c.flash_sale_b2c_UTC2.product.mapper.ProductVariantMapper;
import com.b2c.flash_sale_b2c_UTC2.product.repository.CategoryRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.product.spec.ProductSpecifications;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.b2c.flash_sale_b2c_UTC2.search.event.ProductIndexEvent;
import com.b2c.flash_sale_b2c_UTC2.store.exception.StoreErrorCode;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final StoreRepository storeRepository;
    private final OrderItemRepository orderItemRepository;
    private final FlashSaleItemRepository flashSaleItemRepository;
    private final ProductReviewRepository productReviewRepository;
    private final ProductVariantMapper productVariantMapper;
    private final ImageService imageService;

    private final StringRedisTemplate redisTemplate;

    /**
     * Publisher sự kiện đồng bộ index OpenSearch. Sự kiện được xử lý ở phase
     * AFTER_COMMIT nên không bao giờ dual-write trong transaction.
     */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ProductDetailResponse createProduct(Long userId, CreateProductRequest request) {
        Store store = getApprovedSellerStore(userId);

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));

        validateTierVariations(request.getTierVariationConfigs(), request.getVariants().size());
        validateCreateVariants(request.getVariants());

        Product product = Product.builder()
                .store(store)
                .category(category)
                .name(request.getName().trim())
                .description(request.getDescription())
                .tierVariationConfigs(request.getTierVariationConfigs())
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();

        Product savedProduct = productRepository.save(product);

        List<ProductVariant> variants = new ArrayList<>();
        for (CreateProductVariantRequest vReq : request.getVariants()) {
            ProductVariant variant = ProductVariant.builder()
                    .product(savedProduct)
                    .sku(vReq.getSku().trim())
                    .variantName(vReq.getVariantName().trim())
                    .attributes(vReq.getAttributes())
                    .originalPrice(vReq.getOriginalPrice())
                    .stockQuantity(vReq.getStockQuantity())
                    .status("ACTIVE")
                    .version(0L)
                    .createdAt(Instant.now())
                    .build();
            variants.add(variant);
        }

        List<ProductVariant> savedVariants = productVariantRepository.saveAll(variants);

        publishIndexEvent(ProductIndexEvent.upsert(savedProduct.getId()));

        return buildProductDetailResponse(savedProduct, savedVariants);
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProduct(Long userId, Long productId, UpdateProductRequest request) {
        Store store = getApprovedSellerStore(userId);

        Product product = productRepository.findByIdWithStoreAndCategory(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getStore().getId().equals(store.getId())) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ACCESS_DENIED);
        }

        if (!product.getCategory().getId().equals(request.getCategoryId())) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
            product.setCategory(category);
        }

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }

        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            validateTierVariations(request.getTierVariationConfigs(), request.getVariants().size());
            product.setTierVariationConfigs(request.getTierVariationConfigs());

            updateProductVariants(product, request.getVariants());
        }

        Product updatedProduct = productRepository.save(product);
        List<ProductVariant> currentVariants = productVariantRepository.findByProductId(updatedProduct.getId());

        publishIndexEvent(ProductIndexEvent.upsert(updatedProduct.getId()));

        return buildProductDetailResponse(updatedProduct, currentVariants);
    }

    private void updateProductVariants(Product product, List<UpdateProductVariantRequest> variantRequests) {
        Set<String> skuSet = new HashSet<>();
        for (UpdateProductVariantRequest vReq : variantRequests) {
            if (!skuSet.add(vReq.getSku().trim().toLowerCase())) {
                throw new BusinessException(ProductErrorCode.DUPLICATE_SKU_IN_REQUEST);
            }
            if (vReq.getOriginalPrice() == null || vReq.getOriginalPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ProductErrorCode.INVALID_PRODUCT_PRICE);
            }
            if (vReq.getStockQuantity() == null || vReq.getStockQuantity() < 0) {
                throw new BusinessException(ProductErrorCode.INVALID_STOCK_QUANTITY);
            }
        }

        List<ProductVariant> existingVariants = productVariantRepository.findByProductId(product.getId());
        Map<Long, ProductVariant> existingMap = existingVariants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<ProductVariant> toSave = new ArrayList<>();
        for (UpdateProductVariantRequest vReq : variantRequests) {
            if (vReq.getId() != null) {
                ProductVariant existing = existingMap.get(vReq.getId());
                if (existing == null) {
                    throw new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND);
                }

                if (productVariantRepository.existsBySkuAndIdNot(vReq.getSku().trim(), existing.getId())) {
                    throw new BusinessException(ProductErrorCode.SKU_ALREADY_EXISTS);
                }

                // Kiểm tra nếu biến thể đang trong Flash Sale ACTIVE thì cấm sửa giá hoặc tồn kho
                boolean inActiveFlashSale = flashSaleItemRepository.isVariantInActiveFlashSale(existing.getId());
                if (inActiveFlashSale) {
                    boolean priceChanged = existing.getOriginalPrice().compareTo(vReq.getOriginalPrice()) != 0;
                    boolean stockDecreased = vReq.getStockQuantity() < existing.getStockQuantity();
                    if (priceChanged || stockDecreased) {
                        throw new BusinessException(ProductErrorCode.VARIANT_IN_ACTIVE_FLASH_SALE);
                    }
                }

                existing.setSku(vReq.getSku().trim());
                existing.setVariantName(vReq.getVariantName().trim());
                existing.setAttributes(vReq.getAttributes());
                existing.setOriginalPrice(vReq.getOriginalPrice());
                existing.setStockQuantity(vReq.getStockQuantity());
                if (vReq.getStatus() != null) {
                    existing.setStatus(vReq.getStatus());
                }
                toSave.add(existing);
            } else {
                // Tạo mới biến thể bổ sung vào SPU
                if (productVariantRepository.existsBySku(vReq.getSku().trim())) {
                    throw new BusinessException(ProductErrorCode.SKU_ALREADY_EXISTS);
                }
                ProductVariant newVariant = ProductVariant.builder()
                        .product(product)
                        .sku(vReq.getSku().trim())
                        .variantName(vReq.getVariantName().trim())
                        .attributes(vReq.getAttributes())
                        .originalPrice(vReq.getOriginalPrice())
                        .stockQuantity(vReq.getStockQuantity())
                        .status(vReq.getStatus() != null ? vReq.getStatus() : "ACTIVE")
                        .version(0L)
                        .createdAt(Instant.now())
                        .build();
                toSave.add(newVariant);
            }
        }

        productVariantRepository.saveAll(toSave);
    }

    @Override
    @Transactional
    public void deleteProduct(Long userId, Long productId) {
        Store store = getApprovedSellerStore(userId);

        Product product = productRepository.findByIdWithStoreAndCategory(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getStore().getId().equals(store.getId())) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ACCESS_DENIED);
        }

        List<ProductVariant> variants = productVariantRepository.findByProductId(productId);
        List<Long> variantIds = variants.stream().map(ProductVariant::getId).toList();

        boolean hasOrders = !variantIds.isEmpty() && orderItemRepository.existsByVariantIdIn(variantIds);
        boolean hasFlashSales = !variantIds.isEmpty() && flashSaleItemRepository.existsByVariantIdIn(variantIds);

        if (hasOrders || hasFlashSales) {
            log.info("Sản phẩm ID {} hoặc các biến thể đã có đơn hàng/flash sale. Tiến hành Soft Delete (INACTIVE).", productId);
            product.setStatus("INACTIVE");
            productRepository.save(product);

            for (ProductVariant v : variants) {
                v.setStatus("INACTIVE");
            }
            productVariantRepository.saveAll(variants);
            publishIndexEvent(ProductIndexEvent.delete(productId));
        } else {
            log.info("Sản phẩm ID {} chưa phát sinh giao dịch. Tiến hành Hard Delete.", productId);
            productVariantRepository.deleteAll(variants);
            productRepository.delete(product);
            publishIndexEvent(ProductIndexEvent.delete(productId));
        }
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProductStatus(Long userId, Long productId, String status) {
        Store store = getApprovedSellerStore(userId);

        Product product = productRepository.findByIdWithStoreAndCategory(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getStore().getId().equals(store.getId())) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ACCESS_DENIED);
        }

        product.setStatus(status.toUpperCase());
        Product saved = productRepository.save(product);
        List<ProductVariant> variants = productVariantRepository.findByProductId(saved.getId());

        publishIndexEvent(ProductIndexEvent.upsert(saved.getId()));

        return buildProductDetailResponse(saved, variants);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> getPublicProducts(ProductFilterRequest filter, Pageable pageable) {
        ProductSortOption sortOption = filter.getSortOption() != null
                ? filter.getSortOption() : ProductSortOption.CREATED_AT_DESC;

        Page<Product> productPage;

        if (sortOption == ProductSortOption.PRICE_ASC || sortOption == ProductSortOption.PRICE_DESC) {
            // Price sort needs aggregate query — use dedicated methods
            int page = pageable.getPageNumber();
            int size = pageable.getPageSize();
            List<Product> products = (sortOption == ProductSortOption.PRICE_ASC)
                    ? productRepository.findAllOrderByMinPriceAsc(PageRequest.of(page, size))
                    : productRepository.findAllOrderByMinPriceDesc(PageRequest.of(page, size));
            productPage = new PageImpl<>(products, pageable, products.size());
        } else {
            Specification<Product> spec = ProductSpecifications.fromFilterRequest(filter);
            Sort sort = buildSort(sortOption, pageable);
            Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
            productPage = productRepository.findAll(spec, sortedPageable);
        }

        List<Long> productIds = productPage.getContent().stream().map(Product::getId).toList();
        Map<Long, List<ProductVariant>> variantsByProductId = productIds.isEmpty() ? Map.of() :
                productVariantRepository.findByProductIdInAndStatus(productIds, "ACTIVE").stream()
                        .collect(Collectors.groupingBy(v -> v.getProduct().getId()));

        // Batch-load primary images to avoid N+1
        Map<Long, String> primaryImageMap = productIds.isEmpty() ? Map.of() :
                imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.PRODUCT, productIds)
                        .entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> nullSafeUrl(e.getValue())));

        List<ProductSummaryResponse> content = productPage.getContent().stream()
                .map(p -> mapToSummaryResponse(p, variantsByProductId.getOrDefault(p.getId(), List.of()),
                        primaryImageMap.get(p.getId())))
                .toList();

        return PageResponse.of(productPage, content);
    }

    private Sort buildSort(ProductSortOption sortOption, Pageable pageable) {
        if (sortOption == null) {
            return pageable.getSort();
        }
        return switch (sortOption) {
            case CREATED_AT_ASC -> Sort.by(Sort.Direction.ASC, "createdAt");
            case PRICE_ASC, PRICE_DESC -> pageable.getSort(); // handled separately
            case SOLD_DESC -> Sort.by(Sort.Direction.DESC, "totalSold");
            case RATING_DESC -> Sort.by(Sort.Direction.DESC, "ratingAvg"); // Note: ratingAvg not on Product entity; handled by spec sort
            case CREATED_AT_DESC -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

        @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getPublicProductDetailBySlug(String slug) {
        Product product = productRepository.findBySlugWithStoreAndCategory(slug)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!"ACTIVE".equalsIgnoreCase(product.getStatus()) || !"APPROVED".equalsIgnoreCase(product.getStore().getStatus())) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }

        List<ProductVariant> activeVariants = productVariantRepository.findByProductIdAndStatus(product.getId(), "ACTIVE");
        return buildProductDetailResponse(product, activeVariants);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getPublicProductDetail(Long productId) {
        Product product = productRepository.findByIdWithStoreAndCategory(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!"ACTIVE".equalsIgnoreCase(product.getStatus()) || !"APPROVED".equalsIgnoreCase(product.getStore().getStatus())) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }

        List<ProductVariant> activeVariants = productVariantRepository.findByProductIdAndStatus(productId, "ACTIVE");

        return buildProductDetailResponse(product, activeVariants);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> getSellerProducts(Long userId, String status, Pageable pageable) {
        Store store = getApprovedSellerStore(userId);

        Page<Product> productPage = productRepository.findSellerProducts(store.getId(), status, pageable);

        List<Long> productIds = productPage.getContent().stream().map(Product::getId).toList();
        Map<Long, List<ProductVariant>> variantsByProductId = productIds.isEmpty() ? Map.of() :
                productVariantRepository.findByProductIdInAndStatus(productIds, "ACTIVE").stream()
                        .collect(Collectors.groupingBy(v -> v.getProduct().getId()));

        Map<Long, String> primaryImageMap = productIds.isEmpty() ? Map.of() :
                imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.PRODUCT, productIds)
                        .entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> nullSafeUrl(e.getValue())));

        List<ProductSummaryResponse> content = productPage.getContent().stream()
                .map(p -> mapToSummaryResponse(p, variantsByProductId.getOrDefault(p.getId(), List.of()),
                        primaryImageMap.get(p.getId())))
                .toList();

        return PageResponse.of(productPage, content);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getSellerProductDetail(Long userId, Long productId) {
        Store store = getApprovedSellerStore(userId);

        Product product = productRepository.findByIdWithStoreAndCategory(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getStore().getId().equals(store.getId())) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ACCESS_DENIED);
        }

        List<ProductVariant> allVariants = productVariantRepository.findByProductId(productId);

        return buildProductDetailResponse(product, allVariants);
    }

    private Store getApprovedSellerStore(Long userId) {
        Store store = storeRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        if (!"APPROVED".equalsIgnoreCase(store.getStatus())) {
            throw new BusinessException(ProductErrorCode.STORE_NOT_ELIGIBLE);
        }

        return store;
    }

    private void validateTierVariations(List<TierVariationConfigDto> configs, int variantCount) {
        if (configs == null || configs.isEmpty()) {
            if (variantCount != 1) {
                throw new BusinessException(ProductErrorCode.INVALID_TIER_VARIATION);
            }
            return;
        }

        int expectedCount = 1;
        for (TierVariationConfigDto config : configs) {
            if (config.getOptions() == null || config.getOptions().isEmpty()) {
                throw new BusinessException(ProductErrorCode.INVALID_TIER_VARIATION);
            }
            expectedCount *= config.getOptions().size();
        }

        if (variantCount != expectedCount) {
            log.warn("Số biến thể gửi lên ({}) không khớp với số biến thể dự kiến ({}) từ phân loại", variantCount, expectedCount);
            throw new BusinessException(ProductErrorCode.INVALID_TIER_VARIATION);
        }
    }

    private void validateCreateVariants(List<CreateProductVariantRequest> variantRequests) {
        Set<String> skuSet = new HashSet<>();
        for (CreateProductVariantRequest vReq : variantRequests) {
            String lowerSku = vReq.getSku().trim().toLowerCase();
            if (!skuSet.add(lowerSku)) {
                throw new BusinessException(ProductErrorCode.DUPLICATE_SKU_IN_REQUEST);
            }

            if (vReq.getOriginalPrice() == null || vReq.getOriginalPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ProductErrorCode.INVALID_PRODUCT_PRICE);
            }

            if (vReq.getStockQuantity() == null || vReq.getStockQuantity() < 0) {
                throw new BusinessException(ProductErrorCode.INVALID_STOCK_QUANTITY);
            }

            if (productVariantRepository.existsBySku(vReq.getSku().trim())) {
                throw new BusinessException(ProductErrorCode.SKU_ALREADY_EXISTS);
            }
        }
    }

    private ProductSummaryResponse mapToSummaryResponse(Product product, List<ProductVariant> variants, String preloadedImageUrl) {
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        int totalStock = 0;

        for (ProductVariant v : variants) {
            if (minPrice == null || v.getOriginalPrice().compareTo(minPrice) < 0) {
                minPrice = v.getOriginalPrice();
            }
            if (maxPrice == null || v.getOriginalPrice().compareTo(maxPrice) > 0) {
                maxPrice = v.getOriginalPrice();
            }
            totalStock += (v.getStockQuantity() != null ? v.getStockQuantity() : 0);
        }

        String imageUrl = preloadedImageUrl != null
                ? preloadedImageUrl
                : nullSafeUrl(imageService.getPrimaryImage(ImageOwnerType.PRODUCT, product.getId()));

        return ProductSummaryResponse.builder()
                .id(product.getId())
                .storeId(product.getStore().getId())
                .storeName(product.getStore().getStoreName())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .imageUrl(imageUrl)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .totalStock(totalStock)
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .build();
    }

    private ProductDetailResponse buildProductDetailResponse(Product product, List<ProductVariant> variants) {
        Long productId = product.getId();

        // 1 query cho ảnh primary của Product
        String productImageUrl = nullSafeUrl(
                imageService.getPrimaryImage(ImageOwnerType.PRODUCT, productId));

        // Từng variant: 1 query / variant (chấp nhận N+1 nhỏ trong context detail)
        // filterNullEntries prevents NPE in toMap when a variant has no image
        Map<Long, String> variantImageMap = buildVariantImageMap(variants);

        List<ProductVariantResponse> variantResponses = variants.stream()
                .map(v -> {
                    ProductVariantResponse r = productVariantMapper.toResponse(v);
                    r.setImageUrl(variantImageMap.get(v.getId()));
                    return r;
                })
                .toList();

        // ─── Subplan 2: populate 7 new fields ────────────────────────────────────────
        // 1. ratingAvg / ratingCount
        Double avgRating = productReviewRepository.avgRatingByProductId(productId);
        long ratingCount = productReviewRepository.countByProductIdAndVisible(productId);

        // 2. soldCount
        Integer soldCount = product.getTotalSold() != null ? product.getTotalSold() : 0;

        // 3. minPrice / maxPrice (tinh truc tiep tu variants ACTIVE da load - tranh sai so voi
        // findPriceRangeByProductId tra List<Object[]> gay ClassCastException o Hibernate multi-column)
        List<ProductVariant> activeVariants = variants.stream()
                .filter(v -> v.getStatus() != null && "ACTIVE".equalsIgnoreCase(v.getStatus()))
                .filter(v -> v.getOriginalPrice() != null)
                .toList();

        BigDecimal minPrice = BigDecimal.ZERO;
        BigDecimal maxPrice = BigDecimal.ZERO;
        if (!activeVariants.isEmpty()) {
            minPrice = activeVariants.stream()
                    .map(ProductVariant::getOriginalPrice)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            maxPrice = activeVariants.stream()
                    .map(ProductVariant::getOriginalPrice)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
        }

        // 4. discountPercent: find min-priced ACTIVE FlashSaleItem, compare to variant original price
        Integer discountPercent = null;
        List<com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem> activeItems =
                flashSaleItemRepository.findActiveByProductId(productId);
        if (!activeItems.isEmpty()) {
            // Get the item with lowest flash sale price
            var minFlashItem = activeItems.stream()
                    .min((a, b) -> a.getFlashSalePrice().compareTo(b.getFlashSalePrice()))
                    .orElse(null);
            if (minFlashItem != null) {
                BigDecimal originalPrice = minFlashItem.getVariant().getOriginalPrice();
                BigDecimal flashPrice = minFlashItem.getFlashSalePrice();
                if (originalPrice != null && originalPrice.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal percentBD = BigDecimal.valueOf(100)
                            .multiply(originalPrice.subtract(flashPrice))
                            .divide(originalPrice, 0, java.math.RoundingMode.HALF_UP);
                    discountPercent = percentBD.intValue();
                }
            }
        }

        // 5. images list
        List<ProductImageDto> images = imageService.listImages(ImageOwnerType.PRODUCT, productId)
                .stream()
                .map(img -> ProductImageDto.builder()
                        .id(img.getId())
                        .url(img.getUrl())
                        .isPrimary(img.getIsPrimary())
                        .displayOrder(img.getDisplayOrder())
                        .alt(product.getName())
                        .build())
                .toList();

        // Part A Subplan 4: populate active flash sales list
        List<ActiveFlashSaleDto> flashSales = buildActiveFlashSales(activeItems);

        // Empty over null guarantees for Frontend integration contract
        String safeSlug = product.getSlug() != null ? product.getSlug() : "";
        Object safeDescRich = product.getDescriptionRich() != null ? product.getDescriptionRich() : List.of();
        Object safeSpecs = product.getSpecs() != null ? product.getSpecs() : Map.of();
        Object safeFeatures = product.getFeatures() != null ? product.getFeatures() : List.of();
        Object safeCommitments = product.getCommitments() != null ? product.getCommitments() : List.of();
        Object safeSizeChart = product.getSizeChart() != null ? product.getSizeChart() : List.of();
        List<TierVariationConfigDto> safeConfigs = product.getTierVariationConfigs() != null
                ? product.getTierVariationConfigs() : List.of();

        Map<String, Object> storeProfileMap = new java.util.HashMap<>();
        storeProfileMap.put("id", product.getStore().getId());
        storeProfileMap.put("name", product.getStore().getStoreName());
        storeProfileMap.put("initials", product.getStore().getStoreName() != null && !product.getStore().getStoreName().isBlank()
                ? product.getStore().getStoreName().substring(0, 1).toUpperCase() : "S");
        storeProfileMap.put("isOfficial", "OFFICIAL_MALL".equalsIgnoreCase(String.valueOf(product.getStore().getTier())));
        storeProfileMap.put("ratingAvg", 0.0);
        storeProfileMap.put("ratingCount", 0);
        storeProfileMap.put("productCount", 0);
        storeProfileMap.put("responseRate", 100);
        storeProfileMap.put("responseTimeLabel", "Vài phút");
        storeProfileMap.put("lastActiveLabel", "Online vừa xong");

        Map<String, Object> shippingMap = Map.of(
                "freeshipBadge", "FREESHIP XTRA",
                "freeshipText", "Miễn phí vận chuyển",
                "shipTo", "Toàn quốc",
                "estimatedDays", "1 - 2 ngày"
        );

        return ProductDetailResponse.builder()
                .id(productId)
                .storeId(product.getStore().getId())
                .storeName(product.getStore().getStoreName())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .imageUrl(productImageUrl != null ? productImageUrl : "")
                .description(product.getDescription() != null ? product.getDescription() : "")
                .tierVariationConfigs(safeConfigs)
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .variants(variantResponses != null ? variantResponses : List.of())
                // new fields
                .ratingAvg(avgRating != null ? avgRating : 0.0)
                .ratingCount((int) ratingCount)
                .soldCount(soldCount != null ? soldCount : 0)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .discountPercent(discountPercent)
                .images(images != null ? images : List.of())
                .flashSales(flashSales != null ? flashSales : List.of())
                .slug(safeSlug)
                .descriptionRich(safeDescRich)
                .specs(safeSpecs)
                .features(safeFeatures)
                .commitments(safeCommitments)
                .sizeChart(safeSizeChart)
                .vouchers(List.of())
                .storeProfile(storeProfileMap)
                .shipping(shippingMap)
                .ratingDistribution(com.b2c.flash_sale_b2c_UTC2.review.dto.RatingDistributionDto.builder().build())
                .questionCount(0)
                .relatedSameStore(List.of())
                .relatedBestSellers(List.of())
                .build();
    }

    private String nullSafeUrl(com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse r) {
        return r == null ? null : r.getUrl();
    }

    private Map<Long, String> buildVariantImageMap(List<ProductVariant> variants) {
        Map<Long, String> result = new java.util.HashMap<>();
        for (ProductVariant v : variants) {
            String url = nullSafeUrl(imageService.getPrimaryImage(ImageOwnerType.VARIANT, v.getId()));
            if (url != null) {
                result.put(v.getId(), url);
            }
        }
        return result;
    }

    private static final String STOCK_KEY_PREFIX = "flash_sale:stock:";

    /**
     * Build ActiveFlashSaleDto list from FlashSaleItem entities.
     * Gets realtime stock from Redis with DB fallback.
     */
    private List<ActiveFlashSaleDto> buildActiveFlashSales(List<FlashSaleItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(item -> {
            int displayStock = getRealtimeStock(item);
            return ActiveFlashSaleDto.builder()
                    .slotId(item.getSlot().getId())
                    .slotStartTime(item.getSlot().getStartTime())
                    .slotEndTime(item.getSlot().getEndTime())
                    .slotStatus(item.getSlot().getStatus())
                    .itemId(item.getId())
                    .variantId(item.getVariant().getId())
                    .originalPrice(item.getVariant().getOriginalPrice())
                    .flashSalePrice(item.getFlashSalePrice())
                    .stockAvailable(displayStock)
                    .build();
        }).toList();
    }

    private int getRealtimeStock(FlashSaleItem item) {
        if (redisTemplate != null) {
            try {
                String stockVal = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + item.getId());
                if (stockVal != null) {
                    return Integer.parseInt(stockVal);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch Redis stock for item ID: {}, falling back to DB: {}",
                        item.getId(), e.getMessage());
            }
        }
        return java.util.Optional.ofNullable(item.getAvailableStock()).orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public RecommendedProductsResponse getRecommendedProducts(String tab, Long categoryId, int limit) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, limit);

        List<RecommendedProductResponse> forYou = buildRecommendedProducts(
                productRepository.findForYouProducts(pageable), "for_you");
        List<RecommendedProductResponse> bestSellers = buildRecommendedProducts(
                productRepository.findBestSellerProducts(pageable), "best_seller");
        List<RecommendedProductResponse> hotDeals = buildRecommendedHotDeals(limit);

        if (tab == null || "all".equalsIgnoreCase(tab)) {
            return RecommendedProductsResponse.builder()
                    .forYou(forYou).bestSellers(bestSellers).hotDeals(hotDeals).build();
        }
        return switch (tab.toLowerCase()) {
            case "for_you" -> RecommendedProductsResponse.builder().forYou(forYou).build();
            case "best_seller" -> RecommendedProductsResponse.builder().bestSellers(bestSellers).build();
            case "hot_deal" -> RecommendedProductsResponse.builder().hotDeals(hotDeals).build();
            default -> RecommendedProductsResponse.builder()
                    .forYou(forYou).bestSellers(bestSellers).hotDeals(hotDeals).build();
        };
    }

    private List<RecommendedProductResponse> buildRecommendedProducts(List<Product> products, String label) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, List<ProductVariant>> variantsMap = productVariantRepository
                .findByProductIdInAndStatus(productIds, "ACTIVE").stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getId()));
        Map<Long, String> imageMap = imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.PRODUCT, productIds)
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> nullSafeUrl(e.getValue())));

        return products.stream()
                .map(p -> mapToRecommended(p, variantsMap.getOrDefault(p.getId(), List.of()),
                        imageMap.get(p.getId()), label))
                .toList();
    }

    private List<RecommendedProductResponse> buildRecommendedHotDeals(int limit) {
        List<com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem> hotItems =
                flashSaleItemRepository.findHotDealItems(org.springframework.data.domain.PageRequest.of(0, limit));
        if (hotItems.isEmpty()) {
            return List.of();
        }
        return hotItems.stream().map(item -> {
            Product p = item.getVariant().getProduct();
            String imageUrl = nullSafeUrl(imageService.getPrimaryImage(ImageOwnerType.PRODUCT, p.getId()));
            return RecommendedProductResponse.builder()
                    .id(p.getId())
                    .storeId(p.getStore().getId())
                    .storeName(p.getStore().getStoreName())
                    .name(p.getName())
                    .imageUrl(imageUrl)
                    .minPrice(item.getFlashSalePrice())
                    .maxPrice(item.getFlashSalePrice())
                    .totalStock(item.getAvailableStock())
                    .totalSold(p.getTotalSold())
                    .label("hot_deal")
                    .build();
        }).toList();
    }

    private RecommendedProductResponse mapToRecommended(Product p, List<ProductVariant> variants,
                                                       String imageUrl, String label) {
        BigDecimal minPrice = null, maxPrice = null;
        int totalStock = 0;
        for (ProductVariant v : variants) {
            if (minPrice == null || v.getOriginalPrice().compareTo(minPrice) < 0) minPrice = v.getOriginalPrice();
            if (maxPrice == null || v.getOriginalPrice().compareTo(maxPrice) > 0) maxPrice = v.getOriginalPrice();
            totalStock += (v.getStockQuantity() != null ? v.getStockQuantity() : 0);
        }
        return RecommendedProductResponse.builder()
                .id(p.getId())
                .storeId(p.getStore().getId())
                .storeName(p.getStore().getStoreName())
                .name(p.getName())
                .imageUrl(imageUrl)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .totalStock(totalStock)
                .totalSold(p.getTotalSold())
                .label(label)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<com.b2c.flash_sale_b2c_UTC2.product.dto.AdminProductResponse> getAdminProducts(
            String status,
            Integer categoryId,
            String search,
            Pageable pageable
    ) {
        String trimmedStatus = (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status.trim()))
                ? status.trim().toUpperCase()
                : null;
        Integer safeCategoryId = (categoryId != null && categoryId > 0) ? categoryId : null;
        String trimmedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Page<Product> page = productRepository.findAdminProducts(trimmedStatus, safeCategoryId, trimmedSearch, pageable);
        if (page.isEmpty()) {
            return PageResponse.of(page, java.util.Collections.emptyList());
        }

        List<Long> productIds = page.getContent().stream().map(Product::getId).toList();

        // 1. Batch load variants
        List<ProductVariant> variants = productVariantRepository.findByProductIdIn(productIds);
        Map<Long, List<ProductVariant>> variantMap = variants.stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getId()));

        // 2. Batch load primary images
        Map<Long, com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse> imageMap =
                imageService.getPrimaryImagesByOwnerIds(com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType.PRODUCT, productIds);

        List<com.b2c.flash_sale_b2c_UTC2.product.dto.AdminProductResponse> content = page.getContent().stream().map(p -> {
            Long pid = p.getId();
            List<ProductVariant> pVariants = variantMap.getOrDefault(pid, java.util.Collections.emptyList());
            BigDecimal minPrice = null, maxPrice = null;
            int totalStock = 0;
            for (ProductVariant v : pVariants) {
                if (v.getOriginalPrice() != null) {
                    if (minPrice == null || v.getOriginalPrice().compareTo(minPrice) < 0) minPrice = v.getOriginalPrice();
                    if (maxPrice == null || v.getOriginalPrice().compareTo(maxPrice) > 0) maxPrice = v.getOriginalPrice();
                }
                totalStock += (v.getStockQuantity() != null ? v.getStockQuantity() : 0);
            }
            if (minPrice == null) minPrice = BigDecimal.ZERO;
            if (maxPrice == null) maxPrice = BigDecimal.ZERO;

            var img = imageMap.get(pid);
            String imgUrl = img != null ? img.getUrl() : null;

            return com.b2c.flash_sale_b2c_UTC2.product.dto.AdminProductResponse.builder()
                    .id(pid)
                    .name(p.getName())
                    .slug(p.getSlug())
                    .storeId(p.getStore() != null ? p.getStore().getId() : null)
                    .storeName(p.getStore() != null ? p.getStore().getStoreName() : "")
                    .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                    .categoryName(p.getCategory() != null ? p.getCategory().getName() : "")
                    .minPrice(minPrice)
                    .maxPrice(maxPrice)
                    .totalStock(totalStock)
                    .status(p.getStatus())
                    .primaryImageUrl(imgUrl)
                    .imageUrl(imgUrl)
                    .createdAt(p.getCreatedAt())
                    .build();
        }).toList();

        return PageResponse.of(page, content);
    }

    @Override
    @Transactional
    public com.b2c.flash_sale_b2c_UTC2.product.dto.AdminProductResponse updateProductStatusForAdmin(
            Long productId,
            com.b2c.flash_sale_b2c_UTC2.product.dto.UpdateProductStatusRequest request
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        String newStatus = request.getStatus().trim().toUpperCase();
        if (!"ACTIVE".equals(newStatus) && !"INACTIVE".equals(newStatus) && !"BANNED".equals(newStatus)) {
            throw new BusinessException(ProductErrorCode.INVALID_STATUS);
        }

        product.setStatus(newStatus);
        product = productRepository.save(product);
        log.info("Admin đã cập nhật trạng thái sản phẩm id={} thành {}, lý do: {}", productId, newStatus, request.getReason());

        publishIndexEvent(ProductIndexEvent.upsert(productId));

        List<ProductVariant> variants = productVariantRepository.findByProductId(productId);
        BigDecimal minPrice = null, maxPrice = null;
        int totalStock = 0;
        for (ProductVariant v : variants) {
            if (v.getOriginalPrice() != null) {
                if (minPrice == null || v.getOriginalPrice().compareTo(minPrice) < 0) minPrice = v.getOriginalPrice();
                if (maxPrice == null || v.getOriginalPrice().compareTo(maxPrice) > 0) maxPrice = v.getOriginalPrice();
            }
            totalStock += (v.getStockQuantity() != null ? v.getStockQuantity() : 0);
        }
        if (minPrice == null) minPrice = BigDecimal.ZERO;
        if (maxPrice == null) maxPrice = BigDecimal.ZERO;

        var img = imageService.getPrimaryImage(com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType.PRODUCT, productId);
        String imgUrl = img != null ? img.getUrl() : null;

        return com.b2c.flash_sale_b2c_UTC2.product.dto.AdminProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .storeId(product.getStore() != null ? product.getStore().getId() : null)
                .storeName(product.getStore() != null ? product.getStore().getStoreName() : "")
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : "")
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .totalStock(totalStock)
                .status(product.getStatus())
                .primaryImageUrl(imgUrl)
                .imageUrl(imgUrl)
                .createdAt(product.getCreatedAt())
                .build();
    }

    /**
     * Publish sự kiện đồng bộ index OpenSearch.
     *
     * <p>Sự kiện được xử lý ở phase AFTER_COMMIT bởi
     * {@code ProductIndexEventListener} — không gọi OpenSearch trong transaction
     * để tránh dual-write hazard (transaction rollback nhưng document đã index).
     */
    private void publishIndexEvent(ProductIndexEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Không publish được sự kiện đồng bộ index: {}", ex.getMessage());
        }
    }
}
