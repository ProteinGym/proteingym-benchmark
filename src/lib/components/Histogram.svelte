<script lang="ts">
  import { tooltip } from "$lib/tooltip";

  let { target, n, edges, counts }: { target: string; n: number; edges: number[]; counts: number[] } =
    $props();

  const W = 640, H = 300;
  const m = { top: 16, right: 16, bottom: 48, left: 60 };
  const pw = W - m.left - m.right, ph = H - m.top - m.bottom;

  // d3-style "nice" tick step so axes land on round numbers
  function ticks(lo: number, hi: number, count: number): number[] {
    const raw = (hi - lo) / count;
    const p = 10 ** Math.floor(Math.log10(raw));
    const e = raw / p;
    const step = (e >= 7.07 ? 10 : e >= 3.16 ? 5 : e >= 1.41 ? 2 : 1) * p;
    const out = [];
    for (let t = Math.ceil(lo / step) * step; t <= hi + step * 1e-9; t += step)
      out.push(Number(t.toPrecision(12)));
    return out;
  }

  const x0 = $derived(edges[0]);
  const x1 = $derived(edges[edges.length - 1]);
  // seaborn/matplotlib default: 5% x margin, y starts at 0
  const pad = $derived((x1 - x0) * 0.05);
  const xTicks = $derived(ticks(x0 - pad, x1 + pad, 7));
  const yTicks = $derived(ticks(0, Math.max(...counts), 5));
  const yMax = $derived(Math.max(Math.max(...counts) * 1.05, yTicks[yTicks.length - 1]));

  const sx = (v: number) => ((v - (x0 - pad)) / (x1 - x0 + 2 * pad)) * pw;
  const sy = (v: number) => ph - (v / yMax) * ph;
  const fmt = (v: number) => Number(v.toPrecision(4)).toLocaleString();
</script>

<svg
  viewBox="0 0 {W} {H}"
  class="w-full h-auto font-sans"
  role="img"
  aria-label="Histogram of {target}, n = {n.toLocaleString()}"
>
  <g transform="translate({m.left},{m.top})">
    {#each yTicks as t}
      <line x1="0" x2={pw} y1={sy(t)} y2={sy(t)} stroke="#e5e5e5" />
    {/each}

    {#each counts as c, i}
      <rect
        x={sx(edges[i])}
        y={sy(c)}
        width={Math.max(sx(edges[i + 1]) - sx(edges[i]), 0)}
        height={ph - sy(c)}
        fill="#a3a3a3"
        stroke="#000"
        stroke-width="0.75"
        use:tooltip={`${fmt(edges[i])} – ${fmt(edges[i + 1])}\n${c.toLocaleString()} variants`}
      />
    {/each}

    <line x1="0" x2="0" y1="0" y2={ph} stroke="#262626" stroke-width="1" />
    <line x1="0" x2={pw} y1={ph} y2={ph} stroke="#262626" stroke-width="1" />

    {#each yTicks as t}
      <line x1="-5" x2="0" y1={sy(t)} y2={sy(t)} stroke="#262626" />
      <text x="-9" y={sy(t)} text-anchor="end" dominant-baseline="middle" font-size="12" fill="#262626">
        {t.toLocaleString()}
      </text>
    {/each}
    {#each xTicks as t}
      <line x1={sx(t)} x2={sx(t)} y1={ph} y2={ph + 5} stroke="#262626" />
      <text x={sx(t)} y={ph + 19} text-anchor="middle" font-size="12" fill="#262626">
        {t.toLocaleString()}
      </text>
    {/each}

    <text x={pw / 2} y={ph + 42} text-anchor="middle" font-size="13" fill="#262626">{target}</text>
    <text transform="translate({-46},{ph / 2}) rotate(-90)" text-anchor="middle" font-size="13" fill="#262626">
      Count
    </text>
    <text x={pw - 6} y="6" text-anchor="end" dominant-baseline="hanging" font-size="13" fill="#262626">
      n = {n.toLocaleString()}
    </text>
  </g>
</svg>
