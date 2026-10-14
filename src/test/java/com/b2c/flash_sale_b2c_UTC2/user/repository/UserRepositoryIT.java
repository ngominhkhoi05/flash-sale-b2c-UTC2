package com.b2c.flash_sale_b2c_UTC2.user.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class UserRepositoryIT extends AbstractPostgresIT {

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