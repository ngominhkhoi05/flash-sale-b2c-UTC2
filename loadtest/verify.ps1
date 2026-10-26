param(
    [int]$ItemId = 1
)

Write-Host "=== VERIFYING FLASH SALE ITEM $ItemId ==="

$env:PGPASSWORD = "root"
$psqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
$redisCliPath = "C:\Program Files\Redis\redis-cli.exe"

$sql = @"
SELECT 
    COUNT(DISTINCT o.id) AS total_orders,
    COALESCE(SUM(oi.quantity), 0) AS total_quantity_ordered,
    MAX(fsi.available_stock) AS db_available_stock,
    MAX(fsi.allocated_stock) AS db_allocated_stock,
    MIN(o.created_at) AS min_created_at,
    MAX(o.created_at) AS max_created_at,
    ROUND(
        CASE WHEN COUNT(DISTINCT o.id) > 1 AND EXTRACT(EPOCH FROM (MAX(o.created_at) - MIN(o.created_at))) > 0
             THEN COUNT(DISTINCT o.id) / EXTRACT(EPOCH FROM (MAX(o.created_at) - MIN(o.created_at)))
             ELSE COUNT(DISTINCT o.id)
        END, 2
    ) AS throughput_orders_per_sec
FROM flash_sale_items fsi
LEFT JOIN order_items oi ON oi.flash_sale_item_id = fsi.id
LEFT JOIN orders o ON o.id = oi.order_id AND o.status != 'CANCELLED_TIMEOUT'
WHERE fsi.id = $ItemId;

SELECT buyer_id, COUNT(*) as duplicate_orders_count
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
WHERE oi.flash_sale_item_id = $ItemId
GROUP BY buyer_id
HAVING COUNT(*) > (SELECT user_purchase_limit FROM flash_sale_items WHERE id = $ItemId);
"@

& $psqlPath -U postgres -d flash_sale_b2c -c "$sql"

$redisStock = & $redisCliPath GET "flash_sale:stock:$ItemId"
Write-Host "Redis Stock for Item $ItemId : $redisStock"

