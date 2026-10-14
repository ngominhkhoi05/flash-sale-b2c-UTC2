package com.b2c.flash_sale_b2c_UTC2.order.mapper;

import com.b2c.flash_sale_b2c_UTC2.order.dto.OrderItemResponse;
import com.b2c.flash_sale_b2c_UTC2.order.dto.OrderResponse;
import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(source = "buyer.id", target = "buyerId")
    @Mapping(source = "store.id", target = "storeId")
    @Mapping(source = "store.storeName", target = "storeName")
    @Mapping(source = "slot.id", target = "slotId")
    @Mapping(source = "voucher.id", target = "voucherId")
    @Mapping(target = "items", ignore = true)
    OrderResponse toResponse(Order order);

    @Mapping(source = "variant.id", target = "variantId")
    @Mapping(source = "flashSaleItem.id", target = "flashSaleItemId")
    @Mapping(target = "itemSubtotal", expression = "java(calculateItemSubtotal(orderItem))")
    @Mapping(target = "imageUrl", ignore = true)
    OrderItemResponse toItemResponse(OrderItem orderItem);

    List<OrderItemResponse> toItemResponseList(List<OrderItem> orderItems);

    default BigDecimal calculateItemSubtotal(OrderItem orderItem) {
        if (orderItem == null || orderItem.getPriceAtPurchase() == null || orderItem.getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        return orderItem.getPriceAtPurchase().multiply(BigDecimal.valueOf(orderItem.getQuantity()));
    }
}
