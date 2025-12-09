<script lang="ts">
  import { goto } from "$app/navigation";
  import { base } from "$app/paths";
  import { page } from "$app/stores";
  import { datasetsStore } from "$lib/stores/datasets";

  const datasets = $derived($datasetsStore);

  let dataset = $derived(
    datasets.find((d) => d.slug === $page.params.slug),
  );

  function navigateBack() {
    goto(`${base}/datasets`);
  }
</script>

{#if dataset}
  <div class="min-h-screen bg-gray-50">
    <header class="bg-white border-b border-gray-200 sticky top-0 z-10">
      <div class="max-w-4xl mx-auto px-6 py-4">
        <button
          onclick={navigateBack}
          class="text-blue-600 hover:text-blue-800 mb-4 py-1 px-3 inline-flex items-center rounded-full hover:bg-blue-100"
        >
          <svg
            class="w-4 h-4 mr-2"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M10 19l-7-7m0 0l7-7m-7 7h18"
            />
          </svg>
          Back to Datasets
        </button>

        <div class="mb-4">
          <h1 class="text-3xl font-bold text-gray-900 mb-3">
            {dataset.data.name || dataset.slug}
          </h1>
          {#if dataset.data.description}
            <p class="text-gray-600 mb-3">{dataset.data.description}</p>
          {/if}
          <div class="flex flex-wrap gap-2 mb-4">
            {#each Object.entries(dataset.data) as [key, value]}
              {#if key !== "name" && key !== "description" && key !== "_archive_filename" && !Array.isArray(value) && typeof value !== "object"}
                <span class="px-3 py-1 text-sm font-medium bg-blue-100 text-blue-800 rounded-full">
                  {key}: {value}
                </span>
              {/if}
            {/each}
          </div>
          {#if dataset.data._archive_filename}
            <a
              href="https://github.com/ProteinGym/proteingym-benchmark/raw/main/datasets/{dataset.data._archive_filename}"
              class="inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
              download
            >
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-5 h-5 mr-2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
              </svg>
              Download Dataset
            </a>
          {/if}
        </div>
      </div>
    </header>

    <main class="max-w-4xl mx-auto px-6 py-8">
      <div class="space-y-6">
        {#each Object.entries(dataset.data) as [key, value] (key)}
          {#if key !== "name" && key !== "description" && key !== "_archive_filename" && (Array.isArray(value) || typeof value === "object")}
            <div class="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
              <h2 class="text-lg font-semibold text-gray-900 mb-4 capitalize">
                {key.replace(/_/g, " ")}
              </h2>
              <div class="text-gray-900">
                {#if Array.isArray(value)}
                  <div class="space-y-4">
                    {#each value as item, idx}
                      <div class="bg-gray-50 p-4 rounded">
                        <h3 class="text-sm font-medium text-gray-700 mb-2">Item {idx + 1}</h3>
                        {#if typeof item === "object" && item !== null}
                          <dl class="space-y-3">
                            {#each Object.entries(item) as [k, v]}
                              <div class="grid grid-cols-[10rem_1fr] gap-4">
                                <dt class="text-sm font-medium text-gray-600">{k}:</dt>
                                <dd class="text-sm text-gray-900 min-w-0" style="overflow-wrap: anywhere; word-break: break-all;">
                                  {#if typeof v === "object" && v !== null}
                                    <pre class="text-xs break-words whitespace-pre-wrap">{JSON.stringify(v, null, 2)}</pre>
                                  {:else}
                                    {v}
                                  {/if}
                                </dd>
                              </div>
                            {/each}
                          </dl>
                        {:else}
                          <p class="text-sm">{item}</p>
                        {/if}
                      </div>
                    {/each}
                  </div>
                {:else if typeof value === "object" && value !== null}
                  <dl class="space-y-3">
                    {#each Object.entries(value) as [k, v]}
                      <div class="grid grid-cols-[10rem_1fr] gap-4">
                        <dt class="text-sm font-medium text-gray-600">{k}:</dt>
                        <dd class="text-sm text-gray-900 min-w-0" style="overflow-wrap: anywhere; word-break: break-all;">
                          {#if typeof v === "object" && v !== null}
                            <pre class="text-xs bg-gray-50 p-2 rounded break-words whitespace-pre-wrap">{JSON.stringify(v, null, 2)}</pre>
                          {:else}
                            {v}
                          {/if}
                        </dd>
                      </div>
                    {/each}
                  </dl>
                {:else}
                  <p class="text-base">{value}</p>
                {/if}
              </div>
            </div>
          {/if}
        {/each}
      </div>
    </main>
  </div>
{:else}
  <div class="min-h-screen bg-gray-50 flex items-center justify-center">
    <p class="text-gray-600">Loading...</p>
  </div>
{/if}
