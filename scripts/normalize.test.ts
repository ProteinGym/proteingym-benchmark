// Run: node scripts/normalize.test.ts
import assert from "node:assert/strict";
import { normalizationWeights, type DatasetKeys } from "../src/lib/normalize.ts";

// p1 has proteins A (x2) and B; p2 has A; p3 has C (x3); p4 has D.
const meta: Record<string, DatasetKeys> = {
  a1: { protein: "A", paper: "p1" },
  a2: { protein: "A", paper: "p1" },
  b1: { protein: "B", paper: "p1" },
  a3: { protein: "A", paper: "p2" },
  c1: { protein: "C", paper: "p3" },
  c2: { protein: "C", paper: "p3" },
  c3: { protein: "C", paper: "p3" },
  d1: { protein: "D", paper: "p4" },
};
const ds = Object.keys(meta);
const run = (prot: boolean, paper: boolean) => normalizationWeights(ds, (d) => meta[d], prot, paper);
const w = (m: ReturnType<typeof run>, d: string) => m.get(d)!.weight;
const units = (m: ReturnType<typeof run>) => new Set([...m.values()].map((v) => v.unit)).size;

for (const [prot, paper] of [[false, false], [true, false], [false, true], [true, true]] as const) {
  const m = run(prot, paper);
  const sums = new Map<string, number>();
  for (const { unit, weight } of m.values()) sums.set(unit, (sums.get(unit) ?? 0) + weight);
  for (const s of sums.values()) assert.ok(Math.abs(s - 1) < 1e-12, "weights in a unit sum to 1");
}

assert.equal(units(run(false, false)), 8);
assert.equal(w(run(true, false), "a1"), 1 / 3); // A has 3 datasets
assert.equal(units(run(true, false)), 4);
assert.equal(w(run(false, true), "a1"), 1 / 3); // p1 has 3 datasets
assert.equal(units(run(false, true)), 4);

// both: p1 and p2 share protein A -> one unit {a1,a2,b1,a3}; p3 and p4 stay separate.
const both = run(true, true);
assert.equal(units(both), 3);
assert.equal(w(both, "a3"), 1 / 2); // half to p2
assert.equal(w(both, "b1"), 1 / 2 / 2); // p1 half, B half of p1
assert.equal(w(both, "a1"), 1 / 2 / 2 / 2); // p1 half, A half of p1, a1 half of A-in-p1
assert.equal(w(both, "c1"), 1 / 3);
assert.equal(w(both, "d1"), 1);
console.log("normalize: ok");
