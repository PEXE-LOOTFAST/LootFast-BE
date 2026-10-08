package com.lootfast.backend.merchant.sanction.repository;

import com.lootfast.backend.merchant.sanction.entity.MerchantSanction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantSanctionRepository extends JpaRepository<MerchantSanction, Long> {

  @EntityGraph(attributePaths = {"merchant", "merchant.user", "createdBy"})
  Optional<MerchantSanction> findByPublicId(UUID publicId);

  @EntityGraph(attributePaths = {"merchant", "merchant.user", "createdBy"})
  Page<MerchantSanction> findByMerchantId(Long merchantId, Pageable pageable);
}
