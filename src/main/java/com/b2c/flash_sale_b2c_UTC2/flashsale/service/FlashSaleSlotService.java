package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.CreateFlashSaleSlotRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.EmergencyPauseRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.EmergencyPauseResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.FlashSaleSlotResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.LiveSkuInventoryResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.RealtimeTelemetryResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.UpdateFlashSaleSlotRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.exception.FlashSaleErrorCode;
import com.b2c.flash_sale_b2c_UTC2.flashsale.mapper.FlashSaleSlotMapper;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsBroadcaster;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleSlotService {

    public static final String STOCK_KEY_PREFIX = "flash_sale:stock:";

    private final FlashSaleSlotRepository slotRepository;
    private final FlashSaleSlotMapper slotMapper;
    private final FlashSaleItemRepository itemRepository;
    private final ProductVariantRepository variantRepository;
    private final FlashSaleWsBroadcaster wsBroadcaster;

    @Autowired(required = false)
    private OrderRepository orderRepository;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Transactional
    public FlashSaleSlotResponse createSlot(CreateFlashSaleSlotRequest request) {
        validateSlotTimes(request.getStartTime(), request.getEndTime());

        boolean hasOverlap = slotRepository.existsOverlappingSlot(null, request.getStartTime(), request.getEndTime());
        if (hasOverlap) {
            throw new BusinessException(FlashSaleErrorCode.SLOT_OVERLAP);
        }

        FlashSaleSlot slot = FlashSaleSlot.builder()
                .title(request.getTitle())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .reservationTtlSeconds(request.getReservationTtlSeconds() != null ? request.getReservationTtlSeconds() : 300)
                .status("UPCOMING")
                .createdAt(Instant.now())
                .build();

        return slotMapper.toResponse(slotRepository.save(slot));
    }

    @Transactional
    public FlashSaleSlotResponse updateSlot(Long slotId, UpdateFlashSaleSlotRequest request) {
        FlashSaleSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.SLOT_NOT_FOUND));

        if ("ENDED".equals(slot.getStatus())) {
            throw new BusinessException(FlashSaleErrorCode.SLOT_ALREADY_ENDED);
        }

        validateSlotTimes(request.getStartTime(), request.getEndTime());

        boolean hasOverlap = slotRepository.existsOverlappingSlot(slotId, request.getStartTime(), request.getEndTime());
        if (hasOverlap) {
            throw new BusinessException(FlashSaleErrorCode.SLOT_OVERLAP);
        }

        slot.setTitle(request.getTitle());
        slot.setStartTime(request.getStartTime());
        slot.setEndTime(request.getEndTime());
        if (request.getReservationTtlSeconds() != null) {
            slot.setReservationTtlSeconds(request.getReservationTtlSeconds());
        }
        if (request.getStatus() != null) {
            slot.setStatus(request.getStatus());
        }

        return slotMapper.toResponse(slotRepository.save(slot));
    }

    @Transactional(readOnly = true)
    public FlashSaleSlotResponse getSlotById(Long slotId) {
        FlashSaleSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.SLOT_NOT_FOUND));
        return slotMapper.toResponse(slot);
    }

    @Transactional(readOnly = true)
    public List<FlashSaleSlotResponse> getAllSlots() {
        return slotRepository.findAll().stream()
                .map(slotMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RealtimeTelemetryResponse getSlotTelemetry(Long slotId) {
        FlashSaleSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.SLOT_NOT_FOUND));

        boolean isPaused = "PAUSED".equals(slot.getStatus());
        if (redisTemplate != null) {
            try {
                String pausedVal = redisTemplate.opsForValue().get("flash_sale:slot:" + slotId + ":paused");
                if ("1".equals(pausedVal)) {
                    isPaused = true;
                }
            } catch (Exception ignored) {}
        }

        double redisLatencyMs = 0.5;
        long activeLocks = 0;
        long currentRps = 0;
        long totalRequests = 0;

        if (redisTemplate != null) {
            try {
                long start = System.nanoTime();
                redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection conn) -> {
                    conn.ping();
                    return null;
                });
                redisLatencyMs = (System.nanoTime() - start) / 1_000_000.0;
                redisLatencyMs = Math.round(redisLatencyMs * 10.0) / 10.0;

                Long zcard = redisTemplate.opsForZSet().zCard("flash_sale:pending_res_index");
                if (zcard != null) {
                    activeLocks = zcard;
                }

                String rpsVal = redisTemplate.opsForValue().get("flash_sale:telemetry:rps:" + slotId);
                if (rpsVal != null) {
                    currentRps = Long.parseLong(rpsVal);
                }
                String totalReqVal = redisTemplate.opsForValue().get("flash_sale:telemetry:req_total:" + slotId);
                if (totalReqVal != null) {
                    totalRequests = Long.parseLong(totalReqVal);
                }
            } catch (Exception e) {
                log.warn("Failed to read Redis telemetry for slot {}: {}", slotId, e.getMessage());
            }
        }

        long successfulOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long expiredReleases = 0;
        if (orderRepository != null) {
            successfulOrders = orderRepository.countSuccessfulOrdersBySlotId(slotId);
            totalRevenue = orderRepository.sumTotalRevenueBySlotId(slotId);
            expiredReleases = orderRepository.countExpiredOrdersBySlotId(slotId);
        }

        if (totalRequests == 0) {
            totalRequests = successfulOrders + expiredReleases + activeLocks;
        }

        String systemHealth = redisLatencyMs > 50 ? "DEGRADED" : "OPTIMAL";

        return RealtimeTelemetryResponse.builder()
                .slotId(slot.getId())
                .status(slot.getStatus())
                .isPaused(isPaused)
                .currentRps(currentRps)
                .totalRequests(totalRequests)
                .activeLocks(activeLocks)
                .successfulOrders(successfulOrders)
                .totalRevenue(totalRevenue)
                .failedRequests(0L)
                .expiredReleases(expiredReleases)
                .oversoldIncidents(0)
                .redisLatencyMs(redisLatencyMs)
                .systemHealth(systemHealth)
                .lastUpdated(Instant.now())
                .build();
    }

    @Transactional
    public EmergencyPauseResponse emergencyPauseSlot(Long slotId, EmergencyPauseRequest request) {
        FlashSaleSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.SLOT_NOT_FOUND));

        if (!"ACTIVE".equals(slot.getStatus())) {
            throw new BusinessException(FlashSaleErrorCode.SLOT_NOT_ACTIVE);
        }

        // 1. Set pause flags on Redis
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("flash_sale:slot:" + slotId + ":paused", "1", Duration.ofDays(1));
                redisTemplate.opsForValue().set("flashsale:slot:" + slotId + ":paused", "1", Duration.ofDays(1));
            } catch (Exception e) {
                log.error("Failed to set pause flag in Redis for slot {}: {}", slotId, e.getMessage());
            }
        }

        // 2. Mark slot PAUSED
        slot.setStatus("PAUSED");

        int rolledBackItemsCount = 0;
        int totalStockReturned = 0;
        boolean rollbackStock = Boolean.TRUE.equals(request.getRollbackStock());

        // 3. Rollback stock if requested and not rolled back yet
        if (rollbackStock && !Boolean.TRUE.equals(slot.getIsStockRolledBack())) {
            List<FlashSaleItem> items = itemRepository.findBySlotId(slot.getId());
            for (FlashSaleItem item : items) {
                if ("APPROVED".equals(item.getStatus())) {
                    int unsoldStock = item.getAvailableStock();
                    if (redisTemplate != null) {
                        try {
                            String redisStockStr = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + item.getId());
                            if (redisStockStr != null) {
                                unsoldStock = Integer.parseInt(redisStockStr);
                            }
                            redisTemplate.delete(STOCK_KEY_PREFIX + item.getId());
                        } catch (Exception e) {
                            log.warn("Failed to get/delete Redis stock for itemId {}: {}", item.getId(), e.getMessage());
                        }
                    }

                    if (unsoldStock > 0 && item.getVariant() != null) {
                        variantRepository.replenishStockQuantityConditionally(
                                item.getVariant().getId(),
                                unsoldStock
                        );
                        totalStockReturned += unsoldStock;
                        rolledBackItemsCount++;
                        log.info("Emergency pause: returned {} unsold stock to variant ID {} from FlashSaleItem ID {}",
                                unsoldStock, item.getVariant().getId(), item.getId());
                    }

                    try {
                        wsBroadcaster.broadcastUnsoldStockReturned(slot.getId(), item.getId(), 0);
                    } catch (Exception ignored) {}

                    item.setStatus("ENDED");
                    itemRepository.save(item);
                }
            }
            slot.setIsStockRolledBack(true);
        }

        slotRepository.save(slot);

        // 4. Broadcast WebSocket
        try {
            wsBroadcaster.broadcastSlotStatus(slot.getId(), "PAUSED");
        } catch (Exception e) {
            log.warn("Failed to broadcast slot PAUSED event: {}", e.getMessage());
        }

        String msg = String.format("Đã dừng phiên Flash Sale thành công. %s",
                rollbackStock ? "Đã hoàn trả " + totalStockReturned + " tồn kho của " + rolledBackItemsCount + " sản phẩm về kho chính."
                        : "Không thực hiện hoàn kho ngay.");

        return EmergencyPauseResponse.builder()
                .slotId(slot.getId())
                .status("PAUSED")
                .isPaused(true)
                .stockRolledBack(Boolean.TRUE.equals(slot.getIsStockRolledBack()))
                .rolledBackItemsCount(rolledBackItemsCount)
                .totalStockReturned(totalStockReturned)
                .message(msg)
                .build();
    }

    @Transactional(readOnly = true)
    public List<LiveSkuInventoryResponse> getLiveSkus(Long slotId) {
        FlashSaleSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.SLOT_NOT_FOUND));

        List<FlashSaleItem> items = itemRepository.findBySlotId(slot.getId());
        return items.stream()
                .filter(item -> "APPROVED".equals(item.getStatus()) || "ENDED".equals(item.getStatus()))
                .map(item -> {
                    int availableStock = item.getAvailableStock() != null ? item.getAvailableStock() : 0;
                    if (redisTemplate != null) {
                        try {
                            String redisStockStr = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + item.getId());
                            if (redisStockStr != null) {
                                availableStock = Integer.parseInt(redisStockStr);
                            }
                        } catch (Exception ignored) {}
                    }
                    int allocatedStock = item.getAllocatedStock() != null ? item.getAllocatedStock() : 0;
                    int soldStock = Math.max(0, allocatedStock - availableStock);
                    String status = "SELLING";
                    if (availableStock <= 0) {
                        status = "SOLD_OUT";
                    } else if (availableStock <= allocatedStock * 0.1 || availableStock < 5) {
                        status = "ALMOST_OUT";
                    }

                    String skuCode = item.getVariant() != null ? item.getVariant().getSku() : "SKU-" + item.getId();
                    String variantName = item.getVariant() != null ? item.getVariant().getVariantName() : "";
                    String productName = (item.getVariant() != null && item.getVariant().getProduct() != null)
                            ? item.getVariant().getProduct().getName() : "Sản phẩm Flash Sale";
                    String storeName = (item.getVariant() != null && item.getVariant().getProduct() != null && item.getVariant().getProduct().getStore() != null)
                            ? item.getVariant().getProduct().getStore().getStoreName() : "Gian hàng";

                    return LiveSkuInventoryResponse.builder()
                            .id(item.getId())
                            .skuCode(skuCode)
                            .productName(productName)
                            .variantName(variantName)
                            .storeName(storeName)
                            .allocatedStock(allocatedStock)
                            .availableStock(availableStock)
                            .holdingStock(0)
                            .soldStock(soldStock)
                            .status(status)
                            .icon("inventory_2")
                            .build();
                })
                .toList();
    }

    private void validateSlotTimes(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new BusinessException(FlashSaleErrorCode.SLOT_TIME_INVALID);
        }
    }
}

