package com.b2c.flash_sale_b2c_UTC2.permission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclPermissionResponse {
    private String code;
    private String name;
    private String module; // PRODUCT, FLASH_SALE, ORDER_VOUCHER, SYSTEM
    private boolean isSensitive;
    private boolean isReadOnly;
}
