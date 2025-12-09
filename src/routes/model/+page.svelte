<script lang="ts">
  import { goto } from "$app/navigation";
  import { base } from "$app/paths";
  import { modelsStore } from "$lib/stores/models";
  import { marked } from "marked";
  import Pagination from '$lib/components/Pagination.svelte';

  const modelsPerPage = 12;

  let currentPage = $state(1);
  let searchQuery = $state("");

  const models = $derived($modelsStore);

  let filteredModels = $derived(
    searchQuery
      ? models.filter((model) => {
          const query = searchQuery.toLowerCase();
          const name = (typeof model.frontmatter.name === "string" ? model.frontmatter.name : "").toLowerCase();
          const tags = (model.frontmatter.tags || []).map((t: string) => t.toLowerCase()).join(" ");
          return name.includes(query) || tags.includes(query);
        })
      : models,
  );

  let paginatedModels = $derived(
    filteredModels.slice(
      (currentPage - 1) * modelsPerPage,
      currentPage * modelsPerPage,
    ),
  );

  let totalPages = $derived(Math.ceil(filteredModels.length / modelsPerPage));

  $effect(() => {
    if (searchQuery) {
      currentPage = 1;
    }
  });

  function navigateToModel(slug: string) {
    goto(`${base}/model/${slug}`);
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
    placeholder="Filter by model name or tags"
  />
  </div>
</div>

<!-- Model Card -->
<main
  class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 p-6 max-w-7xl mx-auto auto-rows-fr"
>
  {#each paginatedModels as model (model.slug)}
    <div
      class="bg-white border border-gray-200 rounded-lg shadow-sm hover:shadow-md transition-shadow p-6 flex flex-col cursor-pointer"
      onclick={() => navigateToModel(model.slug)}
      onkeydown={(e) => e.key === "Enter" && navigateToModel(model.slug)}
      role="button"
      tabindex="0"
    >
      <div class="mb-4">
        <h2 class="text-xl font-semibold text-gray-900 mb-2">
          {model.frontmatter.name}
        </h2>
        {#if model.frontmatter.tags}
          <div class="flex flex-wrap gap-2 mb-3">
            {#each model.frontmatter.tags as tag (tag)}
              <span
                class="px-2 py-1 text-xs font-medium bg-blue-100 text-blue-800 rounded-full"
                >{tag}</span
              >
            {/each}
          </div>
        {/if}
      </div>
      <div
        class="prose prose-sm max-w-none text-gray-600 overflow-hidden flex-1"
      >
        <div class="line-clamp-6">
          <!-- eslint-disable-next-line svelte/no-at-html-tags -->
          {@html marked(model.overview)}
        </div>
      </div>
    </div>
  {/each}
</main>

<Pagination bind:currentPage {totalPages}/>
