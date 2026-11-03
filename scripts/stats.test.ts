// Run: node scripts/stats.test.ts
import assert from "node:assert/strict";
import { pairedDeltaCI } from "../src/lib/stats.ts";

// Mean of paired differences equals the difference of means.
const a = [0.5, 0.4, 0.6, 0.3, 0.55];
const b = [0.45, 0.42, 0.5, 0.31, 0.5];
const r = pairedDeltaCI(a, b);
assert.ok(Math.abs(r.mean - (0.47 - 0.436)) < 1e-12);
assert.ok(r.lo <= r.mean && r.mean <= r.hi);
assert.deepEqual(pairedDeltaCI(a, b), r, "seeded: same CI every call");

// A constant shift has a zero-width CI that excludes 0.
const shifted = pairedDeltaCI(a.map((v) => v + 0.1), a);
assert.ok(Math.abs(shifted.lo - 0.1) < 1e-12 && Math.abs(shifted.hi - 0.1) < 1e-12);

// Symmetric noise around 0 over many pairs: CI straddles 0 and is about ±1.96·sd/√n.
const n = 400;
const noise = Array.from({ length: n }, (_, i) => (i % 2 ? 0.1 : -0.1));
const z = pairedDeltaCI(noise, new Array(n).fill(0));
assert.ok(z.lo < 0 && z.hi > 0);
assert.ok(Math.abs(z.hi - 1.96 * 0.1 / Math.sqrt(n)) < 0.003, `hi ${z.hi}`);
console.log("stats: ok");
