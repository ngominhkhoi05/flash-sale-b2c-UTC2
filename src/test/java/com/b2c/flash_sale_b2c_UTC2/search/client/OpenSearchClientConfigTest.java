package com.b2c.flash_sale_b2c_UTC2.search.client;

import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchIndexDocument;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests cho {@link OpenSearchClientConfig}.
 *
 * <p>2 bug cốt lõi được chốt lại ở đây:
 * <ol>
 *   <li>{@code HttpHost} — thứ tự tham số bị đảo khiến hostname thành {@code "https"}
 *       → toàn bộ OpenSearch im lặng rơi về fallback PostgreSQL (bug "gõ den mini không ra kết quả").</li>
 *   <li>{@code Instant} phải serialize ra ISO-8601, không phải epoch dạng số thập phân,
 *       vì mapping {@code date} của OpenSearch reject epoch float.</li>
 * </ol>
 */
class OpenSearchClientConfigTest {

    private OpenSearchProperties properties;
    private OpenSearchClientConfig config;

    @BeforeEach
    void setUp() {
        properties = new OpenSearchProperties();
        config = new OpenSearchClientConfig(properties);
    }

    @Test
    @DisplayName("resolveHost: tach dung host/scheme/port tu opensearch.host")
    void resolveHost_parsesHostSchemePortCorrectly() {
        properties.setHost("https://opensearch.internal:9200");

        HttpHost host = config.resolveHost();

        assertEquals("opensearch.internal", host.getHostName());
        assertEquals("https", host.getSchemeName());
        assertEquals(9200, host.getPort());
    }

    @Test
    @DisplayName("resolveHost: KHONG tao host co hostname la 'https' (bug inversion tham so)")
    void resolveHost_doesNotInvertHostnameAndScheme() {
        properties.setHost("https://localhost:9200");

        HttpHost host = config.resolveHost();

        assertNotEquals("https", host.getHostName());
        assertNotEquals("localhost", host.getSchemeName());
        assertEquals("localhost", host.getHostName());
    }

    @Test
    @DisplayName("resolveHost: http khong SSL van tach dung scheme/host")
    void resolveHost_handlesPlainHttp() {
        properties.setHost("http://127.0.0.1:9200");

        HttpHost host = config.resolveHost();

        assertEquals("127.0.0.1", host.getHostName());
        assertEquals("http", host.getSchemeName());
        assertEquals(9200, host.getPort());
    }

    @Test
    @DisplayName("createJsonpMapper: Instant serialize ra ISO-8601 chu khong phai epoch float")
    void createJsonpMapper_serializesInstantAsIso8601() throws Exception {
        JacksonJsonpMapper mapper = config.createJsonpMapper();

        String json = writeJson(mapper, SearchIndexDocument.builder()
                .id(1L)
                .name("Den ban mini")
                .minPrice(new BigDecimal("1290000"))
                .maxPrice(new BigDecimal("1990000"))
                .totalStock(10)
                .status("ACTIVE")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T03:04:05.123Z"))
                .build());

        assertTrue(json.contains("\"createdAt\":\"2026-01-01T00:00:00Z\""),
                "createdAt phai la ISO-8601: " + json);
        assertTrue(json.contains("\"updatedAt\":\"2026-01-02T03:04:05.123Z\""),
                "updatedAt phai la ISO-8601: " + json);
        assertTrue(!json.contains("1761234567"),
                "khong duoc serialize epoch dang so thap phan: " + json);
    }

    private static String writeJson(JacksonJsonpMapper mapper, SearchIndexDocument document) throws Exception {
        StringWriter writer = new StringWriter();
        try (jakarta.json.stream.JsonGenerator generator =
                     mapper.jsonProvider().createGenerator(writer)) {
            mapper.serialize(document, generator);
        }
        return writer.toString();
    }
}
