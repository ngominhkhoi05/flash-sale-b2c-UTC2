package com.b2c.flash_sale_b2c_UTC2.common.test;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;

public final class AuthTestHelper {

    private AuthTestHelper() {}

    public static CustomUserDetails userDetails(User user) {
        return new CustomUserDetails(user, List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
    }

    public static void setAuthentication(User user) {
        CustomUserDetails ud = userDetails(user);
        Collection<? extends GrantedAuthority> auths = ud.getAuthorities();
        Authentication auth = new UsernamePasswordAuthenticationToken(ud, "test", auths);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}