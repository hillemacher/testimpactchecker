@import io.github.hillemacher.testimpactchecker.report.HtmlImpactReportTemplateModel

@param HtmlImpactReportTemplateModel model
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Test Impact Report</title>
  <style>
${model.inlineCss()}
  </style>
</head>
<body>
  <main class="container">
    <header class="page-header">
      <h1>Test Impact Report</h1>
    </header>

    <section>
      <h2>Run metadata</h2>
      <dl class="metadata-grid">
        <dt>Project</dt>
        <dd>${model.report().metadata().projectPath().toString()}</dd>
        <dt>Generated</dt>
        <dd>${model.formattedGeneratedTimestamp()}</dd>
        <dt>Execution zone</dt>
        <dd>${model.report().metadata().executionZoneId().getId()}</dd>
        <dt>Base ref</dt>
        <dd>${model.displayOrDash(model.report().metadata().baseRef())}</dd>
        <dt>Target ref</dt>
        <dd>${model.displayOrDash(model.report().metadata().targetRef())}</dd>
        <dt>Annotations</dt>
        <dd>${model.annotationsOrDash()}</dd>
        <dt>Analysis mode</dt>
        <dd>${model.report().metadata().analysisMode().name()}</dd>
        <dt>Max propagation depth</dt>
        <dd>${model.report().metadata().maxPropagationDepth()}</dd>
        <dt>Mock policy</dt>
        <dd>${model.report().metadata().mockPolicy().name()}</dd>
        @if(model.hasConfigPath())
          <dt>Config path</dt>
          <dd>${model.configPath()}</dd>
        @endif
      </dl>
    </section>

    <section class="card-section">
      <article class="metric-card"><h2>Impacted tests</h2><p>${model.report().impactedTestsCount()}</p></article>
      <article class="metric-card"><h2>Unique causes</h2><p>${model.report().uniqueCausesCount()}</p></article>
      <article class="metric-card"><h2>Avg causes/test</h2><p>${String.format(java.util.Locale.ROOT, "%.2f", model.report().averageCausesPerTest())}</p></article>
    </section>

    <section>
      <h2>Impacted tests and causes</h2>
      @if(model.report().impactedTests().isEmpty())
        <p class="empty-state">None found.</p>
      @else
        <div class="table-filter-panel" aria-label="Cause filters">
          <div class="table-filter-toolbar">
            <div>
              <p class="table-filter-label">Filter by causes</p>
              <p id="impacted-tests-filter-summary" class="meta">Showing ${model.report().impactedTests().size()} of ${model.report().impactedTests().size()} tests</p>
            </div>
            <button type="button" id="clear-cause-filters" class="filter-button">Clear filters</button>
          </div>
          <div class="cause-filter-options">
            @for(var causeEntry : model.causeTokens().entrySet())
              <label class="filter-checkbox" for="cause-filter-${causeEntry.getValue()}">
                <input type="checkbox" id="cause-filter-${causeEntry.getValue()}" class="cause-filter-checkbox" value="${causeEntry.getValue()}">
                <span>${causeEntry.getKey()}</span>
              </label>
            @endfor
          </div>
        </div>
        <div class="table-wrapper">
          <table id="impacted-tests-table">
            <thead><tr><th>Test path</th><th>Causes</th></tr></thead>
            <tbody>
              @for(var entry : model.report().impactedTests())
                <tr data-causes="${model.joinCausesAttributeValue(entry.causes())}"><td>${entry.relativeTestPath().toString()}</td><td>${String.join(", ", entry.causes())}</td></tr>
              @endfor
            </tbody>
          </table>
        </div>
        <p id="impacted-tests-empty-filter" class="empty-state" hidden>No impacted tests match the selected causes.</p>
      @endif
    </section>

    <section>
      <h2>Top causes</h2>
      @if(model.report().topCauses().isEmpty())
        <p class="empty-state">None found.</p>
      @else
        <div class="table-wrapper">
          <table>
            <thead><tr><th>Cause</th><th>Impacted tests</th></tr></thead>
            <tbody>
              @for(var cause : model.report().topCauses())
                <tr><td>${cause.cause()}</td><td>${cause.impactedTestCount()}</td></tr>
              @endfor
            </tbody>
          </table>
        </div>
      @endif
    </section>

    <section>
      <h2>Impact graph</h2>
      <p class="meta">Overview graph is simplified for readability. Use the per-cause graphs for full edge detail.</p>
      <p class="legend"><span class="legend-chip legend-cause">Changed causes</span> <span class="legend-chip legend-type">Impacted types</span> <span class="legend-chip legend-test">Impacted tests</span></p>
      @if(model.report().graphBundle().fullGraph().stats().isTruncated())
        <p class="meta">Showing ${model.report().graphBundle().fullGraph().stats().shownNodes()}/${model.report().graphBundle().fullGraph().stats().totalNodes()} nodes and ${model.report().graphBundle().fullGraph().stats().shownEdges()}/${model.report().graphBundle().fullGraph().stats().totalEdges()} edges.</p>
      @endif
      @if(model.report().graphBundle().overviewGraph().stats().shownEdges() < model.report().graphBundle().overviewGraph().stats().totalEdges())
        <p class="meta">Overview hides ${model.report().graphBundle().overviewGraph().stats().totalEdges() - model.report().graphBundle().overviewGraph().stats().shownEdges()} lower-signal edges to reduce clutter.</p>
      @endif
      <h3>Overview</h3>
      <div class="graph-wrapper">
${model.overviewGraphMarkup()}
      </div>

      @if(!model.renderedCauseGraphSections().isEmpty())
        <div class="cause-graph-sections">
          <h3>Per-cause detail</h3>
          @for(var section : model.renderedCauseGraphSections())
            <details class="cause-graph-details">
              <summary>${section.cause()} (${section.impactedTypeCount()} types, ${section.impactedTestCount()} tests)</summary>
              <div class="graph-wrapper cause-graph-wrapper">
${section.graphMarkup()}
              </div>
            </details>
          @endfor
        </div>
      @endif
    </section>
  </main>
  <script>
${model.inlineScript()}
  </script>
</body>
</html>
