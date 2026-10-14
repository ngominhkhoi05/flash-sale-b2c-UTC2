package com.b2c.flash_sale_b2c_UTC2.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrOrderDetailResponse {
    private String orderCode;
    private String qrCodeUrl;
    private Instant expiresAt;
    private Instant paymentExpiry;
    private BigDecimal amount;
    private BigDecimal totalAmount;
    private String paymentUrl;
    private List<QrShopOrderResponse> shopOrders;
}
