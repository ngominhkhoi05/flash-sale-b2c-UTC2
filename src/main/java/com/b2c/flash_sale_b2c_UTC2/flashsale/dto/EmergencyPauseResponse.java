package com.b2c.flash_sale_b2c_UTC2.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyPauseResponse {
    private Long slotId;
    private String status;
    private Boolean isPaused;
    private Boolean stockRolledBack;
    private Integer rolledBackItemsCount;
    private Integer totalStockReturned;
    private String message;
}
