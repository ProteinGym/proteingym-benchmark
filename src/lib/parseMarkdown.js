export function parseMarkdown(content) {
  const frontmatterRegex = /^---\n([\s\S]*?)\n---\n([\s\S]*)$/;
  const contentMatch = content.match(frontmatterRegex);

  if (!contentMatch) return null;

  const frontmatter = {};
  const yamlContent = contentMatch[1];
  const markdownContent = contentMatch[2];

  const firstParagraphRegex = /^(?:#+[^\n]*\n+|\s)*(.*?)(?=\n\n|$)/s;
  const firstParagraphMatch = markdownContent.match(firstParagraphRegex)

  const firstParagraph = firstParagraphMatch[1]

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



  return { frontmatter, overview: firstParagraph, content: markdownContent };
}