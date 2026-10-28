package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.BatchApproveFailedItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.BatchApproveFlashSaleItemsRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.BatchApproveFlashSaleItemsResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.FlashSaleItemResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.PublicFlashSaleSlotResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.RegisterFlashSaleItemRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.RejectFlashSaleItemRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
                com.b2c.flash_sale_b2c_UTC2.product.entity.Category cat = item.getVariant().getProduct().getCategory();
                Integer catId = cat != null ? cat.getId() : null;
                String catName = cat != null ? cat.getName() : null;
                String catSlug = cat != null ? cat.getSlug() : null;
                String imageUrl = variantImageMap.get(vId);
                if (imageUrl == null) {
                    imageUrl = productImageMap.get(pId);
                }

                itemDtos.add(PublicFlashSaleSlotResponse.PublicFlashSaleItemResponse.builder()
                        .id(item.getId())
                        .slotId(slot.getId())
                        .productId(pId)
                        .variantId(vId)
                        .categoryId(catId)
                        .categoryName(catName)
                        .categorySlug(catSlug)
                        .sku(item.getVariant().getSku())
                        .variantName(item.getVariant().getVariantName())
                        .attributes(item.getVariant().getAttributes())
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

    @Transactional(readOnly = true)
    public PageResponse<FlashSaleItemResponse> getSellerItems(Long sellerUserId, Long slotId, String status, Pageable pageable) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.NOT_STORE_OWNER));

        Page<FlashSaleItem> page = itemRepository.findByStoreIdAndFilters(store.getId(), slotId, status, pageable);
        List<FlashSaleItemResponse> dtos = enrichItemResponses(page.getContent());
        return PageResponse.of(page, dtos);
    }

    @Transactional
    public void cancelRegistration(Long sellerUserId, Long itemId) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.NOT_STORE_OWNER));

        FlashSaleItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.ITEM_NOT_FOUND));

        if (!item.getVariant().getProduct().getStore().getId().equals(store.getId())) {
            throw new BusinessException(FlashSaleErrorCode.NOT_STORE_OWNER);
        }

        if (!"PENDING_APPROVAL".equals(item.getStatus())) {
            throw new BusinessException(FlashSaleErrorCode.ITEM_NOT_PENDING_APPROVAL);
        }

        itemRepository.delete(item);
        log.info("Seller user ID: {} cancelled registration for FlashSaleItem ID: {}", sellerUserId, itemId);
    }

    @Transactional(readOnly = true)
    public PageResponse<FlashSaleItemResponse> getAdminSlotItems(Long slotId, String status, String search, Pageable pageable) {
        return getAdminItems(slotId, status, search, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<FlashSaleItemResponse> getAdminItems(Long slotId, String status, String search, Pageable pageable) {
        if (slotId != null) {
            slotRepository.findById(slotId)
                    .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.SLOT_NOT_FOUND));
        }

        String sanitizedStatus = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim()))
                ? status.trim().toUpperCase()
                : null;
        String sanitizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<FlashSaleItem> page = itemRepository.findBySlotIdAndFilters(slotId, sanitizedStatus, sanitizedSearch, pageable);
        List<FlashSaleItemResponse> dtos = enrichItemResponses(page.getContent());
        return PageResponse.of(page, dtos);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getAdminItemCounts(Long slotId) {
        if (slotId != null) {
            slotRepository.findById(slotId)
                    .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.SLOT_NOT_FOUND));
        }

        List<Object[]> rows = itemRepository.countStatusBySlotId(slotId);
        long pending = 0;
        long approved = 0;
        long rejected = 0;
        for (Object[] row : rows) {
            String status = (String) row[0];
            Long count = (Long) row[1];
            if ("PENDING_APPROVAL".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status)) {
                pending += (count != null ? count : 0);
            } else if ("APPROVED".equalsIgnoreCase(status)) {
                approved += (count != null ? count : 0);
            } else if ("REJECTED".equalsIgnoreCase(status)) {
                rejected += (count != null ? count : 0);
            }
        }
        return java.util.Map.of(
                "pending", pending,
                "approved", approved,
                "rejected", rejected
        );
    }

    @Transactional
    public FlashSaleItemResponse rejectItem(Long itemId, RejectFlashSaleItemRequest request) {
        FlashSaleItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.ITEM_NOT_FOUND));

        if (!"PENDING_APPROVAL".equals(item.getStatus())) {
            throw new BusinessException(FlashSaleErrorCode.ITEM_NOT_PENDING_APPROVAL);
        }

        item.setStatus("REJECTED");
        FlashSaleItem savedItem = itemRepository.save(item);
        log.info("Admin rejected FlashSaleItem ID: {}, reason: {}", savedItem.getId(), request != null ? request.getReason() : "None");
        return itemMapper.toResponse(savedItem);
    }

    @Transactional
    public BatchApproveFlashSaleItemsResponse batchApproveItems(BatchApproveFlashSaleItemsRequest request) {
        List<Long> approvedItemIds = new ArrayList<>();
        List<BatchApproveFailedItem> failedReasons = new ArrayList<>();

        if (request != null && request.getItemIds() != null) {
            for (Long itemId : request.getItemIds()) {
                try {
                    approveItem(itemId);
                    approvedItemIds.add(itemId);
                } catch (BusinessException ex) {
                    failedReasons.add(BatchApproveFailedItem.builder()
                            .itemId(itemId)
                            .reason(ex.getMessage())
                            .build());
                } catch (Exception ex) {
                    failedReasons.add(BatchApproveFailedItem.builder()
                            .itemId(itemId)
                            .reason("Lỗi: " + ex.getMessage())
                            .build());
                }
            }
        }
        return BatchApproveFlashSaleItemsResponse.builder()
                .approvedItemIds(approvedItemIds)
                .failedReasons(failedReasons)
                .build();
    }

    private List<FlashSaleItemResponse> enrichItemResponses(List<FlashSaleItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> variantIds = items.stream().map(i -> i.getVariant().getId()).toList();
        List<Long> productIds = items.stream().map(i -> i.getVariant().getProduct().getId()).distinct().toList();

        java.util.Map<Long, String> variantImageMap = variantIds.isEmpty() ? java.util.Map.of() :
                imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.VARIANT, variantIds)
                        .entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, e -> e.getValue().getUrl()));
        java.util.Map<Long, String> productImageMap = productIds.isEmpty() ? java.util.Map.of() :
                imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.PRODUCT, productIds)
                        .entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, e -> e.getValue().getUrl()));

        List<FlashSaleItemResponse> responses = new ArrayList<>(items.size());
        for (FlashSaleItem item : items) {
            FlashSaleItemResponse dto = itemMapper.toResponse(item);
            String img = variantImageMap.get(item.getVariant().getId());
            if (img == null) {
                img = productImageMap.get(item.getVariant().getProduct().getId());
            }
            dto.setImageUrl(img);
            responses.add(dto);
        }
        return responses;
    }
}
