package com.lootfast.backend.user.admin.repository;
import com.lootfast.backend.user.admin.entity.AccountAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
public interface AccountAuditRepository extends JpaRepository<AccountAudit,UUID> {
  Page<AccountAudit> findByTargetPublicId(UUID target, Pageable pageable);
}
