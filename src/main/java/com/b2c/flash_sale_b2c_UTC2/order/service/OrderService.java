package com.b2c.flash_sale_b2c_UTC2.order.service;

import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.order.dto.CreateOrderRequest;
import com.b2c.flash_sale_b2c_UTC2.order.dto.OrderResponse;
import com.b2c.flash_sale_b2c_UTC2.order.dto.UpdateOrderStatusRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderService {

    List<OrderResponse> createOrders(Long buyerId, CreateOrderRequest request);

    PageResponse<OrderResponse> getBuyerOrders(Long buyerId, Pageable pageable);

    OrderResponse getBuyerOrderDetail(Long buyerId, Long orderId);

    OrderResponse cancelOrder(Long buyerId, Long orderId);

    PageResponse<OrderResponse> getSellerOrders(Long sellerUserId, Pageable pageable);

    PageResponse<OrderResponse> getSellerOrders(Long sellerUserId, String status, Pageable pageable);

    OrderResponse getSellerOrderDetail(Long sellerUserId, Long orderId);

    OrderResponse updateOrderStatusBySeller(Long sellerUserId, Long orderId, UpdateOrderStatusRequest request);

    com.b2c.flash_sale_b2c_UTC2.order.dto.QrOrderDetailResponse getOrderQrDetail(Long buyerId, String orderCode);
}
