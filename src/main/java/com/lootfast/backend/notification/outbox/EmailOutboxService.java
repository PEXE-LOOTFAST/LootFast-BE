package com.lootfast.backend.notification.outbox;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class EmailOutboxService {
  private final NamedParameterJdbcTemplate jdbc;
  private final Clock clock;

  public EmailOutboxService(NamedParameterJdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(
      UUID id, String email, String subject, String body, EmailPreferenceCategory category) {
    int inserted =
        jdbc.update(
            """
insert into email_outbox(id,notification_id,recipient_email,subject,html_body,preference_category,status,next_attempt_at,created_at)
select public_id,public_id,:email,:subject,:body,:category,'PENDING',:now,:now from notifications where public_id=:id
on conflict(id) do nothing
""",
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("email", email)
                .addValue("subject", subject)
                .addValue("body", body)
                .addValue("category", category.name())
                .addValue("now", Timestamp.from(clock.instant())));
    if (inserted == 0
        && !Boolean.TRUE.equals(
            jdbc.queryForObject(
                "select exists(select 1 from notifications where public_id=:id)",
                Map.of("id", id),
                Boolean.class)))
      throw new IllegalStateException("Cannot enqueue email without its notification");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueueFilterBatch(List<UUID> ids, String subject, String body) {
    if (ids.isEmpty()) return;
    jdbc.update(
        """
insert into email_outbox(id,notification_id,recipient_email,subject,html_body,preference_category,status,next_attempt_at,created_at)
select n.public_id,n.public_id,u.email,:subject,:body,'SAVED_FILTER_DEALS','PENDING',:now,:now
from notifications n join users u on u.id=n.recipient_user_id
  join notification_preferences p on p.user_id=u.id and p.email_enabled and p.matching_deals_enabled
where n.public_id in (:ids) and u.locked_at is null and u.status='VERIFIED'
on conflict(id) do nothing
""",
        new MapSqlParameterSource()
            .addValue("ids", ids)
            .addValue("subject", subject)
            .addValue("body", body)
            .addValue("now", Timestamp.from(clock.instant())));
  }
}
