package com.lootfast.backend.user.admin.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="account_admin_audit")
public class AccountAudit {
  @Id private UUID id;
  @Column(name="target_public_id", nullable=false, updatable=false) private UUID targetPublicId;
  @Column(name="actor_public_id", nullable=false, updatable=false) private UUID actorPublicId;
  @Column(nullable=false, length=8, updatable=false) private String action;
  @Column(nullable=false, length=500, updatable=false) private String reason;
  @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
  protected AccountAudit() {}
  public AccountAudit(UUID target, UUID actor, String action, String reason, Instant now) {
    id=UUID.randomUUID(); targetPublicId=target; actorPublicId=actor;
    this.action=action; this.reason=reason; createdAt=now;
  }
  public UUID getId(){return id;}
  public UUID getTargetPublicId(){return targetPublicId;}
  public UUID getActorPublicId(){return actorPublicId;}
  public String getAction(){return action;}
  public String getReason(){return reason;}
  public Instant getCreatedAt(){return createdAt;}
}
