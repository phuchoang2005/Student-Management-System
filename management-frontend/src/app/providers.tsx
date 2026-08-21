'use client';

import { ChakraProvider } from '@chakra-ui/react';
import { ThemeProvider } from 'next-themes';
import type { ReactNode } from 'react';

import EmotionRegistry from './emotion-registry';
import { AuthProvider } from '@/lib/auth/AuthContext';
import system from '@/theme/system';

/**
 * Everything client-side that has to wrap the whole tree. Kept out of `layout.tsx` so the root
 * layout can stay a server component and only this file carries `'use client'`.
 *
 * `EmotionRegistry` sits outside `ChakraProvider` because Chakra's two `<Global>` elements have to
 * resolve to that cache — see the file for why the app cannot hydrate without it.
 *
 * `next-themes` with `attribute="class"` is what drives Chakra's `_dark` conditions; defaulting to
 * `system` means the app follows the OS the way the generated docs do.
 */
export default function Providers({ children }: { children: ReactNode }) {
  return (
    <EmotionRegistry>
      <ChakraProvider value={system}>
        <ThemeProvider attribute="class" defaultTheme="system" disableTransitionOnChange>
          <AuthProvider>{children}</AuthProvider>
        </ThemeProvider>
      </ChakraProvider>
    </EmotionRegistry>
  );
}
