package com.b2c.flash_sale_b2c_UTC2.auth.service;

import com.b2c.flash_sale_b2c_UTC2.auth.dto.AuthResponse;
import com.b2c.flash_sale_b2c_UTC2.auth.dto.GoogleConsentUrlResponse;
import com.b2c.flash_sale_b2c_UTC2.auth.dto.GoogleOAuth2LoginRequest;
import com.b2c.flash_sale_b2c_UTC2.auth.entity.AuthAccount;
import com.b2c.flash_sale_b2c_UTC2.auth.exception.AuthErrorCode;
import com.b2c.flash_sale_b2c_UTC2.auth.repository.AuthAccountRepository;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.config.GoogleOAuth2Properties;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Role;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.entity.UserRole;
import com.b2c.flash_sale_b2c_UTC2.user.mapper.UserMapper;
import com.b2c.flash_sale_b2c_UTC2.user.repository.RoleRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test cho GoogleOAuth2ServiceImpl.
 * Mock toan bo Google HTTP call va GoogleIdTokenVerifier (cach ly network).
 */
@ExtendWith(MockitoExtension.class)
class GoogleOAuth2ServiceImplTest {

    @Mock private RestTemplate restTemplate;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private AuthAccountRepository authAccountRepository;
    @Mock private UserDetailsService userDetailsService;
    @Mock private UserMapper userMapper;
    @Mock private JwtService jwtService;
    @Mock private JwtProperties jwtProperties;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private GoogleIdTokenVerifier idTokenVerifier;
    @Mock private GoogleIdToken googleIdToken;
    @Mock private GoogleIdToken.Payload payload;
    @Mock private HttpTransport httpTransport;

    private GoogleOAuth2Properties props;
    private GoogleOAuth2ServiceImpl service;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        props = new GoogleOAuth2Properties();
        props.setClientId("test-client-id.apps.googleusercontent.com");
        props.setClientSecret("test-client-secret");
        props.setRedirectUri("http://localhost:3000/oauth2/redirect");
        props.setTokenEndpoint("https://oauth2.googleapis.com/token");

        // Su dung constructor de truyen mock idTokenVerifier (thay vi tao that trong constructor)
        service = new GoogleOAuth2ServiceImpl(
                props,
                restTemplate,
                objectMapper,
                userRepository,
                roleRepository,
                userRoleRepository,
                authAccountRepository,
                userDetailsService,
                userMapper,
                jwtService,
                jwtProperties,
                stringRedisTemplate,
                httpTransport,
                idTokenVerifier
        );

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
    }

    // ========== exchangeCodeForIdToken ==========

    @Test
    @DisplayName("Trao doi code thanh cong lay id_token tu Google")
    void loginWithGoogle_Success_NewUser() throws Exception {
        GoogleOAuth2LoginRequest request = new GoogleOAuth2LoginRequest("valid_code", "http://localhost:3000/oauth2/redirect");
        String tokenResponseJson = "{\"access_token\":\"ya29.x\",\"id_token\":\"mock-id-token\",\"expires_in\":3600,\"token_type\":\"Bearer\"}";

        when(restTemplate.postForEntity(eq(props.getTokenEndpoint()), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(tokenResponseJson));
        when(idTokenVerifier.verify("mock-id-token")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getSubject()).thenReturn("google-sub-123");
        when(payload.getEmail()).thenReturn("newuser@gmail.com");
        when(payload.getEmailVerified()).thenReturn(true);
        when(payload.get("name")).thenReturn("New User");

        // Khong co AuthAccount va User cu
        when(authAccountRepository.findByProviderAndProviderAccountId("GOOGLE", "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("newuser@gmail.com")).thenReturn(Optional.empty());

        User savedUser = User.builder()
                .id(99L)
                .email("newuser@gmail.com")
                .fullName("New User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        Role buyerRole = Role.builder().id((short) 1).name("BUYER").build();
        when(roleRepository.findByName("BUYER")).thenReturn(Optional.of(buyerRole));

        // Build auth response
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "newuser@gmail.com", null, Collections.emptyList());
        when(userDetailsService.loadUserByUsername("newuser@gmail.com")).thenReturn(userDetails);
        when(jwtService.generateAccessToken(userDetails)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq(userDetails), eq(99L), anyString())).thenReturn("refresh-token");
        when(jwtProperties.getExpirationMs()).thenReturn(3600000L);
        when(jwtProperties.getRefreshExpirationMs()).thenReturn(604800000L);
        when(userMapper.toResponse(savedUser)).thenReturn(
                com.b2c.flash_sale_b2c_UTC2.user.dto.UserResponse.builder()
                        .id(99L).email("newuser@gmail.com").build());
        when(userRoleRepository.findByUserId(99L)).thenReturn(List.of(UserRole.builder().role(buyerRole).build()));

        AuthResponse response = service.loginWithGoogle(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        // Da tao User moi + AuthAccount moi
        verify(userRepository).save(any(User.class));
        verify(userRoleRepository).save(any());
        verify(authAccountRepository).save(any(AuthAccount.class));
    }

    @Test
    @DisplayName("User Google cu quay lai, khong tao moi")
    void loginWithGoogle_Success_ExistingUser() throws Exception {
        GoogleOAuth2LoginRequest request = new GoogleOAuth2LoginRequest("valid_code", "http://localhost:3000/oauth2/redirect");
        String tokenResponseJson = "{\"id_token\":\"mock-id-token\"}";

        when(restTemplate.postForEntity(eq(props.getTokenEndpoint()), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(tokenResponseJson));
        when(idTokenVerifier.verify("mock-id-token")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getSubject()).thenReturn("google-sub-existing");
        when(payload.getEmail()).thenReturn("existing@gmail.com");
        when(payload.getEmailVerified()).thenReturn(true);
        when(payload.get("name")).thenReturn("Existing User");

        User existing = User.builder().id(7L).email("existing@gmail.com").fullName("Existing User")
                .status("ACTIVE").build();
        AuthAccount existingAa = AuthAccount.builder().id(1L).user(existing)
                .provider("GOOGLE").providerAccountId("google-sub-existing").build();
        when(authAccountRepository.findByProviderAndProviderAccountId("GOOGLE", "google-sub-existing"))
                .thenReturn(Optional.of(existingAa));

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "existing@gmail.com", null, Collections.emptyList());
        when(userDetailsService.loadUserByUsername("existing@gmail.com")).thenReturn(userDetails);
        when(jwtService.generateAccessToken(userDetails)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq(userDetails), eq(7L), anyString())).thenReturn("refresh-token");
        when(jwtProperties.getExpirationMs()).thenReturn(3600000L);
        when(jwtProperties.getRefreshExpirationMs()).thenReturn(604800000L);
        when(userMapper.toResponse(existing)).thenReturn(
                com.b2c.flash_sale_b2c_UTC2.user.dto.UserResponse.builder()
                        .id(7L).email("existing@gmail.com").build());
        when(userRoleRepository.findByUserId(7L)).thenReturn(List.of());

        AuthResponse response = service.loginWithGoogle(request);

        assertNotNull(response);
        verify(userRepository, never()).save(any(User.class));
        verify(authAccountRepository, never()).save(any(AuthAccount.class));
    }

    @Test
    @DisplayName("Email Google chua xac minh bi reject")
    void loginWithGoogle_EmailNotVerified() throws Exception {
        GoogleOAuth2LoginRequest request = new GoogleOAuth2LoginRequest("code", "http://localhost:3000/oauth2/redirect");
        String tokenResponseJson = "{\"id_token\":\"mock-id-token\"}";

        when(restTemplate.postForEntity(eq(props.getTokenEndpoint()), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(tokenResponseJson));
        when(idTokenVerifier.verify("mock-id-token")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getSubject()).thenReturn("google-sub");
        when(payload.getEmail()).thenReturn("unverified@gmail.com");
        when(payload.getEmailVerified()).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.loginWithGoogle(request));
        assertEquals(AuthErrorCode.GOOGLE_EMAIL_NOT_VERIFIED.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("id_token khong hop le (verify null) bi reject")
    void loginWithGoogle_InvalidIdToken() throws Exception {
        GoogleOAuth2LoginRequest request = new GoogleOAuth2LoginRequest("code", "http://localhost:3000/oauth2/redirect");
        String tokenResponseJson = "{\"id_token\":\"mock-id-token\"}";

        when(restTemplate.postForEntity(eq(props.getTokenEndpoint()), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(tokenResponseJson));
        when(idTokenVerifier.verify("mock-id-token")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.loginWithGoogle(request));
        assertEquals(AuthErrorCode.GOOGLE_INVALID_TOKEN.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Google tra error trong token response bi reject")
    void loginWithGoogle_TokenExchangeError() throws Exception {
        GoogleOAuth2LoginRequest request = new GoogleOAuth2LoginRequest("bad_code", "http://localhost:3000/oauth2/redirect");
        String errorJson = "{\"error\":\"invalid_grant\",\"error_description\":\"Code expired\"}";

        when(restTemplate.postForEntity(eq(props.getTokenEndpoint()), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(errorJson));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.loginWithGoogle(request));
        assertEquals(AuthErrorCode.GOOGLE_EXCHANGE_FAILED.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Google HTTP loi (5xx) bi reject")
    void loginWithGoogle_TokenExchangeHttpError() {
        GoogleOAuth2LoginRequest request = new GoogleOAuth2LoginRequest("code", "http://localhost:3000/oauth2/redirect");

        when(restTemplate.postForEntity(eq(props.getTokenEndpoint()), any(), eq(String.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("Connection refused"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.loginWithGoogle(request));
        assertEquals(AuthErrorCode.GOOGLE_EXCHANGE_FAILED.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Thieu config Google OAuth2 tren server")
    void loginWithGoogle_MissingConfig() {
        GoogleOAuth2Properties emptyProps = new GoogleOAuth2Properties();
        emptyProps.setClientId("");
        emptyProps.setClientSecret("");
        emptyProps.setRedirectUri("");

        GoogleOAuth2ServiceImpl emptyService = new GoogleOAuth2ServiceImpl(
                emptyProps, restTemplate, objectMapper, userRepository, roleRepository,
                userRoleRepository, authAccountRepository, userDetailsService, userMapper,
                jwtService, jwtProperties, stringRedisTemplate, httpTransport, idTokenVerifier);

        GoogleOAuth2LoginRequest request = new GoogleOAuth2LoginRequest("code", "http://localhost:3000/oauth2/redirect");

        BusinessException ex = assertThrows(BusinessException.class, () -> emptyService.loginWithGoogle(request));
        assertEquals(AuthErrorCode.GOOGLE_CONFIG_MISSING.getCode(), ex.getErrorCode().getCode());
    }

    // ========== getConsentUrl ==========

    @Test
    @DisplayName("User local da co cung email, lan dau link Google - merge thanh cong")
    void loginWithGoogle_LinkExistingLocalUserByEmail() throws Exception {
        GoogleOAuth2LoginRequest request = new GoogleOAuth2LoginRequest("code", "http://localhost:3000/oauth2/redirect");
        String tokenResponseJson = "{\"id_token\":\"mock-id-token\"}";

        when(restTemplate.postForEntity(eq(props.getTokenEndpoint()), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(tokenResponseJson));
        when(idTokenVerifier.verify("mock-id-token")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getSubject()).thenReturn("google-sub-newlink");
        when(payload.getEmail()).thenReturn("LocalUser@gmail.com");
        when(payload.getEmailVerified()).thenReturn(true);
        when(payload.get("name")).thenReturn("Local User");

        // Chua co AuthAccount Google nhung da co User local voi cung email (case-insensitive)
        when(authAccountRepository.findByProviderAndProviderAccountId("GOOGLE", "google-sub-newlink"))
                .thenReturn(Optional.empty());
        User existingLocal = User.builder().id(15L).email("localuser@gmail.com").fullName("Local User")
                .status("ACTIVE").build();
        when(userRepository.findByEmail("localuser@gmail.com"))
                .thenReturn(Optional.of(existingLocal));

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "localuser@gmail.com", null, Collections.emptyList());
        when(userDetailsService.loadUserByUsername("localuser@gmail.com")).thenReturn(userDetails);
        when(jwtService.generateAccessToken(userDetails)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq(userDetails), eq(15L), anyString())).thenReturn("refresh-token");
        when(jwtProperties.getExpirationMs()).thenReturn(3600000L);
        when(jwtProperties.getRefreshExpirationMs()).thenReturn(604800000L);
        when(userMapper.toResponse(existingLocal)).thenReturn(
                com.b2c.flash_sale_b2c_UTC2.user.dto.UserResponse.builder()
                        .id(15L).email("localuser@gmail.com").build());
        when(userRoleRepository.findByUserId(15L)).thenReturn(List.of());

        AuthResponse response = service.loginWithGoogle(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        // Khong tao User moi, chi tao AuthAccount moi de link
        verify(userRepository, never()).save(any(User.class));
        verify(authAccountRepository, times(1)).save(any(AuthAccount.class));
    }

    @Test
    @DisplayName("Tao User moi nhung Role BUYER chua co trong DB - tu khoi tao")
    void loginWithGoogle_AutoCreateBuyerRoleWhenMissing() throws Exception {
        GoogleOAuth2LoginRequest request = new GoogleOAuth2LoginRequest("code", "http://localhost:3000/oauth2/redirect");
        String tokenResponseJson = "{\"id_token\":\"mock-id-token\"}";

        when(restTemplate.postForEntity(eq(props.getTokenEndpoint()), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(tokenResponseJson));
        when(idTokenVerifier.verify("mock-id-token")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getSubject()).thenReturn("google-sub-role");
        when(payload.getEmail()).thenReturn("newrole@gmail.com");
        when(payload.getEmailVerified()).thenReturn(true);
        when(payload.get("name")).thenReturn("New Role");

        when(authAccountRepository.findByProviderAndProviderAccountId("GOOGLE", "google-sub-role"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("newrole@gmail.com")).thenReturn(Optional.empty());

        User savedUser = User.builder().id(50L).email("newrole@gmail.com").fullName("New Role")
                .status("ACTIVE").createdAt(Instant.now()).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Role BUYER khong ton tai, service phai tu khoi tao
        when(roleRepository.findByName("BUYER")).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_BUYER")).thenReturn(Optional.empty());
        Role createdRole = Role.builder().id((short) 1).name("BUYER").build();
        when(roleRepository.save(any(Role.class))).thenReturn(createdRole);

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "newrole@gmail.com", null, Collections.emptyList());
        when(userDetailsService.loadUserByUsername("newrole@gmail.com")).thenReturn(userDetails);
        when(jwtService.generateAccessToken(userDetails)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq(userDetails), eq(50L), anyString())).thenReturn("refresh-token");
        when(jwtProperties.getExpirationMs()).thenReturn(3600000L);
        when(jwtProperties.getRefreshExpirationMs()).thenReturn(604800000L);
        when(userMapper.toResponse(savedUser)).thenReturn(
                com.b2c.flash_sale_b2c_UTC2.user.dto.UserResponse.builder()
                        .id(50L).email("newrole@gmail.com").build());
        when(userRoleRepository.findByUserId(50L))
                .thenReturn(List.of(UserRole.builder().role(createdRole).build()));

        AuthResponse response = service.loginWithGoogle(request);

        assertNotNull(response);
        // Service phai tao Role moi khi chua co
        verify(roleRepository, times(1)).save(any(Role.class));
        assertNotNull(response.getRoles());
        assertTrue(response.getRoles().contains("BUYER") || response.getRoles().contains("ROLE_BUYER"));
    }

    @Test
    @DisplayName("getConsentUrl reject khi config thieu")
    void getConsentUrl_MissingConfig() {
        GoogleOAuth2Properties emptyProps = new GoogleOAuth2Properties();
        emptyProps.setClientId("");
        emptyProps.setClientSecret("");
        emptyProps.setRedirectUri("");

        GoogleOAuth2ServiceImpl emptyService = new GoogleOAuth2ServiceImpl(
                emptyProps, restTemplate, objectMapper, userRepository, roleRepository,
                userRoleRepository, authAccountRepository, userDetailsService, userMapper,
                jwtService, jwtProperties, stringRedisTemplate, httpTransport, idTokenVerifier);

        BusinessException ex = assertThrows(BusinessException.class, emptyService::getConsentUrl);
        assertEquals(AuthErrorCode.GOOGLE_CONFIG_MISSING.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("User co nhieu roles - response.tra ve day du cac role prefix ROLE_")
    void loginWithGoogle_MultipleRolesInResponse() throws Exception {
        GoogleOAuth2LoginRequest request = new GoogleOAuth2LoginRequest("code", "http://localhost:3000/oauth2/redirect");
        String tokenResponseJson = "{\"id_token\":\"mock-id-token\"}";

        when(restTemplate.postForEntity(eq(props.getTokenEndpoint()), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(tokenResponseJson));
        when(idTokenVerifier.verify("mock-id-token")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getSubject()).thenReturn("google-sub-multirole");
        when(payload.getEmail()).thenReturn("multirole@gmail.com");
        when(payload.getEmailVerified()).thenReturn(true);
        when(payload.get("name")).thenReturn("Multi Role");

        User existing = User.builder().id(22L).email("multirole@gmail.com").fullName("Multi Role")
                .status("ACTIVE").build();
        AuthAccount existingAa = AuthAccount.builder().id(1L).user(existing)
                .provider("GOOGLE").providerAccountId("google-sub-multirole").build();
        when(authAccountRepository.findByProviderAndProviderAccountId("GOOGLE", "google-sub-multirole"))
                .thenReturn(Optional.of(existingAa));

        Role buyerRole = Role.builder().id((short) 1).name("BUYER").build();
        Role sellerRole = Role.builder().id((short) 2).name("ROLE_SELLER").build();
        when(userRoleRepository.findByUserId(22L)).thenReturn(List.of(
                UserRole.builder().role(buyerRole).build(),
                UserRole.builder().role(sellerRole).build()
        ));

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "multirole@gmail.com", null, Collections.emptyList());
        when(userDetailsService.loadUserByUsername("multirole@gmail.com")).thenReturn(userDetails);
        when(jwtService.generateAccessToken(userDetails)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq(userDetails), eq(22L), anyString())).thenReturn("refresh-token");
        when(jwtProperties.getExpirationMs()).thenReturn(3600000L);
        when(jwtProperties.getRefreshExpirationMs()).thenReturn(604800000L);
        when(userMapper.toResponse(existing)).thenReturn(
                com.b2c.flash_sale_b2c_UTC2.user.dto.UserResponse.builder()
                        .id(22L).email("multirole@gmail.com").build());

        AuthResponse response = service.loginWithGoogle(request);

        assertNotNull(response);
        assertNotNull(response.getRoles());
        assertEquals(2, response.getRoles().size());
        // "BUYER" phai duoc prefix thanh "ROLE_BUYER", "ROLE_SELLER" giu nguyen
        assertTrue(response.getRoles().contains("ROLE_BUYER"));
        assertTrue(response.getRoles().contains("ROLE_SELLER"));
    }

    @Test
    @DisplayName("Tao Google consent URL dung client_id + redirect_uri + scope")
    void getConsentUrl_Success() {
        GoogleConsentUrlResponse response = service.getConsentUrl();

        assertNotNull(response);
        assertNotNull(response.getAuthorizationUrl());
        assertTrue(response.getAuthorizationUrl().contains("accounts.google.com"));
        assertTrue(response.getAuthorizationUrl().contains("client_id=" + props.getClientId()));
        assertTrue(response.getAuthorizationUrl().contains("redirect_uri="));
        assertTrue(response.getAuthorizationUrl().contains("response_type=code"));
        assertTrue(response.getAuthorizationUrl().contains("scope=openid"));
        assertNotNull(response.getState());
    }
}