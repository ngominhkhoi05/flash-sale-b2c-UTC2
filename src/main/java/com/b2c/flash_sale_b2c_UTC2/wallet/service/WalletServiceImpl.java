package com.b2c.flash_sale_b2c_UTC2.wallet.service;

import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.exception.StoreErrorCode;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.AdminWithdrawalResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.ApproveWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.RejectWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerBankAccount;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private static final BigDecimal MIN_WITHDRAWAL = new BigDecimal("50000.00");

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final StoreRepository storeRepository;
    private final StoreBankAccountRepository storeBankAccountRepository;
    private final WalletWithdrawalRequestRepository walletWithdrawalRequestRepository;
    private final UserRepository userRepository;
    private final WalletTransactionMapper walletTransactionMapper;

    @Override
    @Transactional(readOnly = true)
    public SellerWalletResponse getSellerWallet(Long userId) {
        Store store = storeRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        Wallet wallet = walletRepository.findByStoreId(store.getId())
                .orElseGet(() -> createDefaultWallet(store));

        BigDecimal totalWithdrawn = walletTransactionRepository.sumWithdrawnByWalletId(wallet.getId());

        Optional<StoreBankAccount> bankOpt = storeBankAccountRepository.findByStoreId(store.getId());
        SellerBankAccount bankAccount = bankOpt.map(bank -> SellerBankAccount.builder()
                .bankName(bank.getBankName())
                .bankCode(bank.getBankCode())
                .accountNumber(bank.getAccountNumber())
                .accountHolder(bank.getAccountHolder())
                .branch(bank.getBranch())
                .isVerified(bank.isVerified() || (bank.getAccountNumber() != null && !bank.getAccountNumber().isBlank()))
                .build()).orElse(null);

        return SellerWalletResponse.builder()
                .balance(wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO)
                .pendingBalance(wallet.getFrozenBalance() != null ? wallet.getFrozenBalance() : BigDecimal.ZERO)
                .totalWithdrawn(totalWithdrawn != null ? totalWithdrawn : BigDecimal.ZERO)
                .bankAccount(bankAccount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SellerBankAccountResponse getSellerBankAccount(Long userId) {
        Store store = storeRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        return storeBankAccountRepository.findByStoreId(store.getId())
                .map(this::toBankAccountResponse)
                .orElse(null);
    }

    @Override
    @Transactional
    public SellerBankAccountResponse updateSellerBankAccount(Long userId, SellerBankAccountRequest request) {
        Store store = storeRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        StoreBankAccount account = storeBankAccountRepository.findByStoreId(store.getId())
                .orElseGet(() -> StoreBankAccount.builder()
                        .store(store)
                        .build());

        account.setBankName(request.getBankName().trim());
        account.setBankCode(request.getBankCode().trim().toUpperCase());
        account.setAccountNumber(request.getAccountNumber().trim());
        account.setAccountHolder(request.getAccountHolder().trim().toUpperCase());
        account.setBranch(request.getBranch() != null ? request.getBranch().trim() : null);
        account.setVerified(true);
        account.setUpdatedAt(Instant.now());

        StoreBankAccount saved = storeBankAccountRepository.save(account);
        log.info("Updated bank account for store ID {}: bank={}, account={}",
                store.getId(), saved.getBankCode(), saved.getAccountNumber());
        return toBankAccountResponse(saved);
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

        StoreBankAccount bankAccount = storeBankAccountRepository.findByStoreId(store.getId())
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_BANK_NOT_CONFIGURED));

        Wallet wallet = walletRepository.findByStoreIdWithLock(store.getId())
                .orElseGet(() -> createDefaultWallet(store));

        if (wallet.getBalance() == null || wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BusinessException(WalletErrorCode.WALLET_INSUFFICIENT);
        }

        BigDecimal amount = request.getAmount();
        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        BigDecimal newFrozenBalance = (wallet.getFrozenBalance() != null ? wallet.getFrozenBalance() : BigDecimal.ZERO).add(amount);

        wallet.setBalance(newBalance);
        wallet.setFrozenBalance(newFrozenBalance);
        wallet.setUpdatedAt(Instant.now());
        walletRepository.save(wallet);

        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        String requestCode = "WDR-" + dateStr + "-" + randomSuffix;

        WalletWithdrawalRequest withdrawalRequest = WalletWithdrawalRequest.builder()
                .requestCode(requestCode)
                .wallet(wallet)
                .store(store)
                .amount(amount)
                .status("PENDING")
                .bankName(bankAccount.getBankName())
                .bankCode(bankAccount.getBankCode())
                .accountNumber(bankAccount.getAccountNumber())
                .accountHolder(bankAccount.getAccountHolder())
                .branch(bankAccount.getBranch())
                .sellerNote(request.getNote() != null ? request.getNote().trim() : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        walletWithdrawalRequestRepository.save(withdrawalRequest);

        String desc = (request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote().trim()
                : "Yêu cầu rút tiền #" + requestCode + " về TK " + bankAccount.getAccountNumber();

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .transactionType("WITHDRAWAL_REQUEST")
                .amount(amount.negate())
                .balanceAfter(newBalance)
                .description(desc)
                .createdAt(Instant.now())
                .build();

        tx = walletTransactionRepository.save(tx);
        log.info("Seller user ID {} successfully requested withdrawal of {} VND. RequestCode: {}, TxId: {}",
                userId, amount, requestCode, tx.getId());

        return walletTransactionMapper.toResponse(tx);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminWithdrawalResponse> getAdminWithdrawals(String status, Long storeId, Pageable pageable) {
        String statusFilter = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim()))
                ? status.trim().toUpperCase()
                : null;

        Page<WalletWithdrawalRequest> page = walletWithdrawalRequestRepository.findWithFilters(statusFilter, storeId, pageable);
        List<AdminWithdrawalResponse> items = page.getContent().stream()
                .map(this::toAdminWithdrawalResponse)
                .toList();

        return PageResponse.of(page, items);
    }

    @Override
    @Transactional
    public AdminWithdrawalResponse approveWithdrawal(Long withdrawalId, Long adminId, ApproveWithdrawalRequest request) {
        WalletWithdrawalRequest withdrawal = walletWithdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WITHDRAWAL_NOT_FOUND));

        if (!"PENDING".equalsIgnoreCase(withdrawal.getStatus())) {
            throw new BusinessException(WalletErrorCode.WITHDRAWAL_ALREADY_PROCESSED);
        }

        Wallet wallet = walletRepository.findByIdWithLock(withdrawal.getWallet().getId())
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        BigDecimal newFrozen = (wallet.getFrozenBalance() != null ? wallet.getFrozenBalance() : BigDecimal.ZERO)
                .subtract(withdrawal.getAmount());
        if (newFrozen.compareTo(BigDecimal.ZERO) < 0) {
            newFrozen = BigDecimal.ZERO;
        }
        wallet.setFrozenBalance(newFrozen);
        wallet.setUpdatedAt(Instant.now());
        walletRepository.save(wallet);

        User adminUser = adminId != null ? userRepository.findById(adminId).orElse(null) : null;

        withdrawal.setStatus("APPROVED");
        withdrawal.setApprovedBy(adminUser);
        withdrawal.setApprovedAt(Instant.now());
        if (request != null && request.getAdminNote() != null) {
            withdrawal.setAdminNote(request.getAdminNote().trim());
        }
        withdrawal.setUpdatedAt(Instant.now());
        WalletWithdrawalRequest saved = walletWithdrawalRequestRepository.save(withdrawal);

        log.info("Admin ID {} approved withdrawal request ID {} (code {}) for amount {}",
                adminId, saved.getId(), saved.getRequestCode(), saved.getAmount());

        return toAdminWithdrawalResponse(saved);
    }

    @Override
    @Transactional
    public AdminWithdrawalResponse rejectWithdrawal(Long withdrawalId, Long adminId, RejectWithdrawalRequest request) {
        WalletWithdrawalRequest withdrawal = walletWithdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WITHDRAWAL_NOT_FOUND));

        if (!"PENDING".equalsIgnoreCase(withdrawal.getStatus())) {
            throw new BusinessException(WalletErrorCode.WITHDRAWAL_ALREADY_PROCESSED);
        }

        Wallet wallet = walletRepository.findByIdWithLock(withdrawal.getWallet().getId())
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        BigDecimal amount = withdrawal.getAmount();
        BigDecimal newFrozen = (wallet.getFrozenBalance() != null ? wallet.getFrozenBalance() : BigDecimal.ZERO)
                .subtract(amount);
        if (newFrozen.compareTo(BigDecimal.ZERO) < 0) {
            newFrozen = BigDecimal.ZERO;
        }
        BigDecimal newBalance = (wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO).add(amount);

        wallet.setFrozenBalance(newFrozen);
        wallet.setBalance(newBalance);
        wallet.setUpdatedAt(Instant.now());
        walletRepository.save(wallet);

        User adminUser = adminId != null ? userRepository.findById(adminId).orElse(null) : null;

        withdrawal.setStatus("REJECTED");
        withdrawal.setRejectedBy(adminUser);
        withdrawal.setRejectedAt(Instant.now());
        withdrawal.setAdminNote(request.getReason().trim());
        withdrawal.setUpdatedAt(Instant.now());
        WalletWithdrawalRequest saved = walletWithdrawalRequestRepository.save(withdrawal);

        WalletTransaction refundTx = WalletTransaction.builder()
                .wallet(wallet)
                .transactionType("WITHDRAWAL_REFUND")
                .amount(amount)
                .balanceAfter(newBalance)
                .description("Hoàn tiền yêu cầu rút #" + withdrawal.getRequestCode() + ": " + request.getReason().trim())
                .createdAt(Instant.now())
                .build();
        walletTransactionRepository.save(refundTx);

        log.info("Admin ID {} rejected withdrawal request ID {} (code {}) for amount {}. Reason: {}",
                adminId, saved.getId(), saved.getRequestCode(), saved.getAmount(), request.getReason());

        return toAdminWithdrawalResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SellerWalletResponse getCustomerWallet(Long userId) {
        Optional<Store> storeOpt = storeRepository.findByUserId(userId);
        if (storeOpt.isPresent()) {
            return getSellerWallet(userId);
        }

        return SellerWalletResponse.builder()
                .balance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .bankAccount(null)
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

    private SellerBankAccountResponse toBankAccountResponse(StoreBankAccount acc) {
        return SellerBankAccountResponse.builder()
                .id(acc.getId())
                .storeId(acc.getStore() != null ? acc.getStore().getId() : null)
                .bankName(acc.getBankName())
                .bankCode(acc.getBankCode())
                .accountNumber(acc.getAccountNumber())
                .accountHolder(acc.getAccountHolder())
                .branch(acc.getBranch())
                .isVerified(acc.isVerified())
                .updatedAt(acc.getUpdatedAt())
                .build();
    }

    private AdminWithdrawalResponse toAdminWithdrawalResponse(WalletWithdrawalRequest req) {
        return AdminWithdrawalResponse.builder()
                .id(req.getId())
                .requestCode(req.getRequestCode())
                .walletId(req.getWallet() != null ? req.getWallet().getId() : null)
                .storeId(req.getStore() != null ? req.getStore().getId() : null)
                .storeName(req.getStore() != null ? req.getStore().getStoreName() : null)
                .amount(req.getAmount())
                .status(req.getStatus())
                .bankName(req.getBankName())
                .bankCode(req.getBankCode())
                .accountNumber(req.getAccountNumber())
                .accountHolder(req.getAccountHolder())
                .branch(req.getBranch())
                .sellerNote(req.getSellerNote())
                .adminNote(req.getAdminNote())
                .createdAt(req.getCreatedAt())
                .approvedAt(req.getApprovedAt())
                .rejectedAt(req.getRejectedAt())
                .build();
    }
}
