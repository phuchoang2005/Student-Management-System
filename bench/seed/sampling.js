// bench/seed/sampling.js — the distribution shapes required by
// 04-workload-data-preparation.md §2: heavy-skew course popularity, a skewed per-student
// enrollment count (mean ~6, tail 15-20), and the student_code shuffle that keeps insertion order
// from matching sort order.

/**
 * Precompute a Zipf-like weight vector (weight(rank) = 1/rank^exponent) and its cumulative-sum
 * array, for O(log n) weighted sampling via binary search.
 */
export function buildZipfWeights(n, exponent) {
  const weights = new Float64Array(n);
  const cumulative = new Float64Array(n);
  let total = 0;
  for (let i = 0; i < n; i++) {
    const w = 1 / Math.pow(i + 1, exponent);
    weights[i] = w;
    total += w;
    cumulative[i] = total;
  }
  return { weights, cumulative, total };
}

function binarySearchFirstGTE(cumulative, target) {
  let lo = 0;
  let hi = cumulative.length - 1;
  while (lo < hi) {
    const mid = (lo + hi) >>> 1;
    if (cumulative[mid] >= target) {
      hi = mid;
    } else {
      lo = mid + 1;
    }
  }
  return lo;
}

/**
 * Per-student enrollment count: 90% uniform[lowRange], 10% uniform[highRange] -- mean ~5.8,
 * satisfying "mean ~6, tail 15-20" (04-workload-data-preparation.md §2).
 */
export function sampleEnrollmentCount(rng, mixture) {
  const { tailProbability, lowRange, highRange } = mixture;
  if (rng() < tailProbability) {
    const [min, max] = highRange;
    return min + Math.floor(rng() * (max - min + 1));
  }
  const [min, max] = lowRange;
  return min + Math.floor(rng() * (max - min + 1));
}

/**
 * Sample `k` distinct indices in [0, n) weighted by a precomputed Zipf cumulative-weight array,
 * without replacement.
 *
 * Fast path (the common case: k/n small, i.e. S2/S3 where a student picks a handful of courses
 * out of hundreds/thousands): reject-on-duplicate via a Set, O(k log n) expected.
 *
 * Fallback (k/n > 0.25 -- only triggers at S1, where the enrollment-count tail can require nearly
 * exhausting a 20-course catalog): a full weighted key-sort (Efraimidis-Spirakis) over every
 * index. Reject-sampling would otherwise degenerate into a coupon-collector problem as the
 * remaining unchosen pool shrinks toward zero.
 */
export function sampleWeightedDistinct(k, n, cumulative, total, rng) {
  const count = Math.min(k, n);
  if (count === n) {
    return Array.from({ length: n }, (_, i) => i);
  }

  if (count / n > 0.25) {
    const keyed = new Array(n);
    for (let i = 0; i < n; i++) {
      const weight = i === 0 ? cumulative[0] : cumulative[i] - cumulative[i - 1];
      keyed[i] = { index: i, key: Math.pow(rng(), 1 / weight) };
    }
    keyed.sort((a, b) => b.key - a.key);
    return keyed.slice(0, count).map((entry) => entry.index);
  }

  const chosen = new Set();
  while (chosen.size < count) {
    const r = rng() * total;
    chosen.add(binarySearchFirstGTE(cumulative, r));
  }
  return [...chosen];
}

/** Sample a single weighted index (with replacement) -- e.g. picking one owner for one book. */
export function sampleWeightedIndex(cumulative, total, rng) {
  return binarySearchFirstGTE(cumulative, rng() * total);
}

/** In-place Fisher-Yates shuffle, driven by the seeded RNG. */
export function fisherYatesShuffle(array, rng) {
  for (let i = array.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [array[i], array[j]] = [array[j], array[i]];
  }
  return array;
}

/**
 * Pick one token from a { common, medium, rare } tiered pool, weighted by tier so common tokens
 * dominate generated text and rare tokens surface in only a handful of rows. True per-term hit
 * counts are observed after loading (bench/seed/generate.js), not predicted here.
 */
export function pickWeightedToken(tieredPool, tierWeights, rng) {
  let total = 0;
  for (const tier of Object.keys(tieredPool)) {
    total += tieredPool[tier].length * (tierWeights[tier] ?? 1);
  }
  let r = rng() * total;
  for (const tier of Object.keys(tieredPool)) {
    const weight = tierWeights[tier] ?? 1;
    for (const token of tieredPool[tier]) {
      r -= weight;
      if (r <= 0) return token;
    }
  }
  const lastTier = Object.keys(tieredPool).pop();
  return tieredPool[lastTier][tieredPool[lastTier].length - 1];
}
