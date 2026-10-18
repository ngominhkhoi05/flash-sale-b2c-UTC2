package com.b2c.flash_sale_b2c_UTC2.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerBankAccountRequest {

    @NotBlank(message = "Tên ngân hàng không được để trống")
    @Size(max = 100, message = "Tên ngân hàng không quá 100 ký tự")
    private String bankName;

    @NotBlank(message = "Mã ngân hàng không được để trống")
    @Size(max = 20, message = "Mã ngân hàng không quá 20 ký tự")
    private String bankCode;

    @NotBlank(message = "Số tài khoản không được để trống")
    @Pattern(regexp = "^[0-9]{6,30}$", message = "Số tài khoản chỉ bao gồm các chữ số (từ 6 đến 30 chữ số)")
    private String accountNumber;

    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    @Pattern(regexp = "^[A-Z ]+$", message = "Tên chủ tài khoản phải là chữ in hoa không dấu")
    @Size(max = 100, message = "Tên chủ tài khoản không quá 100 ký tự")
    private String accountHolder;

    @Size(max = 150, message = "Chi nhánh không quá 150 ký tự")
    private String branch;
}
