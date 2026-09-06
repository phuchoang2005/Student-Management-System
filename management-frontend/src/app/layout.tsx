import type { Metadata } from 'next';
import localFont from 'next/font/local';

import Providers from './providers';

export const metadata: Metadata = {
  title: 'Student Management',
  description:
    'Demo UI for the Student Management System — five roles, each scoped to the records its work needs.',
};

/**
 * Two faces, chosen for what this application actually is: a records office.
 *
 * **Public Sans** was drawn for the US Web Design System — for government forms and public records,
 * which is this app's genre rather than a resemblance to it. It satisfies §4's "single modern
 * sans-serif", and it ships true tabular figures, which is what makes a column of credits or
 * enrolment counts line up.
 *
 * **IBM Plex Mono** carries the business keys. Every record in this system is addressed by a
 * human-readable code — `S00123`, `CS100`, an ISBN — and staff read and speak those codes all day
 * (`lib/api/types.ts`). They are the app's real typographic material, so they get a face with some
 * record-keeping character rather than a grey chip in whatever monospace the OS offers.
 *
 * Only 400/500/600 are loaded, because §4 permits only those three and `theme/system.ts` aliases
 * every heavier name down into them — a weight that cannot be reached should not be downloaded.
 *
 * Wired with `next/font/local` against the `@fontsource` packages rather than `next/font/google`,
 * for the same reason the previous `geist` package was chosen: the woff2 files sit in
 * `node_modules`, so `next build` needs no network. The theme reads only the two CSS variables
 * below, so the token layer still never has to know which typeface it is.
 */
const sans = localFont({
  src: [
    { path: '../../node_modules/@fontsource/public-sans/files/public-sans-latin-400-normal.woff2', weight: '400', style: 'normal' },
    { path: '../../node_modules/@fontsource/public-sans/files/public-sans-latin-500-normal.woff2', weight: '500', style: 'normal' },
    { path: '../../node_modules/@fontsource/public-sans/files/public-sans-latin-600-normal.woff2', weight: '600', style: 'normal' },
  ],
  variable: '--font-sans',
  display: 'swap',
  // Public Sans runs slightly large on the body; matching the fallback's metrics keeps the swap
  // from reflowing a table mid-read.
  fallback: ['system-ui', 'sans-serif'],
});

const mono = localFont({
  src: [
    { path: '../../node_modules/@fontsource/ibm-plex-mono/files/ibm-plex-mono-latin-400-normal.woff2', weight: '400', style: 'normal' },
    { path: '../../node_modules/@fontsource/ibm-plex-mono/files/ibm-plex-mono-latin-500-normal.woff2', weight: '500', style: 'normal' },
  ],
  variable: '--font-mono',
  display: 'swap',
  fallback: ['ui-monospace', 'monospace'],
});

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${sans.variable} ${mono.variable}`} suppressHydrationWarning>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
