'use client';

import { motion } from 'motion/react';
import type { ReactNode } from 'react';

import { RISE, useMotionDisabled, useZenTransition } from './motion-config';

/** Beyond this many children the stagger stops accumulating, so a long list never becomes a wave. */
const MAX_STAGGERED = 10;
const STEP = 0.03;

/**
 * Entrance for a list of siblings — the demo-account rows on the login screen, card lists.
 *
 * The delay is computed per child and capped rather than handed to Framer's `staggerChildren`,
 * because `staggerChildren` scales with list length: at 25 items the last one would arrive most of a
 * second late, which is exactly the attention-grabbing motion §8 rules out.
 *
 * Table rows deliberately do *not* use this. A table is scanned, not read in sequence, and 30ms ×
 * 20 rows reads as a ripple; `DataTable` fades the table as one object instead.
 */
export default function StaggerItem({ children, index }: { children: ReactNode; index: number }) {
  const transition = useZenTransition();
  const disabled = useMotionDisabled();
  const delay = disabled ? 0 : Math.min(index, MAX_STAGGERED) * STEP;

  return (
    <motion.div
      initial={{ opacity: 0, y: disabled ? 0 : RISE }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ ...transition, delay }}
    >
      {children}
    </motion.div>
  );
}
