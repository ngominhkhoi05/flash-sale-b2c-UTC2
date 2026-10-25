package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.common.exception.CommonErrorCode;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.product.dto.RecommendedProductResponse;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.client.ImageEmbeddingGateway;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.config.ImageSearchProperties;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.dto.ImageSearchProductResponse;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.dto.ImageSearchResult;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.repository.ImageEmbeddingRepository;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.repository.ProductSimilarityProjection;
import com.b2c.flash_sale_b2c_UTC2.search.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageSearchServiceImpl implements ImageSearchService {

    private final ImageEmbeddingGateway imageEmbeddingGateway;
    private final ImageEmbeddingRepository imageEmbeddingRepository;
    private final ProductSearchRepository productSearchRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ImageService imageService;
    private final ImageSearchProperties properties;

    @Override
    @Transactional(readOnly = true)
    public ImageSearchResult searchByImage(MultipartFile file, Integer limit) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        try {
            byte[] bytes = file.getBytes();
            List<Float> vector = imageEmbeddingGateway.embedImage(bytes, file.getOriginalFilename(), contentType);
            return executeVectorSearch(vector, limit);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("[ImageSearchServiceImpl] Loi khi doc file upload: {}", e.getMessage());
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ImageSearchResult searchByImageUrl(String imageUrl, Integer limit) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        List<Float> vector = imageEmbeddingGateway.embedUrl(imageUrl);
        return executeVectorSearch(vector, limit);
    }

    private ImageSearchResult executeVectorSearch(List<Float> vector, Integer requestedLimit) {
        long startTime = System.currentTimeMillis();
        int limit = (requestedLimit != null && requestedLimit > 0)
                ? Math.min(requestedLimit, 50)
                : properties.getDefaultLimit();

        String vectorStr = vector.toString();
        List<ProductSimilarityProjection> projections = imageEmbeddingRepository.findSimilarProducts(
                vectorStr,
                properties.getMaxDistance(),
                limit
        );

        if (projections.isEmpty()) {
            return ImageSearchResult.builder()
                    .items(List.of())
                    .total(0)
                    .searchDurationMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        List<Long> productIds = new ArrayList<>(projections.size());
        Map<Long, Double> distanceMap = new HashMap<>(projections.size());
        for (ProductSimilarityProjection proj : projections) {
            Long pid = proj.getProductId();
            if (pid != null) {
                productIds.add(pid);
                distanceMap.put(pid, proj.getDistance() != null ? proj.getDistance() : 0.0);
            }
        }

        // Hydrate thông tin sản phẩm đầy đủ (Defense layer: Search-then-Hydrate)
        Map<Long, ProductSearchRepository.PriceRange> priceRanges = loadPriceRanges(productIds);
        Map<Long, String> images = loadPrimaryImages(productIds);
        List<RecommendedProductResponse> hydratedProducts = productSearchRepository.hydrate(
                productIds, priceRanges, images
        );

        List<ImageSearchProductResponse> items = new ArrayList<>(hydratedProducts.size());
        for (RecommendedProductResponse product : hydratedProducts) {
            double distance = distanceMap.getOrDefault(product.getId(), 0.0);
            // Cosine distance từ 0.0 đến 2.0 (với normalized vectors, khoảng cách thường 0.0 đến ~0.4)
            double similarity = Math.max(0.0, Math.min(1.0, 1.0 - distance));
            items.add(ImageSearchProductResponse.builder()
                    .product(product)
                    .similarityScore(Math.round(similarity * 1000.0) / 1000.0)
                    .distance(Math.round(distance * 1000.0) / 1000.0)
                    .build());
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("[ImageSearchServiceImpl] Tim thay {} san pham tuong dong trong {}ms", items.size(), durationMs);

        return ImageSearchResult.builder()
                .items(items)
                .total(items.size())
                .searchDurationMs(durationMs)
                .build();
    }

    private Map<Long, ProductSearchRepository.PriceRange> loadPriceRanges(List<Long> ids) {
        Map<Long, ProductSearchRepository.PriceRange> result = new HashMap<>();
        for (Object[] row : productVariantRepository.aggregatePriceAndStockByProductIds(ids)) {
            Long productId = (Long) row[0];
            BigDecimal min = (BigDecimal) row[1];
            BigDecimal max = (BigDecimal) row[2];
            Number stock = (Number) row[3];
            result.put(productId, new ProductSearchRepository.PriceRange(
                    min != null ? min : BigDecimal.ZERO,
                    max != null ? max : BigDecimal.ZERO,
                    stock != null ? stock.intValue() : 0));
        }
        return result;
    }

    private Map<Long, String> loadPrimaryImages(List<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.PRODUCT, ids)
                .forEach((id, image) -> {
                    if (image != null && image.getUrl() != null) {
                        result.put(id, image.getUrl());
                    }
                });
        return result;
    }
}
