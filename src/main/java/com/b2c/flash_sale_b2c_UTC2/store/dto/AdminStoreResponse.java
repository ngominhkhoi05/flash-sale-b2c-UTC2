package com.b2c.flash_sale_b2c_UTC2.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStoreResponse {
    private Long id;
    private String storeName;
    private String name;
    private String slug;
    private String description;
    private String legalName;
    private BigDecimal defaultCommissionRate;
    private String status;
    private String tier;
    private String logoUrl;
    private String avatarUrl;
    private String bannerUrl;
    private String ownerEmail;
    private String ownerFullName;
    private String ownerName;
    private String ownerPhone;
    private String phone;
    private Long productsCount;
    private Long totalProducts;
    private Long ordersCount;
    private Long totalOrders;
    private Double ratingAvg;
    private Instant createdAt;
}
