package org.phuchoang.management.course.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.course.application.CourseService;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.web.GlobalExceptionHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class CourseControllerTest {

  @Mock private CourseService courseService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    CourseController controller = new CourseController(courseService, new CourseMapperImpl());
    mockMvc =
        standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(
                new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
            .build();
  }

  private static CourseService.CreatedCourse aCreatedCourse() {
    Instant now = Instant.now();
    return new CourseService.CreatedCourse(1L, "CS101", "Intro to CS", "Basics", 3, now, now);
  }

  private static CourseService.UpdatedCourse anUpdatedCourse() {
    Instant now = Instant.now();
    return new CourseService.UpdatedCourse(1L, "CS101", "Advanced CS", "Deeper dive", 4, now, now);
  }

  private static final String VALID_BODY =
      """
      {"courseCode":"CS101","name":"Intro to CS","description":"Basics","credits":3}
      """;

  private static final String VALID_UPDATE_BODY =
      """
      {"name":"Advanced CS","description":"Deeper dive","credits":4}
      """;

  @Test
  void createCourseReturns201WithCourseDetails() throws Exception {
    when(courseService.create(any())).thenReturn(aCreatedCourse());

    mockMvc
        .perform(post("/api/v1/courses").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.courseCode").value("CS101"))
        .andExpect(jsonPath("$.name").value("Intro to CS"))
        .andExpect(jsonPath("$.credits").value(3));
  }

  @Test
  void createCoursePropagatesDuplicateCodeAs409() throws Exception {
    when(courseService.create(any()))
        .thenThrow(new DuplicateCodeException("Course code 'CS101' is already in use."));

    mockMvc
        .perform(post("/api/v1/courses").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isConflict());
  }

  @Test
  void createCourseRejectsBlankNameBeforeReachingService() throws Exception {
    String body = """
        {"courseCode":"CS101","name":"","description":"Basics","credits":3}
        """;

    mockMvc
        .perform(post("/api/v1/courses").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createCourseRejectsNonPositiveCreditsBeforeReachingService() throws Exception {
    String body = """
        {"courseCode":"CS101","name":"Intro to CS","description":"Basics","credits":0}
        """;

    mockMvc
        .perform(post("/api/v1/courses").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateCourseReturns200WithCourseDetails() throws Exception {
    when(courseService.update(eq("CS101"), any())).thenReturn(anUpdatedCourse());

    mockMvc
        .perform(
            put("/api/v1/courses/CS101")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_UPDATE_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courseCode").value("CS101"))
        .andExpect(jsonPath("$.name").value("Advanced CS"))
        .andExpect(jsonPath("$.credits").value(4));
  }

  @Test
  void updateCoursePropagatesNotFoundAs404() throws Exception {
    when(courseService.update(eq("does-not-exist"), any()))
        .thenThrow(new NotFoundException("Course 'does-not-exist' does not exist."));

    mockMvc
        .perform(
            put("/api/v1/courses/does-not-exist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_UPDATE_BODY))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateCourseRejectsBlankNameBeforeReachingService() throws Exception {
    String body = """
        {"name":"","description":"Deeper dive","credits":4}
        """;

    mockMvc
        .perform(put("/api/v1/courses/CS101").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateCourseRejectsNonPositiveCreditsBeforeReachingService() throws Exception {
    String body = """
        {"name":"Advanced CS","description":"Deeper dive","credits":0}
        """;

    mockMvc
        .perform(put("/api/v1/courses/CS101").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void removeCourseReturns204() throws Exception {
    doNothing().when(courseService).remove("CS101");

    mockMvc.perform(delete("/api/v1/courses/CS101")).andExpect(status().isNoContent());

    verify(courseService).remove("CS101");
  }

  @Test
  void removeCoursePropagatesNotFoundAs404() throws Exception {
    doThrow(new NotFoundException("Course 'CS101' does not exist."))
        .when(courseService)
        .remove("CS101");

    mockMvc.perform(delete("/api/v1/courses/CS101")).andExpect(status().isNotFound());
  }

  private static final CourseService.CourseSummaryView A_SUMMARY =
      new CourseService.CourseSummaryView(1L, "CS101", "Intro to CS", 3);

  @Test
  void searchCoursesReturnsPagedSummaries() throws Exception {
    Page<CourseService.CourseSummaryView> page =
        new PageImpl<>(List.of(A_SUMMARY), PageRequest.of(0, 20), 1);
    when(courseService.search(any(), any())).thenReturn(page);

    mockMvc
        .perform(get("/api/v1/courses").param("query", "cs101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].courseCode").value("CS101"));
  }

  @Test
  void searchCoursesReturnsEmptyContentWhenNoMatch() throws Exception {
    when(courseService.search(any(), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(get("/api/v1/courses").param("query", "nobody"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  private static CourseService.CourseDetailView aCourseDetailView() {
    Instant now = Instant.now();
    return new CourseService.CourseDetailView(
        1L, "CS101", "Intro to CS", "Basics", 3, now, now, List.of());
  }

  @Test
  void getCourseReturnsDetailWithEmptyRoster() throws Exception {
    when(courseService.getDetail("CS101")).thenReturn(aCourseDetailView());

    mockMvc
        .perform(get("/api/v1/courses/CS101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courseCode").value("CS101"))
        .andExpect(jsonPath("$.roster").isArray())
        .andExpect(jsonPath("$.roster").isEmpty());
  }

  @Test
  void getCoursePropagatesNotFoundAs404() throws Exception {
    when(courseService.getDetail("CS101"))
        .thenThrow(new NotFoundException("Course 'CS101' does not exist."));

    mockMvc
        .perform(get("/api/v1/courses/CS101"))
        .andExpect(status().isNotFound());
  }
}
