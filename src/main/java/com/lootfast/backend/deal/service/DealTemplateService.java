package com.lootfast.backend.deal.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.dto.CreateMerchantDealRequest;
import com.lootfast.backend.deal.dto.DealTemplateResponse;
import com.lootfast.backend.deal.dto.MerchantDealResponse;
import com.lootfast.backend.deal.dto.ReuseDealTemplateRequest;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.entity.DealTemplate;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.deal.repository.DealTemplateRepository;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DealTemplateService {
  private final MerchantDealAccess access;
  private final MerchantProfileRepository merchants;
  private final DealRepository deals;
  private final DealTemplateRepository templates;
  private final MerchantDealService dealService;

  public DealTemplateService(
      MerchantDealAccess access,
      MerchantProfileRepository merchants,
      DealRepository deals,
      DealTemplateRepository templates,
      MerchantDealService dealService) {
    this.access = access;
    this.merchants = merchants;
    this.deals = deals;
    this.templates = templates;
    this.dealService = dealService;
  }

  @Transactional
  public DealTemplateResponse save(String subject, UUID dealId) {
    var merchant = access.requireApprovedMerchant(subject);
    merchants
        .findByUserIdForUpdate(merchant.getId())
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.MERCHANT_PROFILE_NOT_FOUND, "Merchant profile was not found"));
    var deal =
        deals
            .findOwnedForUpdate(dealId, merchant.getId())
            .orElseThrow(() -> new ApiException(ErrorCode.DEAL_NOT_FOUND, "Deal was not found"));
    if (deal.getStatus() == DealStatus.REMOVED) {
      throw new ApiException(
          ErrorCode.DEAL_NOT_EDITABLE, "Removed deals cannot be saved as templates");
    }
    return DealTemplateResponse.from(templates.saveAndFlush(new DealTemplate(deal)));
  }

  @Transactional(readOnly = true)
  public PagedResponse<DealTemplateResponse> list(String subject, int page, int size) {
    var merchant = access.requireApprovedMerchant(subject);
    var pageable =
        PageRequest.of(
            page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
    return PagedResponse.from(
        templates
            .findMerchantTemplates(merchant.getId(), pageable)
            .map(DealTemplateResponse::from));
  }

  public MerchantDealResponse reuse(
      String subject, UUID templateId, ReuseDealTemplateRequest request) {
    var merchant = access.requireApprovedMerchant(subject);
    DealTemplate template =
        templates
            .findOwned(templateId, merchant.getId())
            .orElseThrow(this::templateNotFound);
    return dealService.create(
        subject,
        new CreateMerchantDealRequest(
            template.getName(),
            template.getCategoryCode(),
            template.getOriginalPrice(),
            template.getDiscountedPrice(),
            request.totalQuantity(),
            template.getPreparationMinutes(),
            request.saleStartsAt(),
            request.saleEndsAt()));
  }

  @Transactional
  public void delete(String subject, UUID templateId) {
    var merchant = access.requireApprovedMerchant(subject);
    DealTemplate template =
        templates
            .findOwned(templateId, merchant.getId())
            .orElseThrow(this::templateNotFound);
    templates.delete(template);
  }

  private ApiException templateNotFound() {
    return new ApiException(ErrorCode.DEAL_TEMPLATE_NOT_FOUND, "Deal template was not found");
  }
}
