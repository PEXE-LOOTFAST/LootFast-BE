package com.lootfast.backend.content.service;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.storage.*;
import com.lootfast.backend.content.dto.ContentResponses.BannerView;
import com.lootfast.backend.content.repository.BannerRepository;
import java.time.Clock;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BannerImageService {
  private final ContentAccess access;
  private final BannerRepository banners;
  private final ImageFileValidator validator;
  private final ImageStorage storage;
  private final Clock clock;
  private final TransactionTemplate transactions;
  private final StringRedisTemplate redis;
  private static final DefaultRedisScript<Long> LIMIT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],3600) end;"
              + " return n",
          Long.class);

  public BannerImageService(
      ContentAccess access,
      BannerRepository banners,
      ImageFileValidator validator,
      ImageStorage storage,
      Clock clock,
      PlatformTransactionManager manager,
      StringRedisTemplate redis) {
    this.access = access;
    this.banners = banners;
    this.validator = validator;
    this.storage = storage;
    this.clock = clock;
    this.redis = redis;
    transactions = new TransactionTemplate(manager);
    transactions.setTimeout(5);
  }

  public BannerView upload(String subject, UUID id, long revision, MultipartFile file) {
    var actor = access.admin(subject);
    var readable = banners.findById(id).orElseThrow(ContentAccess::missing);
    ContentAccess.revision(readable.getRevision(), revision);
    byte[] bytes;
    try {
      bytes = validator.validateAndRead(file, 5 * 1024 * 1024L, 20_000_000L);
    } catch (ImageValidationException e) {
      throw new ApiException(
          e.getFailure() == ImageValidationFailure.FILE_TOO_LARGE
              ? ErrorCode.BANNER_IMAGE_TOO_LARGE
              : ErrorCode.INVALID_BANNER_IMAGE,
          "Use a valid JPEG/PNG image up to 5 MB and 20 million pixels");
    }
    try {
      Long count = redis.execute(LIMIT, List.of("rate:banner-upload:" + actor.getId()));
      if (count == null)
        throw new ApiException(ErrorCode.RESOURCE_BUSY, "Upload rate limiter unavailable");
      if (count > 10)
        throw new ApiException(
            ErrorCode.BANNER_IMAGE_RATE_LIMITED, "At most 10 banner uploads per hour");
    } catch (DataAccessException e) {
      throw new ApiException(ErrorCode.RESOURCE_BUSY, "Upload rate limiter unavailable");
    }
    StoredImage stored;
    try {
      stored =
          storage.upload(
              "lootfast/banners/" + id + "/" + UUID.randomUUID(),
              bytes,
              ImageUploadPreset.BANNER_IMAGE);
    } catch (ImageStorageException e) {
      throw new ApiException(
          ErrorCode.BANNER_IMAGE_UPLOAD_FAILED, "Banner image could not be uploaded");
    }
    UploadResult result;
    try {
      result =
          transactions.execute(
              status -> {
                access.admin(subject);
                var b = banners.lock(id).orElseThrow(ContentAccess::missing);
                ContentAccess.revision(b.getRevision(), revision);
                String old = b.getStorageKey();
                b.image(stored.url(), stored.storageKey(), clock.instant(), actor.getPublicId());
                banners.flush();
                return new UploadResult(BannerView.from(b), old);
              });
      if (result == null) throw new IllegalStateException("Image transaction returned no result");
    } catch (RuntimeException e) {
      cleanup(stored.storageKey());
      throw e;
    }
    cleanup(result.oldKey());
    return result.view();
  }

  private void cleanup(String key) {
    if (key == null) return;
    try {
      storage.delete(key);
    } catch (RuntimeException e) {
      LoggerFactory.getLogger(getClass())
          .warn("Banner image cleanup failed; storage reconciliation is required");
    }
  }

  private record UploadResult(BannerView view, String oldKey) {}
}
