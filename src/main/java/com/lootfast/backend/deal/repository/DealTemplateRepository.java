package com.lootfast.backend.deal.repository;

import com.lootfast.backend.deal.entity.DealTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DealTemplateRepository extends JpaRepository<DealTemplate, Long> {
  @Query(
      "select template from DealTemplate template where template.publicId=:publicId"
          + " and template.merchant.user.id=:merchantUserId")
  Optional<DealTemplate> findOwned(UUID publicId, Long merchantUserId);

  @Query(
      value =
          "select template from DealTemplate template where"
              + " template.merchant.user.id=:merchantUserId",
      countQuery =
          "select count(template) from DealTemplate template where"
              + " template.merchant.user.id=:merchantUserId")
  Page<DealTemplate> findMerchantTemplates(Long merchantUserId, Pageable pageable);
}
