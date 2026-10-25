package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.client;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.search.exception.SearchErrorCode;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.client.dto.BatchEmbeddingResult;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.config.ImageSearchProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RealImageEmbeddingGateway implements ImageEmbeddingGateway {

    private final RestClient restClient;
    private final ImageSearchProperties properties;

    public RealImageEmbeddingGateway(ImageSearchProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());

        this.restClient = RestClient.builder()
                .baseUrl(properties.getEmbeddingServiceUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Float> embedImage(byte[] imageBytes, String filename, String contentType) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return filename != null ? filename : "image.jpg";
                }
            };
            body.add("file", fileResource);

            Map<String, Object> response = restClient.post()
                    .uri("/embed-image")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.get("vector") instanceof List<?> list) {
                return ((List<Number>) list).stream()
                        .map(Number::floatValue)
                        .toList();
            }
            throw new BusinessException(SearchErrorCode.SEARCH_INDEX_UNAVAILABLE);
        } catch (Exception e) {
            log.error("[RealImageEmbeddingGateway] embedImage failed: {}", e.getMessage());
            throw new BusinessException(SearchErrorCode.SEARCH_INDEX_UNAVAILABLE);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Float> embedUrl(String imageUrl) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/embed-url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("url", imageUrl))
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.get("vector") instanceof List<?> list) {
                return ((List<Number>) list).stream()
                        .map(Number::floatValue)
                        .toList();
            }
            throw new BusinessException(SearchErrorCode.SEARCH_INDEX_UNAVAILABLE);
        } catch (Exception e) {
            log.error("[RealImageEmbeddingGateway] embedUrl failed for {}: {}", imageUrl, e.getMessage());
            throw new BusinessException(SearchErrorCode.SEARCH_INDEX_UNAVAILABLE);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<BatchEmbeddingResult> embedBatchUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/embed-batch-urls")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("urls", imageUrls))
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.get("results") instanceof List<?> rawResults) {
                return rawResults.stream()
                        .filter(item -> item instanceof Map)
                        .map(item -> {
                            Map<String, Object> map = (Map<String, Object>) item;
                            String url = (String) map.get("url");
                            boolean success = Boolean.TRUE.equals(map.get("success"));
                            List<Float> vector = null;
                            if (map.get("vector") instanceof List<?> vList) {
                                vector = ((List<Number>) vList).stream().map(Number::floatValue).toList();
                            }
                            String error = (String) map.get("error");
                            return new BatchEmbeddingResult(url, success, vector, error);
                        })
                        .toList();
            }
            return List.of();
        } catch (Exception e) {
            log.error("[RealImageEmbeddingGateway] embedBatchUrls failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            Map<?, ?> resp = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);
            return resp != null && "UP".equalsIgnoreCase(String.valueOf(resp.get("status")));
        } catch (Exception e) {
            log.warn("[RealImageEmbeddingGateway] AI service health check failed: {}", e.getMessage());
            return false;
        }
    }
}
