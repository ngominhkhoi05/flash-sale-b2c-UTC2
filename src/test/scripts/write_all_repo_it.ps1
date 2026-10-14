$utf8 = [System.Text.UTF8Encoding]::new($false)
function Write-Utf8($path, $content) {
    [System.IO.File]::WriteAllText($path, $content, $utf8)
}

$root = "src\test\java\com\b2c\flash_sale_b2c_UTC2"

# CategoryRepositoryIT
$content = @'
package com.b2c.flash_sale_b2c_UTC2.product.repository;

import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class CategoryRepositoryIT {

    @Autowired private CategoryRepository categoryRepository;
    @PersistenceContext private EntityManager em;

    private Category persistCategory(String name, String slug) {
        Category c = Category.builder().name(name).slug(slug).build();
        em.persist(c);
        em.flush();
        return c;
    }

    @Test
    @DisplayName("findBySlug returns category by slug")
    void findBySlug_Success() {
        persistCategory("Electronics", "electronics");

        Optional<Category> found = categoryRepository.findBySlug("electronics");

        assertTrue(found.isPresent());
        assertEquals("Electronics", found.get().getName());
    }

    @Test
    @DisplayName("existsBySlug true when duplicate slug")
    void existsBySlug_True() {
        persistCategory("Fashion", "fashion");
        assertTrue(categoryRepository.existsBySlug("fashion"));
    }

    @Test
    @DisplayName("existsByName true when duplicate name")
    void existsByName_True() {
        persistCategory("Beauty", "beauty");
        assertTrue(categoryRepository.existsByName("Beauty"));
    }

    @Test
    @DisplayName("Custom commission rate is persisted")
    void customCommissionRate_Persisted() {
        Category c = Category.builder()
                .name("Luxury").slug("luxury")
                .commissionRate(new BigDecimal("0.1500")).build();
        em.persist(c);
        em.flush();

        Category found = categoryRepository.findById(c.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("0.1500").compareTo(found.getCommissionRate()));
    }
}
'@
Write-Utf8 "$root\product\repository\CategoryRepositoryIT.java" $content

# ProductRepositoryIT
$content = @'
package com.b2c.flash_sale_b2c_UTC2.product.repository;

import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class ProductRepositoryIT {

    @Autowired private ProductRepository productRepository;
    @PersistenceContext private EntityManager em;

    private Product persistProduct(String name, String status, Store store, Category cat) {
        Product p = Product.builder()
                .store(store).category(cat).name(name).status(status).build();
        em.persist(p);
        em.flush();
        return p;
    }

    private Store createStore() {
        User u = User.builder()
                .email("ps-" + System.nanoTime() + "@x.com").fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        Store s = Store.builder().user(u).storeName("S-" + System.nanoTime()).status("APPROVED").build();
        em.persist(s);
        em.flush();
        return s;
    }

    private Category createCategory(String name) {
        Category c = Category.builder().name(name).slug(name + "-slug").build();
        em.persist(c);
        em.flush();
        return c;
    }

    @Test
    @DisplayName("findPublicProducts no filter returns ACTIVE products of APPROVED store")
    void findPublicProducts_NoFilter() {
        Store s = createStore();
        Category c = createCategory("C1");
        persistProduct("P1", "ACTIVE", s, c);
        persistProduct("P2", "INACTIVE", s, c);

        Page<Product> page = productRepository.findPublicProducts(
                -1, "", new BigDecimal("-1"), new BigDecimal("-1"),
                PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("P1", page.getContent().get(0).getName());
    }

    @Test
    @DisplayName("findPublicProducts filters by keyword")
    void findPublicProducts_ByKeyword() {
        Store s = createStore();
        Category c = createCategory("C2");
        persistProduct("iPhone 15", "ACTIVE", s, c);
        persistProduct("Samsung Galaxy", "ACTIVE", s, c);

        Page<Product> page = productRepository.findPublicProducts(
                -1, "iPhone", new BigDecimal("-1"), new BigDecimal("-1"),
                PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("iPhone 15", page.getContent().get(0).getName());
    }

    @Test
    @DisplayName("findPublicProducts filters by categoryId")
    void findPublicProducts_ByCategory() {
        Store s = createStore();
        Category c1 = createCategory("C-A");
        Category c2 = createCategory("C-B");
        persistProduct("PA", "ACTIVE", s, c1);
        persistProduct("PB", "ACTIVE", s, c2);

        Page<Product> page = productRepository.findPublicProducts(
                c1.getId().intValue(), "", new BigDecimal("-1"), new BigDecimal("-1"),
                PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("PA", page.getContent().get(0).getName());
    }

    @Test
    @DisplayName("findPublicProducts skips products of non-APPROVED store")
    void findPublicProducts_SkipsUnapprovedStore() {
        User u = User.builder().email("pu@x.com").fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        Store pendingStore = Store.builder().user(u).storeName("Pending").status("PENDING").build();
        em.persist(pendingStore);
        em.flush();
        Category c = createCategory("C-Pend");
        persistProduct("Hidden", "ACTIVE", pendingStore, c);

        Page<Product> page = productRepository.findPublicProducts(
                -1, "", new BigDecimal("-1"), new BigDecimal("-1"),
                PageRequest.of(0, 10));

        assertEquals(0, page.getTotalElements());
    }

    @Test
    @DisplayName("findSellerProducts returns products of store by status")
    void findSellerProducts_ByStatus() {
        Store s = createStore();
        Category c = createCategory("C-S");
        persistProduct("Active", "ACTIVE", s, c);
        persistProduct("Inactive", "INACTIVE", s, c);

        Page<Product> activePage = productRepository.findSellerProducts(
                s.getId(), "ACTIVE", PageRequest.of(0, 10));
        Page<Product> inactivePage = productRepository.findSellerProducts(
                s.getId(), "INACTIVE", PageRequest.of(0, 10));

        assertEquals(1, activePage.getTotalElements());
        assertEquals("Active", activePage.getContent().get(0).getName());
        assertEquals(1, inactivePage.getTotalElements());
    }

    @Test
    @DisplayName("findByIdWithStoreAndCategory returns product with fetch join")
    void findByIdWithStoreAndCategory() {
        Store s = createStore();
        Category c = createCategory("C-Fetch");
        Product p = persistProduct("Fetched", "ACTIVE", s, c);

        Optional<Product> found = productRepository.findByIdWithStoreAndCategory(p.getId());

        assertTrue(found.isPresent());
        assertNotNull(found.get().getStore());
        assertNotNull(found.get().getCategory());
    }
}
'@
Write-Utf8 "$root\product\repository\ProductRepositoryIT.java" $content

# ProductVariantRepositoryIT
$content = @'
package com.b2c.flash_sale_b2c_UTC2.product.repository;

import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class ProductVariantRepositoryIT {

    @Autowired private ProductVariantRepository variantRepository;
    @PersistenceContext private EntityManager em;

    private User persistUser(String email) {
        User u = User.builder().email(email).fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        return u;
    }

    private Store persistStore(String name, User user) {
        Store s = Store.builder().user(user).storeName(name).status("APPROVED").build();
        em.persist(s);
        em.flush();
        return s;
    }

    private Category persistCategory(String name) {
        Category c = Category.builder().name(name).slug(name.toLowerCase()).build();
        em.persist(c);
        em.flush();
        return c;
    }

    private Product persistProduct(String name, Store store, Category cat) {
        Product p = Product.builder().name(name).store(store).category(cat).status("ACTIVE").build();
        em.persist(p);
        em.flush();
        return p;
    }

    private ProductVariant persistVariant(String sku, int stock) {
        User u = persistUser("s-" + sku + "@x.com");
        Store store = persistStore("Store " + sku, u);
        Category cat = persistCategory("Cat " + sku);
        Product product = persistProduct("P " + sku, store, cat);
        ProductVariant v = ProductVariant.builder()
                .product(product).sku(sku).variantName("V " + sku)
                .originalPrice(new BigDecimal("100000")).stockQuantity(stock)
                .status("ACTIVE").version(0L).build();
        em.persist(v);
        em.flush();
        return v;
    }

    @Test
    @DisplayName("findBySku returns variant when exists")
    void findBySku_Success() {
        ProductVariant v = persistVariant("SKU-FIND", 10);

        Optional<ProductVariant> found = variantRepository.findBySku("SKU-FIND");

        assertTrue(found.isPresent());
        assertEquals(v.getId(), found.get().getId());
    }

    @Test
    @DisplayName("existsBySku true when SKU exists")
    void existsBySku_True() {
        persistVariant("SKU-EXISTS", 5);
        assertTrue(variantRepository.existsBySku("SKU-EXISTS"));
    }

    @Test
    @DisplayName("existsBySkuAndIdNot false for same id")
    void existsBySkuAndIdNot_OwnId() {
        ProductVariant v = persistVariant("SKU-OWN", 5);
        assertFalse(variantRepository.existsBySkuAndIdNot("SKU-OWN", v.getId()));
    }

    @Test
    @DisplayName("existsBySkuAndIdNot true for other id")
    void existsBySkuAndIdNot_OtherId() {
        ProductVariant v = persistVariant("SKU-OTHER", 5);
        assertTrue(variantRepository.existsBySkuAndIdNot("SKU-OTHER", 999L));
    }

    @Test
    @DisplayName("deductStockQuantityConditionally decrements when enough")
    void deductStock_Success() {
        ProductVariant v = persistVariant("SKU-DEDUCT", 10);

        int updated = variantRepository.deductStockQuantityConditionally(v.getId(), 3);

        assertEquals(1, updated);
        em.clear();
        ProductVariant reloaded = variantRepository.findById(v.getId()).orElseThrow();
        assertEquals(7, reloaded.getStockQuantity());
    }

    @Test
    @DisplayName("deductStockQuantityConditionally no-op when not enough")
    void deductStock_Fail_WhenInsufficient() {
        ProductVariant v = persistVariant("SKU-NO", 2);

        int updated = variantRepository.deductStockQuantityConditionally(v.getId(), 10);

        assertEquals(0, updated);
        em.clear();
        ProductVariant reloaded = variantRepository.findById(v.getId()).orElseThrow();
        assertEquals(2, reloaded.getStockQuantity());
    }

    @Test
    @DisplayName("replenishStockQuantityConditionally increments stock")
    void replenishStock_Success() {
        ProductVariant v = persistVariant("SKU-REPLENISH", 5);

        int updated = variantRepository.replenishStockQuantityConditionally(v.getId(), 10);

        assertEquals(1, updated);
        em.clear();
        ProductVariant reloaded = variantRepository.findById(v.getId()).orElseThrow();
        assertEquals(15, reloaded.getStockQuantity());
    }

    @Test
    @DisplayName("findByProductIdInAndStatus returns ACTIVE variants by productIds")
    void findByProductIdInAndStatus_FiltersActive() {
        ProductVariant v1 = persistVariant("SKU-A", 5);
        List<ProductVariant> result = variantRepository.findByProductIdInAndStatus(
                List.of(v1.getProduct().getId()), "ACTIVE");

        assertEquals(1, result.size());
        assertEquals("SKU-A", result.get(0).getSku());
    }
}
'@
Write-Utf8 "$root\product\repository\ProductVariantRepositoryIT.java" $content

# ImageRepositoryIT
$content = @'
package com.b2c.flash_sale_b2c_UTC2.image.repository;

import com.b2c.flash_sale_b2c_UTC2.image.entity.Image;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class ImageRepositoryIT {

    @Autowired private ImageRepository imageRepository;
    @PersistenceContext private EntityManager em;

    private Image persistImage(ImageOwnerType ownerType, Long ownerId, boolean isPrimary, int order, String status) {
        Image i = Image.builder()
                .ownerType(ownerType).ownerId(ownerId)
                .url("http://x.com/" + ownerId + "/" + order + ".jpg")
                .cloudinaryPublicId("pub-" + ownerId + "-" + order)
                .isPrimary(isPrimary).displayOrder(order).status(status)
                .build();
        em.persist(i);
        em.flush();
        return i;
    }

    @Test
    @DisplayName("findByOwnerTypeAndOwnerIdAndStatusOrderByDisplayOrderAsc returns ACTIVE ordered")
    void findByOwner_OrdersByDisplayOrder() {
        persistImage(ImageOwnerType.PRODUCT, 1L, false, 2, "ACTIVE");
        persistImage(ImageOwnerType.PRODUCT, 1L, true, 0, "ACTIVE");
        persistImage(ImageOwnerType.PRODUCT, 1L, false, 1, "ACTIVE");

        List<Image> result = imageRepository.findByOwnerTypeAndOwnerIdAndStatusOrderByDisplayOrderAsc(
                ImageOwnerType.PRODUCT, 1L, "ACTIVE");

        assertEquals(3, result.size());
        assertEquals(0, result.get(0).getDisplayOrder());
        assertEquals(1, result.get(1).getDisplayOrder());
        assertEquals(2, result.get(2).getDisplayOrder());
    }

    @Test
    @DisplayName("findFirstByOwnerTypeAndOwnerIdAndIsPrimaryTrueAndStatus returns primary")
    void findPrimary_Success() {
        persistImage(ImageOwnerType.PRODUCT, 1L, true, 0, "ACTIVE");
        persistImage(ImageOwnerType.PRODUCT, 1L, false, 1, "ACTIVE");

        Optional<Image> primary = imageRepository.findFirstByOwnerTypeAndOwnerIdAndIsPrimaryTrueAndStatus(
                ImageOwnerType.PRODUCT, 1L, "ACTIVE");

        assertTrue(primary.isPresent());
        assertTrue(primary.get().getIsPrimary());
    }

    @Test
    @DisplayName("findPrimaryImagesByOwnerIds batch load")
    void findPrimaryImagesByOwnerIds_BatchLoad() {
        persistImage(ImageOwnerType.PRODUCT, 10L, true, 0, "ACTIVE");
        persistImage(ImageOwnerType.PRODUCT, 11L, true, 0, "ACTIVE");
        persistImage(ImageOwnerType.PRODUCT, 12L, false, 0, "ACTIVE");

        List<Image> result = imageRepository.findPrimaryImagesByOwnerIds(
                ImageOwnerType.PRODUCT, List.of(10L, 11L, 12L));

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("clearOtherPrimary resets other primary")
    void clearOtherPrimary_ResetsOthers() {
        Image keep = persistImage(ImageOwnerType.PRODUCT, 1L, true, 0, "ACTIVE");
        Image other1 = persistImage(ImageOwnerType.PRODUCT, 1L, true, 1, "ACTIVE");
        Image other2 = persistImage(ImageOwnerType.PRODUCT, 1L, true, 2, "ACTIVE");

        int updated = imageRepository.clearOtherPrimary(
                ImageOwnerType.PRODUCT, 1L, keep.getId(), Instant.now());

        assertEquals(2, updated);
        em.clear();
        assertTrue(imageRepository.findById(keep.getId()).orElseThrow().getIsPrimary());
        assertFalse(imageRepository.findById(other1.getId()).orElseThrow().getIsPrimary());
        assertFalse(imageRepository.findById(other2.getId()).orElseThrow().getIsPrimary());
    }

    @Test
    @DisplayName("markCloudinaryDeleted marks deleted")
    void markCloudinaryDeleted_Success() {
        Image img = persistImage(ImageOwnerType.PRODUCT, 1L, true, 0, "ACTIVE");

        int updated = imageRepository.markCloudinaryDeleted(img.getId(), Instant.now());

        assertEquals(1, updated);
        em.clear();
        assertTrue(imageRepository.findById(img.getId()).orElseThrow().getCloudinaryDeleted());
    }

    @Test
    @DisplayName("findInactiveNotCleanedUp returns INACTIVE not deleted")
    void findInactiveNotCleanedUp_Returns() {
        persistImage(ImageOwnerType.PRODUCT, 1L, true, 0, "INACTIVE");
        persistImage(ImageOwnerType.PRODUCT, 2L, true, 0, "ACTIVE");

        List<Image> result = imageRepository.findInactiveNotCleanedUp();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getOwnerId());
    }

    @Test
    @DisplayName("findReadyForHardDelete returns INACTIVE+deleted past threshold")
    void findReadyForHardDelete_Returns() {
        Image old = persistImage(ImageOwnerType.PRODUCT, 1L, true, 0, "INACTIVE");
        em.clear();
        Image managed = imageRepository.findById(old.getId()).orElseThrow();
        managed.setCloudinaryDeleted(true);
        managed.setUpdatedAt(Instant.now().minus(31, ChronoUnit.DAYS));
        em.flush();

        List<Image> result = imageRepository.findReadyForHardDelete(Instant.now().minus(30, ChronoUnit.DAYS));

        assertTrue(result.stream().anyMatch(i -> i.getId().equals(old.getId())));
    }
}
'@
Write-Utf8 "$root\image\repository\ImageRepositoryIT.java" $content

# VoucherRepositoryIT
$content = @'
package com.b2c.flash_sale_b2c_UTC2.voucher.repository;

import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.Voucher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class VoucherRepositoryIT {

    @Autowired private VoucherRepository voucherRepository;
    @PersistenceContext private EntityManager em;

    private Voucher persistVoucher(String code, Store store, String status) {
        Instant now = Instant.now();
        Voucher v = Voucher.builder()
                .code(code).store(store)
                .discountType("FIXED").discountValue(new BigDecimal("10000"))
                .totalQuantity(100).usedQuantity(0).userUsageLimit(1)
                .startTime(now).endTime(now.plus(30, ChronoUnit.DAYS))
                .status(status).createdAt(now).build();
        em.persist(v);
        em.flush();
        return v;
    }

    private Store createStore() {
        User u = User.builder().email("vs-" + System.nanoTime() + "@x.com").fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        Store s = Store.builder().user(u).storeName("S-" + System.nanoTime()).status("APPROVED").build();
        em.persist(s);
        em.flush();
        return s;
    }

    @Test
    @DisplayName("findByCode returns matching voucher")
    void findByCode_Success() {
        Voucher v = persistVoucher("V-FIND", null, "ACTIVE");

        Optional<Voucher> found = voucherRepository.findByCode("V-FIND");

        assertTrue(found.isPresent());
        assertEquals(v.getId(), found.get().getId());
    }

    @Test
    @DisplayName("existsByCode true when exists")
    void existsByCode_True() {
        persistVoucher("V-EXISTS", null, "ACTIVE");
        assertTrue(voucherRepository.existsByCode("V-EXISTS"));
    }

    @Test
    @DisplayName("existsByCode false when not exists")
    void existsByCode_False() {
        assertFalse(voucherRepository.existsByCode("V-NOT-EXISTS"));
    }

    @Test
    @DisplayName("findByStoreId returns store vouchers")
    void findByStoreId_ReturnsStoreVouchers() {
        Store s = createStore();
        persistVoucher("V-S1", s, "ACTIVE");

        List<Voucher> result = voucherRepository.findByStoreId(s.getId());

        assertEquals(1, result.size());
        assertEquals("V-S1", result.get(0).getCode());
    }

    @Test
    @DisplayName("findByStoreIdIsNull returns platform vouchers")
    void findByStoreIdIsNull_ReturnsPlatform() {
        persistVoucher("V-PLAT-1", null, "ACTIVE");
        Store s = createStore();
        persistVoucher("V-S-1", s, "ACTIVE");

        List<Voucher> result = voucherRepository.findByStoreIdIsNull();

        assertEquals(1, result.size());
        assertEquals("V-PLAT-1", result.get(0).getCode());
        assertNull(result.get(0).getStore());
    }
}
'@
Write-Utf8 "$root\voucher\repository\VoucherRepositoryIT.java" $content

# OrderRepositoryIT
$content = @'
package com.b2c.flash_sale_b2c_UTC2.order.repository;

import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class OrderRepositoryIT {

    @Autowired private OrderRepository orderRepository;
    @PersistenceContext private EntityManager em;

    private Order persistOrder(String code, User buyer, Store store, String status) {
        Order o = Order.builder()
                .orderCode(code).buyer(buyer).store(store)
                .recipientName("Buyer").recipientPhone("0123456789")
                .shippingAddressText("Addr")
                .subtotalAmount(new BigDecimal("100000"))
                .totalAmount(new BigDecimal("100000"))
                .commissionRate(new BigDecimal("0.05"))
                .platformFee(new BigDecimal("5000"))
                .sellerAmount(new BigDecimal("95000"))
                .status(status)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        em.persist(o);
        em.flush();
        return o;
    }

    private User persistUser(String email) {
        User u = User.builder().email(email).fullName("B").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        return u;
    }

    private Store persistStore(String name, User user) {
        Store s = Store.builder().user(user).storeName(name).status("APPROVED").build();
        em.persist(s);
        em.flush();
        return s;
    }

    @Test
    @DisplayName("findByOrderCode returns matching order")
    void findByOrderCode_Success() {
        User buyer = persistUser("o@x.com");
        Store store = persistStore("S-Find-Order", buyer);
        Order o = persistOrder("ORD-1", buyer, store, "PENDING_PAYMENT");

        Optional<Order> found = orderRepository.findByOrderCode("ORD-1");

        assertTrue(found.isPresent());
        assertEquals(o.getId(), found.get().getId());
    }

    @Test
    @DisplayName("findByBuyerId returns page of buyer orders")
    void findByBuyerId_ReturnsPage() {
        User buyer = persistUser("ob@x.com");
        Store store = persistStore("S-Buyer-Order", buyer);
        persistOrder("O-B1", buyer, store, "PENDING_PAYMENT");
        persistOrder("O-B2", buyer, store, "COMPLETED");

        Page<Order> page = orderRepository.findByBuyerId(buyer.getId(),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertEquals(2, page.getTotalElements());
    }

    @Test
    @DisplayName("findByStoreId returns page of store orders")
    void findByStoreId_ReturnsPage() {
        User buyer = persistUser("os@x.com");
        Store store = persistStore("S-Store-Order", buyer);
        persistOrder("O-S1", buyer, store, "PENDING_PAYMENT");

        Page<Order> page = orderRepository.findByStoreId(store.getId(), PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
    }

    @Test
    @DisplayName("Pagination paginates correctly")
    void pagination_Works() {
        User buyer = persistUser("p@x.com");
        Store store = persistStore("S-Page-Order", buyer);
        for (int i = 0; i < 25; i++) {
            persistOrder("O-P-" + i, buyer, store, "PENDING_PAYMENT");
        }

        Page<Order> page1 = orderRepository.findByBuyerId(buyer.getId(), PageRequest.of(0, 10));
        Page<Order> page2 = orderRepository.findByBuyerId(buyer.getId(), PageRequest.of(1, 10));
        Page<Order> page3 = orderRepository.findByBuyerId(buyer.getId(), PageRequest.of(2, 10));

        assertEquals(25, page1.getTotalElements());
        assertEquals(10, page1.getContent().size());
        assertEquals(10, page2.getContent().size());
        assertEquals(5, page3.getContent().size());
    }
}
'@
Write-Utf8 "$root\order\repository\OrderRepositoryIT.java" $content

# FlashSaleSlotRepositoryIT
$content = @'
package com.b2c.flash_sale_b2c_UTC2.flashsale.repository;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class FlashSaleSlotRepositoryIT {

    @Autowired private FlashSaleSlotRepository slotRepository;
    @PersistenceContext private EntityManager em;

    private FlashSaleSlot persistSlot(String title, Instant start, Instant end, String status) {
        FlashSaleSlot s = FlashSaleSlot.builder()
                .title(title).startTime(start).endTime(end)
                .reservationTtlSeconds(300).status(status)
                .createdAt(Instant.now()).build();
        em.persist(s);
        em.flush();
        return s;
    }

    @Test
    @DisplayName("findByStatusOrderByStartTimeAsc sorts by start_time ASC")
    void findByStatusOrderByStartTimeAsc_Sorts() {
        Instant now = Instant.now();
        persistSlot("S2", now.plus(2, ChronoUnit.HOURS), now.plus(3, ChronoUnit.HOURS), "UPCOMING");
        persistSlot("S1", now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), "UPCOMING");

        List<FlashSaleSlot> result = slotRepository.findByStatusOrderByStartTimeAsc("UPCOMING");

        assertEquals(2, result.size());
        assertEquals("S1", result.get(0).getTitle());
        assertEquals("S2", result.get(1).getTitle());
    }

    @Test
    @DisplayName("findByStatusInOrderByStartTimeAsc supports multiple statuses")
    void findByStatusInOrderByStartTimeAsc_MultipleStatuses() {
        Instant now = Instant.now();
        persistSlot("ACTIVE-1", now, now.plus(1, ChronoUnit.HOURS), "ACTIVE");
        persistSlot("UPCOMING-1", now.plus(2, ChronoUnit.HOURS), now.plus(3, ChronoUnit.HOURS), "UPCOMING");

        List<FlashSaleSlot> result = slotRepository.findByStatusInOrderByStartTimeAsc(List.of("ACTIVE", "UPCOMING"));

        assertEquals(2, result.size());
        assertEquals("ACTIVE-1", result.get(0).getTitle());
    }

    @Test
    @DisplayName("existsOverlappingSlot true when time range overlaps")
    void existsOverlappingSlot_True() {
        Instant now = Instant.now();
        persistSlot("EXIST", now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), "UPCOMING");

        boolean overlap = slotRepository.existsOverlappingSlot(
                null,
                now.plus(1, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES),
                now.plus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES));

        assertTrue(overlap);
    }

    @Test
    @DisplayName("existsOverlappingSlot false when no overlap")
    void existsOverlappingSlot_False() {
        Instant now = Instant.now();
        persistSlot("EXIST", now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), "UPCOMING");

        boolean overlap = slotRepository.existsOverlappingSlot(
                null,
                now.plus(3, ChronoUnit.HOURS),
                now.plus(4, ChronoUnit.HOURS));

        assertFalse(overlap);
    }

    @Test
    @DisplayName("existsOverlappingSlot ignores ENDED slots")
    void existsOverlappingSlot_IgnoresEnded() {
        Instant now = Instant.now();
        persistSlot("ENDED", now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), "ENDED");

        boolean overlap = slotRepository.existsOverlappingSlot(
                null,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(2, ChronoUnit.HOURS));

        assertFalse(overlap);
    }

    @Test
    @DisplayName("existsOverlappingSlot excludeId for update")
    void existsOverlappingSlot_ExcludeId() {
        Instant now = Instant.now();
        FlashSaleSlot s = persistSlot("SELF", now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), "UPCOMING");

        boolean overlap = slotRepository.existsOverlappingSlot(
                s.getId(),
                now.plus(1, ChronoUnit.HOURS),
                now.plus(2, ChronoUnit.HOURS));

        assertFalse(overlap);
    }

    @Test
    @DisplayName("findSlotsToActivate returns UPCOMING slots past start_time")
    void findSlotsToActivate_ReturnsDueSlots() {
        Instant now = Instant.now();
        persistSlot("ACTIVATABLE", now.minus(5, ChronoUnit.MINUTES), now.plus(1, ChronoUnit.HOURS), "UPCOMING");
        persistSlot("FUTURE", now.plus(2, ChronoUnit.HOURS), now.plus(3, ChronoUnit.HOURS), "UPCOMING");

        List<FlashSaleSlot> result = slotRepository.findSlotsToActivate(Instant.now());

        assertEquals(1, result.size());
        assertEquals("ACTIVATABLE", result.get(0).getTitle());
    }

    @Test
    @DisplayName("findSlotsToEnd returns ACTIVE slots past end_time")
    void findSlotsToEnd_ReturnsExpiredActiveSlots() {
        Instant now = Instant.now();
        persistSlot("TO_END", now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS), "ACTIVE");
        persistSlot("STILL_ACTIVE", now.minus(30, ChronoUnit.MINUTES), now.plus(1, ChronoUnit.HOURS), "ACTIVE");

        List<FlashSaleSlot> result = slotRepository.findSlotsToEnd(Instant.now());

        assertEquals(1, result.size());
        assertEquals("TO_END", result.get(0).getTitle());
    }
}
'@
Write-Utf8 "$root\flashsale\repository\FlashSaleSlotRepositoryIT.java" $content

# FlashSaleItemRepositoryIT
$content = @'
package com.b2c.flash_sale_b2c_UTC2.flashsale.repository;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class FlashSaleItemRepositoryIT {

    @Autowired private FlashSaleItemRepository itemRepository;
    @PersistenceContext private EntityManager em;

    private User persistUser(String email) {
        User u = User.builder().email(email).fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        return u;
    }

    private Store persistStore(String name, User user) {
        Store s = Store.builder().user(user).storeName(name).status("APPROVED").build();
        em.persist(s);
        em.flush();
        return s;
    }

    private Category persistCategory(String name) {
        Category c = Category.builder().name(name).slug("c-" + System.nanoTime()).build();
        em.persist(c);
        em.flush();
        return c;
    }

    private Product persistProduct(String name, Store store, Category cat) {
        Product p = Product.builder().store(store).category(cat).name(name).build();
        em.persist(p);
        em.flush();
        return p;
    }

    private ProductVariant persistVariant(String sku, int stock) {
        User u = persistUser("u-" + System.nanoTime() + "@x.com");
        Store store = persistStore("S-" + System.nanoTime(), u);
        Category cat = persistCategory("C-" + System.nanoTime());
        Product product = persistProduct("P-" + System.nanoTime(), store, cat);
        ProductVariant v = ProductVariant.builder()
                .product(product).sku(sku).variantName("V")
                .originalPrice(new BigDecimal("100000")).stockQuantity(stock)
                .status("ACTIVE").version(0L).build();
        em.persist(v);
        em.flush();
        return v;
    }

    private FlashSaleSlot persistSlot(String title, String status) {
        FlashSaleSlot s = FlashSaleSlot.builder()
                .title(title).startTime(Instant.now()).endTime(Instant.now().plus(2, ChronoUnit.HOURS))
                .reservationTtlSeconds(300).status(status).createdAt(Instant.now()).build();
        em.persist(s);
        em.flush();
        return s;
    }

    private FlashSaleItem persistItem(String slotStatus, String itemStatus, int allocated, int available) {
        ProductVariant variant = persistVariant("SKU-" + System.nanoTime(), 100);
        FlashSaleSlot slot = persistSlot("Slot-" + System.nanoTime(), slotStatus);

        FlashSaleItem item = FlashSaleItem.builder()
                .slot(slot).variant(variant)
                .flashSalePrice(new BigDecimal("50000"))
                .allocatedStock(allocated).availableStock(available)
                .userPurchaseLimit(2).status(itemStatus).createdAt(Instant.now()).build();
        em.persist(item);
        em.flush();
        return item;
    }

    @Test
    @DisplayName("findBySlotIdAndVariantId returns matching item")
    void findBySlotIdAndVariantId_Success() {
        FlashSaleItem item = persistItem("UPCOMING", "APPROVED", 10, 10);

        Optional<FlashSaleItem> found = itemRepository.findBySlotIdAndVariantId(
                item.getSlot().getId(), item.getVariant().getId());

        assertTrue(found.isPresent());
        assertEquals(item.getId(), found.get().getId());
    }

    @Test
    @DisplayName("findBySlotId returns all items in slot")
    void findBySlotId_ReturnsAll() {
        FlashSaleItem i1 = persistItem("UPCOMING", "APPROVED", 10, 10);

        FlashSaleSlot slot = i1.getSlot();
        ProductVariant variant = persistVariant("SKU2-" + System.nanoTime(), 50);
        FlashSaleItem item2 = FlashSaleItem.builder()
                .slot(slot).variant(variant)
                .flashSalePrice(new BigDecimal("50000"))
                .allocatedStock(20).availableStock(20)
                .userPurchaseLimit(1).status("PENDING_APPROVAL").createdAt(Instant.now()).build();
        em.persist(item2);
        em.flush();

        List<FlashSaleItem> result = itemRepository.findBySlotId(slot.getId());

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("findBySlotIdAndStatus filters by status")
    void findBySlotIdAndStatus_Filters() {
        FlashSaleItem i1 = persistItem("UPCOMING", "APPROVED", 10, 10);
        FlashSaleSlot slot = i1.getSlot();
        ProductVariant variant = persistVariant("SKUP-" + System.nanoTime(), 50);
        FlashSaleItem item2 = FlashSaleItem.builder()
                .slot(slot).variant(variant)
                .flashSalePrice(new BigDecimal("50000"))
                .allocatedStock(5).availableStock(5)
                .userPurchaseLimit(1).status("PENDING_APPROVAL").createdAt(Instant.now()).build();
        em.persist(item2);
        em.flush();

        List<FlashSaleItem> approved = itemRepository.findBySlotIdAndStatus(slot.getId(), "APPROVED");

        assertEquals(1, approved.size());
        assertEquals("APPROVED", approved.get(0).getStatus());
    }

    @Test
    @DisplayName("existsBySlotIdAndVariantId returns true when exists")
    void existsBySlotIdAndVariantId_True() {
        FlashSaleItem item = persistItem("UPCOMING", "APPROVED", 10, 10);
        assertTrue(itemRepository.existsBySlotIdAndVariantId(item.getSlot().getId(), item.getVariant().getId()));
    }

    @Test
    @DisplayName("deductAvailableStockConditionally decrements when enough")
    void deduct_Success() {
        FlashSaleItem item = persistItem("ACTIVE", "APPROVED", 10, 10);

        int updated = itemRepository.deductAvailableStockConditionally(item.getId(), 3);

        assertEquals(1, updated);
        em.clear();
        FlashSaleItem reloaded = itemRepository.findById(item.getId()).orElseThrow();
        assertEquals(7, reloaded.getAvailableStock());
    }

    @Test
    @DisplayName("deductAvailableStockConditionally fails when not enough")
    void deduct_Fail_WhenInsufficient() {
        FlashSaleItem item = persistItem("ACTIVE", "APPROVED", 10, 2);

        int updated = itemRepository.deductAvailableStockConditionally(item.getId(), 5);

        assertEquals(0, updated);
    }

    @Test
    @DisplayName("replenishAvailableStockConditionally increments stock")
    void replenish_Success() {
        FlashSaleItem item = persistItem("ACTIVE", "APPROVED", 10, 5);

        int updated = itemRepository.replenishAvailableStockConditionally(item.getId(), 3);

        assertEquals(1, updated);
        em.clear();
        FlashSaleItem reloaded = itemRepository.findById(item.getId()).orElseThrow();
        assertEquals(8, reloaded.getAvailableStock());
    }

    @Test
    @DisplayName("replenishAvailableStockConditionally rejects when exceeds allocatedStock")
    void replenish_Fail_WhenExceedsAllocated() {
        FlashSaleItem item = persistItem("ACTIVE", "APPROVED", 10, 9);

        int updated = itemRepository.replenishAvailableStockConditionally(item.getId(), 5);

        assertEquals(0, updated);
    }

    @Test
    @DisplayName("isVariantInActiveFlashSale true when variant in ACTIVE slot")
    void isVariantInActiveFlashSale_True() {
        FlashSaleItem item = persistItem("ACTIVE", "APPROVED", 10, 10);
        assertTrue(itemRepository.isVariantInActiveFlashSale(item.getVariant().getId()));
    }

    @Test
    @DisplayName("isVariantInActiveFlashSale false when slot UPCOMING")
    void isVariantInActiveFlashSale_False_WhenSlotNotActive() {
        FlashSaleItem item = persistItem("UPCOMING", "APPROVED", 10, 10);
        assertFalse(itemRepository.isVariantInActiveFlashSale(item.getVariant().getId()));
    }
}
'@
Write-Utf8 "$root\flashsale\repository\FlashSaleItemRepositoryIT.java" $content

# AddressRepositoryIT
$content = @'
package com.b2c.flash_sale_b2c_UTC2.user.repository;

import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Address;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class AddressRepositoryIT {

    @Autowired private AddressRepository addressRepository;
    @PersistenceContext private EntityManager em;

    private User persistUser(String email) {
        User u = User.builder().email(email).fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        return u;
    }

    private Address persistAddress(User user, String contactName, boolean isDefault) {
        Address a = Address.builder()
                .user(user)
                .contactName(contactName)
                .phone("0900000000")
                .province("P").district("D").ward("W")
                .detailAddress("Detail")
                .isDefault(isDefault)
                .build();
        em.persist(a);
        em.flush();
        return a;
    }

    @Test
    @DisplayName("findByUserId returns all addresses of user")
    void findByUserId_Success() {
        User u = persistUser("addr@x.com");
        Address a1 = persistAddress(u, "Addr-1", false);
        Address a2 = persistAddress(u, "Addr-2", true);

        List<Address> result = addressRepository.findByUserId(u.getId());

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(a -> a.getId().equals(a1.getId())));
        assertTrue(result.stream().anyMatch(a -> a.getId().equals(a2.getId())));
    }

    @Test
    @DisplayName("findByUserIdAndIsDefaultTrue returns single default")
    void findByUserIdAndIsDefaultTrue_Success() {
        User u = persistUser("addr2@x.com");
        Address a1 = persistAddress(u, "Addr-1", false);
        Address a2 = persistAddress(u, "Addr-2", true);

        Optional<Address> found = addressRepository.findByUserIdAndIsDefaultTrue(u.getId());

        assertTrue(found.isPresent());
        assertEquals(a2.getId(), found.get().getId());
    }

    @Test
    @DisplayName("findByStoreId returns store warehouse addresses")
    void findByStoreId_Success() {
        User u = persistUser("sa-owner@x.com");
        em.persist(u);
        em.flush();
        Store s = Store.builder().user(u).storeName("S-Addr").status("APPROVED").build();
        em.persist(s);
        em.flush();
        Address a = Address.builder()
                .store(s)
                .contactName("Warehouse")
                .phone("0900000001")
                .province("P").district("D").ward("W")
                .detailAddress("Detail")
                .isDefault(false)
                .build();
        em.persist(a);
        em.flush();

        List<Address> result = addressRepository.findByStoreId(s.getId());

        assertEquals(1, result.size());
        assertEquals(a.getId(), result.get(0).getId());
    }

    @Test
    @DisplayName("findByUserId empty when no addresses")
    void findByUserId_Empty() {
        List<Address> result = addressRepository.findByUserId(99999L);
        assertTrue(result.isEmpty());
    }
}
'@
Write-Utf8 "$root\user\repository\AddressRepositoryIT.java" $content

# UserRoleRepositoryIT
$content = @'
package com.b2c.flash_sale_b2c_UTC2.user.repository;

import com.b2c.flash_sale_b2c_UTC2.user.entity.Role;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class UserRoleRepositoryIT {

    @Autowired private UserRoleRepository userRoleRepository;
    @PersistenceContext private EntityManager em;

    private User persistUser(String email) {
        User u = User.builder().email(email).fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        return u;
    }

    private Role persistRole(String name) {
        Role r = Role.builder().name(name).description("Test role").createdAt(Instant.now()).build();
        em.persist(r);
        em.flush();
        return r;
    }

    private UserRole persistUserRole(User u, Role r) {
        UserRole ur = UserRole.builder().user(u).role(r).assignedAt(Instant.now()).build();
        em.persist(ur);
        em.flush();
        return ur;
    }

    @Test
    @DisplayName("findByUserId returns all roles of user")
    void findByUserId_Success() {
        User u = persistUser("ur@x.com");
        Role buyer = persistRole("BUYER");
        Role seller = persistRole("SELLER");
        persistUserRole(u, buyer);
        persistUserRole(u, seller);

        List<UserRole> result = userRoleRepository.findByUserId(u.getId());

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("existsByUserIdAndRoleId true when mapping exists")
    void existsByUserIdAndRoleId_True() {
        User u = persistUser("ur2@x.com");
        Role r = persistRole("BUYER");
        persistUserRole(u, r);

        assertTrue(userRoleRepository.existsByUserIdAndRoleId(u.getId(), r.getId()));
    }

    @Test
    @DisplayName("existsByUserIdAndRoleId false when mapping missing")
    void existsByUserIdAndRoleId_False() {
        User u = persistUser("ur3@x.com");
        Role r = persistRole("ADMIN");

        assertFalse(userRoleRepository.existsByUserIdAndRoleId(u.getId(), r.getId()));
    }

    @Test
    @DisplayName("findByUserId empty when no roles")
    void findByUserId_Empty() {
        assertTrue(userRoleRepository.findByUserId(99999L).isEmpty());
    }
}
'@
Write-Utf8 "$root\user\repository\UserRoleRepositoryIT.java" $content

# WalletRepositoryIT
$content = @'
package com.b2c.flash_sale_b2c_UTC2.wallet.repository;

import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.Wallet;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class WalletRepositoryIT {

    @Autowired private WalletRepository walletRepository;
    @PersistenceContext private EntityManager em;

    private Store persistStore(String name) {
        User u = User.builder().email("ws-" + System.nanoTime() + "@x.com").fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        Store s = Store.builder().user(u).storeName(name).status("APPROVED").build();
        em.persist(s);
        em.flush();
        return s;
    }

    @Test
    @DisplayName("findByStoreId returns store wallet")
    void findByStoreId_Success() {
        Store s = persistStore("S-Wallet");
        Wallet w = Wallet.builder().store(s).balance(BigDecimal.ZERO).frozenBalance(BigDecimal.ZERO).build();
        em.persist(w);
        em.flush();

        Optional<Wallet> found = walletRepository.findByStoreId(s.getId());

        assertTrue(found.isPresent());
        assertEquals(w.getId(), found.get().getId());
    }

    @Test
    @DisplayName("Wallet default balance is zero")
    void defaultBalance_Zero() {
        Store s = persistStore("S-Wallet-Default");
        Wallet w = Wallet.builder().store(s).build();
        em.persist(w);
        em.flush();

        Wallet reloaded = walletRepository.findById(w.getId()).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(reloaded.getBalance()));
    }

    @Test
    @DisplayName("findByStoreId empty when store has no wallet")
    void findByStoreId_Empty() {
        Optional<Wallet> found = walletRepository.findByStoreId(99999L);
        assertTrue(found.isEmpty());
    }
}
'@
Write-Utf8 "$root\wallet\repository\WalletRepositoryIT.java" $content

# UserRepositoryIT (already has @Transactional, but rewrite to ensure UTF-8)
$content = @'
package com.b2c.flash_sale_b2c_UTC2.user.repository;

import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class UserRepositoryIT {

    @Autowired private UserRepository userRepository;
    @PersistenceContext private EntityManager em;

    private User persistUser(String email, String phone) {
        User u = User.builder()
                .email(email).phone(phone).fullName("U-" + email).status("ACTIVE").build();
        em.persist(u);
        em.flush();
        return u;
    }

    @Test
    @DisplayName("findByEmail returns user by email")
    void findByEmail_Success() {
        User u = persistUser("a@x.com", "0900000001");

        Optional<User> found = userRepository.findByEmail("a@x.com");

        assertTrue(found.isPresent());
    }

    @Test
    @DisplayName("findByPhone returns user by phone")
    void findByPhone_Success() {
        persistUser("b@x.com", "0900000002");

        Optional<User> found = userRepository.findByPhone("0900000002");

        assertTrue(found.isPresent());
    }

    @Test
    @DisplayName("existsByEmail true when email registered")
    void existsByEmail_True() {
        persistUser("c@x.com", "0900000003");
        assertTrue(userRepository.existsByEmail("c@x.com"));
    }

    @Test
    @DisplayName("existsByPhone true when phone registered")
    void existsByPhone_True() {
        persistUser("d@x.com", "0900000004");
        assertTrue(userRepository.existsByPhone("0900000004"));
    }

    @Test
    @DisplayName("existsByEmail false for unknown")
    void existsByEmail_False() {
        assertFalse(userRepository.existsByEmail("unknown@x.com"));
    }
}
'@
Write-Utf8 "$root\user\repository\UserRepositoryIT.java" $content

Write-Host "All 14 files written"

# Verify all are UTF-8
$files = Get-ChildItem -Path "$root" -Recurse -Filter "*IT.java"
foreach ($f in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
    $first = $bytes[0]
    if ($first -eq 0x70) {
        Write-Host "OK: $($f.Name)"
    } else {
        Write-Host "BAD: $($f.Name) - first byte: $first"
    }
}