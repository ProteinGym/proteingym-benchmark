<script lang="ts">
  // Nav icons from pg_site_icons.svg, kept in that file's coordinates: the top row is
  // unselected, the bottom row selected, so each icon/state is just a 20x20 window onto it.
  // outline: unselected shape as a single currentColor stroke, no fills (landing page cards).
  let { name, active = false, outline = false, class: className = '' }: {
    name: 'models' | 'datasets' | 'zero-shot' | 'supervised';
    active?: boolean;
    outline?: boolean;
    class?: string;
  } = $props();

  const VIEWBOX = {
    models: ['69.6 54.5', '69.6 89.18'],
    datasets: ['114.86 53.9', '114.86 88.34'],
    'zero-shot': ['162 53.98', '162 88.34'],
    supervised: ['203.4 53.9', '203.4 88.34']
  };
</script>

<svg
  xmlns="http://www.w3.org/2000/svg"
  viewBox="{outline && name === 'zero-shot' ? '162 54' : VIEWBOX[name][active ? 1 : 0]} 20 20"
  overflow="visible"
  aria-hidden="true"
  class="{className} {outline ? 'line-only' : ''}"
>
  {#if name === 'models'}
    {#if active}
      <path class="round" d="M77.35,92.77v5.37l-1.07,1.78M82.86,100.16l-1.21-2.02v-5.37M82.72,92.77h-6.44" />
      <path class="round" d="M85.56,106.2h-12.35c-.3,0-.49-.34-.33-.6l3.4-5.68c1.14,1,1.6,1,2.21.93,1.14-.14.89-1.14,2.16-1.37,1.09-.19,1.57.29,2.21.68l3.15,5.25c.21.35-.04.79-.45.79Z" />
      <path d="M76.28,99.92c-.08-.06-.16-.14-.25-.22" />
      <path class="fill round" d="M86.01,105.36l-3.15-5.25c-.64-.39-1.12-.87-2.21-.68-1.27.23-1.02,1.23-2.16,1.37-.61.07-1.07.07-2.21-.93l-3.4,5.68c-.16.26.03.6.33.6h12.35c.41,0,.66-.44.45-.79Z" />
    {:else}
      <path class="round" d="M76.28,58.09h6.45M77.35,58.09v5.37l-4.48,7.46c-.16.26.03.6.34.6h12.34c.41,0,.66-.44.45-.79l-4.36-7.27v-5.37" />
      <path d="M74.71,67.86c1.34,1.22,3.05,1.21,3.69,1.12,1.14-.14,1.56-1.07,2.83-1.29,1.09-.19,2.7.25,3.33.64" />
    {/if}
  {:else if name === 'datasets'}
    {#if active}
      <path d="M130.67,93.01c0,1.23-2.6,2.23-5.81,2.23s-5.8-1-5.8-2.23,2.6-2.24,5.8-2.24,5.81,1,5.81,2.24Z" />
      <path class="fill" d="M130.67,98.37v5.36c0,1.23-2.6,2.23-5.81,2.23s-5.8-1-5.8-2.23v-5.36c0,1.23,2.6,2.23,5.8,2.23s5.81-1,5.81-2.23Z" />
      <line x1="130.67" y1="98.37" x2="130.67" y2="93.01" />
      <line x1="119.06" y1="98.37" x2="119.06" y2="93.01" />
    {:else}
      <ellipse cx="124.86" cy="58.57" rx="5.81" ry="2.23" />
      <path d="M119.06,58.57v10.72c0,1.23,2.6,2.23,5.81,2.23s5.81-1,5.81-2.23h0v-10.72" />
      <path d="M119.06,63.93c0,1.23,2.6,2.23,5.81,2.23s5.81-1,5.81-2.23" />
    {/if}
  {:else if name === 'zero-shot'}
    {#if active}
      <path class="fill" d="M169.6,91.81v4.47h-5.96v-3.59c0-.49.4-.88.88-.88h5.08Z" />
      <rect class="fill" x="169.6" y="96.28" width="10.73" height="4.17" />
      <path class="fill" d="M169.6,100.45v4.47h-5.08c-.48,0-.88-.39-.88-.87v-3.6h5.96Z" />
      <rect class="fill" x="163.64" y="96.28" width="5.96" height="4.17" />
      <path class="fill" d="M180.33,100.45v3.6c0,.48-.39.87-.87.87h-9.86v-4.47h10.73Z" />
      <path class="fill" d="M180.33,96.28h-10.73v-4.47h9.86c.48,0,.87.39.87.88v3.59Z" />
      <path class="paper" d="M171.6,93.9v4.47h-5.96v-3.59c0-.49.4-.88.88-.88h5.08Z" />
      <rect class="paper" x="171.6" y="98.36" width="10.73" height="4.17" />
      <path class="paper" d="M171.6,102.54v4.47h-5.08c-.48,0-.88-.39-.88-.87v-3.6h5.96Z" />
      <rect class="paper" x="165.64" y="98.36" width="5.96" height="4.17" />
      <path class="paper" d="M182.33,102.54v3.6c0,.48-.39.87-.87.87h-9.86v-4.47h10.73Z" />
      <path class="paper" d="M182.33,98.36h-10.73v-4.47h9.86c.48,0,.87.39.87.88v3.59Z" />
    {:else}
      <path class="paper" d="M169.6,57.45v4.47h-5.96v-3.59c0-.49.4-.88.88-.88h5.08Z" />
      <rect class="paper" x="169.6" y="61.92" width="10.73" height="4.17" />
      <path class="paper" d="M169.6,66.09v4.47h-5.08c-.48,0-.88-.39-.88-.87v-3.6h5.96Z" />
      <rect class="paper" x="163.64" y="61.92" width="5.96" height="4.17" />
      <path class="paper" d="M180.33,66.09v3.6c0,.48-.39.87-.87.87h-9.86v-4.47h10.73Z" />
      <path class="paper" d="M180.33,61.92h-10.73v-4.47h9.86c.48,0,.87.39.87.88v3.59Z" />
      {#if !outline}
      <path class="paper" d="M171.6,59.53v4.47h-5.96v-3.59c0-.49.4-.88.88-.88h5.08Z" />
      <rect class="paper" x="171.6" y="64" width="10.73" height="4.17" />
      <path class="paper" d="M171.6,68.17v4.47h-5.08c-.48,0-.88-.39-.88-.87v-3.6h5.96Z" />
      <rect class="paper" x="165.64" y="64" width="5.96" height="4.17" />
      <path class="paper" d="M182.33,68.17v3.6c0,.48-.39.87-.87.87h-9.86v-4.47h10.73Z" />
      <path class="paper" d="M182.33,64h-10.73v-4.47h9.86c.48,0,.87.39.87.88v3.59Z" />
      {/if}
    {/if}
  {:else if active}
    <path class="accent" d="M204.22,103.9c.79.24,2.29,0,3.65-.5,3.15-1.18,4.6-5.07,7.46-7.71,1.36-1.26,2-2.21,7.23-2.37" />
    <polyline class="round" points="204.22 91.81 204.22 104.92 222.55 104.92" />
    <path class="accent round" d="M215.32,95.7c1.36-1.26,2-2.21,7.23-2.37" />
  {:else}
    <polyline class="round" points="204.22 57.37 204.22 70.48 222.55 70.48" />
    <path class="round" d="M204.22,69.36c.79.24,2.29,0,3.65-.5,3.15-1.18,4.6-5.07,7.46-7.71,1.36-1.26,2-2.21,7.23-2.37" />
  {/if}
</svg>

<style>
  svg :global(*) {
    fill: none;
    stroke: #6d6e71;
    stroke-width: 0.76px;
    stroke-miterlimit: 1.17;
  }
  svg :global(.thin) {
    stroke-width: 0.64px;
  }
  svg :global(.round) {
    stroke-linejoin: round;
    stroke-linecap: round;
  }
  svg :global(.paper) {
    fill: #fff;
  }
  svg :global(.fill) {
    fill: var(--color-accent-600);
  }
  svg :global(.accent) {
    stroke: var(--color-accent-600);
    stroke-width: 2px;
    stroke-linejoin: round;
  }
  svg.line-only :global(*) {
    fill: none;
    stroke: currentColor;
    stroke-width: 1.25px;
    stroke-linejoin: round;
    stroke-linecap: round;
  }
</style>
