package com.b2c.flash_sale_b2c_UTC2.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private String orderCode;
    private String paymentMethod;
    private String transactionCode;
    private BigDecimal amount;
    private String qrCodeUrl;
    private String paymentUrl;
    private String status;
    private Instant paidAt;
    private Instant createdAt;
}
