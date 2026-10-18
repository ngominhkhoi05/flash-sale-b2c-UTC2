package com.b2c.flash_sale_b2c_UTC2.payment.service;

import com.b2c.flash_sale_b2c_UTC2.config.ZaloPayProperties;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.ZaloPayCallbackDto;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.ZaloPayCreateOrderRequest;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.ZaloPayCreateOrderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ZaloPayClient {

    /**
     * Static HMAC-SHA256 helper (used by tests and other utility classes
     * that need to verify a MAC without holding a ZaloPayClient instance).
     */
    public static String hmacSHA256Static(String data, String key) {
        try {
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                    key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }


    private final RestTemplate restTemplate;
    private final ZaloPayProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZaloPayCreateOrderResponse createOrder(ZaloPayCreateOrderRequest req) {
        String macData = String.join("|",
                String.valueOf(req.getAppId()),
                req.getAppTransId(),
                req.getAppUser() == null ? "" : req.getAppUser(),
                String.valueOf(req.getAmount()),
                String.valueOf(req.getAppTime()),
                req.getEmbedData() == null ? "" : req.getEmbedData(),
                req.getItem() == null ? "" : req.getItem()
        );
        String mac = hmacSHA256(macData, props.getKey1());
        req.setMac(mac);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("app_id", String.valueOf(req.getAppId()));
        form.add("app_trans_id", req.getAppTransId());
        form.add("app_user", req.getAppUser());
        form.add("amount", String.valueOf(req.getAmount()));
        form.add("app_time", String.valueOf(req.getAppTime()));
        form.add("embed_data", req.getEmbedData() == null ? "{}" : req.getEmbedData());
        form.add("item", req.getItem() == null ? "[]" : req.getItem());
        form.add("description", req.getDescription());
        form.add("bank_code", req.getBankCode() == null ? "" : req.getBankCode());
        form.add("callback_url", req.getCallbackUrl() == null ? "" : req.getCallbackUrl());
        form.add("mac", mac);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        log.info("[ZaloPay] POST /v2/create app_trans_id={} amount={}", req.getAppTransId(), req.getAmount());
        try {
            ResponseEntity<ZaloPayCreateOrderResponse> resp = restTemplate.postForEntity(
                    props.getEndpoint(), entity, ZaloPayCreateOrderResponse.class);
            ZaloPayCreateOrderResponse body = resp.getBody();
            if (body == null) {
                log.error("[ZaloPay] Empty response from /v2/create");
                return ZaloPayCreateOrderResponse.builder()
                        .returnCode(0)
                        .returnMessage("Empty response from ZaloPay")
                        .build();
            }
            log.info("[ZaloPay] /v2/create response: return_code={} message={}",
                    body.getReturnCode(), body.getReturnMessage());
            return body;
        } catch (RestClientException e) {
            log.error("[ZaloPay] /v2/create failed: {}", e.getMessage(), e);
            return ZaloPayCreateOrderResponse.builder()
                    .returnCode(0)
                    .returnMessage("HTTP error: " + e.getMessage())
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> queryOrder(String appTransId) {
        String queryUrl = props.getEndpoint().replace("/create", "/query");
        String macData = String.join("|", String.valueOf(props.getAppId()), appTransId, props.getKey1());
        String mac = hmacSHA256(macData, props.getKey1());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("app_id", String.valueOf(props.getAppId()));
        form.add("app_trans_id", appTransId);
        form.add("mac", mac);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        log.info("[ZaloPay] POST /v2/query app_trans_id={}", appTransId);
        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(queryUrl, entity, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                return Map.of("return_code", 0, "return_message", "Empty response from ZaloPay");
            }
            log.info("[ZaloPay] /v2/query response: return_code={} message={}",
                    body.get("return_code"), body.get("return_message"));
            return body;
        } catch (RestClientException e) {
            log.error("[ZaloPay] /v2/query failed: {}", e.getMessage(), e);
            return Map.of("return_code", 0, "return_message", "HTTP error: " + e.getMessage());
        }
    }

    public boolean verifyCallbackMac(ZaloPayCallbackDto cb) {
        if (cb == null || cb.getData() == null || cb.getMac() == null) {
            return false;
        }
        String expected = hmacSHA256(cb.getData(), props.getKey2());
        boolean match = constantTimeEquals(expected, cb.getMac());
        log.info("[ZaloPay] Callback MAC verify: match={}", match);
        return match;
    }

    public ZaloPayCallbackDto.ZaloPayCallbackData parseCallbackData(String data) {
        try {
            return objectMapper.readValue(data, ZaloPayCallbackDto.ZaloPayCallbackData.class);
        } catch (Exception e) {
            log.error("[ZaloPay] Failed to parse callback data: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid callback data format", e);
        }
    }

    public String hmacSHA256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}