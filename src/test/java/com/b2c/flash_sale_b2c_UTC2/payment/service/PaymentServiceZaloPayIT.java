package com.b2c.flash_sale_b2c_UTC2.payment.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.ZaloPayCallbackDto;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.ZaloPayCreateOrderResponse;
import com.b2c.flash_sale_b2c_UTC2.payment.entity.Payment;
import com.b2c.flash_sale_b2c_UTC2.payment.repository.PaymentRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Integration test for ZaloPay payment flow.
 * Uses real PostgreSQL (AbstractPostgresIT). ZaloPayClient is replaced with a mock
 * via @MockitoBean so the real HTTP client is never invoked.
 * Profile chain: test + test-local (application-test-local.yaml).
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require&prepareThreshold=0",
        "spring.datasource.username=postgres.dygnssynjofuaaapnvcg",
        "spring.datasource.password=&zyXVX8TNLWYF!B"
})
class PaymentServiceZaloPayIT extends AbstractPostgresIT {

    private static final String ZALOPAY_KEY2 = "trMrHtvjo6myautxDUiAcYsVtaeQ8nhf";

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @MockitoBean
    private ZaloPayClient zaloPayClientMock;

    private User testUser;
    private Store testStore;
    private Order testOrder;

    @BeforeEach
    void setUpBuyerAndOrder() {
        testUser = userRepository.findByEmail("test_zalopay_it@example.com").orElseGet(() -> {
            User u = User.builder()
                    .email("test_zalopay_it@example.com")
                    .fullName("ZaloPay IT Test User")
                    .phone("0909000000")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            return userRepository.save(u);
        });

        if (testStore == null) {
            testStore = storeRepository.findAll().stream().findFirst().orElseGet(() -> {
                Store s = Store.builder()
                        .user(testUser)
                        .storeName("IT Test Store")
                        .status("APPROVED")
                        .build();
                return storeRepository.save(s);
            });
        }

        testOrder = orderRepository.save(Order.builder()
                .orderCode("ZPIT_" + System.currentTimeMillis())
                .buyer(testUser)
                .store(testStore)
                .totalAmount(new BigDecimal("150000"))
                .subtotalAmount(new BigDecimal("150000"))
                .commissionRate(new BigDecimal("0.0500"))
                .platformFee(new BigDecimal("7500"))
                .sellerAmount(new BigDecimal("142500"))
                .status("PENDING_PAYMENT")
                .recipientName(testUser.getFullName())
                .recipientPhone(testUser.getPhone())
                .shippingAddressText("123 IT Test Street")
                .expiresAt(Instant.now().plusSeconds(300))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("createZaloPayPayment returns pending payment with URL and QR code")
    void createZaloPayPayment_ReturnsPendingWithUrlAndQR() {
        ZaloPayCreateOrderResponse mockResp = ZaloPayCreateOrderResponse.builder()
                .returnCode(1)
                .returnMessage("Success")
                .orderUrl("https://sb-openapi.zalopay.vn/pay?token=MOCK_TOKEN_123")
                .qrCode("data:image/png;base64,MOCK_QR_DATA")
                .zpTransToken("zp_mock_token")
                .build();

        doReturn(mockResp).when(zaloPayClientMock).createOrder(any());

        var response = paymentService.createZaloPayPayment(testUser.getId(), testOrder.getId());

        assertNotNull(response);
        assertEquals("ZALOPAY", response.getPaymentMethod());
        assertEquals("PENDING", response.getStatus());
        assertEquals("https://sb-openapi.zalopay.vn/pay?token=MOCK_TOKEN_123", response.getPaymentUrl());
        assertNotNull(response.getQrCodeUrl());
        assertTrue(response.getQrCodeUrl().startsWith("data:image/png;base64,"));

        Optional<Payment> saved = paymentRepository.findByOrderId(testOrder.getId()).stream().findFirst();
        assertTrue(saved.isPresent());
        assertEquals("PENDING", saved.get().getStatus());
    }

    @Test
    @DisplayName("createZaloPayPayment throws when ZaloPay returns error code")
    void createZaloPayPayment_ThrowsOnZaloPayError() {
        ZaloPayCreateOrderResponse errorResp = ZaloPayCreateOrderResponse.builder()
                .returnCode(0)
                .returnMessage("Invalid amount")
                .build();

        doReturn(errorResp).when(zaloPayClientMock).createOrder(any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.createZaloPayPayment(testUser.getId(), testOrder.getId()));

        assertNotNull(ex.getErrorCode());
    }

    @Test
    @DisplayName("handleZaloPayCallback MAC mismatch returns return_code=-1")
    void handleZaloPayCallback_InvalidMac_ReturnsMinus1() {
        ZaloPayCallbackDto cb = ZaloPayCallbackDto.builder()
                .data("{\"app_id\":\"2554\"}")
                .mac("wrong_mac_here")
                .type("1")
                .build();

        Map<String, Object> result = paymentService.handleZaloPayCallback(cb);

        assertEquals(-1, result.get("return_code"));
        assertEquals("mac not equal", result.get("return_message"));
    }

    @Test
    @DisplayName("handleZaloPayCallback payment not found returns return_code=0")
    void handleZaloPayCallback_PaymentNotFound_Returns0() {
        String callbackData = "{\"app_id\":\"2554\",\"app_trans_id\":\"NOTFOUND_999\",\"zp_trans_id\":\"zpt_nf\",\"amount\":150000}";
        String validMac = ZaloPayClient.hmacSHA256Static(callbackData, ZALOPAY_KEY2);

        ZaloPayCallbackDto.ZaloPayCallbackData parsedData = new ZaloPayCallbackDto.ZaloPayCallbackData();
        parsedData.setAppId("2554");
        parsedData.setAppTransId("NOTFOUND_999");
        parsedData.setZpTransId("zpt_nf");
        parsedData.setAmount(150000L);

        when(zaloPayClientMock.verifyCallbackMac(any())).thenReturn(true);
        when(zaloPayClientMock.parseCallbackData(any())).thenReturn(parsedData);

        ZaloPayCallbackDto cb = ZaloPayCallbackDto.builder()
                .data(callbackData)
                .mac(validMac)
                .type("1")
                .build();

        Map<String, Object> result = paymentService.handleZaloPayCallback(cb);

        assertEquals(0, result.get("return_code"));
        assertTrue(result.get("return_message").toString().contains("not found"));
    }

    @Test
    @DisplayName("handleZaloPayCallback success: payment PENDING -> SUCCESS + order CONFIRMED")
    void handleZaloPayCallback_Success_UpdatesPaymentAndOrder() throws Exception {        ZaloPayCreateOrderResponse mockResp = ZaloPayCreateOrderResponse.builder()
                .returnCode(1)
                .returnMessage("Success")
                .orderUrl("https://sb-openapi.zalopay.vn/pay?token=SUCCESS_456")
                .qrCode("data:image/png;base64,SUCCESS_QR")
                .zpTransToken("zp_success_token_456")
                .build();

        doReturn(mockResp).when(zaloPayClientMock).createOrder(any());
        paymentService.createZaloPayPayment(testUser.getId(), testOrder.getId());

        // Read back the real app_trans_id stored in DB (service generates "ZP{timestamp}_{orderId}")
        Payment savedPayment = paymentRepository.findByOrderId(testOrder.getId()).stream().findFirst().orElseThrow();
        String appTransId = savedPayment.getTransactionCode();

        String callbackData = "{\"app_id\":\"2554\",\"app_trans_id\":\"" + appTransId
                + "\",\"zp_trans_id\":\"zpt_success\",\"amount\":150000}";
        String validMac = ZaloPayClient.hmacSHA256Static(callbackData, ZALOPAY_KEY2);

        stubCallbackParsing(callbackData, 2554L, appTransId, "zpt_success", 150000L);

        ZaloPayCallbackDto cb = ZaloPayCallbackDto.builder()
                .data(callbackData)
                .mac(validMac)
                .type("1")
                .build();

        Map<String, Object> result = paymentService.handleZaloPayCallback(cb);

        assertEquals(1, result.get("return_code"));
        assertEquals("Success", result.get("return_message"));

        Payment updatedPayment = paymentRepository.findByOrderId(testOrder.getId()).stream().findFirst().orElseThrow();
        assertEquals("SUCCESS", updatedPayment.getStatus());
        assertNotNull(updatedPayment.getPaidAt());
        assertEquals("zpt_success", updatedPayment.getGatewayTransId());

        Order refreshedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertEquals("CONFIRMED", refreshedOrder.getStatus());
    }

    @Test
    @DisplayName("handleZaloPayCallback idempotent: already SUCCESS payment returns 1 without update")
    void handleZaloPayCallback_Idempotent_Returns1WithoutUpdate() throws Exception {        ZaloPayCreateOrderResponse mockResp = ZaloPayCreateOrderResponse.builder()
                .returnCode(1)
                .returnMessage("Success")
                .orderUrl("https://sb-openapi.zalopay.vn/pay?token=IDEM_789")
                .qrCode("data:image/png;base64,IDEM_QR")
                .zpTransToken("zp_idem_token_789")
                .build();

        doReturn(mockResp).when(zaloPayClientMock).createOrder(any());
        paymentService.createZaloPayPayment(testUser.getId(), testOrder.getId());

        Payment savedPayment = paymentRepository.findByOrderId(testOrder.getId()).stream().findFirst().orElseThrow();
        String appTransId = savedPayment.getTransactionCode();

        // First callback: PENDING -> SUCCESS
        String callbackData = "{\"app_id\":\"2554\",\"app_trans_id\":\"" + appTransId
                + "\",\"zp_trans_id\":\"zpt_idem\",\"amount\":150000}";
        String validMac = ZaloPayClient.hmacSHA256Static(callbackData, ZALOPAY_KEY2);

        stubCallbackParsing(callbackData, 2554L, appTransId, "zpt_idem", 150000L);

        ZaloPayCallbackDto cb = ZaloPayCallbackDto.builder()
                .data(callbackData)
                .mac(validMac)
                .type("1")
                .build();

        Map<String, Object> firstResult = paymentService.handleZaloPayCallback(cb);
        assertEquals(1, firstResult.get("return_code"));

        // Second callback: same data, must return 1 idempotently without throwing
        Map<String, Object> secondResult = paymentService.handleZaloPayCallback(cb);
        assertEquals(1, secondResult.get("return_code"));
        assertEquals("Success", secondResult.get("return_message"));

        // Order stays CONFIRMED, no second transition
        Order refreshedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertEquals("CONFIRMED", refreshedOrder.getStatus());
    }

    /**
     * Stub the mocked ZaloPayClient so that:
     *  - verifyCallbackMac returns true
     *  - parseCallbackData returns a real callback data object built from the JSON
     * The real ObjectMapper from Spring is used to deserialize the JSON.
     */
    private void stubCallbackParsing(String callbackData, Long appId, String appTransId,
                                     String zpTransId, Long amount) throws Exception {
        ZaloPayCallbackDto.ZaloPayCallbackData parsed = new ZaloPayCallbackDto.ZaloPayCallbackData();
        parsed.setAppId(String.valueOf(appId));
        parsed.setAppTransId(appTransId);
        parsed.setZpTransId(zpTransId);
        parsed.setAmount(amount);
        when(zaloPayClientMock.verifyCallbackMac(any())).thenReturn(true);
        when(zaloPayClientMock.parseCallbackData(any())).thenReturn(parsed);
    }}