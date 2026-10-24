package com.b2c.flash_sale_b2c_UTC2.product.entity;

import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Entity đại diện cho bảng products (Thông tin sản phẩm gốc - SPU).
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tier_variation_configs", columnDefinition = "jsonb")
    private java.util.List<com.b2c.flash_sale_b2c_UTC2.product.dto.TierVariationConfigDto> tierVariationConfigs;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "slug", length = 255)
    private String slug;

    @Column(name = "total_sold")
    @Builder.Default
    private Integer totalSold = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specs", columnDefinition = "jsonb")
    private Object specs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb")
    private Object features;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "size_chart", columnDefinition = "jsonb")
    private Object sizeChart;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "commitments", columnDefinition = "jsonb")
    private Object commitments;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_rich", columnDefinition = "jsonb")
    private Object descriptionRich;

    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * Thời điểm cập nhật gần nhất — dùng cho reconcile định kỳ của OpenSearch
     * (quét sản phẩm thay đổi theo {@code updated_at}).
     */
    @Column(name = "updated_at")
    private Instant updatedAt;
}
