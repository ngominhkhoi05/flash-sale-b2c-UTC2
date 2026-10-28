package com.b2c.flash_sale_b2c_UTC2.flashsale.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchApproveFlashSaleItemsRequest {

    @NotEmpty(message = "Danh sách ID không được rỗng")
    @JsonAlias({"ids", "item_ids"})
    private List<Long> itemIds;

    private java.math.BigDecimal commissionRateOverride;

    public BatchApproveFlashSaleItemsRequest(List<Long> itemIds) {
        this.itemIds = itemIds;
    }
}
