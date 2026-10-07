package com.lootfast.backend.content.repository;

import com.lootfast.backend.content.entity.Banner;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface BannerRepository extends JpaRepository<Banner, UUID> {
  @Query("select x from Banner x where x.id=:id")
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Banner> lock(UUID id);

  @Query(
      "select b from Banner b where b.enabled=true and b.imageUrl is not null and (b.startsAt is"
          + " null or b.startsAt<=:now) and (b.endsAt is null or b.endsAt>:now)")
  Page<Banner> visible(Instant now, Pageable page);
}
