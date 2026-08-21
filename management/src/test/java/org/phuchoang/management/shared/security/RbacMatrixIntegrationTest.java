package org.phuchoang.management.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PM-010 (04-sprint-backlog.md §6) — the RBAC role×endpoint matrix, {@code cross-cutting.md} §1,
 * TC-XC-001–008. {@code SecurityConfig}'s {@code authorizeHttpRequests} rules were already correct;
 * this file adds the test coverage the backlog calls for. First use of {@code @ParameterizedTest}/
 * {@code @MethodSource} in this codebase, per the backlog's explicit "parameterized test data
 * source" ask — kept to plain {@code @MethodSource} (no {@code @CsvSource}, no custom {@code
 * ArgumentsProvider}) to match the codebase's terse style.
 *
 * <p>Uses {@code @Testcontainers} (unlike {@code SecurityConfigTest}, which has none) purely so a
 * real {@code SpringBootTest} context is available; every assertion here happens at the
 * filter-chain level before a request reaches a controller, so no fixture data is created — a
 * 403/401 for the wrong role, or a 200 on an unfiltered/empty search, doesn't depend on any row
 * existing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RbacMatrixIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;

  // TC-XC-001–004 (subsumes TC-XC-005: STUDENT already appears as a non-owning role for every
  // resource below, so no separate "STUDENT can never write" case is needed).
  @ParameterizedTest(name = "{2} cannot {0} {1}")
  @MethodSource("nonOwningRoleWriteAttempts")
  void nonOwningRolesCannotWrite(HttpMethod method, String path, String role) throws Exception {
    mockMvc
        .perform(
            request(method, path)
                .with(user("caller").roles(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  static Stream<Arguments> nonOwningRoleWriteAttempts() {
    return Stream.of(
            studentWrites("LIBRARIAN"), studentWrites("COURSE_ADMINISTRATOR"), studentWrites("STUDENT"),
            bookWrites("REGISTRAR"), bookWrites("COURSE_ADMINISTRATOR"), bookWrites("STUDENT"),
            courseWrites("REGISTRAR"), courseWrites("LIBRARIAN"), courseWrites("STUDENT"),
            enrollmentWrites("LIBRARIAN"), enrollmentWrites("COURSE_ADMINISTRATOR"), enrollmentWrites("STUDENT"))
        .flatMap(s -> s);
  }

  private static Stream<Arguments> studentWrites(String role) {
    return Stream.of(
        Arguments.of(HttpMethod.POST, "/api/v1/students", role),
        Arguments.of(HttpMethod.PUT, "/api/v1/students/S00001", role),
        Arguments.of(HttpMethod.DELETE, "/api/v1/students/S00001", role));
  }

  private static Stream<Arguments> bookWrites(String role) {
    return Stream.of(
        Arguments.of(HttpMethod.POST, "/api/v1/books", role),
        Arguments.of(HttpMethod.PUT, "/api/v1/books/000-0", role),
        Arguments.of(HttpMethod.PATCH, "/api/v1/books/000-0/owner", role),
        Arguments.of(HttpMethod.DELETE, "/api/v1/books/000-0", role));
  }

  private static Stream<Arguments> courseWrites(String role) {
    return Stream.of(
        Arguments.of(HttpMethod.POST, "/api/v1/courses", role),
        Arguments.of(HttpMethod.PUT, "/api/v1/courses/CS101", role),
        Arguments.of(HttpMethod.DELETE, "/api/v1/courses/CS101", role));
  }

  private static Stream<Arguments> enrollmentWrites(String role) {
    return Stream.of(
        Arguments.of(HttpMethod.POST, "/api/v1/enrollments", role),
        Arguments.of(HttpMethod.DELETE, "/api/v1/enrollments/S00001/CS101", role));
  }

  // TC-XC-007
  @ParameterizedTest(name = "{0} cannot view a student's initial password")
  @MethodSource("nonRegistrarInitialPasswordAttempts")
  void onlyRegistrarCanViewInitialPassword(String role) throws Exception {
    mockMvc
        .perform(get("/api/v1/students/S00001/initial-password").with(user("caller").roles(role)))
        .andExpect(status().isForbidden());
  }

  static Stream<Arguments> nonRegistrarInitialPasswordAttempts() {
    return Stream.of(Arguments.of("LIBRARIAN"), Arguments.of("COURSE_ADMINISTRATOR"), Arguments.of("STUDENT"));
  }

  // TC-XC-006. Read access is granted per resource, not as one undifferentiated "domain read":
  // each role reads only what its own work needs (02-component-diagram.md §4). Detail-endpoint 200
  // coverage per role already exists in StudentLookupIntegrationTest / BookLookupIntegrationTest /
  // EnrollmentLookupIntegrationTest -- this completes the search-endpoint slice of the matrix, in
  // both directions.
  @ParameterizedTest(name = "{0} can read {1}")
  @MethodSource("grantedReads")
  void grantedRolesCanReachTheirSearchEndpoints(String role, String path) throws Exception {
    mockMvc.perform(get(path).with(user("caller").roles(role))).andExpect(status().isOk());
  }

  @ParameterizedTest(name = "{0} cannot read {1}")
  @MethodSource("deniedReads")
  void everyOtherRoleIsForbiddenFromReadingIt(String role, String path) throws Exception {
    mockMvc.perform(get(path).with(user("caller").roles(role))).andExpect(status().isForbidden());
  }

  private static final String[] DOMAIN_ROLES = {
    "REGISTRAR", "LIBRARIAN", "COURSE_ADMINISTRATOR", "STUDENT"
  };

  /** resource path -> the roles granted a read on it, mirroring SecurityConfig's GET allow-lists. */
  private static final Map<String, List<String>> READ_MATRIX =
      Map.of(
          "/api/v1/students", List.of("REGISTRAR", "LIBRARIAN", "COURSE_ADMINISTRATOR", "STUDENT"),
          "/api/v1/books", List.of("LIBRARIAN", "STUDENT"),
          "/api/v1/courses", List.of("REGISTRAR", "COURSE_ADMINISTRATOR", "STUDENT"),
          "/api/v1/enrollments", List.of("REGISTRAR", "COURSE_ADMINISTRATOR"));

  /**
   * The three resources whose bare search endpoint is a {@code 200} for a granted role.
   * {@code /api/v1/enrollments} is excluded because an unfiltered call there is a {@code 400} by
   * design — {@link #grantedRolesReachEnrollmentSearchAndAreRejectedOnItsTermsNotTheFilterChains}
   * covers its granted direction instead.
   */
  static Stream<Arguments> grantedReads() {
    Stream.Builder<Arguments> args = Stream.builder();
    READ_MATRIX.forEach(
        (path, roles) -> {
          if (!path.equals("/api/v1/enrollments")) {
            roles.forEach(role -> args.add(Arguments.of(role, path)));
          }
        });
    return args.build();
  }

  static Stream<Arguments> deniedReads() {
    Stream.Builder<Arguments> args = Stream.builder();
    READ_MATRIX.forEach(
        (path, granted) -> {
          for (String role : DOMAIN_ROLES) {
            if (!granted.contains(role)) {
              args.add(Arguments.of(role, path));
            }
          }
        });
    return args.build();
  }

  /**
   * The granted half of the enrollment read row. A {@code 400} here is the point: it means the
   * request reached {@code EnrollmentService.search} and was rejected for missing its required
   * filter, rather than being stopped at the filter chain with a {@code 403} the way a Librarian or
   * a Student is.
   */
  @ParameterizedTest(name = "{0} reaches enrollment search")
  @ValueSource(strings = {"REGISTRAR", "COURSE_ADMINISTRATOR"})
  void grantedRolesReachEnrollmentSearchAndAreRejectedOnItsTermsNotTheFilterChains(String role)
      throws Exception {
    mockMvc
        .perform(get("/api/v1/enrollments").with(user("caller").roles(role)))
        .andExpect(status().isBadRequest());
  }

  // TC-XC-008, write slice only -- the GET slice and staff-accounts cases (TC-XC-039–042) are
  // already covered by SecurityConfigTest.
  @ParameterizedTest(name = "unauthenticated caller cannot {0} {1}")
  @MethodSource("unauthenticatedWriteAttempts")
  void unauthenticatedCallerCannotWrite(HttpMethod method, String path) throws Exception {
    MvcResult result =
        mockMvc
            .perform(request(method, path).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }

  static Stream<Arguments> unauthenticatedWriteAttempts() {
    return Stream.of(
        Arguments.of(HttpMethod.POST, "/api/v1/students"),
        Arguments.of(HttpMethod.POST, "/api/v1/books"),
        Arguments.of(HttpMethod.POST, "/api/v1/courses"),
        Arguments.of(HttpMethod.POST, "/api/v1/enrollments"));
  }
}
