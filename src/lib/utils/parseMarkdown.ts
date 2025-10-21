import type { ParsedMarkdown } from "$lib/types/model";

export function parseMarkdown(content: string): ParsedMarkdown | null {
  // Matches markdown with frontmatter in format:
  // ---
  // key: value
  // tags: [tag1, tag2]
  // ---
  // # Markdown content here
  const modelCardRegex = /^---\n([\s\S]*?)\n---\n([\s\S]*)$/;
  const modelCardMatch = content.match(modelCardRegex);

  if (!modelCardMatch) return null;

  const frontmatter: Record<string, string | string[]> = {};
  const yaml = modelCardMatch[1];
  const markdown = modelCardMatch[2];

  // Extracts first paragraph of content, skipping any leading headings or whitespace
  // Example: "# Title\nThis is the overview\nMore text" -> captures "This is the overview"
  // Example: "  \nFirst paragraph here\nSecond line" -> captures "First paragraph here"
  const overviewRegex = /^(?:#+[^\n]*\n+|\s)*(.*?)(?=\n|$)/s;
  const overviewMatch = markdown.match(overviewRegex);

  const overview = overviewMatch?.[1] || "";

  yaml.split("\n").forEach((line) => {
    const [key, ...valueParts] = line.split(":");

    if (key && valueParts.length) {
      let value: string | string[] = valueParts.join(":").trim();

      if (value.startsWith("[") && value.endsWith("]")) {
        value = value
          .slice(1, -1)
          .split(",")
          .map((v) => v.trim());
      }
      frontmatter[key.trim()] = value;
    }
  });

  return { frontmatter, overview: overview, content: markdown };
}
