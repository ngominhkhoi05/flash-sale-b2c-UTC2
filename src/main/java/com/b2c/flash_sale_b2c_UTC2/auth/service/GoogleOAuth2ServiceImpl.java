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
import com.b2c.flash_sale_b2c_UTC2.user.dto.UserResponse;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Role;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.entity.UserRole;
import com.b2c.flash_sale_b2c_UTC2.user.mapper.UserMapper;
import com.b2c.flash_sale_b2c_UTC2.user.repository.RoleRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRoleRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Google OAuth2 service implementation (Authorization Code flow).
 * Buoc 1: Doi authorization code -> id_token (REST call den Google token endpoint).
 * Buoc 2: Verify id_token signature + claims bang GoogleIdTokenVerifier.
 * Buoc 3: Upsert User + AuthAccount(provider=GOOGLE).
 * Buoc 4: Cap JWT access + refresh token (tai su dung pattern cua AuthServiceImpl).
 */
@Slf4j
@Service
public class GoogleOAuth2ServiceImpl implements GoogleOAuth2Service {

    private static final String PROVIDER_GOOGLE = "GOOGLE";
    private static final String REDIS_REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String REDIS_USER_TOKENS_PREFIX = "auth:user_tokens:";

    private final GoogleOAuth2Properties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthAccountRepository authAccountRepository;
    private final UserDetailsService userDetailsService;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final GoogleIdTokenVerifier idTokenVerifier;

    @Autowired
    public GoogleOAuth2ServiceImpl(
            GoogleOAuth2Properties props,
            @Qualifier("restTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            AuthAccountRepository authAccountRepository,
            UserDetailsService userDetailsService,
            UserMapper userMapper,
            JwtService jwtService,
            JwtProperties jwtProperties,
            StringRedisTemplate stringRedisTemplate,
            HttpTransport httpTransport
    ) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.authAccountRepository = authAccountRepository;
        this.userDetailsService = userDetailsService;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.stringRedisTemplate = stringRedisTemplate;

        // Khoi tao GoogleIdTokenVerifier de verify id_token signature offline (JWKS cache).
        com.google.api.client.json.JsonFactory jsonFactory = new com.google.api.client.json.gson.GsonFactory();
        GoogleIdTokenVerifier.Builder builder = new GoogleIdTokenVerifier.Builder(
                httpTransport != null ? httpTransport : new com.google.api.client.http.javanet.NetHttpTransport(),
                jsonFactory
        ).setAudience(Collections.singletonList(props.getClientId()));
        if (props.getIssuer() != null && !props.getIssuer().isBlank()) {
            builder.setIssuer(props.getIssuer());
        }
        this.idTokenVerifier = builder.build();
    }

    /**
     * Constructor noi bo (package-private) cho test: truyen san GoogleIdTokenVerifier mock.
     * KHONG danh dau @Autowired de Spring khong nham lan voi constructor production.
     */
    GoogleOAuth2ServiceImpl(
            GoogleOAuth2Properties props,
            @Qualifier("restTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            AuthAccountRepository authAccountRepository,
            UserDetailsService userDetailsService,
            UserMapper userMapper,
            JwtService jwtService,
            JwtProperties jwtProperties,
            StringRedisTemplate stringRedisTemplate,
            HttpTransport httpTransport,
            GoogleIdTokenVerifier verifierOverride
    ) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.authAccountRepository = authAccountRepository;
        this.userDetailsService = userDetailsService;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.stringRedisTemplate = stringRedisTemplate;

        if (verifierOverride != null) {
            this.idTokenVerifier = verifierOverride;
        } else {
            // Khoi tao GoogleIdTokenVerifier de verify id_token signature offline (JWKS cache).
            com.google.api.client.json.JsonFactory jsonFactory = new com.google.api.client.json.gson.GsonFactory();
            GoogleIdTokenVerifier.Builder builder = new GoogleIdTokenVerifier.Builder(
                    httpTransport != null ? httpTransport : new com.google.api.client.http.javanet.NetHttpTransport(),
                    jsonFactory
            ).setAudience(Collections.singletonList(props.getClientId()));
            if (props.getIssuer() != null && !props.getIssuer().isBlank()) {
                builder.setIssuer(props.getIssuer());
            }
            this.idTokenVerifier = builder.build();
        }
    }

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(GoogleOAuth2LoginRequest request) {
        validateConfig();

        // 1) Doi code -> id_token
        String idToken = exchangeCodeForIdToken(request.getCode(), request.getRedirectUri());

        // 2) Verify id_token -> lay profile
        GoogleProfile profile = verifyIdTokenAndExtractProfile(idToken);
        log.info("Google login: sub={} email={} emailVerified={}", profile.sub, profile.email, profile.emailVerified);

        // 3) Upsert User + AuthAccount
        User user = upsertUserAndAuthAccount(profile);

        // 4) Cap JWT
        return buildAuthResponse(user);
    }

    @Override
    public GoogleConsentUrlResponse getConsentUrl() {
        validateConfig();

        // Google OAuth2 v2 authorization endpoint
        // (openid+email+profile la scope toi thieu can thiet)
        String baseUrl = "https://accounts.google.com/o/oauth2/v2/auth";
        String url = baseUrl
                + "?client_id=" + urlEncode(props.getClientId())
                + "&redirect_uri=" + urlEncode(props.getRedirectUri())
                + "&response_type=code"
                + "&scope=" + urlEncode("openid email profile")
                + "&access_type=offline"
                + "&prompt=consent";

        // State co the them sau de CSRF (FE tu tao random + verify)
        return GoogleConsentUrlResponse.builder()
                .authorizationUrl(url)
                .state(UUID.randomUUID().toString())
                .build();
    }

    // ===== Buoc 1: Doi code -> id_token =====

    private String exchangeCodeForIdToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    props.getTokenEndpoint(), entity, String.class);
            JsonNode body = objectMapper.readTree(response.getBody());

            // Google tra ve id_token, access_token, refresh_token, expires_in, token_type, scope
            if (body.has("error")) {
                String err = body.path("error").asText("unknown");
                String desc = body.path("error_description").asText("");
                log.error("Google token exchange error: {} - {}", err, desc);
                throw new BusinessException(AuthErrorCode.GOOGLE_EXCHANGE_FAILED);
            }

            JsonNode idTokenNode = body.get("id_token");
            if (idTokenNode == null || idTokenNode.asText().isBlank()) {
                log.error("Google token exchange response missing id_token: {}", body);
                throw new BusinessException(AuthErrorCode.GOOGLE_EXCHANGE_FAILED);
            }
            return idTokenNode.asText();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("Google token exchange HTTP error: {}", ex.getMessage());
            throw new BusinessException(AuthErrorCode.GOOGLE_EXCHANGE_FAILED);
        } catch (Exception ex) {
            log.error("Google token exchange unexpected error: {}", ex.getMessage(), ex);
            throw new BusinessException(AuthErrorCode.GOOGLE_EXCHANGE_FAILED);
        }
    }

    // ===== Buoc 2: Verify id_token bang GoogleIdTokenVerifier =====

    private GoogleProfile verifyIdTokenAndExtractProfile(String idToken) {
        try {
            GoogleIdToken verified = idTokenVerifier.verify(idToken);
            if (verified == null) {
                log.error("Google id_token verification failed (signature/expired/audience mismatch)");
                throw new BusinessException(AuthErrorCode.GOOGLE_INVALID_TOKEN);
            }
            GoogleIdToken.Payload payload = verified.getPayload();

            String sub = payload.getSubject();
            String email = payload.getEmail();
            boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
                log.error("Google id_token missing sub/email: sub={} email={}", sub, email);
                throw new BusinessException(AuthErrorCode.GOOGLE_INVALID_TOKEN);
            }
            if (!emailVerified) {
                log.error("Google email not verified: email={}", email);
                throw new BusinessException(AuthErrorCode.GOOGLE_EMAIL_NOT_VERIFIED);
            }

            return new GoogleProfile(sub, email, name, picture, emailVerified);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Google id_token verify error: {}", ex.getMessage(), ex);
            throw new BusinessException(AuthErrorCode.GOOGLE_INVALID_TOKEN);
        }
    }

    // ===== Buoc 3: Upsert User + AuthAccount =====

    private User upsertUserAndAuthAccount(GoogleProfile profile) {
        // 3a) Tim theo (provider, providerAccountId) truoc
        return authAccountRepository.findByProviderAndProviderAccountId(PROVIDER_GOOGLE, profile.sub)
                .map(AuthAccount::getUser)
                .map(user -> {
                    log.info("Google login: existing user id={} (linked Google account)", user.getId());
                    return user;
                })
                .orElseGet(() -> {
                    // 3b) Neu chua link, tim theo email de merge voi local account
                    User user = userRepository.findByEmail(profile.email.toLowerCase())
                            .orElseGet(() -> createNewUserFromGoogle(profile));
                    attachGoogleAuthAccount(user, profile);
                    return user;
                });
    }

    private User createNewUserFromGoogle(GoogleProfile profile) {
        Instant now = Instant.now();
        User newUser = User.builder()
                .email(profile.email.toLowerCase())
                .passwordHash(null) // Google-only user, khong co password
                .fullName(profile.name != null && !profile.name.isBlank() ? profile.name : profile.email)
                .phone(null)
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();
        newUser = userRepository.save(newUser);
        assignDefaultBuyerRole(newUser);
        log.info("Google login: created new user id={} email={}", newUser.getId(), newUser.getEmail());
        return newUser;
    }

    private void attachGoogleAuthAccount(User user, GoogleProfile profile) {
        // Check duplicate (defensive — unique constraint cung bao ve)
        if (authAccountRepository.findByProviderAndProviderAccountId(PROVIDER_GOOGLE, profile.sub).isPresent()) {
            return;
        }
        AuthAccount authAccount = AuthAccount.builder()
                .user(user)
                .provider(PROVIDER_GOOGLE)
                .providerAccountId(profile.sub)
                .providerEmail(profile.email.toLowerCase())
                .createdAt(Instant.now())
                .build();
        authAccountRepository.save(authAccount);
        log.info("Google login: attached AuthAccount provider=GOOGLE sub={} to user id={}", profile.sub, user.getId());
    }

    private void assignDefaultBuyerRole(User user) {
        String roleName = "BUYER";
        Role role = roleRepository.findByName(roleName)
                .or(() -> roleRepository.findByName("ROLE_" + roleName))
                .orElseGet(() -> {
                    log.info("Khoi tao vai tro mac dinh: {}", roleName);
                    Role newRole = Role.builder()
                            .name(roleName)
                            .description("Vai tro " + roleName)
                            .createdAt(Instant.now())
                            .build();
                    return roleRepository.save(newRole);
                });

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .assignedAt(Instant.now())
                .build();
        userRoleRepository.save(userRole);
    }

    // ===== Buoc 4: Cap JWT (mirror AuthServiceImpl.login) =====

    private AuthResponse buildAuthResponse(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails);
        String jti = UUID.randomUUID().toString();
        String refreshToken = jwtService.generateRefreshToken(userDetails, user.getId(), jti);
        saveRefreshTokenInRedis(user.getId(), jti);

        UserResponse userResponse = userMapper.toResponse(user);
        List<String> roles = getUserRoles(user.getId());
        if (userResponse != null) {
            userResponse.setRoles(roles);
        }

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpirationMs() / 1000)
                .user(userResponse)
                .roles(roles)
                .build();
    }

    private List<String> getUserRoles(Long userId) {
        if (userId == null || userRoleRepository == null) return List.of();
        List<UserRole> list = userRoleRepository.findByUserId(userId);
        if (list == null || list.isEmpty()) return List.of();
        return list.stream()
                .filter(ur -> ur != null && ur.getRole() != null && ur.getRole().getName() != null)
                .map(ur -> {
                    String name = ur.getRole().getName();
                    return name.startsWith("ROLE_") ? name : "ROLE_" + name;
                })
                .distinct()
                .toList();
    }

    private void saveRefreshTokenInRedis(Long userId, String jti) {
        if (userId == null || jti == null) return;
        String redisKey = REDIS_REFRESH_KEY_PREFIX + userId + ":" + jti;
        long ttlSeconds = jwtProperties.getRefreshExpirationMs() / 1000;
        stringRedisTemplate.opsForValue().set(redisKey, "VALID", ttlSeconds, TimeUnit.SECONDS);

        String userTokensKey = REDIS_USER_TOKENS_PREFIX + userId;
        stringRedisTemplate.opsForSet().add(userTokensKey, jti);
        stringRedisTemplate.expire(userTokensKey, ttlSeconds, TimeUnit.SECONDS);
    }

    // ===== Helpers =====

    private void validateConfig() {
        if (isBlank(props.getClientId()) || isBlank(props.getClientSecret()) || isBlank(props.getRedirectUri())) {
            log.error("Google OAuth2 config missing: clientId/secret/redirectUri");
            throw new BusinessException(AuthErrorCode.GOOGLE_CONFIG_MISSING);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ===== DTO noi bo cho Google profile =====
    private record GoogleProfile(String sub, String email, String name, String picture, boolean emailVerified) {
    }
}