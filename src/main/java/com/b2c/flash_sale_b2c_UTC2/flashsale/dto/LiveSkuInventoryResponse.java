package com.b2c.flash_sale_b2c_UTC2.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveSkuInventoryResponse {
    private Long id;
    private String skuCode;
    private String productName;
    private String variantName;
    private String storeName;
    private String categoryName;
    private Integer allocatedStock;
    private Integer availableStock;
    private Integer holdingStock;
    private Integer soldStock;
    private String status;
    private String icon;
}
