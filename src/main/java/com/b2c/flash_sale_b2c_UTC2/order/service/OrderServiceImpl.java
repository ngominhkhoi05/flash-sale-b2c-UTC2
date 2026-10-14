package com.b2c.flash_sale_b2c_UTC2.order.service;

import com.b2c.flash_sale_b2c_UTC2.cart.entity.Cart;
import com.b2c.flash_sale_b2c_UTC2.cart.repository.CartItemRepository;
import com.b2c.flash_sale_b2c_UTC2.cart.repository.CartRepository;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.order.dto.CheckoutItemRequest;
import com.b2c.flash_sale_b2c_UTC2.order.dto.CheckoutStoreOrderRequest;
import com.b2c.flash_sale_b2c_UTC2.order.dto.CreateOrderRequest;
import com.b2c.flash_sale_b2c_UTC2.order.dto.OrderItemResponse;
import com.b2c.flash_sale_b2c_UTC2.order.dto.OrderResponse;
import com.b2c.flash_sale_b2c_UTC2.order.dto.UpdateOrderStatusRequest;
import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.order.entity.OrderItem;
import com.b2c.flash_sale_b2c_UTC2.order.exception.OrderErrorCode;
import com.b2c.flash_sale_b2c_UTC2.order.mapper.OrderMapper;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderItemRepository;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.exception.StoreErrorCode;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Address;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.exception.UserErrorCode;
import com.b2c.flash_sale_b2c_UTC2.user.repository.AddressRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.Voucher;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.VoucherUsage;
import com.b2c.flash_sale_b2c_UTC2.voucher.repository.VoucherRepository;
import com.b2c.flash_sale_b2c_UTC2.voucher.repository.VoucherUsageRepository;
import com.b2c.flash_sale_b2c_UTC2.voucher.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final VoucherService voucherService;
        private final CartRepository cartRepository;
    private final com.b2c.flash_sale_b2c_UTC2.payment.repository.PaymentRepository paymentRepository;
    private final CartItemRepository cartItemRepository;
    private final ImageService imageService;
    private final OrderMapper orderMapper;

    @Override
    @Transactional
    public List<OrderResponse> createOrders(Long buyerId, CreateOrderRequest request) {
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        // 1. Ownership check địa chỉ giao hàng của Buyer
        Address address = addressRepository.findById(request.getShippingAddressId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.ADDRESS_NOT_FOUND));

        if (address.getUser() == null || !address.getUser().getId().equals(buyerId)) {
            throw new BusinessException(UserErrorCode.ADDRESS_ACCESS_DENIED);
        }

        String recipientName = address.getContactName();
        String recipientPhone = address.getPhone();
        String shippingAddressText = String.format("%s, %s, %s, %s",
                address.getDetailAddress(), address.getWard(), address.getDistrict(), address.getProvince());

        List<OrderResponse> createdOrders = new ArrayList<>();
        Cart buyerCart = cartRepository.findByUserId(buyerId).orElse(null);

        // 2. Tách đơn theo từng Gian hàng (Order Splitting)
        for (CheckoutStoreOrderRequest storeRequest : request.getStoreOrders()) {
            Store store = storeRepository.findById(storeRequest.getStoreId())
                    .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

            if (!"APPROVED".equalsIgnoreCase(store.getStatus())) {
                throw new BusinessException(OrderErrorCode.STORE_NOT_APPROVED);
            }

            if (storeRequest.getItems() == null || storeRequest.getItems().isEmpty()) {
                throw new BusinessException(OrderErrorCode.EMPTY_ORDER_ITEMS);
            }

            BigDecimal subtotalAmount = BigDecimal.ZERO;
            List<OrderItem> orderItemsToSave = new ArrayList<>();

            // Deduct base stock & create OrderItems snapshots
            for (CheckoutItemRequest itemReq : storeRequest.getItems()) {
                ProductVariant variant = productVariantRepository.findById(itemReq.getVariantId())
                        .orElseThrow(() -> new BusinessException(OrderErrorCode.INSUFFICIENT_VARIANT_STOCK));

                if (variant.getStockQuantity() < itemReq.getQuantity()) {
                    throw new BusinessException(OrderErrorCode.INSUFFICIENT_VARIANT_STOCK);
                }

                // Trừ tồn kho gốc
                variant.setStockQuantity(variant.getStockQuantity() - itemReq.getQuantity());
                productVariantRepository.save(variant);

                BigDecimal price = variant.getOriginalPrice();
                BigDecimal itemSubtotal = price.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
                subtotalAmount = subtotalAmount.add(itemSubtotal);

                OrderItem orderItem = OrderItem.builder()
                        .variant(variant)
                        .productName(variant.getProduct().getName())
                        .variantName(variant.getVariantName())
                        .priceAtPurchase(price)
                        .quantity(itemReq.getQuantity())
                        .build();

                orderItemsToSave.add(orderItem);

                // Clear from buyer's cart
                if (buyerCart != null) {
                    cartItemRepository.findByCartIdAndVariantId(buyerCart.getId(), variant.getId())
                            .ifPresent(cartItemRepository::delete);
                }
            }

            // Apply Voucher nếu có
            Voucher voucher = null;
            BigDecimal voucherDiscountAmount = BigDecimal.ZERO;

            if (storeRequest.getVoucherCode() != null && !storeRequest.getVoucherCode().isBlank()) {
                voucher = voucherService.validateVoucherForOrder(
                        storeRequest.getVoucherCode(), buyerId, store.getId(), subtotalAmount);
                voucherDiscountAmount = voucherService.calculateDiscountAmount(voucher, subtotalAmount);

                voucher.setUsedQuantity(voucher.getUsedQuantity() + 1);
                voucherRepository.save(voucher);
            }

            BigDecimal totalAmount = subtotalAmount.subtract(voucherDiscountAmount).max(BigDecimal.ZERO);
            BigDecimal commissionRate = store.getDefaultCommissionRate() != null ?
                    store.getDefaultCommissionRate() : new BigDecimal("0.0500");

            BigDecimal platformFee = totalAmount.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal sellerAmount = totalAmount.subtract(platformFee);

            String orderCode = "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            Order order = Order.builder()
                    .orderCode(orderCode)
                    .buyer(buyer)
                    .store(store)
                    .voucher(voucher)
                    .shippingAddress(address)
                    .recipientName(recipientName)
                    .recipientPhone(recipientPhone)
                    .shippingAddressText(shippingAddressText)
                    .subtotalAmount(subtotalAmount)
                    .voucherDiscountAmount(voucherDiscountAmount)
                    .totalAmount(totalAmount)
                    .commissionRate(commissionRate)
                    .platformFee(platformFee)
                    .sellerAmount(sellerAmount)
                    .status("PENDING_PAYMENT")
                    .expiresAt(Instant.now().plusSeconds(300)) // TTL 300s reservation
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            order = orderRepository.save(order);

            // Save order items & associate with order
            for (OrderItem orderItem : orderItemsToSave) {
                orderItem.setOrder(order);
                orderItemRepository.save(orderItem);
            }

            // Save voucher usage record if applicable
            if (voucher != null) {
                VoucherUsage usage = VoucherUsage.builder()
                        .voucher(voucher)
                        .user(buyer)
                        .order(order)
                        .discountAmount(voucherDiscountAmount)
                        .usedAt(Instant.now())
                        .build();
                voucherUsageRepository.save(usage);
            }

            OrderResponse response = orderMapper.toResponse(order);
            List<OrderItemResponse> itemResponses = enrichItemResponses(orderItemsToSave);
            response.setItems(itemResponses);

            createdOrders.add(response);
            log.info("Tạo thành công đơn hàng orderCode={} cho storeId={}", orderCode, store.getId());
        }

        return createdOrders;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getBuyerOrders(Long buyerId, Pageable pageable) {
        Page<Order> page = orderRepository.findByBuyerId(buyerId, pageable);
        List<OrderResponse> content = page.getContent().stream().map(this::enrichOrderResponse).toList();
        return PageResponse.of(page, content);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getBuyerOrderDetail(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getBuyer().getId().equals(buyerId)) {
            throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }

        return enrichOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getBuyer().getId().equals(buyerId)) {
            throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }

        if (!"PENDING_PAYMENT".equalsIgnoreCase(order.getStatus()) && !"CREATED".equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }

        // Refund base stock to variant
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        for (OrderItem item : items) {
            ProductVariant variant = item.getVariant();
            variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
            productVariantRepository.save(variant);
        }

        // Bồi hoàn lượt dùng voucher nếu có áp dụng
        voucherService.rollbackVoucherUsage(order.getId());

        order.setStatus("CANCELLED");
        order.setUpdatedAt(Instant.now());
        order = orderRepository.save(order);

        log.info("Khách hàng buyerId={} đã hủy đơn hàng id={}", buyerId, orderId);
        return enrichOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getSellerOrders(Long sellerUserId, Pageable pageable) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        Page<Order> page = orderRepository.findByStoreId(store.getId(), pageable);
        List<OrderResponse> content = page.getContent().stream().map(this::enrichOrderResponse).toList();
        return PageResponse.of(page, content);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatusBySeller(Long sellerUserId, Long orderId, UpdateOrderStatusRequest request) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getStore().getId().equals(store.getId())) {
            throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }

        String newStatus = request.getStatus().trim().toUpperCase();
        if ("CANCELLED".equals(newStatus) && !"CANCELLED".equals(order.getStatus())) {
            // Restore stock if seller cancels order
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            for (OrderItem item : items) {
                ProductVariant variant = item.getVariant();
                variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
                productVariantRepository.save(variant);
            }

            // Bồi hoàn lượt dùng voucher nếu có áp dụng
            voucherService.rollbackVoucherUsage(order.getId());
        }

        // When order transitions to COMPLETED, increment product.total_sold
        if ("COMPLETED".equals(newStatus) && !"COMPLETED".equals(order.getStatus())) {
            List<OrderItem> completedItems = orderItemRepository.findByOrderId(order.getId());
            for (OrderItem item : completedItems) {
                productRepository.incrementTotalSold(item.getVariant().getProduct().getId(), item.getQuantity());
            }
            log.info("Order id={} COMPLETED: incremented totalSold for {} products", orderId, completedItems.size());
        }

        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());
        order = orderRepository.save(order);

        log.info("Seller storeId={} cập nhật trạng thái đơn hàng id={} thành {}", store.getId(), orderId, newStatus);
        return enrichOrderResponse(order);
    }

    private OrderResponse enrichOrderResponse(Order order) {
        OrderResponse response = orderMapper.toResponse(order);
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        response.setItems(enrichItemResponses(items));

        // Enrich payment info from payments table
        List<com.b2c.flash_sale_b2c_UTC2.payment.entity.Payment> payments =
                paymentRepository.findByOrderId(order.getId());
        if (payments != null && !payments.isEmpty()) {
            com.b2c.flash_sale_b2c_UTC2.payment.entity.Payment latest = payments.get(payments.size() - 1);
            response.setPaymentMethod(latest.getPaymentMethod());
            response.setPaymentStatus(latest.getStatus());
            response.setPaymentUrl(latest.getPaymentUrl());
            response.setQrCodeUrl(latest.getQrCodeData());
        }

        // Build timeline from order status history
        List<com.b2c.flash_sale_b2c_UTC2.order.dto.OrderTimelineDto> timeline = new java.util.ArrayList<>();
        timeline.add(com.b2c.flash_sale_b2c_UTC2.order.dto.OrderTimelineDto.builder()
                .status("PENDING_PAYMENT").label("Chờ thanh toán").at(order.getCreatedAt()).build());
        if (order.getShippedAt() != null) {
            timeline.add(com.b2c.flash_sale_b2c_UTC2.order.dto.OrderTimelineDto.builder()
                    .status("SHIPPING").label("Đang giao hàng").at(order.getShippedAt()).build());
        }
        if (order.getCompletedAt() != null) {
            timeline.add(com.b2c.flash_sale_b2c_UTC2.order.dto.OrderTimelineDto.builder()
                    .status("COMPLETED").label("Hoàn thành").at(order.getCompletedAt()).build());
        }
        response.setTimeline(timeline);

        return response;
    }

    private List<OrderItemResponse> enrichItemResponses(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<OrderItemResponse> itemResponses = orderMapper.toItemResponseList(items);
        if (itemResponses == null || itemResponses.isEmpty()) {
            return itemResponses != null ? itemResponses : List.of();
        }

        List<Long> variantIds = items.stream()
                .filter(i -> i.getVariant() != null)
                .map(i -> i.getVariant().getId())
                .distinct().toList();
        List<Long> productIds = items.stream()
                .filter(i -> i.getVariant() != null && i.getVariant().getProduct() != null)
                .map(i -> i.getVariant().getProduct().getId())
                .distinct().toList();

        Map<Long, com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse> variantImageMap =
                (imageService == null || variantIds.isEmpty()) ? Map.of() :
                        imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.VARIANT, variantIds);
        Map<Long, com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse> productImageMap =
                (imageService == null || productIds.isEmpty()) ? Map.of() :
                        imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.PRODUCT, productIds);

        int count = Math.min(items.size(), itemResponses.size());
        for (int i = 0; i < count; i++) {
            OrderItem item = items.get(i);
            OrderItemResponse dto = itemResponses.get(i);
            if (dto != null && item.getVariant() != null) {
                String img = null;
                if (variantImageMap != null && variantImageMap.containsKey(item.getVariant().getId())) {
                    var imgResp = variantImageMap.get(item.getVariant().getId());
                    if (imgResp != null) {
                        img = imgResp.getUrl();
                    }
                }
                if (img == null && item.getVariant().getProduct() != null && productImageMap != null
                        && productImageMap.containsKey(item.getVariant().getProduct().getId())) {
                    var imgResp = productImageMap.get(item.getVariant().getProduct().getId());
                    if (imgResp != null) {
                        img = imgResp.getUrl();
                    }
                }
                dto.setImageUrl(img);
            }
        }

        return itemResponses;
    }
}
