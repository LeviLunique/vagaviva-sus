package br.com.vagaviva.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Regras da arquitetura hexagonal aplicadas a TODOS os módulos (br.com.vagaviva.<modulo>):
 * domain -> nada de framework; application -> não conhece adapters; adapters de entrada e
 * saída não se conhecem.
 */
@AnalyzeClasses(packages = "br.com.vagaviva", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "..application..", "..adapter..")
            .because("o domínio deve ser Java puro e independente de infraestrutura");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .because("casos de uso dependem de portas, não de adapters (DIP)");

    @ArchTest
    static final ArchRule inboundAdaptersDoNotUseOutboundAdapters = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..")
            .because("controllers falam com casos de uso, nunca com repositórios");

    @ArchTest
    static final ArchRule controllersLiveInWebAdapters = classes()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideInAPackage("..adapter.in.web..");

    @ArchTest
    static final ArchRule entitiesLiveInPersistenceAdapters = classes()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().resideInAPackage("..adapter.out.persistence..");

    @ArchTest
    static final ArchRule noFieldInjection = noFields()
            .should().beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
            .because("use injeção por construtor");
}
