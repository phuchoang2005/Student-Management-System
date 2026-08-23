'use client';

import { IconButton, Skeleton } from '@chakra-ui/react';
import { Moon, Sun } from 'lucide-react';
import { useTheme } from 'next-themes';
import { useEffect, useState } from 'react';

import Tooltip from './Tooltip';

/**
 * Light/dark toggle.
 *
 * The Zen spec describes a single light palette; dark mode survives because the app already followed
 * the OS through `next-themes` and silently dropping that would be a regression for anyone whose
 * machine is dark. The dark palette is the same ink ramp inverted, not a second design.
 *
 * The mounted guard is not optional: `resolvedTheme` is undefined on the server, so rendering the
 * real icon before mount produces a hydration mismatch. A same-sized skeleton keeps the topbar from
 * reflowing on that first paint.
 */
export default function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);

  useEffect(() => setMounted(true), []);

  if (!mounted) return <Skeleton boxSize="10" borderRadius="l2" />;

  const dark = resolvedTheme === 'dark';

  return (
    <Tooltip content={dark ? 'Switch to light' : 'Switch to dark'}>
      <IconButton
        aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'}
        variant="ghost"
        colorPalette="gray"
        size="sm"
        onClick={() => setTheme(dark ? 'light' : 'dark')}
      >
        {dark ? <Sun strokeWidth={1.5} /> : <Moon strokeWidth={1.5} />}
      </IconButton>
    </Tooltip>
  );
}
