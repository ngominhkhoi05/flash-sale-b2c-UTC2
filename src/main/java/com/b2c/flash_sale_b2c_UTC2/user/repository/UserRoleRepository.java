package com.b2c.flash_sale_b2c_UTC2.user.repository;

import com.b2c.flash_sale_b2c_UTC2.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUserId(Long userId);
    boolean existsByUserIdAndRoleId(Long userId, Short roleId);

    @org.springframework.data.jpa.repository.Query("SELECT ur FROM UserRole ur JOIN FETCH ur.role r WHERE ur.user.id IN :userIds")
    List<UserRole> findByUserIdsWithRole(@org.springframework.data.repository.query.Param("userIds") List<Long> userIds);
}
