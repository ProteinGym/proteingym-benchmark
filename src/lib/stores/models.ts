import { writable } from "svelte/store";
import type { Model, ModelFrontmatter } from "$lib/types/model";
import matter from "front-matter";

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

          const markdown = (await loader()) as string;
          const parsedMarkdown = matter<ModelFrontmatter>(markdown);

          const frontmatter = parsedMarkdown.attributes;
          const content = parsedMarkdown.body;

          // Extracts first paragraph of content, skipping any leading headings, whitespace, or GitHub alerts
          // Example: "# Title\nThis is the overview\nMore text" -> captures "This is the overview"
          // Example: "  \nFirst paragraph here\nSecond line" -> captures "First paragraph here"
          // Example: "> [!WARNING]\n> Alert text\nFirst paragraph" -> captures "First paragraph"
          const overviewRegex = /^(?:#+[^\n]*\n+|>\s*\[!.*?\][\s\S]*?(?=\n(?!>)|\n#|$)|\s)*(.*?)(?=\n|$)/s;
          const overviewMatch = content.match(overviewRegex);
          const overview = overviewMatch?.[1] || "";

          return {
            frontmatter,
            overview,
            content,
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
