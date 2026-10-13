package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.FlashSaleItemResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.PublicFlashSaleSlotResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.RegisterFlashSaleItemRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.exception.FlashSaleErrorCode;
import com.b2c.flash_sale_b2c_UTC2.flashsale.mapper.FlashSaleItemMapper;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.FlashSaleOrderPort;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleItemService {

    private final FlashSaleSlotRepository slotRepository;
    private final FlashSaleItemRepository itemRepository;
    private final ProductVariantRepository variantRepository;
    private final StoreRepository storeRepository;
    private final FlashSaleItemMapper itemMapper;
    private final ImageService imageService;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private FlashSaleOrderPort orderPort;

    public static final String STOCK_KEY_PREFIX = "flash_sale:stock:";

    @Transactional
    public FlashSaleItemResponse registerItem(Long sellerUserId, RegisterFlashSaleItemRequest request) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.NOT_STORE_OWNER));

        if (!"APPROVED".equals(store.getStatus())) {
            throw new BusinessException(FlashSaleErrorCode.STORE_NOT_APPROVED);
        }

        FlashSaleSlot slot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.SLOT_NOT_FOUND));

        if ("ENDED".equals(slot.getStatus())) {
            throw new BusinessException(FlashSaleErrorCode.SLOT_ALREADY_ENDED);
        }

        ProductVariant variant = variantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.ITEM_NOT_FOUND));

        if (!variant.getProduct().getStore().getId().equals(store.getId())) {
            throw new BusinessException(FlashSaleErrorCode.NOT_STORE_OWNER);
        }

        if (itemRepository.existsBySlotIdAndVariantId(slot.getId(), variant.getId())) {
            throw new BusinessException(FlashSaleErrorCode.ITEM_ALREADY_REGISTERED);
        }

        if (request.getFlashSalePrice().compareTo(BigDecimal.ZERO) <= 0 ||
                request.getFlashSalePrice().compareTo(variant.getOriginalPrice()) >= 0) {
            throw new BusinessException(FlashSaleErrorCode.INVALID_FLASH_SALE_PRICE);
        }

        if (request.getAllocatedStock() <= 0 || request.getAllocatedStock() > variant.getStockQuantity()) {
            throw new BusinessException(FlashSaleErrorCode.INVALID_ALLOCATED_STOCK);
        }

        if (request.getUserPurchaseLimit() != null && request.getUserPurchaseLimit() <= 0) {
            throw new BusinessException(FlashSaleErrorCode.INVALID_PURCHASE_LIMIT);
        }

        FlashSaleItem item = FlashSaleItem.builder()
                .slot(slot)
                .variant(variant)
                .flashSalePrice(request.getFlashSalePrice())
                .allocatedStock(request.getAllocatedStock())
                .availableStock(request.getAllocatedStock())
                .userPurchaseLimit(request.getUserPurchaseLimit() != null ? request.getUserPurchaseLimit() : 1)
                .commissionRateOverride(request.getCommissionRateOverride())
                .status("PENDING_APPROVAL")
                .createdAt(Instant.now())
                .build();

        return itemMapper.toResponse(itemRepository.save(item));
    }

    @Transactional
    public FlashSaleItemResponse approveItem(Long itemId) {
        FlashSaleItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.ITEM_NOT_FOUND));

        if (!"PENDING_APPROVAL".equals(item.getStatus())) {
            throw new BusinessException(FlashSaleErrorCode.ITEM_NOT_PENDING_APPROVAL);
        }

        int updated = variantRepository.deductStockQuantityConditionally(
                item.getVariant().getId(),
                item.getAllocatedStock()
        );

        if (updated == 0) {
            throw new BusinessException(FlashSaleErrorCode.INSUFFICIENT_BASE_STOCK);
        }

        item.setStatus("APPROVED");
        item.setAvailableStock(item.getAllocatedStock());
        FlashSaleItem savedItem = itemRepository.save(item);

        log.info("Admin approved FlashSaleItem ID: {}, deducted {} base stock from variant ID: {}",
                savedItem.getId(), savedItem.getAllocatedStock(), savedItem.getVariant().getId());

        return itemMapper.toResponse(savedItem);
    }

    @Transactional(readOnly = true)
    public List<PublicFlashSaleSlotResponse> getPublicSlots() {
        List<FlashSaleSlot> slots = slotRepository.findByStatusInOrderByStartTimeAsc(List.of("ACTIVE", "UPCOMING"));
        List<PublicFlashSaleSlotResponse> responseList = new ArrayList<>();

        for (FlashSaleSlot slot : slots) {
            List<FlashSaleItem> approvedItems = itemRepository.findApprovedItemsWithDetailsBySlotId(slot.getId());
            List<PublicFlashSaleSlotResponse.PublicFlashSaleItemResponse> itemDtos = new ArrayList<>();

            // Batch-load ảnh variant + product để tránh N+1
            java.util.List<Long> variantIds = approvedItems.stream().map(i -> i.getVariant().getId()).toList();
            java.util.List<Long> productIds = approvedItems.stream().map(i -> i.getVariant().getProduct().getId()).distinct().toList();
            java.util.Map<Long, String> variantImageMap = variantIds.isEmpty() ? java.util.Map.of() :
                    imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.VARIANT, variantIds)
                            .entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, e -> e.getValue().getUrl()));
            java.util.Map<Long, String> productImageMap = productIds.isEmpty() ? java.util.Map.of() :
                    imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.PRODUCT, productIds)
                            .entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, e -> e.getValue().getUrl()));

            for (FlashSaleItem item : approvedItems) {
                int displayStock = getRealtimeStock(item);
                Long vId = item.getVariant().getId();
                Long pId = item.getVariant().getProduct().getId();
                String imageUrl = variantImageMap.get(vId);
                if (imageUrl == null) {
                    imageUrl = productImageMap.get(pId);
                }

                itemDtos.add(PublicFlashSaleSlotResponse.PublicFlashSaleItemResponse.builder()
                        .id(item.getId())
                        .slotId(slot.getId())
                        .variantId(vId)
                        .sku(item.getVariant().getSku())
                        .variantName(item.getVariant().getVariantName())
                        .productName(item.getVariant().getProduct().getName())
                        .imageUrl(imageUrl)
                        .originalPrice(item.getVariant().getOriginalPrice())
                        .flashSalePrice(item.getFlashSalePrice())
                        .allocatedStock(item.getAllocatedStock())
                        .availableStock(displayStock)
                        .userPurchaseLimit(item.getUserPurchaseLimit())
                        .status(item.getStatus())
                        .build());
            }

            responseList.add(PublicFlashSaleSlotResponse.builder()
                    .id(slot.getId())
                    .title(slot.getTitle())
                    .startTime(slot.getStartTime())
                    .endTime(slot.getEndTime())
                    .reservationTtlSeconds(slot.getReservationTtlSeconds())
                    .status(slot.getStatus())
                    .items(itemDtos)
                    .build());
        }

        return responseList;
    }

        @Transactional(readOnly = true)
    public List<PublicFlashSaleSlotResponse.PublicFlashSaleItemResponse> getActiveItems() {
        return getPublicSlots().stream()
                .filter(slot -> "ACTIVE".equalsIgnoreCase(slot.getStatus()))
                .flatMap(slot -> slot.getItems().stream())
                .toList();
    }

    public int getRealtimeStock(FlashSaleItem item) {
        if (redisTemplate != null) {
            try {
                String stockVal = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + item.getId());
                if (stockVal != null) {
                    return Integer.parseInt(stockVal);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch Redis stock for item ID: {}, falling back to DB availableStock: {}", item.getId(), e.getMessage());
            }
        }
        return item.getAvailableStock();
    }

    @Transactional
    public void preWarmRedisForSlot(Long slotId) {
        FlashSaleSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.SLOT_NOT_FOUND));

        List<FlashSaleItem> items = itemRepository.findBySlotIdAndStatus(slot.getId(), "APPROVED");

        if (redisTemplate == null) {
            log.warn("Redis template not configured. Skipping pre-warm for slot ID: {}", slotId);
            return;
        }

        Instant now = Instant.now();
        long ttlSeconds = Duration.between(now, slot.getEndTime()).getSeconds();
        if (ttlSeconds <= 0) {
            log.warn("Slot ID: {} has already ended. Cannot pre-warm Redis.", slotId);
            return;
        }

        for (FlashSaleItem item : items) {
            String stockKey = STOCK_KEY_PREFIX + item.getId();
            long pendingReservations = 0;
            if (orderPort != null) {
                pendingReservations = orderPort.countPendingBySlot(slot.getId());
            }
            int initialStock = item.getAvailableStock() + (int) pendingReservations;

            Boolean setSuccess = redisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(initialStock), Duration.ofSeconds(ttlSeconds));
            if (Boolean.TRUE.equals(setSuccess)) {
                log.info("Pre-warmed Redis stock for FlashSaleItem ID: {} -> initialStock: {}, TTL: {}s",
                        item.getId(), initialStock, ttlSeconds);
            } else {
                log.debug("Redis stock already exists for FlashSaleItem ID: {}. Skipping SETNX.", item.getId());
            }
        }
    }
}
