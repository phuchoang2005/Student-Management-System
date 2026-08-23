'use client';

import { Button as ChakraButton } from '@chakra-ui/react';
import type { ComponentProps } from 'react';
import { forwardRef } from 'react';

/**
 * The one button.
 *
 * It exists for two reasons the theme alone could not fix. First, Chakra's default `colorPalette` is
 * the neutral ramp, so an unqualified `<Button>` came out grey — the primary had to be opted into on
 * every call site, and half of them forgot. Second, the old pages reached for `size="xs"` for row
 * actions, which §6 (40–44px) and §14 (44×44 touch) both rule out; funnelling every button through
 * one component is what stops that drifting back.
 *
 * `tone` replaces the raw `colorPalette` prop for the three cases this app actually has, so no page
 * has to know that "primary" means the `accent` ramp.
 */
export type ButtonTone = 'primary' | 'neutral' | 'danger';

const TONE_PALETTE: Record<ButtonTone, string> = {
  primary: 'accent',
  neutral: 'gray',
  danger: 'red',
};

export interface ButtonProps extends Omit<ComponentProps<typeof ChakraButton>, 'colorPalette'> {
  tone?: ButtonTone;
}

const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { tone = 'primary', variant = 'solid', size = 'md', ...rest },
  ref,
) {
  // A secondary action should not carry the primary hue: an outline button in the accent ramp reads
  // as a second primary, and §5 allows exactly one.
  const palette = TONE_PALETTE[variant === 'solid' ? tone : tone === 'primary' ? 'neutral' : tone];

  return <ChakraButton ref={ref} colorPalette={palette} variant={variant} size={size} {...rest} />;
});

export default Button;
