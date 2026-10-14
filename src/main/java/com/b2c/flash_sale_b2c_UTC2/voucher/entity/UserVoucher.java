package com.b2c.flash_sale_b2c_UTC2.voucher.entity;

import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity đại diện cho bảng user_vouchers (Voucher đã lưu trong kho ví của người dùng).
 */
@Entity
@Table(name = "user_vouchers", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_voucher", columnNames = {"user_id", "voucher_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Column(name = "saved_at")
    @Builder.Default
    private Instant savedAt = Instant.now();
}
