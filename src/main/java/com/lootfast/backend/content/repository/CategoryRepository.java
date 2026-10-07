package com.lootfast.backend.content.repository;

import com.lootfast.backend.content.entity.Category;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface CategoryRepository extends JpaRepository<Category, String> {
  @Query("select x from Category x where x.code=:id")
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Category> lock(String id);

  @Query("select c from Category c where c.code=:code")
  @Lock(LockModeType.PESSIMISTIC_READ)
  Optional<Category> lockReadable(String code);

  Page<Category> findByEnabledTrue(Pageable page);
}
