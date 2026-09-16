package com.lootfast.backend.merchant.repository;

import com.lootfast.backend.merchant.entity.MerchantOpeningHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MerchantOpeningHourRepository
        extends JpaRepository<MerchantOpeningHour, Long> {

    List<MerchantOpeningHour> findAllByMerchantProfileId(Long merchantProfileId);

    @Modifying(flushAutomatically = true)
    @Query("delete from MerchantOpeningHour hour where hour.merchantProfile.id = :profileId")
    int deleteAllByMerchantProfileId(@Param("profileId") Long profileId);
}
