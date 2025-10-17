import { parseMarkdown } from '$lib/parseMarkdown.js';

export async function load({ fetch }) {
  // List of markdown files (hardcoded for now, could be dynamic)
  const modelSlugs = ['esm', 'pls'];

  const models = await Promise.all(
    modelSlugs.map(async (slug) => {
      try {
        const response = await fetch(`/models/${slug}/README.md`);
        if (!response.ok) {
          console.warn(`Failed to fetch ${slug}.md`);
          return null;
        }
        const content = await response.text();
        const parsed = parseMarkdown(content);
        return {
          ...parsed,
          slug
        };
      } catch (error) {
        console.warn(`Error loading ${slug}.md:`, error);
        return null;
      }
    })
  );

  return { models: models.filter(Boolean) };
}