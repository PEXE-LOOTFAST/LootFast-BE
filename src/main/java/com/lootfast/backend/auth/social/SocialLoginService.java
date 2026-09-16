package com.lootfast.backend.auth.social;

import com.lootfast.backend.auth.dto.SigninResponse;
import com.lootfast.backend.auth.dto.SocialLoginRequest;
import com.lootfast.backend.auth.service.JwtService;
import com.lootfast.backend.auth.session.AuthSessionService;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class SocialLoginService {

    private final SocialNonceService nonceService;
    private final SocialIdentityTokenVerifier tokenVerifier;
    private final SocialLoginTransactionService transactionService;
    private final JwtService jwtService;
    private final AuthSessionService authSessionService;

    public SocialLoginService(
            SocialNonceService nonceService,
            SocialIdentityTokenVerifier tokenVerifier,
            SocialLoginTransactionService transactionService,
            JwtService jwtService,
            AuthSessionService authSessionService
    ) {
        this.nonceService = nonceService;
        this.tokenVerifier = tokenVerifier;
        this.transactionService = transactionService;
        this.jwtService = jwtService;
        this.authSessionService = authSessionService;
    }

    public SigninResponse login(SocialLoginRequest request) {
        nonceService.consume(request.nonce());
        SocialIdentityTokenVerifier.VerifiedSocialIdentity identity = tokenVerifier.verify(
                request.provider(), request.idToken(), request.nonce()
        );
        User user;
        try {
            user = transactionService.findOrCreate(identity, request);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    ErrorCode.SOCIAL_IDENTITY_CONFLICT,
                    "The social account was linked concurrently. Please try again"
            );
        }
        if (user.getStatus() != UserStatus.VERIFIED) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_VERIFIED);
        }

        JwtService.IssuedToken token = jwtService.issueAccessToken(user);
        AuthSessionService.IssuedSession session = authSessionService.createSession(user);
        return new SigninResponse(
                token.value(),
                session.refreshToken(),
                "Bearer",
                token.expiresInSeconds(),
                session.expiresInSeconds(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getRoles()
        );
    }
}
