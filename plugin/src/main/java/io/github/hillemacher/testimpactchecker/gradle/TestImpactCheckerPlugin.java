package io.github.hillemacher.testimpactchecker.gradle;

import io.github.hillemacher.testimpactchecker.TestImpactChecker;
import io.github.hillemacher.testimpactchecker.config.AnalysisMode;
import io.github.hillemacher.testimpactchecker.config.MockPolicy;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

/**
 * Gradle plugin entry point. Registers the {@code testImpact} extension and the {@code
 * testImpactCheck} task, wiring sensible defaults so users only need to override the fields they
 * care about (typically just {@code baseRef} and {@code annotations}).
 */
public class TestImpactCheckerPlugin implements Plugin<Project> {

  private static final String EXTENSION_NAME = "testImpact";
  private static final String TASK_NAME = "testImpactCheck";

  @Override
  public void apply(final Project project) {
    final TestImpactCheckerExtension extension =
        project.getExtensions().create(EXTENSION_NAME, TestImpactCheckerExtension.class);

    extension.getBaseRef().convention("origin/main");
    extension.getTargetRef().convention("HEAD");
    extension.getAnnotations().convention(List.of());
    extension.getAnalysisMode().convention(AnalysisMode.DIRECT);
    extension.getMockPolicy().convention(MockPolicy.CURRENT);
    extension.getMaxPropagationDepth().convention(2);
    extension.getCacheMode().convention(TestImpactChecker.CacheMode.ENABLED);
    extension.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
    extension
        .getCacheDirectory()
        .convention(project.getLayout().getBuildDirectory().dir("test-impact/cache"));
    extension
        .getImpactJsonOutput()
        .convention(project.getLayout().getBuildDirectory().file("test-impact/impact.json"));
    extension
        .getImpactHtmlOutput()
        .convention(project.getLayout().getBuildDirectory().file("test-impact/impact-report.html"));

    final TaskProvider<TestImpactCheckTask> checkTask =
        project
            .getTasks()
            .register(
                TASK_NAME,
                TestImpactCheckTask.class,
                task -> {
                  task.setDescription("Runs the test impact checker and writes impact.json.");
                  task.setGroup("verification");
                  task.getBaseRef().convention(extension.getBaseRef());
                  task.getTargetRef().convention(extension.getTargetRef());
                  task.getAnnotations().convention(extension.getAnnotations());
                  task.getAnalysisMode().convention(extension.getAnalysisMode());
                  task.getMockPolicy().convention(extension.getMockPolicy());
                  task.getMaxPropagationDepth().convention(extension.getMaxPropagationDepth());
                  task.getCacheMode().convention(extension.getCacheMode());
                  task.getProjectDirectory().convention(extension.getProjectDirectory());
                  task.getCacheDirectory().convention(extension.getCacheDirectory());
                  task.getImpactJsonOutput().convention(extension.getImpactJsonOutput());
                  task.getImpactHtmlOutput().convention(extension.getImpactHtmlOutput());
                  // Git state is not a declared input — always re-run.
                  task.getOutputs().upToDateWhen(t -> false);
                });

    extension.wireCheckTask(checkTask);
  }
}
