package com.b2c.flash_sale_b2c_UTC2.voucher.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.exception.StoreErrorCode;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.ApplyVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.CreateVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherCalculationResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.Voucher;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.VoucherUsage;
import com.b2c.flash_sale_b2c_UTC2.voucher.exception.VoucherErrorCode;
import com.b2c.flash_sale_b2c_UTC2.voucher.mapper.VoucherMapper;
import com.b2c.flash_sale_b2c_UTC2.voucher.repository.VoucherRepository;
import com.b2c.flash_sale_b2c_UTC2.voucher.repository.VoucherUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private VoucherUsageRepository voucherUsageRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private VoucherMapper voucherMapper;

    @Mock
    private com.b2c.flash_sale_b2c_UTC2.voucher.repository.UserVoucherRepository userVoucherRepository;

    @Mock
    private com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository userRepository;

    @InjectMocks
    private VoucherServiceImpl voucherService;

    private User sampleUser;
    private Store sampleStore;
    private Voucher sampleVoucher;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder().id(1L).email("user@example.com").build();
        sampleStore = Store.builder().id(10L).user(sampleUser).storeName("Shop UTC2").build();

        sampleVoucher = Voucher.builder()
                .id(100L)
                .code("UTC2DISCOUNT")
                .store(sampleStore)
                .discountType("FIXED_AMOUNT")
                .discountValue(new BigDecimal("50000.00"))
                .minOrderAmount(new BigDecimal("200000.00"))
                .totalQuantity(10)
                .usedQuantity(0)
                .userUsageLimit(1)
                .startTime(Instant.now().minus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(7, ChronoUnit.DAYS))
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("Tạo voucher thành công với mã hợp lệ")
    void createVoucher_Success() {
        CreateVoucherRequest request = CreateVoucherRequest.builder()
                .code("UTC2DISCOUNT")
                .storeId(10L)
                .discountType("FIXED_AMOUNT")
                .discountValue(new BigDecimal("50000.00"))
                .minOrderAmount(new BigDecimal("200000.00"))
                .totalQuantity(10)
                .startTime(Instant.now().minus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        when(voucherRepository.existsByCode("UTC2DISCOUNT")).thenReturn(false);
        when(storeRepository.findById(10L)).thenReturn(Optional.of(sampleStore));
        when(voucherRepository.save(any(Voucher.class))).thenReturn(sampleVoucher);
        when(voucherMapper.toResponse(sampleVoucher)).thenReturn(VoucherResponse.builder().id(100L).code("UTC2DISCOUNT").build());

        VoucherResponse response = voucherService.createVoucher(1L, request);

        assertNotNull(response);
        assertEquals("UTC2DISCOUNT", response.getCode());
        verify(voucherRepository).save(any(Voucher.class));
    }

    @Test
    @DisplayName("Tạo voucher ném lỗi khi mã đã tồn tại")
    void createVoucher_ShouldThrowException_WhenCodeExists() {
        CreateVoucherRequest request = CreateVoucherRequest.builder().code("UTC2DISCOUNT").build();
        when(voucherRepository.existsByCode("UTC2DISCOUNT")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> voucherService.createVoucher(1L, request));
        assertEquals(VoucherErrorCode.VOUCHER_CODE_EXISTS.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Áp dụng voucher giảm giá cố định thành công")
    void validateAndCalculate_FixedAmount_Success() {
        ApplyVoucherRequest request = ApplyVoucherRequest.builder()
                .code("UTC2DISCOUNT")
                .storeId(10L)
                .subtotalAmount(new BigDecimal("300000.00"))
                .build();

        when(voucherRepository.findByCode("UTC2DISCOUNT")).thenReturn(Optional.of(sampleVoucher));
        when(voucherUsageRepository.countByVoucherIdAndUserId(100L, 1L)).thenReturn(0L);

        VoucherCalculationResponse response = voucherService.validateAndCalculate(1L, request);

        assertNotNull(response);
        assertEquals(new BigDecimal("50000.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("250000.00"), response.getFinalAmount());
    }

    @Test
    @DisplayName("Áp dụng voucher phần trăm áp dụng mức giảm tối đa maxDiscountAmount")
    void validateAndCalculate_PercentDiscount_WithMaxDiscount_Success() {
        Voucher percentVoucher = Voucher.builder()
                .id(101L)
                .code("SALE20")
                .discountType("PERCENT")
                .discountValue(new BigDecimal("20.00")) // 20%
                .maxDiscountAmount(new BigDecimal("30000.00")) // Giảm tối đa 30k
                .totalQuantity(10)
                .usedQuantity(0)
                .userUsageLimit(1)
                .startTime(Instant.now().minus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(7, ChronoUnit.DAYS))
                .status("ACTIVE")
                .build();

        ApplyVoucherRequest request = ApplyVoucherRequest.builder()
                .code("SALE20")
                .subtotalAmount(new BigDecimal("200000.00")) // 20% của 200k = 40k -> Giới hạn max 30k
                .build();

        when(voucherRepository.findByCode("SALE20")).thenReturn(Optional.of(percentVoucher));

        VoucherCalculationResponse response = voucherService.validateAndCalculate(1L, request);

        assertNotNull(response);
        assertEquals(new BigDecimal("30000.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("170000.00"), response.getFinalAmount());
    }

    @Test
    @DisplayName("Áp dụng voucher ném lỗi khi đã hết hạn")
    void validateVoucher_ShouldThrowException_WhenExpired() {
        Voucher expiredVoucher = Voucher.builder()
                .id(102L)
                .code("EXPIRED")
                .startTime(Instant.now().minus(10, ChronoUnit.DAYS))
                .endTime(Instant.now().minus(1, ChronoUnit.DAYS))
                .status("ACTIVE")
                .build();

        when(voucherRepository.findByCode("EXPIRED")).thenReturn(Optional.of(expiredVoucher));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                voucherService.validateVoucherForOrder("EXPIRED", 1L, 10L, new BigDecimal("100000.00")));
        assertEquals(VoucherErrorCode.VOUCHER_EXPIRED.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Áp dụng voucher ném lỗi khi gian hàng không khớp (VOUCHER_STORE_MISMATCH)")
    void validateVoucher_ShouldThrowException_WhenStoreMismatch() {
        when(voucherRepository.findByCode("UTC2DISCOUNT")).thenReturn(Optional.of(sampleVoucher));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                voucherService.validateVoucherForOrder("UTC2DISCOUNT", 1L, 999L, new BigDecimal("300000.00"))); // storeId khác 10L
        assertEquals(VoucherErrorCode.VOUCHER_STORE_MISMATCH.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Bồi hoàn voucher thành công: giảm used_quantity và xóa bản ghi voucher_usages")
    void rollbackVoucherUsage_Success() {
        Voucher voucher = Voucher.builder()
                .id(1L)
                .code("UTC2DISCOUNT")
                .usedQuantity(3)
                .build();

        VoucherUsage usage = VoucherUsage.builder()
                .id(100L)
                .voucher(voucher)
                .discountAmount(new BigDecimal("50000.00"))
                .build();

        when(voucherUsageRepository.findByOrderId(1000L)).thenReturn(Optional.of(usage));

        voucherService.rollbackVoucherUsage(1000L);

        assertEquals(2, voucher.getUsedQuantity());
        verify(voucherRepository).save(voucher);
        verify(voucherUsageRepository).delete(usage);
    }

    @Test
    @DisplayName("Bồi hoàn voucher không ném lỗi khi không tìm thấy voucher_usages")
    void rollbackVoucherUsage_NoUsageFound_ShouldDoNothing() {
        when(voucherUsageRepository.findByOrderId(9999L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> voucherService.rollbackVoucherUsage(9999L));
        verify(voucherRepository, never()).save(any());
        verify(voucherUsageRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Bồi hoàn voucher không ném lỗi khi orderId là null")
    void rollbackVoucherUsage_NullOrderId_ShouldDoNothing() {
        assertDoesNotThrow(() -> voucherService.rollbackVoucherUsage(null));
        verify(voucherUsageRepository, never()).findByOrderId(any());
    }

    @Test
    @DisplayName("Áp dụng voucher ném lỗi khi voucher ở trạng thái không ACTIVE (VOUCHER_INACTIVE)")
    void validateVoucher_ThrowsException_WhenInactive() {
        Voucher inactiveVoucher = Voucher.builder().id(103L).code("INACTIVE_VOUCHER").status("INACTIVE").build();
        when(voucherRepository.findByCode("INACTIVE_VOUCHER")).thenReturn(Optional.of(inactiveVoucher));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                voucherService.validateVoucherForOrder("INACTIVE_VOUCHER", 1L, 10L, new BigDecimal("300000.00")));
        assertEquals(VoucherErrorCode.VOUCHER_INACTIVE.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Áp dụng voucher ném lỗi khi chưa tới thời gian bắt đầu (VOUCHER_NOT_STARTED)")
    void validateVoucher_ThrowsException_WhenNotStarted() {
        Voucher notStartedVoucher = Voucher.builder()
                .id(104L)
                .code("FUTURE_VOUCHER")
                .status("ACTIVE")
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(10, ChronoUnit.DAYS))
                .build();
        when(voucherRepository.findByCode("FUTURE_VOUCHER")).thenReturn(Optional.of(notStartedVoucher));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                voucherService.validateVoucherForOrder("FUTURE_VOUCHER", 1L, 10L, new BigDecimal("300000.00")));
        assertEquals(VoucherErrorCode.VOUCHER_NOT_STARTED.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Áp dụng voucher ném lỗi khi đã hết lượt dùng toàn sàn (VOUCHER_OUT_OF_STOCK)")
    void validateVoucher_ThrowsException_WhenOutOfStock() {
        Voucher outOfStockVoucher = Voucher.builder()
                .id(105L)
                .code("OUT_OF_STOCK")
                .status("ACTIVE")
                .startTime(Instant.now().minus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(5, ChronoUnit.DAYS))
                .totalQuantity(10)
                .usedQuantity(10) // Hết lượt
                .build();
        when(voucherRepository.findByCode("OUT_OF_STOCK")).thenReturn(Optional.of(outOfStockVoucher));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                voucherService.validateVoucherForOrder("OUT_OF_STOCK", 1L, 10L, new BigDecimal("300000.00")));
        assertEquals(VoucherErrorCode.VOUCHER_OUT_OF_STOCK.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Áp dụng voucher ném lỗi khi đơn chưa đạt giá trị tối thiểu (VOUCHER_MIN_AMOUNT_NOT_MET)")
    void validateVoucher_ThrowsException_WhenMinAmountNotMet() {
        when(voucherRepository.findByCode("UTC2DISCOUNT")).thenReturn(Optional.of(sampleVoucher));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                voucherService.validateVoucherForOrder("UTC2DISCOUNT", 1L, 10L, new BigDecimal("100000.00"))); // Min là 200k
        assertEquals(VoucherErrorCode.VOUCHER_MIN_AMOUNT_NOT_MET.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Áp dụng voucher ném lỗi khi tài khoản đã dùng hết hạn mức cá nhân (VOUCHER_USER_LIMIT_EXCEEDED)")
    void validateVoucher_ThrowsException_WhenUserLimitExceeded() {
        when(voucherRepository.findByCode("UTC2DISCOUNT")).thenReturn(Optional.of(sampleVoucher));
        when(voucherUsageRepository.countByVoucherIdAndUserId(100L, 1L)).thenReturn(1L); // Đã dùng 1 lần, max là 1

        BusinessException exception = assertThrows(BusinessException.class, () ->
                voucherService.validateVoucherForOrder("UTC2DISCOUNT", 1L, 10L, new BigDecimal("300000.00")));
        assertEquals(VoucherErrorCode.VOUCHER_USER_LIMIT_EXCEEDED.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Lấy danh sách voucher toàn sàn thành công")
    void getPlatformVouchers_Success() {
        when(voucherRepository.findByStoreIdIsNull()).thenReturn(List.of(sampleVoucher));
        when(voucherMapper.toResponse(sampleVoucher)).thenReturn(VoucherResponse.builder().id(100L).code("UTC2DISCOUNT").build());

        List<VoucherResponse> responses = voucherService.getPlatformVouchers();

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("UTC2DISCOUNT", responses.get(0).getCode());
    }

    @Test
    @DisplayName("Lấy danh sách voucher của gian hàng thành công")
    void getStoreVouchers_Success() {
        when(voucherRepository.findByStoreId(10L)).thenReturn(List.of(sampleVoucher));
        when(voucherMapper.toResponse(sampleVoucher)).thenReturn(VoucherResponse.builder().id(100L).code("UTC2DISCOUNT").build());

        List<VoucherResponse> responses = voucherService.getStoreVouchers(10L);

        assertNotNull(responses);
        assertEquals(1, responses.size());
    }

    @Test
    @DisplayName("Seller lấy danh sách voucher của gian hàng mình thành công")
    void getMyStoreVouchers_Success() {
        when(storeRepository.findByUserId(1L)).thenReturn(Optional.of(sampleStore));
        when(voucherRepository.findByStoreId(10L)).thenReturn(List.of(sampleVoucher));
        when(voucherMapper.toResponse(sampleVoucher)).thenReturn(VoucherResponse.builder().id(100L).code("UTC2DISCOUNT").build());

        List<VoucherResponse> responses = voucherService.getMyStoreVouchers(1L);

        assertNotNull(responses);
        assertEquals(1, responses.size());
    }

    @Test
    @DisplayName("Lưu voucher vào ví thành công")
    void saveVoucherToWallet_Success() {
        when(voucherRepository.findByCodeIgnoreCase("UTC2DISCOUNT")).thenReturn(Optional.of(sampleVoucher));
        when(userVoucherRepository.existsByUserIdAndVoucherId(1L, sampleVoucher.getId())).thenReturn(false);
        when(voucherUsageRepository.countByVoucherIdAndUserId(sampleVoucher.getId(), 1L)).thenReturn(0L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        com.b2c.flash_sale_b2c_UTC2.voucher.dto.SaveVoucherRequest request =
                com.b2c.flash_sale_b2c_UTC2.voucher.dto.SaveVoucherRequest.builder().code("UTC2DISCOUNT").build();

        com.b2c.flash_sale_b2c_UTC2.voucher.dto.UserVoucherResponse response = voucherService.saveVoucherToWallet(1L, request);

        assertNotNull(response);
        assertEquals("UTC2DISCOUNT", response.getCode());
        assertEquals("ACTIVE", response.getStatus());
        verify(userVoucherRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Lưu voucher thất bại khi voucher đã được lưu trước đó")
    void saveVoucherToWallet_AlreadySaved_ThrowsConflict() {
        when(voucherRepository.findByCodeIgnoreCase("UTC2DISCOUNT")).thenReturn(Optional.of(sampleVoucher));
        when(userVoucherRepository.existsByUserIdAndVoucherId(1L, sampleVoucher.getId())).thenReturn(true);

        com.b2c.flash_sale_b2c_UTC2.voucher.dto.SaveVoucherRequest request =
                com.b2c.flash_sale_b2c_UTC2.voucher.dto.SaveVoucherRequest.builder().code("UTC2DISCOUNT").build();

        BusinessException ex = assertThrows(BusinessException.class, () -> voucherService.saveVoucherToWallet(1L, request));
        assertEquals(VoucherErrorCode.VOUCHER_ALREADY_SAVED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Lưu voucher thất bại khi đã hết lượt dùng của user")
    void saveVoucherToWallet_UserLimitExceeded_ThrowsBadRequest() {
        when(voucherRepository.findByCodeIgnoreCase("UTC2DISCOUNT")).thenReturn(Optional.of(sampleVoucher));
        when(userVoucherRepository.existsByUserIdAndVoucherId(1L, sampleVoucher.getId())).thenReturn(false);
        when(voucherUsageRepository.countByVoucherIdAndUserId(sampleVoucher.getId(), 1L)).thenReturn(1L);

        com.b2c.flash_sale_b2c_UTC2.voucher.dto.SaveVoucherRequest request =
                com.b2c.flash_sale_b2c_UTC2.voucher.dto.SaveVoucherRequest.builder().code("UTC2DISCOUNT").build();

        BusinessException ex = assertThrows(BusinessException.class, () -> voucherService.saveVoucherToWallet(1L, request));
        assertEquals(VoucherErrorCode.VOUCHER_USER_LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Lấy danh sách voucher trong ví thành công")
    void getMyVouchers_Success() {
        com.b2c.flash_sale_b2c_UTC2.voucher.entity.UserVoucher uv =
                com.b2c.flash_sale_b2c_UTC2.voucher.entity.UserVoucher.builder()
                        .id(1L)
                        .user(sampleUser)
                        .voucher(sampleVoucher)
                        .savedAt(Instant.now())
                        .build();

        when(userVoucherRepository.findAllByUserIdWithVoucherAndStore(1L)).thenReturn(List.of(uv));
        when(voucherUsageRepository.countByUserIdAndVoucherIdIn(eq(1L), anyList())).thenReturn(List.<Object[]>of(new Object[]{sampleVoucher.getId(), 0L}));

        List<com.b2c.flash_sale_b2c_UTC2.voucher.dto.UserVoucherResponse> responses = voucherService.getMyVouchers(1L);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("UTC2DISCOUNT", responses.get(0).getCode());
        assertEquals("ACTIVE", responses.get(0).getStatus());
    }
}

