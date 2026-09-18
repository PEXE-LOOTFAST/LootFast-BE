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

    public ConsentService(UserConsentRepository consentRepository, Clock clock) {
        this.consentRepository = consentRepository;
        this.clock = clock;
    }

    public void recordRequiredConsents(
            User user,
            String termsVersion,
            String privacyPolicyVersion
    ) {
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
