package org.phuchoang.management.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the per-module layout fixed by {@code 06-low-level-design.md} §2.1:
 * {@code web -> application -> domain/port}, with {@code internal/} never crossing
 * a module boundary.
 */
@AnalyzeClasses(packages = "org.phuchoang.management", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringRulesTest {

  private static final String ROOT_PACKAGE = "org.phuchoang.management.";

  // withOptionalLayers/allowEmptyShould: no module code exists yet (Sprint 0 skeleton),
  // so every layer is legitimately empty today -- these rules bind as soon as web/,
  // application/, domain/, port/ or internal/ packages gain their first class.
  @ArchTest
  static final ArchRule layers_are_respected = layeredArchitecture()
      .consideringOnlyDependenciesInLayers()
      .layer("Web").definedBy("..web..")
      .layer("Application").definedBy("..application..")
      .layer("Domain").definedBy("..domain..")
      .layer("Port").definedBy("..port..")
      .layer("Internal").definedBy("..internal..")
      .whereLayer("Web").mayNotBeAccessedByAnyLayer()
      .whereLayer("Application").mayOnlyBeAccessedByLayers("Web")
      .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Port", "Internal")
      .whereLayer("Port").mayOnlyBeAccessedByLayers("Application", "Internal")
      .whereLayer("Internal").mayNotBeAccessedByAnyLayer()
      .withOptionalLayers(true);

  @ArchTest
  static final ArchRule internal_packages_stay_inside_their_own_module = classes()
      .that().resideInAPackage("..internal..")
      .should(onlyBeAccessedFromTheSameModule())
      .allowEmptyShould(true);

  private static ArchCondition<JavaClass> onlyBeAccessedFromTheSameModule() {
    return new ArchCondition<JavaClass>("only be accessed from the same module") {
      @Override
      public void check(JavaClass internalClass, ConditionEvents events) {
        String module = moduleOf(internalClass);
        internalClass.getAccessesToSelf().forEach(access -> {
          boolean satisfied = moduleOf(access.getOriginOwner()).equals(module);
          events.add(new SimpleConditionEvent(access, satisfied, access.getDescription()
              + (satisfied ? "" : " -- crosses module boundary via internal/")));
        });
      }
    };
  }

  private static String moduleOf(JavaClass javaClass) {
    String packageName = javaClass.getPackageName();
    if (!packageName.startsWith(ROOT_PACKAGE)) {
      return "";
    }
    String remainder = packageName.substring(ROOT_PACKAGE.length());
    int dot = remainder.indexOf('.');
    return dot == -1 ? remainder : remainder.substring(0, dot);
  }
}
