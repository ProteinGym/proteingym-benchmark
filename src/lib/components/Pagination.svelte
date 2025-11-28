<script>
    let { currentPage = $bindable(1), totalPages } = $props();
</script>

<nav>
  <ul
    class="flex justify-center items-center space-x-2 text-gray-700 text-base max-w-7xl mx-auto"
  >
    <li>
      <button
        onclick={() => currentPage--}
        class="flex items-center rounded-lg px-2.5 py-1 hover:bg-gray-100 text-gray-400 hover:text-gray-700 {currentPage ===
        1
          ? 'pointer-events-none cursor-default'
          : ''}"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          stroke-width="1.5"
          stroke="currentColor"
          class="size-6"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            d="M15.75 19.5 8.25 12l7.5-7.5"
          />
        </svg>
        Previous
      </button>
    </li>
    <li>
      <span class="text-gray-400">Page {currentPage} of {totalPages}</span>
    </li>
    <li>
      <button
        onclick={() => currentPage++}
        class="text-base flex items-center rounded-lg px-2.5 py-1 hover:bg-gray-100 text-gray-400 hover:text-gray-700 {currentPage ===
        totalPages
          ? 'pointer-events-none cursor-default'
          : ''}"
      >
        Next
        <svg
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          stroke-width="1.5"
          stroke="currentColor"
          class="size-6"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            d="m8.25 4.5 7.5 7.5-7.5 7.5"
          />
        </svg>
      </button>
    </li>
  </ul>
</nav>