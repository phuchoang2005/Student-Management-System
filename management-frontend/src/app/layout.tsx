import type { Metadata } from 'next';

import Providers from './providers';

export const metadata: Metadata = {
  title: 'Student Management',
  description:
    'Demo UI for the Student Management System — five roles, each scoped to the records its work needs.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
