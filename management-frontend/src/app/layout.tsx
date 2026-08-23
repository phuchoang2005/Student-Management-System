import type { Metadata } from 'next';
import { GeistMono } from 'geist/font/mono';
import { GeistSans } from 'geist/font/sans';

import Providers from './providers';

export const metadata: Metadata = {
  title: 'Student Management',
  description:
    'Demo UI for the Student Management System — five roles, each scoped to the records its work needs.',
};

/**
 * The font is wired here rather than in `theme/system.ts` because `next/font` self-hosts the files
 * at build time and has to be reachable from a server component. The theme reads the two CSS
 * variables these classes define, so the token layer never has to know which typeface it is.
 *
 * Geist is one of §4's four permitted faces, and the `geist` package ships the woff2 inside itself —
 * so `next build` needs no network, unlike `next/font/google`.
 */
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="en"
      className={`${GeistSans.variable} ${GeistMono.variable}`}
      suppressHydrationWarning
    >
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
