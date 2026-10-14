package com.b2c.flash_sale_b2c_UTC2.cart.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.cart.dto.AddToCartRequest;
import com.b2c.flash_sale_b2c_UTC2.cart.dto.CartResponse;
import com.b2c.flash_sale_b2c_UTC2.cart.dto.UpdateCartItemRequest;
import com.b2c.flash_sale_b2c_UTC2.cart.service.CartService;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cart", description = "Quản lý giỏ hàng cá nhân và tách nhóm theo gian hàng")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Xem giỏ hàng cá nhân", description = "Lấy giỏ hàng của người dùng hiện tại, tự động gom nhóm sản phẩm theo Gian hàng")
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CartResponse response = cartService.getCart(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin giỏ hàng thành công", response));
    }

    @Operation(summary = "Thêm sản phẩm vào giỏ hàng", description = "Thêm một biến thể sản phẩm vào giỏ hàng")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AddToCartRequest request
    ) {
        CartResponse response = cartService.addToCart(userDetails.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm sản phẩm vào giỏ hàng thành công", response));
    }

    @Operation(summary = "Cập nhật số lượng sản phẩm trong giỏ", description = "Thay đổi số lượng của sản phẩm đã chọn trong giỏ hàng")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateCartItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        CartResponse response = cartService.updateCartItem(userDetails.getUser().getId(), itemId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật số lượng thành công", response));
    }

    @Operation(summary = "Xóa sản phẩm khỏi giỏ hàng", description = "Xóa một sản phẩm cụ thể khỏi giỏ hàng")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> removeCartItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("itemId") Long itemId
    ) {
        cartService.removeCartItem(userDetails.getUser().getId(), itemId);
        return ResponseEntity.ok(ApiResponse.success("Xóa sản phẩm khỏi giỏ hàng thành công", null));
    }

    @Operation(summary = "Xóa toàn bộ giỏ hàng", description = "Làm trống giỏ hàng cá nhân")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        cartService.clearCart(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("Xóa toàn bộ giỏ hàng thành công", null));
    }
}
