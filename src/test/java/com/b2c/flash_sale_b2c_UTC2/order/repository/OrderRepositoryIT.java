package com.b2c.flash_sale_b2c_UTC2.order.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class OrderRepositoryIT extends AbstractPostgresIT {

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

    @Test
    @DisplayName("countOrdersByBuyerIds returns [buyerId, count] rows for batch sidebar summary")
    void countOrdersByBuyerIds_ReturnsMap() {
        // Buyer 1: 2 orders
        User buyer1 = persistUser("cb1@x.com");
        Store store1 = persistStore("S-Buyer-Count-1", buyer1);
        persistOrder("C-B1-1", buyer1, store1, "PENDING_PAYMENT");
        persistOrder("C-B1-2", buyer1, store1, "COMPLETED");

        // Buyer 2: 1 order
        User buyer2 = persistUser("cb2@x.com");
        Store store2 = persistStore("S-Buyer-Count-2", buyer2);
        persistOrder("C-B2-1", buyer2, store2, "PENDING_PAYMENT");

        // Buyer 3: 0 orders (not included in result per query semantics)
        User buyer3 = persistUser("cb3@x.com");

        List<Object[]> rows = orderRepository.countOrdersByBuyerIds(
                List.of(buyer1.getId(), buyer2.getId(), buyer3.getId()));

        Map<Long, Long> counts = rows.stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        assertEquals(2L, counts.get(buyer1.getId()));
        assertEquals(1L, counts.get(buyer2.getId()));
        assertTrue(!counts.containsKey(buyer3.getId()), "buyer with 0 orders should not appear");
    }
}