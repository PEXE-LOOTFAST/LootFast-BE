package com.lootfast.backend.auth.social;

public interface SocialIdentityTokenVerifier {

    VerifiedSocialIdentity verify(
            SocialProvider provider,
            String idToken,
            String rawNonce
    );

    record VerifiedSocialIdentity(
            SocialProvider provider,
            String subject,
            String email,
            String firstName,
            String lastName
    ) {
    }
}
