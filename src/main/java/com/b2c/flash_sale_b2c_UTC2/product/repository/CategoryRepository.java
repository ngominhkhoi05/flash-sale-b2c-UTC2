package com.b2c.flash_sale_b2c_UTC2.product.repository;

import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {
    Optional<Category> findBySlug(String slug);
    Optional<Category> findByName(String name);
    boolean existsBySlug(String slug);
    boolean existsByName(String name);
}
