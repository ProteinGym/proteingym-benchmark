import { marked, Renderer } from "marked";

const ESCAPES: Record<string, string> = {
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  '"': "&quot;",
  "'": "&#39;",
};

const renderer = new Renderer();
// Model cards arrive by pull request, so raw HTML in one is untrusted. marked
// passes it through by default and ships no sanitizer; escaping it here keeps
// markdown working and stops `<img onerror=...>` reaching the DOM.
renderer.html = ({ text }) => text.replace(/[&<>"']/g, (c) => ESCAPES[c]);

/** Render a model card's markdown, with any raw HTML in it escaped. */
export const renderMarkdown = (source: string): string =>
  marked.parse(source, { renderer, async: false }) as string;
