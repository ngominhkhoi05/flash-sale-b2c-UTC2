-- Cleanup SQL for integration tests that need to commit (e.g. scheduler, trigger, async).
-- Use via @Sql(scripts = "/cleanup.sql", executionPhase = AFTER_TEST_METHOD).
-- Note: thứ tự TRUNCATE phải respect FK constraint (xem V1__init_schema.sql).
TRUNCATE TABLE order_items, orders, voucher_usages, cart_items, carts,
    wallet_transactions, payments, product_reviews,
    flash_sale_items, flash_sale_slots,
    product_variants, products, categories,
    addresses, stores, wallets,
    images,
    auth_accounts, user_roles, users,
    group_permissions, permission_groups, permissions, roles
RESTART IDENTITY CASCADE;