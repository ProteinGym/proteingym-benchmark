<script>
  import { goto } from '$app/navigation';
  import { marked } from 'marked';
  import { base } from '$app/paths';

  export let data;
  $: model = data.model;
</script>

<div class="min-h-screen bg-gray-50">
  <header class="bg-white border-b border-gray-200 sticky top-0 z-10">
    <div class="max-w-4xl mx-auto px-6 py-4">
      <button
        on:click={() => goto(`${base}/`)}
        class="text-blue-600 hover:text-blue-800 mb-4 inline-flex items-center"
      >
        <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18"/>
        </svg>
        Back to Models
      </button>

      <div class="mb-4">
        <h1 class="text-3xl font-bold text-gray-900 mb-3">{model.frontmatter.name}</h1>
        {#if model.frontmatter.tags}
          <div class="flex flex-wrap gap-2">
            {#each model.frontmatter.tags as tag}
              <span class="px-3 py-1 text-sm font-medium bg-blue-100 text-blue-800 rounded-full">{tag}</span>
            {/each}
          </div>
        {/if}
      </div>
    </div>
  </header>

  <main class="max-w-4xl mx-auto px-6 py-8">
    <div class="bg-white rounded-lg shadow-sm border border-gray-200 p-8">
      <div class="prose prose-sm max-w-none">
        {@html marked(model.content)}
      </div>
    </div>
  </main>
</div>