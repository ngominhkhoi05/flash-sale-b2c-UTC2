package com.b2c.flash_sale_b2c_UTC2.flashsale.port;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Real database implementation of FlashSaleOrderPort directly querying and inserting into PostgreSQL
 * tables `orders` and `order_items`. Used for integration tests on real PostgreSQL.
 */
@Slf4j
@Component
@Primary
@Profile("test-local")
@RequiredArgsConstructor
public class JdbcFlashSaleOrderPort implements FlashSaleOrderPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public OrderRef createPendingOrder(CreateFlashSaleOrderCommand cmd) {
        String insertOrderSql = """
            INSERT INTO orders (
                order_code, buyer_id, store_id, slot_id, shipping_address_id,
                recipient_name, recipient_phone, shipping_address_text,
                subtotal_amount, voucher_discount_amount, total_amount,
                commission_rate, platform_fee, seller_amount,
                status, expires_at, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_PAYMENT', ?, NOW(), NOW())
        """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        BigDecimal subtotal = cmd.unitPrice().multiply(BigDecimal.valueOf(cmd.quantity()));
        BigDecimal commissionRate = new BigDecimal("0.0500");
        BigDecimal platformFee = subtotal.multiply(commissionRate);
        BigDecimal sellerAmount = subtotal.subtract(platformFee);

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertOrderSql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, cmd.orderCode());
            ps.setLong(2, cmd.userId());
            ps.setLong(3, cmd.storeId());
            if (cmd.slotId() != null) ps.setLong(4, cmd.slotId()); else ps.setNull(4, java.sql.Types.BIGINT);
            ps.setLong(5, cmd.addressId());
            ps.setString(6, "Nguoi nhan");
            ps.setString(7, "0981234567");
            ps.setString(8, "450 Le Van Viet, TP. Thu Duc, TP.HCM");
            ps.setBigDecimal(9, subtotal);
            ps.setBigDecimal(10, BigDecimal.ZERO);
            ps.setBigDecimal(11, subtotal);
            ps.setBigDecimal(12, commissionRate);
            ps.setBigDecimal(13, platformFee);
            ps.setBigDecimal(14, sellerAmount);
            ps.setTimestamp(15, Timestamp.from(cmd.expiresAt()));
            return ps;
        }, keyHolder);

        Number orderIdNum = (Number) keyHolder.getKeys().get("id");
        Long orderId = orderIdNum != null ? orderIdNum.longValue() : 1L;

        String insertItemSql = """
            INSERT INTO order_items (
                order_id, flash_sale_item_id, variant_id,
                product_name, variant_name, price_at_purchase, quantity
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        jdbcTemplate.update(
                insertItemSql,
                orderId,
                cmd.flashSaleItemId(),
                cmd.variantId(),
                cmd.productName(),
                cmd.variantName(),
                cmd.unitPrice(),
                cmd.quantity()
        );

        return new OrderRef(orderId, cmd.orderCode());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ExpiredOrderRef> lockExpiredPendingOrders(Instant now, int batchSize) {
        String query = """
            SELECT o.id AS order_id, o.order_code, oi.flash_sale_item_id, o.slot_id, o.buyer_id AS user_id, oi.quantity
            FROM orders o
            JOIN order_items oi ON o.id = oi.order_id
            WHERE o.status = 'PENDING_PAYMENT'
            AND o.expires_at <= ?
            LIMIT ?
            FOR UPDATE SKIP LOCKED
        """;

        return jdbcTemplate.query(
                query,
                (rs, rowNum) -> new ExpiredOrderRef(
                        rs.getLong("order_id"),
                        rs.getString("order_code"),
                        rs.getLong("flash_sale_item_id"),
                        rs.getLong("slot_id"),
                        rs.getLong("user_id"),
                        rs.getInt("quantity")
                ),
                Timestamp.from(now),
                batchSize
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cancelTimeoutIfPending(Long orderId) {
        String sql = """
            UPDATE orders
            SET status = 'CANCELLED_TIMEOUT', updated_at = NOW()
            WHERE id = ? AND status = 'PENDING_PAYMENT'
        """;
        int updated = jdbcTemplate.update(sql, orderId);
        return updated > 0;
    }

    @Override
    public long countPendingBySlot(Long slotId) {
        String sql = "SELECT COUNT(*) FROM orders WHERE slot_id = ? AND status = 'PENDING_PAYMENT'";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, slotId);
        return count != null ? count : 0L;
    }
}
