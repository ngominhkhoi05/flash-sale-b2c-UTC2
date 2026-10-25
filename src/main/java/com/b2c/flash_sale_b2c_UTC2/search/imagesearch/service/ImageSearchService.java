package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.service;

import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.dto.ImageSearchResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service tìm kiếm sản phẩm bằng hình ảnh (Visual Search).
 */
public interface ImageSearchService {

    /**
     * Tìm kiếm bằng file ảnh upload.
     */
    ImageSearchResult searchByImage(MultipartFile file, Integer limit);

    /**
     * Tìm kiếm bằng link ảnh URL.
     */
    ImageSearchResult searchByImageUrl(String imageUrl, Integer limit);
}
