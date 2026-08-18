package org.phuchoang.management.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Controller/Service/Repository/Row suffix rules from {@code 06-low-level-design.md} §2.2.
 */
// allowEmptyShould: no module code exists yet (Sprint 0 skeleton) -- every rule below
// binds as soon as the first matching class is added.
@AnalyzeClasses(packages = "org.phuchoang.management", importOptions = ImportOption.DoNotIncludeTests.class)
class NamingConventionsTest {

  @ArchTest
  static final ArchRule controllers_are_named_correctly = classes()
      .that().areAnnotatedWith(RestController.class)
      .should().resideInAPackage("..web..")
      .andShould().haveSimpleNameEndingWith("Controller")
      .allowEmptyShould(true);

  @ArchTest
  static final ArchRule application_services_are_named_correctly = classes()
      .that().areAnnotatedWith(Service.class)
      .should().resideInAPackage("..application..")
      .andShould().haveSimpleNameEndingWith("Service")
      .andShould().haveSimpleNameNotEndingWith("ServiceImpl")
      .andShould().notBeInterfaces()
      .allowEmptyShould(true);

  // Repository ports live in port/; identity's PasswordHasher/PasswordCipher are the
  // one documented exception (06-low-level-design.md §2.1) since they aren't repositories.
  @ArchTest
  static final ArchRule repository_ports_are_named_correctly = classes()
      .that().resideInAPackage("..port..")
      .and().areInterfaces()
      .and(describe("are not identity's password ports", NamingConventionsTest::isNotIdentityPasswordPort))
      .should().haveSimpleNameEndingWith("Repository")
      .allowEmptyShould(true);

  @ArchTest
  static final ArchRule persistence_rows_are_named_correctly = classes()
      .that().resideInAPackage("..internal..")
      .and().areRecords()
      .should().haveSimpleNameEndingWith("Row")
      .allowEmptyShould(true);

  private static boolean isNotIdentityPasswordPort(JavaClass javaClass) {
    return !javaClass.getPackageName().contains(".identity.");
  }
}
