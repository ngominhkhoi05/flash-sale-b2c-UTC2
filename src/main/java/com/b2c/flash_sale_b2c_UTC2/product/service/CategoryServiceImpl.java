package com.b2c.flash_sale_b2c_UTC2.product.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.product.dto.CategoryResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.CreateCategoryRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.UpdateCategoryRequest;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.exception.ProductErrorCode;
import com.b2c.flash_sale_b2c_UTC2.product.mapper.CategoryMapper;
import com.b2c.flash_sale_b2c_UTC2.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        List<Category> categories = categoryRepository.findAll();
        return categoryMapper.toResponseList(categories);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Integer id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        String trimmedName = request.getName().trim();
        String trimmedSlug = request.getSlug().trim().toLowerCase();

        if (categoryRepository.existsByName(trimmedName)) {
            throw new BusinessException(ProductErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
        }
        if (categoryRepository.existsBySlug(trimmedSlug)) {
            throw new BusinessException(ProductErrorCode.CATEGORY_SLUG_ALREADY_EXISTS);
        }

        BigDecimal rate = request.getCommissionRate() != null ? request.getCommissionRate() : new BigDecimal("0.0500");

        Category category = Category.builder()
                .name(trimmedName)
                .slug(trimmedSlug)
                .commissionRate(rate)
                .description(request.getDescription())
                .build();

        category = categoryRepository.save(category);
        log.info("Admin đã tạo mới ngành hàng: id={}, name={}", category.getId(), category.getName());
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Integer id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));

        String trimmedName = request.getName().trim();
        String trimmedSlug = request.getSlug().trim().toLowerCase();

        if (!category.getName().equalsIgnoreCase(trimmedName) && categoryRepository.existsByName(trimmedName)) {
            throw new BusinessException(ProductErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
        }
        if (!category.getSlug().equalsIgnoreCase(trimmedSlug) && categoryRepository.existsBySlug(trimmedSlug)) {
            throw new BusinessException(ProductErrorCode.CATEGORY_SLUG_ALREADY_EXISTS);
        }

        category.setName(trimmedName);
        category.setSlug(trimmedSlug);
        if (request.getCommissionRate() != null) {
            category.setCommissionRate(request.getCommissionRate());
        }
        category.setDescription(request.getDescription());

        category = categoryRepository.save(category);
        log.info("Admin đã cập nhật ngành hàng: id={}", category.getId());
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Integer id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));

        if (productRepository.existsByCategoryId(id)) {
            throw new BusinessException(ProductErrorCode.CATEGORY_HAS_PRODUCTS);
        }

        categoryRepository.delete(category);
        log.info("Admin đã xóa ngành hàng id={}", id);
    }
}
