'use client';

import { useReducedMotion, type Transition } from 'motion/react';

/**
 * The whole motion vocabulary of the app, in one file.
 *
 * §8 of the Zen spec is unusually prescriptive: 150–250ms, ease-out, and only fade / opacity /
 * colour / small elevation. No bounce, no elastic, no rotation, no zoom. Framer Motion makes all of
 * those one prop away, so the guard against them is that nothing animates without going through the
 * constants below — there are no inline `transition={{...}}` objects anywhere else in `src/`.
 */

/** §8's ease-out. Deliberately not a spring: a spring overshoots, and overshoot draws the eye. */
export const ZEN_EASE = [0, 0, 0.2, 1] as const;

export const DURATION = {
  /** Hover, focus, colour changes — the fastest thing the eye should still register. */
  fast: 0.15,
  /** Entrances and exits. The upper half of the spec's 150–250ms band. */
  moderate: 0.2,
} as const;

/** The vertical travel of an entrance. Four pixels: enough to read as motion, not as movement. */
export const RISE = 4;

/**
 * The transition every animated component uses.
 *
 * When the OS asks for reduced motion this returns a zero-duration transition rather than a shorter
 * one — §14 treats reduced motion as a requirement, and a fast animation is still an animation.
 */
export function useZenTransition(duration: number = DURATION.moderate): Transition {
  const reduced = useReducedMotion();
  return { duration: reduced ? 0 : duration, ease: ZEN_EASE };
}

/** True when the user has asked for no motion, for the cases where a variant has to change shape. */
export function useMotionDisabled(): boolean {
  return !!useReducedMotion();
}
