package com.b2c.flash_sale_b2c_UTC2.search.listener;

import com.b2c.flash_sale_b2c_UTC2.search.event.ProductIndexEvent;
import com.b2c.flash_sale_b2c_UTC2.search.event.ProductIndexEventType;
import com.b2c.flash_sale_b2c_UTC2.search.service.ProductIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Đồng bộ index OpenSearch sau khi transaction DB commit thành công.
 *
 * <p>Dùng {@code @TransactionalEventListener(phase = AFTER_COMMIT)} — listener chỉ
 * chạy SAU khi transaction commit. Nhờ vậy không bao giờ xảy ra trường hợp
 * "transaction rollback nhưng document đã index" (dual-write hazard).
 *
 * <p>Lỗi OpenSearch KHÔNG được ném ra (nuốt + log) để không ảnh hưởng luồng
 * chính của người bán; reconcile định kỳ sẽ đồng bộ lại các bản ghi bị lệch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIndexEventListener {

    private final ProductIndexer productIndexer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductIndexEvent(ProductIndexEvent event) {
        if (event == null || event.productId() == null) {
            return;
        }
        try {
            if (event.type() == ProductIndexEventType.DELETE) {
                productIndexer.removeProduct(event.productId());
                log.info("[ProductIndexEventListener] đã xoá document productId={}", event.productId());
                return;
            }
            boolean indexed = productIndexer.indexProduct(event.productId());
            log.info("[ProductIndexEventListener] đã đồng bộ productId={}, indexed={}",
                    event.productId(), indexed);
        } catch (Exception ex) {
            log.warn("[ProductIndexEventListener] đồng bộ index thất bại productId={}, error={}",
                    event.productId(), ex.getMessage());
        }
    }
}
