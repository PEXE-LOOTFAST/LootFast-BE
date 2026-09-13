package com.lootfast.backend.merchant.entity;

import com.lootfast.backend.common.storage.ImageUploadPreset;

public enum MerchantImageType {
    LOGO("logo", ImageUploadPreset.MERCHANT_LOGO),
    COVER("cover", ImageUploadPreset.MERCHANT_COVER);

    private final String storageName;
    private final ImageUploadPreset uploadPreset;

    MerchantImageType(String storageName, ImageUploadPreset uploadPreset) {
        this.storageName = storageName;
        this.uploadPreset = uploadPreset;
    }

    public String storageName() {
        return storageName;
    }

    public ImageUploadPreset uploadPreset() {
        return uploadPreset;
    }
}
