<script lang="ts">
  import Pagination from '$lib/components/Pagination.svelte';
  import { benchmarkStore, type MetricRow } from '$lib/stores/benchmark';

  type SortDirection = 'asc' | 'desc' | null;
  type ColumnKey = keyof MetricRow;

  let rawData = $derived($benchmarkStore.rawData);

  let sortColumn = $state<ColumnKey | null>(null);
  let sortDirection = $state<SortDirection>(null);
  let filters = $state<Record<ColumnKey, string>>({
    game: '',
    model: '',
    dataset: '',
    spearman: ''
  });

  const rowsPerPage = 10;

  let currentPage = $state(1);

  const columns: { key: ColumnKey; label: string }[] = [
    { key: 'game', label: 'Game' },
    { key: 'model', label: 'Model' },
    { key: 'dataset', label: 'Dataset' },
    { key: 'spearman', label: 'Spearman' }
  ];

  // Compute filtered and sorted data reactively
  let filteredData = $derived.by(() => {
    let result = rawData.filter((row) => {
      return columns.every((col) => {
        const filterValue = filters[col.key].toLowerCase();
        if (!filterValue) return true;
        return String(row[col.key]).toLowerCase().includes(filterValue);
      });
    });

    if (sortColumn && sortDirection) {
      result = [...result].sort((a, b) => {
        const aVal = a[sortColumn];
        const bVal = b[sortColumn];

        let comparison: number;

        if (sortColumn === 'spearman') {
          const aNum = parseFloat(aVal);
          const bNum = parseFloat(bVal);
          comparison = aNum - bNum;
        } else {
          comparison = String(aVal).localeCompare(String(bVal));
        }

        return sortDirection === 'asc' ? comparison : -comparison;
      });
    }

    return result;
  });

  // Compute total pages reactively
  let totalPages = $derived(Math.ceil(filteredData.length / rowsPerPage));

  // Compute paginated data reactively
  let paginatedData = $derived.by(() => {
    const startIndex = (currentPage - 1) * rowsPerPage;
    const endIndex = startIndex + rowsPerPage;
    return filteredData.slice(startIndex, endIndex);
  });

  function handleSort(column: ColumnKey) {
    if (sortColumn === column) {
      // Toggle direction: asc -> desc -> null
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
    // Reset to first page when sorting changes
    currentPage = 1;
  }

  function handleFilter(column: ColumnKey, value: string) {
    filters[column] = value;
    // Reset to first page when filters change
    currentPage = 1;
  }

  function formatSpearman(value: string): string {
    const num = parseFloat(value);
    return isNaN(num) ? value : num.toFixed(4);
  }
</script>

<div class="p-6 max-w-7xl mx-auto">
    <div class="overflow-x-auto border border-gray-300 rounded-lg bg-white shadow-sm">
      <table class="w-full border-collapse">
        <thead>
          <tr>
            {#each columns as column}
              <th class="bg-gray-100 p-0 border-b-2 border-gray-300 sticky top-0 z-10">
                <button
                  class="w-full p-3 border-none bg-transparent cursor-pointer font-semibold text-left flex items-center gap-2 transition-colors duration-150 hover:bg-gray-200 {sortColumn === column.key ? 'bg-gray-300' : ''}"
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
                  value={filters[column.key]}
                  oninput={(e) => handleFilter(column.key, e.currentTarget.value)}
                />
              </th>
            {/each}
          </tr>
        </thead>
        <tbody>
          {#each paginatedData as row, i}
            <tr class="hover:bg-gray-50">
              <td class="p-3 {i === paginatedData.length - 1 ? '' : 'border-b border-gray-200'}">{row.game}</td>
              <td class="p-3 {i === paginatedData.length - 1 ? '' : 'border-b border-gray-200'}">{row.model}</td>
              <td class="p-3 {i === paginatedData.length - 1 ? '' : 'border-b border-gray-200'}">{row.dataset}</td>
              <td class="p-3 {i === paginatedData.length - 1 ? '' : 'border-b border-gray-200'}">{formatSpearman(row.spearman)}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
</div>

<Pagination bind:currentPage {totalPages}/>
