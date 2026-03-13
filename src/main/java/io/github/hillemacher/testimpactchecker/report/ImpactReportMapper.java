package io.github.hillemacher.testimpactchecker.report;

import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;

/**
 * Maps raw impact detection output into a deterministic report model.
 */
public class ImpactReportMapper {

  static final int MAX_CAUSE_NODES = 12;
  static final int MAX_TYPE_NODES = 28;
  static final int MAX_TEST_NODES = 40;
  static final int MAX_TOTAL_NODES = 80;
  static final int MAX_OVERVIEW_TEST_EDGES_PER_TYPE = 2;

  private final Clock clock;

  /**
   * Creates a mapper that timestamps reports using the current UTC clock.
   */
  public ImpactReportMapper() {
    this(Clock.systemUTC());
  }

  ImpactReportMapper(@NonNull final Clock clock) {
    this.clock = clock;
  }

  /**
   * Builds an immutable report model from impacted tests and causes.
   *
   * @param projectPath root path of the scanned project
   * @param relevantTestsWithCauses impacted tests keyed by absolute/normalized test path
   * @return deterministic report model for rendering
   * @throws NullPointerException if any parameter is {@code null}
   */
  public ImpactReport toImpactReport(
      @NonNull final Path projectPath,
      @NonNull final Map<Path, Set<String>> relevantTestsWithCauses) {
    return toImpactReport(
        projectPath,
        null,
        new ImpactCheckerConfig(),
        ZoneId.systemDefault(),
        relevantTestsWithCauses,
        Map.of());
  }

  /**
   * Builds an immutable report model from impacted tests and propagated impacted types.
   *
   * @param projectPath root path of the scanned project
   * @param relevantTestsWithCauses impacted tests keyed by absolute/normalized test path
   * @param impactedTypeToCauses impacted type names mapped to changed-class causes
   * @return deterministic report model for rendering
   * @throws NullPointerException if any parameter is {@code null}
   */
  public ImpactReport toImpactReport(
      @NonNull final Path projectPath,
      @NonNull final Map<Path, Set<String>> relevantTestsWithCauses,
      @NonNull final Map<String, Set<String>> impactedTypeToCauses) {
    return toImpactReport(
        projectPath,
        null,
        new ImpactCheckerConfig(),
        ZoneId.systemDefault(),
        relevantTestsWithCauses,
        impactedTypeToCauses);
  }

  /**
   * Builds an immutable report model from impacted tests, propagated impacted types, and resolved
   * execution metadata.
   *
   * @param projectPath root path of the scanned project
   * @param configPath path to the configuration file used for this run, or {@code null} when not
   *     available
   * @param impactCheckerConfig effective analysis configuration used for the run
   * @param executionZoneId system timezone of the machine that generated the report
   * @param relevantTestsWithCauses impacted tests keyed by absolute/normalized test path
   * @param impactedTypeToCauses impacted type names mapped to changed-class causes
   * @return deterministic report model for rendering
   * @throws NullPointerException if any required parameter is {@code null}
   */
  public ImpactReport toImpactReport(
      @NonNull final Path projectPath,
      final Path configPath,
      @NonNull final ImpactCheckerConfig impactCheckerConfig,
      @NonNull final ZoneId executionZoneId,
      @NonNull final Map<Path, Set<String>> relevantTestsWithCauses,
      @NonNull final Map<String, Set<String>> impactedTypeToCauses) {

    final Path normalizedProjectPath = projectPath.toAbsolutePath().normalize();
    final Instant generatedAt = clock.instant();

    final List<ImpactedTestEntry> impactedTests = relevantTestsWithCauses.entrySet().stream()
        .map(entry -> new ImpactedTestEntry(
            normalizedProjectPath.relativize(entry.getKey().toAbsolutePath().normalize()),
            new ArrayList<>(entry.getValue())))
        .sorted(Comparator.comparing(entry -> entry.relativeTestPath().toString()))
        .toList();

    final Map<String, Integer> causeCounts = buildCauseCounts(impactedTests);

    final List<CauseSummaryEntry> topCauses = causeCounts.entrySet().stream()
        .map(entry -> new CauseSummaryEntry(entry.getKey(), entry.getValue()))
        .sorted(Comparator.comparingInt(CauseSummaryEntry::impactedTestCount)
            .reversed()
            .thenComparing(CauseSummaryEntry::cause))
        .toList();

    final int impactedTestsCount = impactedTests.size();
    final int uniqueCausesCount = causeCounts.size();

    // Average number of non-mocked causes per impacted test.
    final double averageCausesPerTest = impactedTestsCount == 0
        ? 0
        : impactedTests.stream().mapToInt(entry -> entry.causes().size()).average().orElse(0);

    final ImpactGraph fullGraph = buildImpactGraph(impactedTests, impactedTypeToCauses, causeCounts);
    final ImpactGraphBundle graphBundle = buildGraphBundle(fullGraph, topCauses);
    final ImpactReportMetadata metadata = buildMetadata(
        normalizedProjectPath,
        configPath,
        impactCheckerConfig,
        executionZoneId,
        generatedAt);

    return new ImpactReport(
        metadata,
        impactedTestsCount,
        uniqueCausesCount,
        averageCausesPerTest,
        impactedTests,
        topCauses,
        graphBundle);
  }

  private ImpactReportMetadata buildMetadata(
      final Path projectPath,
      final Path configPath,
      final ImpactCheckerConfig impactCheckerConfig,
      final ZoneId executionZoneId,
      final Instant generatedAtUtc) {
    final List<String> sortedAnnotations = Optional.ofNullable(impactCheckerConfig.getAnnotations())
        .orElse(List.of())
        .stream()
        .sorted()
        .toList();

    return new ImpactReportMetadata(
        projectPath,
        generatedAtUtc,
        executionZoneId,
        toOptionalText(impactCheckerConfig.getBaseRef()),
        toOptionalText(impactCheckerConfig.getTargetRef()),
        sortedAnnotations,
        impactCheckerConfig.getAnalysisMode(),
        impactCheckerConfig.getMaxPropagationDepth(),
        impactCheckerConfig.getMockPolicy(),
        Optional.ofNullable(configPath));
  }

  private Optional<String> toOptionalText(final String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value);
  }

  private Map<String, Integer> buildCauseCounts(final List<ImpactedTestEntry> impactedTests) {
    final Map<String, Integer> causeCounts = new HashMap<>();
    impactedTests.forEach(entry -> entry.causes().forEach(
        cause -> causeCounts.merge(cause, 1, Integer::sum)));
    return causeCounts;
  }

  private ImpactGraph buildImpactGraph(
      final List<ImpactedTestEntry> impactedTests,
      final Map<String, Set<String>> impactedTypeToCauses,
      final Map<String, Integer> causeCounts) {
    if (impactedTests.isEmpty() || causeCounts.isEmpty()) {
      return ImpactGraph.empty();
    }

    final Set<String> selectedCauses = selectCauses(causeCounts);
    final List<TypeCandidate> typeCandidates = buildTypeCandidates(impactedTypeToCauses, causeCounts, selectedCauses);
    final Set<String> selectedTypes = selectTypes(typeCandidates, selectedCauses.size());
    final List<TestCandidate> testCandidates = buildTestCandidates(impactedTests, causeCounts, selectedCauses);
    final Set<String> selectedTests = selectTests(testCandidates, selectedCauses.size(), selectedTypes.size());

    final List<ImpactGraphNode> causeNodes = selectedCauses.stream()
        .map(cause -> new ImpactGraphNode(nodeId(ImpactGraphNodeKind.CAUSE, cause), cause,
            ImpactGraphNodeKind.CAUSE, causeCounts.getOrDefault(cause, 0)))
        .sorted(graphNodeComparator())
        .toList();

    final List<ImpactGraphNode> typeNodes = selectedTypes.stream()
        .map(type -> new ImpactGraphNode(nodeId(ImpactGraphNodeKind.TYPE, type), type,
            ImpactGraphNodeKind.TYPE,
            typeCandidates.stream().filter(candidate -> candidate.type().equals(type))
                .findFirst().map(TypeCandidate::score).orElse(0)))
        .sorted(graphNodeComparator())
        .toList();

    final List<ImpactGraphNode> testNodes = selectedTests.stream()
        .map(test -> new ImpactGraphNode(nodeId(ImpactGraphNodeKind.TEST, test), toTestClassName(test),
            ImpactGraphNodeKind.TEST,
            testCandidates.stream().filter(candidate -> candidate.relativeTestPath().equals(test))
                .findFirst().map(TestCandidate::score).orElse(0)))
        .sorted(graphNodeComparator())
        .toList();

    final List<ImpactGraphEdge> edges = buildEdges(
        impactedTests,
        impactedTypeToCauses,
        selectedCauses,
        selectedTypes,
        selectedTests);

    final int totalNodes = countTotalNodes(causeCounts.keySet(), impactedTypeToCauses.keySet(), impactedTests);
    final int totalEdges = countTotalEdges(impactedTests, impactedTypeToCauses, causeCounts.keySet());

    final List<ImpactGraphNode> allNodes = new ArrayList<>();
    allNodes.addAll(causeNodes);
    allNodes.addAll(typeNodes);
    allNodes.addAll(testNodes);

    return new ImpactGraph(
        allNodes,
        edges,
        new ImpactGraphStats(totalNodes, allNodes.size(), totalEdges, edges.size()));
  }

  private ImpactGraphBundle buildGraphBundle(
      final ImpactGraph fullGraph,
      final List<CauseSummaryEntry> topCauses) {
    if (fullGraph.nodes().isEmpty()) {
      return ImpactGraphBundle.empty();
    }

    final ImpactGraph overviewGraph = buildOverviewGraph(fullGraph);
    final List<ImpactGraphSection> causeSections = buildCauseSections(fullGraph, topCauses);
    return new ImpactGraphBundle(fullGraph, overviewGraph, causeSections);
  }

  private ImpactGraph buildOverviewGraph(final ImpactGraph fullGraph) {
    final List<ImpactGraphEdge> overviewEdges = new ArrayList<>();
    final Set<ImpactGraphEdge> selectedEdges = new HashSet<>();
    final Map<String, ImpactGraphNode> nodesById = indexNodesById(fullGraph.nodes());
    final Map<String, Integer> typeToSelectedTestEdges = new HashMap<>();

    for (final ImpactGraphEdge edge : fullGraph.edges()) {
      final ImpactGraphNode fromNode = nodesById.get(edge.fromNodeId());
      final ImpactGraphNode toNode = nodesById.get(edge.toNodeId());
      if (fromNode == null || toNode == null) {
        continue;
      }

      if (fromNode.kind() == ImpactGraphNodeKind.CAUSE && toNode.kind() == ImpactGraphNodeKind.TYPE) {
        selectedEdges.add(edge);
        continue;
      }

      if (fromNode.kind() == ImpactGraphNodeKind.TYPE
          && toNode.kind() == ImpactGraphNodeKind.TEST
          && typeToSelectedTestEdges.getOrDefault(fromNode.id(), 0) < MAX_OVERVIEW_TEST_EDGES_PER_TYPE) {
        selectedEdges.add(edge);
        typeToSelectedTestEdges.merge(fromNode.id(), 1, Integer::sum);
      }
    }

    overviewEdges.addAll(selectedEdges.stream()
        .sorted(Comparator.comparing(ImpactGraphEdge::fromNodeId).thenComparing(ImpactGraphEdge::toNodeId))
        .toList());

    return new ImpactGraph(
        fullGraph.nodes(),
        overviewEdges,
        new ImpactGraphStats(
            fullGraph.nodes().size(),
            fullGraph.nodes().size(),
            fullGraph.edges().size(),
            overviewEdges.size()));
  }

  private List<ImpactGraphSection> buildCauseSections(
      final ImpactGraph fullGraph,
      final List<CauseSummaryEntry> topCauses) {
    final Map<String, ImpactGraphNode> nodesById = indexNodesById(fullGraph.nodes());
    final List<ImpactGraphSection> sections = new ArrayList<>();

    for (final CauseSummaryEntry causeEntry : topCauses) {
      final String causeNodeId = nodeId(ImpactGraphNodeKind.CAUSE, causeEntry.cause());
      final ImpactGraphNode causeNode = nodesById.get(causeNodeId);
      if (causeNode == null) {
        continue;
      }

      final Set<String> typeIds = fullGraph.edges().stream()
          .filter(edge -> edge.fromNodeId().equals(causeNodeId))
          .map(ImpactGraphEdge::toNodeId)
          .collect(HashSet::new, Set::add, Set::addAll);

      final Set<String> testIds = new HashSet<>();
      final Set<ImpactGraphEdge> focusedEdges = new HashSet<>();

      for (final ImpactGraphEdge edge : fullGraph.edges()) {
        if (edge.fromNodeId().equals(causeNodeId) && typeIds.contains(edge.toNodeId())) {
          focusedEdges.add(edge);
        }
        if (typeIds.contains(edge.fromNodeId())) {
          final ImpactGraphNode targetNode = nodesById.get(edge.toNodeId());
          if (targetNode != null && targetNode.kind() == ImpactGraphNodeKind.TEST) {
            focusedEdges.add(edge);
            testIds.add(edge.toNodeId());
          }
        }
      }

      final List<ImpactGraphNode> sectionNodes = fullGraph.nodes().stream()
          .filter(node -> node.id().equals(causeNodeId) || typeIds.contains(node.id()) || testIds.contains(node.id()))
          .toList();
      final List<ImpactGraphEdge> sectionEdges = focusedEdges.stream()
          .sorted(Comparator.comparing(ImpactGraphEdge::fromNodeId).thenComparing(ImpactGraphEdge::toNodeId))
          .toList();

      sections.add(new ImpactGraphSection(
          causeEntry.cause(),
          typeIds.size(),
          testIds.size(),
          new ImpactGraph(
              sectionNodes,
              sectionEdges,
              new ImpactGraphStats(sectionNodes.size(), sectionNodes.size(), sectionEdges.size(), sectionEdges.size()))));
    }

    return sections;
  }

  private Map<String, ImpactGraphNode> indexNodesById(final List<ImpactGraphNode> nodes) {
    final Map<String, ImpactGraphNode> nodesById = new HashMap<>();
    nodes.forEach(node -> nodesById.put(node.id(), node));
    return nodesById;
  }

  private Comparator<ImpactGraphNode> graphNodeComparator() {
    return Comparator.comparingInt(ImpactGraphNode::score)
        .reversed()
        .thenComparing(ImpactGraphNode::label);
  }

  private Set<String> selectCauses(final Map<String, Integer> causeCounts) {
    final List<String> sorted = causeCounts.entrySet().stream()
        .sorted(Comparator.comparingInt(Map.Entry<String, Integer>::getValue).reversed()
            .thenComparing(Map.Entry::getKey))
        .map(Map.Entry::getKey)
        .toList();

    final Set<String> selected = new HashSet<>();
    for (final String cause : sorted) {
      if (selected.size() >= MAX_CAUSE_NODES || selected.size() >= MAX_TOTAL_NODES) {
        break;
      }
      selected.add(cause);
    }
    return selected;
  }

  private List<TypeCandidate> buildTypeCandidates(
      final Map<String, Set<String>> impactedTypeToCauses,
      final Map<String, Integer> causeCounts,
      final Set<String> selectedCauses) {
    return impactedTypeToCauses.entrySet().stream()
        .filter(entry -> entry.getValue().stream().anyMatch(selectedCauses::contains))
        .map(entry -> new TypeCandidate(entry.getKey(), entry.getValue().stream()
            .filter(selectedCauses::contains)
            .mapToInt(cause -> causeCounts.getOrDefault(cause, 0))
            .sum()))
        .sorted(Comparator.comparingInt(TypeCandidate::score).reversed().thenComparing(TypeCandidate::type))
        .toList();
  }

  private Set<String> selectTypes(final List<TypeCandidate> typeCandidates, final int selectedCauseCount) {
    final Set<String> selected = new HashSet<>();
    for (final TypeCandidate candidate : typeCandidates) {
      if (selected.size() >= MAX_TYPE_NODES) {
        break;
      }
      if (selectedCauseCount + selected.size() >= MAX_TOTAL_NODES) {
        break;
      }
      selected.add(candidate.type());
    }
    return selected;
  }

  private List<TestCandidate> buildTestCandidates(
      final List<ImpactedTestEntry> impactedTests,
      final Map<String, Integer> causeCounts,
      final Set<String> selectedCauses) {
    return impactedTests.stream()
        .map(entry -> new TestCandidate(
            entry.relativeTestPath().toString(),
            entry.causes().stream().filter(selectedCauses::contains)
                .mapToInt(cause -> causeCounts.getOrDefault(cause, 0)).sum()))
        .filter(candidate -> candidate.score() > 0)
        .sorted(Comparator.comparingInt(TestCandidate::score).reversed()
            .thenComparing(TestCandidate::relativeTestPath))
        .toList();
  }

  private Set<String> selectTests(
      final List<TestCandidate> testCandidates,
      final int selectedCauseCount,
      final int selectedTypeCount) {
    final Set<String> selected = new HashSet<>();
    for (final TestCandidate candidate : testCandidates) {
      if (selected.size() >= MAX_TEST_NODES) {
        break;
      }
      if (selectedCauseCount + selectedTypeCount + selected.size() >= MAX_TOTAL_NODES) {
        break;
      }
      selected.add(candidate.relativeTestPath());
    }
    return selected;
  }

  private List<ImpactGraphEdge> buildEdges(
      final List<ImpactedTestEntry> impactedTests,
      final Map<String, Set<String>> impactedTypeToCauses,
      final Set<String> selectedCauses,
      final Set<String> selectedTypes,
      final Set<String> selectedTests) {
    final Set<ImpactGraphEdge> edgeSet = new HashSet<>();

    impactedTypeToCauses.forEach((type, causes) -> {
      if (!selectedTypes.contains(type)) {
        return;
      }
      causes.stream().filter(selectedCauses::contains).forEach(cause ->
          edgeSet.add(new ImpactGraphEdge(
              nodeId(ImpactGraphNodeKind.CAUSE, cause),
              nodeId(ImpactGraphNodeKind.TYPE, type))));
    });

    final Map<String, Set<String>> testToCauses = new HashMap<>();
    impactedTests.forEach(entry -> testToCauses.put(entry.relativeTestPath().toString(), Set.copyOf(entry.causes())));

    selectedTests.forEach(test -> {
      final Set<String> causesForTest = testToCauses.getOrDefault(test, Set.of());
      selectedTypes.forEach(type -> {
        final Set<String> causesForType = impactedTypeToCauses.getOrDefault(type, Set.of());
        final boolean connected = causesForTest.stream().anyMatch(causesForType::contains);
        if (connected) {
          edgeSet.add(new ImpactGraphEdge(
              nodeId(ImpactGraphNodeKind.TYPE, type),
              nodeId(ImpactGraphNodeKind.TEST, test)));
        }
      });
    });

    return edgeSet.stream()
        .sorted(Comparator.comparing(ImpactGraphEdge::fromNodeId).thenComparing(ImpactGraphEdge::toNodeId))
        .toList();
  }

  private int countTotalNodes(
      final Set<String> totalCauses,
      final Set<String> totalTypes,
      final List<ImpactedTestEntry> impactedTests) {
    return totalCauses.size() + totalTypes.size() + impactedTests.size();
  }

  private int countTotalEdges(
      final List<ImpactedTestEntry> impactedTests,
      final Map<String, Set<String>> impactedTypeToCauses,
      final Set<String> totalCauses) {
    int causeToTypeEdges = 0;
    for (final Set<String> causes : impactedTypeToCauses.values()) {
      causeToTypeEdges += causes.stream().filter(totalCauses::contains).count();
    }

    int typeToTestEdges = 0;
    for (final ImpactedTestEntry test : impactedTests) {
      final Set<String> causesForTest = Set.copyOf(test.causes());
      for (final Set<String> causesForType : impactedTypeToCauses.values()) {
        final boolean connected = causesForTest.stream().anyMatch(causesForType::contains);
        if (connected) {
          typeToTestEdges++;
        }
      }
    }

    return causeToTypeEdges + typeToTestEdges;
  }

  private String nodeId(final ImpactGraphNodeKind kind, final String raw) {
    return switch (kind) {
      case CAUSE -> "cause|" + raw;
      case TYPE -> "type|" + raw;
      case TEST -> "test|" + raw;
    };
  }

  private String toTestClassName(final String relativePath) {
    final int lastForwardSlash = relativePath.lastIndexOf('/');
    final int lastBackSlash = relativePath.lastIndexOf('\\');
    final int start = Math.max(lastForwardSlash, lastBackSlash) + 1;
    final String fileName = relativePath.substring(start);
    if (fileName.endsWith(".java")) {
      return fileName.substring(0, fileName.length() - ".java".length());
    }
    return fileName;
  }

  private record TypeCandidate(String type, int score) {
  }

  private record TestCandidate(String relativeTestPath, int score) {
  }
}
