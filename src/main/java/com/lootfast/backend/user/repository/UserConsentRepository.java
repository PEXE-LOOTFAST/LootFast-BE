package com.lootfast.backend.user.repository;

import com.lootfast.backend.user.entity.UserConsent;
import com.lootfast.backend.user.entity.ConsentDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {
    boolean existsByUser_IdAndDocumentAndDocumentVersion(
            Long userId, ConsentDocument document, String documentVersion);
}
