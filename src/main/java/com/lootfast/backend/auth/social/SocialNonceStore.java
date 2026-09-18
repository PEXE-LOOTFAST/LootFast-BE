package com.lootfast.backend.auth.social;

import java.time.Duration;

interface SocialNonceStore {

    boolean save(String nonce, Duration ttl);

    boolean consume(String nonce);
}
