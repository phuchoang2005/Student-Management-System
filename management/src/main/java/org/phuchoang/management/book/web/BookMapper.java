package org.phuchoang.management.book.web;

import org.mapstruct.Mapper;
import org.phuchoang.management.book.application.BookService.AddedBook;
import org.phuchoang.management.book.application.BookService.AssignedBook;
import org.phuchoang.management.book.application.BookService.BookDetailView;
import org.phuchoang.management.book.application.BookService.BookSummaryView;
import org.phuchoang.management.book.application.BookService.UnassignedBook;
import org.phuchoang.management.book.application.command.AddBookCommand;
import org.phuchoang.management.book.application.command.AssignBookOwnerCommand;
import org.phuchoang.management.book.web.dto.BookCreateRequest;
import org.phuchoang.management.book.web.dto.BookDetailDto;
import org.phuchoang.management.book.web.dto.BookOwnerDto;
import org.phuchoang.management.book.web.dto.BookOwnerRequest;
import org.phuchoang.management.book.web.dto.BookResponse;
import org.phuchoang.management.book.web.dto.BookSummaryDto;
import org.phuchoang.management.student.StudentSummary;

@Mapper(componentModel = "spring")
public interface BookMapper {

  AddBookCommand toCommand(BookCreateRequest request);

  AssignBookOwnerCommand toCommand(BookOwnerRequest request);

  BookResponse toResponse(AddedBook added);

  BookResponse toResponse(AssignedBook assigned);

  BookResponse toResponse(UnassignedBook unassigned);

  BookSummaryDto toSummaryDto(BookSummaryView view);

  BookDetailDto toDetailDto(BookDetailView view);

  BookOwnerDto toDto(StudentSummary summary);
}
