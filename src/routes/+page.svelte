<script lang="ts">
  import { goto } from "$app/navigation";
  import { base } from "$app/paths";
  import logo from "$lib/assets/logo.png";
  import { modelsStore } from "$lib/stores/models";
  import { marked } from "marked";

  const modelsPerPage = 12;

  let currentPage = $state(1);
  let searchQuery = $state("");

  const models = $derived($modelsStore);

  let filteredModels = $derived(
    searchQuery
      ? models.filter((model) =>
          (typeof model.frontmatter.name === "string"
            ? model.frontmatter.name
            : ""
          )
            .toLowerCase()
            .includes(searchQuery.toLowerCase()),
        )
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
    goto(`${base}/${slug}`);
  }
</script>

<header class="sticky top-0 border-b border-gray-100 bg-white z-30">
  <div class="flex space-x-10 items-center h-16 px-6 max-w-7xl mx-auto">
    <div class="cursor-pointer w-10">
      <img src={logo} alt="proteingym-logo" />
    </div>
    <div class="relative">
      <div class="absolute top-2 left-2">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          class="h-5 w-5 text-gray-500"
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
        class="text-base text-gray-700 pl-8 rounded-md border border-gray-100 h-9 pr-8 focus:shadow-xl focus:outline-none focus:ring-1 focus:ring-gray-100 focus:border-gray-100"
        type="text"
        placeholder="Filter by name"
      />
    </div>
  </div>
</header>

<main
  class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 p-6 max-w-7xl mx-auto auto-rows-fr"
>
  {#each paginatedModels as model}
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
            {#each model.frontmatter.tags as tag}
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
          {@html marked(model.content)}
        </div>
      </div>
    </div>
  {/each}
</main>

<nav>
  <ul
    class="flex justify-center items-center space-x-2 text-gray-700 text-base max-w-7xl mx-auto"
  >
    <li>
      <button
        onclick={() => currentPage--}
        class="flex items-center rounded-lg px-2.5 py-1 hover:bg-gray-100 text-gray-400 hover:text-gray-700 {currentPage ===
        1
          ? 'pointer-events-none cursor-default'
          : ''}"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          stroke-width="1.5"
          stroke="currentColor"
          class="size-6"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            d="M15.75 19.5 8.25 12l7.5-7.5"
          />
        </svg>
        Previous
      </button>
    </li>
    <li>
      <span class="text-gray-400">Page {currentPage} of {totalPages}</span>
    </li>
    <li>
      <button
        onclick={() => currentPage++}
        class="text-base flex items-center rounded-lg px-2.5 py-1 hover:bg-gray-100 text-gray-400 hover:text-gray-700 {currentPage ===
        totalPages
          ? 'pointer-events-none cursor-default'
          : ''}"
      >
        Next
        <svg
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          stroke-width="1.5"
          stroke="currentColor"
          class="size-6"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            d="m8.25 4.5 7.5 7.5-7.5 7.5"
          />
        </svg>
      </button>
    </li>
  </ul>
</nav>
