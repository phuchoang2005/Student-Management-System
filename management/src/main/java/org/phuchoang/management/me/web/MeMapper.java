package org.phuchoang.management.me.web;

import org.mapstruct.Mapper;
import org.phuchoang.management.book.BookSummary;
import org.phuchoang.management.course.CourseSummary;
import org.phuchoang.management.me.web.dto.MeBookSummaryDto;
import org.phuchoang.management.me.web.dto.MeCourseSummaryDto;
import org.phuchoang.management.me.web.dto.MeProfileDto;
import org.phuchoang.management.student.StudentProfile;

@Mapper(componentModel = "spring")
public interface MeMapper {

  MeProfileDto toDto(StudentProfile profile);

  MeBookSummaryDto toDto(BookSummary summary);

  MeCourseSummaryDto toDto(CourseSummary summary);
}
