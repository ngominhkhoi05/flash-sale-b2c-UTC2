package com.b2c.flash_sale_b2c_UTC2.payment.service;

import com.b2c.flash_sale_b2c_UTC2.config.ZaloPayProperties;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.ZaloPayCallbackDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

class ZaloPayClientTest {

    private ZaloPayProperties props;
    private ZaloPayClient client;

    @BeforeEach
    void setUp() {
        props = new ZaloPayProperties();
        props.setAppId("2554");
        props.setKey1("test_key1_abc123");
        props.setKey2("test_key2_xyz789");
        props.setEndpoint("https://sb-openapi.zalopay.vn/v2/create");
        props.setCallbackUrl("https://example.com/callback");
        props.setAppUser("FlashSaleB2C");

        client = new ZaloPayClient(new RestTemplate(), props);
    }

    @Test
    @DisplayName("hmacSHA256 returns 64-char hex lowercase")
    void hmacSHA256_HappyPath() {
        String result = client.hmacSHA256("hello", "secret");
        assertNotNull(result);
        assertEquals(64, result.length(), "SHA-256 hex must have 64 chars");
        assertTrue(result.matches("[0-9a-f]+"), "Must be lowercase hex");
    }

    @Test
    @DisplayName("hmacSHA256 with empty input returns non-null")
    void hmacSHA256_EmptyInput() {
        String result = client.hmacSHA256("", "secret");
        assertNotNull(result);
        assertEquals(64, result.length());
        String result2 = client.hmacSHA256("non-empty", "secret");
        assertNotEquals(result, result2);
    }

    @Test
    @DisplayName("hmacSHA256 is deterministic: same input+key -> same output")
    void hmacSHA256_Deterministic() {
        String result1 = client.hmacSHA256("data", "key");
        String result2 = client.hmacSHA256("data", "key");
        assertEquals(result1, result2);
    }

    @Test
    @DisplayName("hmacSHA256 different key -> different output")
    void hmacSHA256_DifferentKey() {
        String result1 = client.hmacSHA256("data", "key1");
        String result2 = client.hmacSHA256("data", "key2");
        assertNotEquals(result1, result2);
    }

    @Test
    @DisplayName("verifyCallbackMac returns true when MAC is valid")
    void verifyCallbackMac_Valid() {
        String data = "{\"app_id\":\"2554\",\"app_trans_id\":\"ZP123\"}";
        String mac = client.hmacSHA256(data, props.getKey2());

        ZaloPayCallbackDto cb = ZaloPayCallbackDto.builder()
                .data(data)
                .mac(mac)
                .type("1")
                .build();

        assertTrue(client.verifyCallbackMac(cb));
    }

    @Test
    @DisplayName("verifyCallbackMac returns false when MAC is invalid")
    void verifyCallbackMac_Invalid() {
        ZaloPayCallbackDto cb = ZaloPayCallbackDto.builder()
                .data("{\"app_id\":\"2554\"}")
                .mac("0000000000000000000000000000000000000000000000000000000000000000")
                .type("1")
                .build();

        assertFalse(client.verifyCallbackMac(cb));
    }

    @Test
    @DisplayName("verifyCallbackMac returns false when data/mac is null")
    void verifyCallbackMac_NullFields() {
        ZaloPayCallbackDto cbNull = ZaloPayCallbackDto.builder().build();
        assertFalse(client.verifyCallbackMac(cbNull));

        ZaloPayCallbackDto cbNullMac = ZaloPayCallbackDto.builder()
                .data("data")
                .mac(null)
                .build();
        assertFalse(client.verifyCallbackMac(cbNullMac));
    }

    @Test
    @DisplayName("parseCallbackData correctly parses snake_case JSON from real ZaloPay callback")
    void parseCallbackData_SnakeCaseJson_Success() {
        String json = "{\"app_id\":2554,\"app_trans_id\":\"261025_ORD100_12345\",\"app_time\":1729850000000,"
                + "\"app_user\":\"FlashSaleB2C\",\"amount\":150000,\"zp_trans_id\":261025000012345,"
                + "\"server_time\":1729850005000,\"channel\":38,\"merchant_user_id\":\"buyer1\"}";

        ZaloPayCallbackDto.ZaloPayCallbackData data = client.parseCallbackData(json);

        assertNotNull(data);
        assertEquals("2554", data.getAppId());
        assertEquals("261025_ORD100_12345", data.getAppTransId());
        assertEquals(1729850000000L, data.getAppTime());
        assertEquals("FlashSaleB2C", data.getAppUser());
        assertEquals(150000L, data.getAmount());
        assertEquals("261025000012345", data.getZpTransId());
        assertEquals(1729850005000L, data.getServerTime());
        assertEquals(38, data.getChannel());
        assertEquals("buyer1", data.getMerchantUserId());
    }
}