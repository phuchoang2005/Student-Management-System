package org.phuchoang.management.book.web;

import org.mapstruct.Mapper;
import org.phuchoang.management.book.application.BookService.AddedBook;
import org.phuchoang.management.book.application.command.AddBookCommand;
import org.phuchoang.management.book.web.dto.BookCreateRequest;
import org.phuchoang.management.book.web.dto.BookResponse;

@Mapper(componentModel = "spring")
public interface BookMapper {

  AddBookCommand toCommand(BookCreateRequest request);

  BookResponse toResponse(AddedBook added);
}
