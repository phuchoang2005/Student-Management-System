'use client';

import { AnimatePresence, motion } from 'motion/react';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';

import { RISE, useMotionDisabled, useZenTransition } from './motion-config';

/**
 * Cross-fades route content inside the app shell.
 *
 * `mode="wait"` rather than an overlap: two pages fading through each other is visual noise, and the
 * spec's whole position on motion is that it should support the interaction, not perform. The shell
 * itself — topbar, sidebar — never animates, so navigation feels like the page settling rather than
 * the application rebuilding.
 */
export default function PageTransition({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const transition = useZenTransition();
  const disabled = useMotionDisabled();

  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={pathname}
        initial={{ opacity: 0, y: disabled ? 0 : RISE }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0 }}
        transition={transition}
      >
        {children}
      </motion.div>
    </AnimatePresence>
  );
}
