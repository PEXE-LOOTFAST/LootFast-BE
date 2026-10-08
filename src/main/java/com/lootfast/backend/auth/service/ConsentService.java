package com.lootfast.backend.auth.service;

import com.lootfast.backend.user.entity.ConsentDocument;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserConsent;
import com.lootfast.backend.user.repository.UserConsentRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ConsentService {

    private final UserConsentRepository consentRepository;
    private final Clock clock;
    private final com.lootfast.backend.content.service.LegalContentService legal;

    public ConsentService(UserConsentRepository consentRepository, Clock clock, com.lootfast.backend.content.service.LegalContentService legal) {
        this.consentRepository = consentRepository;
        this.clock = clock;
        this.legal = legal;
    }

    public void recordRequiredConsents(
            User user,
            String termsVersion,
            String privacyPolicyVersion
    ) {
        legal.requireCurrentConsent(ConsentDocument.TERMS_OF_USE, termsVersion);
        legal.requireCurrentConsent(ConsentDocument.PRIVACY_POLICY, privacyPolicyVersion);
        Instant acceptedAt = clock.instant();
        consentRepository.saveAll(List.of(
                new UserConsent(
                        user,
                        ConsentDocument.TERMS_OF_USE,
                        termsVersion.trim(),
                        acceptedAt
                ),
                new UserConsent(
                        user,
                        ConsentDocument.PRIVACY_POLICY,
                        privacyPolicyVersion.trim(),
                        acceptedAt
                )
        ));
    }
}
