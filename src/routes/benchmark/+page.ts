import { redirect } from "@sveltejs/kit";
import { base } from "$app/paths";

// Old /benchmark links land on the zero-shot page.
export const load = () => redirect(308, `${base}/benchmark/zero-shot/`);
