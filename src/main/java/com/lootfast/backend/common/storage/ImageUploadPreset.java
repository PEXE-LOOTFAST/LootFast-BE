package com.lootfast.backend.common.storage;

public enum ImageUploadPreset {
    USER_AVATAR("c_limit,h_1024,w_1024,q_auto"),
    MERCHANT_LOGO("c_fill,g_auto,h_512,w_512,q_auto"),
    MERCHANT_COVER("c_limit,h_1600,w_2400,q_auto"),
    DEAL_IMAGE("c_fill,g_auto,h_1200,w_1600,q_auto");

    private final String transformation;

    ImageUploadPreset(String transformation) {
        this.transformation = transformation;
    }

    public String transformation() {
        return transformation;
    }
}
