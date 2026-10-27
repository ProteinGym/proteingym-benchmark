<script lang="ts">
  import Pagination from '$lib/components/Pagination.svelte';
  import NormalizeToggle from '$lib/components/NormalizeToggle.svelte';
  import HelpTip from '$lib/components/HelpTip.svelte';
  import { benchmarkStore } from '$lib/stores/benchmark';
  import { datasetsStore } from '$lib/stores/datasets';
  import {
    bestSizes,
    datasetMeta,
    FAMILIES,
    familyColor,
    lowerIsBetter,
    metricColumns,
    metricName,
    metricSymbol,
    normalizationKeys,
    scoresByModel,
    SETTINGS,
    summarize
  } from '$lib/benchmark';
  import { pairedDeltaCI } from '$lib/stats';
  import { tooltip } from '$lib/tooltip';
  import { base } from '$app/paths';
  import { page } from '$app/stores';
  import { get } from 'svelte/store';
  import { benchmarkView } from '$lib/stores/benchmarkView';

  // Leaderboard of per-model aggregates (same conventions as the violin plots),
  // with a per-dataset view underneath for the full score matrix.

  const datasetsPerPage = 50;

  let rawData = $derived($benchmarkStore.rawData);
  let splits = $derived([...new Set(rawData.map((r) => r.split))].sort());
  let split = $state('random');
  const saved = get(benchmarkView);
  let sizes = $state(saved.sizes);
  let byProtein = $state(saved.byProtein);
  let byPaper = $state(saved.byPaper);
  let metric = $state(saved.metric);
  let metrics = $derived(metricColumns(rawData));
  let activeMetric = $derived(metrics.includes(metric) ? metric : metrics.includes('spearman') ? 'spearman' : metrics[0] ?? '');
  let lowerBetter = $derived(lowerIsBetter(activeMetric));
  // +1 when higher scores are better; multiplying by it makes "larger" mean "better" for sorting
  let sign = $derived(lowerBetter ? -1 : 1);
  let symbol = $derived(metricSymbol(activeMetric));
  let view = $state<'models' | 'datasets'>('models');
  let setting = $derived(SETTINGS[$page.params.setting ?? ''] ?? 'zero_shot');
  let activeSplit = $derived(split || splits[0] || '');

  let metaOf = $derived(datasetMeta($datasetsStore));
  const phenotype = (dataset: string) => metaOf.get(dataset)?.phenotype ?? 'unknown';
  let byModel = $derived(scoresByModel(rawData, setting, activeSplit, activeMetric));
  let keys = $derived(normalizationKeys(metaOf));
  let unitLabel = $derived(
    byProtein && byPaper ? 'protein/publication groups' : byProtein ? 'proteins' : byPaper ? 'publications' : 'datasets'
  );

  let overall = $derived(summarize(byModel, () => true, keys, byProtein, byPaper, lowerBetter));
  // Named phenotypes present in this setting/split; "unknown" only counts toward the overall mean.
  let phenotypes = $derived(
    [...new Set([...byModel.values()].flatMap((m) => [...m.keys()]).map(phenotype))]
      .filter((p) => p !== 'unknown')
      .sort()
  );
  let perPhenotype = $derived(
    phenotypes.map((p) => {
      const s = summarize(byModel, (d) => phenotype(d) === p, keys, byProtein, byPaper, lowerBetter);
      return {
        phenotype: p,
        units: s.units,
        mean: new Map(s.models.map((m) => [m.model, s.units ? m.mean : undefined])),
        values: new Map(s.models.map((m) => [m.model, m.values]))
      };
    })
  );

  // ---- Models view ----
  // Δ: this model minus the column's best shown model, paired over the same units, with a bootstrap CI.
  type Delta = ReturnType<typeof pairedDeltaCI> | undefined; // undefined = the reference model or no units
  type ModelRow = {
    model: string;
    mean: number;
    rank: number;
    delta: Delta;
    phenoMeans: (number | undefined)[];
    phenoDeltas: Delta[];
  };
  let modelRows = $derived.by((): ModelRow[] => {
    if (!overall.common) return [];
    const byMean = [...overall.models].sort((a, b) => sign * (b.mean - a.mean) || a.rank - b.rank);
    const shown = sizes === 'all' ? byMean : bestSizes(byMean);
    const delta = (values: Map<string, number[]>, ref: string | undefined, model: string): Delta => {
      const a = values.get(model);
      const b = ref ? values.get(ref) : undefined;
      return a?.length && b && model !== ref ? pairedDeltaCI(a, b) : undefined;
    };
    const overallValues = new Map(overall.models.map((m) => [m.model, m.values]));
    const phenoRefs = perPhenotype.map(
      (p) => [...shown].sort((a, b) => goodness(p.mean.get(b.model)) - goodness(p.mean.get(a.model)))[0]?.model
    );
    return shown.map((m) => ({
      model: m.model,
      mean: m.mean,
      rank: m.rank,
      delta: delta(overallValues, shown[0]?.model, m.model),
      phenoMeans: perPhenotype.map((p) => p.mean.get(m.model)),
      phenoDeltas: perPhenotype.map((p, i) => delta(p.values, phenoRefs[i], m.model))
    }));
  });
  const notSignificant = (d: NonNullable<Delta>) => d.lo <= 0 && d.hi >= 0;
  // larger = better whatever the metric's direction; missing values sort last
  const goodness = (v: number | undefined) => (v === undefined ? -Infinity : sign * v);
  let deltaHelp = $derived(`Δ: average paired difference to the best model in the column
For every dataset (or protein / publication group when normalizing), we take this model's ${symbol} minus the column's best model's ${symbol}, then average. Both models are scored on the same datasets, so this equals the gap between their mean ${symbol}, but pairing lets us test it.
95% CI: we resample the datasets (or groups) with replacement 10,000 times and recompute the average. "(n.s.)" means the CI includes 0, so the gap to the best model is not significant. Hover a Δ value for its CI.
Each model is tested against the best separately, with no correction for comparing many models.`);

  // 'model' | 'mean' | 'rank' | `pheno:${name}`
  let modelSort = $state(saved.modelSort);
  $effect(() => benchmarkView.set({ metric, byProtein, byPaper, sizes, modelSort }));
  const phenoKey = (p: string) => `pheno:${p}`;
  const modelValue = (r: ModelRow, key: string) =>
    key === 'model'
      ? r.model
      : key === 'mean'
        ? goodness(r.mean)
        : key === 'rank'
          ? r.rank
          : goodness(r.phenoMeans[perPhenotype.findIndex((p) => phenoKey(p.phenotype) === key)]);
  let sortedModelRows = $derived(sortRows(modelRows, (r) => modelValue(r, modelSort.key), modelSort.asc));
  // Best value per numeric column, so it can be emphasised like a leaderboard.
  let best = $derived({
    mean: sign * Math.max(...modelRows.map((r) => goodness(r.mean))),
    rank: Math.min(...modelRows.map((r) => r.rank)),
    pheno: perPhenotype.map((_, i) => sign * Math.max(...modelRows.map((r) => goodness(r.phenoMeans[i]))))
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
      datasetSort.key === 'dataset' ? d : score(d, datasetSort.key) === undefined ? undefined : goodness(score(d, datasetSort.key));
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
  const fmtDelta = (v: number) => (Math.abs(v) < 5e-4 ? '0.000' : `${v < 0 ? '−' : '+'}${Math.abs(v).toFixed(3)}`);
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

{#snippet deltaCell(d: Delta)}
  <td class="px-2 py-2.5 text-right font-mono tabular-nums whitespace-nowrap text-gray-500">
    {#if d}
      <span use:tooltip={`95% CI\n${fmtDelta(d.lo)} to ${fmtDelta(d.hi)}`}>{fmtDelta(d.mean)}</span>
    {:else}
      —
    {/if}
    <!-- always reserve the width so the digits stay aligned -->
    <span class="inline-block w-9 text-left font-sans text-xs text-gray-400">{d && notSignificant(d) ? '(n.s.)' : ''}</span>
  </td>
{/snippet}

{#snippet deltaHeader(help = false)}
  <!-- trailing spacer matches the (n.s.) slot so Δ sits over the digits -->
  <span class="inline-flex items-center gap-1 pr-9">
    Δ
    {#if help}
      <HelpTip id="delta-help" label="What Δ and (n.s.) mean" text={deltaHelp} align="right" />
    {/if}
  </span>
{/snippet}

<div class="p-6 max-w-7xl mx-auto">
  <!-- controls wrap on the left; the view switch is its own column so it never stretches a row -->
  <div class="mb-4 flex items-start gap-4 text-sm">
    <div class="flex flex-1 flex-wrap items-center gap-x-4 gap-y-2">
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
        <span class="font-semibold text-gray-700">Metric</span>
        <select class="bg-white border border-gray-300 rounded-md px-2 py-1" bind:value={metric}>
          {#each metrics as m (m)}<option value={m}>{metricName(m)}</option>{/each}
        </select>
      </label>
      <!-- zero-shot models are only scored on the random split -->
      {#if setting !== 'zero_shot'}
        <label class="flex items-center gap-2">
          <span class="font-semibold text-gray-700">Split</span>
          <select class="bg-white border border-gray-300 rounded-md px-2 py-1" bind:value={split}>
            {#each splits as s (s)}<option value={s}>{s}</option>{/each}
          </select>
        </label>
      {/if}
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
    </div>
    <a href="{base}/benchmark/{$page.params.setting}/plots" class="shrink-0 inline-flex items-center gap-1.5 rounded-md border border-gray-300 bg-white px-4 py-1.5 font-medium text-gray-700 shadow-sm hover:border-accent-600 hover:bg-accent-50 hover:text-accent-800 active:bg-accent-100 transition-[background-color,border-color,color,scale] duration-150 active:scale-[0.97] motion-reduce:active:scale-100">View as plots <span aria-hidden="true">→</span></a>
  </div>

  {#if !rawData.length}
    <p class="text-gray-500">Loading benchmark…</p>
  {:else if view === 'models'}
    {#if !modelRows.length}
      <p class="text-gray-500">No datasets are scored by every model for this setting and split.</p>
    {:else}
      <p class="mb-3 text-sm text-gray-500">
        {modelRows.length} of {overall.models.length} models · Scores are plotted using the datasets that are scored by every model ({overall.common}/{overall.total}){#if byProtein || byPaper}, weighted into {overall.units} {unitLabel}{/if}
      </p>
      <div class="overflow-x-auto bg-white border border-gray-200 rounded-lg shadow-sm">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-200 text-xs font-semibold text-gray-500 align-bottom">
              <!-- w-0: the model column stays as wide as the longest name; spare width goes to the numbers -->
              <th class="w-0 pl-6 pr-2 py-3 text-left" aria-sort={ariaSort(modelSort.key === 'model', modelSort.asc)}>
                {@render sortHeader('Model', modelSort.key === 'model', modelSort.asc, () => (modelSort = toggleSort(modelSort, 'model', true)), 'left')}
              </th>
              <th class="px-2 py-3 text-right" aria-sort={ariaSort(modelSort.key === 'mean', modelSort.asc)}>
                {@render sortHeader(`MEAN ${symbol}`, modelSort.key === 'mean', modelSort.asc, () => (modelSort = toggleSort(modelSort, 'mean', false)), 'right', false)}
              </th>
              <th class="px-2 py-3 text-right">{@render deltaHeader(true)}</th>
              <!-- w-0: shrink to the digits; the header wraps to two lines -->
              <th class="w-0 px-2 py-3 text-right" aria-sort={ariaSort(modelSort.key === 'rank', modelSort.asc)}>
                {@render sortHeader('Mean rank', modelSort.key === 'rank', modelSort.asc, () => (modelSort = toggleSort(modelSort, 'rank', true)), 'right')}
              </th>
              {#each perPhenotype as p, i (p.phenotype)}
                <th class="px-2 py-3 text-right border-l border-gray-200" aria-sort={ariaSort(modelSort.key === phenoKey(p.phenotype), modelSort.asc)}>
                  {@render sortHeader(p.phenotype, modelSort.key === phenoKey(p.phenotype), modelSort.asc, () => (modelSort = toggleSort(modelSort, phenoKey(p.phenotype), false)), 'right')}
                  <span class="block font-normal normal-case tracking-normal text-gray-400">{p.units} {unitLabel}</span>
                </th>
                <th class="px-2 py-3 text-right">{@render deltaHeader()}</th>
              {/each}
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            {#each sortedModelRows as r (r.model)}
              <tr class="hover:bg-gray-50">
                <td class="pl-6 pr-2 py-2.5 whitespace-nowrap">
                  <span class="inline-block w-2.5 h-2.5 mr-2 rounded-sm border border-gray-500 align-[-1px]" style="background: {familyColor(r.model)}"></span>
                  <span class="font-medium text-gray-900">{r.model}</span>
                </td>
                <td class="px-2 py-2.5 text-right font-mono tabular-nums {r.mean === best.mean ? 'font-bold text-gray-900' : 'text-gray-700'}">{fmt(r.mean)}</td>
                {@render deltaCell(r.delta)}
                <td class="px-2 py-2.5 text-right font-mono tabular-nums {r.rank === best.rank ? 'font-bold text-gray-900' : 'text-gray-700'}">{fmt(r.rank, 2)}</td>
                {#each r.phenoMeans as v, i (i)}
                  <td class="px-2 py-2.5 text-right font-mono tabular-nums border-l border-gray-200 {v === best.pheno[i] ? 'font-bold text-gray-900' : 'text-gray-700'}">{fmt(v)}</td>
                  {@render deltaCell(r.phenoDeltas[i])}
                {/each}
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
      <div class="mt-3 flex flex-wrap gap-4 text-sm text-gray-700">
        {#each FAMILIES as f (f.label)}
          <span class="flex items-center gap-1.5"><span class="inline-block w-3 h-3 rounded-sm border border-gray-500" style="background: {f.color}"></span>{f.label}</span>
        {/each}
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
        {filteredDatasets.length} datasets · {metricName(activeMetric)} per dataset, bold = best model on that dataset
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
            {@const top = sign * Math.max(...scores.map(goodness))}
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
