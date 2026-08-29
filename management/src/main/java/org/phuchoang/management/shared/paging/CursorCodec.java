package org.phuchoang.management.shared.paging;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.phuchoang.management.shared.exception.DomainValidationException;

/**
 * Opaque Base64url encoding for keyset-pagination cursors (PM-045). Deliberately format-agnostic:
 * a module whose sort key isn't a single string — {@code enrollment}'s compound
 * {@code (enrolled_at, id)} key — builds and parses its own delimited raw string and passes it
 * through here unchanged. Encoding happens in the {@code internal} layer, where the last fetched
 * row is in hand; decoding happens in the {@code application} layer, mirroring how a raw request
 * string is turned into a domain concept everywhere else in this codebase (e.g. {@code new
 * StudentCode(code)}).
 */
public final class CursorCodec {

  private CursorCodec() {}

  public static String encode(String rawKey) {
    if (rawKey == null) {
      return null;
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey.getBytes(StandardCharsets.UTF_8));
  }

  public static String decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new DomainValidationException("Malformed pagination cursor.");
    }
  }
}
