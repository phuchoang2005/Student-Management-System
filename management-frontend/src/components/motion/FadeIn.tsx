'use client';

import { motion } from 'motion/react';
import type { ReactNode } from 'react';

import { RISE, useMotionDisabled, useZenTransition } from './motion-config';

/**
 * The default wrapper for page content: opacity 0→1 with a 4px rise, ease-out, ~200ms.
 *
 * "Animations should feel almost invisible" (§8) — the rise is what makes content feel *placed*
 * rather than switched on, and four pixels is the largest distance that still reads as stillness.
 */
export default function FadeIn({
  children,
  delay = 0,
  rise = true,
}: {
  children: ReactNode;
  delay?: number;
  /** Set false where a parent already owns the vertical rhythm, e.g. inside a table row. */
  rise?: boolean;
}) {
  const transition = useZenTransition();
  const disabled = useMotionDisabled();

  return (
    <motion.div
      initial={{ opacity: 0, y: rise && !disabled ? RISE : 0 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ ...transition, delay: disabled ? 0 : delay }}
    >
      {children}
    </motion.div>
  );
}
