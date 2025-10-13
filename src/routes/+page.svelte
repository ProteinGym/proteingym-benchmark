<script>
  import logo from '$lib/assets/logo.png';
  import { marked } from 'marked';

  export let data;
  $: models = data.models;
</script>

<header class="sticky top-0 border-b border-gray-100 bg-white z-30">
  <div class="flex space-x-10 items-center h-16 mx-4 xl:max-w-6xl xl:mx-auto">
    <div class="cursor-pointer w-10">
      <img src={logo} alt="proteingym-logo">
    </div>
    <div class="relative">
      <div class="absolute top-2 left-2">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-gray-500" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
        </svg>
      </div>
      <input class="pl-8 rounded-md border border-gray-100 h-9 w-80 pr-8 focus:shadow-xl focus:outline-none focus:ring-1 focus:ring-gray-100 focus:border-gray-100" type="text" placeholder="Search models...">
    </div>
  </div>
</header>

<main class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 p-6 max-w-7xl mx-auto">
  {#each models as model}
    <div
      class="bg-white border border-gray-200 rounded-lg shadow-sm hover:shadow-md transition-shadow p-6 h-80 flex flex-col cursor-pointer"
      role="button"
      tabindex="0"
    >
      <div class="mb-4">
        <h2 class="text-xl font-semibold text-gray-900 mb-2">{model.frontmatter.name}</h2>
        {#if model.frontmatter.tags}
          <div class="flex flex-wrap gap-2 mb-3">
            {#each model.frontmatter.tags as tag}
              <span class="px-2 py-1 text-xs font-medium bg-blue-100 text-blue-800 rounded-full">{tag}</span>
            {/each}
          </div>
        {/if}
      </div>
      <div class="prose prose-sm max-w-none text-gray-600 overflow-hidden flex-1">
        <div class="line-clamp-6">
          {@html marked(model.content)}
        </div>
      </div>
    </div>
  {/each}
</main>
