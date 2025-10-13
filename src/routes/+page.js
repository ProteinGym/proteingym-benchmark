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

function parseMarkdown(content) {
  const frontmatterRegex = /^---\n([\s\S]*?)\n---\n([\s\S]*)$/;
  const match = content.match(frontmatterRegex);
  
  if (!match) return null;
  
  const frontmatter = {};
  const yamlContent = match[1];
  const markdownContent = match[2];
  
  yamlContent.split('\n').forEach(line => {
    const [key, ...valueParts] = line.split(':');
    if (key && valueParts.length) {
      let value = valueParts.join(':').trim();
      if (value.startsWith('[') && value.endsWith(']')) {
        value = value.slice(1, -1).split(',').map(v => v.trim());
      }
      frontmatter[key.trim()] = value;
    }
  });
  
  return { frontmatter, content: markdownContent };
}