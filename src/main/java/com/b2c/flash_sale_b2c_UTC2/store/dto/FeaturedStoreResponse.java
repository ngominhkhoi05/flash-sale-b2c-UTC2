package com.b2c.flash_sale_b2c_UTC2.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO phan hoi thong tin gian hang noi bat cho trang chu.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedStoreResponse {

    private Long id;

    private String name;

    private String logoUrl;

    private Boolean verified;

    private Double rating;

    private Long totalRatings;

    private Long followers;

    private List<String> productThumbnails;

    private Integer totalProducts;

    private Instant createdAt;
}
