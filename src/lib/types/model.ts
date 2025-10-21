export interface ParsedMarkdown {
  frontmatter: Record<string, string | string[]>;
  overview: string;
  content: string;
}

export interface Model extends ParsedMarkdown {
  slug: string;
}
