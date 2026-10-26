<script lang="ts">
  import { goto } from "$app/navigation";
  import { base } from "$app/paths";
  import { page } from "$app/stores";
  import { datasetsStore, datasetsCommitHash } from "$lib/stores/datasets";
  import { getDatasetDownloadUrl } from "$lib/config";
  import Histogram from "$lib/components/Histogram.svelte";

  const datasets = $derived($datasetsStore);

  let dataset = $derived(
    datasets.find((d) => d.slug === $page.params.slug),
  );

  type HistogramData = { target: string; n: number; edges: number[]; counts: number[] };
  let histogram = $state<HistogramData | null>(null);
  $effect(() => {
    const slug = $page.params.slug;
    fetch(`${base}/histograms.json`)
      .then((r): Promise<{ histograms?: Record<string, HistogramData> }> => (r.ok ? r.json() : Promise.resolve({})))
      .then((h) => (histogram = slug ? (h.histograms?.[slug] ?? null) : null))
      .catch(() => (histogram = null));
  });

  function navigateBack() {
    goto(`${base}/datasets`);
  }

  // Sentence-case labels from manifest keys, keeping acronyms intact.
  const WORDS: Record<string, string> = {
    msa: "MSA", msas: "MSAs", doi: "DOI", id: "ID", ids: "IDs", uniprot: "UniProt", pfam: "Pfam",
    afdb: "AFDB", pae: "PAE", pdb: "PDB", plddt: "pLDDT", rcsb: "RCSB", dms: "DMS",
  };
  const label = (key: string) => {
    const text = key.split("_").map((w) => WORDS[w] ?? w).join(" ");
    return text[0].toUpperCase() + text.slice(1);
  };
  // Long decimal strings (e.g. mean pLDDT 77.72754149305364) are shown to 3 places.
  const tidy = (v: unknown) => (typeof v === "string" && /^-?\d+\.\d{4,}$/.test(v) ? Number(v).toFixed(3) : v);
  const isRecord = (v: unknown): v is Record<string, unknown> => typeof v === "object" && v !== null && !Array.isArray(v);
</script>

{#if dataset}
  <div class="min-h-screen">
    <header class="bg-white border-b border-gray-200">
      <div class="max-w-4xl mx-auto px-6 py-4">
        <button
          onclick={navigateBack}
          class="text-accent-600 hover:text-accent-700 mb-4 -ml-3 py-1 px-3 inline-flex items-center rounded-full hover:bg-accent-50"
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
            <p class="text-gray-600 mb-3 max-w-prose">{dataset.data.description}</p>
          {/if}
          <div class="flex flex-wrap gap-2 mb-4">
            {#each Object.entries(dataset.data) as [key, value]}
              {#if key !== "name" && key !== "description" && key !== "_archive_filename" && !Array.isArray(value) && typeof value !== "object"}
                <span class="px-3 py-1 text-sm font-medium bg-gray-100 text-gray-700 rounded-full">
                  {key}: {value}
                </span>
              {/if}
            {/each}
          </div>
          <a
            href={getDatasetDownloadUrl(dataset.slug)}
            class="inline-flex items-center px-4 py-2 bg-accent-600 text-white rounded-md hover:bg-accent-700 transition-colors"
            download
          >
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-5 h-5 mr-2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
              </svg>
              Download dataset
            </a>
        </div>
      </div>
    </header>

    <main class="max-w-4xl mx-auto px-6 py-8">
      {#snippet scalar(k: string, v: unknown)}
        {#if Array.isArray(v)}
          {v.map((x) => (isRecord(x) ? (x.name ?? JSON.stringify(x)) : x)).join(", ")}
        {:else if isRecord(v)}
          {@render record(v)}
        {:else if typeof v === "boolean"}
          {v ? "Yes" : "No"}
        {:else if typeof v === "number"}
          {v.toLocaleString()}
        {:else if k === "doi"}
          <a href="https://doi.org/{v}" class="text-accent-600 hover:text-accent-700 hover:underline" target="_blank" rel="noopener">{v}</a>
        {:else}
          {tidy(v)}
        {/if}
      {/snippet}

      {#snippet record(item: Record<string, unknown>, skip = "")}
        <dl class="space-y-2">
          {#each Object.entries(item).filter(([k]) => k !== skip) as [k, v] (k)}
            <div class="grid grid-cols-1 sm:grid-cols-[11rem_1fr] gap-x-4">
              <dt class="text-sm text-gray-500">{label(k)}</dt>
              <!-- overflow-wrap only breaks tokens too long for the line (sequences, paths), never words -->
              <dd class="text-sm text-gray-900 min-w-0 [overflow-wrap:anywhere]">{@render scalar(k, v)}</dd>
            </div>
          {/each}
        </dl>
      {/snippet}

      {#snippet fields(value: unknown)}
        {#if Array.isArray(value)}
          <div class="space-y-3">
            {#each value as item, idx (idx)}
              {@const heading = value.length > 1 && isRecord(item) && typeof item.name === "string"}
              <div class="rounded-md bg-gray-50 p-4">
                {#if value.length > 1}
                  <h3 class="mb-2 text-sm font-semibold text-gray-900">
                    {heading ? (item as Record<string, unknown>).name : `Item ${idx + 1}`}
                  </h3>
                {/if}
                {#if isRecord(item)}
                  <!-- the name is already the heading -->
                  {@render record(item, heading ? "name" : "")}
                {:else}
                  <p class="text-sm text-gray-900">{item}</p>
                {/if}
              </div>
            {/each}
          </div>
        {:else if isRecord(value)}
          {@render record(value)}
        {:else}
          <p class="text-sm text-gray-900">{value}</p>
        {/if}
      {/snippet}

      <div class="space-y-6">
        {#if dataset.data.publication}
          <div class="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
            <h2 class="text-lg font-semibold text-gray-900 mb-4">Publication</h2>
            {@render fields(dataset.data.publication)}
          </div>
        {/if}
        {#if histogram}
          <div class="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
            <h2 class="text-lg font-semibold text-gray-900 mb-4">Score distribution</h2>
            <Histogram {...histogram} />
          </div>
        {/if}
        {#each Object.entries(dataset.data) as [key, value] (key)}
          {#if key !== "name" && key !== "description" && key !== "_archive_filename" && key !== "publication" && (Array.isArray(value) || typeof value === "object")}
            <details class="group bg-white rounded-lg shadow-sm border border-gray-200">
              <summary class="flex items-center justify-between cursor-pointer list-none rounded-lg px-6 py-4 hover:bg-gray-50 active:bg-gray-100 transition-colors text-lg font-semibold text-gray-900 [&::-webkit-details-marker]:hidden">
                <span>
                  {label(key)}
                  {#if Array.isArray(value) && value.length > 1}<span class="ml-1 text-sm font-normal text-gray-500">{value.length}</span>{/if}
                </span>
                <svg class="w-5 h-5 text-gray-500 transition-transform duration-200 motion-reduce:transition-none group-open:rotate-180" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                </svg>
              </summary>
              <div class="px-6 pb-6">
                {@render fields(value)}
              </div>
            </details>
          {/if}
        {/each}
      </div>
    </main>

    <div class="text-center py-6">
      <p class="text-sm text-gray-500">Datasets commit: {$datasetsCommitHash}</p>
    </div>
  </div>
{:else}
  <div class="min-h-screen flex items-center justify-center">
    <p class="text-gray-500">Loading dataset…</p>
  </div>
{/if}
