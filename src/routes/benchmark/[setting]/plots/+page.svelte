<script lang="ts">
  import { benchmarkStore } from '$lib/stores/benchmark';
  import { datasetsStore } from '$lib/stores/datasets';
  import { tooltip } from '$lib/tooltip';
  import NormalizeToggle from '$lib/components/NormalizeToggle.svelte';
  import HelpTip from '$lib/components/HelpTip.svelte';
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
  import { base } from '$app/paths';
  import { page } from '$app/stores';
  import { get } from 'svelte/store';
  import { benchmarkView } from '$lib/stores/benchmarkView';
  import { niceTicks } from '$lib/ticks';

  // Prototype: one horizontal violin per model on a shared metric axis,
  // restricted to datasets every model has a score for so means are comparable.

  const W = 960;
  const LABEL_W = 240;
  const MEAN_W = 180;
  const MEAN_X = W - MEAN_W + 14;
  const RANK_X = W - MEAN_W + 90;
  const ROW_H = 48;
  const TOP = 30;
  const BOTTOM = 40;

  let rawData = $derived($benchmarkStore.rawData);

  let splits = $derived([...new Set(rawData.map((r) => r.split))].sort());
  let split = $state('random');
  const saved = get(benchmarkView);
  let sizes = $state(saved.sizes);
  let sortBy = $state<'rank' | 'mean'>('mean');
  let byProtein = $state(saved.byProtein);
  let byPaper = $state(saved.byPaper);
  let metric = $state(saved.metric);
  $effect(() => benchmarkView.update((v) => ({ ...v, metric, byProtein, byPaper, sizes })));
  let metrics = $derived(metricColumns(rawData));
  let activeMetric = $derived(metrics.includes(metric) ? metric : metrics.includes('spearman') ? 'spearman' : metrics[0] ?? '');
  let lowerBetter = $derived(lowerIsBetter(activeMetric));
  let sign = $derived(lowerBetter ? -1 : 1); // flips "higher mean first" for lower-is-better metrics
  let symbol = $derived(metricSymbol(activeMetric));
  let unitLabel = $derived(
    byProtein && byPaper ? 'protein/publication groups' : byProtein ? 'proteins' : byPaper ? 'publications' : 'common datasets'
  );
  let setting = $derived(SETTINGS[$page.params.setting ?? ''] ?? 'zero_shot');
  let activeSplit = $derived(split || splits[0] || '');

  let metaOf = $derived(datasetMeta($datasetsStore));
  const phenotype = (dataset: string) => metaOf.get(dataset)?.phenotype ?? 'unknown';
  // dataset count per phenotype; empty until manifests load so bubbles don't flash as "unknown"
  let phenotypeCounts = $derived.by(() => {
    if (!metaOf.size) return [];
    // eslint-disable-next-line svelte/prefer-svelte-reactivity
    const counts = new Map<string, number>();
    const inView = rawData.filter((r) => r.game === setting && r.split === activeSplit);
    for (const d of new Set(inView.map((r) => r.dataset))) counts.set(phenotype(d), (counts.get(phenotype(d)) ?? 0) + 1);
    return [...counts].sort(([a], [b]) => (a === 'unknown' ? 1 : b === 'unknown' ? -1 : a.localeCompare(b)));
  });
  // store the deselected ones so every phenotype starts switched on
  let hiddenPhenotypes = $state<string[]>([]);
  // Arriving from a table sorted by a phenotype: show only that phenotype (once, when phenotypes load).
  let onlyPhenotype = saved.modelSort.key.startsWith('pheno:') ? saved.modelSort.key.slice(6) : '';
  $effect(() => {
    if (!onlyPhenotype || !phenotypeCounts.length) return;
    hiddenPhenotypes = phenotypeCounts.map(([p]) => p).filter((p) => p !== onlyPhenotype);
    onlyPhenotype = '';
  });
  const togglePhenotype = (p: string) =>
    (hiddenPhenotypes = hiddenPhenotypes.includes(p) ? hiddenPhenotypes.filter((h) => h !== p) : [...hiddenPhenotypes, p]);

  function quantile(sorted: number[], q: number): number {
    const pos = (sorted.length - 1) * q;
    const lo = Math.floor(pos);
    const hi = Math.ceil(pos);
    return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
  }

  let summary = $derived.by(() => {
    const base = summarize(
      scoresByModel(rawData, setting, activeSplit, activeMetric),
      (d) => !hiddenPhenotypes.includes(phenotype(d)),
      normalizationKeys(metaOf),
      byProtein,
      byPaper,
      lowerBetter
    );
    const models = base.models.map(({ model, rank, mean, values: unsorted }) => {
      const values = [...unsorted].sort((a, b) => a - b);
      const q1 = quantile(values, 0.25);
      const q3 = quantile(values, 0.75);
      const median = quantile(values, 0.5);
      const sd = Math.sqrt(values.reduce((s, v) => s + (v - mean) ** 2, 0) / values.length);
      // Gaussian KDE with Silverman's bandwidth, clipped to the data range like seaborn's cut=0.
      const h = 0.9 * Math.min(sd, (q3 - q1) / 1.34 || sd) * values.length ** -0.2 || 0.01;
      const kde = (v: number) => values.reduce((s, x) => s + Math.exp(-0.5 * ((v - x) / h) ** 2), 0);
      const lo = values[0];
      const hi = values[values.length - 1];
      const grid = Array.from({ length: 80 }, (_, k) => lo + ((hi - lo) * k) / 79);
      const peak = Math.max(...grid.map(kde));
      const width = (v: number) => kde(v) / peak;
      return {
        model,
        rank,
        mean,
        median,
        q1,
        q3,
        lo,
        hi,
        outline: grid.map((v) => [v, width(v)]),
        quartiles: [q1, median, q3].map((v) => [v, width(v)])
      };
    });
    models.sort((a, b) => a.rank - b.rank || sign * (b.mean - a.mean));
    return { ...base, models };
  });

  // Violin order: mean rank (matches the CD diagram) or mean score; the other breaks ties.
  let ordered = $derived(
    sortBy === 'rank'
      ? summary.models
      : [...summary.models].sort((a, b) => sign * (b.mean - a.mean) || a.rank - b.rank)
  );
  // ordered is best-first, so the first member of each family is its best size under the chosen sort.
  let shown = $derived(sizes === 'all' ? ordered : bestSizes(ordered));

  // Axis padded out to round ticks; about 10 ticks gives 0.1 steps for correlations.
  let domain = $derived.by(() => {
    const all = shown.flatMap((m) => [m.lo, m.hi]);
    if (!all.length) return [-1, 1];
    const lo = Math.min(...all);
    const hi = Math.max(...all);
    if (lo === hi) return [lo - 1, hi + 1];
    const t = niceTicks(lo, hi, 10);
    const step = t.length > 1 ? t[1] - t[0] : hi - lo;
    return [Math.floor(lo / step) * step, Math.ceil(hi / step) * step];
  });
  let ticks = $derived(niceTicks(domain[0], domain[1], 10));
  // same decimals on every tick label (0.0, 0.1, ... not 0, 0.1)
  let tickDigits = $derived(ticks.length > 1 ? Math.max(0, -Math.floor(Math.log10(ticks[1] - ticks[0]) + 1e-9)) : 1);
  let x = $derived((v: number) => LABEL_W + ((v - domain[0]) / (domain[1] - domain[0])) * (W - LABEL_W - MEAN_W));
  const HALF = ROW_H * 0.45;

  let violinPath = (outline: number[][], cy: number) =>
    'M ' +
    outline.map(([v, w]) => `${x(v)} ${cy - w * HALF}`).join(' L ') +
    ' L ' +
    [...outline].reverse().map(([v, w]) => `${x(v)} ${cy + w * HALF}`).join(' L ') +
    ' Z';
  let H = $derived(TOP + shown.length * ROW_H + BOTTOM);

  // Critical difference diagram (Demšar 2006): mirrored rank axis, best (rank 1) on the right.
  // labels hang off both axis ends; the viewBox is fitted to the drawing below
  const CD_L = 150;
  const CD_R = W - 150;
  const CD_AXIS = 50;
  let cdModels = $derived([...shown].sort((a, b) => a.rank - b.rank));
  // Axis spans the shown mean ranks ± 2, rounded, kept within the valid 1..k range.
  let rankLo = $derived(Math.max(1, Math.round(Math.min(...cdModels.map((m) => m.rank)) - 2)));
  let rankHi = $derived(Math.min(summary.models.length, Math.round(Math.max(...cdModels.map((m) => m.rank)) + 2)));
  let rankTicks = $derived(Array.from({ length: rankHi - rankLo + 1 }, (_, i) => rankLo + i));
  let rx = $derived((r: number) => CD_R - ((r - rankLo) / Math.max(1, rankHi - rankLo)) * (CD_R - CD_L));
  // Maximal runs of rank-adjacent models whose rank spread is within CD (not significantly different).
  let cliques = $derived.by(() => {
    const out: [number, number][] = [];
    let prevEnd = -1;
    for (let i = 0; i < cdModels.length; i++) {
      let j = i;
      while (j + 1 < cdModels.length && cdModels[j + 1].rank - cdModels[i].rank <= summary.cd) j++;
      if (j > i && j > prevEnd) out.push([i, j]);
      prevEnd = Math.max(prevEnd, j);
    }
    return out;
  });
  let cdLabelTop = $derived(CD_AXIS + 24 + cliques.length * 8);
  let cdHalf = $derived(Math.ceil(cdModels.length / 2));
  let cdH = $derived(cdLabelTop + cdHalf * 22 + 10);
  // Widen the viewBox to the drawn content so long model names never get clipped.
  let cdSvg = $state<SVGSVGElement>();
  let cdBox = $state<DOMRect>();
  $effect(() => {
    void [cdModels, cdH];
    cdBox = cdSvg?.getBBox();
  });
  // Leader lines stop just past the outermost dot on each side, not at the axis ends,
  // so labels tuck in under the axis (worse half on the left, better half on the right).
  let cdLabelX = $derived({
    left: Math.min(...cdModels.slice(cdHalf).map((m) => rx(m.rank))) - 20,
    right: Math.max(...cdModels.slice(0, cdHalf).map((m) => rx(m.rank))) + 20
  });
  let violinHelp = $derived(`Metric violins
Each violin is one model's ${metricName(activeMetric)} across the ${summary.units} ${unitLabel}, smoothed with a kernel density estimate. The long-dashed line is the median, the short-dashed lines are the quartiles, and ◆ is the mean.
Click Mean ${symbol} or Mean rank to sort the models. Hover a violin for its exact values.`);
  // "Tied" = within CD of each other, i.e. not significantly different (same rule as the bars)
  const tiedWith = (m: { model: string; rank: number }) =>
    cdModels.filter((o) => o.model !== m.model && Math.abs(o.rank - m.rank) <= summary.cd).map((o) => o.model);
  let cdLen = $derived((summary.cd / Math.max(1, rankHi - rankLo)) * (CD_R - CD_L));
  let cdHelp = $derived(`Critical difference (CD) diagram
Ranks models by ${metricName(activeMetric)} on every ${unitLabel.replace(/s$/, '')} (1 = best), then places each model at its mean rank across all ${summary.units} ${unitLabel}.
A Friedman test checks whether any model ranks consistently better; the Nemenyi post-hoc test then gives the critical difference: two models whose mean ranks differ by more than CD are significantly different (α = 0.05). Models joined by a bar are within CD of each other, so they are not significantly different.${
    shown.length < summary.models.length ? `\nRanks and CD come from all ${summary.models.length} models, including the hidden param sizes.` : ''
  }`);
  let cdViewBox = $derived.by(() => {
    if (!cdBox) return `0 0 ${W} ${cdH}`;
    return `${cdBox.x - 8} 0 ${cdBox.width + 16} ${cdH}`;
  });
</script>

<div class="p-6 max-w-7xl mx-auto">
  <!-- controls wrap on the left; the view switch is its own column so it never stretches a row -->
  <div class="mb-4 flex items-start gap-4 text-sm">
    <div class="flex flex-1 flex-wrap items-center gap-x-4 gap-y-2">
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
      <NormalizeToggle bind:byProtein bind:byPaper />
      {#if phenotypeCounts.length}
        <!-- own row under the other controls -->
        <div class="flex w-full flex-wrap items-center gap-2" role="group" aria-label="Filter datasets by phenotype">
          <span class="font-semibold text-gray-700">Phenotype</span>
          {#each phenotypeCounts as [p, count] (p)}
            {@const on = !hiddenPhenotypes.includes(p)}
            <button
              type="button"
              aria-pressed={on}
              onclick={() => togglePhenotype(p)}
              class="px-2.5 py-0.5 text-xs font-medium rounded-full border transition-colors {on
                ? 'bg-green-100 text-green-800 border-green-300 hover:bg-green-200'
                : 'bg-white text-gray-400 border-gray-300 line-through hover:text-gray-600'}"
            >
              {p} <span class="opacity-70">({count})</span>
            </button>
          {/each}
        </div>
      {/if}
    </div>
    <a href="{base}/benchmark/{$page.params.setting}" class="shrink-0 inline-flex items-center gap-1.5 rounded-md border border-gray-300 bg-white px-4 py-1.5 font-medium text-gray-700 shadow-sm hover:border-accent-600 hover:bg-accent-50 hover:text-accent-800 active:bg-accent-100 transition-[background-color,border-color,color,scale] duration-150 active:scale-[0.97] motion-reduce:active:scale-100">View as table <span aria-hidden="true">→</span></a>
  </div>
  {#if summary.common}
    <p class="mb-3 text-sm text-gray-500">
      {shown.length} of {summary.models.length} models · Scores are plotted using the datasets that are scored by every model ({summary.common}/{summary.total}){#if byProtein || byPaper}, weighted into {summary.units} {unitLabel}{/if}
    </p>
  {/if}

  {#if summary.models.length && summary.common}
    <div class="border border-gray-200 rounded-lg bg-white shadow-sm p-4">
      <h2 class="flex items-center gap-2 text-base font-semibold text-gray-900">
        Metric violins
        <HelpTip id="violin-help" label="How to read the violins" text={violinHelp} large />
      </h2>
      <div class="overflow-x-auto">
      <svg viewBox="0 0 {W} {H}" class="w-full min-w-[640px]" role="img" aria-label="{metricName(activeMetric)} distribution per model (violin plots)">
        {#each shown as _, i (i)}
          {#if i % 2}<rect x="0" y={TOP + i * ROW_H} width={W} height={ROW_H} class="fill-gray-50" />{/if}
        {/each}
        {#each ticks as t (t)}
          <line x1={x(t)} x2={x(t)} y1={TOP - 8} y2={H - BOTTOM} class="stroke-gray-300" stroke-dasharray={t === 0 ? undefined : '2 3'} />
          <text x={x(t)} y={H - BOTTOM + 18} text-anchor="middle" class="fill-gray-500 text-[11px]">{t.toFixed(tickDigits)}</text>
        {/each}
        {#if domain[0] <= 0 && domain[1] >= 0}
          <line x1={x(0)} x2={x(0)} y1={TOP - 8} y2={H - BOTTOM} stroke="black" stroke-width="1" />
        {/if}
        <text x={(LABEL_W + W - MEAN_W) / 2} y={H - 6} text-anchor="middle" class="fill-gray-700 text-xs font-semibold">{metricName(activeMetric)}</text>
        {#each [{ key: 'mean' as const, label: `Mean ${symbol}`, arrow: '▼', at: MEAN_X }, { key: 'rank' as const, label: 'Mean rank', arrow: '▲', at: RANK_X }] as col (col.key)}
          <text
            x={col.at}
            y={TOP - 12}
            role="button"
            tabindex="0"
            aria-pressed={sortBy === col.key}
            aria-label="Sort by {col.label}"
            onclick={() => (sortBy = col.key)}
            onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), (sortBy = col.key))}
            class="text-xs cursor-pointer select-none outline-none focus-visible:underline {sortBy === col.key
              ? 'fill-gray-900 font-bold'
              : 'fill-gray-500 font-semibold hover:fill-gray-900'}"
          >
            {col.label}{sortBy === col.key ? ` ${col.arrow}` : ''}
          </text>
        {/each}

        {#each shown as m, i (m.model)}
          {@const cy = TOP + i * ROW_H + ROW_H / 2}
          <g>
            <text x={LABEL_W - 12} y={cy + 4} text-anchor="end" class="fill-gray-800 text-xs">{m.model}</text>
            <!-- only the violin itself (and the marks drawn on it) triggers the hover tip -->
            <g use:tooltip={`${m.model}\nmean ${symbol} ${m.mean.toFixed(4)} · mean rank ${m.rank.toFixed(2)}\nmedian ${m.median.toFixed(4)} · Q1 ${m.q1.toFixed(4)} · Q3 ${m.q3.toFixed(4)}`}>
              <path d={violinPath(m.outline, cy)} fill={familyColor(m.model)} stroke="#333333" stroke-width="0.8" />
              {#each m.quartiles as [v, w], k (k)}
                <line x1={x(v)} x2={x(v)} y1={cy - w * HALF} y2={cy + w * HALF} stroke="#333333" stroke-width={k === 1 ? 1.5 : 1} stroke-dasharray={k === 1 ? '4 2' : '2 2'} />
              {/each}
              <path d="M {x(m.mean)} {cy - 5} l 5 5 l -5 5 l -5 -5 z" fill="#142142" />
            </g>
            <text x={RANK_X} y={cy + 4} class="fill-gray-900 text-xs font-mono">{m.rank.toFixed(2)}</text>
            <text x={MEAN_X} y={cy + 4} class="fill-gray-900 text-xs font-mono">{m.mean.toFixed(3)}</text>
          </g>
        {/each}
      </svg>
      </div>
      <div class="mt-2 flex flex-wrap gap-4 text-sm text-gray-700">
        {#each FAMILIES as f (f.label)}
          <span class="flex items-center gap-1.5"><span class="inline-block w-3 h-3 rounded-sm border border-gray-500" style="background: {f.color}"></span>{f.label}</span>
        {/each}
      </div>
    </div>

    <div class="mt-6 border border-gray-200 rounded-lg bg-white shadow-sm p-4">
      <h2 class="flex items-center gap-2 text-base font-semibold text-gray-900">
        Critical difference diagram
        <HelpTip id="cd-help" label="How the critical difference diagram works" text={cdHelp} large />
      </h2>
      <div class="overflow-x-auto">
      <svg bind:this={cdSvg} viewBox={cdViewBox} class="w-full min-w-[640px]" role="img" aria-label="Critical difference diagram of mean ranks">
        <!-- CD scale bar, top right -->
        <line x1={CD_R - cdLen} x2={CD_R} y1="14" y2="14" stroke="#142142" stroke-width="2" />
        <text x={CD_R - cdLen - 6} y="18" text-anchor="end" class="fill-gray-700 text-[11px]">CD = {summary.cd.toFixed(3)}</text>
        <line x1={CD_L} x2={CD_R} y1={CD_AXIS} y2={CD_AXIS} class="stroke-gray-700" />
        {#each rankTicks as r (r)}
          <line x1={rx(r)} x2={rx(r)} y1={CD_AXIS - 5} y2={CD_AXIS} class="stroke-gray-700" />
          <text x={rx(r)} y={CD_AXIS - 9} text-anchor="middle" class="fill-gray-600 text-[11px]">{r}</text>
        {/each}
        {#each cdModels as m, i (m.model)}
          {@const left = i >= cdHalf}
          {@const ly = cdLabelTop + (left ? cdModels.length - 1 - i : i) * 22}
          {@const lx = left ? cdLabelX.left : cdLabelX.right}
          <g use:tooltip={`${m.model}\nmean rank ${m.rank.toFixed(3)}\nTied with: ${tiedWith(m).join(', ') || 'none'}`}>
            <polyline points="{rx(m.rank)},{CD_AXIS} {rx(m.rank)},{ly} {lx},{ly}" fill="none" class="stroke-gray-500" />
            <circle cx={rx(m.rank)} cy={CD_AXIS} r="5" fill={familyColor(m.model)} stroke="black" stroke-width="0.5" />
            <text x={left ? lx - 6 : lx + 6} y={ly + 4} text-anchor={left ? 'end' : 'start'} class="fill-gray-800 text-[9.5px]">{m.model} <tspan class="fill-gray-500 font-mono">({m.rank.toFixed(2)})</tspan></text>
          </g>
        {/each}
        {#each cliques as [i, j], c (c)}
          <line x1={rx(cdModels[i].rank)} x2={rx(cdModels[j].rank)} y1={CD_AXIS + 12 + c * 8} y2={CD_AXIS + 12 + c * 8} stroke="#142142" stroke-width="4" stroke-linecap="butt" />
        {/each}
      </svg>
      </div>
    </div>
  {:else if !rawData.length}
    <p class="text-gray-500">Loading benchmark…</p>
  {:else}
    <p class="text-gray-500">No datasets are scored by every model for this setting and split.</p>
  {/if}
</div>
