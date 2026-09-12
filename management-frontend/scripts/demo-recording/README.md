# Demo recording

Produces the README's walkthrough video. Not a test suite — there's exactly one `test()` in
`full-demo.spec.ts` so Playwright writes exactly one video for the whole run.

## One-time setup

```sh
npm install
npx playwright install chromium
```

## Running it

Needs the database, backend, and frontend all running first (see repo root README's "Getting
Started"), with the frontend reachable at `http://localhost:3001` (the config's `baseURL`).

```sh
npm run demo:record                        # runs the walkthrough, writes test-results/**/video.webm
node scripts/demo-recording/collect-video.mjs   # copies it to ../assests/demo-videos/full-demo.webm
```

Each re-record overwrites `assests/demo-videos/full-demo.webm` as a new binary blob — there's no
git LFS configured in this repo, so re-record deliberately, not as a matter of course.
