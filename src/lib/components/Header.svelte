<script>
    import { page } from "$app/stores";
    import * as paths from "$app/paths";
    import logo from "$lib/assets/logo.png";
    import NavIcon from "$lib/components/NavIcon.svelte";

    const basePath = paths.base;
    let mobileMenuOpen = $state(false);
</script>

<header class="material sticky top-0 border-b border-black/5 z-30">
  <div class="flex items-center justify-between h-16 px-6 max-w-7xl mx-auto">
    <!-- Logo -->
    <a href="{basePath}/" class="cursor-pointer w-10 flex-shrink-0">
      <img src={logo} alt="proteingym-logo" />
    </a>

    <!-- Desktop Navigation -->
    <nav class="hidden md:flex items-center space-x-10 flex-1 ml-8 justify-end">
      <a href="{basePath}/model" aria-current={$page.url.pathname.startsWith(`${basePath}/model`) ? "page" : undefined} class="flex items-center space-x-2 text-gray-700 hover:text-gray-900 active:text-gray-500 cursor-pointer">
        <NavIcon name="models" active={$page.url.pathname.startsWith(`${basePath}/model`)} class="h-5 w-5" />
        <span class="{$page.url.pathname.startsWith(`${basePath}/model`) ? 'border-b-2 border-accent-600' : ''}">Models</span>
      </a>
      <a href="{basePath}/datasets" aria-current={$page.url.pathname.startsWith(`${basePath}/datasets`) ? "page" : undefined} class="flex items-center space-x-2 text-gray-700 hover:text-gray-900 active:text-gray-500 cursor-pointer">
        <NavIcon name="datasets" active={$page.url.pathname.startsWith(`${basePath}/datasets`)} class="h-5 w-5" />
        <span class="{$page.url.pathname.startsWith(`${basePath}/datasets`) ? 'border-b-2 border-accent-600' : ''}">Datasets</span>
      </a>
      <a href="{basePath}/benchmark/zero-shot" aria-current={$page.url.pathname.startsWith(`${basePath}/benchmark/zero-shot`) ? "page" : undefined} class="flex items-center space-x-2 text-gray-700 hover:text-gray-900 active:text-gray-500 cursor-pointer">
        <NavIcon name="zero-shot" active={$page.url.pathname.startsWith(`${basePath}/benchmark/zero-shot`)} class="h-5 w-5" />
        <span class="{$page.url.pathname.startsWith(`${basePath}/benchmark/zero-shot`) ? 'border-b-2 border-accent-600' : ''}">Zero-shot benchmark</span>
      </a>
      <a href="{basePath}/benchmark/supervised" aria-current={$page.url.pathname.startsWith(`${basePath}/benchmark/supervised`) ? "page" : undefined} class="flex items-center space-x-2 text-gray-700 hover:text-gray-900 active:text-gray-500 cursor-pointer">
        <NavIcon name="supervised" active={$page.url.pathname.startsWith(`${basePath}/benchmark/supervised`)} class="h-5 w-5" />
        <span class="{$page.url.pathname.startsWith(`${basePath}/benchmark/supervised`) ? 'border-b-2 border-accent-600' : ''}">Supervised benchmark</span>
      </a>
    </nav>

    <!-- Mobile Button -->
    <button
      class="md:hidden p-2"
      onclick={() => mobileMenuOpen = !mobileMenuOpen}
      aria-label="Toggle menu"
    >
      {#if mobileMenuOpen}
        <!-- X icon -->
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="h-6 w-6">
          <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      {:else}
        <!-- Hamburger icon -->
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="h-6 w-6">
          <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
        </svg>
      {/if}
    </button>
  </div>

  <!-- Mobile Navigation -->
  {#if mobileMenuOpen}
    <div class="md:hidden border-t border-black/5">
      <nav class="px-6 py-4 space-y-4">
        <a href="{basePath}/model" class="flex items-center space-x-2 text-gray-700 hover:text-gray-900 cursor-pointer" onclick={() => mobileMenuOpen = false}>
          <NavIcon name="models" active={$page.url.pathname.startsWith(`${basePath}/model`)} class="h-6 w-6" />
          <span>Models</span>
        </a>
        <a href="{basePath}/datasets" class="flex items-center space-x-2 text-gray-700 hover:text-gray-900 cursor-pointer" onclick={() => mobileMenuOpen = false}>
          <NavIcon name="datasets" active={$page.url.pathname.startsWith(`${basePath}/datasets`)} class="h-6 w-6" />
          <span>Datasets</span>
        </a>
        <a href="{basePath}/benchmark/zero-shot" class="flex items-center space-x-2 text-gray-700 hover:text-gray-900 cursor-pointer" onclick={() => mobileMenuOpen = false}>
          <NavIcon name="zero-shot" active={$page.url.pathname.startsWith(`${basePath}/benchmark/zero-shot`)} class="h-6 w-6" />
          <span>Zero-shot benchmark</span>
        </a>
        <a href="{basePath}/benchmark/supervised" class="flex items-center space-x-2 text-gray-700 hover:text-gray-900 cursor-pointer" onclick={() => mobileMenuOpen = false}>
          <NavIcon name="supervised" active={$page.url.pathname.startsWith(`${basePath}/benchmark/supervised`)} class="h-6 w-6" />
          <span>Supervised benchmark</span>
        </a>
      </nav>
    </div>
  {/if}
</header>
