package com.b2c.flash_sale_b2c_UTC2.common.test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper that TRUNCATEs all business tables.
 *
 * Required because tests use a real Supabase Dev DB (not an ephemeral container).
 * Without cleanup, subsequent tests would see leftover data from previous tests,
 * causing false positive/negative results.
 *
 * Method {@link #truncateAll()} runs in its OWN transaction (REQUIRES_NEW) to ensure
 * TRUNCATE commits even when a test method has its own @Transactional scope.
 */
@Component
public class DbCleaner {

    @PersistenceContext
    private EntityManager em;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void truncateAll() {
        em.createNativeQuery("""
                TRUNCATE TABLE
                    order_items,
                    orders,
                    cart_items,
                    carts,
                    user_vouchers,
                    voucher_usages,
                    vouchers,
                    payments,
                    wallet_transactions,
                    wallets,
                    product_reviews,
                    flash_sale_items,
                    flash_sale_slots,
                    product_variants,
                    products,
                    categories,
                    addresses,
                    stores,
                    banners,
                    user_roles,
                    auth_accounts,
                    users,
                    images,
                    group_permissions,
                    permissions,
                    permission_groups,
                    roles
                RESTART IDENTITY CASCADE
                """).executeUpdate();
    }
}
