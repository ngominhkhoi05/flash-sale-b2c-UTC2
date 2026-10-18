package com.b2c.flash_sale_b2c_UTC2.store.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.store.dto.CreateStoreRequest;
import com.b2c.flash_sale_b2c_UTC2.store.dto.StoreResponse;
import com.b2c.flash_sale_b2c_UTC2.store.dto.UpdateStoreRequest;
import com.b2c.flash_sale_b2c_UTC2.store.dto.UpdateStoreStatusRequest;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.exception.StoreErrorCode;
import com.b2c.flash_sale_b2c_UTC2.store.mapper.StoreMapper;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.dto.AddressResponse;
import com.b2c.flash_sale_b2c_UTC2.user.dto.CreateAddressRequest;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Address;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Role;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.entity.UserRole;
import com.b2c.flash_sale_b2c_UTC2.user.mapper.AddressMapper;
import com.b2c.flash_sale_b2c_UTC2.user.repository.AddressRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.RoleRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRoleRepository;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.Wallet;
import com.b2c.flash_sale_b2c_UTC2.wallet.repository.WalletRepository;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private StoreMapper storeMapper;

    @Mock
    private ImageService imageService;

    @Mock
    private AddressMapper addressMapper;

    @Mock
    private com.b2c.flash_sale_b2c_UTC2.image.repository.ImageRepository imageRepository;

    @Mock
    private com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository productRepository;

    @Mock
    private com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository orderRepository;

    @Mock
    private com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository productReviewRepository;

    @InjectMocks
    private StoreServiceImpl storeService;

    private User sampleUser;
    private Store sampleStore;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("seller@example.com")
                .fullName("Trần Thị B")
                .status("ACTIVE")
                .build();

        sampleStore = Store.builder()
                .id(10L)
                .user(sampleUser)
                .storeName("Shop Cong Nghe UTC2")
                .description("Gian hang chinh hang")
                .defaultCommissionRate(new BigDecimal("0.0500"))
                .status("PENDING")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Đăng ký gian hàng thành công khởi tạo trạng thái PENDING và tạo ví tiền")
    void registerStore_Success() {
        CreateStoreRequest request = CreateStoreRequest.builder()
                .storeName("Shop Cong Nghe UTC2")
                .description("Gian hang chinh hang")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(storeRepository.existsByUserId(1L)).thenReturn(false);
        when(storeRepository.existsByStoreName("Shop Cong Nghe UTC2")).thenReturn(false);
        when(storeRepository.save(any(Store.class))).thenReturn(sampleStore);
        when(storeMapper.toResponse(sampleStore)).thenReturn(StoreResponse.builder()
                .id(10L).userId(1L).storeName("Shop Cong Nghe UTC2").status("PENDING").build());

        StoreResponse response = storeService.registerStore(1L, request);

        assertNotNull(response);
        assertEquals("PENDING", response.getStatus());
        assertEquals("Shop Cong Nghe UTC2", response.getStoreName());
        verify(walletRepository).save(any(Wallet.class));
        verify(storeRepository).save(any(Store.class));
    }

    @Test
    @DisplayName("Đăng ký gian hàng thất bại khi người dùng đã có gian hàng")
    void registerStore_ShouldThrowException_WhenUserAlreadyHasStore() {
        CreateStoreRequest request = CreateStoreRequest.builder()
                .storeName("Shop Mới")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(storeRepository.existsByUserId(1L)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> storeService.registerStore(1L, request));
        assertEquals(StoreErrorCode.STORE_ALREADY_EXISTS.getCode(), exception.getErrorCode().getCode());
        verify(storeRepository, never()).save(any(Store.class));
    }

    @Test
    @DisplayName("Đăng ký gian hàng thất bại khi tên gian hàng bị trùng lặp")
    void registerStore_ShouldThrowException_WhenStoreNameExists() {
        CreateStoreRequest request = CreateStoreRequest.builder()
                .storeName("Shop Trùng Tên")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(storeRepository.existsByUserId(1L)).thenReturn(false);
        when(storeRepository.existsByStoreName("Shop Trùng Tên")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> storeService.registerStore(1L, request));
        assertEquals(StoreErrorCode.STORE_NAME_ALREADY_EXISTS.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Admin phê duyệt gian hàng APPROVED tự động gán vai trò SELLER cho chủ shop")
    void updateStoreStatus_Approved_ShouldAssignSellerRole() {
        UpdateStoreStatusRequest request = new UpdateStoreStatusRequest("APPROVED");

        Role sellerRole = Role.builder().id((short) 2).name("SELLER").build();

        when(storeRepository.findById(10L)).thenReturn(Optional.of(sampleStore));
        when(roleRepository.findByName("SELLER")).thenReturn(Optional.of(sellerRole));
        when(userRoleRepository.existsByUserIdAndRoleId(1L, (short) 2)).thenReturn(false);
        when(storeRepository.save(any(Store.class))).thenReturn(sampleStore);
        when(storeMapper.toResponse(sampleStore)).thenReturn(StoreResponse.builder().id(10L).status("APPROVED").build());

        StoreResponse response = storeService.updateStoreStatus(10L, request);

        assertEquals("APPROVED", response.getStatus());
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    @DisplayName("Thêm địa chỉ kho cho gian hàng tuân thủ XOR (user = null, store = store)")
    void createStoreAddress_Success_ShouldSatisfyXorCheck() {
        CreateAddressRequest request = CreateAddressRequest.builder()
                .contactName("Kho Tổng Quận 9")
                .phone("0912345678")
                .province("TP.HCM")
                .district("TP. Thủ Đức")
                .ward("Tăng Nhơn Phú A")
                .detailAddress("450 Lê Văn Việt")
                .isDefault(true)
                .build();

        when(storeRepository.findByUserId(1L)).thenReturn(Optional.of(sampleStore));
        when(addressRepository.findByStoreIdAndIsDefaultTrue(10L)).thenReturn(Optional.empty());

        Address savedAddress = Address.builder()
                .id(100L)
                .user(null)
                .store(sampleStore)
                .contactName("Kho Tổng Quận 9")
                .phone("0912345678")
                .province("TP.HCM")
                .district("TP. Thủ Đức")
                .ward("Tăng Nhơn Phú A")
                .detailAddress("450 Lê Văn Việt")
                .isDefault(true)
                .build();
        when(addressRepository.save(any(Address.class))).thenReturn(savedAddress);
        when(addressMapper.toResponse(savedAddress)).thenReturn(AddressResponse.builder().id(100L).contactName("Kho Tổng Quận 9").build());

        AddressResponse response = storeService.createStoreAddress(1L, request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        verify(addressRepository).save(argThat(addr -> addr.getUser() == null && addr.getStore().getId().equals(10L)));
    }

    @Test
    @DisplayName("Xóa địa chỉ kho thất bại khi địa chỉ không thuộc gian hàng của seller (403)")
    void deleteStoreAddress_ShouldThrowException_WhenNotOwner() {
        Store anotherStore = Store.builder().id(999L).build();
        Address anotherStoreAddress = Address.builder().id(200L).store(anotherStore).build();

        when(storeRepository.findByUserId(1L)).thenReturn(Optional.of(sampleStore));
        when(addressRepository.findById(200L)).thenReturn(Optional.of(anotherStoreAddress));

        BusinessException exception = assertThrows(BusinessException.class, () -> storeService.deleteStoreAddress(1L, 200L));
        assertEquals(StoreErrorCode.STORE_ADDRESS_ACCESS_DENIED.getCode(), exception.getErrorCode().getCode());
        verify(addressRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Lấy thông tin gian hàng của tôi thành công")
    void getMyStore_Success() {
        when(storeRepository.findByUserId(1L)).thenReturn(Optional.of(sampleStore));
        when(storeMapper.toResponse(sampleStore)).thenReturn(StoreResponse.builder().id(10L).storeName("Shop Cong Nghe UTC2").build());

        StoreResponse response = storeService.getMyStore(1L);

        assertNotNull(response);
        assertEquals(10L, response.getId());
    }

    @Test
    @DisplayName("Lấy thông tin gian hàng thất bại khi người dùng chưa có gian hàng STORE_NOT_FOUND (404)")
    void getMyStore_ShouldThrowException_WhenStoreNotFound() {
        when(storeRepository.findByUserId(1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> storeService.getMyStore(1L));
        assertEquals(StoreErrorCode.STORE_NOT_FOUND.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Cập nhật thông tin gian hàng thành công")
    void updateMyStore_Success() {
        UpdateStoreRequest request = UpdateStoreRequest.builder()
                .storeName("Shop Updated")
                .description("Mo ta moi")
                .build();

        when(storeRepository.findByUserId(1L)).thenReturn(Optional.of(sampleStore));
        when(storeRepository.existsByStoreName("Shop Updated")).thenReturn(false);
        when(storeRepository.save(any(Store.class))).thenReturn(sampleStore);
        when(storeMapper.toResponse(sampleStore)).thenReturn(StoreResponse.builder().id(10L).storeName("Shop Updated").build());

        StoreResponse response = storeService.updateMyStore(1L, request);

        assertNotNull(response);
        assertEquals("Shop Updated", response.getStoreName());
    }

    @Test
    @DisplayName("Cập nhật gian hàng thất bại khi tên mới bị trùng lặp")
    void updateMyStore_ShouldThrowException_WhenStoreNameExists() {
        UpdateStoreRequest request = UpdateStoreRequest.builder()
                .storeName("Shop Trùng")
                .build();

        when(storeRepository.findByUserId(1L)).thenReturn(Optional.of(sampleStore));
        when(storeRepository.existsByStoreName("Shop Trùng")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> storeService.updateMyStore(1L, request));
        assertEquals(StoreErrorCode.STORE_NAME_ALREADY_EXISTS.getCode(), exception.getErrorCode().getCode());
        verify(storeRepository, never()).save(any(Store.class));
    }

    @Test
    @DisplayName("Xem thông tin gian hàng công khai thành công khi gian hàng đã APPROVED")
    void getStoreById_Success_WhenApproved() {
        sampleStore.setStatus("APPROVED");
        when(storeRepository.findById(10L)).thenReturn(Optional.of(sampleStore));
        when(storeMapper.toResponse(sampleStore)).thenReturn(StoreResponse.builder().id(10L).status("APPROVED").build());

        StoreResponse response = storeService.getStoreById(10L);

        assertNotNull(response);
        assertEquals("APPROVED", response.getStatus());
    }

    @Test
    @DisplayName("Xem thông tin gian hàng công khai thất bại khi gian hàng chưa duyệt STORE_NOT_APPROVED")
    void getStoreById_ShouldThrowException_WhenNotApproved() {
        sampleStore.setStatus("PENDING");
        when(storeRepository.findById(10L)).thenReturn(Optional.of(sampleStore));

        BusinessException exception = assertThrows(BusinessException.class, () -> storeService.getStoreById(10L));
        assertEquals(StoreErrorCode.STORE_NOT_APPROVED.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Admin từ chối REJECTED không gán vai trò SELLER")
    void updateStoreStatus_Rejected_ShouldNotAssignSellerRole() {
        UpdateStoreStatusRequest request = new UpdateStoreStatusRequest("REJECTED");

        when(storeRepository.findById(10L)).thenReturn(Optional.of(sampleStore));
        when(storeRepository.save(any(Store.class))).thenReturn(sampleStore);
        when(storeMapper.toResponse(sampleStore)).thenReturn(StoreResponse.builder().id(10L).status("REJECTED").build());

        StoreResponse response = storeService.updateStoreStatus(10L, request);

        assertEquals("REJECTED", response.getStatus());
        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    @DisplayName("Admin lấy danh sách gian hàng với thông tin tổng hợp thành công")
    void getAdminStores_Success() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<Store> storePage = new org.springframework.data.domain.PageImpl<>(
                java.util.List.of(sampleStore), pageable, 1);

        when(storeRepository.findAdminStores("PENDING", "Coolmate", pageable)).thenReturn(storePage);
        when(imageRepository.findByOwnerTypeAndOwnerIdIn(eq(com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType.STORE), any()))
                .thenReturn(java.util.Collections.emptyList());
        when(productRepository.countProductsByStoreIds(any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{10L, 25L}));
        when(orderRepository.countOrdersByStoreIds(any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{10L, 100L}));
        when(productReviewRepository.avgRatingsByStoreIds(any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{10L, 4.9}));

        var result = storeService.getAdminStores("PENDING", "Coolmate", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getItems().size());
        var item = result.getItems().get(0);
        assertEquals("Shop Cong Nghe UTC2", item.getStoreName());
        assertEquals("Shop Cong Nghe UTC2", item.getName());
        assertEquals(25L, item.getProductsCount());
        assertEquals(100L, item.getOrdersCount());
        assertEquals(4.9, item.getRatingAvg());
    }
}
