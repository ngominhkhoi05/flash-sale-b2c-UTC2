package com.b2c.flash_sale_b2c_UTC2.search.service;

import com.b2c.flash_sale_b2c_UTC2.search.client.OpenSearchProperties;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchIndexDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesClient;
import org.opensearch.client.transport.endpoints.BooleanResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchIndexServiceTest {

    @Mock
    private OpenSearchClient client;

    @Mock
    private OpenSearchIndicesClient indicesClient;

    private OpenSearchProperties properties;
    private SearchIndexService service;

    @BeforeEach
    void setUp() {
        properties = new OpenSearchProperties();
        properties.setIndexAlias("products");
        service = new SearchIndexService(client, properties);
        when(client.indices()).thenReturn(indicesClient);
    }

    private static BooleanResponse boolResponse(boolean value) {
        return new BooleanResponse(value);
    }

    @Test
    @DisplayName("ensureIndexExists: index chua ton tai -> tao index moi kem alias")
    void ensureIndexExists_createsIndexWithAlias() throws IOException {
        when(indicesClient.exists(any(org.opensearch.client.opensearch.indices.ExistsRequest.class)))
                .thenReturn(boolResponse(false));

        boolean created = service.ensureIndexExists();

        assertTrue(created);
        verify(indicesClient).create(any(CreateIndexRequest.class));
    }

    @Test
    @DisplayName("ensureIndexExists: index da ton tai va alias ton tai -> khong tao lai")
    void ensureIndexExists_isIdempotent() throws IOException {
        when(indicesClient.exists(any(org.opensearch.client.opensearch.indices.ExistsRequest.class)))
                .thenReturn(boolResponse(true));
        when(indicesClient.existsAlias(any(org.opensearch.client.opensearch.indices.ExistsAliasRequest.class)))
                .thenReturn(boolResponse(true));

        boolean created = service.ensureIndexExists();

        assertFalse(created);
        verify(indicesClient, never()).create(any(CreateIndexRequest.class));
    }

    @Test
    @DisplayName("ensureIndexExists: index ton tai nhung alias bi xoa -> tao lai alias")
    void ensureIndexExists_recreatesMissingAlias() throws IOException {
        when(indicesClient.exists(any(org.opensearch.client.opensearch.indices.ExistsRequest.class)))
                .thenReturn(boolResponse(true));
        when(indicesClient.existsAlias(any(org.opensearch.client.opensearch.indices.ExistsAliasRequest.class)))
                .thenReturn(boolResponse(false));

        boolean created = service.ensureIndexExists();

        assertFalse(created);
        verify(indicesClient).putAlias(any(org.opensearch.client.opensearch.indices.PutAliasRequest.class));
    }

    @Test
    @DisplayName("ensureIndexExists: OpenSearch khong phan hoi -> khong throw, tra false (fallback DB)")
    void ensureIndexExists_swallowsTransportError() throws IOException {
        when(indicesClient.exists(any(org.opensearch.client.opensearch.indices.ExistsRequest.class)))
                .thenThrow(new IOException("connection refused"));

        boolean created = service.ensureIndexExists();

        assertFalse(created);
    }

    @Test
    @DisplayName("bulkUpsert: danh sach rong -> khong goi OpenSearch")
    void bulkUpsert_emptyList_isNoOp() throws IOException {
        assertEquals(0, service.bulkUpsert(List.of()));
        assertEquals(0, service.bulkUpsert(null));
        verify(client, never()).bulk(any(BulkRequest.class));
    }

    @Test
    @DisplayName("bulkUpsert: goi bulk voi dung so operation va index vao alias")
    void bulkUpsert_sendsBulkOperations() throws IOException {
        BulkResponseItem item = org.mockito.Mockito.mock(BulkResponseItem.class);
        BulkResponse response = org.mockito.Mockito.mock(BulkResponse.class);
        when(response.errors()).thenReturn(false);
        when(response.items()).thenReturn(List.of(item, item));
        when(client.bulk(any(BulkRequest.class))).thenReturn(response);

        int count = service.bulkUpsert(List.of(doc(1L), doc(2L)));

        assertEquals(2, count);
        var captor = org.mockito.ArgumentCaptor.forClass(BulkRequest.class);
        verify(client).bulk(captor.capture());
        BulkRequest request = captor.getValue();
        assertEquals(2, request.operations().size());
        assertEquals("products", request.operations().get(0).index().index());
        assertEquals("1", request.operations().get(0).index().id());
    }

    @Test
    @DisplayName("bulkUpsert: OpenSearch bao loi -> tra 0 thay vi throw")
    void bulkUpsert_swallowsTransportError() throws IOException {
        when(client.bulk(any(BulkRequest.class))).thenThrow(new IOException("timeout"));

        int count = service.bulkUpsert(List.of(doc(1L)));

        assertEquals(0, count);
    }

    @Test
    @DisplayName("deleteAll: tra so document da xoa")
    void deleteAll_returnsDeletedCount() throws IOException {
        DeleteByQueryResponse response = org.mockito.Mockito.mock(DeleteByQueryResponse.class);
        when(response.deleted()).thenReturn(7L);
        when(client.deleteByQuery(any(org.opensearch.client.opensearch.core.DeleteByQueryRequest.class)))
                .thenReturn(response);

        assertEquals(7L, service.deleteAll());
    }

    @Test
    @DisplayName("delete: xoa 1 document theo id")
    void delete_removesDocumentById() throws IOException {
        service.delete(42L);
        verify(client).delete(any(org.opensearch.client.opensearch.core.DeleteRequest.class));
    }

    @Test
    @DisplayName("delete: OpenSearch loi -> khong throw")
    void delete_swallowsTransportError() throws IOException {
        when(client.delete(any(org.opensearch.client.opensearch.core.DeleteRequest.class)))
                .thenThrow(new IOException("timeout"));

        service.delete(42L);
    }

    @Test
    @DisplayName("indexExists: tra false khi OpenSearch khong phan hoi")
    void indexExists_returnsFalseOnError() throws IOException {
        when(indicesClient.exists(any(org.opensearch.client.opensearch.indices.ExistsRequest.class)))
                .thenThrow(new IOException("timeout"));

        assertFalse(service.indexExists());
    }

    private static SearchIndexDocument doc(long id) {
        return SearchIndexDocument.builder()
                .id(id)
                .name("Tai nghe Bluetooth " + id)
                .brand("Sony")
                .storeId(1L)
                .storeTier("NORMAL")
                .categoryId(10)
                .categorySlug("true-wireless")
                .minPrice(new BigDecimal("1290000"))
                .maxPrice(new BigDecimal("1990000"))
                .totalStock(100)
                .totalSold(20)
                .ratingAvg(4.5)
                .ratingCount(30)
                .isFlashSale(true)
                .hasDiscount(false)
                .hasVoucher(true)
                .freeship(false)
                .status("ACTIVE")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build();
    }
}
