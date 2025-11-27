import { writable } from "svelte/store";
import Papa from "papaparse";

export type MetricRow = {
  game: string;
  model: string;
  dataset: string;
  spearman: string;
};

type BenchmarkState = {
  rawData: MetricRow[];
  loading: boolean;
  error: string | null;
};

const metricsFile = import.meta.glob("/benchmark/metrics.csv", {
  query: "?raw",
  import: "default",
});

function createBenchmarkStore() {
  const { subscribe, update } = writable<BenchmarkState>({
    rawData: [],
    loading: false,
    error: null,
  });

  async function loadCSV() {
    update((state) => ({ ...state, loading: true, error: null }));

    try {
      const metricsEntries = Object.entries(metricsFile);

      if (metricsEntries.length === 0) {
        throw new Error("No metrics.csv file found");
      }

      const [, loader] = metricsEntries[0];
      const csvText = (await loader()) as string;

      Papa.parse<MetricRow>(csvText, {
        header: true,
        skipEmptyLines: true,
        complete: (results) => {
          update((state) => ({
            ...state,
            rawData: results.data,
            loading: false,
          }));
        },
        error: (err: Error) => {
          update((state) => ({
            ...state,
            error: err.message,
            loading: false,
          }));
        },
      });
    } catch (e) {
      update((state) => ({
        ...state,
        error: e instanceof Error ? e.message : "Unknown error occurred",
        loading: false,
      }));
    }
  }

  return {
    subscribe,
    load: loadCSV,
  };
}

export const benchmarkStore = createBenchmarkStore();
