import { writable } from "svelte/store";
import type { Dataset } from "$lib/types/dataset";
import TOML from "smol-toml";
import { HUGGINGFACE_MANIFESTS_URL } from "$lib/config";

function createDatasetsStore() {
  const { subscribe, set } = writable<Dataset[]>([]);

  async function loadDatasets() {
    try {
      const response = await fetch(HUGGINGFACE_MANIFESTS_URL);
      const responseData = await response.json();

      const manifestsData = responseData.manifests as Record<string, string>;
      const commitHash = responseData.commit_hash as string;

      const datasets: Dataset[] = [];

      for (const [slug, tomlContent] of Object.entries(manifestsData)) {
        try {
          const data = TOML.parse(tomlContent) as Record<string, unknown>;
          datasets.push({ slug, data });
        } catch (error) {
          console.warn(`Error parsing TOML for ${slug}:`, error);
        }
      }

      set(datasets);
      datasetsCommitHash.set(commitHash);
    } catch (error) {
      console.error("Error loading datasets from HuggingFace:", error);
      set([]);
    }
  }

  return {
    subscribe,
    load: loadDatasets,
  };
}

export const datasetsStore = createDatasetsStore();
export const datasetsCommitHash = writable<string>("");
