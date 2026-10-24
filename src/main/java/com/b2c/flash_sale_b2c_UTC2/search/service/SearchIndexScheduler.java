package com.b2c.flash_sale_b2c_UTC2.search.service;

import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled job đồng bộ định kỳ index OpenSearch (reconcile).
 *
 * <p>Chạy song song với cơ chế event {@code AFTER_COMMIT}: event xử lý phần lớn
 * các thay đổi tức thời, reconcile bắt lại các bản ghi bị lệch (event thất bại,
 * index bị xoá tay, DB được sửa ngoài luồng ứng dụng).
 *
 * <p><b>Idempotent</b> — bulk upsert ghi đè theo {@code _id}; chạy lại nhiều lần
 * cho kết quả giống nhau. An toàn khi chạy trên nhiều instance (không cần
 * khoá vì thao tác ghi đè là idempotent).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexScheduler {

    /** Kích thước batch khi quét sản phẩm thay đổi. */
    private static final int RECONCILE_BATCH_SIZE = 200;

    /** Kích thước batch khi full-reindex. */
    private static final int FULL_REINDEX_BATCH_SIZE = 500;

    /** Cửa sổ thời gian quét lại (bù cho trường hợp sản phẩm vừa được tạo). */
    private static final Duration OVERLAP_WINDOW = Duration.ofMinutes(5);

    private final ProductRepository productRepository;
    private final ProductIndexer productIndexer;

    /** Cờ bật/tắt reconcile định kỳ (tắt được khi deploy lần đầu). */
    @Value("${app.scheduler.search-reconcile-enabled:true}")
    private boolean reconcileEnabled;

    /**
     * Reconcile định kỳ: quét sản phẩm thay đổi trong cửa sổ thời gian gần đây
     * và index lại (idempotent).
     */
    @Scheduled(fixedDelayString = "${app.scheduler.search-reindex-delay:600000}")
    @Transactional(readOnly = true)
    public void reconcile() {
        if (!reconcileEnabled) {
            log.debug("[SearchIndexScheduler] reconcile bị tắt qua cấu hình");
            return;
        }
        long start = System.currentTimeMillis();
        Instant since = Instant.now().minus(OVERLAP_WINDOW);
        int total = 0;
        int rounds = 0;
        try {
            while (true) {
                List<Product> batch = productRepository.findActiveUpdatedAfter(
                        since, PageRequest.of(0, RECONCILE_BATCH_SIZE));
                if (batch.isEmpty()) {
                    break;
                }
                total += productIndexer.indexProducts(batch.stream().map(Product::getId).toList());
                rounds++;
                if (batch.size() < RECONCILE_BATCH_SIZE) {
                    break;
                }
                // Dịch mốc quét theo batch cuối để không lặp vô hạn trên cùng dữ liệu.
                since = batch.get(batch.size() - 1).getUpdatedAt();
                if (rounds > 100) {
                    log.warn("[SearchIndexScheduler] dừng reconcile sau {} batch để tránh vòng lặp", rounds);
                    break;
                }
            }
            log.info("[SearchIndexScheduler] reconcile xong: {} sản phẩm, {} batch, elapsed={}ms",
                    total, rounds, System.currentTimeMillis() - start);
        } catch (Exception ex) {
            log.warn("[SearchIndexScheduler] reconcile thất bại: {}", ex.getMessage());
        }
    }

    /**
     * Full-reindex: index lại toàn bộ sản phẩm ACTIVE từ đầu.
     *
     * <p>Idempotent — chạy lại vẫn cho index đúng. Dùng cho bootstrap lần đầu
     * hoặc sau khi đổi mapping.
     *
     * @return số document đã index.
     */
    @Transactional(readOnly = true)
    public int fullReindex() {
        long start = System.currentTimeMillis();
        int total = 0;
        int page = 0;
        try {
            while (true) {
                List<Product> batch = productRepository.findAllActiveWithStoreAndCategory(
                        PageRequest.of(page, FULL_REINDEX_BATCH_SIZE));
                if (batch.isEmpty()) {
                    break;
                }
                total += productIndexer.indexProducts(batch.stream().map(Product::getId).toList());
                page++;
                if (batch.size() < FULL_REINDEX_BATCH_SIZE) {
                    break;
                }
            }
            log.info("[SearchIndexScheduler] full reindex xong: {} sản phẩm, elapsed={}ms",
                    total, System.currentTimeMillis() - start);
        } catch (Exception ex) {
            log.warn("[SearchIndexScheduler] full reindex thất bại sau {} sản phẩm: {}",
                    total, ex.getMessage());
        }
        return total;
    }
}
