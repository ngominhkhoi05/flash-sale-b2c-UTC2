-- KEYS[1]: flash_sale:stock:{itemId}
-- KEYS[2]: flash_sale:user_limit:{slotId}:{userId}:{itemId} (tùy chọn)
-- KEYS[3]: flash_sale:rollback:{orderCode}:{itemId} (tùy chọn)
-- KEYS[4]: flash_sale:pending_res:{orderCode} (tùy chọn)
-- KEYS[5]: flash_sale:pending_res_index (ZSET, tùy chọn)
-- ARGV[1]: Số lượng cần hoàn lại (quantity)
-- ARGV[2]: TTL của marker tính bằng giây (mặc định 86400) hoặc fallback_stock
-- ARGV[3]: Mã đơn orderCode (tùy chọn)

-- Bước 1: Kiểm tra marker rollback (Idempotency check)
if KEYS[3] and redis.call('EXISTS', KEYS[3]) == 1 then
    -- Đã rollback trước đó, dọn dẹp pending res nếu còn và trả về 0 (idempotent no-op)
    if KEYS[4] and redis.call('EXISTS', KEYS[4]) == 1 then
        redis.call('DEL', KEYS[4])
    end
    if KEYS[5] and ARGV[3] and ARGV[3] ~= '' then
        redis.call('ZREM', KEYS[5], ARGV[3])
    end
    return 0
end

-- Bước 2: Đặt marker rollback với TTL 24h ngay lập tức nếu có KEYS[3]
if KEYS[3] then
    local marker_ttl = tonumber(ARGV[2]) or 86400
    redis.call('SET', KEYS[3], '1', 'EX', marker_ttl)
end

-- Bước 3: Hoàn tồn kho Redis (INCRBY)
local exists = redis.call('EXISTS', KEYS[1])
if exists == 1 then
    redis.call('INCRBY', KEYS[1], ARGV[1])
else
    -- Nếu key chưa tồn tại trên Redis (chưa prewarm hoặc bị evict), khởi tạo bằng quantity
    local fallback = tonumber(ARGV[1])
    redis.call('SET', KEYS[1], fallback)
end

-- Bước 4: Giảm user limit tương ứng nếu có KEYS[2]
if KEYS[2] and redis.call('EXISTS', KEYS[2]) == 1 then
    local current_limit = redis.call('GET', KEYS[2])
    if current_limit then
        local new_limit = tonumber(current_limit) - tonumber(ARGV[1])
        if new_limit <= 0 then
            redis.call('DEL', KEYS[2])
        else
            redis.call('DECRBY', KEYS[2], ARGV[1])
        end
    end
end

-- Bước 5: Xóa pending reservation record & index nếu còn
if KEYS[4] and redis.call('EXISTS', KEYS[4]) == 1 then
    redis.call('DEL', KEYS[4])
end
if KEYS[5] and ARGV[3] and ARGV[3] ~= '' then
    redis.call('ZREM', KEYS[5], ARGV[3])
end

-- Trả về tồn kho hiện tại sau khi hoàn
return tonumber(redis.call('GET', KEYS[1]))
