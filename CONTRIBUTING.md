# Contributing

This file contains documentation about contributing to this project.

## Develop locally

After cloning the repository, you can start developing locally for both the benchmarking system and the static website to show the model cards and benchmarking results.

### Benchmarking

To install the local environment for the benchmarking system, you can do the following:

```shell
$ python -m venv .venv
$ source .venv/bin/activate
$ pip install -r requirements.txt
```

### Static website

The static website is built with [SvelteKit](https://svelte.dev/docs/kit/introduction) and displays model cards and benchmarking results. It uses TypeScript, [Tailwind CSS](https://tailwindcss.com/) for styling, and is configured as a static site adapter for deployment.

#### Installation

To install the development environment for the static website:

```shell
$ npm install
```

This will install all dependencies including Svelte, SvelteKit, TypeScript, Tailwind CSS, and development tools.

#### Development

Start the development server with hot module replacement:

```shell
$ npm run dev
```

The development server will start at `http://localhost:5173` by default. The site will automatically reload when you make changes to files in the `src/` directory.

#### Building

To create a production build of the static site:

```shell
$ npm run build
```

This generates static files in the `build/` directory that can be deployed to any static hosting service.

#### Preview

To preview the production build locally:

```shell
$ npm run preview
```

This serves the built static files so you can test the production build before deployment.

#### Code Quality

Before committing changes, ensure your code passes all quality checks:

```shell
# Type check with TypeScript and Svelte
$ npm run check

# Type check in watch mode
$ npm run check:watch

# Format code with Prettier (auto-fix)
$ npm run format

# Lint code with Prettier and ESLint
$ npm run lint
```

**Note:** Prettier is configured to only format files in the `src/` directory and specific config files. Python, YAML, and benchmark-related files are excluded. See [.prettierignore](.prettierignore) for details.

#### Project Structure

```
src/
├── routes/             # SvelteKit routes (pages)
│   ├── +page.svelte    # Homepage
│   ├── +layout.svelte  # Root layout
│   └── [slug]/         # Dynamic routes for model cards
├── lib/                # Shared utilities and components
│   ├── stores/         # Svelte stores for state management
│   ├── types/          # TypeScript type definitions
│   └── utils/          # Utility functions
├── app.html            # HTML template
└── app.css             # Global styles
```

#### Technology Stack

- **Framework:** SvelteKit 2.x with Svelte 5.x
- **Language:** TypeScript 5.x
- **Styling:** Tailwind CSS 4.x with Typography plugin
- **Build Tool:** Vite 7.x
- **Markdown:** [Marked](https://github.com/markedjs/marked) for parsing model card content
- **Code Quality:** ESLint, Prettier, svelte-check 