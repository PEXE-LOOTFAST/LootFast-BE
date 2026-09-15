package com.lootfast.backend.auth.social;

import com.lootfast.backend.auth.dto.OnboardingAction;
import com.lootfast.backend.auth.dto.SigninResponse;
import com.lootfast.backend.auth.dto.SocialLoginRequest;
import com.lootfast.backend.auth.service.JwtService;
import com.lootfast.backend.auth.service.OnboardingService;
import com.lootfast.backend.auth.session.AuthSessionService;
import com.lootfast.backend.user.entity.AccountType;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialLoginServiceTests {

    @Mock private SocialNonceService nonceService;
    @Mock private SocialIdentityTokenVerifier tokenVerifier;
    @Mock private SocialLoginTransactionService transactionService;
    @Mock private JwtService jwtService;
    @Mock private AuthSessionService authSessionService;
    @Mock private OnboardingService onboardingService;

    private SocialLoginService service;

    @BeforeEach
    void setUp() {
        service = new SocialLoginService(
                nonceService,
                tokenVerifier,
                transactionService,
                jwtService,
                authSessionService,
                onboardingService
        );
    }

    @Test
    void loginConsumesNonceBeforeVerifyingAndIssuesLootFastSession() {
        SocialLoginRequest request = new SocialLoginRequest(
                SocialProvider.GOOGLE,
                "identity-token",
                "one-time-nonce",
                AccountType.BUYER,
                null,
                null,
                true,
                "terms-v1",
                true,
                "privacy-v1"
        );
        SocialIdentityTokenVerifier.VerifiedSocialIdentity identity =
                new SocialIdentityTokenVerifier.VerifiedSocialIdentity(
                        SocialProvider.GOOGLE,
                        "subject",
                        "user@gmail.com",
                        "An",
                        "Nguyen"
                );
        User user = new User(
                "An", "Nguyen", "user@gmail.com", null, null,
                UserRole.BUYER, UserStatus.VERIFIED, AccountType.BUYER
        );
        when(tokenVerifier.verify(
                SocialProvider.GOOGLE,
                "identity-token",
                "one-time-nonce"
        )).thenReturn(identity);
        when(transactionService.findOrCreate(identity, request)).thenReturn(user);
        when(jwtService.issueAccessToken(user))
                .thenReturn(new JwtService.IssuedToken("access-token", 900));
        when(authSessionService.createSession(user))
                .thenReturn(new AuthSessionService.IssuedSession("refresh-token", 2_592_000));
        when(onboardingService.nextAction(user)).thenReturn(OnboardingAction.BUYER_HOME);

        SigninResponse response = service.login(request);

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals(OnboardingAction.BUYER_HOME, response.nextAction());
        var order = inOrder(nonceService, tokenVerifier);
        order.verify(nonceService).consume("one-time-nonce");
        order.verify(tokenVerifier).verify(
                SocialProvider.GOOGLE,
                "identity-token",
                "one-time-nonce"
        );
    }
}
