package com.b2c.flash_sale_b2c_UTC2.image.enums;

/**
 * Loại đối tượng sở hữu ảnh (polymorphic).
 * Khớp với CHECK constraint trong V3__create_images_table.sql.
 */
public enum ImageOwnerType {
    PRODUCT,
    VARIANT,
    USER,
    STORE,
    REVIEW
}