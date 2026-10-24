package com.b2c.flash_sale_b2c_UTC2.search.cursor;

import com.b2c.flash_sale_b2c_UTC2.search.client.OpenSearchProperties;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchCursor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Encode/decode cursor phân trang bằng HMAC-SHA256.
 *
 * <p>Token có dạng {@code <base64url(payload)>.<base64url(signature)>}. Chữ ký
 * đảm bảo tính toàn vẹn — client không thể tự chế ra cursor hợp lệ, và BE có
 * thể phát hiện cursor bị sửa (tamper) để loại bỏ.
 *
 * <p>Payload chứa fingerprint ({@code fh}) của {@code q + filters}: nếu user
 * đổi bộ lọc hoặc sort, fingerprint lệch → BE <b>bỏ qua cursor</b> và trả về
 * trang đầu, tránh trộn kết quả của 2 query khác nhau.
 */
@Slf4j
@Component
public class CursorCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SECRET_FALLBACK = "flash-sale-b2c-search-cursor-dev-secret";
    private static final int MIN_SECRET_LENGTH = 16;

    private final ObjectMapper objectMapper;
    private final OpenSearchProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public CursorCodec(ObjectMapper objectMapper, OpenSearchProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Mã hoá cursor thành token opaque.
     *
     * @param cursor payload cần mã hoá.
     * @return token base64url(payload).base64url(signature), hoặc {@code null}
     *         nếu cursor null.
     */
    public String encode(SearchCursor cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            byte[] payloadBytes = objectMapper.writeValueAsBytes(cursor);
            String payload = base64UrlEncode(payloadBytes);
            String signature = base64UrlEncode(sign(payload));
            return payload + "." + signature;
        } catch (Exception e) {
            log.error("[CursorCodec] encode failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Giải mã + xác thực cursor.
     *
     * @param token token nhận từ client.
     * @return payload đã xác thực, hoặc {@code null} nếu token null/rỗng,
     *         sai định dạng, sai chữ ký hoặc sai version.
     */
    public SearchCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        int separator = token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            log.warn("[CursorCodec] rejected malformed cursor token");
            return null;
        }
        String payload = token.substring(0, separator);
        String signature = token.substring(separator + 1);
        if (!constantTimeEquals(base64UrlEncode(sign(payload)), signature)) {
            log.warn("[CursorCodec] rejected cursor with invalid signature");
            return null;
        }
        try {
            byte[] decoded = base64UrlDecode(payload);
            SearchCursor cursor = objectMapper.readValue(decoded, new TypeReference<SearchCursor>() {
            });
            if (cursor.version() != SearchCursor.CURRENT_VERSION) {
                log.warn("[CursorCodec] rejected cursor with unsupported version {}", cursor.version());
                return null;
            }
            return normalizeSearchAfter(cursor);
        } catch (Exception e) {
            log.warn("[CursorCodec] rejected undecodable cursor: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Chuẩn hoá {@code searchAfter} về {@code Long}/{@code Double}.
     *
     * <p>Jackson mặc định deserialize JSON number {@code 3} thành {@link Integer},
     * còn {@code 2.5} thành {@link Double}. OpenSearch yêu cầu {@code search_after}
     * khớp đúng kiểu với field sort ({@code id} là {@code long}, {@code _score} là
     * {@code float}) — nếu không chuẩn hoá, request sẽ bị từ chối.
     */
    private SearchCursor normalizeSearchAfter(SearchCursor cursor) {
        List<Object> raw = cursor.searchAfter();
        if (raw == null || raw.isEmpty()) {
            return cursor;
        }
        List<Object> normalized = new ArrayList<>(raw.size());
        for (Object value : raw) {
            if (value instanceof Integer integerValue) {
                normalized.add(integerValue.longValue());
            } else if (value instanceof Short shortValue) {
                normalized.add(shortValue.longValue());
            } else if (value instanceof Byte byteValue) {
                normalized.add(byteValue.longValue());
            } else if (value instanceof Float floatValue) {
                normalized.add(floatValue.doubleValue());
            } else {
                normalized.add(value);
            }
        }
        return new SearchCursor(
                cursor.version(),
                cursor.mode(),
                cursor.pitId(),
                normalized,
                cursor.offset(),
                cursor.sortKey(),
                cursor.fingerprint(),
                cursor.issuedAt());
    }

    /**
     * Tính fingerprint của {@code q + filters + sort}.
     *
     * <p>Dùng để phát hiện việc client đổi bộ lọc giữa chừng — khi đó cursor
     * cũ không còn hợp lệ và phải bị bỏ qua.
     *
     * @param parts các thành phần của query (q, filter, sort...).
     * @return chuỗi hex SHA-256 (rút gọn 32 ký tự).
     */
    public String fingerprint(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            if (parts != null) {
                for (String part : parts) {
                    sb.append(part == null ? "" : part).append('|');
                }
            }
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16 && i < hash.length; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("[CursorCodec] fingerprint failed: {}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * Tạo cursor mới cho vị trí hiện tại.
     *
     * @param mode          nguồn phân trang.
     * @param pitId         id PIT (null nếu không dùng PIT).
     * @param searchAfter   mảng sort values của hit cuối.
     * @param offset        số item đã trả về trước đó.
     * @param sortKey       sort key hiện tại.
     * @param fingerprint   fingerprint của query.
     * @return payload cursor.
     */
    public SearchCursor newCursor(SearchCursor.CursorMode mode,
                                  String pitId,
                                  List<Object> searchAfter,
                                  int offset,
                                  String sortKey,
                                  String fingerprint) {
        return new SearchCursor(
                SearchCursor.CURRENT_VERSION,
                mode,
                pitId,
                searchAfter,
                offset,
                sortKey,
                fingerprint,
                Instant.now().getEpochSecond());
    }

    /**
     * Khoá ký HMAC hiện tại. Nếu chưa cấu hình, dùng khoá dev fallback và cảnh báo.
     */
    private byte[] secretKey() {
        String configured = properties.getCursorSecret();
        if (configured == null || configured.isBlank()) {
            log.warn("[CursorCodec] SEARCH_CURSOR_SECRET not configured - using development fallback key");
            return SECRET_FALLBACK.getBytes(StandardCharsets.UTF_8);
        }
        if (configured.length() < MIN_SECRET_LENGTH) {
            log.warn("[CursorCodec] SEARCH_CURSOR_SECRET too short ({} chars) - at least {} recommended",
                    configured.length(), MIN_SECRET_LENGTH);
        }
        return configured.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey(), HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute cursor signature", e);
        }
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    /**
     * So sánh chuỗi constant-time — tránh rò rỉ thông tin qua timing.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    /**
     * Sinh chuỗi ngẫu nhiên — dùng cho PIT fallback khi không có dữ liệu.
     */
    public String randomId() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return base64UrlEncode(bytes);
    }
}
