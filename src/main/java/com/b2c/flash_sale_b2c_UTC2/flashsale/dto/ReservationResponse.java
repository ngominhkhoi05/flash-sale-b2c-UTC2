package com.b2c.flash_sale_b2c_UTC2.flashsale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ReservationResponse {
    private Long orderId;
    private String orderCode;
    private String reservationId;
    private Long flashSaleItemId;
    private Integer quantity;
    private BigDecimal flashSalePrice;
    private BigDecimal totalAmount;
    private String status;
    private Instant expiresAt;
    private String message;

    @JsonProperty("reservationId")
    public String getReservationId() {
        return reservationId != null ? reservationId : orderCode;
    }
}
