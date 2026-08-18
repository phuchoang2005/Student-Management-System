package org.phuchoang.management.shared.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** {@code PageMeta} + {@code content} envelope (api-specification.md's `PageMeta` schema) every paginated list/roster endpoint returns, in place of Spring Data's own {@code Page} JSON shape. */
public record PageResponse<T>(int page, int size, long totalElements, int totalPages, List<T> content) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.getContent());
  }
}
