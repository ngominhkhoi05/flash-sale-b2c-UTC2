package com.b2c.flash_sale_b2c_UTC2.cart.repository;

import com.b2c.flash_sale_b2c_UTC2.cart.entity.Cart;
import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class CartRepositoryIT extends AbstractPostgresIT {

    @Autowired private CartRepository cartRepository;
    @PersistenceContext private EntityManager em;

    @Test
    @DisplayName("findByUserId returns cart of user")
    void findByUserId_Success() {
        User u = User.builder()
                .email("cart@x.com").fullName("U").status("ACTIVE").build();
        em.persist(u);
        em.flush();
        Cart c = Cart.builder()
                .user(u).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        em.persist(c);
        em.flush();

        Optional<Cart> found = cartRepository.findByUserId(u.getId());

        assertTrue(found.isPresent());
        assertEquals(c.getId(), found.get().getId());
    }

    @Test
    @DisplayName("findByUserId empty when user has no cart")
    void findByUserId_Empty() {
        Optional<Cart> found = cartRepository.findByUserId(99999L);
        assertTrue(found.isEmpty());
    }
}