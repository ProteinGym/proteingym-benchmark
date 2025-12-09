<script lang="ts">
  import { goto } from "$app/navigation";
  import { base } from "$app/paths";
  import { datasetsStore } from "$lib/stores/datasets";
  import Pagination from '$lib/components/Pagination.svelte';

  const datasetsPerPage = 12;

  let currentPage = $state(1);
  let searchQuery = $state("");

  const datasets = $derived($datasetsStore);

  let filteredDatasets = $derived(
    searchQuery
      ? datasets.filter((dataset) => {
          const query = searchQuery.toLowerCase();
          const name = ((dataset.data.name as string) || dataset.slug).toLowerCase();
          const tags = Object.entries(dataset.data)
            .filter(([k, v]) => k !== "name" && k !== "description" && !Array.isArray(v) && typeof v !== "object")
            .map(([k, v]) => `${k} ${v}`.toLowerCase())
            .join(" ");
          return name.includes(query) || tags.includes(query);
        })
      : datasets,
  );

  let paginatedDatasets = $derived(
    filteredDatasets.slice(
      (currentPage - 1) * datasetsPerPage,
      currentPage * datasetsPerPage,
    ),
  );

  let totalPages = $derived(
    Math.ceil(filteredDatasets.length / datasetsPerPage),
  );

  $effect(() => {
    if (searchQuery) {
      currentPage = 1;
    }
  });

  function navigateToDataset(slug: string) {
    goto(`${base}/datasets/${slug}`);
  }
</script>

<!-- Search Bar -->
<div class="max-w-7xl mx-auto px-6 mt-6">
  <div class="flex relative max-w-xs">
    <div class="absolute top-2.5 left-2">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        class="h-4 w-4 text-gray-500"
        fill="none"
        viewBox="0 0 24 24"
        stroke-width="1.5"
        stroke="currentColor"
      >
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z"
        />
      </svg>
    </div>
    <input
      bind:value={searchQuery}
      class="w-full text-base text-gray-700 pl-8 rounded-md border border-gray-100 h-9 pr-8 shadow-sm focus:shadow-md focus:outline-none focus:ring-1 focus:ring-gray-100 focus:border-gray-100"
      type="text"
      placeholder="Filter by dataset name or tags"
    />
  </div>
</div>

<main
  class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 p-6 max-w-7xl mx-auto auto-rows-fr"
>
  {#each paginatedDatasets as dataset (dataset.slug)}
    <div
      class="bg-white border border-gray-200 rounded-lg shadow-sm hover:shadow-md transition-shadow p-6 flex flex-col cursor-pointer"
      onclick={() => navigateToDataset(dataset.slug)}
      onkeydown={(e) => e.key === "Enter" && navigateToDataset(dataset.slug)}
      role="button"
      tabindex="0"
    >
      <div class="mb-4">
        <h2 class="text-xl font-semibold text-gray-900 mb-2">
          {dataset.data.name || dataset.slug}
        </h2>
        <div class="flex flex-wrap gap-2 mb-3">
          {#each Object.entries(dataset.data) as [key, value]}
            {#if key !== "name" && key !== "description" && key !== "_archive_filename" && !Array.isArray(value) && typeof value !== "object"}
              <span class="px-2 py-1 text-xs font-medium bg-blue-100 text-blue-800 rounded-full">
                {key}: {value}
              </span>
            {/if}
          {/each}
        </div>
      </div>
      <div class="text-sm text-gray-600 flex-1">
        {#if dataset.data.description}
          <p class="line-clamp-6">{dataset.data.description}</p>
        {/if}
      </div>
    </div>
  {/each}
</main>

<Pagination bind:currentPage {totalPages}/>
