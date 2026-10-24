package com.b2c.flash_sale_b2c_UTC2.search.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.Timeout;
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
        URI uri = URI.create(properties.getHost());
        ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder
                .builder(new org.apache.hc.core5.http.HttpHost(uri.getHost(), uri.getScheme(), uri.getPort()));

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
