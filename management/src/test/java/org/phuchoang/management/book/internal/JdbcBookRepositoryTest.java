package org.phuchoang.management.book.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.shared.paging.CursorCodec;
import org.phuchoang.management.shared.paging.CursorPage;
import org.phuchoang.management.student.StudentId;

/**
 * Unit coverage of the keyset-pagination trimming (PM-045) and FULLTEXT query sanitization
 * (PM-044) that {@link JdbcBookRepository} adds on top of {@link SpringDataBookRepository} — lives
 * in {@code internal} because both types are package-private, mirroring {@code
 * identity.internal.AesPasswordCipherTest}.
 */
@ExtendWith(MockitoExtension.class)
class JdbcBookRepositoryTest {

  @Mock private SpringDataBookRepository springRepo;

  private JdbcBookRepository repository;

  private static BookRow aRow(String isbn) {
    Instant now = Instant.now();
    return new BookRow(1L, isbn, "Some Title", "Some Author", LocalDate.of(2020, 1, 1), null, 0L, now, now);
  }

  @Test
  void findByOwnerIdReturnsNullCursorWhenFewerRowsThanLimitComeBack() {
    repository = new JdbcBookRepository(springRepo);
    when(springRepo.findByOwnerId(eq(1L), isNull(), eq(21)))
        .thenReturn(List.of(aRow("isbn-1"), aRow("isbn-2")));

    CursorPage<?> page = repository.findByOwnerId(new StudentId(1L), null, 20);

    assertThat(page.content()).hasSize(2);
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void findByOwnerIdReturnsNullCursorWhenExactlyLimitRowsComeBack() {
    // The off-by-one that matters: asking the Spring Data method for limit+1 rows and getting back
    // exactly `limit` means this was the last page, not "maybe more" -- only literally receiving
    // the extra (limit+1)th row means another page exists.
    repository = new JdbcBookRepository(springRepo);
    List<BookRow> exactlyLimit = List.of(aRow("isbn-1"), aRow("isbn-2"));
    when(springRepo.findByOwnerId(eq(1L), isNull(), eq(3))).thenReturn(exactlyLimit);

    CursorPage<?> page = repository.findByOwnerId(new StudentId(1L), null, 2);

    assertThat(page.content()).hasSize(2);
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void findByOwnerIdTrimsTheExtraRowAndEncodesTheCursorFromTheLastKeptRow() {
    repository = new JdbcBookRepository(springRepo);
    when(springRepo.findByOwnerId(eq(1L), isNull(), eq(3)))
        .thenReturn(List.of(aRow("isbn-1"), aRow("isbn-2"), aRow("isbn-3")));

    CursorPage<?> page = repository.findByOwnerId(new StudentId(1L), null, 2);

    assertThat(page.content()).hasSize(2);
    assertThat(page.nextCursor()).isNotNull();
    assertThat(CursorCodec.decode(page.nextCursor())).isEqualTo("isbn-2");
  }

  @Test
  void findByOwnerIdReturnsEmptyContentAndNullCursorWhenNothingComesBack() {
    repository = new JdbcBookRepository(springRepo);
    when(springRepo.findByOwnerId(eq(1L), eq("past-the-end"), eq(21))).thenReturn(List.of());

    CursorPage<?> page = repository.findByOwnerId(new StudentId(1L), "past-the-end", 20);

    assertThat(page.content()).isEmpty();
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void searchReturnsEmptyContentAndNullCursorWhenNothingMatches() {
    repository = new JdbcBookRepository(springRepo);
    when(springRepo.search(eq("+nobody*"), isNull(), isNull(), eq(21))).thenReturn(List.of());

    CursorPage<?> page = repository.search("nobody", null, null, 20);

    assertThat(page.content()).isEmpty();
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void searchTrimsTheExtraRowAndEncodesTheCursorFromTheLastKeptRow() {
    repository = new JdbcBookRepository(springRepo);
    when(springRepo.search(eq("+clean*"), isNull(), isNull(), eq(3)))
        .thenReturn(List.of(aRow("isbn-1"), aRow("isbn-2"), aRow("isbn-3")));

    CursorPage<?> page = repository.search("clean", null, null, 2);

    assertThat(page.content()).hasSize(2);
    assertThat(CursorCodec.decode(page.nextCursor())).isEqualTo("isbn-2");
  }

  @Test
  void searchBuildsAnAndPrefixBooleanQueryFromEachTokenAndDropsOperatorCharacters() {
    repository = new JdbcBookRepository(springRepo);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    when(springRepo.search(queryCaptor.capture(), isNull(), isNull(), anyInt())).thenReturn(List.of());

    repository.search("+foo-bar<baz>~qux*(a)\"b\"@c", null, null, 20);

    // Each non-alphanumeric character is a token boundary (operator characters included, so they
    // can never reach AGAINST as an invalid boolean-mode expression), every token is a required
    // prefix match, and single/double-character tokens ("a"/"b"/"c") are dropped since MySQL's
    // default innodb_ft_min_token_size (3) never indexes them.
    assertThat(queryCaptor.getValue()).isEqualTo("+foo* +bar* +baz* +qux*");
  }

  @Test
  void searchDropsTokensShorterThanTheMinFulltextTokenSize() {
    repository = new JdbcBookRepository(springRepo);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    when(springRepo.search(queryCaptor.capture(), isNull(), isNull(), anyInt())).thenReturn(List.of());

    // An ISBN's hyphen-separated segments tokenize individually; the 1-2 digit segments would
    // never be indexed and must not be required, or the whole query becomes unsatisfiable.
    repository.search("978-0-13-235088-4", null, null, 20);

    assertThat(queryCaptor.getValue()).isEqualTo("+978* +235088*");
  }

  @Test
  void searchRoutesANullOrBlankQueryToBrowseInsteadOfTheFulltextStatement() {
    // A null/blank query must never reach the MATCH-based `search` statement -- routing it there
    // via an "(:query IS NULL OR ...)" branch is exactly the combined-query shape that regressed
    // the no-filter case (docs-v01/Benchmark/09-v01-vs-v00-conclusions.md §3, BM-STU-001 +191%).
    repository = new JdbcBookRepository(springRepo);
    when(springRepo.browse(isNull(), isNull(), anyInt())).thenReturn(List.of());

    repository.search(null, null, null, 20);
    repository.search("", null, null, 20);

    verify(springRepo, times(2)).browse(isNull(), isNull(), eq(21));
    verify(springRepo, never()).search(any(), any(), any(), anyInt());
  }
}
