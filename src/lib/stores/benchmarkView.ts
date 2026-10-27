import { writable } from "svelte/store";

// Benchmark controls shared by the table and plot views, so switching between them keeps them.
export const benchmarkView = writable({
  metric: "spearman",
  byProtein: false,
  byPaper: false,
  sizes: "best" as "all" | "best",
  // Table sort. "pheno:<name>" sorts by that phenotype's column; the plots then show only that phenotype.
  modelSort: { key: "mean", asc: false },
});
