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
public class ZaloPayCreateOrderRequest {

    @JsonProperty("app_id")
    private String appId;

    @JsonProperty("app_trans_id")
    private String appTransId;

    @JsonProperty("app_user")
    private String appUser;

    @JsonProperty("amount")
    private long amount;

    @JsonProperty("app_time")
    private long appTime;

    @JsonProperty("embed_data")
    private String embedData;

    @JsonProperty("item")
    private String item;

    @JsonProperty("description")
    private String description;

    @JsonProperty("bank_code")
    private String bankCode;

    @JsonProperty("callback_url")
    private String callbackUrl;

    @JsonProperty("mac")
    private String mac;
}