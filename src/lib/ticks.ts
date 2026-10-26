/** d3-style "nice" ticks so axes land on round numbers. */
export function niceTicks(lo: number, hi: number, count: number): number[] {
  const raw = (hi - lo) / count;
  const p = 10 ** Math.floor(Math.log10(raw));
  const e = raw / p;
  const step = (e >= 7.07 ? 10 : e >= 3.16 ? 5 : e >= 1.41 ? 2 : 1) * p;
  const out = [];
  // index * step, not repeated += step, so 0 lands on exactly 0 instead of -2.8e-17
  for (let i = Math.ceil(lo / step); i * step <= hi + step * 1e-9; i++) out.push(Number((i * step).toPrecision(12)));
  return out;
}
