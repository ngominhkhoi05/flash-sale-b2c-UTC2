package com.b2c.flash_sale_b2c_UTC2.loadtest;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Address;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Role;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.entity.UserRole;
import com.b2c.flash_sale_b2c_UTC2.user.repository.AddressRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.RoleRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "loadtest.seed.enabled", havingValue = "true")
public class LoadTestSeedRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("=== STARTING LOAD TEST SEEDING ===");
        int totalUsers = 1000;

        Role buyerRole = roleRepository.findByName("BUYER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("BUYER").description("Buyer Role").build()));

        List<Map<String, Object>> tokensList = new ArrayList<>(totalUsers);

        for (int i = 1; i <= totalUsers; i++) {
            String email = "loadtest_user_" + i + "@loadtest.local";
            String fullName = "LoadTest User " + i;
            String phone = String.format("098%07d", i);

            Optional<User> existingUserOpt = userRepository.findByEmail(email);
            User user;
            if (existingUserOpt.isPresent()) {
                user = existingUserOpt.get();
            } else {
                user = User.builder()
                        .email(email)
                        .fullName(fullName)
                        .phone(phone)
                        .status("ACTIVE")
                        .build();
                user = userRepository.save(user);

                UserRole userRole = UserRole.builder()
                        .user(user)
                        .role(buyerRole)
                        .build();
                userRoleRepository.save(userRole);
            }

            Optional<Address> addressOpt = addressRepository.findByUserIdAndIsDefaultTrue(user.getId());
            Address address;
            if (addressOpt.isPresent()) {
                address = addressOpt.get();
            } else {
                List<Address> addresses = addressRepository.findByUserId(user.getId());
                if (addresses != null && !addresses.isEmpty()) {
                    address = addresses.get(0);
                } else {
                    address = Address.builder()
                            .user(user)
                            .contactName(fullName)
                            .phone(phone)
                            .province("TP. Hồ Chí Minh")
                            .district("Thủ Đức")
                            .ward("Tăng Nhơn Phú A")
                            .detailAddress(i + " Lê Văn Việt")
                            .isDefault(true)
                            .build();
                    address = addressRepository.save(address);
                }
            }

            CustomUserDetails userDetails = new CustomUserDetails(user, List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
            String token = jwtService.generateAccessToken(userDetails);

            Map<String, Object> tokenItem = new HashMap<>();
            tokenItem.put("userId", user.getId());
            tokenItem.put("addressId", address.getId());
            tokenItem.put("token", token);
            tokensList.add(tokenItem);
        }

        File loadtestDir = new File("loadtest");
        if (!loadtestDir.exists()) {
            loadtestDir.mkdirs();
        }
        File tokensFile = new File(loadtestDir, "tokens.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tokensFile, tokensList);

        log.info("=== LOAD TEST SEEDING COMPLETED: {} tokens exported to {} ===", tokensList.size(), tokensFile.getAbsolutePath());
    }
}
