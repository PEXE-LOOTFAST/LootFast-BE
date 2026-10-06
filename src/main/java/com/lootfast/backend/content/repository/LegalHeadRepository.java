package com.lootfast.backend.content.repository;

import com.lootfast.backend.content.entity.LegalHead;
import com.lootfast.backend.user.entity.ConsentDocument;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface LegalHeadRepository extends JpaRepository<LegalHead, ConsentDocument> {
  @Query("select x from LegalHead x where x.document=:id")
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<LegalHead> lock(ConsentDocument id);

  @Query("select h from LegalHead h where h.document=:document")
  @Lock(LockModeType.PESSIMISTIC_READ)
  Optional<LegalHead> lockReadable(ConsentDocument document);
}
