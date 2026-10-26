param(
    [int]$Stock = 100,
    [int]$ItemId = 1
)

Write-Host "=== RESETTING FLASH SALE ITEM $ItemId TO STOCK = $Stock ==="

$env:PGPASSWORD = "root"
$psqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
$redisCliPath = "C:\Program Files\Redis\redis-cli.exe"

$sql = @"
UPDATE flash_sale_slots SET status = 'ACTIVE', start_time = NOW() - INTERVAL '1 hour', end_time = NOW() + INTERVAL '2 hours' WHERE id = (SELECT slot_id FROM flash_sale_items WHERE id = $ItemId);
UPDATE flash_sale_items SET allocated_stock = GREATEST(allocated_stock, $Stock), available_stock = $Stock, status = 'APPROVED' WHERE id = $ItemId;
UPDATE product_variants SET stock_quantity = GREATEST(stock_quantity, $Stock * 2) WHERE id = (SELECT variant_id FROM flash_sale_items WHERE id = $ItemId);
DELETE FROM orders WHERE id IN (SELECT order_id FROM order_items WHERE flash_sale_item_id = $ItemId);
"@

& $psqlPath -U postgres -d flash_sale_b2c -c "$sql"

# Reset Redis
& $redisCliPath SET "flash_sale:stock:$ItemId" "$Stock"
& $redisCliPath DEL "lock:flash_sale:item:$ItemId"

# Clean pending reservations in Redis
& $redisCliPath DEL "flash_sale:pending_res_index" | Out-Null
$pendingKeys = (& $redisCliPath KEYS "flash_sale:pending_res:*")
if ($pendingKeys) {
    foreach ($pk in $pendingKeys) {
        if ($pk.Trim()) {
            & $redisCliPath DEL $pk.Trim() | Out-Null
        }
    }
}

# Clean user limits in Redis
$keys = (& $redisCliPath KEYS "flash_sale:user_limit:*")
if ($keys) {
    foreach ($k in $keys) {
        if ($k.Trim()) {
            & $redisCliPath DEL $k.Trim() | Out-Null
        }
    }
}

$currentRedisStock = (& $redisCliPath GET "flash_sale:stock:$ItemId").Trim()

# Pre-flight assertions:
# 1. flash_sale:pending_res_index must be empty
$pendingCount = [int]((& $redisCliPath ZCARD "flash_sale:pending_res_index").Trim())
if ($pendingCount -ne 0) {
    Write-Error "PRE-FLIGHT FAILED: flash_sale:pending_res_index is not empty (count=$pendingCount)"
    exit 1
}

# 2. Redis stock must equal DB stock
$dbStockStr = (& $psqlPath -U postgres -d flash_sale_b2c -t -A -c "SELECT available_stock FROM flash_sale_items WHERE id = $ItemId").Trim()
if ([int]$currentRedisStock -ne [int]$dbStockStr) {
    Write-Error "PRE-FLIGHT FAILED: Redis stock ($currentRedisStock) does not match DB stock ($dbStockStr)"
    exit 1
}

# 3. No active application query in pg_stat_activity
$rawQueries = & $psqlPath -U postgres -d flash_sale_b2c -t -A -c "SELECT COUNT(*) FROM pg_stat_activity WHERE datname='flash_sale_b2c' AND pid <> pg_backend_pid() AND state = 'active' AND query NOT LIKE '%pg_stat_activity%';"
$activeQueries = 0
if ($rawQueries) {
    $firstLine = ($rawQueries | Select-Object -First 1).ToString().Trim()
    if ($firstLine -match '^\d+$') {
        $activeQueries = [int]$firstLine
    }
}
if ($activeQueries -gt 0) {
    Write-Error "PRE-FLIGHT FAILED: PostgreSQL has $activeQueries active queries running."
    exit 1
}

Write-Host "=== RESET COMPLETED & PRE-FLIGHT VERIFIED: ItemId = $ItemId, DB Stock = $dbStockStr, Redis Stock = $currentRedisStock ==="
