package org.phuchoang.management.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.shared.exception.DomainValidationException;

class CourseTest {

  private final CourseCode code = new CourseCode("CS101");
  private final Credits credits = new Credits(3);

  @Test
  void createsCourseWithGeneratedTimestampsAndZeroVersion() {
    Course course = Course.create(code, "Intro to CS", "Basics", credits);

    assertThat(course.id()).isNull();
    assertThat(course.code()).isEqualTo(code);
    assertThat(course.name()).isEqualTo("Intro to CS");
    assertThat(course.description()).isEqualTo("Basics");
    assertThat(course.credits()).isEqualTo(credits);
    assertThat(course.createdAt()).isNotNull().isEqualTo(course.updatedAt());
    assertThat(course.version()).isZero();
  }

  @Test
  void createAcceptsNullDescription() {
    Course course = Course.create(code, "Intro to CS", null, credits);

    assertThat(course.description()).isNull();
  }

  @Test
  void createRejectsBlankName() {
    assertThatThrownBy(() -> Course.create(code, " ", "Basics", credits))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void applyChangesUpdatesFieldsAndTimestampButNotCode() {
    Course course = Course.create(code, "Intro to CS", "Basics", credits);
    Credits newCredits = new Credits(4);

    course.applyChanges("Advanced CS", "Deeper dive", newCredits);

    assertThat(course.code()).isEqualTo(code);
    assertThat(course.name()).isEqualTo("Advanced CS");
    assertThat(course.description()).isEqualTo("Deeper dive");
    assertThat(course.credits()).isEqualTo(newCredits);
    assertThat(course.updatedAt()).isAfterOrEqualTo(course.createdAt());
  }

  @Test
  void applyChangesRejectsBlankName() {
    Course course = Course.create(code, "Intro to CS", "Basics", credits);

    assertThatThrownBy(() -> course.applyChanges(" ", "Basics", credits))
        .isInstanceOf(DomainValidationException.class);
  }
}
