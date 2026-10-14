package com.b2c.flash_sale_b2c_UTC2.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductVariantRequest {

    private Long id; // Null nếu là SKU mới thêm vào SPU

    @NotBlank(message = "Mã SKU không được để trống")
    @Size(max = 50, message = "Mã SKU tối đa 50 ký tự")
    private String sku;

    @NotBlank(message = "Tên biến thể không được để trống")
    @Size(max = 150, message = "Tên biến thể tối đa 150 ký tự")
    private String variantName;

    private Map<String, String> attributes;

    @NotNull(message = "Giá bán gốc không được để trống")
    @DecimalMin(value = "0.01", message = "Giá bán gốc phải lớn hơn 0")
    private BigDecimal originalPrice;

    @NotNull(message = "Số lượng tồn kho không được để trống")
    @Min(value = 0, message = "Số lượng tồn kho phải lớn hơn hoặc bằng 0")
    private Integer stockQuantity;

    @Pattern(regexp = "ACTIVE|INACTIVE|OUT_OF_STOCK", message = "Trạng thái biến thể chỉ có thể là ACTIVE, INACTIVE hoặc OUT_OF_STOCK")
    private String status;
}
