package com.lootfast.backend.content.service;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.content.dto.ContentRequests.*;
import com.lootfast.backend.content.dto.ContentResponses.*;
import com.lootfast.backend.content.entity.Category;
import com.lootfast.backend.content.repository.CategoryRepository;
import java.time.Clock;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
  private final CategoryRepository categories;
  private final ContentAccess access;
  private final Clock clock;

  public CategoryService(CategoryRepository categories, ContentAccess access, Clock clock) {
    this.categories = categories;
    this.access = access;
    this.clock = clock;
  }

  @Transactional(readOnly = true, timeout = 5)
  public PagedResponse<CategoryView> list(boolean admin, String subject, int page, int size) {
    if (admin) access.admin(subject);
    var p = ContentAccess.page(page, size, "sortOrder", "code");
    return PagedResponse.from(
        (admin ? categories.findAll(p) : categories.findByEnabledTrue(p)).map(CategoryView::from));
  }

  @Transactional(timeout = 5)
  public CategoryView create(String subject, CategoryCreate r) {
    var actor = access.admin(subject);
    String code = r.code().trim().toUpperCase(Locale.ROOT);
    if (!code.matches("[A-Z0-9_]{1,100}"))
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Use letters, digits and underscores for category code");
    if (categories.existsById(code))
      throw new ApiException(ErrorCode.CONTENT_CONFLICT, "Category code already exists");
    Category c = new Category(code);
    c.configure(r.name().trim(), r.enabled(), r.sortOrder(), clock.instant(), actor.getPublicId());
    return CategoryView.from(categories.saveAndFlush(c));
  }

  @Transactional(timeout = 5)
  public CategoryView edit(String subject, String code, CategoryEdit r) {
    var actor = access.admin(subject);
    Category c = categories.lock(code).orElseThrow(ContentAccess::missing);
    ContentAccess.revision(c.getRevision(), r.expectedRevision());
    c.configure(r.name().trim(), r.enabled(), r.sortOrder(), clock.instant(), actor.getPublicId());
    categories.flush();
    return CategoryView.from(c);
  }

  // The read lock stays until the caller's deal/profile transaction commits.
  @Transactional(propagation = Propagation.MANDATORY, timeout = 5)
  public void requireEnabled(String code) {
    Category c =
        categories
            .lockReadable(code.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(
                () -> new ApiException(ErrorCode.CATEGORY_UNAVAILABLE, "Category does not exist"));
    if (!c.isEnabled())
      throw new ApiException(
          ErrorCode.CATEGORY_UNAVAILABLE, "Category is disabled; select another category");
  }
}
