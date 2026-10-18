package com.b2c.flash_sale_b2c_UTC2.payment.service;

import com.b2c.flash_sale_b2c_UTC2.config.ZaloPayProperties;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsBroadcaster;
import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.ZaloPayCallbackDto;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.ZaloPayCreateOrderResponse;
import com.b2c.flash_sale_b2c_UTC2.payment.entity.Payment;
import com.b2c.flash_sale_b2c_UTC2.payment.repository.PaymentRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceZaloPayTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ZaloPayClient zaloPayClient;

    @Mock
    private FlashSaleWsBroadcaster wsBroadcaster;

    @Spy
    private ZaloPayProperties props = new ZaloPayProperties();

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User sampleUser;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder().id(1L).email("buyer@example.com").build();
        sampleOrder = Order.builder()
                .id(100L)
                .orderCode("ORD123456")
                .buyer(sampleUser)
                .totalAmount(new BigDecimal("250000"))
                .status("PENDING_PAYMENT")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Callback with invalid MAC returns return_code=-1")
    void handleCallback_InvalidMac_ReturnsMinus1() {
        ZaloPayCallbackDto cb = ZaloPayCallbackDto.builder()
                .data("{\"app_id\":\"2554\"}")
                .mac("invalid_mac")
                .type("1")
                .build();

        when(zaloPayClient.verifyCallbackMac(cb)).thenReturn(false);

        Map<String, Object> result = paymentService.handleZaloPayCallback(cb);

        assertEquals(-1, result.get("return_code"));
        assertEquals("mac not equal", result.get("return_message"));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Callback with valid MAC but payment already SUCCESS -> idempotent return 1")
    void handleCallback_PaymentAlreadySuccess_Idempotent() {
        String dataJson = "{\"app_id\":\"2554\",\"app_trans_id\":\"ZP123\",\"zp_trans_id\":\"zpt_001\",\"amount\":250000}";
        ZaloPayCallbackDto cb = ZaloPayCallbackDto.builder()
                .data(dataJson)
                .mac("valid_mac")
                .type("1")
                .build();

        Payment existingPayment = Payment.builder()
                .id(10L)
                .order(sampleOrder)
                .paymentMethod("ZALOPAY")
                .transactionCode("ZP123")
                .amount(new BigDecimal("250000"))
                .status("SUCCESS")
                .paidAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(zaloPayClient.verifyCallbackMac(cb)).thenReturn(true);
        when(zaloPayClient.parseCallbackData(dataJson)).thenReturn(
                ZaloPayCallbackDto.ZaloPayCallbackData.builder()
                        .appId("2554")
                        .appTransId("ZP123")
                        .zpTransId("zpt_001")
                        .amount(250000L)
                        .build());
        when(paymentRepository.findByTransactionCode("ZP123")).thenReturn(Optional.of(existingPayment));

        Map<String, Object> result = paymentService.handleZaloPayCallback(cb);

        assertEquals(1, result.get("return_code"));
        assertEquals("Success", result.get("return_message"));
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Callback success: PENDING -> SUCCESS + order CONFIRMED + WS push")
    void handleCallback_SuccessPath() {
        String dataJson = "{\"app_id\":\"2554\",\"app_trans_id\":\"ZP_NEW\",\"zp_trans_id\":\"zpt_new\",\"amount\":250000}";
        ZaloPayCallbackDto cb = ZaloPayCallbackDto.builder()
                .data(dataJson)
                .mac("valid_mac")
                .type("1")
                .build();

        Payment pendingPayment = Payment.builder()
                .id(20L)
                .order(sampleOrder)
                .paymentMethod("ZALOPAY")
                .transactionCode("ZP_NEW")
                .amount(new BigDecimal("250000"))
                .status("PENDING")
                .createdAt(Instant.now())
                .build();

        when(zaloPayClient.verifyCallbackMac(cb)).thenReturn(true);
        when(zaloPayClient.parseCallbackData(dataJson)).thenReturn(
                ZaloPayCallbackDto.ZaloPayCallbackData.builder()
                        .appId("2554")
                        .appTransId("ZP_NEW")
                        .zpTransId("zpt_new")
                        .amount(250000L)
                        .build());
        when(paymentRepository.findByTransactionCode("ZP_NEW")).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = paymentService.handleZaloPayCallback(cb);

        assertEquals(1, result.get("return_code"));
        assertEquals("Success", result.get("return_message"));
        assertEquals("SUCCESS", pendingPayment.getStatus());
        assertNotNull(pendingPayment.getPaidAt());
        assertEquals("zpt_new", pendingPayment.getGatewayTransId());
        assertEquals("CONFIRMED", sampleOrder.getStatus());
        verify(paymentRepository).save(pendingPayment);
        verify(orderRepository).save(sampleOrder);
        verify(wsBroadcaster).sendPaymentResultToUser(
                eq("buyer@example.com"), eq("ORD123456"), eq(100L), eq("ZALOPAY"), eq("SUCCESS"));
    }

    @Test
    @DisplayName("Callback with payment not found -> return_code=0")
    void handleCallback_PaymentNotFound() {
        String dataJson = "{\"app_id\":\"2554\",\"app_trans_id\":\"ZP_MISSING\"}";
        ZaloPayCallbackDto cb = ZaloPayCallbackDto.builder()
                .data(dataJson)
                .mac("valid_mac")
                .type("1")
                .build();

        when(zaloPayClient.verifyCallbackMac(cb)).thenReturn(true);
        when(zaloPayClient.parseCallbackData(dataJson)).thenReturn(
                ZaloPayCallbackDto.ZaloPayCallbackData.builder()
                        .appId("2554")
                        .appTransId("ZP_MISSING")
                        .build());
        when(paymentRepository.findByTransactionCode("ZP_MISSING")).thenReturn(Optional.empty());

        Map<String, Object> result = paymentService.handleZaloPayCallback(cb);

        assertEquals(0, result.get("return_code"));
        assertTrue(result.get("return_message").toString().contains("not found"));
    }

    @Test
    @DisplayName("createZaloPayPayment calls real ZaloPay, saves order_url + qr_code from response")
    void createZaloPayPayment_RealZaloPay() {
        props.setAppId("2554");
        props.setKey1("test_key1");
        props.setKey2("test_key2");
        props.setCallbackUrl("https://example.com/cb");
        props.setAppUser("FlashSaleB2C");

        ZaloPayCreateOrderResponse zpResp = ZaloPayCreateOrderResponse.builder()
                .returnCode(1)
                .returnMessage("Success")
                .orderUrl("https://sb-openapi.zalopay.vn/pay?token=REAL_TOKEN")
                .qrCode("data:image/png;base64,REAL_QR_DATA")
                .zpTransToken("REAL_ZP_TOKEN")
                .build();

        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));
        when(zaloPayClient.createOrder(any())).thenReturn(zpResp);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });

        var response = paymentService.createZaloPayPayment(1L, 100L);

        assertEquals("ZALOPAY", response.getPaymentMethod());
        assertEquals("PENDING", response.getStatus());
        assertEquals("https://sb-openapi.zalopay.vn/pay?token=REAL_TOKEN", response.getPaymentUrl());
        assertEquals("data:image/png;base64,REAL_QR_DATA", response.getQrCodeUrl());
        verify(zaloPayClient).createOrder(any());
    }

    @Test
    @DisplayName("createZaloPayPayment throws when ZaloPay returns return_code != 1")
    void createZaloPayPayment_ZaloPayFails_ThrowsException() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));
        when(zaloPayClient.createOrder(any())).thenReturn(
                ZaloPayCreateOrderResponse.builder()
                        .returnCode(0)
                        .returnMessage("Invalid amount")
                        .build());

        assertThrows(com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException.class,
                () -> paymentService.createZaloPayPayment(1L, 100L));
    }
}