package com.b2c.flash_sale_b2c_UTC2.store.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.store.dto.CreateStoreRequest;
import com.b2c.flash_sale_b2c_UTC2.store.dto.FeaturedStoreResponse;
import com.b2c.flash_sale_b2c_UTC2.store.dto.SidebarSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.store.dto.StoreResponse;
import com.b2c.flash_sale_b2c_UTC2.store.dto.UpdateStoreRequest;
import com.b2c.flash_sale_b2c_UTC2.store.dto.UpdateStoreStatusRequest;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.exception.StoreErrorCode;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.store.mapper.StoreMapper;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.image.repository.ImageRepository;
import com.b2c.flash_sale_b2c_UTC2.user.dto.AddressResponse;
import com.b2c.flash_sale_b2c_UTC2.user.dto.CreateAddressRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateAddressRequest;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Address;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Role;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.entity.UserRole;
import com.b2c.flash_sale_b2c_UTC2.user.exception.UserErrorCode;
import com.b2c.flash_sale_b2c_UTC2.user.mapper.AddressMapper;
import com.b2c.flash_sale_b2c_UTC2.user.repository.AddressRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.RoleRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRoleRepository;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.Wallet;
import com.b2c.flash_sale_b2c_UTC2.wallet.repository.WalletRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final WalletRepository walletRepository;
    private final AddressRepository addressRepository;
    private final ImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final FlashSaleItemRepository flashSaleItemRepository;
    private final FlashSaleSlotRepository flashSaleSlotRepository;
    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductReviewRepository productReviewRepository;
    private final StoreMapper storeMapper;
    private final AddressMapper addressMapper;
    private final ImageService imageService;

    @Override
    @Transactional
    public StoreResponse registerStore(Long userId, CreateStoreRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (storeRepository.existsByUserId(userId)) {
            throw new BusinessException(StoreErrorCode.STORE_ALREADY_EXISTS);
        }

        String trimmedName = request.getStoreName().trim();
        if (storeRepository.existsByStoreName(trimmedName)) {
            throw new BusinessException(StoreErrorCode.STORE_NAME_ALREADY_EXISTS);
        }

        Store store = Store.builder()
                .user(user)
                .storeName(trimmedName)
                .description(request.getDescription())
                .defaultCommissionRate(new BigDecimal("0.0500"))
                .status("PENDING")
                .createdAt(Instant.now())
                .build();

        store = storeRepository.save(store);

        // Khởi tạo ví doanh thu cho gian hàng theo mô hình 1-1
        Wallet wallet = Wallet.builder()
                .store(store)
                .balance(BigDecimal.ZERO)
                .frozenBalance(BigDecimal.ZERO)
                .updatedAt(Instant.now())
                .build();
        walletRepository.save(wallet);

        log.info("Người dùng id={} đã đăng ký gian hàng '{}' thành công (chờ duyệt)", userId, trimmedName);
        return toResponseWithLogo(store);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse getMyStore(Long userId) {
        Store store = getStoreByUserIdOrThrow(userId);
        return toResponseWithLogo(store);
    }

    @Override
    @Transactional
    public StoreResponse updateMyStore(Long userId, UpdateStoreRequest request) {
        Store store = getStoreByUserIdOrThrow(userId);

        String trimmedName = request.getStoreName().trim();
        if (!store.getStoreName().equalsIgnoreCase(trimmedName)) {
            if (storeRepository.existsByStoreName(trimmedName)) {
                throw new BusinessException(StoreErrorCode.STORE_NAME_ALREADY_EXISTS);
            }
            store.setStoreName(trimmedName);
        }

        store.setDescription(request.getDescription());

        store = storeRepository.save(store);
        return toResponseWithLogo(store);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse getStoreById(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        if (!"APPROVED".equalsIgnoreCase(store.getStatus())) {
            throw new BusinessException(StoreErrorCode.STORE_NOT_APPROVED);
        }

        return toResponseWithLogo(store);
    }

    @Override
    @Transactional
    public StoreResponse updateStoreStatus(Long storeId, UpdateStoreStatusRequest request) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        String newStatus = request.getStatus().trim().toUpperCase();
        store.setStatus(newStatus);

        // Khi duyệt gian hàng (APPROVED), gán thêm vai trò SELLER cho chủ gian hàng
        if ("APPROVED".equals(newStatus)) {
            assignSellerRole(store.getUser());
        }

        store = storeRepository.save(store);
        log.info("Admin đã cập nhật trạng thái gian hàng id={} thành {}", storeId, newStatus);
        return toResponseWithLogo(store);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeaturedStoreResponse> getFeaturedStores(int limit) {
        int safeLimit = Math.max(1, Math.min(8, limit));
        List<Store> stores = storeRepository.findFeaturedStores(
                org.springframework.data.domain.PageRequest.of(0, safeLimit));
        return stores.stream()
                .map(this::toFeaturedStoreResponse)
                .toList();
    }

    private FeaturedStoreResponse toFeaturedStoreResponse(Store store) {
        return FeaturedStoreResponse.builder()
                .id(store.getId())
                .name(store.getStoreName())
                .logoUrl(resolveStoreLogoUrl(store))
                .verified(true)
                .rating(0.0)
                .totalRatings(0L)
                .followers(0L)
                .productThumbnails(java.util.Collections.emptyList())
                .totalProducts(0)
                .createdAt(store.getCreatedAt())
                .build();
    }

    private String resolveStoreLogoUrl(Store store) {
        try {
            return imageRepository
                    .findFirstByOwnerTypeAndOwnerIdAndStatus(
                            com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType.STORE,
                            store.getId(), "ACTIVE")
                    .map(img -> img.getUrl())
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Could not resolve logo for store {}: {}", store.getId(), e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getStoreAddresses(Long userId) {
        Store store = getStoreByUserIdOrThrow(userId);
        List<Address> addresses = addressRepository.findByStoreId(store.getId());
        return addressMapper.toResponseList(addresses);
    }

    @Override
    @Transactional
    public AddressResponse createStoreAddress(Long userId, CreateAddressRequest request) {
        Store store = getStoreByUserIdOrThrow(userId);

        boolean isDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (isDefault) {
            addressRepository.findByStoreIdAndIsDefaultTrue(store.getId())
                    .ifPresent(existingDefault -> {
                        existingDefault.setIsDefault(false);
                        existingDefault.setUpdatedAt(Instant.now());
                        addressRepository.save(existingDefault);
                    });
        }

        // Tuân thủ triệt để XOR CHECK constraint: user = null, store = store
        Address address = Address.builder()
                .user(null)
                .store(store)
                .contactName(request.getContactName().trim())
                .phone(request.getPhone().trim())
                .province(request.getProvince().trim())
                .district(request.getDistrict().trim())
                .ward(request.getWard().trim())
                .detailAddress(request.getDetailAddress().trim())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .isDefault(isDefault)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        address = addressRepository.save(address);
        log.info("Tạo địa chỉ kho thành công cho storeId={}", store.getId());
        return addressMapper.toResponse(address);
    }

    @Override
    @Transactional
    public AddressResponse updateStoreAddress(Long userId, Long addressId, UpdateAddressRequest request) {
        Store store = getStoreByUserIdOrThrow(userId);
        Address address = getStoreAddressAndVerifyOwnership(store, addressId);

        boolean isDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (isDefault && !Boolean.TRUE.equals(address.getIsDefault())) {
            addressRepository.findByStoreIdAndIsDefaultTrue(store.getId())
                    .ifPresent(existingDefault -> {
                        existingDefault.setIsDefault(false);
                        existingDefault.setUpdatedAt(Instant.now());
                        addressRepository.save(existingDefault);
                    });
        }

        address.setContactName(request.getContactName().trim());
        address.setPhone(request.getPhone().trim());
        address.setProvince(request.getProvince().trim());
        address.setDistrict(request.getDistrict().trim());
        address.setWard(request.getWard().trim());
        address.setDetailAddress(request.getDetailAddress().trim());
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());
        address.setIsDefault(isDefault);
        address.setUpdatedAt(Instant.now());

        address = addressRepository.save(address);
        return addressMapper.toResponse(address);
    }

    @Override
    @Transactional
    public void deleteStoreAddress(Long userId, Long addressId) {
        Store store = getStoreByUserIdOrThrow(userId);
        Address address = getStoreAddressAndVerifyOwnership(store, addressId);
        addressRepository.delete(address);
        log.info("Xóa địa chỉ kho id={} của storeId={}", addressId, store.getId());
    }

    @Override
    @Transactional
    public AddressResponse setDefaultStoreAddress(Long userId, Long addressId) {
        Store store = getStoreByUserIdOrThrow(userId);
        Address address = getStoreAddressAndVerifyOwnership(store, addressId);

        addressRepository.findByStoreIdAndIsDefaultTrue(store.getId())
                .ifPresent(existingDefault -> {
                    if (!existingDefault.getId().equals(addressId)) {
                        existingDefault.setIsDefault(false);
                        existingDefault.setUpdatedAt(Instant.now());
                        addressRepository.save(existingDefault);
                    }
                });

        address.setIsDefault(true);
        address.setUpdatedAt(Instant.now());
        address = addressRepository.save(address);
        return addressMapper.toResponse(address);
    }

    private Store getStoreByUserIdOrThrow(Long userId) {
        return storeRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));
    }

    private Address getStoreAddressAndVerifyOwnership(Store store, Long addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_ADDRESS_NOT_FOUND));

        if (address.getStore() == null || !address.getStore().getId().equals(store.getId())) {
            throw new BusinessException(StoreErrorCode.STORE_ADDRESS_ACCESS_DENIED);
        }
        return address;
    }

    private void assignSellerRole(User user) {
        Role sellerRole = roleRepository.findByName("SELLER")
                .or(() -> roleRepository.findByName("ROLE_SELLER"))
                .orElseGet(() -> {
                    log.info("Khởi tạo vai trò SELLER trong hệ thống");
                    Role newRole = Role.builder()
                            .name("SELLER")
                            .description("Vai trò Người bán hàng")
                            .createdAt(Instant.now())
                            .build();
                    return roleRepository.save(newRole);
                });

        if (!userRoleRepository.existsByUserIdAndRoleId(user.getId(), sellerRole.getId())) {
            UserRole userRole = UserRole.builder()
                    .user(user)
                    .role(sellerRole)
                    .assignedAt(Instant.now())
                    .build();
            userRoleRepository.save(userRole);
            log.info("Đã gán vai trò SELLER cho người dùng id={}", user.getId());
        }
    }

    /**
     * Map Store → StoreResponse rồi bổ sung logoUrl lấy từ bảng images (owner_type=STORE).
     */
    private StoreResponse toResponseWithLogo(Store store) {
        StoreResponse resp = storeMapper.toResponse(store);
        com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse logo = imageService.getPrimaryImage(ImageOwnerType.STORE, store.getId());
        if (logo != null) {
            resp.setLogoUrl(logo.getUrl());
        }
        return resp;
    }

    @Override
    @Transactional(readOnly = true)
    public SidebarSummaryResponse getSidebarSummary(Long userId) {
        Store store = getStoreByUserIdOrThrow(userId);
        Long storeId = store.getId();

        // Date boundaries
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate monthStart = today.withDayOfMonth(1);
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant monthStartInst = monthStart.atStartOfDay(zone).toInstant();

        // Product count
        long totalProducts = productRepository.countByStoreIdAndStatus(storeId, "ACTIVE");

        // Active flash sales
        long activeFlashSales = flashSaleItemRepository.countActiveByStoreId(storeId);

        // Order counts
        long pendingOrders = orderRepository.countByStoreIdAndStatus(storeId, "PENDING_PAYMENT");

        // Today's orders
        long todayOrders = orderRepository.countByStoreIdAndCreatedAtBetween(storeId, todayStart, todayEnd);

        // Today's revenue (CONFIRMED + COMPLETED)
        List<String> revenueStatuses = List.of("CONFIRMED", "COMPLETED");
        BigDecimal todayRevenue = orderRepository.sumTotalAmountByStoreIdAndCreatedAtBetweenAndStatusIn(
                storeId, todayStart, todayEnd, revenueStatuses);
        if (todayRevenue == null) {
            todayRevenue = BigDecimal.ZERO;
        }

        // Month revenue
        BigDecimal monthRevenue = orderRepository.sumTotalAmountByStoreIdAndCreatedAtBetweenAndStatusIn(
                storeId, monthStartInst, todayEnd, revenueStatuses);
        if (monthRevenue == null) {
            monthRevenue = BigDecimal.ZERO;
        }

        // Ratings
        Double ratingAvg = productReviewRepository.avgRatingByStoreId(storeId);
        if (ratingAvg == null) {
            ratingAvg = 0.0;
        }
        long ratingCount = productReviewRepository.countByStoreId(storeId);

        // Low stock products
        long lowStockProducts = productVariantRepository.countByProductStoreIdAndStockQuantityLessThan(storeId, 10);

        return SidebarSummaryResponse.builder()
                .totalProducts(totalProducts)
                .activeFlashSales(activeFlashSales)
                .pendingOrders(pendingOrders)
                .unpaidOrders(pendingOrders) // same as pending for this API design
                .todayOrders(todayOrders)
                .todayRevenue(todayRevenue)
                .monthRevenue(monthRevenue)
                .ratingAvg(ratingAvg)
                .ratingCount(ratingCount)
                .lowStockProducts(lowStockProducts)
                .storeStatus(store.getStatus())
                .tier(store.getTier().name())
                .build();
    }
}
