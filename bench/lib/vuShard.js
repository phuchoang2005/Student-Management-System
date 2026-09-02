// bench/lib/vuShard.js — deterministic per-VU partitioning for write scenarios (PM-035/036,
// Sprint 8; resharded per consuming scenario, PM-042). A write-shaped scenario discovers a pool of
// candidate targets (existing student/course codes, or freshly-generated new ones) during warm-up
// and must guarantee no two concurrently running VUs ever pick the same one -- two VUs updating
// the same student, or enrolling the same student in the same course, would manufacture contention
// (an optimistic-lock conflict, or an ALREADY_ENROLLED result) that has nothing to do with the
// endpoint's real cost. A fixed, deterministic partition by VU number is cheaper and simpler than
// any locking/reservation scheme.
//
// Calling convention (PM-042): `totalVus` must be the *consuming* scenario's own declared `vus`,
// not a shared upper bound sized to warm-up's VU count. A scenario file's warm-up typically runs
// with more VUs than any individual measured scenario that follows it, and k6 reuses low-numbered
// VU slots across sequentially-run scenarios in the same file -- sharding a pool once, during
// warm-up, against a divisor larger than the measured scenario's real VU count silently orphans
// whichever remainders that scenario's smaller VU population never reaches. The fix is for each
// exec function to shard its own pool lazily, on first use, against its own scenario's `vus` and
// its own live VU identity (`vu.idInTest` from `k6/execution`) -- see bench/scenarios/writes.js and
// cascade-delete.js for the pattern.

export function shardFor(pool, vuNumber, totalVus) {
  if (totalVus <= 0) return pool.slice();
  return pool.filter((_, i) => i % totalVus === (vuNumber - 1) % totalVus);
}

// A short, guaranteed-fits-in-20-chars unique suffix for freshly-generated write targets --
// StudentCode/CourseCode/Isbn are all capped at 20 chars with no format/regex constraint beyond
// non-blank (student/StudentCode.java, course/CourseCode.java, book/domain/Isbn.java). Base36-
// encodes (VU, iteration, current time) so two VUs, or two iterations of the same VU, never
// collide; call from inside an exec function, not module-top-level (__VU/__ITER are only set once
// the VU starts iterating).
export function uniqueCode(prefix) {
  const vu = (__VU || 0).toString(36);
  const iter = (__ITER || 0).toString(36);
  const time = Date.now().toString(36).slice(-6);
  return `${prefix}${vu}-${iter}-${time}`;
}
