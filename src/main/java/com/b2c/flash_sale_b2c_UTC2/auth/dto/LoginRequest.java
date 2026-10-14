package com.b2c.flash_sale_b2c_UTC2.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * Email hoặc số điện thoại. BE tự nhận diện:
     *   - chứa "@" → tra cứu theo email
     *   - còn lại   → tra cứu theo phone
     */
    @NotBlank(message = "Email hoặc số điện thoại không được để trống")
    @Size(max = 100, message = "Email hoặc số điện thoại không được vượt quá 100 ký tự")
    private String usernameOrEmail;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;
}
