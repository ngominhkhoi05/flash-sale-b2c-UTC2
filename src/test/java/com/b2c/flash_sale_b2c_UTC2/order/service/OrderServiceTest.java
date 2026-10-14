package com.b2c.flash_sale_b2c_UTC2.order.service;

import com.b2c.flash_sale_b2c_UTC2.cart.repository.CartItemRepository;
import com.b2c.flash_sale_b2c_UTC2.cart.repository.CartRepository;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse;
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
import com.b2c.flash_sale_b2c_UTC2.payment.repository.PaymentRepository;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private VoucherUsageRepository voucherUsageRepository;

    @Mock
    private VoucherService voucherService;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User buyer;
    private Store store;
    private Address address;
    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        buyer = User.builder().id(1L).email("buyer@example.com").build();
        store = Store.builder().id(10L).storeName("Shop UTC2").status("APPROVED").defaultCommissionRate(new BigDecimal("0.0500")).build();

        address = Address.builder()
                .id(100L)
                .user(buyer)
                .contactName("Nguyễn Văn A")
                .phone("0987654321")
                .province("TP.HCM")
                .district("Quận 9")
                .ward("Tăng Nhơn Phú A")
                .detailAddress("123 Lê Văn Việt")
                .build();

        product = Product.builder().id(50L).name("Laptop UTC2").store(store).build();
        variant = ProductVariant.builder()
                .id(500L)
                .product(product)
                .variantName("i7 / 16GB")
                .originalPrice(new BigDecimal("20000000.00"))
                .stockQuantity(10)
                .build();
    }

    @Test
    @DisplayName("Đặt hàng thành công với địa chỉ chính chủ và trừ tồn kho gốc")
    void createOrders_Success() {
        CheckoutItemRequest itemReq = CheckoutItemRequest.builder().variantId(500L).quantity(1).build();
        CheckoutStoreOrderRequest storeReq = CheckoutStoreOrderRequest.builder().storeId(10L).items(List.of(itemReq)).build();
        CreateOrderRequest request = CreateOrderRequest.builder().shippingAddressId(100L).storeOrders(List.of(storeReq)).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(addressRepository.findById(100L)).thenReturn(Optional.of(address));
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));
        when(productVariantRepository.findById(500L)).thenReturn(Optional.of(variant));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        Order savedOrder = Order.builder().id(1000L).orderCode("ORD-12345").buyer(buyer).store(store).subtotalAmount(new BigDecimal("20000000.00")).totalAmount(new BigDecimal("20000000.00")).build();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderMapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(1000L).orderCode("ORD-12345").build());

        List<OrderResponse> responses = orderService.createOrders(1L, request);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(9, variant.getStockQuantity()); // Tồn kho giảm từ 10 -> 9
        verify(productVariantRepository).save(variant);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("Đặt hàng thất bại khi địa chỉ không thuộc quyền sở hữu của Buyer (403)")
    void createOrders_ShouldThrowException_WhenAddressNotOwned() {
        User anotherUser = User.builder().id(999L).build();
        Address anotherAddress = Address.builder().id(200L).user(anotherUser).build();

        CreateOrderRequest request = CreateOrderRequest.builder().shippingAddressId(200L).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(addressRepository.findById(200L)).thenReturn(Optional.of(anotherAddress));

        BusinessException exception = assertThrows(BusinessException.class, () -> orderService.createOrders(1L, request));
        assertEquals(UserErrorCode.ADDRESS_ACCESS_DENIED.getCode(), exception.getErrorCode().getCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hủy đơn hàng chưa thanh toán hoàn trả lại số kho tương ứng")
    void cancelOrder_Success_ShouldRestoreStock() {
        Order pendingOrder = Order.builder().id(1000L).buyer(buyer).store(store).status("PENDING_PAYMENT").build();
        OrderItem item = OrderItem.builder().id(5000L).order(pendingOrder).variant(variant).quantity(2).build();

        when(orderRepository.findById(1000L)).thenReturn(Optional.of(pendingOrder));
        when(orderItemRepository.findByOrderId(1000L)).thenReturn(List.of(item));
        when(orderRepository.save(any(Order.class))).thenReturn(pendingOrder);
        when(orderMapper.toResponse(pendingOrder)).thenReturn(OrderResponse.builder().id(1000L).status("CANCELLED").build());

        OrderResponse response = orderService.cancelOrder(1L, 1000L);

        assertNotNull(response);
        assertEquals(12, variant.getStockQuantity()); // Tồn kho hoàn trả từ 10 -> 12
        verify(productVariantRepository).save(variant);
        verify(voucherService).rollbackVoucherUsage(1000L);
        assertEquals("CANCELLED", pendingOrder.getStatus());
    }

    @Test
    @DisplayName("Seller hủy đơn hàng hoàn trả lại số kho và bồi hoàn voucher tương ứng")
    void updateOrderStatusBySeller_Cancel_ShouldRestoreStockAndVoucher() {
        User sellerUser = User.builder().id(2L).build();
        Store sellerStore = Store.builder().id(10L).user(sellerUser).build();

        Order order = Order.builder().id(1000L).store(sellerStore).status("PENDING_PAYMENT").build();
        OrderItem item = OrderItem.builder().id(5000L).order(order).variant(variant).quantity(3).build();
        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder().status("CANCELLED").build();

        when(storeRepository.findByUserId(2L)).thenReturn(Optional.of(sellerStore));
        when(orderRepository.findById(1000L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1000L)).thenReturn(List.of(item));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(OrderResponse.builder().id(1000L).status("CANCELLED").build());

        OrderResponse response = orderService.updateOrderStatusBySeller(2L, 1000L, request);

        assertNotNull(response);
        assertEquals(13, variant.getStockQuantity()); // 10 -> 13
        verify(productVariantRepository).save(variant);
        verify(voucherService).rollbackVoucherUsage(1000L);
        assertEquals("CANCELLED", order.getStatus());
    }

    @Test
    @DisplayName("Seller cập nhật trạng thái đơn hàng thành công")
    void updateOrderStatusBySeller_Success() {
        User sellerUser = User.builder().id(2L).build();
        Store sellerStore = Store.builder().id(10L).user(sellerUser).build();

        Order order = Order.builder().id(1000L).store(sellerStore).status("PAID").build();
        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder().status("SHIPPED").build();

        when(storeRepository.findByUserId(2L)).thenReturn(Optional.of(sellerStore));
        when(orderRepository.findById(1000L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(OrderResponse.builder().id(1000L).status("SHIPPED").build());

        OrderResponse response = orderService.updateOrderStatusBySeller(2L, 1000L, request);

        assertNotNull(response);
        assertEquals("SHIPPED", order.getStatus());
        verify(orderRepository).save(order);
        verify(voucherService, never()).rollbackVoucherUsage(any());
    }

    @Test
    @DisplayName("Tạo đơn hàng ném lỗi khi tồn kho biến thể không đủ (INSUFFICIENT_VARIANT_STOCK)")
    void createOrders_ThrowsException_WhenInsufficientVariantStock() {
        CheckoutItemRequest itemReq = CheckoutItemRequest.builder().variantId(500L).quantity(25).build(); // 25 > 10 stock
        CheckoutStoreOrderRequest storeReq = CheckoutStoreOrderRequest.builder().storeId(10L).items(List.of(itemReq)).build();
        CreateOrderRequest request = CreateOrderRequest.builder().shippingAddressId(100L).storeOrders(List.of(storeReq)).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(addressRepository.findById(100L)).thenReturn(Optional.of(address));
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));
        when(productVariantRepository.findById(500L)).thenReturn(Optional.of(variant));

        BusinessException exception = assertThrows(BusinessException.class, () -> orderService.createOrders(1L, request));
        assertEquals(OrderErrorCode.INSUFFICIENT_VARIANT_STOCK.getCode(), exception.getErrorCode().getCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo đơn hàng ném lỗi khi danh sách món hàng rỗng (EMPTY_ORDER_ITEMS)")
    void createOrders_ThrowsException_WhenEmptyItems() {
        CheckoutStoreOrderRequest storeReq = CheckoutStoreOrderRequest.builder().storeId(10L).items(List.of()).build();
        CreateOrderRequest request = CreateOrderRequest.builder().shippingAddressId(100L).storeOrders(List.of(storeReq)).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(addressRepository.findById(100L)).thenReturn(Optional.of(address));
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));

        BusinessException exception = assertThrows(BusinessException.class, () -> orderService.createOrders(1L, request));
        assertEquals(OrderErrorCode.EMPTY_ORDER_ITEMS.getCode(), exception.getErrorCode().getCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo đơn hàng có áp dụng Voucher thành công")
    void createOrders_Success_WithVoucher() {
        CheckoutItemRequest itemReq = CheckoutItemRequest.builder().variantId(500L).quantity(2).build();
        CheckoutStoreOrderRequest storeReq = CheckoutStoreOrderRequest.builder()
                .storeId(10L)
                .voucherCode("DISCOUNT50")
                .items(List.of(itemReq))
                .build();
        CreateOrderRequest request = CreateOrderRequest.builder().shippingAddressId(100L).storeOrders(List.of(storeReq)).build();

        Voucher voucher = Voucher.builder().id(200L).code("DISCOUNT50").usedQuantity(1).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(addressRepository.findById(100L)).thenReturn(Optional.of(address));
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));
        when(productVariantRepository.findById(500L)).thenReturn(Optional.of(variant));
        when(voucherService.validateVoucherForOrder(eq("DISCOUNT50"), eq(1L), eq(10L), any(BigDecimal.class)))
                .thenReturn(voucher);
        when(voucherService.calculateDiscountAmount(eq(voucher), any(BigDecimal.class)))
                .thenReturn(new BigDecimal("50000.00"));

        Order mockOrder = Order.builder().id(1000L).orderCode("ORD-123").totalAmount(new BigDecimal("250000.00")).build();
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);
        when(orderMapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(1000L).build());
        when(orderMapper.toItemResponseList(any())).thenReturn(List.of());

        List<OrderResponse> responses = orderService.createOrders(1L, request);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(2, voucher.getUsedQuantity()); // 1 -> 2
        verify(voucherRepository).save(voucher);
        verify(voucherUsageRepository).save(any(VoucherUsage.class));
    }

    @Test
    @DisplayName("Lấy danh sách đơn hàng của Buyer thành công")
    void getBuyerOrders_Success() {
        Order order = Order.builder().id(1000L).buyer(buyer).build();
        Page<Order> orderPage = new PageImpl<>(List.of(order));
        Pageable pageable = PageRequest.of(0, 10);

        when(orderRepository.findByBuyerId(1L, pageable)).thenReturn(orderPage);
        when(orderMapper.toResponse(order)).thenReturn(OrderResponse.builder().id(1000L).build());
        when(orderItemRepository.findByOrderId(1000L)).thenReturn(List.of());

        PageResponse<OrderResponse> response = orderService.getBuyerOrders(1L, pageable);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }

    @Test
    @DisplayName("Xem chi tiết đơn hàng thành công")
    void getBuyerOrderDetail_Success() {
        Order order = Order.builder().id(1000L).buyer(buyer).build();

        when(orderRepository.findById(1000L)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(OrderResponse.builder().id(1000L).build());
        when(orderItemRepository.findByOrderId(1000L)).thenReturn(List.of());

        OrderResponse response = orderService.getBuyerOrderDetail(1L, 1000L);

        assertNotNull(response);
        assertEquals(1000L, response.getId());
    }

    @Test
    @DisplayName("Xem chi tiết đơn hàng ném lỗi khi không phải chủ đơn (ORDER_ACCESS_DENIED)")
    void getBuyerOrderDetail_ThrowsException_WhenNotOwned() {
        User otherBuyer = User.builder().id(999L).build();
        Order order = Order.builder().id(1000L).buyer(otherBuyer).build();

        when(orderRepository.findById(1000L)).thenReturn(Optional.of(order));

        BusinessException exception = assertThrows(BusinessException.class, () -> orderService.getBuyerOrderDetail(1L, 1000L));
        assertEquals(OrderErrorCode.ORDER_ACCESS_DENIED.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Hủy đơn ném lỗi khi không phải chủ đơn (ORDER_ACCESS_DENIED)")
    void cancelOrder_ThrowsException_WhenNotOwned() {
        User otherBuyer = User.builder().id(999L).build();
        Order order = Order.builder().id(1000L).buyer(otherBuyer).status("PENDING_PAYMENT").build();

        when(orderRepository.findById(1000L)).thenReturn(Optional.of(order));

        BusinessException exception = assertThrows(BusinessException.class, () -> orderService.cancelOrder(1L, 1000L));
        assertEquals(OrderErrorCode.ORDER_ACCESS_DENIED.getCode(), exception.getErrorCode().getCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hủy đơn ném lỗi khi trạng thái không hợp lệ (INVALID_STATUS_TRANSITION)")
    void cancelOrder_ThrowsException_WhenInvalidStatus() {
        Order order = Order.builder().id(1000L).buyer(buyer).status("SHIPPED").build();

        when(orderRepository.findById(1000L)).thenReturn(Optional.of(order));

        BusinessException exception = assertThrows(BusinessException.class, () -> orderService.cancelOrder(1L, 1000L));
        assertEquals(OrderErrorCode.INVALID_STATUS_TRANSITION.getCode(), exception.getErrorCode().getCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lấy danh sách đơn hàng của Seller thành công")
    void getSellerOrders_Success() {
        User sellerUser = User.builder().id(2L).build();
        Store sellerStore = Store.builder().id(10L).user(sellerUser).build();
        Order order = Order.builder().id(1000L).store(sellerStore).build();
        Page<Order> page = new PageImpl<>(List.of(order));
        Pageable pageable = PageRequest.of(0, 10);

        when(storeRepository.findByUserId(2L)).thenReturn(Optional.of(sellerStore));
        when(orderRepository.findByStoreId(10L, pageable)).thenReturn(page);
        when(orderMapper.toResponse(order)).thenReturn(OrderResponse.builder().id(1000L).build());
        when(orderItemRepository.findByOrderId(1000L)).thenReturn(List.of());

        PageResponse<OrderResponse> response = orderService.getSellerOrders(2L, pageable);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }

    @Test
    @DisplayName("Seller cập nhật trạng thái đơn ném lỗi khi đơn không thuộc shop mình (ORDER_ACCESS_DENIED)")
    void updateOrderStatusBySeller_ThrowsException_WhenOrderNotBelongsToStore() {
        User sellerUser = User.builder().id(2L).build();
        Store sellerStore = Store.builder().id(10L).user(sellerUser).build();
        Store anotherStore = Store.builder().id(999L).build();
        Order order = Order.builder().id(1000L).store(anotherStore).status("PAID").build();

        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder().status("SHIPPED").build();

        when(storeRepository.findByUserId(2L)).thenReturn(Optional.of(sellerStore));
        when(orderRepository.findById(1000L)).thenReturn(Optional.of(order));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                orderService.updateOrderStatusBySeller(2L, 1000L, request));
        assertEquals(OrderErrorCode.ORDER_ACCESS_DENIED.getCode(), exception.getErrorCode().getCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("getBuyerOrderDetail: Làm giàu imageUrl cho từng order item thành công")
    void getBuyerOrderDetail_EnrichesImageUrl_Success() {
        Long buyerId = 1L;
        Long orderId = 100L;

        User buyerUser = User.builder().id(buyerId).build();
        Order order = Order.builder().id(orderId).buyer(buyerUser).build();

        ProductVariant testVariant = ProductVariant.builder().id(500L).product(Product.builder().id(50L).build()).build();
        OrderItem item = OrderItem.builder().id(10L).order(order).variant(testVariant).productName("Áo thun").quantity(1).build();

        OrderItemResponse itemDto = OrderItemResponse.builder().id(10L).variantId(500L).productName("Áo thun").quantity(1).build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(OrderResponse.builder().id(orderId).build());
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(orderMapper.toItemResponseList(List.of(item))).thenReturn(List.of(itemDto));

        ImageResponse imgResp = ImageResponse.builder().url("https://res.cloudinary.com/demo/image/upload/sample.jpg").build();
        when(imageService.getPrimaryImagesByOwnerIds(eq(ImageOwnerType.VARIANT), anyList()))
                .thenReturn(Map.of(500L, imgResp));

        OrderResponse response = orderService.getBuyerOrderDetail(buyerId, orderId);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals("https://res.cloudinary.com/demo/image/upload/sample.jpg", response.getItems().get(0).getImageUrl());
    }
}
