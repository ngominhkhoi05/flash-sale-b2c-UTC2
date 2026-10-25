package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.common.exception.CommonErrorCode;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.product.dto.RecommendedProductResponse;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.client.ImageEmbeddingGateway;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.config.ImageSearchProperties;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.dto.ImageSearchResult;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.repository.ImageEmbeddingRepository;
import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.repository.ProductSimilarityProjection;
import com.b2c.flash_sale_b2c_UTC2.search.repository.ProductSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageSearchServiceImplTest {

    @Mock
    private ImageEmbeddingGateway imageEmbeddingGateway;

    @Mock
    private ImageEmbeddingRepository imageEmbeddingRepository;

    @Mock
    private ProductSearchRepository productSearchRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private ImageSearchProperties properties;

    @InjectMocks
    private ImageSearchServiceImpl imageSearchService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(properties.getDefaultLimit()).thenReturn(20);
        org.mockito.Mockito.lenient().when(properties.getMaxDistance()).thenReturn(0.38);
    }

    @Test
    @DisplayName("searchByImage: ném lỗi khi file rỗng")
    void searchByImage_emptyFile_throwsInvalidRequest() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> imageSearchService.searchByImage(emptyFile, 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("searchByImage: ném lỗi khi định dạng không phải ảnh")
    void searchByImage_nonImageFile_throwsInvalidRequest() {
        MockMultipartFile textFile = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> imageSearchService.searchByImage(textFile, 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("searchByImage: thành công và tính đúng điểm tương đồng")
    void searchByImage_success() {
        byte[] imageBytes = new byte[]{1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("file", "shoe.jpg", "image/jpeg", imageBytes);

        List<Float> dummyVector = List.of(0.1f, 0.2f, 0.3f);
        when(imageEmbeddingGateway.embedImage(eq(imageBytes), eq("shoe.jpg"), eq("image/jpeg")))
                .thenReturn(dummyVector);

        ProductSimilarityProjection proj = new ProductSimilarityProjection() {
            @Override
            public Long getProductId() {
                return 100L;
            }

            @Override
            public Double getDistance() {
                return 0.15; // Cosine distance = 0.15 -> similarity = 0.85 (85%)
            }
        };

        when(imageEmbeddingRepository.findSimilarProducts(eq(dummyVector.toString()), eq(0.38), eq(20)))
                .thenReturn(List.of(proj));

        when(productVariantRepository.aggregatePriceAndStockByProductIds(List.of(100L)))
                .thenReturn(List.<Object[]>of(new Object[]{100L, BigDecimal.valueOf(50000), BigDecimal.valueOf(100000), 50}));

        when(imageService.getPrimaryImagesByOwnerIds(eq(ImageOwnerType.PRODUCT), eq(List.of(100L))))
                .thenReturn(Map.of());

        RecommendedProductResponse productResp = RecommendedProductResponse.builder()
                .id(100L)
                .name("Giày Sneaker Vibe")
                .minPrice(BigDecimal.valueOf(50000))
                .maxPrice(BigDecimal.valueOf(100000))
                .build();

        when(productSearchRepository.hydrate(eq(List.of(100L)), any(), any()))
                .thenReturn(List.of(productResp));

        ImageSearchResult result = imageSearchService.searchByImage(file, null);

        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getProduct().getId()).isEqualTo(100L);
        assertThat(result.getItems().get(0).getSimilarityScore()).isEqualTo(0.85);
        assertThat(result.getItems().get(0).getDistance()).isEqualTo(0.15);
    }

    @Test
    @DisplayName("searchByImageUrl: thành công khi tìm qua link")
    void searchByImageUrl_success() {
        String url = "https://example.com/dress.jpg";
        List<Float> dummyVector = List.of(0.5f, 0.5f);
        when(imageEmbeddingGateway.embedUrl(url)).thenReturn(dummyVector);
        when(imageEmbeddingRepository.findSimilarProducts(anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of());

        ImageSearchResult result = imageSearchService.searchByImageUrl(url, 10);

        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getItems()).isEmpty();
    }
}
