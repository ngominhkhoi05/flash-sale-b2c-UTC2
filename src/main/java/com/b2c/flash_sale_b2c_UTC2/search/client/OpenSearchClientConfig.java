package com.b2c.flash_sale_b2c_UTC2.search.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.net.URI;

/**
 * Tạo {@link OpenSearchClient} dùng ApacheHttpClient5Transport.
 *
 * <p>Transport này dùng httpclient5 5.6 (bắt buộc với Spring Boot 4.1) — cần
 * {@code opensearch-java >= 3.6.0}.
 *
 * <p>Timeout đặt theo nguyên tắc fail-fast: nếu OpenSearch chậm, backend chuyển
 * sang fallback PostgreSQL thay vì giữ thread request.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class OpenSearchClientConfig {

    private final OpenSearchProperties properties;

    @Bean(destroyMethod = "close")
    public OpenSearchTransport openSearchTransport() {
        // HttpHost.create(URI) tự tách đúng host/scheme/port.
        // KHÔNG dùng new HttpHost(uri.getHost(), uri.getScheme(), uri.getPort()):
        // thứ tự tham số của httpcore5 là (scheme, hostname, port) → code cũ tạo
        // host="https", scheme="localhost" → lỗi "No such host is known (https)"
        // và toàn bộ OpenSearch im lặng rơi về fallback PostgreSQL.
        ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder
                .builder(resolveHost());

        builder.setMapper(createJsonpMapper());

        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            httpClientBuilder.setConnectionManager(
                    PoolingAsyncClientConnectionManagerBuilder.create()
                            .setTlsStrategy(tlsStrategy())
                            .build());
            applyBasicAuth(httpClientBuilder);
            return httpClientBuilder;
        });

        builder.setConnectionConfigCallback(conn -> conn
                .setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeoutMs()))
                .setSocketTimeout(Timeout.ofMilliseconds(properties.getSocketTimeoutMs())));

        builder.setRequestConfigCallback(req -> req
                .setResponseTimeout(Timeout.ofMilliseconds(properties.getSocketTimeoutMs())));

        // Retry tự động sẽ khiến request vượt quá budget fail-fast.
        builder.setAutomaticRetriesDisabled(true);

        log.info("[OpenSearchClientConfig] initialized host={} alias={} sslTrustAll={}",
                properties.getHost(), properties.getIndexAlias(), properties.isSslTrustAll());
        return builder.build();
    }

    @Bean
    public OpenSearchClient openSearchClient(OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }

    /**
     * Tách host/scheme/port từ {@code opensearch.host}.
     *
     * <p>Package-private để test được: bug cốt lõi là truyền sai thứ tự tham số
     * cho {@code HttpHost} khiến hostname thành {@code "https"}.
     */
    HttpHost resolveHost() {
        return HttpHost.create(URI.create(properties.getHost()));
    }

    /**
     * Tạo mapper JSON cho OpenSearch client.
     *
     * <p><b>Quan trọng — {@code Instant} phải serialize ra ISO-8601, không phải
     * epoch dạng số thập phân.</b> Mapping {@code date} của OpenSearch chỉ nhận
     * {@code strict_date_optional_time||epoch_millis}. Với cấu hình mặc định của
     * Jackson ({@code WRITE_DATES_AS_TIMESTAMPS=true}), {@code Instant} được ghi
     * thành {@code 1761234567.123000000} (float) — OpenSearch <b>reject</b> toàn bộ
     * document với lỗi {@code mapper_parsing_exception}, khiến index rỗng trong khi
     * log lại báo reindex thành công.
     *
     * <p>ISO-8601 ({@code 2026-01-01T00:00:00Z}) nằm trong {@code strict_date_optional_time}
     * nên được chấp nhận, và không cần đổi mapping index đã tạo.
     */
    JacksonJsonpMapper createJsonpMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new JacksonJsonpMapper(objectMapper);
    }

    /**
     * Thiết lập HTTP Basic auth cho OpenSearch (security plugin BẬT).
     *
     * <p>Bỏ qua nếu username rỗng — cho phép cluster không bật security.
     */
    private void applyBasicAuth(HttpAsyncClientBuilder httpClientBuilder) {
        String username = properties.getUsername();
        if (username == null || username.isBlank()) {
            log.info("[OpenSearchClientConfig] no username configured - skipping basic auth");
            return;
        }
        String password = properties.getPassword() == null ? "" : properties.getPassword();
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                new AuthScope(null, null, -1, null, null),
                new UsernamePasswordCredentials(username, password.toCharArray()));
        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
    }

    /**
     * TLS strategy cho kết nối https.
     *
     * <ul>
     *   <li>{@code sslTrustAll=true} → trust-all + bỏ hostname verification
     *       (CHỈ cho local với demo cert tự ký của OpenSearch).</li>
     *   <li>{@code sslTrustAll=false} → SSLContext mặc định của JVM, có hostname
     *       verification (bắt buộc cho production).</li>
     * </ul>
     */
    private org.apache.hc.core5.http.nio.ssl.TlsStrategy tlsStrategy() {
        if (!properties.isSslTrustAll()) {
            try {
                return ClientTlsStrategyBuilder.create()
                        .setSslContext(SSLContext.getDefault())
                        .buildAsync();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to build default SSLContext", e);
            }
        }
        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial((TrustStrategy) (chain, authType) -> true)
                    .build();
            log.warn("[OpenSearchClientConfig] sslTrustAll is ENABLED - must not be used in production");
            return ClientTlsStrategyBuilder.create()
                    .setSslContext(sslContext)
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .buildAsync();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build trust-all SSLContext", e);
        }
    }
}
