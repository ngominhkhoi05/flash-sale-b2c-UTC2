package com.b2c.flash_sale_b2c_UTC2.search.service;

import com.b2c.flash_sale_b2c_UTC2.search.client.OpenSearchProperties;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchIndexDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsAliasRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.PutAliasRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Quản lý index OpenSearch: bootstrap index + alias + mapping, và bulk upsert document.
 *
 * <p>Bootstrap là <b>idempotent</b>: nếu index đã tồn tại thì không tạo lại, chỉ
 * đảm bảo alias trỏ đúng. Nhờ vậy có thể gọi ở mỗi lần khởi động app mà không
 * mất dữ liệu.
 *
 * <p>Index thật: {@code products_v1}. Alias: {@code products} — luôn query alias,
 * không query index thật (alias cho phép reindex không downtime).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {

    /** Tên index thật (alias trỏ tới index này). */
    public static final String INDEX_NAME = "products_v1";

    /** Analyzer giữ nguyên dấu (chỉ lowercase). */
    public static final String ANALYZER_EXACT = "vi_exact";

    /** Analyzer bỏ dấu tiếng Việt qua {@code icu_folding}. */
    public static final String ANALYZER_FOLDED = "vi_folded";

    /** Số item lỗi tối đa được log chi tiết trong 1 lần bulk (tránh spam log). */
    private static final int MAX_LOGGED_FAILED_ITEMS = 5;

    private final OpenSearchClient client;
    private final OpenSearchProperties properties;

    /**
     * Đảm bảo index + alias tồn tại. Idempotent — an toàn gọi mỗi lần boot.
     *
     * @return {@code true} nếu index vừa được tạo, {@code false} nếu đã tồn tại.
     */
    public boolean ensureIndexExists() {
        String alias = properties.getIndexAlias();
        try {
            boolean indexExists = client.indices()
                    .exists(ExistsRequest.of(b -> b.index(INDEX_NAME)))
                    .value();
            if (!indexExists) {
                createIndexWithAlias(alias);
                log.info("[SearchIndexService] created index={} with alias={}", INDEX_NAME, alias);
                return true;
            }
            // Index đã tồn tại — đảm bảo alias tồn tại (trường hợp alias bị xoá tay).
            if (!client.indices().existsAlias(ExistsAliasRequest.of(b -> b.name(alias))).value()) {
                client.indices().putAlias(putAlias(alias));
                log.info("[SearchIndexService] re-created alias={} -> index={}", alias, INDEX_NAME);
            } else {
                log.info("[SearchIndexService] index={} and alias={} already exist", INDEX_NAME, alias);
            }
            return false;
        } catch (Exception e) {
            // Không chặn startup — search sẽ fallback sang DB nếu OS chưa sẵn sàng.
            log.warn("[SearchIndexService] failed to ensure index exists: {}", e.getMessage());
            return false;
        }
    }

    private void createIndexWithAlias(String alias) throws Exception {
        client.indices().create(createRequest(alias));
    }

    private CreateIndexRequest createRequest(String alias) {
        return CreateIndexRequest.of(b -> b
                .index(INDEX_NAME)
                .aliases(alias, a -> a)
                .settings(settings -> settings
                        .numberOfShards(1)
                        .numberOfReplicas(0)
                        .maxResultWindow(10000)
                        .analysis(analysis -> analysis
                                .analyzer(ANALYZER_EXACT, a -> a
                                        .custom(c -> c
                                                .tokenizer("icu_tokenizer")
                                                .filter("lowercase")))
                                .analyzer(ANALYZER_FOLDED, a -> a
                                        .custom(c -> c
                                                .tokenizer("icu_tokenizer")
                                                .filter("icu_folding")
                                                .filter("lowercase")))))
                .mappings(mappings -> mappings
                        .properties("id", Property.of(p -> p.long_(l -> l)))
                        .properties("name", Property.of(p -> p
                                .text(t -> t
                                        .analyzer(ANALYZER_EXACT)
                                        .fields("folded", Property.of(fp -> fp
                                                .text(ft -> ft.analyzer(ANALYZER_FOLDED)))))))
                        .properties("brand", Property.of(p -> p.keyword(k -> k)))
                        .properties("storeId", Property.of(p -> p.long_(l -> l)))
                        .properties("storeTier", Property.of(p -> p.keyword(k -> k)))
                        .properties("categoryId", Property.of(p -> p.integer(i -> i)))
                        .properties("categorySlug", Property.of(p -> p.keyword(k -> k)))
                        .properties("minPrice", Property.of(p -> p.scaledFloat(s -> s.scalingFactor(100d))))
                        .properties("maxPrice", Property.of(p -> p.scaledFloat(s -> s.scalingFactor(100d))))
                        .properties("totalStock", Property.of(p -> p.integer(i -> i)))
                        .properties("totalSold", Property.of(p -> p.integer(i -> i)))
                        .properties("ratingAvg", Property.of(p -> p.halfFloat(h -> h)))
                        .properties("ratingCount", Property.of(p -> p.integer(i -> i)))
                        .properties("isFlashSale", Property.of(p -> p.boolean_(bs -> bs)))
                        .properties("hasDiscount", Property.of(p -> p.boolean_(bb -> bb)))
                        .properties("hasVoucher", Property.of(p -> p.boolean_(bv -> bv)))
                        .properties("freeship", Property.of(p -> p.boolean_(fs -> fs)))
                        .properties("status", Property.of(p -> p.keyword(k -> k)))
                        .properties("createdAt", Property.of(p -> p.date(d -> d)))
                        .properties("updatedAt", Property.of(p -> p.date(d -> d)))));
    }

    private PutAliasRequest putAlias(String alias) {
        return PutAliasRequest.of(b -> b
                .index(INDEX_NAME)
                .name(alias));
    }

    /**
     * Bulk upsert documents vào alias. <b>Idempotent</b> — dùng {@code index}
     * (ghi đè theo {@code _id}) chứ không phải {@code create}.
     *
     * <p><b>Chỉ trả về số document thực sự ghi thành công.</b> OpenSearch có thể
     * trả {@code errors=true} với từng item lỗi (vd: field {@code date} sai format)
     * mà HTTP vẫn 200 — nếu đếm {@code response.items().size()} thì sẽ báo "đã
     * index N sản phẩm" trong khi index thực tế rỗng.
     *
     * @return số document đã index thành công.
     */
    public int bulkUpsert(List<SearchIndexDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }
        try {
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (SearchIndexDocument doc : documents) {
                bulk.operations(BulkOperation.of(op -> op.index(idx -> idx
                        .index(properties.getIndexAlias())
                        .id(String.valueOf(doc.getId()))
                        .document(doc))));
            }
            BulkResponse response = client.bulk(bulk.build());
            int success = countSuccessful(response);
            if (success < response.items().size()) {
                logFailedItems(response);
                log.warn("[SearchIndexService] bulk upsert: {}/{} document thành công, {} lỗi",
                        success, response.items().size(), response.items().size() - success);
            }
            return success;
        } catch (Exception e) {
            log.error("[SearchIndexService] bulk upsert failed: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Đếm số item bulk thực sự ghi thành công (HTTP 2xx, không có error).
     */
    private int countSuccessful(BulkResponse response) {
        int success = 0;
        for (BulkResponseItem item : response.items()) {
            if (item == null) {
                continue;
            }
            if (item.error() == null && item.status() >= 200 && item.status() < 300) {
                success++;
            }
        }
        return success;
    }

    /**
     * Log chi tiết các item bulk bị lỗi — giúp tìm ra nguyên nhân (sai format
     * field, mapping không tồn tại, alias chưa được tạo...) thay vì im lặng.
     */
    private void logFailedItems(BulkResponse response) {
        int logged = 0;
        for (BulkResponseItem item : response.items()) {
            if (item == null || item.error() == null) {
                continue;
            }
            if (logged >= MAX_LOGGED_FAILED_ITEMS) {
                log.warn("[SearchIndexService] ... còn {} item lỗi khác không được log",
                        response.items().size() - logged);
                break;
            }
            log.warn("[SearchIndexService] bulk item lỗi: id={} status={} type={} reason={}",
                    item.id(), item.status(), item.error().type(), item.error().reason());
            logged++;
        }
    }

    /**
     * Xoá toàn bộ document trong index (dùng khi cần rebuild từ đầu).
     *
     * @return số document đã xoá.
     */
    public long deleteAll() {
        try {
            var response = client.deleteByQuery(DeleteByQueryRequest.of(b -> b
                    .index(properties.getIndexAlias())
                    .query(q -> q.matchAll(m -> m))));
            return response.deleted();
        } catch (Exception e) {
            log.error("[SearchIndexService] deleteAll failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Xoá 1 document khỏi index (dùng khi sản phẩm bị xoá).
     *
     * @param productId ID sản phẩm.
     */
    public void delete(Long productId) {
        try {
            client.delete(DeleteRequest.of(b -> b
                    .index(properties.getIndexAlias())
                    .id(String.valueOf(productId))));
        } catch (Exception e) {
            log.warn("[SearchIndexService] delete productId={} failed: {}", productId, e.getMessage());
        }
    }

    /**
     * Đếm số document thực sự đang nằm trong index.
     *
     * <p>Dùng để verify trạng thái thật của index (sau khi reindex) — tránh tình
     * trạng "log báo N sản phẩm" nhưng index thực tế rỗng.
     *
     * @return số document trong index, {@code -1} nếu không truy vấn được.
     */
    public long countDocuments() {
        try {
            CountRequest request = CountRequest.of(b -> b.index(properties.getIndexAlias()));
            return client.count(request).count();
        } catch (Exception e) {
            log.warn("[SearchIndexService] countDocuments failed: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Mapping hiện tại của index — dùng cho tài liệu / debug.
     */
    public Map<String, TypeMapping> currentMappings() {
        return Map.of();
    }

    /**
     * Index hiện tại có tồn tại không.
     */
    public boolean indexExists() {
        try {
            return client.indices()
                    .exists(ExistsRequest.of(b -> b.index(properties.getIndexAlias())))
                    .value();
        } catch (Exception e) {
            return false;
        }
    }
}
