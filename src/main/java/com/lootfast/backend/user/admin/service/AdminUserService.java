package com.lootfast.backend.user.admin.service;
import com.lootfast.backend.auth.session.AuthSessionRepository;
import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import com.lootfast.backend.user.admin.dto.*;
import com.lootfast.backend.user.admin.entity.AccountAudit;
import com.lootfast.backend.user.admin.repository.AccountAuditRepository;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
  private final UserRepository users;
  private final AuthSessionRepository sessions;
  private final AccountAuditRepository audits;
  private final Clock clock;
  private final com.lootfast.backend.merchant.repository.MerchantProfileRepository merchants;
  public AdminUserService(UserRepository users, AuthSessionRepository sessions,
      AccountAuditRepository audits, Clock clock, com.lootfast.backend.merchant.repository.MerchantProfileRepository merchants) {
    this.users=users; this.sessions=sessions; this.audits=audits; this.clock=clock; this.merchants=merchants;
  }
  private User admin(String subject) {
    Long id;
    try {id=Long.valueOf(subject);} catch(RuntimeException e){throw new ApiException(ErrorCode.ACCESS_DENIED);}
    User admin=users.findByIdWithRoles(id).orElseThrow(()->new ApiException(ErrorCode.ACCESS_DENIED));
    if(!admin.hasRole(UserRole.ADMIN)||admin.isLocked()||admin.getStatus()!=UserStatus.VERIFIED)
      throw new ApiException(ErrorCode.ACCESS_DENIED);
    return admin;
  }
  private Pageable page(int page,int size) {
    if(page<0||size<1||size>50||(long)page*size>Integer.MAX_VALUE)
      throw new ApiException(ErrorCode.VALIDATION_FAILED,"Invalid pagination; size must be 1 to 50");
    return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt","id"));
  }
  @Transactional(readOnly=true,timeout=5)
  public PagedResponse<AdminUserResponse> list(String subject,String keyword,UserRole role,
      UserStatus verificationStatus,Boolean locked,int page,int size) {
    admin(subject);
    String pattern=null;
    if(keyword!=null&&!keyword.isBlank()) {
      if(keyword.length()>100)throw new ApiException(ErrorCode.VALIDATION_FAILED);
      pattern="%"+keyword.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
    }
    return PagedResponse.from(users.searchAdmin(pattern,role,verificationStatus,locked,page(page,size))
        .map(AdminUserResponse::from));
  }
  @Transactional(readOnly=true)
  public AdminUserResponse get(String subject,UUID id) {
    admin(subject);return AdminUserResponse.from(users.findByPublicId(id)
        .orElseThrow(()->new ApiException(ErrorCode.USER_NOT_FOUND)));
  }
  @Transactional(readOnly=true)
  public PagedResponse<AccountAuditResponse> history(String subject,UUID id,int page,int size) {
    admin(subject);
    if(!users.existsByPublicId(id))throw new ApiException(ErrorCode.USER_NOT_FOUND);
    return PagedResponse.from(audits.findByTargetPublicId(id,page(page,size)).map(AccountAuditResponse::from));
  }
  @Transactional(timeout=5)
  public AdminUserResponse change(String subject,UUID id,String reason,boolean lock) {
    User actor=admin(subject);
    if(reason==null||reason.isBlank()||reason.length()>500)throw new ApiException(ErrorCode.VALIDATION_FAILED);
    User target=users.findByPublicIdForUpdate(id).orElseThrow(()->new ApiException(ErrorCode.USER_NOT_FOUND));
    if(target.getId().equals(actor.getId())||target.hasRole(UserRole.ADMIN))
      throw new ApiException(ErrorCode.ACCOUNT_ACTION_FORBIDDEN,"Cannot lock/unlock an ADMIN account");
    if(target.isLocked()==lock)return AdminUserResponse.from(target);
    if(target.hasRole(UserRole.MERCHANT))merchants.findByUserIdForUpdate(target.getId());
    var now=clock.instant();
    if(lock)target.lockAccount(reason.trim(),now);else target.unlockAccount();
    audits.save(new AccountAudit(target.getPublicId(),actor.getPublicId(),lock?"LOCK":"UNLOCK",reason.trim(),now));
    if(lock)sessions.revokeActiveSessions(target.getId(),now);
    return AdminUserResponse.from(target);
  }
}
