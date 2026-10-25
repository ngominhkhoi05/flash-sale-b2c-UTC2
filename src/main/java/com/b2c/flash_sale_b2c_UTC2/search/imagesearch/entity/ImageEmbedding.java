package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Entity liên kết giữa ảnh sản phẩm và vector embedding trong PostgreSQL (pgvector).
 * Cột {@code embedding (vector)} được xử lý trực tiếp qua Native Query để đảm bảo
 * tương thích tuyệt đối với Hibernate validator (ddl-auto=validate).
 */
@Entity
@Table(name = "image_embeddings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageEmbedding {

    @Id
    @Column(name = "image_id")
    private Long imageId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
