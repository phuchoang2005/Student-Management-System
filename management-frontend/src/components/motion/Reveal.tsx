'use client';

import { AnimatePresence, motion } from 'motion/react';
import type { ReactNode } from 'react';

import { useMotionDisabled, useZenTransition } from './motion-config';

/**
 * Mount/unmount for something that changes the height of the page — an error banner, a one-time
 * password panel.
 *
 * Animating height as well as opacity is the point: these appear above content that has already
 * been read, and letting that content jump is more disruptive than the animation itself. `overflow`
 * is only hidden while the height is in flight, so a focus ring inside is never clipped at rest.
 */
export default function Reveal({ show, children }: { show: boolean; children: ReactNode }) {
  const transition = useZenTransition();
  const disabled = useMotionDisabled();

  return (
    <AnimatePresence initial={false}>
      {show ? (
        <motion.div
          initial={{ opacity: 0, height: disabled ? 'auto' : 0 }}
          animate={{ opacity: 1, height: 'auto' }}
          exit={{ opacity: 0, height: disabled ? 'auto' : 0 }}
          transition={transition}
          style={{ overflow: 'hidden' }}
        >
          {children}
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
