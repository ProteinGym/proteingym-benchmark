// Dataset weighting for the benchmark's "normalize by protein / paper" options.

export type DatasetKeys = { protein: string; paper: string };

/**
 * Group datasets into units that each count once in the mean and the CD test.
 * Returns dataset -> { unit, weight }; weights within a unit sum to 1.
 *
 * - protein: one unit per protein, its datasets averaged.
 * - paper: one unit per paper, its datasets averaged.
 * - both: datasets sharing a protein or a paper are linked into one unit; inside it,
 *   repeat proteins are averaged within each paper, then the papers are averaged.
 */
export function normalizationWeights(
  datasets: string[],
  keys: (dataset: string) => DatasetKeys,
  byProtein: boolean,
  byPaper: boolean,
): Map<string, { unit: string; weight: number }> {
  const cluster = byProtein && byPaper ? linkedClusters(datasets, keys) : null;
  // Path from unit down to the dataset; each level splits its parent's weight equally.
  const path = (d: string): string[] => {
    const { protein, paper } = keys(d);
    if (cluster) return [cluster.get(d)!, paper, protein, d];
    if (byProtein) return [protein, d];
    if (byPaper) return [paper, d];
    return [d];
  };
  const paths = new Map(datasets.map((d) => [d, path(d)]));
  const children = new Map<string, Set<string>>();
  for (const p of paths.values())
    for (let i = 1; i < p.length; i++) {
      const parent = JSON.stringify(p.slice(0, i));
      if (!children.has(parent)) children.set(parent, new Set());
      children.get(parent)!.add(p[i]);
    }
  const out = new Map<string, { unit: string; weight: number }>();
  for (const [d, p] of paths) {
    let weight = 1;
    for (let i = 1; i < p.length; i++) weight /= children.get(JSON.stringify(p.slice(0, i)))!.size;
    out.set(d, { unit: p[0], weight });
  }
  return out;
}

// Union-find over datasets joined by a shared protein or paper; returns dataset -> cluster root.
function linkedClusters(datasets: string[], keys: (dataset: string) => DatasetKeys): Map<string, string> {
  const parent = new Map<string, string>();
  const find = (x: string): string => {
    if (!parent.has(x)) parent.set(x, x);
    while (parent.get(x) !== x) x = parent.get(x)!;
    return x;
  };
  const union = (a: string, b: string) => parent.set(find(a), find(b));
  for (const d of datasets) {
    const { protein, paper } = keys(d);
    union(`dataset:${d}`, `protein:${protein}`);
    union(`dataset:${d}`, `paper:${paper}`);
  }
  return new Map(datasets.map((d) => [d, find(`dataset:${d}`)]));
}
