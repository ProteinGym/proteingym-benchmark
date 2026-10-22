// Friedman mean ranks + Nemenyi critical difference, matching scikit-posthocs /
// statsmodels qsturng at infinite degrees of freedom.

// Abramowitz & Stegun 7.1.26, |error| < 1.5e-7.
function normalCdf(z: number): number {
  const t = 1 / (1 + 0.3275911 * (Math.abs(z) / Math.SQRT2));
  const poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))));
  const erf = 1 - poly * Math.exp(-(z * z) / 2);
  return z >= 0 ? (1 + erf) / 2 : (1 - erf) / 2;
}

/** P(range of k iid standard normals <= q): k ∫ φ(z) [Φ(z+q) − Φ(z)]^(k−1) dz, Simpson on [-8, 8]. */
export function studentizedRangeCdf(q: number, k: number): number {
  if (q <= 0) return 0;
  const steps = 400;
  const h = 16 / steps;
  let sum = 0;
  for (let i = 0; i <= steps; i++) {
    const z = -8 + i * h;
    const f = Math.exp(-(z * z) / 2) / Math.sqrt(2 * Math.PI) * (normalCdf(z + q) - normalCdf(z)) ** (k - 1);
    sum += f * (i === 0 || i === steps ? 1 : i % 2 ? 4 : 2);
  }
  return Math.min(1, (k * sum * h) / 3);
}

export function studentizedRangeQuantile(p: number, k: number): number {
  let lo = 0;
  let hi = 20;
  for (let i = 0; i < 60; i++) {
    const mid = (lo + hi) / 2;
    if (studentizedRangeCdf(mid, k) < p) lo = mid;
    else hi = mid;
  }
  return (lo + hi) / 2;
}

/** Rank each row (block) descending, 1 = best, ties averaged; return mean rank per column. */
export function meanRanks(blocks: number[][]): number[] {
  const k = blocks[0].length;
  const totals = new Array(k).fill(0);
  for (const row of blocks) {
    const order = row.map((v, i) => i).sort((a, b) => row[b] - row[a]);
    for (let i = 0; i < k; ) {
      let j = i;
      while (j + 1 < k && row[order[j + 1]] === row[order[i]]) j++;
      for (let t = i; t <= j; t++) totals[order[t]] += (i + j) / 2 + 1;
      i = j + 1;
    }
  }
  return totals.map((t) => t / blocks.length);
}

/** Nemenyi critical difference: two models differ at level alpha iff |ΔR| > CD. */
export function criticalDifference(k: number, n: number, alpha = 0.05): number {
  return (studentizedRangeQuantile(1 - alpha, k) / Math.SQRT2) * Math.sqrt((k * (k + 1)) / (6 * n));
}
