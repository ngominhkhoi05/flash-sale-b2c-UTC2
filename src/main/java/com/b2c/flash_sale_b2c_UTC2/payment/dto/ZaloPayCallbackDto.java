package com.b2c.flash_sale_b2c_UTC2.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZaloPayCallbackDto {

    private String data;
    private String mac;
    private String type;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZaloPayCallbackData {
        private String appId;
        private String appTransId;
        private Long appTime;
        private String appUser;
        private Long amount;
        private String zpTransId;
        private Long serverTime;
        private Integer channel;
        private String merchantUserId;
        private Long userFeeAmount;
        private Long discountAmount;
    }
}