package org.phuchoang.management.me.web;

import org.mapstruct.Mapper;
import org.phuchoang.management.book.BookSummary;
import org.phuchoang.management.course.CourseSummary;
import org.phuchoang.management.me.web.dto.MeBookSummaryDto;
import org.phuchoang.management.me.web.dto.MeCourseSummaryDto;

@Mapper(componentModel = "spring")
public interface MeMapper {

  MeBookSummaryDto toDto(BookSummary summary);

  MeCourseSummaryDto toDto(CourseSummary summary);
}
