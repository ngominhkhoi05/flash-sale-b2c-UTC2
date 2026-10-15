-- KEYS[1]: flash_sale:stock:{itemId}
-- KEYS[2]: flash_sale:user_limit:{slotId}:{userId}:{itemId}
-- KEYS[3]: flash_sale:pending_res:{orderCode} (tùy chọn)
-- KEYS[4]: flash_sale:pending_res_index (ZSET, tùy chọn)
-- ARGV[1]: Số lượng muốn mua (quantity)
-- ARGV[2]: Giới hạn mua tối đa của user trong toàn bộ phiên (user_purchase_limit)
-- ARGV[3]: Thời gian còn lại của phiên sale tính bằng giây (slot_remaining_ttl_seconds)
-- ARGV[4]: orderCode (tùy chọn)
-- ARGV[5]: userId (tùy chọn)
-- ARGV[6]: slotId (tùy chọn)
-- ARGV[7]: itemId (tùy chọn)
-- ARGV[8]: pending_record_ttl_seconds (tùy chọn, mặc định 86400)

-- Bước 1: Kiểm tra user đã mua hoặc đang giữ chỗ vượt quá giới hạn chưa
local current_purchased = redis.call('GET', KEYS[2])
if current_purchased and (tonumber(current_purchased) + tonumber(ARGV[1]) > tonumber(ARGV[2])) then
    return -1 -- Mã lỗi: Vượt quá giới hạn mua trên mỗi khách hàng trong phiên sale
end

-- Bước 2: Kiểm tra tồn kho khả dụng
local current_stock = redis.call('GET', KEYS[1])
if not current_stock or tonumber(current_stock) < tonumber(ARGV[1]) then
    return -2 -- Mã lỗi: Hết hàng hoặc không đủ số lượng yêu cầu
end

-- Bước 3: Trừ tồn kho nguyên tử & Tăng số lượng user đã giữ chỗ
redis.call('DECRBY', KEYS[1], ARGV[1])
redis.call('INCRBY', KEYS[2], ARGV[1])

-- Thiết lập TTL cho purchase limit sống theo toàn bộ thời gian diễn ra của Slot
if tonumber(ARGV[3]) > 0 then
    redis.call('EXPIRE', KEYS[2], ARGV[3])
end

-- Bước 4: Lấy thời gian nguyên tử từ Redis (TIME trả về {seconds, microseconds})
local redis_time = redis.call('TIME')
local created_at = tonumber(redis_time[1])

-- Bước 5: Ghi bản ghi giữ chỗ tạm (Pending Reservation) với TTL dài (24 giờ) nếu có KEYS[3] và ARGV[4]
if KEYS[3] and ARGV[4] and ARGV[4] ~= '' then
    local payload = cjson.encode({
        orderCode = ARGV[4],
        userId = tonumber(ARGV[5]),
        slotId = tonumber(ARGV[6]),
        itemId = tonumber(ARGV[7]),
        quantity = tonumber(ARGV[1]),
        createdAt = created_at
    })
    local ttl = tonumber(ARGV[8]) or 86400
    redis.call('SET', KEYS[3], payload, 'EX', ttl)

    -- Thêm vào Sorted Set chỉ mục theo createdAt để job đối soát quét bằng ZRANGEBYSCORE
    if KEYS[4] then
        redis.call('ZADD', KEYS[4], created_at, ARGV[4])
    end
end

-- Trả về số lượng tồn kho còn lại trên Redis sau khi trừ thành công (>= 0)
return tonumber(current_stock) - tonumber(ARGV[1])
