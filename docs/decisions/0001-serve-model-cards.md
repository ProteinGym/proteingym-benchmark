# 1. Use SvelteKit and TailwindCSS for Model Card Website

Date: 2025-10-08
Status: Accepted

## Context and Problem Statement

We need a framework to build and deploy a model card website that automatically publishes when model cards are committed to the repository. The website must support listing all models, filtering by tags (defined in YAML frontmatter), and full-text search across both YAML metadata and optional Markdown content. The solution must integrate seamlessly with GitHub Pages for zero-cost hosting and provide a foundation for future extensions like advanced filtering (similar to Hugging Face), and benchmark result pages.

## Decision

We will use **SvelteKit** as the web framework and **TailwindCSS** for styling to build a static site that deploys to GitHub Pages via GitHub Actions CI/CD.

## Decision Drivers

- **Driver 1: Static Site Generation & GitHub Pages Compatibility** - The solution must generate static files that can be deployed to GitHub Pages without requiring a separate backend server. This ensures zero hosting costs and simple deployment through GitHub Actions.

- **Driver 2: Search Performance & User Experience** - With model cards stored as structured data (YAML + Markdown), the solution must provide fast client-side search across potentially hundreds of model cards. Performance considerations include whether to load all model cards as JSON in memory or implement progressive loading strategies.

- **Driver 3: Content Extensibility & Developer Experience** - The framework must easily accommodate future content types (e.g., benchmark pages) and advanced UI features (multi-faceted filtering, sorting, comparison views) without requiring major architectural changes. Developer experience matters for long-term maintenance.

- **Driver 4: Backend & Database Extensibility** - While the initial implementation is static, the framework should support future migration to dynamic features (real-time data, database integration) without requiring a complete rewrite. This ensures the architecture can evolve with changing requirements.

- **Driver 5: Deployment Flexibility** - The solution should not lock us into a single hosting platform. As requirements evolve (traffic scaling, geographic distribution, cost optimization), we need the ability to migrate between hosting providers or use multiple deployment targets without significant code changes.

## Considered Options

- **Option 1: GitHub Pages Themes (Jekyll)** - Use pre-built Jekyll themes designed for GitHub Pages with minimal customization.

- **Option 2: Streamlit + Streamlit Cloud** - Build an interactive Python-based application using Streamlit, deployed on Streamlit Cloud.

- **Option 3: React with Next.js** - Use React with Next.js for static site generation, styled with TailwindCSS.

- **Option 4: SvelteKit + TailwindCSS** - Use SvelteKit for static site generation, styled with TailwindCSS.

## Decision Matrix

| Option | Static Generation & GitHub Pages | Search Performance & UX | Content Extensibility & DX | Backend & Database Extensibility | Deployment Flexibility |
| ------ | -------------------------------- | ----------------------- | -------------------------- | -------------------------------- | ---------------------- |
| Jekyl | High | Low | Low | Low | Low |
| Streamlit | Low | Medium | Low | High | Low |
| React/Next.js | High | High | High | High | High |
| SvelteKit | High | High | High | High | High |

### Analysis of Decision Matrix

**Driver 1: Static Generation & GitHub Pages Compatibility**
- **Jekyll (High)**: Native GitHub Pages support, automatic builds, but limited to Ruby ecosystem and Liquid templating.
- **Streamlit (Low)**: Requires separate hosting on Streamlit Cloud or custom server; cannot deploy to GitHub Pages as a static site.
- **React/Next.js (High)**: Excellent static generation with `next export`, well-documented GitHub Pages deployment.
- **SvelteKit (High)**: First-class static adapter (`@sveltejs/adapter-static`), produces optimized static files for GitHub Pages.

**Driver 2: Search Performance & User Experience**
- **Jekyll (Low)**: Limited client-side interactivity; would require external search service or basic JavaScript implementation. No built-in state management for complex filtering.
- **Streamlit (Medium)**: Python-based search is powerful but requires server round-trips. Not suitable for real-time client-side search experience.
- **React/Next.js (High)**: Excellent client-side search libraries (Fuse.js, FlexSearch). Can load model cards as JSON and implement fast in-memory search. Rich ecosystem for complex UI patterns.
- **SvelteKit (High)**: Similar capabilities to React with better bundle size optimization. Can preload model cards as JSON during build time and provide instant client-side search with stores for state management.

**Driver 3: Content Extensibility & Developer Experience**
- **Jekyll (Low)**: Limited to Ruby plugins and Liquid templating. Adding data cards or complex filtering requires significant workarounds. Difficult to create interactive benchmark visualizations.
- **Streamlit (Low)**: Excellent for Python data scientists but poor for content-driven sites. Adding new page types requires Python backend changes. Not designed for markdown-heavy content sites.
- **React/Next.js (High)**: Massive ecosystem, extensive component libraries, excellent for content extensions. Can easily add data card pages with same infrastructure. TypeScript support and MDX integration for rich content.
- **SvelteKit (High)**: Modern file-based routing makes adding new page types trivial (`/models-cards/[id]` or `/benchmark`). Built-in markdown processing with mdsvex. Smaller bundle sizes mean better performance. Excellent TypeScript support and growing ecosystem.

**Driver 4: Backend & Database Extensibility**
- **Jekyll (Low)**: Static-only framework with no backend capabilities. Adding dynamic features requires a completely separate application stack. Migration path requires rebuilding frontend in a different framework.
- **Streamlit (High)**: Designed as a full-stack Python framework with native backend support. Excellent database integration (SQLAlchemy, direct DB connectors). However, the entire architecture is Python-based, making it less suitable for content-first static sites.
- **React/Next.js (High)**: Next.js provides excellent backend extensibility through API routes, server-side rendering, and serverless functions. Seamless database integration with Prisma, Drizzle, or any Node.js ORM. Can switch from static export to full SSR by changing configuration. Supports hybrid rendering (static + dynamic pages).
- **SvelteKit (High)**: Built-in backend capabilities through server routes and hooks for middleware. Database-agnostic with excellent support for all major ORMs (Prisma, Drizzle, Mongoose). Can migrate from static to full-stack by simply changing the adapter (`adapter-static` → `adapter-vercel` or `adapter-node`). Supports hybrid rendering strategies.

**Driver 5: Deployment Flexibility**
- **Jekyll**: GitHub Pages only (static)
- **Streamlit**: Streamlit Cloud or custom server required
- **React/Next.js**: GitHub Pages (static), Vercel, Netlify, AWS, self-hosted
- **SvelteKit**: GitHub Pages (static), Vercel, Netlify, Cloudflare Pages, AWS, self-hosted

### Why SvelteKit Won

While React/Next.js scored equally high across all drivers, **SvelteKit** was chosen for:
1. **Smaller bundle sizes**: 30-40% smaller JavaScript bundles mean faster page loads, crucial for content-heavy sites.
2. **Simpler state management**: Svelte stores are more intuitive than React Context/Redux for filter state.
3. **Better DX for content sites**: File-based routing and built-in markdown handling feel more natural.
4. **Modern architecture**: Leverages latest web standards, less boilerplate than React.
5. **Growing momentum**: Increasingly adopted for static documentation sites, good community support.
6. **Backend integration simplicity**: Server routes and form actions feel more integrated than Next.js API routes, with less configuration overhead.
7. **Adapter elegance**: SvelteKit's adapter system provides cleaner separation between application code and deployment target compared to Next.js configuration-based approach.

## Consequences

1. **Learning curve**: Team members unfamiliar with Svelte need to learn the framework.

2. **Search scalability**: Loading all model cards as JSON may become problematic beyond 500-1000 models.
   - *Mitigation*: Monitor bundle size. If exceeded, implement:
     - Lazy loading of Markdown content (load only metadata initially)
     - Pagination with client-side caching
     - Build-time search index generation with paginated results
     - Consider server-side search when migrating to full-stack deployment

3. **GitHub Pages limitations**: 1GB repo size limit, 1GB/month bandwidth soft limit for free tier.
   - *Mitigation*: Optimize images, implement aggressive caching headers, monitor usage. Migration path to Vercel/Netlify is straightforward if needed.