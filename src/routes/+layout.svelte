<script lang="ts">
  import "../app.css";
  import favicon from "$lib/assets/favicon.svg";
  import { modelsStore } from "$lib/stores/models";
  import { onMount } from "svelte";

  let { children } = $props();

  onMount(() => {
    modelsStore.load();
  });
</script>

<svelte:head>
  <link rel="icon" href={favicon} />
</svelte:head>

{@render children?.()}
