package com.b2c.flash_sale_b2c_UTC2.order.port;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.CreateFlashSaleOrderCommand;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.ExpiredOrderRef;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.FlashSaleOrderPort;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.OrderRef;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.order.entity.OrderItem;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderItemRepository;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Address;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.AddressRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation chính thức của FlashSaleOrderPort lưu đơn giữ chỗ vào cơ sở dữ liệu PostgreSQL.
 * Giao dịch có timeout cứng (3s) để đảm bảo không bị treo vượt quá grace period của job đối soát.
 */
@Slf4j
@Component("realFlashSaleOrderPort")
@RequiredArgsConstructor
public class RealFlashSaleOrderPort implements FlashSaleOrderPort {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final StoreRepository storeRepository;
    private final FlashSaleSlotRepository slotRepository;
    private final FlashSaleItemRepository flashSaleItemRepository;
    private final ProductVariantRepository productVariantRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 3)
    public OrderRef createPendingOrder(CreateFlashSaleOrderCommand cmd) {
        User buyer = userRepository.getReferenceById(cmd.userId());
        Address address = addressRepository.getReferenceById(cmd.addressId());
        Store store = storeRepository.getReferenceById(cmd.storeId());
        FlashSaleSlot slot = slotRepository.getReferenceById(cmd.slotId());
        FlashSaleItem flashSaleItem = flashSaleItemRepository.getReferenceById(cmd.flashSaleItemId());
        ProductVariant variant = productVariantRepository.getReferenceById(cmd.variantId());

        Address fullAddress = addressRepository.findById(cmd.addressId()).orElse(address);
        String recipientName = fullAddress.getContactName();
        String recipientPhone = fullAddress.getPhone();
        String shippingAddressText = String.format("%s, %s, %s, %s",
                fullAddress.getDetailAddress() != null ? fullAddress.getDetailAddress() : "",
                fullAddress.getWard() != null ? fullAddress.getWard() : "",
                fullAddress.getDistrict() != null ? fullAddress.getDistrict() : "",
                fullAddress.getProvince() != null ? fullAddress.getProvince() : "");

        BigDecimal subtotal = cmd.unitPrice().multiply(BigDecimal.valueOf(cmd.quantity()));
        Store fullStore = storeRepository.findById(cmd.storeId()).orElse(store);
        BigDecimal commissionRate = fullStore.getDefaultCommissionRate() != null 
                ? fullStore.getDefaultCommissionRate() : new BigDecimal("0.0500");
        BigDecimal platformFee = subtotal.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellerAmount = subtotal.subtract(platformFee);

        Order order = Order.builder()
                .orderCode(cmd.orderCode())
                .buyer(buyer)
                .store(store)
                .slot(slot)
                .shippingAddress(address)
                .recipientName(recipientName)
                .recipientPhone(recipientPhone)
                .shippingAddressText(shippingAddressText)
                .subtotalAmount(subtotal)
                .voucherDiscountAmount(BigDecimal.ZERO)
                .totalAmount(subtotal)
                .commissionRate(commissionRate)
                .platformFee(platformFee)
                .sellerAmount(sellerAmount)
                .status("PENDING_PAYMENT")
                .expiresAt(cmd.expiresAt())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        order = orderRepository.save(order);

        OrderItem orderItem = OrderItem.builder()
                .order(order)
                .flashSaleItem(flashSaleItem)
                .variant(variant)
                .productName(cmd.productName())
                .variantName(cmd.variantName())
                .priceAtPurchase(cmd.unitPrice())
                .quantity(cmd.quantity())
                .build();

        orderItemRepository.save(orderItem);

        log.info("[RealFlashSaleOrderPort] Đã tạo đơn giữ chỗ thực tế trong DB: orderId={}, orderCode={}", order.getId(), order.getOrderCode());
        return new OrderRef(order.getId(), order.getOrderCode());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpiredOrderRef> lockExpiredPendingOrders(Instant now, int batchSize) {
        List<Order> expiredOrders = orderRepository.findExpiredPendingOrdersWithLock(now, PageRequest.of(0, batchSize));
        List<ExpiredOrderRef> result = new ArrayList<>();
        for (Order o : expiredOrders) {
            List<OrderItem> items = orderItemRepository.findByOrderId(o.getId());
            if (!items.isEmpty()) {
                OrderItem first = items.get(0);
                Long itemId = first.getFlashSaleItem() != null ? first.getFlashSaleItem().getId() : null;
                result.add(new ExpiredOrderRef(
                        o.getId(),
                        itemId,
                        o.getSlot() != null ? o.getSlot().getId() : null,
                        o.getBuyer().getId(),
                        first.getQuantity()
                ));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public boolean cancelTimeoutIfPending(Long orderId) {
        int updated = orderRepository.cancelTimeoutIfPending(orderId, Instant.now());
        return updated > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingBySlot(Long slotId) {
        return orderRepository.countPendingBySlotId(slotId);
    }
}
