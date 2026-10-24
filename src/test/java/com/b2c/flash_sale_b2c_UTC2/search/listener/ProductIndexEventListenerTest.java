package com.b2c.flash_sale_b2c_UTC2.search.listener;

import com.b2c.flash_sale_b2c_UTC2.search.event.ProductIndexEvent;
import com.b2c.flash_sale_b2c_UTC2.search.service.ProductIndexer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductIndexEventListenerTest {

    @Mock
    private ProductIndexer productIndexer;

    @InjectMocks
    private ProductIndexEventListener listener;

    @Test
    @DisplayName("onProductIndexEvent: event UPSERT -> index lại sản phẩm")
    void onProductIndexEvent_upsert_indexesProduct() {
        listener.onProductIndexEvent(ProductIndexEvent.upsert(7L));
        verify(productIndexer).indexProduct(7L);
    }

    @Test
    @DisplayName("onProductIndexEvent: event DELETE -> xoá document")
    void onProductIndexEvent_delete_removesProduct() {
        listener.onProductIndexEvent(ProductIndexEvent.delete(7L));
        verify(productIndexer).removeProduct(7L);
        verify(productIndexer, never()).indexProduct(anyLong());
    }

    @Test
    @DisplayName("onProductIndexEvent: event null -> không làm gì")
    void onProductIndexEvent_null_isNoOp() {
        listener.onProductIndexEvent(null);
        verifyNoInteractions(productIndexer);
    }

    @Test
    @DisplayName("onProductIndexEvent: productId null -> không làm gì")
    void onProductIndexEvent_nullProductId_isNoOp() {
        listener.onProductIndexEvent(new ProductIndexEvent(null,
                com.b2c.flash_sale_b2c_UTC2.search.event.ProductIndexEventType.UPSERT));
        verifyNoInteractions(productIndexer);
    }

    @Test
    @DisplayName("onProductIndexEvent: indexer ném lỗi -> nuốt lỗi, không propagate")
    void onProductIndexEvent_indexerThrows_doesNotPropagate() {
        org.mockito.Mockito.doThrow(new RuntimeException("os down"))
                .when(productIndexer).indexProduct(7L);

        listener.onProductIndexEvent(ProductIndexEvent.upsert(7L));

        verify(productIndexer).indexProduct(7L);
    }
}
