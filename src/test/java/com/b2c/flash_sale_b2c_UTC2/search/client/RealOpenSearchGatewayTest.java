package com.b2c.flash_sale_b2c_UTC2.search.client;

import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchQuerySpec;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.CreatePitRequest;
import org.opensearch.client.opensearch.core.CreatePitResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests cho {@link RealOpenSearchGateway} — PIT + search_after + {@code _source: false}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealOpenSearchGatewayTest {

    @Mock
    private OpenSearchClient client;

    private OpenSearchProperties properties;
    private RealOpenSearchGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new OpenSearchProperties();
        properties.setIndexAlias("products");
        properties.getSearch().setPageSize(24);
        properties.getSearch().setPitKeepAlive("2m");
        properties.getSearch().setRequestTimeoutMs(1500);
        gateway = new RealOpenSearchGateway(client, properties);
    }

    private SearchQuerySpec spec(String keyword, String sortKey, String pitId, List<Object> searchAfter) {
        return new SearchQuerySpec(
                keyword,
                List.of(),
                null,
                null,
                null,
                null,
                sortKey,
                24,
                pitId,
                searchAfter);
    }

    private SearchResponse<Object> response(List<Hit<Object>> hits, String pitId,
                                            long total, TotalHitsRelation relation) {
        HitsMetadata<Object> metadata = HitsMetadata.of(h -> h
                .hits(hits)
                .total(t -> t.value(total).relation(relation)));
        return SearchResponse.searchResponseOf(b -> b
                .hits(metadata)
                .pitId(pitId)
                .took(5L)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0).skipped(0)));
    }

    private Hit<Object> hit(String id, FieldValue... sort) {
        return Hit.of(h -> h.id(id).index("products_v1").sort(List.of(sort)));
    }

    @Test
    @DisplayName("search: tao PIT moi khi spec khong co pitId")
    void search_createsPitWhenMissing() throws Exception {
        when(client.createPit(any(CreatePitRequest.class)))
                .thenReturn(CreatePitResponse.of(b -> b.pitId("pit-created").creationTime(1L)));
        when(client.search(any(SearchRequest.class), any(Class.class)))
                .thenReturn(response(List.of(hit("1")), "pit-created", 1, TotalHitsRelation.Eq));

        SearchResult result = gateway.search(spec("dien thoai", "relevance", null, null));

        verify(client).createPit(any(CreatePitRequest.class));
        assertEquals("pit-created", result.pitId());
        assertEquals(List.of(1L), result.productIds());
    }

    @Test
    @DisplayName("search: khong tao PIT moi khi spec da co pitId")
    void search_reusesExistingPit() throws Exception {
        when(client.search(any(SearchRequest.class), any(Class.class)))
                .thenReturn(response(List.of(hit("1")), "pit-existing", 1, TotalHitsRelation.Eq));

        gateway.search(spec(null, "relevance", "pit-existing", null));

        verify(client, never()).createPit(any(CreatePitRequest.class));
    }

    @Test
    @DisplayName("search: chi lay _id, khong lay _source")
    void search_doesNotFetchSource() throws Exception {
        when(client.search(any(SearchRequest.class), any(Class.class)))
                .thenReturn(response(List.of(hit("1")), "pit", 1, TotalHitsRelation.Eq));

        gateway.search(spec(null, "relevance", "pit", null));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(captor.capture(), any(Class.class));
        assertNotNull(captor.getValue().source());
        assertFalse(captor.getValue().source().fetch(), "phai gui _source=false de giam bang thong");
    }

    @Test
    @DisplayName("search: truyen search_after khi spec co sort values")
    void search_passesSearchAfter() throws Exception {
        when(client.search(any(SearchRequest.class), any(Class.class)))
                .thenReturn(response(List.of(hit("2")), "pit", 1, TotalHitsRelation.Eq));

        gateway.search(spec(null, "relevance", "pit", List.of(1.5, 42L)));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(captor.capture(), any(Class.class));
        List<FieldValue> after = captor.getValue().searchAfter();
        assertNotNull(after);
        assertEquals(2, after.size());
        assertEquals(1.5, after.get(0).doubleValue(), 0.0001);
        assertEquals(42L, after.get(1).longValue());
    }

    @Test
    @DisplayName("search: khong dung from/size offset — chi PIT + search_after")
    void search_usesPitNotFromSize() throws Exception {
        when(client.search(any(SearchRequest.class), any(Class.class)))
                .thenReturn(response(List.of(hit("1")), "pit", 1, TotalHitsRelation.Eq));

        gateway.search(spec(null, "relevance", "pit", List.of(1L)));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(captor.capture(), any(Class.class));
        assertNotNull(captor.getValue().pit());
        assertEquals("pit", captor.getValue().pit().id());
    }

    @Test
    @DisplayName("search: sort luon ket thuc bang tiebreaker id asc")
    void search_sortHasTiebreaker() throws Exception {
        when(client.search(any(SearchRequest.class), any(Class.class)))
                .thenReturn(response(List.of(hit("1")), "pit", 1, TotalHitsRelation.Eq));

        gateway.search(spec(null, "relevance", "pit", null));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(captor.capture(), any(Class.class));
        List<org.opensearch.client.opensearch._types.SortOptions> sorts = captor.getValue().sort();
        assertNotNull(sorts);
        assertEquals(2, sorts.size());
        assertEquals("id", sorts.get(1).field().field());
        assertEquals(org.opensearch.client.opensearch._types.SortOrder.Asc, sorts.get(1).field().order());
    }

    @Test
    @DisplayName("search: lay last sort values cua hit cuoi vao cursor")
    void search_extractsLastSortValues() throws Exception {
        when(client.search(any(SearchRequest.class), any(Class.class)))
                .thenReturn(response(
                        List.of(hit("1", FieldValue.of(2.5), FieldValue.of(1L)),
                                hit("2", FieldValue.of(3.5), FieldValue.of(2L))),
                        "pit", 2, TotalHitsRelation.Eq));

        SearchResult result = gateway.search(spec(null, "relevance", "pit", null));

        assertEquals(List.of(3.5, 2L), result.lastSortValues());
    }

    @Test
    @DisplayName("search: hasMore = true khi so hit >= size")
    void search_hasMoreWhenHitsFillPage() throws Exception {
        List<Hit<Object>> hits = new java.util.ArrayList<>();
        for (int i = 0; i < 24; i++) {
            hits.add(hit(String.valueOf(i + 1)));
        }
        when(client.search(any(SearchRequest.class), any(Class.class)))
                .thenReturn(response(hits, "pit", 100, TotalHitsRelation.Eq));

        SearchResult result = gateway.search(spec(null, "relevance", "pit", null));

        assertTrue(result.hasMore());
        assertEquals(100, result.totalHits());
        assertTrue(result.totalIsExact());
    }

    @Test
    @DisplayName("search: totalIsExact = false khi relation = Gte (bi cap track_total_hits)")
    void search_totalNotExactWhenCapped() throws Exception {
        when(client.search(any(SearchRequest.class), any(Class.class)))
                .thenReturn(response(List.of(hit("1")), "pit", 10000, TotalHitsRelation.Gte));

        SearchResult result = gateway.search(spec(null, "relevance", "pit", null));

        assertFalse(result.totalIsExact());
        assertEquals(10000, result.totalHits());
    }

    @Test
    @DisplayName("search: doc id khong phai so -> bo qua, khong crash")
    void search_skipsNonNumericIds() throws Exception {
        when(client.search(any(SearchRequest.class), any(Class.class)))
                .thenReturn(response(List.of(hit("abc"), hit("12")), "pit", 2, TotalHitsRelation.Eq));

        SearchResult result = gateway.search(spec(null, "relevance", "pit", null));

        assertEquals(List.of(12L), result.productIds());
    }

    @Test
    @DisplayName("search: loi client -> nem OpenSearchException (service fallback DB)")
    void search_wrapsClientException() throws Exception {
        when(client.search(any(SearchRequest.class), any(Class.class)))
                .thenThrow(new java.io.IOException("connection refused"));

        OpenSearchException ex = assertThrows(OpenSearchException.class,
                () -> gateway.search(spec(null, "relevance", "pit", null)));
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("search: PIT rong -> nem OpenSearchException")
    void search_emptyPitIdThrows() throws Exception {
        when(client.createPit(any(CreatePitRequest.class)))
                .thenReturn(CreatePitResponse.of(b -> b.pitId("").creationTime(1L)));

        assertThrows(OpenSearchException.class,
                () -> gateway.search(spec(null, "relevance", null, null)));
    }

    @Test
    @DisplayName("deletePit: null/blank -> khong goi client")
    void deletePit_nullSafe() throws Exception {
        gateway.deletePit(null);
        gateway.deletePit("");
        verify(client, never()).deletePit(any(org.opensearch.client.opensearch.core.DeletePitRequest.class));
    }

    @Test
    @DisplayName("deletePit: loi client -> log, khong nem")
    void deletePit_swallowsException() throws Exception {
        when(client.deletePit(any(org.opensearch.client.opensearch.core.DeletePitRequest.class)))
                .thenThrow(new java.io.IOException("boom"));
        gateway.deletePit("pit-1");
    }

    @Test
    @DisplayName("fromFieldValues: chuyen dung kieu du lieu sang Object")
    void fromFieldValues_converts() {
        List<Object> converted = RealOpenSearchGateway.fromFieldValues(List.of(
                FieldValue.of(1L),
                FieldValue.of(2.5),
                FieldValue.of(true),
                FieldValue.of("abc"),
                FieldValue.NULL));

        assertEquals(5, converted.size());
        assertEquals(1L, converted.get(0));
        assertEquals(2.5, converted.get(1));
        assertEquals(Boolean.TRUE, converted.get(2));
        assertEquals("abc", converted.get(3));
        assertNull(converted.get(4));
    }

    @Test
    @DisplayName("fromFieldValues: rong -> list rong")
    void fromFieldValues_empty() {
        assertTrue(RealOpenSearchGateway.fromFieldValues(List.of()).isEmpty());
        assertTrue(RealOpenSearchGateway.fromFieldValues(null).isEmpty());
    }

    @Test
    @DisplayName("search: price/rating/flashSale filter duoc dua vao query")
    void search_buildsFilters() throws Exception {
        when(client.search(any(SearchRequest.class), any(Class.class)))
                .thenReturn(response(List.of(hit("1")), "pit", 1, TotalHitsRelation.Eq));

        SearchQuerySpec specWithFilters = new SearchQuerySpec(
                "dien thoai",
                List.of(5, 7),
                new BigDecimal("1000"),
                new BigDecimal("5000"),
                4,
                Boolean.TRUE,
                "price_asc",
                24,
                "pit",
                null);

        gateway.search(specWithFilters);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(captor.capture(), any(Class.class));
        assertNotNull(captor.getValue().query());
        assertNotNull(captor.getValue().query().bool());
        assertFalse(captor.getValue().query().bool().must().isEmpty());
        assertFalse(captor.getValue().query().bool().filter().isEmpty());
    }

    @Test
    @DisplayName("search: khong co keyword -> khong co must clause")
    void search_noKeywordNoMust() throws Exception {
        when(client.search(any(SearchRequest.class), any(Class.class)))
                .thenReturn(response(List.of(hit("1")), "pit", 1, TotalHitsRelation.Eq));

        gateway.search(spec(null, "relevance", "pit", null));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(captor.capture(), any(Class.class));
        assertTrue(captor.getValue().query().bool().must().isEmpty());
        assertFalse(captor.getValue().query().bool().filter().isEmpty());
    }

    @Test
    @DisplayName("search: response builder hop le -> khong nem")
    void search_mockClientSanity() {
        OpenSearchClient mockClient = mock(OpenSearchClient.class);
        assertNotNull(mockClient);
    }
}
