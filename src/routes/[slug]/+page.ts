export const prerender = true;

const modelFiles = import.meta.glob("/models/*/README.md", {
  query: "?url",
  import: "default",
});

export function entries() {
  const slugs = Object.keys(modelFiles).map((path) => {
    return path.split("/")[2];
  });

  return slugs.map((slug) => ({ slug }));
}
