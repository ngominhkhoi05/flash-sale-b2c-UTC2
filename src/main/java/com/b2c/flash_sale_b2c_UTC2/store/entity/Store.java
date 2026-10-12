package com.b2c.flash_sale_b2c_UTC2.store.entity;

import com.b2c.flash_sale_b2c_UTC2.store.enums.StoreTier;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity đại diện cho bảng stores (Gian hàng của Người bán).
 */
@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "store_name", nullable = false, unique = true, length = 150)
    private String storeName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "default_commission_rate", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal defaultCommissionRate = new BigDecimal("0.0500");

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "PENDING";

    /**
     * Store tier for B2C marketplace badges.
     * NORMAL = regular store, OFFICIAL_MALL = official mall store, PREFERRED = preferred store.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 20, nullable = false)
    @Builder.Default
    private StoreTier tier = StoreTier.NORMAL;

    /**
     * Last active timestamp of the store (for store profile display).
     */
    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "created_at")
    private Instant createdAt;
}
