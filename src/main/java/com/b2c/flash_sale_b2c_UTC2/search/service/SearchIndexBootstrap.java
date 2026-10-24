package com.b2c.flash_sale_b2c_UTC2.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Bootstrap index OpenSearch khi ứng dụng khởi động.
 *
 * <p>Gọi {@link SearchIndexService#ensureIndexExists()} — idempotent nên an toàn
 * chạy mỗi lần boot (không tạo lại index đã có, không mất dữ liệu).
 *
 * <p>Không chặn startup: nếu OpenSearch chưa sẵn sàng, log warning và để search
 * chạy nhánh fallback PostgreSQL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexBootstrap {

    private final SearchIndexService searchIndexService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        boolean created = searchIndexService.ensureIndexExists();
        log.info("[SearchIndexBootstrap] index bootstrap done (created={})", created);
    }
}
