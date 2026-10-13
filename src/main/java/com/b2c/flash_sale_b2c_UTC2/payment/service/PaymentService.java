package com.b2c.flash_sale_b2c_UTC2.payment.service;

import com.b2c.flash_sale_b2c_UTC2.payment.dto.PaymentResponse;

public interface PaymentService {
    PaymentResponse getOrderPayment(Long buyerId, Long orderId);
    PaymentResponse createZaloPayPayment(Long buyerId, Long orderId);
    PaymentResponse confirmCodPayment(Long buyerId, Long orderId);
}
