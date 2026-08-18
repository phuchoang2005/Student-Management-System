package org.phuchoang.management.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * {@code domain/} is framework-free per {@code 06-low-level-design.md} §2.2 --
 * no Spring dependency of any kind.
 */
@AnalyzeClasses(packages = "org.phuchoang.management", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainPurityTest {

  // allowEmptyShould: no domain/ package exists yet (Sprint 0 skeleton) -- this rule
  // binds as soon as the first domain/ class is added.
  @ArchTest
  static final ArchRule domain_has_no_spring_dependencies = noClasses()
      .that().resideInAPackage("..domain..")
      .should().dependOnClassesThat().resideInAPackage("org.springframework..")
      .allowEmptyShould(true);
}
