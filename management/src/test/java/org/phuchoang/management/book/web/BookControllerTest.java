package org.phuchoang.management.book.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.book.application.BookService;
import org.phuchoang.management.shared.exception.DuplicateIsbnException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.exception.UnknownStudentException;
import org.phuchoang.management.shared.web.GlobalExceptionHandler;
import org.phuchoang.management.student.StudentSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class BookControllerTest {

  @Mock private BookService bookService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    BookController controller = new BookController(bookService, new BookMapperImpl());
    mockMvc =
        standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(
                new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
            .build();
  }

  private static BookService.AddedBook anAddedBook() {
    Instant now = Instant.now();
    return new BookService.AddedBook(
        1L, "978-0-13-468599-1", "Clean Architecture", "Robert C. Martin",
        LocalDate.of(2017, 9, 20), null, now, now);
  }

  private static final String VALID_BODY =
      """
      {"isbn":"978-0-13-468599-1","title":"Clean Architecture","author":"Robert C. Martin","publishedDate":"2017-09-20"}
      """;

  @Test
  void addBookReturns201WithBookDetails() throws Exception {
    when(bookService.addBook(any())).thenReturn(anAddedBook());

    mockMvc
        .perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isbn").value("978-0-13-468599-1"))
        .andExpect(jsonPath("$.title").value("Clean Architecture"))
        .andExpect(jsonPath("$.author").value("Robert C. Martin"))
        .andExpect(jsonPath("$.ownerId").doesNotExist());
  }

  @Test
  void addBookPropagatesDuplicateIsbnAs409() throws Exception {
    when(bookService.addBook(any()))
        .thenThrow(new DuplicateIsbnException("ISBN '978-0-13-468599-1' is already in use."));

    mockMvc
        .perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isConflict());
  }

  @Test
  void addBookPropagatesUnknownOwnerAs400() throws Exception {
    when(bookService.addBook(any()))
        .thenThrow(new UnknownStudentException("Student '99' does not exist."));

    String body =
        """
        {"isbn":"978-0-13-468599-1","title":"Clean Architecture","author":"Robert C. Martin","ownerId":99}
        """;

    mockMvc
        .perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void addBookRejectsBlankTitleBeforeReachingService() throws Exception {
    String body =
        """
        {"isbn":"978-0-13-468599-1","title":"","author":"Robert C. Martin"}
        """;

    mockMvc
        .perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void addBookRejectsBlankIsbnBeforeReachingService() throws Exception {
    String body =
        """
        {"isbn":"","title":"Clean Architecture","author":"Robert C. Martin"}
        """;

    mockMvc
        .perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  private static BookService.AssignedBook anAssignedBook() {
    Instant now = Instant.now();
    return new BookService.AssignedBook(
        1L, "978-0-13-468599-1", "Clean Architecture", "Robert C. Martin",
        LocalDate.of(2017, 9, 20), 1L, now, now);
  }

  @Test
  void assignBookOwnerReturns200WithNewOwner() throws Exception {
    when(bookService.assignOwner(any(), any())).thenReturn(anAssignedBook());

    mockMvc
        .perform(
            patch("/api/v1/books/978-0-13-468599-1/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentId":1}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ownerId").value(1));
  }

  @Test
  void assignBookOwnerPropagatesUnknownBookAs404() throws Exception {
    when(bookService.assignOwner(any(), any()))
        .thenThrow(new NotFoundException("Book '978-0-13-468599-1' does not exist."));

    mockMvc
        .perform(
            patch("/api/v1/books/978-0-13-468599-1/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentId":1}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void assignBookOwnerPropagatesUnknownStudentAs400() throws Exception {
    when(bookService.assignOwner(any(), any()))
        .thenThrow(new UnknownStudentException("Student '99' does not exist."));

    mockMvc
        .perform(
            patch("/api/v1/books/978-0-13-468599-1/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentId":99}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void assignBookOwnerRejectsMissingStudentIdBeforeReachingService() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/books/978-0-13-468599-1/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  private static BookService.UnassignedBook anUnassignedBook() {
    Instant now = Instant.now();
    return new BookService.UnassignedBook(
        1L, "978-0-13-468599-1", "Clean Architecture", "Robert C. Martin",
        LocalDate.of(2017, 9, 20), now, now);
  }

  @Test
  void clearBookOwnerReturns200WithNoOwner() throws Exception {
    when(bookService.unassignOwner("978-0-13-468599-1")).thenReturn(anUnassignedBook());

    mockMvc
        .perform(delete("/api/v1/books/978-0-13-468599-1/owner"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ownerId").doesNotExist());
  }

  @Test
  void clearBookOwnerPropagatesUnknownBookAs404() throws Exception {
    when(bookService.unassignOwner("978-0-13-468599-1"))
        .thenThrow(new NotFoundException("Book '978-0-13-468599-1' does not exist."));

    mockMvc
        .perform(delete("/api/v1/books/978-0-13-468599-1/owner"))
        .andExpect(status().isNotFound());
  }

  @Test
  void removeBookReturns204() throws Exception {
    mockMvc.perform(delete("/api/v1/books/978-0-13-468599-1")).andExpect(status().isNoContent());

    verify(bookService).remove("978-0-13-468599-1");
  }

  @Test
  void removeBookPropagatesUnknownBookAs404() throws Exception {
    doThrow(new NotFoundException("Book '978-0-13-468599-1' does not exist."))
        .when(bookService)
        .remove("978-0-13-468599-1");

    mockMvc.perform(delete("/api/v1/books/978-0-13-468599-1")).andExpect(status().isNotFound());
  }

  private static final BookService.BookSummaryView A_SUMMARY =
      new BookService.BookSummaryView(1L, "978-0-13-468599-1", "Clean Architecture", "Robert C. Martin", 1L);

  @Test
  void searchBooksReturnsPagedSummaries() throws Exception {
    Page<BookService.BookSummaryView> page =
        new PageImpl<>(List.of(A_SUMMARY), PageRequest.of(0, 20), 1);
    when(bookService.search(any(), any(), any(), any())).thenReturn(page);

    mockMvc
        .perform(get("/api/v1/books").param("query", "clean"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].isbn").value("978-0-13-468599-1"))
        .andExpect(jsonPath("$.content[0].ownerId").value(1));
  }

  @Test
  void searchBooksReturnsEmptyContentWhenNoMatch() throws Exception {
    when(bookService.search(any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(get("/api/v1/books").param("query", "nobody"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  private static BookService.BookDetailView anUnownedBookDetail() {
    Instant now = Instant.now();
    return new BookService.BookDetailView(
        1L, "978-0-13-468599-1", "Clean Architecture", "Robert C. Martin",
        LocalDate.of(2017, 9, 20), null, now, now, null);
  }

  private static BookService.BookDetailView anOwnedBookDetail() {
    Instant now = Instant.now();
    StudentSummary owner = new StudentSummary(1L, "S00101", "Amy", "Lee", "amy.lee@example.edu");
    return new BookService.BookDetailView(
        1L, "978-0-13-468599-1", "Clean Architecture", "Robert C. Martin",
        LocalDate.of(2017, 9, 20), 1L, now, now, owner);
  }

  @Test
  void getBookReturnsDetailWithNullOwnerWhenUnowned() throws Exception {
    when(bookService.getDetail(eq("978-0-13-468599-1"), any())).thenReturn(anUnownedBookDetail());

    mockMvc
        .perform(get("/api/v1/books/978-0-13-468599-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isbn").value("978-0-13-468599-1"))
        .andExpect(jsonPath("$.owner").doesNotExist());
  }

  @Test
  void getBookReturnsDetailWithOwnerSummaryWhenOwned() throws Exception {
    when(bookService.getDetail(eq("978-0-13-468599-1"), any())).thenReturn(anOwnedBookDetail());

    mockMvc
        .perform(get("/api/v1/books/978-0-13-468599-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owner.studentCode").value("S00101"))
        .andExpect(jsonPath("$.owner.firstName").value("Amy"));
  }

  @Test
  void getBookPropagatesNotFoundAs404() throws Exception {
    when(bookService.getDetail(eq("978-0-13-468599-1"), any()))
        .thenThrow(new NotFoundException("Book '978-0-13-468599-1' does not exist."));

    mockMvc.perform(get("/api/v1/books/978-0-13-468599-1")).andExpect(status().isNotFound());
  }
}
