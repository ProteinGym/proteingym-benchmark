<script lang="ts">
  import { benchmarkStore } from '$lib/stores/benchmark';
  import { criticalDifference, meanRanks } from '$lib/stats';
  import { base } from '$app/paths';

  // Prototype: one horizontal boxplot per model on a shared Spearman axis,
  // restricted to datasets every model has a score for so means are comparable.

  const W = 960;
  const LABEL_W = 240;
  const MEAN_W = 90;
  const ROW_H = 48;
  const TOP = 30;
  const BOTTOM = 40;

  let rawData = $derived($benchmarkStore.rawData);

  let games = $derived([...new Set(rawData.map((r) => r.game))].sort());
  let splits = $derived([...new Set(rawData.map((r) => r.split))].sort());
  let game = $state('zero_shot');
  let split = $state('');
  let sizes = $state<'all' | 'best'>('best');
  let activeGame = $derived(game || games[0] || '');
  let activeSplit = $derived(split || splits[0] || '');

  function quantile(sorted: number[], q: number): number {
    const pos = (sorted.length - 1) * q;
    const lo = Math.floor(pos);
    const hi = Math.ceil(pos);
    return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
  }

  let summary = $derived.by(() => {
    // eslint-disable-next-line svelte/prefer-svelte-reactivity
    const byModel = new Map<string, Map<string, number>>();
    for (const row of rawData) {
      if (row.game !== activeGame || row.split !== activeSplit) continue;
      const score = parseFloat(row.spearman);
      if (!Number.isFinite(score)) continue;
      if (!byModel.has(row.model)) byModel.set(row.model, new Map());
      byModel.get(row.model)!.set(row.dataset, score);
    }
    const scored = [...byModel.values()];
    const common = scored.length
      ? [...scored[0].keys()].filter((d) => scored.every((m) => m.has(d)))
      : [];
    const allDatasets = new Set(scored.flatMap((m) => [...m.keys()]));

    // Friedman blocks = common datasets (rows) x models (columns), alpha 0.05.
    const blocks = common.map((d) => [...byModel.values()].map((m) => m.get(d)!));
    const ranks = blocks.length ? meanRanks(blocks) : [];
    const cd = blocks.length ? criticalDifference(byModel.size, blocks.length) : 0;

    const models = [...byModel].map(([model, scores], idx) => {
      const values = common.map((d) => scores.get(d)!).sort((a, b) => a - b);
      const q1 = quantile(values, 0.25);
      const q3 = quantile(values, 0.75);
      const median = quantile(values, 0.5);
      const mean = values.reduce((s, v) => s + v, 0) / values.length;
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
        rank: ranks[idx],
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
    models.sort((a, b) => b.mean - a.mean);
    return { models, cd, common: common.length, total: allDatasets.size };
  });

  // Size family: ESM2_8M_UR50D and ESM2_3B_UR50D both belong to ESM2_UR50D.
  // SaProt variants also differ in structure source (AF2 / PDB / AFDB), so they collapse to one family.
  const sizeFamily = (model: string) =>
    /^SaProt/i.test(model) ? 'SaProt' : model.replace(/_\d+(\.\d+)?[MB](?=_|$)/i, '');
  // summary.models is sorted by mean, so the first member of each family is its best size.
  let shown = $derived(
    sizes === 'all'
      ? summary.models
      : summary.models.filter((m, _, all) => all.find((o) => sizeFamily(o.model) === sizeFamily(m.model)) === m)
  );

  let domain = $derived.by(() => {
    const all = shown.flatMap((m) => [m.lo, m.hi]);
    if (!all.length) return [-1, 1];
    return [Math.floor(Math.min(...all) * 10) / 10, Math.ceil(Math.max(...all) * 10) / 10];
  });
  let ticks = $derived.by(() => {
    const out: number[] = [];
    for (let t = domain[0]; t <= domain[1] + 1e-9; t += 0.1) out.push(Math.round(t * 10) / 10);
    return out;
  });
  let x = $derived((v: number) => LABEL_W + ((v - domain[0]) / (domain[1] - domain[0])) * (W - LABEL_W - MEAN_W));
  const HALF = ROW_H * 0.45;

  // FAMILY_COLORS from the rank-correlation notebook, desaturated to 75% like sns.desaturate(c, 0.75).
  // shortcut: model -> family by name prefix; move to model README frontmatter once more models land.
  const FAMILIES = [
    { label: 'Sequence-structure', color: '#156591', match: /^(RSALOR|AIDO)/i },
    { label: 'MSA (classic)', color: '#5CBAD0', match: /^(EVCOUPLINGS|EVmutation|GEMME|Site)/i },
    { label: 'PLM', color: '#EDC77D', match: /^(ESM2|ESMC|ESM1|ProGen|Tranception)/i },
    { label: 'Structure', color: '#E86861', match: /^(ProteinMPNN|MPNN|ESM-?IF|SaProt)/i }
  ];
  const familyColor = (model: string) => FAMILIES.find((f) => f.match.test(model))?.color ?? '#AAAAAA';
  let violinPath = (outline: number[][], cy: number) =>
    'M ' +
    outline.map(([v, w]) => `${x(v)} ${cy - w * HALF}`).join(' L ') +
    ' L ' +
    [...outline].reverse().map(([v, w]) => `${x(v)} ${cy + w * HALF}`).join(' L ') +
    ' Z';
  let H = $derived(TOP + shown.length * ROW_H + BOTTOM);

  // Critical difference diagram (Demšar 2006): mirrored rank axis, best (rank 1) on the right.
  const CD_L = 260;
  const CD_R = W - 260;
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
  let cdViewBox = $derived.by(() => {
    const x0 = Math.min(0, (cdBox?.x ?? 0) - 8);
    const x1 = Math.max(W, (cdBox ? cdBox.x + cdBox.width : 0) + 8);
    return `${x0} 0 ${x1 - x0} ${cdH}`;
  });
</script>

<div class="p-6 max-w-7xl mx-auto">
  <div class="mb-4 flex flex-wrap items-center gap-4 text-sm">
    <label class="flex items-center gap-2">
      <span class="font-semibold text-gray-700">Game</span>
      <select class="border border-gray-300 rounded-md px-2 py-1" bind:value={game}>
        {#each games as g (g)}<option value={g}>{g}</option>{/each}
      </select>
    </label>
    <label class="flex items-center gap-2">
      <span class="font-semibold text-gray-700">Split</span>
      <select class="border border-gray-300 rounded-md px-2 py-1" bind:value={split}>
        {#each splits as s (s)}<option value={s}>{s}</option>{/each}
      </select>
    </label>
    <label class="flex items-center gap-2">
      <span class="font-semibold text-gray-700">Model sizes</span>
      <select class="border border-gray-300 rounded-md px-2 py-1" bind:value={sizes}>
        <option value="all">Display all param sizes</option>
        <option value="best">Display best param size</option>
      </select>
    </label>
    <span class="text-gray-500">
      {shown.length} of {summary.models.length} models · {summary.common} of {summary.total} datasets scored by every model
    </span>
    <a href="{base}/benchmark-table" class="ml-auto text-blue-700 hover:underline">View as table →</a>
  </div>

  {#if summary.models.length && summary.common}
    <div class="border border-gray-300 rounded-lg bg-white shadow-sm p-4 overflow-x-auto">
      <svg viewBox="0 0 {W} {H}" class="w-full min-w-[640px]" role="img" aria-label="Spearman distribution per model (violin plots)">
        {#each shown as _, i (i)}
          {#if i % 2}<rect x="0" y={TOP + i * ROW_H} width={W} height={ROW_H} class="fill-gray-50" />{/if}
        {/each}
        {#each ticks as t (t)}
          <line x1={x(t)} x2={x(t)} y1={TOP - 8} y2={H - BOTTOM} class="stroke-gray-300" stroke-dasharray={t === 0 ? undefined : '2 3'} />
          <text x={x(t)} y={H - BOTTOM + 18} text-anchor="middle" class="fill-gray-500 text-[11px]">{t.toFixed(1)}</text>
        {/each}
        {#if domain[0] <= 0 && domain[1] >= 0}
          <line x1={x(0)} x2={x(0)} y1={TOP - 8} y2={H - BOTTOM} stroke="black" stroke-width="1" />
        {/if}
        <text x={(LABEL_W + W - MEAN_W) / 2} y={H - 6} text-anchor="middle" class="fill-gray-700 text-xs font-semibold">Spearman ρ</text>
        <text x={W - MEAN_W + 10} y={TOP - 12} class="fill-gray-700 text-xs font-semibold">Mean</text>

        {#each shown as m, i (m.model)}
          {@const cy = TOP + i * ROW_H + ROW_H / 2}
          <g>
            <title>{m.model}
mean {m.mean.toFixed(4)} · median {m.median.toFixed(4)}
Q1 {m.q1.toFixed(4)} · Q3 {m.q3.toFixed(4)}</title>
            <rect x="0" y={cy - ROW_H / 2} width={W} height={ROW_H} class="fill-transparent" />
            <text x={LABEL_W - 12} y={cy + 4} text-anchor="end" class="fill-gray-800 text-xs">{m.model}</text>
            <path d={violinPath(m.outline, cy)} fill={familyColor(m.model)} stroke="#333333" stroke-width="0.8" />
            {#each m.quartiles as [v, w], k (k)}
              <line x1={x(v)} x2={x(v)} y1={cy - w * HALF} y2={cy + w * HALF} stroke="#333333" stroke-width={k === 1 ? 1.5 : 1} stroke-dasharray={k === 1 ? '4 2' : '2 2'} />
            {/each}
            <path d="M {x(m.mean)} {cy - 5} l 5 5 l -5 5 l -5 -5 z" fill="#142142" />
            <text x={W - MEAN_W + 10} y={cy + 4} class="fill-gray-900 text-xs font-mono">{m.mean.toFixed(3)}</text>
          </g>
        {/each}
      </svg>
      <div class="mt-2 flex flex-wrap gap-4 text-xs text-gray-700">
        {#each FAMILIES as f (f.label)}
          <span class="flex items-center gap-1.5"><span class="inline-block w-3 h-3 rounded-sm border border-gray-500" style="background: {f.color}"></span>{f.label}</span>
        {/each}
      </div>
      <p class="mt-2 text-xs text-gray-500">
        Violin: kernel density over common datasets · dashed lines: Q1, median (bold), Q3 ·
        <span style="color: #142142">◆</span> mean. Sorted by mean Spearman. Hover a row for details.
      </p>
    </div>

    <div class="mt-6 border border-gray-300 rounded-lg bg-white shadow-sm p-4 overflow-x-auto">
      <h2 class="text-sm font-semibold text-gray-800">Critical difference diagram (Friedman + Nemenyi, α = 0.05)</h2>
      <svg bind:this={cdSvg} viewBox={cdViewBox} class="w-full min-w-[640px]" role="img" aria-label="Critical difference diagram of mean ranks">
        <line x1={CD_L} x2={CD_L + (summary.cd / Math.max(1, rankHi - rankLo)) * (CD_R - CD_L)} y1="14" y2="14" stroke="#142142" stroke-width="2" />
        <text x={CD_L} y="10" class="fill-gray-700 text-[11px]">CD = {summary.cd.toFixed(3)}</text>
        <line x1={CD_L} x2={CD_R} y1={CD_AXIS} y2={CD_AXIS} class="stroke-gray-700" />
        {#each rankTicks as r (r)}
          <line x1={rx(r)} x2={rx(r)} y1={CD_AXIS - 5} y2={CD_AXIS} class="stroke-gray-700" />
          <text x={rx(r)} y={CD_AXIS - 9} text-anchor="middle" class="fill-gray-600 text-[11px]">{r}</text>
        {/each}
        {#each cdModels as m, i (m.model)}
          {@const left = i >= cdHalf}
          {@const ly = cdLabelTop + (left ? cdModels.length - 1 - i : i) * 22}
          {@const lx = left ? CD_L - 20 : CD_R + 20}
          <g>
            <title>{m.model}: mean rank {m.rank.toFixed(3)}</title>
            <polyline points="{rx(m.rank)},{CD_AXIS} {rx(m.rank)},{ly} {lx},{ly}" fill="none" class="stroke-gray-500" />
            <circle cx={rx(m.rank)} cy={CD_AXIS} r="5" fill={familyColor(m.model)} stroke="black" stroke-width="0.5" />
            <text x={left ? lx - 6 : lx + 6} y={ly + 4} text-anchor={left ? 'end' : 'start'} class="fill-gray-800 text-xs">{m.model} <tspan class="fill-gray-500 font-mono">{m.rank.toFixed(2)}</tspan></text>
          </g>
        {/each}
        {#each cliques as [i, j], c (c)}
          <line x1={rx(cdModels[i].rank)} x2={rx(cdModels[j].rank)} y1={CD_AXIS + 12 + c * 8} y2={CD_AXIS + 12 + c * 8} stroke="#142142" stroke-width="4" stroke-linecap="butt" />
        {/each}
      </svg>
      <p class="mt-2 text-xs text-gray-500">
        Mean rank over the {summary.common} common datasets (1 = highest Spearman). Models joined by a bar are not
        significantly different (|ΔR| ≤ CD).
        {#if shown.length < summary.models.length}
          Ranks and CD come from the full {summary.models.length}-model test.
        {/if}
      </p>
    </div>
  {:else}
    <p class="text-gray-500">No datasets are scored by every model for this game and split.</p>
  {/if}
</div>
