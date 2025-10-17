import { parseMarkdown } from '$lib/parseMarkdown.js';
import { base } from '$app/paths';

export async function load({ params, fetch }) {
  try {
    const response = await fetch(`${base}/models/${params.slug}/README.md`);

    if (!response.ok) {
      throw new Error(`Model ${params.slug} not found`);
    }

    const content = await response.text();
    const parsed = parseMarkdown(content);

    return {
      model: {
        ...parsed,
        slug: params.slug
      }
    };
  } catch (error) {
    throw new Error(`Model ${params.slug} not found`);
  }
}
