package com.b2c.flash_sale_b2c_UTC2.user.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Role;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class UserRoleRepositoryIT extends AbstractPostgresIT {

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