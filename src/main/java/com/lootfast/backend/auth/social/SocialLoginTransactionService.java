package com.lootfast.backend.auth.social;

import com.lootfast.backend.auth.dto.SocialLoginRequest;
import com.lootfast.backend.auth.service.ConsentService;
import com.lootfast.backend.auth.session.AuthSessionRepository;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserProfile;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserProfileRepository;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class SocialLoginTransactionService {

    private final ExternalAuthIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ConsentService consentService;
    private final AuthSessionRepository authSessionRepository;
    private final Clock clock;

    public SocialLoginTransactionService(
            ExternalAuthIdentityRepository identityRepository,
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            ConsentService consentService,
            AuthSessionRepository authSessionRepository,
            Clock clock
    ) {
        this.identityRepository = identityRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.consentService = consentService;
        this.authSessionRepository = authSessionRepository;
        this.clock = clock;
    }

    @Transactional(timeout = 5)
    public User findOrCreate(
            SocialIdentityTokenVerifier.VerifiedSocialIdentity identity,
            SocialLoginRequest request
    ) {
        ExternalAuthIdentity existingIdentity = identityRepository
                .findByProviderAndProviderSubject(identity.provider(), identity.subject())
                .orElse(null);
        if (existingIdentity != null) {
            User user = existingIdentity.getUser();
            if (user.isLocked()) throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
            existingIdentity.recordLogin(identity.email(), clock.instant());
            user.getRoles();
            return user;
        }

        User user = userRepository.findByEmailForUpdate(identity.email()).orElse(null);
        if (user == null) {
            validateRegistrationConsent(request);
            user = createSocialUser(identity, request);
        } else {
            if (user.isLocked()) throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
            if (user.getStatus() != UserStatus.VERIFIED) {
                if (user.getStatus() != UserStatus.UNVERIFIED
                        || identity.provider() != SocialProvider.GOOGLE
                        || !identity.authoritativeEmail()) {
                    throw new ApiException(ErrorCode.ACCOUNT_NOT_VERIFIED,
                            "Verify the existing LootFast account before linking social login");
                }
                validateRegistrationConsent(request);
                user.verifyWithGoogle();
                consentService.recordRequiredConsents(user, request.termsVersion(), request.privacyPolicyVersion());
                // This bulk update flushes and clears the persistence context. Reload the
                // locked user afterwards so role loading and session creation remain safe.
                authSessionRepository.revokeActiveSessions(user.getId(), clock.instant());
                user = userRepository.findByEmailForUpdate(identity.email()).orElseThrow();
            }
            if (identity.provider() == SocialProvider.GOOGLE && !identity.authoritativeEmail()) {
                throw new ApiException(ErrorCode.SOCIAL_IDENTITY_CONFLICT,
                        "Sign in with the existing LootFast credentials; automatic Google linking is unavailable for this email");
            }
        }

        if (user.isLocked()) throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
        identityRepository.saveAndFlush(new ExternalAuthIdentity(
                user,
                identity.provider(),
                identity.subject(),
                identity.email(),
                clock.instant()
        ));
        user.getRoles();
        return user;
    }

    private User createSocialUser(
            SocialIdentityTokenVerifier.VerifiedSocialIdentity identity,
            SocialLoginRequest request
    ) {
        String firstName = preferredName(
                request.firstName(), identity.firstName(), identity.provider().name()
        );
        String lastName = preferredName(request.lastName(), identity.lastName(), "User");

        User user = userRepository.save(new User(
                firstName,
                lastName,
                identity.email(),
                null,
                null,
                UserRole.BUYER,
                UserStatus.VERIFIED
        ));
        userProfileRepository.save(new UserProfile(user, firstName + " " + lastName));
        consentService.recordRequiredConsents(
                user,
                request.termsVersion(),
                request.privacyPolicyVersion()
        );
        return user;
    }

    private void validateRegistrationConsent(SocialLoginRequest request) {
        if (!Boolean.TRUE.equals(request.termsAccepted())
                || !Boolean.TRUE.equals(request.privacyPolicyAccepted())
                || isBlank(request.termsVersion())
                || isBlank(request.privacyPolicyVersion())) {
            throw new ApiException(
                    ErrorCode.CONSENT_REQUIRED,
                    "Terms of use and privacy policy consent are required"
            );
        }
    }

    private String preferredName(String requestValue, String tokenValue, String fallback) {
        String value = !isBlank(requestValue)
                ? requestValue.trim()
                : !isBlank(tokenValue) ? tokenValue.trim() : fallback;
        return value.substring(0, Math.min(value.length(), 100));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
