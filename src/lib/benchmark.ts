// Shared benchmark aggregation for the plots page and the table page.
import { criticalDifference, meanRanks } from "$lib/stats";
import { normalizationWeights, type DatasetKeys } from "$lib/normalize";
import type { MetricRow } from "$lib/stores/benchmark";
import type { Dataset } from "$lib/types/dataset";

export type DatasetMeta = { slug: string; phenotype?: string; protein?: string; paper?: string };

/** Manifest facts keyed by slug and by `name` (metrics.csv uses the name, which can differ from the slug). */
export function datasetMeta(datasets: Dataset[]): Map<string, DatasetMeta> {
  const str = (v: unknown) => (typeof v === "string" && v ? v : undefined);
  const out = new Map<string, DatasetMeta>();
  for (const d of datasets) {
    const meta = {
      slug: d.slug,
      phenotype: str((d.data.assays as Record<string, unknown>[] | undefined)?.[0]?.target_phenotype),
      protein: str((d.data.sequences as Record<string, unknown>[] | undefined)?.[0]?.uniprot_id),
      paper: str((d.data.publication as Record<string, unknown> | undefined)?.doi),
    };
    out.set(d.slug, meta);
    if (typeof d.data.name === "string") out.set(d.data.name, meta);
  }
  return out;
}

/** Datasets without a UniProt ID or DOI count as their own protein / paper. */
export const normalizationKeys =
  (meta: Map<string, DatasetMeta>) =>
  (dataset: string): DatasetKeys => ({
    protein: meta.get(dataset)?.protein ?? dataset,
    paper: meta.get(dataset)?.paper ?? dataset,
  });

/** model -> dataset -> Spearman for one game/split, skipping missing scores. */
export function scoresByModel(rows: MetricRow[], game: string, split: string): Map<string, Map<string, number>> {
  const byModel = new Map<string, Map<string, number>>();
  for (const row of rows) {
    if (row.game !== game || row.split !== split) continue;
    const score = parseFloat(row.spearman);
    if (!Number.isFinite(score)) continue;
    if (!byModel.has(row.model)) byModel.set(row.model, new Map());
    byModel.get(row.model)!.set(row.dataset, score);
  }
  return byModel;
}

export type ModelSummary = { model: string; values: number[]; mean: number; rank: number };

/**
 * Restrict to datasets passing `keep` that every model scored, collapse them into normalization
 * units, and return each model's unit scores, mean and Friedman mean rank (+ Nemenyi CD, alpha 0.05).
 */
export function summarize(
  byModel: Map<string, Map<string, number>>,
  keep: (dataset: string) => boolean,
  keys: (dataset: string) => DatasetKeys,
  byProtein: boolean,
  byPaper: boolean,
) {
  const scored = [...byModel.values()];
  const kept = new Set(scored.flatMap((m) => [...m.keys()]).filter(keep));
  const common = [...kept].filter((d) => scored.every((m) => m.has(d)));

  const units = new Map<string, [string, number][]>();
  for (const [d, { unit, weight }] of normalizationWeights(common, keys, byProtein, byPaper)) {
    if (!units.has(unit)) units.set(unit, []);
    units.get(unit)!.push([d, weight]);
  }
  const unitScores = (scores: Map<string, number>) =>
    [...units.values()].map((members) => members.reduce((s, [d, w]) => s + w * scores.get(d)!, 0));

  const perModel = scored.map(unitScores);
  // Friedman blocks = units (rows) x models (columns)
  const blocks = [...units.keys()].map((_, u) => perModel.map((v) => v[u]));
  const ranks = blocks.length ? meanRanks(blocks) : [];
  const cd = blocks.length ? criticalDifference(byModel.size, blocks.length) : 0;

  const models: ModelSummary[] = [...byModel.keys()].map((model, i) => ({
    model,
    values: perModel[i],
    mean: perModel[i].reduce((s, v) => s + v, 0) / perModel[i].length,
    rank: ranks[i],
  }));
  return { models, cd, common: common.length, total: kept.size, units: units.size };
}

// Size family: ESM2_8M_UR50D and ESM2_3B_UR50D both belong to ESM2_UR50D.
// SaProt variants also differ in structure source (AF2 / PDB / AFDB), so they collapse to one family.
export const sizeFamily = (model: string) =>
  /^SaProt/i.test(model) ? "SaProt" : model.replace(/_\d+(\.\d+)?[MB](?=_|$)/i, "");

/** Keep the first (best, given best-first input) member of each size family. */
export const bestSizes = <T extends { model: string }>(ordered: T[]) =>
  ordered.filter((m, _, all) => all.find((o) => sizeFamily(o.model) === sizeFamily(m.model)) === m);

// FAMILY_COLORS from the rank-correlation notebook, desaturated to 75% like sns.desaturate(c, 0.75).
// shortcut: model -> family by name prefix; move to model README frontmatter once more models land.
export const FAMILIES = [
  { label: "Sequence-structure", color: "#156591", match: /^(RSALOR|AIDO)/i },
  { label: "MSA", color: "#5CBAD0", match: /^(EVCOUPLINGS|EVmutation|GEMME|Site)/i },
  { label: "pLM", color: "#EDC77D", match: /^(ESM2|ESMC|ESM1|ProGen|Tranception)/i },
  { label: "Structure", color: "#E86861", match: /^(ProteinMPNN|MPNN|ESM-?IF|SaProt)/i },
];
export const familyColor = (model: string) => FAMILIES.find((f) => f.match.test(model))?.color ?? "#AAAAAA";
