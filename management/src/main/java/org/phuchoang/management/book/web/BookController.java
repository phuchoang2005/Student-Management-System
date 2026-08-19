package org.phuchoang.management.book.web;

import jakarta.validation.Valid;
import org.phuchoang.management.book.application.BookService;
import org.phuchoang.management.book.web.dto.BookCreateRequest;
import org.phuchoang.management.book.web.dto.BookOwnerRequest;
import org.phuchoang.management.book.web.dto.BookResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
