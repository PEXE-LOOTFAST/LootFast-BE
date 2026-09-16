package com.lootfast.backend.auth.social;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ExternalAuthIdentityRepository
        extends JpaRepository<ExternalAuthIdentity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "user.roles"})
    Optional<ExternalAuthIdentity> findByProviderAndProviderSubject(
            SocialProvider provider,
            String providerSubject
    );
}
