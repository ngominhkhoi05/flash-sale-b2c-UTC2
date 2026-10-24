package com.b2c.flash_sale_b2c_UTC2.search.cursor;

import com.b2c.flash_sale_b2c_UTC2.search.client.OpenSearchProperties;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchCursor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests cho {@link CursorCodec} — mã hoá/giải mã cursor + HMAC tamper detection.
 */
class CursorCodecTest {

    private static final String SECRET = "test-search-cursor-secret-value-0123456789";

    private CursorCodec cursorCodec;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        OpenSearchProperties properties = new OpenSearchProperties();
        properties.setCursorSecret(SECRET);
        cursorCodec = new CursorCodec(mapper, properties);
    }

    @Test
    @DisplayName("encode -> decode: round-trip giữ nguyên toàn bộ payload")
    void encodeDecode_roundTrip() {
        SearchCursor original = cursorCodec.newCursor(
                SearchCursor.CursorMode.OS_PIT,
                "pit-123",
                List.of(12.5, 99L),
                24,
                "relevance",
                "fp-abc");

        String token = cursorCodec.encode(original);
        assertNotNull(token);
        assertTrue(token.contains("."), "token phai co dang <payload>.<signature>");

        SearchCursor decoded = cursorCodec.decode(token);
        assertNotNull(decoded);
        assertEquals(SearchCursor.CursorMode.OS_PIT, decoded.mode());
        assertEquals("pit-123", decoded.pitId());
        assertEquals(24, decoded.offset());
        assertEquals("relevance", decoded.sortKey());
        assertEquals("fp-abc", decoded.fingerprint());
        assertEquals(original.issuedAt(), decoded.issuedAt());
    }

    @Test
    @DisplayName("decode null/blank token -> null (khong throw)")
    void decode_nullOrBlank_returnsNull() {
        assertNull(cursorCodec.decode(null));
        assertNull(cursorCodec.decode(""));
        assertNull(cursorCodec.decode("   "));
    }

    @Test
    @DisplayName("decode token tampered -> null (chuyen sang trang dau)")
    void decode_tamperedPayload_rejected() {
        SearchCursor original = cursorCodec.newCursor(
                SearchCursor.CursorMode.OS_PIT, "pit-1", List.of(1L), 0, "relevance", "fp");
        String token = cursorCodec.encode(original);

        // Do hai vao payload -> chu ky khong con hop le
        int sep = token.indexOf('.');
        String payload = token.substring(0, sep);
        String tamperedPayload = "X" + payload.substring(1);

        assertNull(cursorCodec.decode(tamperedPayload + token.substring(sep)));
    }

    @Test
    @DisplayName("decode token sai chu ky -> null")
    void decode_wrongSignature_rejected() {
        SearchCursor original = cursorCodec.newCursor(
                SearchCursor.CursorMode.OS_PIT, "pit-1", List.of(1L), 0, "relevance", "fp");
        String token = cursorCodec.encode(original);
        int sep = token.indexOf('.');
        String payload = token.substring(0, sep);
        String signature = token.substring(sep + 1);

        String forgedSignature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("not-the-real-signature".getBytes(StandardCharsets.UTF_8));

        assertNull(cursorCodec.decode(payload + "." + forgedSignature));
        assertNull(cursorCodec.decode(payload + "." + signature.substring(1) + "X"));
    }

    @Test
    @DisplayName("decode token khong dung dinh dang -> null")
    void decode_malformedToken_rejected() {
        assertNull(cursorCodec.decode("no-separator"));
        assertNull(cursorCodec.decode(".only-signature"));
        assertNull(cursorCodec.decode("only-payload."));
    }

    @Test
    @DisplayName("decode token sai version -> null")
    void decode_unsupportedVersion_rejected() throws Exception {
        SearchCursor payload = new SearchCursor(
                SearchCursor.CURRENT_VERSION + 99,
                SearchCursor.CursorMode.OS_PIT, "pit", List.of(), 0, "relevance", "fp", 1L);
        String token = cursorCodec.encode(payload);
        assertNotNull(token);
        assertNull(cursorCodec.decode(token), "cursor sai version phai bi tu choi");
    }

    @Test
    @DisplayName("fingerprint: cung input -> cung output, khac input -> khac output")
    void fingerprint_stableAndSensitive() {
        String a = cursorCodec.fingerprint("abc", "def");
        String b = cursorCodec.fingerprint("abc", "def");
        String c = cursorCodec.fingerprint("abc", "deg");

        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("fingerprint null part -> khong throw")
    void fingerprint_nullPart_safe() {
        assertNotNull(cursorCodec.fingerprint(null, "x"));
        assertNotNull(cursorCodec.fingerprint());
    }

    @Test
    @DisplayName("randomId: sinh gia tri khac nhau")
    void randomId_unique() {
        assertNotEquals(cursorCodec.randomId(), cursorCodec.randomId());
    }
}
