package com.b2c.flash_sale_b2c_UTC2.common.test;

import com.b2c.flash_sale_b2c_UTC2.auth.entity.AuthAccount;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Address;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.Voucher;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.Wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class TestDataFactory {

    private TestDataFactory() {}

    public static User user(Long id) {
        return User.builder()
                .id(id)
                .email("user" + id + "@x.com")
                .phone("0900000" + String.format("%03d", id == null ? 0 : id.intValue() % 1000))
                .fullName("User " + id)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();
    }

    public static User user(String email) {
        return User.builder()
                .email(email)
                .phone("0900000999")
                .fullName("User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();
    }

    public static AuthAccount authAccount(User user) {
        return AuthAccount.builder()
                .user(user)
                .provider("LOCAL")
                .providerAccountId(user.getEmail())
                .providerEmail(user.getEmail())
                .createdAt(Instant.now())
                .build();
    }

    public static Store store(Long id, User owner) {
        return Store.builder()
                .id(id)
                .user(owner)
                .storeName("Store " + id)
                .description("desc")
                .status("APPROVED")
                .createdAt(Instant.now())
                .build();
    }

    public static Category category(String name) {
        return Category.builder()
                .name(name)
                .slug(name.toLowerCase().replace(' ', '-'))
                .commissionRate(new BigDecimal("0.0500"))
                .build();
    }

    public static Product product(Long id, Store store, Category cat) {
        return Product.builder()
                .id(id)
                .store(store)
                .category(cat)
                .name("Product " + id)
                .description("desc")
                .status("ACTIVE")
                .build();
    }

    public static ProductVariant variant(Long id, Product product, String sku, int stock) {
        return ProductVariant.builder()
                .id(id)
                .product(product)
                .sku(sku)
                .variantName("Variant " + id)
                .originalPrice(new BigDecimal("100000"))
                .stockQuantity(stock)
                .status("ACTIVE")
                .version(0L)
                .build();
    }

    public static Address userAddress(User user) {
        return Address.builder()
                .user(user)
                .contactName(user.getFullName())
                .phone(user.getPhone())
                .province("HCM")
                .district("Q1")
                .ward("P1")
                .detailAddress("123 Test Street")
                .isDefault(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static Address storeAddress(Store store) {
        return Address.builder()
                .store(store)
                .contactName("Kho " + store.getStoreName())
                .phone("0980000000")
                .province("HN")
                .district("QBD")
                .ward("P2")
                .detailAddress("456 Warehouse")
                .isDefault(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static Wallet wallet(Store store) {
        return Wallet.builder()
                .store(store)
                .balance(BigDecimal.ZERO)
                .frozenBalance(BigDecimal.ZERO)
                .updatedAt(Instant.now())
                .build();
    }

    public static FlashSaleSlot slot(Long id, Instant start, Instant end) {
        return FlashSaleSlot.builder()
                .id(id)
                .title("Slot " + id)
                .startTime(start)
                .endTime(end)
                .reservationTtlSeconds(300)
                .status("UPCOMING")
                .createdAt(Instant.now())
                .build();
    }

    public static FlashSaleSlot activeSlot(Long id) {
        Instant now = Instant.now();
        return FlashSaleSlot.builder()
                .id(id)
                .title("Active " + id)
                .startTime(now.minus(1, ChronoUnit.HOURS))
                .endTime(now.plus(2, ChronoUnit.HOURS))
                .reservationTtlSeconds(300)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();
    }

    public static FlashSaleItem flashSaleItem(Long id, FlashSaleSlot slot, ProductVariant variant,
                                             BigDecimal price, Integer allocatedStock) {
        return FlashSaleItem.builder()
                .id(id)
                .slot(slot)
                .variant(variant)
                .flashSalePrice(price)
                .allocatedStock(allocatedStock)
                .availableStock(allocatedStock)
                .userPurchaseLimit(2)
                .status("APPROVED")
                .createdAt(Instant.now())
                .build();
    }

    public static FlashSaleItem flashSaleItem(FlashSaleSlot slot, ProductVariant variant,
                                              Integer allocatedStock) {
        BigDecimal price = variant.getOriginalPrice().multiply(new BigDecimal("0.7"));
        return FlashSaleItem.builder()
                .slot(slot)
                .variant(variant)
                .flashSalePrice(price)
                .allocatedStock(allocatedStock)
                .availableStock(allocatedStock)
                .userPurchaseLimit(2)
                .status("APPROVED")
                .createdAt(Instant.now())
                .build();
    }

    public static Order order(Long id, User buyer, Store store) {
        return Order.builder()
                .id(id)
                .orderCode("ORD-" + id)
                .buyer(buyer)
                .store(store)
                .recipientName(buyer.getFullName())
                .recipientPhone(buyer.getPhone())
                .shippingAddressText("123 Test Street")
                .subtotalAmount(new BigDecimal("100000"))
                .totalAmount(new BigDecimal("100000"))
                .commissionRate(new BigDecimal("0.05"))
                .platformFee(new BigDecimal("5000"))
                .sellerAmount(new BigDecimal("95000"))
                .status("PENDING_PAYMENT")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static Voucher voucher(Long id, Store store, String code) {
        Instant now = Instant.now();
        return Voucher.builder()
                .id(id)
                .code(code)
                .store(store)
                .discountType("FIXED")
                .discountValue(new BigDecimal("10000"))
                .minOrderAmount(BigDecimal.ZERO)
                .totalQuantity(100)
                .usedQuantity(0)
                .userUsageLimit(1)
                .startTime(now.minus(1, ChronoUnit.DAYS))
                .endTime(now.plus(30, ChronoUnit.DAYS))
                .status("ACTIVE")
                .createdAt(now)
                .build();
    }
}