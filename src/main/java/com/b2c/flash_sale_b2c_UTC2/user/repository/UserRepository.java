package com.b2c.flash_sale_b2c_UTC2.user.repository;

import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    long countByCreatedAtBetween(java.time.Instant start, java.time.Instant end);

    @org.springframework.data.jpa.repository.Query(value = """
        SELECT DISTINCT u FROM User u
        WHERE (:status IS NULL OR :status = '' OR u.status = :status)
        AND (
            :search IS NULL OR :search = '' OR
            LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR
            (u.phone IS NOT NULL AND u.phone LIKE CONCAT('%', :search, '%'))
        )
        AND (
            :role IS NULL OR :role = '' OR EXISTS (
                SELECT 1 FROM UserRole ur
                JOIN ur.role r
                WHERE ur.user.id = u.id
                AND (UPPER(r.name) = UPPER(:role) OR UPPER(r.name) = UPPER(CONCAT('ROLE_', :role)))
            )
        )
        ORDER BY u.createdAt DESC
    """,
    countQuery = """
        SELECT count(DISTINCT u) FROM User u
        WHERE (:status IS NULL OR :status = '' OR u.status = :status)
        AND (
            :search IS NULL OR :search = '' OR
            LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR
            (u.phone IS NOT NULL AND u.phone LIKE CONCAT('%', :search, '%'))
        )
        AND (
            :role IS NULL OR :role = '' OR EXISTS (
                SELECT 1 FROM UserRole ur
                JOIN ur.role r
                WHERE ur.user.id = u.id
                AND (UPPER(r.name) = UPPER(:role) OR UPPER(r.name) = UPPER(CONCAT('ROLE_', :role)))
            )
        )
    """)
    org.springframework.data.domain.Page<User> findAdminUsers(
            @org.springframework.data.repository.query.Param("role") String role,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("search") String search,
            org.springframework.data.domain.Pageable pageable
    );
}
