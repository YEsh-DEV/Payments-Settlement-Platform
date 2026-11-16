package io.github.yesh.paycore.ledger.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

@AnalyzeClasses(
    packages = "io.github.yesh.paycore.ledger",
    importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureRulesTest {

  @ArchTest
  public static final ArchRule controllers_must_not_depend_on_repositories =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..repository..")
          .because("Controllers must delegate to services and not access repositories directly");

  @ArchTest
  public static final ArchRule domain_must_not_depend_on_controller_or_config =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..controller..", "..config..")
          .because("Domain model must not depend on API or infrastructure configuration");

  @ArchTest
  public static final ArchRule no_standard_streams =
      GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because(
          "Production code must use SLF4J logging instead of System.out/System.err");

  @ArchTest
  public static final ArchRule no_package_cycles =
      slices()
          .matching("io.github.yesh.paycore.ledger.(*)..")
          .should()
          .beFreeOfCycles()
          .because("Subpackages in ledger-service must be free of cyclic dependencies");
}
