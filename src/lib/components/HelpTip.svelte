<script lang="ts">
  // (?) bubble with a frosted explanation on hover or keyboard focus; the text's first line is bold.
  let {
    id,
    label,
    text,
    align = 'left',
    large = false
  }: { id: string; label: string; text: string; align?: 'left' | 'right'; large?: boolean } = $props();
</script>

<span class="relative group inline-flex">
  <button
    type="button"
    aria-label={label}
    aria-describedby={id}
    class="flex items-center justify-center rounded-full border border-gray-300 font-semibold text-gray-500 hover:border-gray-500 hover:text-gray-900 {large
      ? 'h-5 w-5 text-xs'
      : 'h-4 w-4 text-[10px]'}"
  >?</button>
  <span
    {id}
    role="tooltip"
    class="pointer-events-none absolute top-full z-20 material mt-2 w-80 whitespace-pre-line rounded-lg border border-black/10 px-3 py-2 text-left text-xs font-medium normal-case leading-snug tracking-normal text-gray-900 shadow-xl first-line:font-semibold invisible opacity-0 scale-95 motion-reduce:scale-100 transition duration-150 ease-out group-hover:visible group-hover:opacity-100 group-hover:scale-100 group-has-[:focus-visible]:visible group-has-[:focus-visible]:opacity-100 group-has-[:focus-visible]:scale-100 {align ===
    'right'
      ? 'right-0 origin-top-right'
      : 'left-0 origin-top-left'}"
  >{text}</span>
</span>
