(() => {
  const table = document.getElementById("impacted-tests-table");
  if (!table) {
    return;
  }

  const rows = Array.from(table.querySelectorAll("tbody tr"));
  const checkboxes = Array.from(document.querySelectorAll(".cause-filter-checkbox"));
  const clearButton = document.getElementById("clear-cause-filters");
  const summary = document.getElementById("impacted-tests-filter-summary");
  const emptyState = document.getElementById("impacted-tests-empty-filter");
  const totalRows = rows.length;

  const updateVisibility = () => {
    const selectedCauses = new Set(
      checkboxes.filter((checkbox) => checkbox.checked).map((checkbox) => checkbox.value)
    );
    let visibleRows = 0;

    rows.forEach((row) => {
      const rowCauses = (row.dataset.causes || "")
        .split("|")
        .filter((value) => value.length > 0);
      const isVisible =
        selectedCauses.size === 0 || rowCauses.some((cause) => selectedCauses.has(cause));
      row.hidden = !isVisible;
      if (isVisible) {
        visibleRows += 1;
      }
    });

    summary.textContent = `Showing ${visibleRows} of ${totalRows} tests`;
    emptyState.hidden = visibleRows !== 0;
  };

  checkboxes.forEach((checkbox) => {
    checkbox.addEventListener("change", updateVisibility);
  });
  clearButton.addEventListener("click", () => {
    checkboxes.forEach((checkbox) => {
      checkbox.checked = false;
    });
    updateVisibility();
  });

  updateVisibility();
})();
