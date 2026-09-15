package com.lootfast.backend.user.repository;

import com.lootfast.backend.user.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {
}
