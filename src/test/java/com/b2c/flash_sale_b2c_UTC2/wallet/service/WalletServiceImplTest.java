package com.b2c.flash_sale_b2c_UTC2.wallet.service;

import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.AdminWithdrawalResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.ApproveWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.RejectWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerBankAccountRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerBankAccountResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerWalletResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.WalletTransactionResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.StoreBankAccount;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.Wallet;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.WalletTransaction;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.WalletWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.exception.WalletErrorCode;
import com.b2c.flash_sale_b2c_UTC2.wallet.mapper.WalletTransactionMapper;
import com.b2c.flash_sale_b2c_UTC2.wallet.repository.StoreBankAccountRepository;
import com.b2c.flash_sale_b2c_UTC2.wallet.repository.WalletRepository;
import com.b2c.flash_sale_b2c_UTC2.wallet.repository.WalletTransactionRepository;
import com.b2c.flash_sale_b2c_UTC2.wallet.repository.WalletWithdrawalRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private StoreBankAccountRepository storeBankAccountRepository;

    @Mock
    private WalletWithdrawalRequestRepository walletWithdrawalRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletTransactionMapper walletTransactionMapper;

    @InjectMocks
    private WalletServiceImpl walletService;

    private User testUser;
    private Store testStore;
    private Wallet testWallet;
    private StoreBankAccount testBankAccount;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(10L).fullName("Nguyễn Văn A").email("seller@test.com").build();
        testStore = Store.builder().id(100L).user(testUser).storeName("Vibe Store").build();
        testWallet = Wallet.builder()
                .id(1L)
                .store(testStore)
                .balance(new BigDecimal("5000000.00"))
                .frozenBalance(new BigDecimal("1000000.00"))
                .updatedAt(Instant.now())
                .build();

        testBankAccount = StoreBankAccount.builder()
                .id(1L)
                .store(testStore)
                .bankName("Vietcombank")
                .bankCode("VCB")
                .accountNumber("102988391204")
                .accountHolder("NGUYEN VAN A")
                .branch("Chi nhánh Thủ Đức")
                .isVerified(true)
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("getSellerWallet - Thành công trả về thông tin ví và tài khoản ngân hàng")
    void getSellerWallet_Success() {
        when(storeRepository.findByUserId(10L)).thenReturn(Optional.of(testStore));
        when(walletRepository.findByStoreId(100L)).thenReturn(Optional.of(testWallet));
        when(walletTransactionRepository.sumWithdrawnByWalletId(1L)).thenReturn(new BigDecimal("2000000.00"));
        when(storeBankAccountRepository.findByStoreId(100L)).thenReturn(Optional.of(testBankAccount));

        SellerWalletResponse response = walletService.getSellerWallet(10L);

        assertNotNull(response);
        assertEquals(new BigDecimal("5000000.00"), response.getBalance());
        assertEquals(new BigDecimal("1000000.00"), response.getPendingBalance());
        assertEquals(new BigDecimal("2000000.00"), response.getTotalWithdrawn());
        assertNotNull(response.getBankAccount());
        assertEquals("Vietcombank", response.getBankAccount().getBankName());
        assertEquals("NGUYEN VAN A", response.getBankAccount().getAccountHolder());
    }

    @Test
    @DisplayName("getSellerBankAccount - Thành công trả về tài khoản ngân hàng")
    void getSellerBankAccount_Success() {
        when(storeRepository.findByUserId(10L)).thenReturn(Optional.of(testStore));
        when(storeBankAccountRepository.findByStoreId(100L)).thenReturn(Optional.of(testBankAccount));

        SellerBankAccountResponse response = walletService.getSellerBankAccount(10L);

        assertNotNull(response);
        assertEquals("VCB", response.getBankCode());
        assertEquals("102988391204", response.getAccountNumber());
    }

    @Test
    @DisplayName("updateSellerBankAccount - Cập nhật hoặc thêm mới tài khoản ngân hàng")
    void updateSellerBankAccount_Success() {
        SellerBankAccountRequest request = SellerBankAccountRequest.builder()
                .bankName("Techcombank")
                .bankCode("TCB")
                .accountNumber("1903829102")
                .accountHolder("NGUYEN VAN A")
                .branch("Chi nhánh Tân Bình")
                .build();

        when(storeRepository.findByUserId(10L)).thenReturn(Optional.of(testStore));
        when(storeBankAccountRepository.findByStoreId(100L)).thenReturn(Optional.of(testBankAccount));
        when(storeBankAccountRepository.save(any(StoreBankAccount.class))).thenAnswer(i -> i.getArgument(0));

        SellerBankAccountResponse response = walletService.updateSellerBankAccount(10L, request);

        assertNotNull(response);
        assertEquals("TCB", response.getBankCode());
        assertEquals("1903829102", response.getAccountNumber());
        verify(storeBankAccountRepository).save(any(StoreBankAccount.class));
    }

    @Test
    @DisplayName("getSellerWallet - Ném BusinessException khi không tìm thấy Store")
    void getSellerWallet_StoreNotFound() {
        when(storeRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> walletService.getSellerWallet(99L));
    }

    @Test
    @DisplayName("getSellerTransactions - Lấy danh sách giao dịch có phân trang")
    void getSellerTransactions_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        WalletTransaction tx = WalletTransaction.builder()
                .id(50L)
                .wallet(testWallet)
                .transactionType("WITHDRAWAL")
                .amount(new BigDecimal("-200000.00"))
                .balanceAfter(new BigDecimal("4800000.00"))
                .build();

        WalletTransactionResponse txResponse = WalletTransactionResponse.builder()
                .id(50L)
                .type("WITHDRAWAL")
                .amount(new BigDecimal("-200000.00"))
                .balanceAfter(new BigDecimal("4800000.00"))
                .build();

        when(storeRepository.findByUserId(10L)).thenReturn(Optional.of(testStore));
        when(walletRepository.findByStoreId(100L)).thenReturn(Optional.of(testWallet));
        when(walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(tx), pageable, 1));
        when(walletTransactionMapper.toResponse(tx)).thenReturn(txResponse);

        PageResponse<WalletTransactionResponse> result = walletService.getSellerTransactions(10L, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(50L, result.getItems().get(0).getId());
    }

    @Test
    @DisplayName("requestSellerWithdrawal - Thành công trừ số dư khả dụng, tăng số dư đóng băng và lưu yêu cầu rút")
    void requestSellerWithdrawal_Success() {
        SellerWithdrawalRequest request = SellerWithdrawalRequest.builder()
                .amount(new BigDecimal("500000.00"))
                .note("Rút tiền tuần 1")
                .build();

        when(storeRepository.findByUserId(10L)).thenReturn(Optional.of(testStore));
        when(storeBankAccountRepository.findByStoreId(100L)).thenReturn(Optional.of(testBankAccount));
        when(walletRepository.findByStoreIdWithLock(100L)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WalletTransaction savedTx = WalletTransaction.builder()
                .id(88L)
                .wallet(testWallet)
                .transactionType("WITHDRAWAL_REQUEST")
                .amount(new BigDecimal("-500000.00"))
                .balanceAfter(new BigDecimal("4500000.00"))
                .description("Rút tiền tuần 1")
                .build();

        when(walletTransactionRepository.save(any(WalletTransaction.class))).thenReturn(savedTx);
        when(walletTransactionMapper.toResponse(any(WalletTransaction.class))).thenReturn(
                WalletTransactionResponse.builder().id(88L).amount(new BigDecimal("-500000.00")).build()
        );

        WalletTransactionResponse response = walletService.requestSellerWithdrawal(10L, request);

        assertNotNull(response);
        assertEquals(88L, response.getId());
        assertEquals(new BigDecimal("4500000.00"), testWallet.getBalance());
        assertEquals(new BigDecimal("1500000.00"), testWallet.getFrozenBalance());
        verify(walletRepository).save(testWallet);
        verify(walletWithdrawalRequestRepository).save(any(WalletWithdrawalRequest.class));
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
    }

    @Test
    @DisplayName("requestSellerWithdrawal - Ném ngoại lệ khi chưa cấu hình tài khoản ngân hàng")
    void requestSellerWithdrawal_NoBankConfigured() {
        SellerWithdrawalRequest request = SellerWithdrawalRequest.builder()
                .amount(new BigDecimal("100000.00"))
                .build();

        when(storeRepository.findByUserId(10L)).thenReturn(Optional.of(testStore));
        when(storeBankAccountRepository.findByStoreId(100L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.requestSellerWithdrawal(10L, request));
        assertEquals(WalletErrorCode.WALLET_BANK_NOT_CONFIGURED, ex.getErrorCode());
    }

    @Test
    @DisplayName("requestSellerWithdrawal - Ném ngoại lệ khi số tiền nhỏ hơn 50,000 VND")
    void requestSellerWithdrawal_BelowMinimum() {
        SellerWithdrawalRequest request = SellerWithdrawalRequest.builder()
                .amount(new BigDecimal("40000.00"))
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.requestSellerWithdrawal(10L, request));
        assertEquals(WalletErrorCode.MINIMUM_WITHDRAWAL_AMOUNT, ex.getErrorCode());
    }

    @Test
    @DisplayName("requestSellerWithdrawal - Ném ngoại lệ khi số dư không đủ")
    void requestSellerWithdrawal_InsufficientBalance() {
        SellerWithdrawalRequest request = SellerWithdrawalRequest.builder()
                .amount(new BigDecimal("10000000.00"))
                .build();

        when(storeRepository.findByUserId(10L)).thenReturn(Optional.of(testStore));
        when(storeBankAccountRepository.findByStoreId(100L)).thenReturn(Optional.of(testBankAccount));
        when(walletRepository.findByStoreIdWithLock(100L)).thenReturn(Optional.of(testWallet));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.requestSellerWithdrawal(10L, request));
        assertEquals(WalletErrorCode.WALLET_INSUFFICIENT, ex.getErrorCode());
    }

    @Test
    @DisplayName("approveWithdrawal - Admin phê duyệt thành công, trừ số dư đóng băng")
    void approveWithdrawal_Success() {
        WalletWithdrawalRequest request = WalletWithdrawalRequest.builder()
                .id(1L)
                .requestCode("WDR-261018-0001")
                .wallet(testWallet)
                .store(testStore)
                .amount(new BigDecimal("500000.00"))
                .status("PENDING")
                .build();

        User admin = User.builder().id(999L).fullName("Admin").build();

        when(walletWithdrawalRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testWallet));
        when(userRepository.findById(999L)).thenReturn(Optional.of(admin));
        when(walletWithdrawalRequestRepository.save(any(WalletWithdrawalRequest.class))).thenAnswer(i -> i.getArgument(0));

        AdminWithdrawalResponse response = walletService.approveWithdrawal(1L, 999L,
                ApproveWithdrawalRequest.builder().adminNote("Đã chuyển khoản").build());

        assertNotNull(response);
        assertEquals("APPROVED", response.getStatus());
        assertEquals(new BigDecimal("500000.00"), testWallet.getFrozenBalance());
        verify(walletRepository).save(testWallet);
    }

    @Test
    @DisplayName("rejectWithdrawal - Admin từ chối thành công, hoàn lại số dư khả dụng")
    void rejectWithdrawal_Success() {
        WalletWithdrawalRequest request = WalletWithdrawalRequest.builder()
                .id(1L)
                .requestCode("WDR-261018-0001")
                .wallet(testWallet)
                .store(testStore)
                .amount(new BigDecimal("500000.00"))
                .status("PENDING")
                .build();

        User admin = User.builder().id(999L).fullName("Admin").build();

        when(walletWithdrawalRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testWallet));
        when(userRepository.findById(999L)).thenReturn(Optional.of(admin));
        when(walletWithdrawalRequestRepository.save(any(WalletWithdrawalRequest.class))).thenAnswer(i -> i.getArgument(0));

        AdminWithdrawalResponse response = walletService.rejectWithdrawal(1L, 999L,
                RejectWithdrawalRequest.builder().reason("Sai số tài khoản").build());

        assertNotNull(response);
        assertEquals("REJECTED", response.getStatus());
        assertEquals(new BigDecimal("500000.00"), testWallet.getFrozenBalance());
        assertEquals(new BigDecimal("5500000.00"), testWallet.getBalance());
        verify(walletRepository).save(testWallet);
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
    }
}
