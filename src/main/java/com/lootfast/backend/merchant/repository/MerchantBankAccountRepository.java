package com.lootfast.backend.merchant.repository;

import com.lootfast.backend.merchant.entity.MerchantBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MerchantBankAccountRepository extends JpaRepository<MerchantBankAccount, Long> {

    @Query("""
            select account
            from MerchantBankAccount account
            join fetch account.merchant merchant
            where merchant.user.id = :userId
            """)
    Optional<MerchantBankAccount> findByMerchantUserId(@Param("userId") Long userId);
}
