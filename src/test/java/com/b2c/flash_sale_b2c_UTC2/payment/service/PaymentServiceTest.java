package com.b2c.flash_sale_b2c_UTC2.payment.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.PaymentResponse;
import com.b2c.flash_sale_b2c_UTC2.payment.entity.Payment;
import com.b2c.flash_sale_b2c_UTC2.payment.repository.PaymentRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

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
    @DisplayName("Lấy trạng thái payment khi chưa có payment record")
    void getOrderPayment_WhenNoPaymentRecord_ReturnsUnpaid() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.findByOrderId(100L)).thenReturn(List.of());

        PaymentResponse response = paymentService.getOrderPayment(1L, 100L);

        assertNotNull(response);
        assertEquals("UNPAID", response.getPaymentMethod());
        assertEquals("PENDING", response.getStatus());
        assertEquals(new BigDecimal("250000"), response.getAmount());
    }

    @Test
    @DisplayName("Tạo payment COD thành công và cập nhật trạng thái đơn hàng")
    void createCodPayment_Success() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));
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
    @DisplayName("Tạo payment ZaloPay thành công trả về QR code và payment url")
    void createZaloPayPayment_Success() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(2L);
            return p;
        });

        PaymentResponse response = paymentService.createZaloPayPayment(1L, 100L);

        assertNotNull(response);
        assertEquals("ZALOPAY", response.getPaymentMethod());
        assertEquals("PENDING", response.getStatus());
        assertNotNull(response.getPaymentUrl());
        assertNotNull(response.getQrCodeUrl());
    }

    @Test
    @DisplayName("Không cho phép thanh toán đơn hàng của người khác")
    void validateBuyerOrder_AccessDenied() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));

        assertThrows(BusinessException.class, () -> paymentService.getOrderPayment(999L, 100L));
    }
}
