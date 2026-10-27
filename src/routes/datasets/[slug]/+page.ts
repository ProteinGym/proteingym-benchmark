import fs from "fs";

export const prerender = true;

export function entries() {
  const manifestsData = JSON.parse(fs.readFileSync("static/manifests.json", "utf-8"));
  const slugs = Object.keys(manifestsData.manifests);
  return slugs.map((slug: string) => ({ slug }));
}
