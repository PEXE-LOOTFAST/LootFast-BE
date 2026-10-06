package com.lootfast.backend.user.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.lootfast.backend.auth.dto.SigninRequest;
import com.lootfast.backend.auth.service.*;
import com.lootfast.backend.auth.session.*;
import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.deal.entity.*;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.order.dto.CreateOrderRequest;
import com.lootfast.backend.order.entity.*;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.order.service.OrderService;
import com.lootfast.backend.user.admin.service.AdminUserService;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;

@SpringBootTest(properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@Transactional
abstract class AdminUserTests {
 @Autowired UserRepository users;
 @Autowired AdminUserService service;
 @Autowired AuthSessionService sessions;
 @Autowired SigninService signin;
 @Autowired JwtService jwt;
 @Autowired JwtDecoder decoder;
 @Autowired MockMvc mvc;
 @Autowired PasswordEncoder passwords;
 @Autowired MerchantProfileRepository merchants;
 @Autowired DealRepository deals;
 @Autowired OrderRepository orders;
 @Autowired OrderService ordering;
 User admin,buyer;
 @BeforeEach void setup(){admin=user("Admin",UserRole.ADMIN);buyer=user("Buyer",UserRole.BUYER);}
 User user(String name,UserRole role){return users.saveAndFlush(new User(name,"Account",
     UUID.randomUUID()+"@test.local",null,passwords.encode("Password123!"),role,UserStatus.VERIFIED));}
 String token(User u){return "Bearer "+jwt.issueAccessToken(u).value();}
 String subject(){return admin.getId().toString();}

 @Test void locksRevokeSessionsAndOldJwtStaysInvalidAfterUnlock() throws Exception {
   var login=signin.signin(new SigninRequest(buyer.getEmail(),"Password123!"));
   mvc.perform(get("/api/v1/orders").header("Authorization","Bearer "+login.accessToken())).andExpect(status().isOk());
   var locked=service.change(subject(),buyer.getPublicId(),"Fraud investigation",true);
   assertTrue(locked.locked());
   mvc.perform(get("/api/v1/orders").header("Authorization","Bearer "+login.accessToken())).andExpect(status().isUnauthorized());
   assertEquals(ErrorCode.INVALID_REFRESH_TOKEN,assertThrows(ApiException.class,
       ()->sessions.refresh(login.refreshToken())).getErrorCode());
   assertEquals(ErrorCode.ACCOUNT_LOCKED,assertThrows(ApiException.class,
       ()->signin.signin(new SigninRequest(buyer.getEmail(),"Password123!"))).getErrorCode());
   service.change(subject(),buyer.getPublicId(),"Investigation complete",false);
   mvc.perform(get("/api/v1/orders").header("Authorization","Bearer "+login.accessToken())).andExpect(status().isUnauthorized());
   var fresh=signin.signin(new SigninRequest(buyer.getEmail(),"Password123!"));
   mvc.perform(get("/api/v1/orders").header("Authorization","Bearer "+fresh.accessToken())).andExpect(status().isOk());
   assertEquals(2,service.history(subject(),buyer.getPublicId(),0,20).totalElements());
 }

 @Test void apiRequiresAdminFiltersResultsAndDoesNotExposeSecrets() throws Exception {
   String path="/api/v1/admin/users";
   mvc.perform(get(path)).andExpect(status().isUnauthorized());
   mvc.perform(get(path).header("Authorization",token(buyer))).andExpect(status().isForbidden());
   mvc.perform(get(path).header("Authorization",token(admin)).param("keyword",buyer.getEmail()))
       .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
       .andExpect(jsonPath("$.data.items[0].userId").value(buyer.getPublicId().toString()))
       .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());
   assertEquals(1,service.list(subject(),buyer.getEmail(),UserRole.BUYER,UserStatus.VERIFIED,false,0,20).totalElements());
   assertEquals(0,service.list(subject(),buyer.getEmail(),UserRole.MERCHANT,null,null,0,20).totalElements());
   var percent=user("Literal%",UserRole.BUYER);
   assertEquals(1,service.list(subject(),"%",null,null,null,0,20).totalElements());
   assertEquals(percent.getPublicId(),service.list(subject(),"%",null,null,null,0,20).items().getFirst().userId());
   mvc.perform(get(path).header("Authorization",token(admin)).param("size","51")).andExpect(status().isBadRequest());
   mvc.perform(post(path+"/"+buyer.getPublicId()+"/lock").header("Authorization",token(admin))
       .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"   \"}"))
       .andExpect(status().isBadRequest());
   mvc.perform(post(path+"/"+buyer.getPublicId()+"/lock").header("Authorization",token(admin))
       .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Review account\"}"))
       .andExpect(status().isOk()).andExpect(jsonPath("$.data.locked").value(true));
   mvc.perform(get(path+"/"+buyer.getPublicId()+"/actions").header("Authorization",token(admin)))
       .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
   mvc.perform(post(path+"/"+buyer.getPublicId()+"/unlock").header("Authorization",token(admin))
       .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Resolved\"}"))
       .andExpect(status().isOk()).andExpect(jsonPath("$.data.locked").value(false));
   mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['"+path+"/{userId}/lock'].post").exists());
 }

 @Test void idempotentActionsPreserveVerificationAndProtectAdmins() {
   User unverified=users.saveAndFlush(new User("Pending","Account",UUID.randomUUID()+"@test.local",
       null,null,UserRole.BUYER,UserStatus.UNVERIFIED));
   service.change(subject(),unverified.getPublicId(),"Review",true);
   service.change(subject(),unverified.getPublicId(),"Review again",true);
   assertEquals(1,service.history(subject(),unverified.getPublicId(),0,20).totalElements());
   assertEquals(1,service.list(subject(),unverified.getEmail(),null,null,true,0,20).totalElements());
   var unlocked=service.change(subject(),unverified.getPublicId(),"Resolved",false);
   assertEquals(UserStatus.UNVERIFIED,unlocked.verificationStatus());
   service.change(subject(),unverified.getPublicId(),"Already resolved",false);
   assertEquals(2,service.history(subject(),unverified.getPublicId(),0,20).totalElements());
   assertEquals(ErrorCode.ACCOUNT_ACTION_FORBIDDEN,assertThrows(ApiException.class,
       ()->service.change(subject(),admin.getPublicId(),"Cannot",true)).getErrorCode());
   assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(ApiException.class,
       ()->service.get(buyer.getId().toString(),admin.getPublicId())).getErrorCode());
 }

 @Test void lockedMerchantDisappearsFromPublicReadsAndCannotReceiveNewOrders() {
   User seller=user("Seller",UserRole.MERCHANT);
   MerchantProfile m=new MerchantProfile(seller);
   m.updateStoreInformation("Test store","description","MEAL",null,null);
   m.updateLocation("Test","Ward","District","City",BigDecimal.TEN,BigDecimal.TEN);merchants.saveAndFlush(m);
   Instant now=Instant.now();
   Deal d=deals.saveAndFlush(new Deal(m,"Meal","MEAL",BigDecimal.valueOf(100000),BigDecimal.valueOf(50000),
       5,"https://test.local/image",0,now.plusSeconds(10800),now.plusSeconds(14400),now.minusSeconds(60),now.plusSeconds(7200),DealStatus.ACTIVE));
   assertTrue(deals.findPublicDeal(d.getPublicId(),java.util.Set.of(DealStatus.ACTIVE),now).isPresent());
   service.change(subject(),seller.getPublicId(),"Full account lock",true);
   assertTrue(deals.findPublicDeal(d.getPublicId(),java.util.Set.of(DealStatus.ACTIVE),now).isEmpty());
   assertTrue(merchants.findPublicByUserPublicId(seller.getPublicId()).isEmpty());
   assertEquals(ErrorCode.DEAL_NOT_RESERVABLE,assertThrows(ApiException.class,
       ()->ordering.reserve(buyer.getId().toString(),UUID.randomUUID(),new CreateOrderRequest(d.getPublicId(),1,PaymentMethod.CASH))).getErrorCode());
   assertEquals(0,orders.countByBuyerIdAndStatus(buyer.getId(),OrderStatus.RESERVED));
 }

 @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
 void concurrentLockAndRefreshLeaveNoUsableTokenAndOnlyOneAudit() throws Exception {
   // NOT_SUPPORTED means @BeforeEach data is committed before worker threads begin.
   var login=signin.signin(new SigninRequest(buyer.getEmail(),"Password123!"));
   var pool=Executors.newFixedThreadPool(3);
   var go=new CountDownLatch(1);
   try {
     var a=pool.submit(()->{go.await();return service.change(subject(),buyer.getPublicId(),"Concurrent lock",true);});
     var b=pool.submit(()->{go.await();return service.change(subject(),buyer.getPublicId(),"Concurrent lock",true);});
     var refresh=pool.submit(()->{go.await();try{return sessions.refresh(login.refreshToken());}catch(ApiException ex){
       assertEquals(ErrorCode.INVALID_REFRESH_TOKEN,ex.getErrorCode());return null;}});
     go.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
     var result=refresh.get(10,TimeUnit.SECONDS);
     if(result!=null)assertThrows(JwtException.class,()->decoder.decode(result.accessToken()));
     assertThrows(JwtException.class,()->decoder.decode(login.accessToken()));
     assertEquals(1,service.history(subject(),buyer.getPublicId(),0,20).totalElements());
   }finally{pool.shutdownNow();}
 }
 @Autowired com.lootfast.backend.security.AccountJwtValidator accountValidator;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

 @Test void legacyJwtWorksOnlyUntilFirstAccountLock() {
   var legacy=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("legacy-token")
       .header("alg","HS256").subject(buyer.getId().toString()).build();
   assertFalse(accountValidator.validate(legacy).hasErrors());
   service.change(subject(),buyer.getPublicId(),"Lock legacy token",true);
   assertTrue(accountValidator.validate(legacy).hasErrors());
   service.change(subject(),buyer.getPublicId(),"Unlock without restoring token",false);
   assertTrue(accountValidator.validate(legacy).hasErrors());
 }

 @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
 void unrelatedConcurrentUserUpdateCannotEraseAccountLock() throws Exception {
   var loaded=new CountDownLatch(1);
   var locked=new CountDownLatch(1);
   var pool=Executors.newSingleThreadExecutor();
   try {
     var update=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions)
         .execute(status->{
           var stale=users.findById(buyer.getId()).orElseThrow();
           loaded.countDown();
           try {assertTrue(locked.await(5,TimeUnit.SECONDS));}
           catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
           stale.changePassword("test-replaced-password-hash");
           return null;
         }));
     assertTrue(loaded.await(5,TimeUnit.SECONDS));
     service.change(subject(),buyer.getPublicId(),"Concurrent security action",true);
     locked.countDown();update.get(10,TimeUnit.SECONDS);
     User persisted=users.findById(buyer.getId()).orElseThrow();
     assertTrue(persisted.isLocked());
     assertEquals(1,persisted.getAuthenticationVersion());
   }finally{locked.countDown();pool.shutdownNow();}
 }

 @Autowired RefreshTokenCodec refreshCodec;
 @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
 void logoutIsIdempotentForLockedAndUnknownSessions() {
   var login=signin.signin(new SigninRequest(buyer.getEmail(),"Password123!"));
   service.change(subject(),buyer.getPublicId(),"Logout locked account",true);
   assertDoesNotThrow(()->sessions.logout(login.refreshToken()));
   var unknown=refreshCodec.issue(UUID.randomUUID());
   assertDoesNotThrow(()->sessions.logout(unknown.value()));
 }

}
