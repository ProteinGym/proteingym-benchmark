import { writable } from "svelte/store";
import type { Model } from "$lib/types/model";
import { parseMarkdown } from "$lib/utils/parseMarkdown";

const modelFiles = import.meta.glob("/models/*/README.md", {
  query: "?raw",
  import: "default",
});

function createModelsStore() {
  const { subscribe, set } = writable<Model[]>([]);

  async function loadModels() {
    const modelEntries = Object.entries(modelFiles);

    const models = await Promise.all(
      modelEntries.map(async ([path, loader]) => {
        try {
          // Example: Extract slug from path: '/models/esm/README.md' -> 'esm'
          // The list is after path.split("/"): ['', 'models', 'esm', 'README.md']
          const slug = path.split("/")[2];

          const content = (await loader()) as string;
          const parsed = parseMarkdown(content);

          if (!parsed) return null;

          return {
            ...parsed,
            slug,
          };
        } catch (error) {
          console.warn(`Error loading ${path}:`, error);
          return null;
        }
      }),
    );

    set(models.filter((model): model is Model => model !== null));
  }

  return {
    subscribe,
    load: loadModels,
  };
}

export const modelsStore = createModelsStore();
