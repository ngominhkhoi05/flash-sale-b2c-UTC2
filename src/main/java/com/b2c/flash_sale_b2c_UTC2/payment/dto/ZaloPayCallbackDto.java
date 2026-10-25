package com.b2c.flash_sale_b2c_UTC2.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
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
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZaloPayCallbackData {
        @JsonProperty("app_id")
        private String appId;

        @JsonProperty("app_trans_id")
        private String appTransId;

        @JsonProperty("app_time")
        private Long appTime;

        @JsonProperty("app_user")
        private String appUser;

        @JsonProperty("amount")
        private Long amount;

        @JsonProperty("embed_data")
        private String embedData;

        @JsonProperty("item")
        private String item;

        @JsonProperty("zp_trans_id")
        private String zpTransId;

        @JsonProperty("server_time")
        private Long serverTime;

        @JsonProperty("channel")
        private Integer channel;

        @JsonProperty("merchant_user_id")
        private String merchantUserId;

        @JsonProperty("user_fee_amount")
        private Long userFeeAmount;

        @JsonProperty("discount_amount")
        private Long discountAmount;
    }
}