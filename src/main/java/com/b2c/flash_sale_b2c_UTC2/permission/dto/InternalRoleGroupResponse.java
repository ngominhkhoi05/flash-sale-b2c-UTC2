package com.b2c.flash_sale_b2c_UTC2.permission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalRoleGroupResponse {
    private String id;
    private String name;
    private String code;
    private String description;
    private long memberCount;
    private List<String> permissions;
}
