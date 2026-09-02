package org.phuchoang.management.shared.paging;

import java.util.List;
import java.util.function.Function;

/**
 * The cursor-pagination envelope every converted list endpoint returns, in place of Spring Data's
 * {@code Page}/{@code PageResponse} (api-specification.md's pagination decision, successor to
 * decision #8, PM-045). {@code nextCursor} is {@code null} exactly when there is no further page —
 * deliberately no separate {@code hasMore} flag, since a second field could drift out of sync with
 * it for no benefit.
 */
public record CursorPage<T>(List<T> content, String nextCursor) {

  public <R> CursorPage<R> map(Function<T, R> mapper) {
    return new CursorPage<>(content.stream().map(mapper).toList(), nextCursor);
  }
}
