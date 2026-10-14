package com.b2c.flash_sale_b2c_UTC2.product.exception;

import com.b2c.flash_sale_b2c_UTC2.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CAT_404_NOT_FOUND", "Không tìm thấy ngành hàng yêu cầu"),
    CATEGORY_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "CAT_409_NAME_EXISTS", "Tên ngành hàng đã tồn tại"),
    CATEGORY_SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "CAT_409_SLUG_EXISTS", "Đường dẫn slug của ngành hàng đã tồn tại"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROD_404_NOT_FOUND", "Không tìm thấy sản phẩm"),
    PRODUCT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PROD_403_ACCESS_DENIED", "Bạn không có quyền quản lý sản phẩm này"),
    STORE_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "PROD_403_STORE_NOT_ELIGIBLE", "Gian hàng chưa được phê duyệt hoặc không đủ điều kiện đăng bán"),
    SKU_ALREADY_EXISTS(HttpStatus.CONFLICT, "PROD_409_SKU_EXISTS", "Mã SKU này đã tồn tại trong hệ thống"),
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROD_404_VARIANT_NOT_FOUND", "Không tìm thấy biến thể phân loại sản phẩm"),
    AT_LEAST_ONE_VARIANT_REQUIRED(HttpStatus.BAD_REQUEST, "PROD_400_VARIANTS_REQUIRED", "Sản phẩm phải có ít nhất một biến thể phân loại (SKU)"),
    INVALID_TIER_VARIATION(HttpStatus.BAD_REQUEST, "PROD_400_INVALID_TIER_VARIATION", "Số lượng biến thể không khớp với cấu hình phân loại tier_variation_configs"),
    INVALID_PRODUCT_PRICE(HttpStatus.BAD_REQUEST, "PROD_400_INVALID_PRICE", "Giá bán của biến thể phải lớn hơn 0"),
    INVALID_STOCK_QUANTITY(HttpStatus.BAD_REQUEST, "PROD_400_INVALID_STOCK", "Số lượng tồn kho không được nhỏ hơn 0"),
    DUPLICATE_SKU_IN_REQUEST(HttpStatus.BAD_REQUEST, "PROD_400_DUPLICATE_SKU", "Mã SKU không được trùng lặp trong cùng sản phẩm"),
    VARIANT_IN_ACTIVE_FLASH_SALE(HttpStatus.CONFLICT, "PROD_409_VARIANT_IN_ACTIVE_FLASH_SALE", "Không thể chỉnh sửa giá hoặc tồn kho của biến thể đang tham gia Flash Sale đang hoạt động"),
    INVALID_SEARCH_PARAMS(HttpStatus.BAD_REQUEST, "PROD_400_SEARCH_PARAMS", "Tham số tìm kiếm không hợp lệ"),
    PAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "PROD_400_PAGE_SIZE", "Kích thước trang không được vượt quá 50"),
    INVALID_SORT_OPTION(HttpStatus.BAD_REQUEST, "PROD_400_SORT", "Tùy chọn sắp xếp không hợp lệ (hỗ trợ: relevance, price_asc, price_desc, best_selling, newest)");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
