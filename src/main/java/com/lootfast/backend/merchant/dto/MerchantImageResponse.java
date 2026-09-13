package com.lootfast.backend.merchant.dto;

import com.lootfast.backend.merchant.entity.MerchantImageType;

public record MerchantImageResponse(
        MerchantImageType imageType,
        String url
) {
}
