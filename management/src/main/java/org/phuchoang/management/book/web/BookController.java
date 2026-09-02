package org.phuchoang.management.book.web;

import jakarta.validation.Valid;
import org.phuchoang.management.book.application.BookService;
import org.phuchoang.management.book.web.dto.BookCreateRequest;
import org.phuchoang.management.book.web.dto.BookDetailDto;
import org.phuchoang.management.book.web.dto.BookOwnerRequest;
import org.phuchoang.management.book.web.dto.BookResponse;
import org.phuchoang.management.book.web.dto.BookSummaryDto;
import org.phuchoang.management.shared.paging.CursorPage;
import org.phuchoang.management.shared.security.AuthenticatedPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/books")
public class BookController {

  private final BookService bookService;
  private final BookMapper mapper;

  public BookController(BookService bookService, BookMapper mapper) {
    this.bookService = bookService;
    this.mapper = mapper;
  }

  @GetMapping
  public CursorPage<BookSummaryDto> searchBooks(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String ownerStudentCode,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    Long callerStudentId = AuthenticatedPrincipal.studentIdOf(authentication);
    int clampedSize = Math.min(Math.max(size, 1), 100);
    return bookService
        .search(query, ownerStudentCode, cursor, clampedSize, callerStudentId)
        .map(mapper::toSummaryDto);
  }

  @GetMapping("/{isbn}")
  public BookDetailDto getBook(@PathVariable String isbn, Authentication authentication) {
    Long callerStudentId = AuthenticatedPrincipal.studentIdOf(authentication);
    return mapper.toDetailDto(bookService.getDetail(isbn, callerStudentId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BookResponse addBook(@Valid @RequestBody BookCreateRequest request) {
    BookService.AddedBook added = bookService.addBook(mapper.toCommand(request));
    return mapper.toResponse(added);
  }

  @PatchMapping("/{isbn}/owner")
  public BookResponse assignBookOwner(
      @PathVariable String isbn, @Valid @RequestBody BookOwnerRequest request) {
    BookService.AssignedBook assigned = bookService.assignOwner(isbn, mapper.toCommand(request));
    return mapper.toResponse(assigned);
  }

  @DeleteMapping("/{isbn}/owner")
  public BookResponse clearBookOwner(@PathVariable String isbn) {
    BookService.UnassignedBook unassigned = bookService.unassignOwner(isbn);
    return mapper.toResponse(unassigned);
  }

  @DeleteMapping("/{isbn}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeBook(@PathVariable String isbn) {
    bookService.remove(isbn);
  }
}
