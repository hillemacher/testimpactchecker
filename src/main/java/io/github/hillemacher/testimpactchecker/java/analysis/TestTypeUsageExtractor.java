package io.github.hillemacher.testimpactchecker.java.analysis;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestTypeUsageExtractor {

  private final ImpactCheckerConfig impactCheckerConfig;

  /**
   * Extracts annotation eligibility and referenced type names from a parsed test file.
   *
   * <p>The extractor keeps only the information needed for impact evaluation:
   * whether the test class has one of the configured annotations and which type names are
   * referenced in the compilation unit. Tests without a class declaration are treated as
   * ineligible and return an empty usage model.
   *
   * @param compilationUnit parsed test compilation unit
   * @return extracted test type usage data used by impact evaluation
   */
  public TestTypeUsage extract(final CompilationUnit compilationUnit) {
    final ClassOrInterfaceDeclaration classDecl =
        compilationUnit.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
    if (classDecl == null) {
      return new TestTypeUsage(false, Set.of());
    }

    final boolean hasRequiredAnnotation = classDecl.getAnnotations().stream()
        .map(AnnotationExpr::getNameAsString)
        .anyMatch(impactCheckerConfig.getAnnotations()::contains);

    final Set<String> referencedTypeNames = compilationUnit.findAll(ClassOrInterfaceType.class).stream()
        .map(ClassOrInterfaceType::getNameAsString)
        .collect(Collectors.toSet());

    return new TestTypeUsage(hasRequiredAnnotation, referencedTypeNames);
  }
}
