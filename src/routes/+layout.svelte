<script lang="ts">
  import "../app.css";
  import favicon from "$lib/assets/favicon.svg";
  import { modelsStore } from "$lib/stores/models";
  import { datasetsStore } from "$lib/stores/datasets";
  import { benchmarkStore } from "$lib/stores/benchmark";
  import Header from "$lib/components/Header.svelte";
  import { onMount } from "svelte";

  let { children } = $props();

  onMount(async () => {
    try {
      await modelsStore.load();
    } catch (e) {
      console.error('Failed to load models:', e);
    }
    try {
      await datasetsStore.load();
    } catch (e) {
      console.error('Failed to load datasets:', e);
    }
    try {
      await benchmarkStore.load();
    } catch (e) {
      console.error('Failed to load benchmark:', e);
    }
  });
</script>

<svelte:head>
  <link rel="icon" href={favicon} />
</svelte:head>

<Header />

{@render children?.()}
