import fs from "fs";

export const prerender = true;

export function entries() {
  const list = JSON.parse(fs.readFileSync("static/datasets-list.json", "utf-8"));
  return list.slugs.map((slug: string) => ({ slug }));
}
