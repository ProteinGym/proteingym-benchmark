import { SETTINGS } from "$lib/benchmark";

export const entries = () => Object.keys(SETTINGS).map((setting) => ({ setting }));
