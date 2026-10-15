package com.b2c.flash_sale_b2c_UTC2.cart.service;

import com.b2c.flash_sale_b2c_UTC2.cart.dto.AddToCartRequest;
import com.b2c.flash_sale_b2c_UTC2.cart.dto.CartItemResponse;
import com.b2c.flash_sale_b2c_UTC2.cart.dto.CartResponse;
import com.b2c.flash_sale_b2c_UTC2.cart.dto.CartStoreGroupResponse;
import com.b2c.flash_sale_b2c_UTC2.cart.dto.UpdateCartItemRequest;
import com.b2c.flash_sale_b2c_UTC2.cart.entity.Cart;
import com.b2c.flash_sale_b2c_UTC2.cart.entity.CartItem;
import com.b2c.flash_sale_b2c_UTC2.cart.exception.CartErrorCode;
import com.b2c.flash_sale_b2c_UTC2.cart.repository.CartItemRepository;
import com.b2c.flash_sale_b2c_UTC2.cart.repository.CartRepository;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.exception.UserErrorCode;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.b2c.flash_sale_b2c_UTC2.store.enums.StoreTier;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final ImageService imageService;

    @Override
    @Transactional
    public CartResponse getCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addToCart(Long userId, AddToCartRequest request) {
        if (request.getQuantity() <= 0) {
            throw new BusinessException(CartErrorCode.INVALID_QUANTITY);
        }

        Cart cart = getOrCreateCart(userId);
        ProductVariant variant = productVariantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND));

        CartItem existingItem = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variant.getId())
                .orElse(null);

        int newQuantity = request.getQuantity();
        if (existingItem != null) {
            newQuantity += existingItem.getQuantity();
        }

        if (variant.getStockQuantity() < newQuantity) {
            throw new BusinessException(CartErrorCode.INSUFFICIENT_STOCK);
        }

        if (existingItem != null) {
            existingItem.setQuantity(newQuantity);
            existingItem.setUpdatedAt(Instant.now());
            cartItemRepository.save(existingItem);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .quantity(newQuantity)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            cartItemRepository.save(newItem);
        }

        log.info("Thêm sản phẩm variantId={} số lượng={} vào giỏ hàng userId={}", variant.getId(), request.getQuantity(), userId);
        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateCartItem(Long userId, Long itemId, UpdateCartItemRequest request) {
        if (request.getQuantity() <= 0) {
            throw new BusinessException(CartErrorCode.INVALID_QUANTITY);
        }

        Cart cart = getOrCreateCart(userId);
        CartItem cartItem = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND));

        if (!cartItem.getCart().getId().equals(cart.getId())) {
            throw new BusinessException(CartErrorCode.CART_ITEM_ACCESS_DENIED);
        }

        if (cartItem.getVariant().getStockQuantity() < request.getQuantity()) {
            throw new BusinessException(CartErrorCode.INSUFFICIENT_STOCK);
        }

        cartItem.setQuantity(request.getQuantity());
        cartItem.setUpdatedAt(Instant.now());
        cartItemRepository.save(cartItem);

        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public void removeCartItem(Long userId, Long itemId) {
        Cart cart = getOrCreateCart(userId);
        CartItem cartItem = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND));

        if (!cartItem.getCart().getId().equals(cart.getId())) {
            throw new BusinessException(CartErrorCode.CART_ITEM_ACCESS_DENIED);
        }

        cartItemRepository.delete(cartItem);
        log.info("Xóa mục giỏ hàng id={} của userId={}", itemId, userId);
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        cartItemRepository.deleteAllByCartId(cart.getId());
        log.info("Xóa toàn bộ giỏ hàng của userId={}", userId);
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
                    Cart newCart = Cart.builder()
                            .user(user)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                    return cartRepository.save(newCart);
                });
    }

    private CartResponse buildCartResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        Map<Long, CartStoreGroupResponse> storeGroupMap = new LinkedHashMap<>();

        int totalItems = 0;
        BigDecimal grandTotal = BigDecimal.ZERO;

        // Batch-load ảnh variant và product (1 query mỗi loại) để tránh N+1
        java.util.List<Long> variantIds = items.stream().map(i -> i.getVariant().getId()).toList();
        java.util.List<Long> productIds = items.stream().map(i -> i.getVariant().getProduct().getId()).distinct().toList();
        java.util.Map<Long, String> variantImageMap = variantIds.isEmpty() ? java.util.Map.of() :
                imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.VARIANT, variantIds)
                        .entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, e -> e.getValue().getUrl()));
        java.util.Map<Long, String> productImageMap = productIds.isEmpty() ? java.util.Map.of() :
                imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.PRODUCT, productIds)
                        .entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, e -> e.getValue().getUrl()));

        for (CartItem item : items) {
            ProductVariant variant = item.getVariant();
            Store store = variant.getProduct().getStore();

            BigDecimal price = variant.getOriginalPrice();
            BigDecimal itemSubtotal = price.multiply(BigDecimal.valueOf(item.getQuantity()));

            // Ưu tiên ảnh variant, fallback ảnh product
            String imageUrl = variantImageMap.get(variant.getId());
            if (imageUrl == null) {
                imageUrl = productImageMap.get(variant.getProduct().getId());
            }

            CartItemResponse itemDto = CartItemResponse.builder()
                    .id(item.getId())
                    .productId(variant.getProduct().getId())
                    .variantId(variant.getId())
                    .sku(variant.getSku())
                    .productName(variant.getProduct().getName())
                    .variantName(variant.getVariantName())
                    .imageUrl(imageUrl)
                    .price(price)
                    .stockQuantity(variant.getStockQuantity())
                    .quantity(item.getQuantity())
                    .itemSubtotal(itemSubtotal)
                    .storeId(store.getId())
                    .storeName(store.getStoreName())
                    .build();

            totalItems += item.getQuantity();
            grandTotal = grandTotal.add(itemSubtotal);

            CartStoreGroupResponse group = storeGroupMap.computeIfAbsent(store.getId(), k -> {
                String storeBadge = "";
                if (store.getTier() != null) {
                    switch (store.getTier()) {
                        case OFFICIAL_MALL -> storeBadge = "OfficialMall";
                        case PREFERRED -> storeBadge = "Mall";
                        default -> storeBadge = "";
                    }
                }
                LocalDate today = LocalDate.now();
                LocalDate fromDate = today.plusDays(2);
                LocalDate toDate = today.plusDays(4);
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd 'Th'M");
                String estimatedDeliveryLabel = "Giao vào " + fromDate.format(fmt) + " - " + toDate.format(fmt);

                return CartStoreGroupResponse.builder()
                        .storeId(store.getId())
                        .storeName(store.getStoreName())
                        .storeBadge(storeBadge)
                        .estimatedDeliveryLabel(estimatedDeliveryLabel)
                        .shippingMethodLabel("Tiêu chuẩn")
                        .shippingFee(BigDecimal.ZERO)
                        .items(new ArrayList<>())
                        .storeSubtotal(BigDecimal.ZERO)
                        .build();
            });

            group.getItems().add(itemDto);
            group.setStoreSubtotal(group.getStoreSubtotal().add(itemSubtotal));
        }

        BigDecimal totalShippingFee = BigDecimal.ZERO;
        for (CartStoreGroupResponse g : storeGroupMap.values()) {
            if (g.getShippingFee() != null) {
                totalShippingFee = totalShippingFee.add(g.getShippingFee());
            }
        }
        BigDecimal totalAmount = grandTotal.add(totalShippingFee);

        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUser().getId())
                .storeGroups(new ArrayList<>(storeGroupMap.values()))
                .totalItems(totalItems)
                .subtotal(grandTotal)
                .totalShippingFee(totalShippingFee)
                .totalAmount(totalAmount)
                .grandTotal(grandTotal)
                .build();
    }
}
