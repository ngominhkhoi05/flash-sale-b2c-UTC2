#!/usr/bin/env bash
# Reset Flash Sale Item stock & clean orders for testing
# Usage: ./reset.sh [STOCK_QUANTITY] [ITEM_ID]
STOCK=${1:-100}
ITEM_ID=${2:-1}

echo "=== RESETTING FLASH SALE ITEM $ITEM_ID TO STOCK = $STOCK ==="

# 1. Update Database
export PGPASSWORD="root"
psql -h localhost -U postgres -d flash_sale_b2c -c "
-- 1. Activate slot
UPDATE flash_sale_slots SET status = 'ACTIVE', start_time = NOW() - INTERVAL '1 hour', end_time = NOW() + INTERVAL '2 hours' WHERE id = (SELECT slot_id FROM flash_sale_items WHERE id = $ITEM_ID);

-- 2. Update flash sale item stock
UPDATE flash_sale_items SET allocated_stock = GREATEST(allocated_stock, $STOCK), available_stock = $STOCK, status = 'APPROVED' WHERE id = $ITEM_ID;

-- 3. Update variant stock if needed
UPDATE product_variants SET stock_quantity = GREATEST(stock_quantity, $STOCK * 2) WHERE id = (SELECT variant_id FROM flash_sale_items WHERE id = $ITEM_ID);

-- 4. Clean previous orders for this item
DELETE FROM orders WHERE id IN (SELECT order_id FROM order_items WHERE flash_sale_item_id = $ITEM_ID);
"

# 2. Reset Redis keys
redis-cli -h localhost -p 6379 SET "flash_sale:stock:$ITEM_ID" "$STOCK"
# Xoa tat ca user purchase limit cu va lock cu
redis-cli -h localhost -p 6379 --scan --pattern "flash_sale:user_limit:*" | xargs -r redis-cli -h localhost -p 6379 DEL
redis-cli -h localhost -p 6379 --scan --pattern "lock:flash_sale:item:$ITEM_ID" | xargs -r redis-cli -h localhost -p 6379 DEL

echo "=== RESET COMPLETED: DB Stock = $STOCK, Redis Stock = $(redis-cli -h localhost -p 6379 GET "flash_sale:stock:$ITEM_ID") ==="
