package com.lootfast.backend.common.storage;

import java.util.UUID;

public interface AvatarStorage {

    StoredAvatar upload(UUID userPublicId, byte[] content);
}
