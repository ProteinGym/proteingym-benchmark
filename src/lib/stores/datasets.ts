import { writable } from "svelte/store";
import type { Dataset } from "$lib/types/dataset";
import TOML from "smol-toml";
import { base } from "$app/paths";

function createDatasetsStore() {
  const { subscribe, set } = writable<Dataset[]>([]);

  async function loadDatasets() {
    const response = await fetch(`${base}/datasets-list.json`);
    const { slugs } = await response.json();

    const datasets = await Promise.all(
      slugs.map(async (slug: string) => {
        try {
          const response = await fetch(`${base}/datasets/${slug}.toml`);
          const tomlContent = await response.text();
          const data = TOML.parse(tomlContent) as Record<string, unknown>;

          return { slug, data };
        } catch (error) {
          console.warn(`Error loading ${slug}:`, error);
          return null;
        }
      }),
    );

    set(datasets.filter((dataset): dataset is Dataset => dataset !== null));
  }

  return {
    subscribe,
    load: loadDatasets,
  };
}

export const datasetsStore = createDatasetsStore();
