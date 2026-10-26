#!/usr/bin/env bash
# Verify Flash Sale Stock & Orders in DB vs Redis
# Usage: ./verify.sh [ITEM_ID]
ITEM_ID=${1:-1}

echo "=== VERIFYING FLASH SALE ITEM $ITEM_ID ==="

export PGPASSWORD="root"
psql -h localhost -U postgres -d flash_sale_b2c -c "
SELECT 
    COUNT(DISTINCT o.id) AS total_orders,
    COALESCE(SUM(oi.quantity), 0) AS total_quantity_ordered,
    MAX(fsi.available_stock) AS db_available_stock,
    MAX(fsi.allocated_stock) AS db_allocated_stock
FROM flash_sale_items fsi
LEFT JOIN order_items oi ON oi.flash_sale_item_id = fsi.id
LEFT JOIN orders o ON o.id = oi.order_id AND o.status != 'CANCELLED_TIMEOUT'
WHERE fsi.id = $ITEM_ID;

-- Check duplicate orders per user
SELECT buyer_id, COUNT(*) as orders_count
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
WHERE oi.flash_sale_item_id = $ITEM_ID
GROUP BY buyer_id
HAVING COUNT(*) > (SELECT user_purchase_limit FROM flash_sale_items WHERE id = $ITEM_ID);
"

REDIS_STOCK=$(redis-cli -h localhost -p 6379 GET "flash_sale:stock:$ITEM_ID")
echo "Redis Stock for Item $ITEM_ID: $REDIS_STOCK"

