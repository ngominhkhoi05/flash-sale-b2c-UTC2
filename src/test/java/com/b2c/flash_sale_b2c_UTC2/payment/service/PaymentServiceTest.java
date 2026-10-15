package com.b2c.flash_sale_b2c_UTC2.payment.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.config.ZaloPayProperties;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsBroadcaster;
import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.PaymentResponse;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

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

        props.setAppId("2554");
        props.setKey1("test_key1");
        props.setKey2("test_key2");
        props.setCallbackUrl("https://example.com/cb");
        props.setAppUser("FlashSaleB2C");
    }

    @Test
    @DisplayName("getOrderPayment returns UNPAID when no payment record exists")
    void getOrderPayment_WhenNoPaymentRecord_ReturnsUnpaid() {
        when(orderRepository.findById(100L)).thenReturn(java.util.Optional.of(sampleOrder));
        when(paymentRepository.findByOrderId(100L)).thenReturn(List.of());

        PaymentResponse response = paymentService.getOrderPayment(1L, 100L);

        assertNotNull(response);
        assertEquals("UNPAID", response.getPaymentMethod());
        assertEquals("PENDING", response.getStatus());
        assertEquals(new BigDecimal("250000"), response.getAmount());
    }

    @Test
    @DisplayName("confirmCodPayment creates COD payment and transitions order to CONFIRMED")
    void createCodPayment_Success() {
        when(orderRepository.findById(100L)).thenReturn(java.util.Optional.of(sampleOrder));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.confirmCodPayment(1L, 100L);

        assertNotNull(response);
        assertEquals("COD", response.getPaymentMethod());
        assertEquals("PENDING", response.getStatus());
        assertEquals("CONFIRMED", sampleOrder.getStatus());
        verify(orderRepository).save(sampleOrder);
    }

    @Test
    @DisplayName("createZaloPayPayment calls ZaloPay and saves order_url + qr_code from response")
    void createZaloPayPayment_Success() {
        ZaloPayCreateOrderResponse zpResp = ZaloPayCreateOrderResponse.builder()
                .returnCode(1)
                .returnMessage("Success")
                .orderUrl("https://sb-openapi.zalopay.vn/pay?token=ABC")
                .qrCode("data:image/png;base64,XYZ")
                .zpTransToken("zp_tok")
                .build();

        when(orderRepository.findById(100L)).thenReturn(java.util.Optional.of(sampleOrder));
        when(zaloPayClient.createOrder(any())).thenReturn(zpResp);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(2L);
            return p;
        });

        PaymentResponse response = paymentService.createZaloPayPayment(1L, 100L);

        assertNotNull(response);
        assertEquals("ZALOPAY", response.getPaymentMethod());
        assertEquals("PENDING", response.getStatus());
        assertEquals("https://sb-openapi.zalopay.vn/pay?token=ABC", response.getPaymentUrl());
        assertEquals("data:image/png;base64,XYZ", response.getQrCodeUrl());
    }

    @Test
    @DisplayName("validateBuyerOrder throws exception for wrong buyer")
    void validateBuyerOrder_AccessDenied() {
        when(orderRepository.findById(100L)).thenReturn(java.util.Optional.of(sampleOrder));

        assertThrows(BusinessException.class, () -> paymentService.getOrderPayment(999L, 100L));
    }
}