<script lang="ts">
  import Pagination from '$lib/components/Pagination.svelte';
  import { benchmarkStore } from '$lib/stores/benchmark';

  type CategoryKey = 'game' | 'model' | 'dataset' | 'split';
  type ColumnKey = CategoryKey | 'n' | 'spearman' | 'std';
  type SortDirection = 'asc' | 'desc' | null;

  type TableRow = {
    key: string;
    values: Partial<Record<CategoryKey, string>>;
    n: number;
    spearman: number | null;
    std: number | null;
  };

  const CATEGORIES: { key: CategoryKey; label: string }[] = [
    { key: 'game', label: 'Game' },
    { key: 'model', label: 'Model' },
    { key: 'dataset', label: 'Dataset' },
    { key: 'split', label: 'Split' }
  ];

  const MISSING = '—';
  const rowsPerPage = 10;

  let rawData = $derived($benchmarkStore.rawData);

  let grouping = $state<Record<CategoryKey, boolean>>({
    game: true,
    model: true,
    dataset: true,
    split: true
  });

  let sortColumn = $state<ColumnKey | null>(null);
  let sortDirection = $state<SortDirection>(null);
  let filters = $state<Partial<Record<ColumnKey, string>>>({});
  let currentPage = $state(1);

  let activeCategories = $derived(CATEGORIES.filter((c) => grouping[c.key]));
  let aggregatedCategories = $derived(CATEGORIES.filter((c) => !grouping[c.key]));
  let isAggregated = $derived(aggregatedCategories.length > 0);

  let columns = $derived<{ key: ColumnKey; label: string }[]>([
    ...activeCategories,
    ...(isAggregated ? [{ key: 'n' as const, label: 'n' }] : []),
    { key: 'spearman' as const, label: 'Spearman' },
    ...(isAggregated ? [{ key: 'std' as const, label: 'Std' }] : [])
  ]);

  /**
   * Groups the raw rows by the active categories. Mean and sample standard deviation
   * are only reported when every member of a group has a spearman value, so a printed
   * score always covers the group's complete set of runs.
   */
  let tableRows = $derived.by(() => {
    // Local to this computation and rebuilt on every run, so it needs no reactivity.
    // eslint-disable-next-line svelte/prefer-svelte-reactivity
    const groups = new Map<
      string,
      { key: string; values: Partial<Record<CategoryKey, string>>; scores: number[] }
    >();

    for (const row of rawData) {
      const key = activeCategories.map((c) => row[c.key]).join('\u001F');
      let group = groups.get(key);
      if (!group) {
        const values: Partial<Record<CategoryKey, string>> = {};
        for (const category of activeCategories) values[category.key] = row[category.key];
        group = { key, values, scores: [] };
        groups.set(key, group);
      }
      group.scores.push(parseFloat(row.spearman));
    }

    return [...groups.values()].map(({ key, values, scores }): TableRow => {
      if (!scores.every((score) => Number.isFinite(score))) {
        return { key, values, n: scores.length, spearman: null, std: null };
      }
      const mean = scores.reduce((sum, score) => sum + score, 0) / scores.length;
      const variance =
        scores.length > 1
          ? scores.reduce((sum, score) => sum + (score - mean) ** 2, 0) / (scores.length - 1)
          : 0;
      return { key, values, n: scores.length, spearman: mean, std: Math.sqrt(variance) };
    });
  });

  function formatNumber(value: number | null): string {
    return value === null ? MISSING : value.toFixed(4);
  }

  function cellText(row: TableRow, key: ColumnKey): string {
    if (key === 'n') return String(row.n);
    if (key === 'spearman') return formatNumber(row.spearman);
    if (key === 'std') return formatNumber(row.std);
    return row.values[key] ?? '';
  }

  function isNumericColumn(key: ColumnKey): boolean {
    return key === 'n' || key === 'spearman' || key === 'std';
  }

  function numericValue(row: TableRow, key: ColumnKey): number | null {
    if (key === 'n') return row.n;
    return key === 'spearman' ? row.spearman : row.std;
  }

  let filteredData = $derived.by(() => {
    let result = tableRows.filter((row) =>
      columns.every((column) => {
        const filterValue = (filters[column.key] ?? '').toLowerCase();
        if (!filterValue) return true;
        return cellText(row, column.key).toLowerCase().includes(filterValue);
      })
    );

    if (sortColumn && sortDirection) {
      const column = sortColumn;
      const direction = sortDirection;
      result = [...result].sort((a, b) => {
        if (isNumericColumn(column)) {
          const aVal = numericValue(a, column);
          const bVal = numericValue(b, column);
          // Groups without a score always sort last, whichever direction is active.
          if (aVal === null || bVal === null) {
            return aVal === bVal ? 0 : aVal === null ? 1 : -1;
          }
          return direction === 'asc' ? aVal - bVal : bVal - aVal;
        }
        const comparison = cellText(a, column).localeCompare(cellText(b, column));
        return direction === 'asc' ? comparison : -comparison;
      });
    }

    return result;
  });

  let totalPages = $derived(Math.ceil(filteredData.length / rowsPerPage));

  let paginatedData = $derived.by(() => {
    const startIndex = (currentPage - 1) * rowsPerPage;
    return filteredData.slice(startIndex, startIndex + rowsPerPage);
  });

  function handleToggleCategory(key: CategoryKey) {
    grouping[key] = !grouping[key];
    filters = {};
    sortColumn = null;
    sortDirection = null;
    currentPage = 1;
  }

  function handleSort(column: ColumnKey) {
    if (sortColumn === column) {
      if (sortDirection === 'asc') {
        sortDirection = 'desc';
      } else if (sortDirection === 'desc') {
        sortColumn = null;
        sortDirection = null;
      }
    } else {
      sortColumn = column;
      sortDirection = 'asc';
    }
    currentPage = 1;
  }

  function handleFilter(column: ColumnKey, value: string) {
    filters[column] = value;
    currentPage = 1;
  }
</script>

<div class="p-6 max-w-7xl mx-auto">
  <div class="mb-4 flex flex-wrap items-center gap-2">
    <span class="text-sm font-semibold text-gray-700 mr-1">Group by:</span>
    {#each CATEGORIES as category (category.key)}
      <label
        class="flex items-center gap-2 text-sm cursor-pointer select-none rounded-md border px-3 py-1.5 transition-colors duration-150 {grouping[
          category.key
        ]
          ? 'border-blue-600 bg-blue-50 text-blue-900'
          : 'border-gray-300 bg-white text-gray-600 hover:bg-gray-50'}"
      >
        <input
          type="checkbox"
          class="cursor-pointer"
          checked={grouping[category.key]}
          onchange={() => handleToggleCategory(category.key)}
        />
        {category.label}
      </label>
    {/each}
    <span class="text-sm text-gray-500 ml-1">
      {filteredData.length} group{filteredData.length === 1 ? '' : 's'}
    </span>
  </div>

  <div class="overflow-x-auto border border-gray-300 rounded-lg bg-white shadow-sm">
    <table class="w-full border-collapse">
      <thead>
        <tr>
          {#each columns as column (column.key)}
            <th class="bg-gray-100 p-0 border-b-2 border-gray-300 sticky top-0 z-10">
              <button
                class="w-full p-3 border-none bg-transparent cursor-pointer font-semibold text-left flex items-center gap-2 transition-colors duration-150 hover:bg-gray-200 {sortColumn ===
                column.key
                  ? 'bg-gray-300'
                  : ''}"
                onclick={() => handleSort(column.key)}
              >
                {column.label}
                {#if sortColumn === column.key && sortDirection}
                  <span class="text-xs ml-auto">
                    {sortDirection === 'asc' ? '▲' : '▼'}
                  </span>
                {/if}
              </button>
              <input
                type="text"
                class="w-full p-2 border-none border-t border-gray-300 text-sm box-border transition-all duration-200 focus:outline-2 focus:outline-blue-600 focus:-outline-offset-2"
                placeholder="Filter..."
                value={filters[column.key] ?? ''}
                oninput={(e) => handleFilter(column.key, e.currentTarget.value)}
              />
            </th>
          {/each}
        </tr>
      </thead>
      <tbody>
        {#each paginatedData as row, i (row.key)}
          <tr class="hover:bg-gray-50">
            {#each columns as column (column.key)}
              <td class="p-3 {i === paginatedData.length - 1 ? '' : 'border-b border-gray-200'}">
                {cellText(row, column.key)}
              </td>
            {/each}
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
</div>

<Pagination bind:currentPage {totalPages} />
