export interface ParsedMarkdown {
  frontmatter: Record<string, any>;
  overview: string;
  content: string;
}

export interface Model extends ParsedMarkdown {
  slug: string;
}
