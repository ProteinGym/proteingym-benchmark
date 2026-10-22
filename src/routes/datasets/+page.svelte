<script lang="ts">
  import { base } from "$app/paths";
  import { datasetsStore, datasetsCommitHash } from "$lib/stores/datasets";
  import Pagination from '$lib/components/Pagination.svelte';
  import type { Dataset } from "$lib/types/dataset";
  import { onMount } from "svelte";

  const datasetsPerPage = 50;

  let currentPage = $state(1);
  let searchQuery = $state("");

  const datasets = $derived($datasetsStore);

  // target_phenotype and readout of the dataset's assay, each with its bubble colour
  function assayTags(data: Record<string, unknown>): { label: string; cls: string }[] {
    const assay = (data.assays as Record<string, unknown>[] | undefined)?.[0];
    return [
      { value: assay?.target_phenotype, cls: "bg-green-100 text-green-800" },
      { value: assay?.readout, cls: "bg-amber-100 text-amber-800" },
    ]
      .filter((t) => typeof t.value === "string")
      .map((t) => ({ label: (t.value as string).replace(/_/g, " "), cls: t.cls }));
  }

  let filteredDatasets = $derived(
    searchQuery
      ? datasets.filter((dataset) => {
          const query = searchQuery.toLowerCase();
          const name = ((dataset.data.name as string) || dataset.slug).toLowerCase();
          const tags = Object.entries(dataset.data)
            .filter(([k, v]) => k !== "name" && k !== "description" && !Array.isArray(v) && typeof v !== "object")
            .map(([k, v]) => `${k} ${v}`.toLowerCase())
            .join(" ");
          return (
            name.includes(query) ||
            tags.includes(query) ||
            assayTags(dataset.data).some((t) => t.label.includes(query))
          );
        })
      : datasets,
  );

  function variantCount(data: Record<string, unknown>): number | undefined {
    const n = (data.assays as Record<string, unknown>[] | undefined)?.[0]?.number_of_variants;
    return typeof n === "number" ? n : undefined;
  }
  const datasetName = (d: Dataset) => String(d.data.name || d.slug);

  let sortKey = $state<"name" | "variants">("name");
  let sortAsc = $state(true);

  function sortBy(key: "name" | "variants") {
    // first click on variants shows the largest datasets first
    sortAsc = key === sortKey ? !sortAsc : key === "name";
    sortKey = key;
    currentPage = 1;
  }

  let sortedDatasets = $derived(
    [...filteredDatasets].sort((a, b) => {
      const cmp =
        sortKey === "name"
          ? datasetName(a).localeCompare(datasetName(b))
          : (variantCount(a.data) ?? -1) - (variantCount(b.data) ?? -1);
      return sortAsc ? cmp : -cmp;
    }),
  );

  let paginatedDatasets = $derived(
    sortedDatasets.slice(
      (currentPage - 1) * datasetsPerPage,
      currentPage * datasetsPerPage,
    ),
  );

  let totalPages = $derived(
    Math.ceil(filteredDatasets.length / datasetsPerPage),
  );

  // keep search/sort/page across visits to a dataset and back (per tab)
  const STATE_KEY = "datasets-table";
  let restored = $state(false);
  onMount(() => {
    try {
      const saved = JSON.parse(sessionStorage.getItem(STATE_KEY) ?? "null");
      if (saved) {
        ({ searchQuery, sortKey, sortAsc, currentPage } = saved);
        shownPage = currentPage;
      }
    } catch {
      // storage blocked or corrupt: start from defaults
    }
    restored = true;
  });
  $effect(() => {
    const state = { searchQuery, sortKey, sortAsc, currentPage };
    if (!restored) return;
    try {
      sessionStorage.setItem(STATE_KEY, JSON.stringify(state));
    } catch {
      // storage blocked: state just won't persist
    }
  });

  // jump back to the top of the table when the page changes (not on first load)
  let shownPage = 1;
  $effect(() => {
    if (currentPage !== shownPage) {
      shownPage = currentPage;
      window.scrollTo({ top: 0 });
    }
  });
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
      oninput={() => (currentPage = 1)}
      class="w-full bg-white text-base text-gray-700 pl-8 rounded-md border border-gray-200 h-9 pr-8 shadow-sm focus:outline-none focus:ring-2 focus:ring-accent-600/30 focus:border-accent-600"
      type="text"
      placeholder="Filter by dataset name or tags"
    />
  </div>
</div>

<main class="p-6 max-w-7xl mx-auto">
  <div class="bg-white border border-gray-200 rounded-lg shadow-sm divide-y divide-gray-200">
    <div class="grid grid-cols-[1fr_auto] md:grid-cols-[22rem_7rem_1fr] gap-6 px-6 py-3 text-xs font-semibold uppercase tracking-wide text-gray-500">
      {#each [["name", "Dataset"], ["variants", "Variants"]] as const as [key, label]}
        <button
          type="button"
          onclick={() => sortBy(key)}
          aria-label="Sort by {label.toLowerCase()}{sortKey === key ? (sortAsc ? ', currently ascending' : ', currently descending') : ''}"
          class="flex items-center gap-1 uppercase tracking-wide hover:text-gray-900 {key === 'variants' ? 'md:justify-end' : ''} {sortKey === key ? 'text-gray-900' : ''}"
        >
          {label}
          <span aria-hidden="true" class={sortKey === key ? "" : "invisible"}>{sortAsc ? "▲" : "▼"}</span>
        </button>
      {/each}
      <span class="hidden md:block">Description</span>
    </div>
    {#each paginatedDatasets as dataset (dataset.slug)}
      <a
        href="{base}/datasets/{dataset.slug}"
        class="grid grid-cols-1 md:grid-cols-[22rem_7rem_1fr] gap-x-6 gap-y-1 group px-6 py-3 hover:bg-accent-50 active:bg-accent-100 transition-colors"
      >
        <h2 class="text-base font-semibold text-gray-900 group-hover:text-accent-700 truncate min-w-0">
          {dataset.data.name || dataset.slug}
        </h2>
        <span class="text-sm text-gray-700 tabular-nums md:text-right md:row-span-2">
          {variantCount(dataset.data)?.toLocaleString() ?? "—"}<span class="md:hidden"> variants</span>
        </span>
        <p class="text-sm text-gray-600 line-clamp-2">{dataset.data.description ?? ""}</p>
        <div class="flex flex-wrap gap-2">
          {#each Object.entries(dataset.data) as [key, value]}
            {#if key !== "name" && key !== "description" && key !== "_archive_filename" && !Array.isArray(value) && typeof value !== "object"}
              <span class="px-2 py-0.5 text-xs font-medium bg-gray-100 text-gray-700 rounded-full">
                {key}: {value}
              </span>
            {/if}
          {/each}
        </div>
        <div class="flex flex-wrap gap-2">
          {#each assayTags(dataset.data) as tag}
            <span class="px-2 py-0.5 text-xs font-medium rounded-full {tag.cls}">
              {tag.label}
            </span>
          {/each}
        </div>
      </a>
    {:else}
      <p class="px-6 py-10 text-center text-sm text-gray-500">
        {datasets.length ? `No datasets match “${searchQuery}”.` : "Loading datasets…"}
      </p>
    {/each}
  </div>
</main>

<Pagination bind:currentPage {totalPages}/>

<div class="text-center py-6">
  <p class="text-sm text-gray-500">Datasets commit: {$datasetsCommitHash}</p>
</div>
