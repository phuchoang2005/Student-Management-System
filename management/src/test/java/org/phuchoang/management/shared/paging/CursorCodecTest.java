package org.phuchoang.management.shared.paging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.exception.DomainValidationException;

class CursorCodecTest {

  @Test
  void roundTripsARawKey() {
    String encoded = CursorCodec.encode("STU-0042");

    assertThat(CursorCodec.decode(encoded)).isEqualTo("STU-0042");
  }

  @Test
  void encodeOfNullIsNull() {
    assertThat(CursorCodec.encode(null)).isNull();
  }

  @Test
  void decodeOfNullIsNull() {
    assertThat(CursorCodec.decode(null)).isNull();
  }

  @Test
  void decodeOfBlankIsNull() {
    assertThat(CursorCodec.decode("")).isNull();
  }

  @Test
  void decodeRejectsMalformedBase64() {
    assertThatThrownBy(() -> CursorCodec.decode("not-valid-base64url!!"))
        .isInstanceOf(DomainValidationException.class);
  }
}
