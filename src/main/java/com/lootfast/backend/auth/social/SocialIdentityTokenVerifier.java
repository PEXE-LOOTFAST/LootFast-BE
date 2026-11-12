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
            String lastName,
            boolean authoritativeEmail
    ) {
        // Keep callers/tests using the original identity shape compatible. For Google,
        // a third-party email requires a verified Workspace claim before auto-linking.
        public VerifiedSocialIdentity(SocialProvider provider, String subject, String email,
                                      String firstName, String lastName) {
            this(provider, subject, email, firstName, lastName,
                    provider != SocialProvider.GOOGLE
                            || email.toLowerCase(java.util.Locale.ROOT).endsWith("@gmail.com"));
        }
    }
}
