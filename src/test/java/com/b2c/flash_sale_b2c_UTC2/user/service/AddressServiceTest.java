package com.b2c.flash_sale_b2c_UTC2.user.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.user.dto.AddressResponse;
import com.b2c.flash_sale_b2c_UTC2.user.dto.CreateAddressRequest;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Address;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.exception.UserErrorCode;
import com.b2c.flash_sale_b2c_UTC2.user.mapper.AddressMapper;
import com.b2c.flash_sale_b2c_UTC2.user.repository.AddressRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressMapper addressMapper;

    @InjectMocks
    private AddressServiceImpl addressService;

    private User user1;
    private User user2;
    private Address addressUser1;

    @BeforeEach
    void setUp() {
        user1 = User.builder().id(1L).email("user1@example.com").fullName("User One").build();
        user2 = User.builder().id(2L).email("user2@example.com").fullName("User Two").build();

        addressUser1 = Address.builder()
                .id(100L)
                .user(user1)
                .contactName("User One")
                .phone("0987654321")
                .province("TP.HCM")
                .district("Thủ Đức")
                .ward("Tăng Nhơn Phú A")
                .detailAddress("450 Lê Văn Việt")
                .isDefault(true)
                .build();
    }

    @Test
    @DisplayName("Tạo địa chỉ đầu tiên của người dùng sẽ tự động đặt làm mặc định")
    void createAddress_FirstAddress_ShouldBeDefaultAutomatically() {
        CreateAddressRequest request = CreateAddressRequest.builder()
                .contactName("User One")
                .phone("0987654321")
                .province("TP.HCM")
                .district("Thủ Đức")
                .ward("Tăng Nhơn Phú A")
                .detailAddress("450 Lê Văn Việt")
                .isDefault(false)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(addressRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(addressMapper.toResponse(any(Address.class))).thenReturn(AddressResponse.builder().id(100L).isDefault(true).build());

        AddressResponse response = addressService.createAddress(1L, request);

        assertNotNull(response);
        assertTrue(response.getIsDefault());
        verify(addressRepository).save(argThat(Address::getIsDefault));
    }

    @Test
    @DisplayName("Xem chi tiết địa chỉ thành công khi là chủ sở hữu")
    void getAddressById_Success_WhenOwnerMatches() {
        when(addressRepository.findById(100L)).thenReturn(Optional.of(addressUser1));
        when(addressMapper.toResponse(addressUser1)).thenReturn(AddressResponse.builder().id(100L).build());

        AddressResponse response = addressService.getAddressById(1L, 100L);

        assertNotNull(response);
        assertEquals(100L, response.getId());
    }

    @Test
    @DisplayName("Truy cập địa chỉ của người khác bị từ chối với ADDRESS_ACCESS_DENIED (403)")
    void getAddressById_ShouldThrowAccessDenied_WhenUserDoesNotOwnAddress() {
        when(addressRepository.findById(100L)).thenReturn(Optional.of(addressUser1));

        // User 2 cố ý truy cập địa chỉ của User 1
        BusinessException exception = assertThrows(BusinessException.class,
                () -> addressService.getAddressById(2L, 100L));

        assertEquals(UserErrorCode.ADDRESS_ACCESS_DENIED.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Xóa địa chỉ thành công khi là chủ sở hữu")
    void deleteAddress_Success() {
        when(addressRepository.findById(100L)).thenReturn(Optional.of(addressUser1));
        when(addressRepository.findByUserId(1L)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> addressService.deleteAddress(1L, 100L));
        verify(addressRepository).delete(addressUser1);
    }

    @Test
    @DisplayName("Xóa địa chỉ của người khác bị từ chối với ADDRESS_ACCESS_DENIED (403)")
    void deleteAddress_ShouldThrowAccessDenied_WhenUserDoesNotOwnAddress() {
        when(addressRepository.findById(100L)).thenReturn(Optional.of(addressUser1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> addressService.deleteAddress(2L, 100L));

        assertEquals(UserErrorCode.ADDRESS_ACCESS_DENIED.getCode(), exception.getErrorCode().getCode());
        verify(addressRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Lấy danh sách địa chỉ của người dùng thành công")
    void getUserAddresses_Success() {
        when(addressRepository.findByUserId(1L)).thenReturn(new java.util.ArrayList<>(List.of(addressUser1)));
        when(addressMapper.toResponseList(any())).thenReturn(List.of(AddressResponse.builder().id(100L).build()));

        List<AddressResponse> responses = addressService.getUserAddresses(1L);

        assertNotNull(responses);
        assertEquals(1, responses.size());
    }

    @Test
    @DisplayName("Cập nhật địa chỉ thành công khi là chủ sở hữu")
    void updateAddress_Success() {
        com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateAddressRequest request = com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateAddressRequest.builder()
                .contactName("User Updated")
                .phone("0999999999")
                .isDefault(false)
                .build();

        when(addressRepository.findById(100L)).thenReturn(Optional.of(addressUser1));
        when(addressRepository.save(any(Address.class))).thenReturn(addressUser1);
        when(addressMapper.toResponse(any(Address.class))).thenReturn(AddressResponse.builder().id(100L).contactName("User Updated").build());

        AddressResponse response = addressService.updateAddress(1L, 100L, request);

        assertNotNull(response);
        assertEquals("User Updated", response.getContactName());
    }

    @Test
    @DisplayName("Thiết lập địa chỉ mặc định thành công")
    void setDefaultAddress_Success() {
        addressUser1.setIsDefault(false);
        when(addressRepository.findById(100L)).thenReturn(Optional.of(addressUser1));
        when(addressRepository.findByUserIdAndIsDefaultTrue(1L)).thenReturn(Optional.empty());
        when(addressRepository.save(any(Address.class))).thenReturn(addressUser1);
        when(addressMapper.toResponse(any(Address.class))).thenReturn(AddressResponse.builder().id(100L).isDefault(true).build());

        AddressResponse response = addressService.setDefaultAddress(1L, 100L);

        assertNotNull(response);
        assertTrue(response.getIsDefault());
    }
}
