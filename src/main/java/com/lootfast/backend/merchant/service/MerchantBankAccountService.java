package com.lootfast.backend.merchant.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.merchant.dto.MerchantBankAccountResponse;
import com.lootfast.backend.merchant.dto.UpdateBankAccountRequest;
import com.lootfast.backend.merchant.entity.MerchantBankAccount;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantBankAccountRepository;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merchant payout account management (FR-ME-03). Plain service class (single
 * implementation, no abstraction to swap) matching the other merchant services.
 * The merchant id is always derived from the JWT subject, never from the request.
 */
@Service
public class MerchantBankAccountService {

    private final com.lootfast.backend.config.DatabaseLockTimeout lockTimeout;
    private final MerchantBankAccountRepository bankAccountRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final com.lootfast.backend.order.repository.OrderRepository orders;

    public MerchantBankAccountService(
            MerchantBankAccountRepository bankAccountRepository,
            MerchantProfileRepository merchantProfileRepository,
            com.lootfast.backend.order.repository.OrderRepository orders,
            com.lootfast.backend.config.DatabaseLockTimeout lockTimeout
    ) {
        this.lockTimeout = lockTimeout;
        this.orders = orders;
        this.bankAccountRepository = bankAccountRepository;
        this.merchantProfileRepository = merchantProfileRepository;
    }

    @Transactional(readOnly = true)
    public MerchantBankAccountResponse getMyBankAccount(String subject) {
        Long userId = parseUserId(subject);
        MerchantBankAccount account = bankAccountRepository.findByMerchantUserId(userId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.BANK_ACCOUNT_NOT_FOUND,
                        "Bank account has not been set up yet"
                ));
        return MerchantBankAccountResponse.from(account);
    }

    @Transactional
    public MerchantBankAccountResponse upsertMyBankAccount(
            String subject,
            UpdateBankAccountRequest request
    ) {
        Long userId = parseUserId(subject);

        lockTimeout.apply();
        // Same merchant lock is acquired by reservations before the bank snapshot.
        MerchantProfile merchant = merchantProfileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.MERCHANT_PROFILE_NOT_FOUND,
                        "Merchant profile was not found"
                ));

        if (orders.existsByMerchantIdAndStatus(merchant.getId(),
                com.lootfast.backend.order.entity.OrderStatus.RESERVED)) {
            throw new ApiException(ErrorCode.BANK_ACCOUNT_IN_USE,
                    "Bank account cannot change while reserved orders exist");
        }
        MerchantBankAccount account = bankAccountRepository.findByMerchantUserId(userId)
                .map(existing -> {
                    existing.update(
                            request.bankBin(),
                            request.bankName(),
                            request.accountNumber(),
                            request.accountHolderName()
                    );
                    return existing;
                })
                .orElseGet(() -> bankAccountRepository.save(new MerchantBankAccount(
                        merchant,
                        request.bankBin(),
                        request.bankName(),
                        request.accountNumber(),
                        request.accountHolderName()
                )));

        return MerchantBankAccountResponse.from(account);
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException exception) {
            throw new ApiException(
                    ErrorCode.USER_NOT_FOUND,
                    "Authenticated user is no longer available"
            );
        }
    }
}
