package com.b2c.flash_sale_b2c_UTC2.auth.service;

import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        jwtProperties.setExpirationMs(3600000L); // 1 giá»
        jwtProperties.setRefreshExpirationMs(604800000L); // 7 ngÃ y

        jwtService = new JwtService(jwtProperties);
    }

    @Test
    @DisplayName("Táº¡o Access Token thÃ nh cÃ´ng vÃ  trÃ­ch xuáº¥t Ä‘Ãºng username")
    void generateAccessToken_ShouldReturnValidToken() {
        UserDetails userDetails = new User("buyer@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_BUYER")));

        String token = jwtService.generateAccessToken(userDetails);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals("buyer@example.com", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    @DisplayName("Táº¡o Refresh Token thÃ nh cÃ´ng vÃ  há»£p lá»‡")
    void generateRefreshToken_ShouldReturnValidToken() {
        UserDetails userDetails = new User("seller@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SELLER")));

        String refreshToken = jwtService.generateRefreshToken(userDetails);

        assertNotNull(refreshToken);
        assertEquals("seller@example.com", jwtService.extractUsername(refreshToken));
        assertTrue(jwtService.isTokenValid(refreshToken, userDetails));
    }

    @Test
    @DisplayName("Token khÃ´ng há»£p lá»‡ khi username khÃ´ng khá»›p")
    void isTokenValid_ShouldReturnFalse_WhenUsernameDoesNotMatch() {
        UserDetails user1 = new User("user1@example.com", "password", Collections.emptyList());
        UserDetails user2 = new User("user2@example.com", "password", Collections.emptyList());

        String token = jwtService.generateAccessToken(user1);

        assertFalse(jwtService.isTokenValid(token, user2));
    }

    @Test
    @DisplayName("Token háº¿t háº¡n pháº£i tráº£ vá» true khi kiá»ƒm tra isTokenExpired")
    void isTokenExpired_ShouldReturnTrue_WhenTokenIsExpired() {
        jwtProperties.setExpirationMs(-1000L); // DÃ£ háº¿t háº¡n cÃ¡ch Ä‘Ã¢y 1s
        UserDetails user = new User("expired@example.com", "password", Collections.emptyList());

        String expiredToken = jwtService.generateAccessToken(user);

        assertTrue(jwtService.isTokenExpired(expiredToken));
    }

    @Test
    @DisplayName("Token pháº£i chá»©a Ä‘Ãºng claim type (ACCESS hoáº·c REFRESH) vÃ  jti")
    void tokenTypeAndJti_ShouldBePresentInClaims() {
        UserDetails user = new User("claim@example.com", "password", Collections.emptyList());

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        assertEquals("ACCESS", jwtService.extractTokenType(accessToken));
        assertEquals("REFRESH", jwtService.extractTokenType(refreshToken));
        assertNotNull(jwtService.extractJti(refreshToken));
        assertFalse(jwtService.extractJti(refreshToken).isBlank());
    }

    @Test
    @DisplayName("JwtProperties nÃ©m IllegalStateException khi secret rá»—ng hoáº·c < 32 kÃ½ tá»±")
    void jwtProperties_ShouldFailFast_WhenSecretInvalid() {
        JwtProperties emptyProps = new JwtProperties();
        assertThrows(IllegalStateException.class, emptyProps::validate);

        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret("short_secret_under_32_chars");
        assertThrows(IllegalStateException.class, shortProps::validate);

        JwtProperties validProps = new JwtProperties();
        validProps.setSecret("this_is_a_very_long_and_secure_secret_key_256bit");
        assertDoesNotThrow(validProps::validate);
    }
}
