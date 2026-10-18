package com.b2c.flash_sale_b2c_UTC2.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchApproveFlashSaleItemsResponse {

    @Builder.Default
    private List<Long> approvedItemIds = Collections.emptyList();

    @Builder.Default
    private List<BatchApproveFailedItem> failedReasons = Collections.emptyList();
}
