<script lang="ts">
  import "../app.css";
  import favicon from "$lib/assets/favicon.svg";
  import { modelsStore } from "$lib/stores/models";
  import { benchmarkStore } from "$lib/stores/benchmark";
  import Header from "$lib/components/Header.svelte";
  import { onMount } from "svelte";

  let { children } = $props();

  onMount(() => {
    modelsStore.load();
    benchmarkStore.load();
  });
</script>

<svelte:head>
  <link rel="icon" href={favicon} />
</svelte:head>

<Header />

{@render children?.()}
