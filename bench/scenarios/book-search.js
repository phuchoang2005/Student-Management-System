// bench/scenarios/book-search.js — BM-BK-001..004 (03-benchmark-scenarios.md §3).
//
// H1 on a larger table than students (BM-BK-001), the owner-filtered FK-index path as a control
// (BM-BK-002), and BM-BK-003's deliberate *non-hazard* check -- BookService memoizes owner lookups
// per page, so an unfiltered size=100 page should stay cheap even with many distinct owners
// (01-benchmark-strategy.md §3.1). Role: LIBRARIAN.

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../lib/config.js';
import { buildOptions } from '../lib/runner.js';
import { ensureLoggedIn } from '../lib/vuSession.js';
import { loadSearchTerms, termsWithHits, pickRandom } from '../lib/manifest.js';

const searchTerms = termsWithHits(loadSearchTerms().searchTermTable.book);

export const options = buildOptions('warmup', [
  { id: 'BM_BK_001', exec: 'bmBk001' },
  { id: 'BM_BK_002', exec: 'bmBk002' },
  { id: 'BM_BK_003', exec: 'bmBk003' },
  { id: 'BM_BK_004', exec: 'bmBk004' },
]);

// Owner codes and ISBNs discovered from a live sample during warm-up (per-VU, module-scoped --
// never counted in a measured scenario's metrics). A handful of unfiltered pages is enough: owner
// skew (04-workload-data-preparation.md §2) means heavy owners appear disproportionately often
// even in a partial sample.
let ownerCodes = [];
let isbnSample = [];

export function warmup() {
  ensureLoggedIn('LIBRARIAN');
  for (let i = 0; i < 3; i++) {
    const res = http.get(`${BASE_URL}/api/v1/books?page=${i}&size=100`, { tags: { name: 'warmup' } });
    if (res.status !== 200) continue;
    const content = JSON.parse(res.body).content || [];
    for (const book of content) {
      isbnSample.push(book.isbn);
      if (book.ownerStudentCode) ownerCodes.push(book.ownerStudentCode);
    }
  }
  http.get(`${BASE_URL}/api/v1/books?query=${encodeURIComponent(pickRandom(searchTerms))}&page=0&size=20`, {
    tags: { name: 'warmup' },
  });
}

export function bmBk001() {
  ensureLoggedIn('LIBRARIAN');
  const term = pickRandom(searchTerms);
  const res = http.get(`${BASE_URL}/api/v1/books?query=${encodeURIComponent(term)}&page=0&size=20`, {
    tags: { name: 'BM_BK_001' },
  });
  check(res, {
    'BM-BK-001 status 200': (r) => r.status === 200,
    'BM-BK-001 has content array': (r) => Array.isArray(JSON.parse(r.body).content),
  });
}

export function bmBk002() {
  ensureLoggedIn('LIBRARIAN');
  if (ownerCodes.length === 0) return;
  const owner = pickRandom(ownerCodes);
  const res = http.get(`${BASE_URL}/api/v1/books?ownerStudentCode=${owner}&page=0&size=20`, {
    tags: { name: 'BM_BK_002' },
  });
  check(res, {
    'BM-BK-002 status 200': (r) => r.status === 200,
    'BM-BK-002 rows all match owner': (r) =>
      (JSON.parse(r.body).content || []).every((b) => b.ownerStudentCode === owner),
  });
}

export function bmBk003() {
  ensureLoggedIn('LIBRARIAN');
  const res = http.get(`${BASE_URL}/api/v1/books?page=0&size=100`, { tags: { name: 'BM_BK_003' } });
  check(res, {
    'BM-BK-003 status 200': (r) => r.status === 200,
    'BM-BK-003 has content array': (r) => Array.isArray(JSON.parse(r.body).content),
  });
}

export function bmBk004() {
  ensureLoggedIn('LIBRARIAN');
  if (isbnSample.length === 0) return;
  const isbn = pickRandom(isbnSample);
  const res = http.get(`${BASE_URL}/api/v1/books/${isbn}`, { tags: { name: 'BM_BK_004' } });
  check(res, {
    'BM-BK-004 status 200': (r) => r.status === 200,
    'BM-BK-004 has isbn': (r) => JSON.parse(r.body).isbn === isbn,
    'BM-BK-004 owner field present (object or null)': (r) => 'owner' in JSON.parse(r.body),
  });
}
