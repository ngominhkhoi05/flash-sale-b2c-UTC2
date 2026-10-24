package com.b2c.flash_sale_b2c_UTC2.search.client;

import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchQuerySpec;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.CreatePitRequest;
import org.opensearch.client.opensearch.core.CreatePitResponse;
import org.opensearch.client.opensearch.core.DeletePitRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.Pit;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.opensearch.client.opensearch.core.search.TrackHits;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Triển khai thật của {@link OpenSearchGateway} dùng PIT + {@code search_after}.
 *
 * <p>Các nguyên tắc áp dụng (xem {@code docs/opensearch-search-architecture.md}):
 * <ul>
 *   <li><b>Không dùng {@code from/size}</b> — chỉ dùng {@code search_after} trên PIT.</li>
 *   <li><b>{@code _source: false}</b> — chỉ lấy {@code hits.hits[]._id}, giảm băng thông
 *       tối đa (mô hình search-then-hydrate).</li>
 *   <li><b>Filter tách khỏi Must</b> — filter đặt trong {@code bool.filter} (cached,
 *       không tính score), chỉ full-text nằm trong {@code bool.must}.</li>
 *   <li><b>Sort luôn kết thúc bằng tiebreaker</b> {@code id} để thứ tự ổn định
 *       giữa 2 request (tránh trùng/thiếu item).</li>
 *   <li><b>{@code track_total_hits}</b> bị cap → {@code totalIsExact=false}.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealOpenSearchGateway implements OpenSearchGateway {

    /** Field chứa ID sản phẩm trong index. */
    private static final String FIELD_ID = "id";

    /** Field tên sản phẩm (có sub-field {@code folded} bỏ dấu tiếng Việt). */
    private static final String FIELD_NAME = "name";

    private static final String FIELD_NAME_FOLDED = "name.folded";
    private static final String FIELD_CATEGORY_ID = "categoryId";
    private static final String FIELD_MIN_PRICE = "minPrice";
    private static final String FIELD_RATING_AVG = "ratingAvg";
    private static final String FIELD_IS_FLASH_SALE = "isFlashSale";
    private static final String FIELD_STATUS = "status";

    /** Trạng thái duy nhất được hiển thị trong kết quả tìm kiếm. */
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final OpenSearchClient client;
    private final OpenSearchProperties properties;

    @Override
    public SearchResult search(SearchQuerySpec spec) {
        String pitId = spec.pitId();
        boolean createdPit = false;
        try {
            if (pitId == null || pitId.isBlank()) {
                pitId = createPit();
                createdPit = true;
            }
            SearchResponse<Object> response = executeSearch(spec, pitId);
            return toResult(response, spec, pitId);
        } catch (OpenSearchException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenSearchException("OpenSearch search failed: " + e.getMessage(), e);
        } finally {
            if (createdPit) {
                // PIT được gia hạn bởi chính request search, không cần xoá ở đây.
                log.debug("[RealOpenSearchGateway] created PIT {} for search", pitId);
            }
        }
    }

    @Override
    public void deletePit(String pitId) {
        if (pitId == null || pitId.isBlank()) {
            return;
        }
        try {
            client.deletePit(DeletePitRequest.of(b -> b.pitId(pitId)));
        } catch (Exception e) {
            log.warn("[RealOpenSearchGateway] deletePit failed: {}", e.getMessage());
        }
    }

    private String createPit() {
        try {
            CreatePitResponse response = client.createPit(CreatePitRequest.of(b -> b
                    .index(properties.getIndexAlias())
                    .keepAlive(t -> t.time(properties.getSearch().getPitKeepAlive()))));
            String pitId = response.pitId();
            if (pitId == null || pitId.isBlank()) {
                throw new OpenSearchException("OpenSearch returned empty PIT id");
            }
            return pitId;
        } catch (OpenSearchException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenSearchException("Failed to create PIT: " + e.getMessage(), e);
        }
    }

    private SearchResponse<Object> executeSearch(SearchQuerySpec spec, String pitId) throws Exception {
        SearchRequest request = SearchRequest.of(b -> b
                .index(properties.getIndexAlias())
                .pit(Pit.of(p -> p.id(pitId).keepAlive(properties.getSearch().getPitKeepAlive())))
                .size(spec.size())
                .query(buildQuery(spec))
                .sort(buildSort(spec.sortKey()))
                .source(SourceConfig.of(s -> s.fetch(false)))
                .trackTotalHits(TrackHits.of(t -> t.enabled(
                        Boolean.valueOf(properties.getSearch().getTrackTotalHits() > 0)
                                ? Boolean.TRUE : Boolean.FALSE)))
                .timeout(properties.getSearch().getRequestTimeoutMs() + "ms")
        );
        if (spec.searchAfter() != null && !spec.searchAfter().isEmpty()) {
            request = request.toBuilder().searchAfter(toFieldValues(spec.searchAfter())).build();
        }
        return client.search(request, Object.class);
    }

    /**
     * Dựng query: {@code bool.must} cho full-text, {@code bool.filter} cho điều kiện lọc.
     */
    private Query buildQuery(SearchQuerySpec spec) {
        List<Query> musts = new ArrayList<>();
        List<Query> filters = new ArrayList<>();

        // Defense layer 1: chỉ sản phẩm ACTIVE mới xuất hiện trong kết quả.
        filters.add(Query.of(q -> q.term(t -> t
                .field(FIELD_STATUS)
                .value(FieldValue.of(STATUS_ACTIVE)))));

        String keyword = spec.keyword();
        if (keyword != null && !keyword.isBlank()) {
            // Multi-match: ưu tiên field giữ dấu, fallback field bỏ dấu (icu_folding).
            musts.add(Query.of(q -> q.multiMatch(m -> m
                    .query(keyword)
                    .fields(FIELD_NAME, FIELD_NAME_FOLDED)
                    .type(org.opensearch.client.opensearch._types.query_dsl.TextQueryType.BestFields)
                    .operator(org.opensearch.client.opensearch._types.query_dsl.Operator.Or))));
        }

        if (spec.categoryIds() != null && !spec.categoryIds().isEmpty()) {
            filters.add(Query.of(q -> q.terms(t -> t
                    .field(FIELD_CATEGORY_ID)
                    .terms(tv -> tv.value(spec.categoryIds().stream()
                            .map(i -> FieldValue.of(i.longValue()))
                            .toList())))));
        }

        BigDecimal priceMin = spec.priceMin();
        BigDecimal priceMax = spec.priceMax();
        if (priceMin != null || priceMax != null) {
            filters.add(Query.of(q -> q.range(r -> {
                if (priceMin != null) {
                    r.gte(JsonData.of(priceMin));
                }
                if (priceMax != null) {
                    r.lte(JsonData.of(priceMax));
                }
                return r.field(FIELD_MIN_PRICE);
            })));
        }

        Integer ratingMin = spec.ratingMin();
        if (ratingMin != null) {
            filters.add(Query.of(q -> q.range(r -> r
                    .field(FIELD_RATING_AVG)
                    .gte(JsonData.of(ratingMin.doubleValue())))));
        }

        Boolean flashSaleOnly = spec.flashSaleOnly();
        if (Boolean.TRUE.equals(flashSaleOnly)) {
            filters.add(Query.of(q -> q.term(t -> t
                    .field(FIELD_IS_FLASH_SALE)
                    .value(FieldValue.of(true)))));
        }

        return Query.of(q -> q.bool(b -> {
            if (!musts.isEmpty()) {
                b.must(musts);
            }
            if (!filters.isEmpty()) {
                b.filter(filters);
            }
            return b;
        }));
    }

    /**
     * Dựng sort clause — luôn kết thúc bằng tiebreaker {@code id} để thứ tự ổn định.
     */
    private List<SortOptions> buildSort(String sortKey) {
        List<SortOptions> sorts = new ArrayList<>();
        String key = sortKey == null ? "relevance" : sortKey;
        switch (key) {
            case "newest" -> sorts.add(fieldSort("createdAt", SortOrder.Desc));
            case "best_selling" -> sorts.add(fieldSort("totalSold", SortOrder.Desc));
            case "price_asc" -> sorts.add(fieldSort(FIELD_MIN_PRICE, SortOrder.Asc));
            case "price_desc" -> sorts.add(fieldSort(FIELD_MIN_PRICE, SortOrder.Desc));
            default -> {
                // relevance: _score chỉ ổn định khi có PIT (snapshot đóng băng).
                sorts.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
            }
        }
        sorts.add(fieldSort(FIELD_ID, SortOrder.Asc));
        return sorts;
    }

    private SortOptions fieldSort(String field, SortOrder order) {
        return SortOptions.of(s -> s.field(f -> f.field(field).order(order)));
    }

    private SearchResult toResult(SearchResponse<Object> response, SearchQuerySpec spec, String pitId) {
        List<Long> productIds = new ArrayList<>();
        List<Hit<Object>> hits = response.hits() == null ? List.of() : response.hits().hits();
        for (Hit<Object> hit : hits) {
            if (hit.id() == null) {
                continue;
            }
            try {
                productIds.add(Long.parseLong(hit.id()));
            } catch (NumberFormatException e) {
                log.warn("[RealOpenSearchGateway] non-numeric document id '{}' skipped", hit.id());
            }
        }

        long total = 0;
        boolean totalIsExact = true;
        if (response.hits() != null && response.hits().total() != null) {
            total = response.hits().total().value();
            totalIsExact = response.hits().total().relation()
                    == org.opensearch.client.opensearch.core.search.TotalHitsRelation.Eq;
        }

        // PIT id có thể thay đổi sau mỗi lần gia hạn -> luôn lấy id mới từ response.
        String effectivePitId = response.pitId() != null && !response.pitId().isBlank()
                ? response.pitId()
                : pitId;

        boolean hasMore = hits.size() >= spec.size();
        List<Object> lastSortValues = List.of();
        if (!hits.isEmpty()) {
            List<FieldValue> sortValues = hits.get(hits.size() - 1).sort();
            if (sortValues != null && !sortValues.isEmpty()) {
                lastSortValues = fromFieldValues(sortValues);
            }
        }
        return new SearchResult(productIds, effectivePitId, total, totalIsExact, hasMore, lastSortValues);
    }

    /**
     * Chuyển mảng sort values (đã decode từ cursor) sang {@link FieldValue}.
     */
    private List<FieldValue> toFieldValues(List<Object> searchAfter) {
        List<FieldValue> values = new ArrayList<>(searchAfter.size());
        for (Object raw : searchAfter) {
            if (raw instanceof Double || raw instanceof Float) {
                // _score là float -> phải giữ double, không cắt phần thập phân.
                values.add(FieldValue.of(((Number) raw).doubleValue()));
            } else if (raw instanceof Number number) {
                // id / totalSold / createdAt(millis) đều là số nguyên -> long.
                values.add(FieldValue.of(number.longValue()));
            } else if (raw instanceof Boolean bool) {
                values.add(FieldValue.of(bool));
            } else if (raw == null) {
                values.add(FieldValue.NULL);
            } else {
                values.add(FieldValue.of(String.valueOf(raw)));
            }
        }
        return values;
    }

    /**
     * Chuyển mảng sort values từ {@link FieldValue} sang {@code Object} để nhúng vào cursor.
     */
    public static List<Object> fromFieldValues(List<FieldValue> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Object> result = new ArrayList<>(values.size());
        for (FieldValue v : values) {
            if (v == null) {
                result.add(null);
                continue;
            }
            switch (v._kind()) {
                case Long -> result.add(v.longValue());
                case Double -> result.add(v.doubleValue());
                case Boolean -> result.add(v.booleanValue());
                case String -> result.add(v.stringValue());
                case Null -> result.add(null);
            }
        }
        return result;
    }
}
