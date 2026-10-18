package com.b2c.flash_sale_b2c_UTC2.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZaloPayCreateOrderResponse {

    @JsonProperty("return_code")
    private Integer returnCode;

    @JsonProperty("return_message")
    private String returnMessage;

    @JsonProperty("order_url")
    private String orderUrl;

    @JsonProperty("order_token")
    private String orderToken;

    @JsonProperty("zp_trans_token")
    private String zpTransToken;

    @JsonProperty("qr_code")
    private String qrCode;

    @JsonProperty("app_id")
    private String appId;

    @JsonProperty("app_trans_id")
    private String appTransId;

    @JsonProperty("app_time")
    private Long appTime;

    @JsonProperty("expire_time")
    private Long expireTime;

    @JsonProperty("amount")
    private Long amount;
}