package com.b2c.flash_sale_b2c_UTC2.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingDistributionDto {

    @JsonProperty("stars5")
    @Builder.Default
    private Long stars5 = 0L;

    @JsonProperty("stars4")
    @Builder.Default
    private Long stars4 = 0L;

    @JsonProperty("stars3")
    @Builder.Default
    private Long stars3 = 0L;

    @JsonProperty("stars2")
    @Builder.Default
    private Long stars2 = 0L;

    @JsonProperty("stars1")
    @Builder.Default
    private Long stars1 = 0L;
}