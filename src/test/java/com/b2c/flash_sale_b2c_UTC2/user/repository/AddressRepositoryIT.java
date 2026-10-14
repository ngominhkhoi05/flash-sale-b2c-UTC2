package com.b2c.flash_sale_b2c_UTC2.user.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Address;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class AddressRepositoryIT extends AbstractPostgresIT {

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