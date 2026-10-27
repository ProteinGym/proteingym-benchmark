<script lang="ts">
  let { byProtein = $bindable(false), byPaper = $bindable(false) }: { byProtein?: boolean; byPaper?: boolean } =
    $props();

  const NORMALIZE = [
    {
      key: 'protein',
      label: 'Protein',
      help: 'Datasets of the same protein (same UniProt ID) are averaged first, so each protein counts once in the mean ρ and the CD test. E.g. 3 datasets of one protein each get weight 1/3.'
    },
    {
      key: 'paper',
      label: 'Publication',
      help: 'Datasets from the same publication (same DOI) are averaged first, so a publication with many datasets counts as much as a publication with one.'
    }
  ] as const;
  const BOTH_HELP =
    'With both on: datasets that share a protein or a publication form one group. Repeat proteins are averaged within each publication, then the publications are averaged, and each group counts once.';
</script>

<div class="flex items-center gap-2" role="group" aria-label="Normalize by">
  <span class="font-semibold text-gray-700">Normalize by</span>
  {#each NORMALIZE as n (n.key)}
    {@const on = n.key === 'protein' ? byProtein : byPaper}
    <span class="relative group">
      <button
        type="button"
        aria-pressed={on}
        aria-describedby="normalize-help-{n.key}"
        onclick={() => (n.key === 'protein' ? (byProtein = !byProtein) : (byPaper = !byPaper))}
        class="px-2.5 py-0.5 text-xs font-medium rounded-full border transition-colors {on
          ? 'bg-accent-600 text-white border-accent-600 hover:bg-accent-700'
          : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-100'}"
      >
        {n.label}
      </button>
      <span
        id="normalize-help-{n.key}"
        role="tooltip"
        class="pointer-events-none absolute left-0 top-full z-20 material mt-2 w-72 origin-top-left rounded-lg border border-black/10 px-3 py-2 text-xs font-medium leading-snug text-gray-900 shadow-xl invisible opacity-0 scale-95 motion-reduce:scale-100 transition duration-150 ease-out group-hover:visible group-hover:opacity-100 group-hover:scale-100 group-has-[:focus-visible]:visible group-has-[:focus-visible]:opacity-100 group-has-[:focus-visible]:scale-100"
      >
        {n.help}
        <span class="mt-1.5 block text-gray-700">{BOTH_HELP}</span>
      </span>
    </span>
  {/each}
</div>
