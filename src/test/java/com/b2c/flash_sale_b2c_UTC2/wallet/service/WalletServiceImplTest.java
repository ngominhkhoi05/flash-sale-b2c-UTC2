package com.b2c.flash_sale_b2c_UTC2.wallet.service;

import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerWalletResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.WalletTransactionResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.Wallet;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.WalletTransaction;
import com.b2c.flash_sale_b2c_UTC2.wallet.exception.WalletErrorCode;
import com.b2c.flash_sale_b2c_UTC2.wallet.mapper.WalletTransactionMapper;
import com.b2c.flash_sale_b2c_UTC2.wallet.repository.WalletRepository;
import com.b2c.flash_sale_b2c_UTC2.wallet.repository.WalletTransactionRepository;
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
    private WalletTransactionMapper walletTransactionMapper;

    @InjectMocks
    private WalletServiceImpl walletService;

    private User testUser;
    private Store testStore;
    private Wallet testWallet;

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
    }

    @Test
    @DisplayName("getSellerWallet - Thành công trả về thông tin ví và tài khoản ngân hàng")
    void getSellerWallet_Success() {
        when(storeRepository.findByUserId(10L)).thenReturn(Optional.of(testStore));
        when(walletRepository.findByStoreId(100L)).thenReturn(Optional.of(testWallet));
        when(walletTransactionRepository.sumWithdrawnByWalletId(1L)).thenReturn(new BigDecimal("2000000.00"));

        SellerWalletResponse response = walletService.getSellerWallet(10L);

        assertNotNull(response);
        assertEquals(new BigDecimal("5000000.00"), response.getBalance());
        assertEquals(new BigDecimal("1000000.00"), response.getPendingBalance());
        assertEquals(new BigDecimal("2000000.00"), response.getTotalWithdrawn());
        assertNotNull(response.getBankAccount());
        assertEquals("MB Bank", response.getBankAccount().getBankName());
        assertEquals("NGUYỄN VĂN A", response.getBankAccount().getAccountHolder());
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
    @DisplayName("requestSellerWithdrawal - Thành công trừ số dư và lưu giao dịch WITHDRAWAL")
    void requestSellerWithdrawal_Success() {
        SellerWithdrawalRequest request = SellerWithdrawalRequest.builder()
                .amount(new BigDecimal("500000.00"))
                .bankAccountNumber("123456789")
                .note("Rút tiền tuần 1")
                .build();

        when(storeRepository.findByUserId(10L)).thenReturn(Optional.of(testStore));
        when(walletRepository.findByStoreIdWithLock(100L)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WalletTransaction savedTx = WalletTransaction.builder()
                .id(88L)
                .wallet(testWallet)
                .transactionType("WITHDRAWAL")
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
        verify(walletRepository).save(testWallet);
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
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
        when(walletRepository.findByStoreIdWithLock(100L)).thenReturn(Optional.of(testWallet));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.requestSellerWithdrawal(10L, request));
        assertEquals(WalletErrorCode.WALLET_INSUFFICIENT, ex.getErrorCode());
    }

    @Test
    @DisplayName("getCustomerWallet - Người dùng chưa có shop trả về ví mặc định số dư 0")
    void getCustomerWallet_NoStore_ReturnsZero() {
        when(storeRepository.findByUserId(20L)).thenReturn(Optional.empty());

        SellerWalletResponse response = walletService.getCustomerWallet(20L);

        assertNotNull(response);
        assertEquals(BigDecimal.ZERO, response.getBalance());
        assertFalse(response.getBankAccount().isVerified());
    }
}
