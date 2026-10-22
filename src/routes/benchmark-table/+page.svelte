<script lang="ts">
  import Pagination from '$lib/components/Pagination.svelte';
  import NormalizeToggle from '$lib/components/NormalizeToggle.svelte';
  import { benchmarkStore } from '$lib/stores/benchmark';
  import { datasetsStore } from '$lib/stores/datasets';
  import {
    bestSizes,
    datasetMeta,
    FAMILIES,
    familyColor,
    normalizationKeys,
    scoresByModel,
    summarize
  } from '$lib/benchmark';
  import { base } from '$app/paths';

  // Leaderboard of per-model aggregates (same conventions as the violin plots),
  // with a per-dataset view underneath for the full score matrix.

  const datasetsPerPage = 50;

  let rawData = $derived($benchmarkStore.rawData);
  let games = $derived([...new Set(rawData.map((r) => r.game))].sort());
  let splits = $derived([...new Set(rawData.map((r) => r.split))].sort());
  let game = $state('zero_shot');
  let split = $state('random');
  let sizes = $state<'all' | 'best'>('best');
  let byProtein = $state(false);
  let byPaper = $state(false);
  let view = $state<'models' | 'datasets'>('models');
  let activeGame = $derived(game || games[0] || '');
  let activeSplit = $derived(split || splits[0] || '');

  let metaOf = $derived(datasetMeta($datasetsStore));
  const phenotype = (dataset: string) => metaOf.get(dataset)?.phenotype ?? 'unknown';
  let byModel = $derived(scoresByModel(rawData, activeGame, activeSplit));
  let keys = $derived(normalizationKeys(metaOf));
  let unitLabel = $derived(
    byProtein && byPaper ? 'protein/paper groups' : byProtein ? 'proteins' : byPaper ? 'papers' : 'datasets'
  );

  let overall = $derived(summarize(byModel, () => true, keys, byProtein, byPaper));
  // Named phenotypes present in this game/split; "unknown" only counts toward the overall mean.
  let phenotypes = $derived(
    [...new Set([...byModel.values()].flatMap((m) => [...m.keys()]).map(phenotype))]
      .filter((p) => p !== 'unknown')
      .sort()
  );
  let perPhenotype = $derived(
    phenotypes.map((p) => {
      const s = summarize(byModel, (d) => phenotype(d) === p, keys, byProtein, byPaper);
      return { phenotype: p, units: s.units, mean: new Map(s.models.map((m) => [m.model, s.units ? m.mean : undefined])) };
    })
  );

  // ---- Models view ----
  type ModelRow = { model: string; mean: number; rank: number; delta: number; phenoMeans: (number | undefined)[] };
  let modelRows = $derived.by((): ModelRow[] => {
    if (!overall.common) return [];
    const byMean = [...overall.models].sort((a, b) => b.mean - a.mean || a.rank - b.rank);
    const shown = sizes === 'all' ? byMean : bestSizes(byMean);
    const top = shown[0]?.mean ?? 0;
    return shown.map((m) => ({
      model: m.model,
      mean: m.mean,
      rank: m.rank,
      delta: m.mean - top,
      phenoMeans: perPhenotype.map((p) => p.mean.get(m.model))
    }));
  });

  type ModelSortKey = 'model' | 'mean' | 'rank' | number; // number = phenotype column index
  let modelSort = $state<{ key: ModelSortKey; asc: boolean }>({ key: 'mean', asc: false });
  const modelValue = (r: ModelRow, key: ModelSortKey) =>
    key === 'model' ? r.model : key === 'mean' ? r.mean : key === 'rank' ? r.rank : r.phenoMeans[key];
  let sortedModelRows = $derived(sortRows(modelRows, (r) => modelValue(r, modelSort.key), modelSort.asc));
  // Best value per numeric column, so it can be emphasised like a leaderboard.
  let best = $derived({
    mean: Math.max(...modelRows.map((r) => r.mean)),
    rank: Math.min(...modelRows.map((r) => r.rank)),
    pheno: perPhenotype.map((_, i) => Math.max(...modelRows.map((r) => r.phenoMeans[i] ?? -Infinity)))
  });

  // ---- Datasets view ----
  let shownModels = $derived(modelRows.map((r) => r.model));
  let datasets = $derived([...new Set([...byModel.values()].flatMap((m) => [...m.keys()]))]);
  let searchQuery = $state('');
  let datasetSort = $state<{ key: string; asc: boolean }>({ key: 'dataset', asc: true });
  let currentPage = $state(1);
  const score = (dataset: string, model: string) => byModel.get(model)?.get(dataset);
  let filteredDatasets = $derived.by(() => {
    const q = searchQuery.toLowerCase();
    const hits = q ? datasets.filter((d) => d.toLowerCase().includes(q) || phenotype(d).includes(q)) : datasets;
    const value = (d: string) =>
      datasetSort.key === 'dataset' ? d : score(d, datasetSort.key);
    return sortRows(hits, value, datasetSort.asc);
  });
  let totalPages = $derived(Math.ceil(filteredDatasets.length / datasetsPerPage));
  let pageDatasets = $derived(
    filteredDatasets.slice((currentPage - 1) * datasetsPerPage, currentPage * datasetsPerPage)
  );
  let shownPage = 1;
  $effect(() => {
    if (currentPage !== shownPage) {
      shownPage = currentPage;
      window.scrollTo({ top: 0 });
    }
  });

  /** Sort by a string or number; missing numbers always go last. */
  function sortRows<T>(rows: T[], value: (r: T) => string | number | undefined, asc: boolean): T[] {
    return [...rows].sort((a, b) => {
      const va = value(a);
      const vb = value(b);
      if (va === undefined || vb === undefined) return va === vb ? 0 : va === undefined ? 1 : -1;
      const cmp = typeof va === 'string' ? va.localeCompare(vb as string) : va - (vb as number);
      return asc ? cmp : -cmp;
    });
  }
  // First click sorts best-first: names A→Z, ranks low→high, scores high→low.
  function toggleSort<K>(state: { key: K; asc: boolean }, key: K, ascByDefault: boolean) {
    return state.key === key ? { key, asc: !state.asc } : { key, asc: ascByDefault };
  }
  const fmt = (v: number | undefined, digits = 3) => (v === undefined ? '—' : v.toFixed(digits));
  const fmtDelta = (v: number) => (Math.abs(v) < 5e-4 ? '—' : `${v < 0 ? '−' : '+'}${Math.abs(v).toFixed(3)}`);
  const ariaSort = (active: boolean, asc: boolean) => (active ? (asc ? 'ascending' : 'descending') : undefined);
</script>

<!-- caps=false for model names and ρ (uppercasing turns ρ into Ρ) -->
{#snippet sortHeader(label: string, active: boolean, asc: boolean, onclick: () => void, align: 'left' | 'right', caps = true)}
  <button
    type="button"
    {onclick}
    class="inline-flex items-center gap-1 hover:text-gray-900 {caps ? 'uppercase tracking-wide' : ''} {align === 'right'
      ? 'flex-row-reverse text-right'
      : 'text-left'} {active ? 'text-gray-900' : ''}"
  >
    <!-- long model names may only wrap after an underscore, never mid-word -->
    <span>{#each label.split('_') as part, i (i)}{#if i}_<wbr />{/if}{part}{/each}</span>
    <span aria-hidden="true" class={active ? '' : 'invisible'}>{asc ? '▲' : '▼'}</span>
  </button>
{/snippet}

<div class="p-6 max-w-7xl mx-auto">
  <div class="mb-4 flex flex-wrap items-center gap-4 text-sm">
    <div class="inline-flex rounded-lg border border-gray-300 bg-white p-0.5" role="group" aria-label="View">
      {#each [['models', 'Models'], ['datasets', 'Datasets']] as const as [key, label] (key)}
        <button
          type="button"
          aria-pressed={view === key}
          onclick={() => (view = key)}
          class="rounded-md px-3 py-1 font-medium transition-colors {view === key
            ? 'bg-accent-600 text-white'
            : 'text-gray-600 hover:bg-gray-100'}"
        >
          {label}
        </button>
      {/each}
    </div>
    <label class="flex items-center gap-2">
      <span class="font-semibold text-gray-700">Game</span>
      <select class="bg-white border border-gray-300 rounded-md px-2 py-1" bind:value={game}>
        {#each games as g (g)}<option value={g}>{g}</option>{/each}
      </select>
    </label>
    <label class="flex items-center gap-2">
      <span class="font-semibold text-gray-700">Split</span>
      <select class="bg-white border border-gray-300 rounded-md px-2 py-1" bind:value={split}>
        {#each splits as s (s)}<option value={s}>{s}</option>{/each}
      </select>
    </label>
    <label class="flex items-center gap-2">
      <span class="font-semibold text-gray-700">Model sizes</span>
      <select class="bg-white border border-gray-300 rounded-md px-2 py-1" bind:value={sizes}>
        <option value="all">Display all param sizes</option>
        <option value="best">Display best param size</option>
      </select>
    </label>
    {#if view === 'models'}
      <NormalizeToggle bind:byProtein bind:byPaper />
    {/if}
    <a href="{base}/benchmark" class="ml-auto text-accent-600 hover:text-accent-700 hover:underline">View as plots →</a>
  </div>

  {#if !rawData.length}
    <p class="text-gray-500">Loading benchmark…</p>
  {:else if view === 'models'}
    {#if !modelRows.length}
      <p class="text-gray-500">No datasets are scored by every model for this game and split.</p>
    {:else}
      <p class="mb-3 text-sm text-gray-500">
        {modelRows.length} of {overall.models.length} models · means over the {overall.units}
        {unitLabel} scored by every model ({overall.common}/{overall.total} datasets) · Δ is the gap to the best mean ρ
      </p>
      <div class="overflow-x-auto bg-white border border-gray-200 rounded-lg shadow-sm">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-200 text-xs font-semibold text-gray-500">
              <th class="px-6 py-3 text-left" aria-sort={ariaSort(modelSort.key === 'model', modelSort.asc)}>
                {@render sortHeader('Model', modelSort.key === 'model', modelSort.asc, () => (modelSort = toggleSort(modelSort, 'model', true)), 'left')}
              </th>
              <th class="px-4 py-3 text-right" aria-sort={ariaSort(modelSort.key === 'mean', modelSort.asc)}>
                {@render sortHeader('MEAN ρ', modelSort.key === 'mean', modelSort.asc, () => (modelSort = toggleSort(modelSort, 'mean', false)), 'right', false)}
              </th>
              <th class="px-4 py-3 text-right">
                <span class="uppercase tracking-wide">Δ</span>
              </th>
              <th class="px-4 py-3 text-right" aria-sort={ariaSort(modelSort.key === 'rank', modelSort.asc)}>
                {@render sortHeader('Mean rank', modelSort.key === 'rank', modelSort.asc, () => (modelSort = toggleSort(modelSort, 'rank', true)), 'right')}
              </th>
              {#each perPhenotype as p, i (p.phenotype)}
                <th class="px-4 py-3 text-right {i === 0 ? 'border-l border-gray-200' : ''}" aria-sort={ariaSort(modelSort.key === i, modelSort.asc)}>
                  {@render sortHeader(p.phenotype, modelSort.key === i, modelSort.asc, () => (modelSort = toggleSort(modelSort, i, false)), 'right')}
                  <span class="block font-normal normal-case tracking-normal text-gray-400">{p.units} {unitLabel}</span>
                </th>
              {/each}
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            {#each sortedModelRows as r (r.model)}
              <tr class="hover:bg-gray-50">
                <td class="px-6 py-2.5 whitespace-nowrap">
                  <span class="inline-block w-2.5 h-2.5 mr-2 rounded-sm border border-gray-500 align-[-1px]" style="background: {familyColor(r.model)}"></span>
                  <span class="font-medium text-gray-900">{r.model}</span>
                </td>
                <td class="px-4 py-2.5 text-right font-mono tabular-nums {r.mean === best.mean ? 'font-bold text-gray-900' : 'text-gray-700'}">{fmt(r.mean)}</td>
                <td class="px-4 py-2.5 text-right font-mono tabular-nums text-gray-500">{fmtDelta(r.delta)}</td>
                <td class="px-4 py-2.5 text-right font-mono tabular-nums {r.rank === best.rank ? 'font-bold text-gray-900' : 'text-gray-700'}">{fmt(r.rank, 2)}</td>
                {#each r.phenoMeans as v, i (i)}
                  <td class="px-4 py-2.5 text-right font-mono tabular-nums {i === 0 ? 'border-l border-gray-200' : ''} {v === best.pheno[i] ? 'font-bold text-gray-900' : 'text-gray-700'}">{fmt(v)}</td>
                {/each}
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
      <div class="mt-3 flex flex-wrap gap-4 text-xs text-gray-700">
        {#each FAMILIES as f (f.label)}
          <span class="flex items-center gap-1.5"><span class="inline-block w-3 h-3 rounded-sm border border-gray-500" style="background: {f.color}"></span>{f.label}</span>
        {/each}
        <span class="text-gray-500">Bold = best in column. Phenotype columns average only that phenotype's datasets scored by every model.</span>
      </div>
    {/if}
  {:else}
    <div class="mb-3 flex flex-wrap items-center gap-4">
      <div class="relative w-full max-w-xs">
        <svg xmlns="http://www.w3.org/2000/svg" class="absolute top-2.5 left-2 h-4 w-4 text-gray-500" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
        </svg>
        <input
          bind:value={searchQuery}
          oninput={() => (currentPage = 1)}
          class="w-full bg-white text-base text-gray-700 pl-8 rounded-md border border-gray-200 h-9 pr-8 shadow-sm focus:outline-none focus:ring-2 focus:ring-accent-600/30 focus:border-accent-600"
          type="text"
          placeholder="Filter by dataset or phenotype"
        />
      </div>
      <span class="text-sm text-gray-500">
        {filteredDatasets.length} datasets · Spearman ρ per dataset, bold = best model on that dataset
      </span>
    </div>
    <div class="overflow-x-auto bg-white border border-gray-200 rounded-lg shadow-sm">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-gray-200 text-xs font-semibold text-gray-500 align-bottom">
            <th class="sticky left-0 z-10 bg-white px-6 py-3 text-left" aria-sort={ariaSort(datasetSort.key === 'dataset', datasetSort.asc)}>
              {@render sortHeader('Dataset', datasetSort.key === 'dataset', datasetSort.asc, () => ((datasetSort = toggleSort(datasetSort, 'dataset', true)), (currentPage = 1)), 'left')}
            </th>
            {#each shownModels as m (m)}
              <th class="px-2 py-3 text-right" aria-sort={ariaSort(datasetSort.key === m, datasetSort.asc)}>
                {@render sortHeader(m, datasetSort.key === m, datasetSort.asc, () => ((datasetSort = toggleSort(datasetSort, m, false)), (currentPage = 1)), 'right', false)}
              </th>
            {/each}
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          {#each pageDatasets as d (d)}
            {@const scores = shownModels.map((m) => score(d, m))}
            {@const top = Math.max(...scores.map((v) => v ?? -Infinity))}
            {@const slug = metaOf.get(d)?.slug}
            <tr class="group hover:bg-gray-50">
              <td class="sticky left-0 bg-white group-hover:bg-gray-50 px-6 py-2 whitespace-nowrap">
                {#if slug}
                  <a href="{base}/datasets/{slug}" class="font-medium text-gray-900 hover:text-accent-700 hover:underline">{d}</a>
                {:else}
                  <span class="font-medium text-gray-900">{d}</span>
                {/if}
                {#if phenotype(d) !== 'unknown'}
                  <span class="ml-2 px-2 py-0.5 text-xs font-medium rounded-full bg-green-100 text-green-800">{phenotype(d)}</span>
                {/if}
              </td>
              {#each scores as v, i (i)}
                <td class="px-2 py-2 text-right font-mono tabular-nums {v !== undefined && v === top ? 'font-bold text-gray-900' : 'text-gray-700'}">{fmt(v)}</td>
              {/each}
            </tr>
          {:else}
            <tr><td colspan={shownModels.length + 1} class="px-6 py-10 text-center text-gray-500">No datasets match “{searchQuery}”.</td></tr>
          {/each}
        </tbody>
      </table>
    </div>
    <div class="mt-4">
      <Pagination bind:currentPage {totalPages} />
    </div>
  {/if}
</div>
