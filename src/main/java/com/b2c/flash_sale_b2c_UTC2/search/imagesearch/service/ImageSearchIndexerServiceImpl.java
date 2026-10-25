package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.service;

import com.b2c.flash_sale_b2c_UTC2.image.entity.Image;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.repository.ImageRepository;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.client.ImageEmbeddingGateway;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.client.dto.BatchEmbeddingResult;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.config.ImageSearchProperties;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.dto.ImageReindexResponse;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.repository.ImageEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageSearchIndexerServiceImpl implements ImageSearchIndexerService {

    private final ImageRepository imageRepository;
    private final ImageEmbeddingRepository imageEmbeddingRepository;
    private final ImageEmbeddingGateway imageEmbeddingGateway;
    private final ImageSearchProperties properties;

    @Override
    public ImageReindexResponse reindexAllProductImages() {
        long startTime = System.currentTimeMillis();
        int batchSize = properties.getReindexBatchSize();
        int pageNumber = 0;
        int totalScanned = 0;
        int successCount = 0;
        int failedCount = 0;

        log.info("[ImageSearchIndexer] Bat dau qua trinh reindex toan bo anh san pham...");

        while (true) {
            Page<Image> page = imageRepository.findImagesForIndexing(
                    ImageOwnerType.PRODUCT,
                    true, // Uu tien anh primary truoc
                    PageRequest.of(pageNumber, batchSize)
            );

            List<Image> images = page.getContent();
            if (images.isEmpty()) {
                break;
            }

            totalScanned += images.size();
            List<String> urls = images.stream().map(Image::getUrl).toList();

            try {
                List<BatchEmbeddingResult> results = imageEmbeddingGateway.embedBatchUrls(urls);
                Map<String, BatchEmbeddingResult> resultMap = results.stream()
                        .collect(Collectors.toMap(BatchEmbeddingResult::url, r -> r, (r1, r2) -> r1));

                for (Image image : images) {
                    BatchEmbeddingResult item = resultMap.get(image.getUrl());
                    if (item != null && item.success() && item.vector() != null) {
                        try {
                            String vectorStr = item.vector().toString();
                            imageEmbeddingRepository.upsertEmbedding(image.getId(), image.getOwnerId(), vectorStr);
                            successCount++;
                        } catch (Exception e) {
                            log.error("[ImageSearchIndexer] Loi khi luu vector cho imageId={}: {}",
                                    image.getId(), e.getMessage());
                            failedCount++;
                        }
                    } else {
                        log.warn("[ImageSearchIndexer] Khong the lay vector cho anh imageId={}, url={}: {}",
                                image.getId(), image.getUrl(), item != null ? item.error() : "No result");
                        failedCount++;
                    }
                }
            } catch (Exception e) {
                log.error("[ImageSearchIndexer] Loi khi goi batch embedding cho trang {}: {}", pageNumber, e.getMessage());
                failedCount += images.size();
            }

            if (!page.hasNext()) {
                break;
            }
            pageNumber++;
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("[ImageSearchIndexer] Hoan tat reindex anh san pham: scanned={}, success={}, failed={}, duration={}ms",
                totalScanned, successCount, failedCount, durationMs);

        return new ImageReindexResponse(totalScanned, successCount, failedCount, durationMs);
    }

    @Override
    @Async
    public void indexProductImage(Long imageId, Long productId, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        try {
            List<Float> vector = imageEmbeddingGateway.embedUrl(imageUrl);
            if (vector != null && !vector.isEmpty()) {
                imageEmbeddingRepository.upsertEmbedding(imageId, productId, vector.toString());
                log.info("[ImageSearchIndexer] Da cap nhat vector cho san pham id={}, imageId={}", productId, imageId);
            }
        } catch (Exception e) {
            log.warn("[ImageSearchIndexer] Khong the tao vector cho san pham id={}, imageId={}: {}",
                    productId, imageId, e.getMessage());
        }
    }
}
