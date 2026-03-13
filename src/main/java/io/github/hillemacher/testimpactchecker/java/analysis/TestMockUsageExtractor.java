package io.github.hillemacher.testimpactchecker.java.analysis;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.HashSet;
import java.util.Set;

public class TestMockUsageExtractor {

  /**
   * Extracts mocked type names from Mockito calls and mock field annotations.
   *
   * <p>The extractor currently recognizes:
   *
   * <ul>
   *   <li>{@code mock(X.class)} method calls
   *   <li>fields annotated with {@code @Mock}
   *   <li>fields annotated with {@code @MockBean}
   * </ul>
   *
   * Returned names are simple type names to match the analyzer's current naming model.
   *
   * @param compilationUnit parsed test compilation unit
   * @return set of mocked type simple names
   */
  public Set<String> extractMockedTypes(final CompilationUnit compilationUnit) {
    final Set<String> mockedTypes = new HashSet<>();

    compilationUnit
        .findAll(MethodCallExpr.class)
        .forEach(
            methodCallExpr -> {
              if (!"mock".equals(methodCallExpr.getNameAsString())
                  || methodCallExpr.getArguments().size() != 1) {
                return;
              }

              final String arg = methodCallExpr.getArgument(0).toString();
              if (!arg.endsWith(".class")) {
                return;
              }

              mockedTypes.add(arg.substring(0, arg.length() - ".class".length()));
            });

    compilationUnit
        .findAll(FieldDeclaration.class)
        .forEach(
            fieldDeclaration -> {
              final boolean isMockField =
                  fieldDeclaration.getAnnotations().stream()
                      .map(AnnotationExpr::getNameAsString)
                      .anyMatch(
                          annotation -> "Mock".equals(annotation) || "MockBean".equals(annotation));
              if (!isMockField) {
                return;
              }

              if (fieldDeclaration.getElementType().isClassOrInterfaceType()) {
                final ClassOrInterfaceType type =
                    fieldDeclaration.getElementType().asClassOrInterfaceType();
                mockedTypes.add(type.getNameAsString());
              }
            });

    return mockedTypes;
  }
}
