package com.b2c.flash_sale_b2c_UTC2.cart.service;

import com.b2c.flash_sale_b2c_UTC2.cart.dto.AddToCartRequest;
import com.b2c.flash_sale_b2c_UTC2.cart.dto.CartResponse;
import com.b2c.flash_sale_b2c_UTC2.cart.dto.UpdateCartItemRequest;
import com.b2c.flash_sale_b2c_UTC2.cart.entity.Cart;
import com.b2c.flash_sale_b2c_UTC2.cart.entity.CartItem;
import com.b2c.flash_sale_b2c_UTC2.cart.exception.CartErrorCode;
import com.b2c.flash_sale_b2c_UTC2.cart.repository.CartItemRepository;
import com.b2c.flash_sale_b2c_UTC2.cart.repository.CartRepository;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.enums.StoreTier;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private CartServiceImpl cartService;

    private User sampleUser;
    private Cart sampleCart;
    private Store sampleStore;
    private Product sampleProduct;
    private ProductVariant sampleVariant;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder().id(1L).email("buyer@example.com").build();
        sampleCart = Cart.builder().id(10L).user(sampleUser).build();
        sampleStore = Store.builder().id(100L).storeName("Shop UTC2").tier(StoreTier.OFFICIAL_MALL).build();

        sampleProduct = Product.builder().id(50L).name("Áo thun UTC2").store(sampleStore).build();
        sampleVariant = ProductVariant.builder()
                .id(500L)
                .product(sampleProduct)
                .sku("SKU-AO-XL")
                .variantName("Size XL")
                .originalPrice(new BigDecimal("150000.00"))
                .stockQuantity(20)
                .build();
    }

    @Test
    @DisplayName("Thêm sản phẩm vào giỏ hàng thành công")
    void addToCart_Success() {
        AddToCartRequest request = AddToCartRequest.builder().variantId(500L).quantity(2).build();

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(productVariantRepository.findById(500L)).thenReturn(Optional.of(sampleVariant));
        when(cartItemRepository.findByCartIdAndVariantId(10L, 500L)).thenReturn(Optional.empty());

        CartItem savedItem = CartItem.builder().id(1000L).cart(sampleCart).variant(sampleVariant).quantity(2).build();
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedItem);
        when(cartItemRepository.findByCartId(10L)).thenReturn(List.of(savedItem));
        when(imageService.getPrimaryImagesByOwnerIds(any(ImageOwnerType.class), any())).thenReturn(java.util.Map.of());

        CartResponse response = cartService.addToCart(1L, request);

        assertNotNull(response);
        assertEquals(1, response.getStoreGroups().size());
        assertEquals("Shop UTC2", response.getStoreGroups().get(0).getStoreName());
        assertEquals(new BigDecimal("300000.00"), response.getGrandTotal());
        assertEquals(new BigDecimal("300000.00"), response.getSubtotal());
        assertEquals(BigDecimal.ZERO, response.getTotalShippingFee());
        assertEquals(new BigDecimal("300000.00"), response.getTotalAmount());

        assertEquals("OfficialMall", response.getStoreGroups().get(0).getStoreBadge());
        assertNotNull(response.getStoreGroups().get(0).getEstimatedDeliveryLabel());
        assertEquals("Tiêu chuẩn", response.getStoreGroups().get(0).getShippingMethodLabel());
        assertEquals(BigDecimal.ZERO, response.getStoreGroups().get(0).getShippingFee());

        assertEquals(50L, response.getStoreGroups().get(0).getItems().get(0).getProductId());
        assertEquals(20, response.getStoreGroups().get(0).getItems().get(0).getStockQuantity());
    }

    @Test
    @DisplayName("Thêm sản phẩm ném lỗi khi tồn kho không đủ")
    void addToCart_ShouldThrowException_WhenInsufficientStock() {
        AddToCartRequest request = AddToCartRequest.builder().variantId(500L).quantity(50).build();

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(productVariantRepository.findById(500L)).thenReturn(Optional.of(sampleVariant));

        BusinessException exception = assertThrows(BusinessException.class, () -> cartService.addToCart(1L, request));
        assertEquals(CartErrorCode.INSUFFICIENT_STOCK.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Cập nhật số lượng item thành công")
    void updateCartItem_Success() {
        CartItem existingItem = CartItem.builder().id(1000L).cart(sampleCart).variant(sampleVariant).quantity(2).build();
        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(5).build();

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.findById(1000L)).thenReturn(Optional.of(existingItem));
        when(cartItemRepository.findByCartId(10L)).thenReturn(List.of(existingItem));
        when(imageService.getPrimaryImagesByOwnerIds(any(ImageOwnerType.class), any())).thenReturn(java.util.Map.of());

        CartResponse response = cartService.updateCartItem(1L, 1000L, request);

        assertNotNull(response);
        assertEquals(5, existingItem.getQuantity());
        verify(cartItemRepository).save(existingItem);
    }

    @Test
    @DisplayName("Xóa toàn bộ sản phẩm trong giỏ hàng thành công")
    void clearCart_Success() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));

        cartService.clearCart(1L);

        verify(cartItemRepository).deleteAllByCartId(10L);
    }

    @Test
    @DisplayName("Lấy giỏ hàng thành công gom nhóm theo cửa hàng")
    void getCart_Success() {
        CartItem item = CartItem.builder().id(1000L).cart(sampleCart).variant(sampleVariant).quantity(2).build();
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.findByCartId(10L)).thenReturn(List.of(item));
        when(imageService.getPrimaryImagesByOwnerIds(any(ImageOwnerType.class), any())).thenReturn(java.util.Map.of());

        CartResponse response = cartService.getCart(1L);

        assertNotNull(response);
        assertEquals(1, response.getStoreGroups().size());
        assertEquals(new BigDecimal("300000.00"), response.getGrandTotal());
    }

    @Test
    @DisplayName("Thêm sản phẩm đã có sẵn trong giỏ -> cộng dồn số lượng")
    void addToCart_ItemAlreadyExists_ShouldAccumulateQuantity() {
        AddToCartRequest request = AddToCartRequest.builder().variantId(500L).quantity(3).build();
        CartItem existingItem = CartItem.builder().id(1000L).cart(sampleCart).variant(sampleVariant).quantity(2).build();

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(productVariantRepository.findById(500L)).thenReturn(Optional.of(sampleVariant));
        when(cartItemRepository.findByCartIdAndVariantId(10L, 500L)).thenReturn(Optional.of(existingItem));
        when(cartItemRepository.findByCartId(10L)).thenReturn(List.of(existingItem));
        when(imageService.getPrimaryImagesByOwnerIds(any(ImageOwnerType.class), any())).thenReturn(java.util.Map.of());

        CartResponse response = cartService.addToCart(1L, request);

        assertNotNull(response);
        assertEquals(5, existingItem.getQuantity()); // 2 + 3 = 5
        verify(cartItemRepository).save(existingItem);
    }

    @Test
    @DisplayName("Thêm sản phẩm đã có sẵn nhưng tổng số lượng cộng dồn vượt kho -> ném lỗi INSUFFICIENT_STOCK")
    void addToCart_ItemAlreadyExists_ExceedsStock_ShouldThrowException() {
        AddToCartRequest request = AddToCartRequest.builder().variantId(500L).quantity(5).build();
        CartItem existingItem = CartItem.builder().id(1000L).cart(sampleCart).variant(sampleVariant).quantity(18).build(); // 18 + 5 = 23 > 20

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(productVariantRepository.findById(500L)).thenReturn(Optional.of(sampleVariant));
        when(cartItemRepository.findByCartIdAndVariantId(10L, 500L)).thenReturn(Optional.of(existingItem));

        BusinessException exception = assertThrows(BusinessException.class, () -> cartService.addToCart(1L, request));
        assertEquals(CartErrorCode.INSUFFICIENT_STOCK.getCode(), exception.getErrorCode().getCode());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật số lượng item ném lỗi khi số lượng <= 0 (INVALID_QUANTITY)")
    void updateCartItem_ThrowsException_WhenInvalidQuantity() {
        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(0).build();

        BusinessException exception = assertThrows(BusinessException.class, () -> cartService.updateCartItem(1L, 1000L, request));
        assertEquals(CartErrorCode.INVALID_QUANTITY.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Cập nhật số lượng item ném lỗi khi vượt tồn kho (INSUFFICIENT_STOCK)")
    void updateCartItem_ThrowsException_WhenExceedStock() {
        CartItem existingItem = CartItem.builder().id(1000L).cart(sampleCart).variant(sampleVariant).quantity(2).build();
        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(50).build(); // 50 > 20 stock

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.findById(1000L)).thenReturn(Optional.of(existingItem));

        BusinessException exception = assertThrows(BusinessException.class, () -> cartService.updateCartItem(1L, 1000L, request));
        assertEquals(CartErrorCode.INSUFFICIENT_STOCK.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Cập nhật số lượng item ném lỗi khi item không thuộc giỏ của user (CART_ITEM_ACCESS_DENIED)")
    void updateCartItem_ThrowsException_WhenItemNotOwned() {
        Cart otherCart = Cart.builder().id(999L).build();
        CartItem otherItem = CartItem.builder().id(1000L).cart(otherCart).variant(sampleVariant).quantity(2).build();
        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(3).build();

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.findById(1000L)).thenReturn(Optional.of(otherItem));

        BusinessException exception = assertThrows(BusinessException.class, () -> cartService.updateCartItem(1L, 1000L, request));
        assertEquals(CartErrorCode.CART_ITEM_ACCESS_DENIED.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Xóa một món hàng khỏi giỏ thành công")
    void removeCartItem_Success() {
        CartItem existingItem = CartItem.builder().id(1000L).cart(sampleCart).variant(sampleVariant).quantity(2).build();

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.findById(1000L)).thenReturn(Optional.of(existingItem));

        cartService.removeCartItem(1L, 1000L);

        verify(cartItemRepository).delete(existingItem);
    }

    @Test
    @DisplayName("Xóa một món hàng ném lỗi khi món hàng không thuộc giỏ của user (CART_ITEM_ACCESS_DENIED)")
    void removeCartItem_ThrowsException_WhenItemNotOwned() {
        Cart otherCart = Cart.builder().id(999L).build();
        CartItem otherItem = CartItem.builder().id(1000L).cart(otherCart).variant(sampleVariant).quantity(2).build();

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.findById(1000L)).thenReturn(Optional.of(otherItem));

        BusinessException exception = assertThrows(BusinessException.class, () -> cartService.removeCartItem(1L, 1000L));
        assertEquals(CartErrorCode.CART_ITEM_ACCESS_DENIED.getCode(), exception.getErrorCode().getCode());
        verify(cartItemRepository, never()).delete(any());
    }
}
