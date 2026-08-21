import { createSystem, defaultConfig, defineConfig } from '@chakra-ui/react';

/**
 * A thin layer over Chakra's defaults: a single accent ramp and a slightly tighter body font stack.
 * Deliberately small — the demo's job is to show the backend's behaviour, not a design system, and
 * every token below is one Chakra already understands.
 */
const config = defineConfig({
  globalCss: {
    body: {
      bg: 'bg.subtle',
      color: 'fg',
    },
  },
  theme: {
    tokens: {
      fonts: {
        body: {
          value:
            '-apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif',
        },
        heading: {
          value:
            '-apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif',
        },
      },
    },
    semanticTokens: {
      colors: {
        accent: {
          solid: { value: '{colors.blue.600}' },
          contrast: { value: 'white' },
          fg: { value: '{colors.blue.700}' },
          muted: { value: '{colors.blue.100}' },
          subtle: { value: '{colors.blue.50}' },
          emphasized: { value: '{colors.blue.300}' },
          focusRing: { value: '{colors.blue.500}' },
        },
      },
    },
  },
});

export const system = createSystem(defaultConfig, config);

export default system;
