package com.lootfast.backend.merchant.dto;

import com.lootfast.backend.merchant.entity.MerchantBankAccount;

import java.time.Instant;

/**
 * The merchant's own payout account. Returned to the owning merchant only, so
 * the account number is not masked.
 */
public record MerchantBankAccountResponse(
        String bankBin,
        String bankName,
        String accountNumber,
        String accountHolderName,
        Instant updatedAt
) {
    public static MerchantBankAccountResponse from(MerchantBankAccount account) {
        return new MerchantBankAccountResponse(
                account.getBankBin(),
                account.getBankName(),
                account.getAccountNumber(),
                account.getAccountHolderName(),
                account.getUpdatedAt()
        );
    }
}
