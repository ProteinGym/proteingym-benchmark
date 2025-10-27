export interface ModelFrontmatter {
  name: string;
  tags?: string[];
  hyper_parameters?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface ParsedMarkdown {
  frontmatter: ModelFrontmatter;
  overview: string;
  content: string;
}

export interface Model extends ParsedMarkdown {
  slug: string;
}
