package com.b2c.flash_sale_b2c_UTC2.wallet.service;

import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.exception.StoreErrorCode;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerBankAccount;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerWalletResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.WalletTransactionResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.Wallet;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.WalletTransaction;
import com.b2c.flash_sale_b2c_UTC2.wallet.exception.WalletErrorCode;
import com.b2c.flash_sale_b2c_UTC2.wallet.mapper.WalletTransactionMapper;
import com.b2c.flash_sale_b2c_UTC2.wallet.repository.WalletRepository;
import com.b2c.flash_sale_b2c_UTC2.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private static final BigDecimal MIN_WITHDRAWAL = new BigDecimal("50000.00");

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final StoreRepository storeRepository;
    private final WalletTransactionMapper walletTransactionMapper;

    @Override
    @Transactional(readOnly = true)
    public SellerWalletResponse getSellerWallet(Long userId) {
        Store store = storeRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        Wallet wallet = walletRepository.findByStoreId(store.getId())
                .orElseGet(() -> createDefaultWallet(store));

        BigDecimal totalWithdrawn = walletTransactionRepository.sumWithdrawnByWalletId(wallet.getId());

        SellerBankAccount bankAccount = SellerBankAccount.builder()
                .bankName("MB Bank")
                .accountNumber("9999" + store.getId() + "888")
                .accountHolder(store.getUser() != null && store.getUser().getFullName() != null
                        ? store.getUser().getFullName().toUpperCase()
                        : store.getStoreName().toUpperCase())
                .branch("Chi nhánh TP.HCM")
                .isVerified(true)
                .build();

        return SellerWalletResponse.builder()
                .balance(wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO)
                .pendingBalance(wallet.getFrozenBalance() != null ? wallet.getFrozenBalance() : BigDecimal.ZERO)
                .totalWithdrawn(totalWithdrawn != null ? totalWithdrawn : BigDecimal.ZERO)
                .bankAccount(bankAccount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> getSellerTransactions(Long userId, String type, Pageable pageable) {
        Store store = storeRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        Wallet wallet = walletRepository.findByStoreId(store.getId())
                .orElseGet(() -> createDefaultWallet(store));

        Page<WalletTransaction> page;
        if (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type.trim())) {
            page = walletTransactionRepository.findByWalletIdAndTransactionTypeOrderByCreatedAtDesc(
                    wallet.getId(), type.trim(), pageable
            );
        } else {
            page = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable);
        }

        List<WalletTransactionResponse> items = page.getContent().stream()
                .map(walletTransactionMapper::toResponse)
                .toList();

        return PageResponse.of(page, items);
    }

    @Override
    @Transactional
    public WalletTransactionResponse requestSellerWithdrawal(Long userId, SellerWithdrawalRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(MIN_WITHDRAWAL) < 0) {
            throw new BusinessException(WalletErrorCode.MINIMUM_WITHDRAWAL_AMOUNT);
        }

        Store store = storeRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        Wallet wallet = walletRepository.findByStoreIdWithLock(store.getId())
                .orElseGet(() -> createDefaultWallet(store));

        if (wallet.getBalance() == null || wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BusinessException(WalletErrorCode.WALLET_INSUFFICIENT);
        }

        BigDecimal newBalance = wallet.getBalance().subtract(request.getAmount());
        wallet.setBalance(newBalance);
        wallet.setUpdatedAt(Instant.now());
        walletRepository.save(wallet);

        String desc = (request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote().trim()
                : "Yêu cầu rút tiền về tài khoản ngân hàng " + (request.getBankAccountNumber() != null ? request.getBankAccountNumber() : "");

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .transactionType("WITHDRAWAL")
                .amount(request.getAmount().negate())
                .balanceAfter(newBalance)
                .description(desc)
                .build();

        tx = walletTransactionRepository.save(tx);
        log.info("Seller user ID {} successfully requested withdrawal of {} VND. Transaction ID: {}",
                userId, request.getAmount(), tx.getId());

        return walletTransactionMapper.toResponse(tx);
    }

    @Override
    @Transactional(readOnly = true)
    public SellerWalletResponse getCustomerWallet(Long userId) {
        Optional<Store> storeOpt = storeRepository.findByUserId(userId);
        if (storeOpt.isPresent()) {
            return getSellerWallet(userId);
        }

        SellerBankAccount defaultBank = SellerBankAccount.builder()
                .bankName("Chưa liên kết")
                .accountNumber("---")
                .accountHolder("---")
                .branch("---")
                .isVerified(false)
                .build();

        return SellerWalletResponse.builder()
                .balance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .bankAccount(defaultBank)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> getCustomerTransactions(Long userId, String type, Pageable pageable) {
        Optional<Store> storeOpt = storeRepository.findByUserId(userId);
        if (storeOpt.isPresent()) {
            return getSellerTransactions(userId, type, pageable);
        }

        return PageResponse.<WalletTransactionResponse>builder()
                .items(Collections.emptyList())
                .pageNumber(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalElements(0L)
                .totalPages(0)
                .isFirst(true)
                .isLast(true)
                .hasNext(false)
                .hasPrevious(false)
                .build();
    }

    @Override
    @Transactional
    public WalletTransactionResponse requestCustomerWithdrawal(Long userId, SellerWithdrawalRequest request) {
        Optional<Store> storeOpt = storeRepository.findByUserId(userId);
        if (storeOpt.isPresent()) {
            return requestSellerWithdrawal(userId, request);
        }
        throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
    }

    private Wallet createDefaultWallet(Store store) {
        Wallet wallet = Wallet.builder()
                .store(store)
                .balance(BigDecimal.ZERO)
                .frozenBalance(BigDecimal.ZERO)
                .updatedAt(Instant.now())
                .build();
        return walletRepository.save(wallet);
    }
}
