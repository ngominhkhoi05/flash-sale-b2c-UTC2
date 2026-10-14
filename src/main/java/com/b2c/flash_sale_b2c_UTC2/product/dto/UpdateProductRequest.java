package com.b2c.flash_sale_b2c_UTC2.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    @NotNull(message = "Ngành hàng không được để trống")
    private Integer categoryId;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 255, message = "Tên sản phẩm tối đa 255 ký tự")
    private String name;

    private String description;

    private List<TierVariationConfigDto> tierVariationConfigs;

    @Pattern(regexp = "ACTIVE|INACTIVE|OUT_OF_STOCK", message = "Trạng thái chỉ có thể là ACTIVE, INACTIVE hoặc OUT_OF_STOCK")
    private String status;

    @Valid
    private List<UpdateProductVariantRequest> variants;
}
