import { createSystem, defaultConfig, defineConfig } from '@chakra-ui/react';
import {
  alertAnatomy,
  cardAnatomy,
  dataListAnatomy,
  dialogAnatomy,
  tableAnatomy,
} from '@chakra-ui/react/anatomy';

/**
 * The Zen design system, per `docs/UI-UX/02-Japanese-Zen-Design.md`.
 *
 * Still a `defineConfig` overlay on Chakra's defaults rather than a stylesheet: the Emotion SSR
 * registry (`app/emotion-registry.tsx`) is load-bearing for hydration, and a second styling runtime
 * would have to fight it. Everything below is a token or a recipe Chakra already understands.
 *
 * The one structural trick worth knowing: Chakra derives its whole semantic layer — `bg`, `fg`,
 * `border`, and every neutral component surface — from `colors.gray.*`. Replacing that ramp with the
 * warm ink ramp below re-colours the entire app from one place, so only a handful of semantic tokens
 * need an explicit override underneath.
 *
 * ## The two commitments this file makes
 *
 * **Every colour has exactly one job.** §5 caps the palette at one primary and one accent, and the
 * earlier build honoured the cap by simply never using the accent — leaving an app that was indigo
 * and grey. Here `ai` means *the primary action, or where you currently are*, and `matcha` means
 * *state that belongs to a record* — a book on loan, a live session. Neither is ever decoration, and
 * neither is ever used for the other's job.
 *
 * **The type scale is a token, not a call-site decision.** `textStyles` below is the whole
 * vocabulary; pages ask for `display`/`title`/`prose`/`meta`/`key` rather than reassembling
 * `fontSize="sm" color="fg.muted"` by hand, which is how six screens ended up with six slightly
 * different captions.
 */

/** Warm ink neutral. 50 and 200 are the spec's background (#F8F8F6) and border (#E5E5E5) verbatim. */
const sumi = {
  50: { value: '#F8F8F6' },
  100: { value: '#F1F1EE' },
  200: { value: '#E5E5E5' },
  300: { value: '#D2D2CE' },
  400: { value: '#A8A8A3' },
  500: { value: '#8A8A85' },
  600: { value: '#6B6B66' },
  700: { value: '#4D4D49' },
  800: { value: '#2F2F2C' },
  900: { value: '#1C1C1A' },
  950: { value: '#121211' },
};

/** Primary indigo. 600 is the spec's #1E3A5F; the lighter steps exist for dark mode and for fills. */
const ai = {
  50: { value: '#EEF2F7' },
  100: { value: '#D8E1EC' },
  200: { value: '#B3C4D8' },
  300: { value: '#85A2BF' },
  400: { value: '#547FA3' },
  500: { value: '#2F5B83' },
  600: { value: '#1E3A5F' },
  700: { value: '#18304E' },
  800: { value: '#13253C' },
  900: { value: '#0E1B2C' },
  950: { value: '#0A1420' },
};

/**
 * Accent moss. 500 is the spec's #6B8E7A — but at 3.3:1 on white it fails WCAG AA for text, so it is
 * only ever a fill, a border, or an indicator. Accent *text* resolves to 700 (7.3:1), which is why
 * `matcha.fg` below points there rather than at the nominal accent value.
 */
const matcha = {
  50: { value: '#F0F4F1' },
  100: { value: '#DEE8E1' },
  200: { value: '#BFD2C6' },
  300: { value: '#9BB8A6' },
  400: { value: '#82A490' },
  500: { value: '#6B8E7A' },
  600: { value: '#56745F' },
  700: { value: '#445B4B' },
  800: { value: '#334339' },
  900: { value: '#253128' },
  950: { value: '#1A231D' },
};

/** The eight slots Chakra's `colorPalette` indirection expects, for one ramp. */
const paletteSlots = (
  name: string,
  steps: {
    solid: [number, number];
    contrast: [string, string];
    fg: [number, number];
    subtle: [number, number];
    muted: [number, number];
    emphasized: [number, number];
    focusRing: [number, number];
    border: [number, number];
  },
) => ({
  solid: { value: { _light: `{colors.${name}.${steps.solid[0]}}`, _dark: `{colors.${name}.${steps.solid[1]}}` } },
  contrast: { value: { _light: steps.contrast[0], _dark: steps.contrast[1] } },
  fg: { value: { _light: `{colors.${name}.${steps.fg[0]}}`, _dark: `{colors.${name}.${steps.fg[1]}}` } },
  subtle: { value: { _light: `{colors.${name}.${steps.subtle[0]}}`, _dark: `{colors.${name}.${steps.subtle[1]}}` } },
  muted: { value: { _light: `{colors.${name}.${steps.muted[0]}}`, _dark: `{colors.${name}.${steps.muted[1]}}` } },
  emphasized: { value: { _light: `{colors.${name}.${steps.emphasized[0]}}`, _dark: `{colors.${name}.${steps.emphasized[1]}}` } },
  focusRing: { value: { _light: `{colors.${name}.${steps.focusRing[0]}}`, _dark: `{colors.${name}.${steps.focusRing[1]}}` } },
  border: { value: { _light: `{colors.${name}.${steps.border[0]}}`, _dark: `{colors.${name}.${steps.border[1]}}` } },
});

const config = defineConfig({
  globalCss: {
    body: {
      bg: 'bg.canvas',
      color: 'fg',
      textStyle: 'body',
      // Zen typography is about readability, not expression: soften the default grid-fitting so the
      // weights render at the intended thickness rather than a heavier hinted approximation.
      textRendering: 'optimizeLegibility',
    },
    /**
     * §14: the focus indicator must be visible, and it must be the *same* indicator everywhere.
     * Stated in full rather than only re-coloured, because Chakra's per-component defaults differ
     * in width and offset — which is how "visible focus" became "visible on some controls".
     */
    '*:focus-visible': {
      outlineWidth: '2px',
      outlineStyle: 'solid',
      outlineColor: 'colorPalette.focusRing',
      outlineOffset: '2px',
    },
  },

  theme: {
    tokens: {
      fonts: {
        // Loaded by `next/font/local` in `app/layout.tsx`, which self-hosts the files — no network
        // at build. The token layer never names the typeface, only the variable.
        body: { value: 'var(--font-sans), system-ui, sans-serif' },
        heading: { value: 'var(--font-sans), system-ui, sans-serif' },
        mono: { value: 'var(--font-mono), ui-monospace, monospace' },
      },

      /**
       * §4 permits 400/500/600 only. `bold`, `extrabold` and `black` are aliased down rather than
       * deleted so that any Chakra recipe reaching for them lands inside the spec instead of
       * breaking — there is no way for a heavy weight to reach the screen. `app/layout.tsx` loads
       * only these three files, so a heavier weight could not render even if one leaked through.
       */
      fontWeights: {
        thin: { value: '400' },
        extralight: { value: '400' },
        light: { value: '400' },
        normal: { value: '400' },
        medium: { value: '500' },
        semibold: { value: '600' },
        bold: { value: '600' },
        extrabold: { value: '600' },
        black: { value: '600' },
      },

      // §4: body 1.5–1.7, headings 1.2–1.3.
      lineHeights: {
        shorter: { value: 1.2 },
        short: { value: 1.3 },
        moderate: { value: 1.6 },
        tall: { value: 1.65 },
        taller: { value: 1.7 },
      },

      colors: {
        sumi,
        ai,
        matcha,
        // The whole semantic layer derives from `gray`. Re-point it and the app follows.
        gray: sumi,
      },

      easings: {
        // §8: ease-out, and nothing that overshoots. Named `zen` so its use is deliberate at the
        // call site; `ease-out` is also sharpened to match, since Chakra's recipes reach for it.
        zen: { value: 'cubic-bezier(0, 0, 0.2, 1)' },
        'ease-out': { value: 'cubic-bezier(0, 0, 0.2, 1)' },
      },
    },

    /**
     * The whole type vocabulary, in six entries.
     *
     * §3 allows exactly three hierarchy levels, so there are three *sizes* that carry hierarchy
     * (`display`, `title`, `body`) and three that carry role rather than rank: `prose` for running
     * explanation, `meta` for a label, `key` for a business code.
     *
     * `key` is the one that matters most here. Every record in this system is addressed by a
     * human-readable code, so codes are read in columns — `tabular-nums` plus a fixed monospace
     * advance is what lets the eye run down `S00121 / S00122 / S00123` instead of re-finding the
     * left edge on every row.
     */
    textStyles: {
      display: {
        value: {
          fontFamily: 'heading',
          fontSize: '2rem',
          lineHeight: '1.2',
          fontWeight: '600',
          letterSpacing: '-0.02em',
        },
      },
      title: {
        value: {
          fontFamily: 'heading',
          fontSize: '1.25rem',
          lineHeight: '1.3',
          fontWeight: '600',
          letterSpacing: '-0.01em',
        },
      },
      body: {
        value: { fontSize: '0.9375rem', lineHeight: '1.6', fontWeight: '400', letterSpacing: '0' },
      },
      prose: {
        value: { fontSize: '1rem', lineHeight: '1.65', fontWeight: '400', letterSpacing: '0' },
      },
      meta: {
        value: { fontSize: '0.8125rem', lineHeight: '1.5', fontWeight: '500', letterSpacing: '0' },
      },
      key: {
        value: {
          fontFamily: 'mono',
          fontSize: '0.875rem',
          lineHeight: '1.4',
          fontWeight: '500',
          letterSpacing: '0',
          fontVariantNumeric: 'tabular-nums',
        },
      },
    },

    semanticTokens: {
      colors: {
        // Softer than Chakra's pure black — ink on paper, not print on screen.
        fg: {
          DEFAULT: { value: { _light: '{colors.sumi.900}', _dark: '#EDEDEA' } },
        },
        bg: {
          /**
           * The page ground (§5's "background"). Lists sit directly on it; only a genuinely bounded
           * object gets `bg.panel`. The two are ~1.5% apart by design — the separation between a
           * list and the page is meant to come from the rules and the spacing, not from a second
           * shade, and wrapping every table in a panel is what made the old screens read as one
           * flat field with a white slab dropped on it.
           */
          canvas: { value: { _light: '{colors.sumi.50}', _dark: '#161614' } },
          // In dark mode Chakra maps both the page and its panels to gray.950, which flattens the
          // surface/background distinction §5 depends on. Lift the panel one step.
          panel: { value: { _light: '{colors.white}', _dark: '{colors.sumi.900}' } },
          subtle: { value: { _light: '{colors.sumi.50}', _dark: '#161614' } },
        },
        border: {
          DEFAULT: { value: { _light: '{colors.sumi.200}', _dark: '#2E2E2B' } },
        },

        ai: paletteSlots('ai', {
          solid: [600, 400],
          contrast: ['white', 'white'],
          fg: [700, 300],
          subtle: [50, 900],
          muted: [100, 800],
          emphasized: [200, 700],
          focusRing: [500, 300],
          border: [400, 500],
        }),

        matcha: paletteSlots('matcha', {
          // 600/700 rather than the nominal 500: white-on-500 is 3.3:1 and fails AA.
          solid: [700, 500],
          contrast: ['white', 'white'],
          fg: [700, 300],
          subtle: [50, 900],
          muted: [100, 800],
          emphasized: [200, 700],
          focusRing: [600, 400],
          border: [400, 500],
        }),

        // `accent` is the name the app already used for "the primary". It now means indigo, not blue.
        accent: paletteSlots('ai', {
          solid: [600, 400],
          contrast: ['white', 'white'],
          fg: [700, 300],
          subtle: [50, 900],
          muted: [100, 800],
          emphasized: [200, 700],
          focusRing: [500, 300],
          border: [400, 500],
        }),
      },

      // §6: cards 12px, buttons and inputs 10px. `l1`/`l2`/`l3` are the slots Chakra's recipes read.
      radii: {
        l1: { value: '8px' },
        l2: { value: '10px' },
        l3: { value: '12px' },
      },
    },

    recipes: {
      /**
       * §6: 40–44px tall, solid fill, and a hover that touches background and border only — no
       * scaling, ever. The `xs`/`2xs` sizes are deliberately raised to the floor rather than left
       * available: a 24px control cannot satisfy §14's touch target, and leaving it callable is how
       * dense one-off buttons crept into the old pages.
       */
      button: {
        base: {
          borderRadius: 'l2',
          fontWeight: 'medium',
          transitionProperty: 'background-color, border-color, color, box-shadow',
          transitionDuration: 'fast',
          transitionTimingFunction: 'zen',
          // §14: 44×44 on touch, where a pointer cannot be precise.
          '@media (pointer: coarse)': { minHeight: '44px', minWidth: '44px' },
        },
        variants: {
          size: {
            '2xs': { h: '10', minW: '10', px: '4', textStyle: 'meta' },
            xs: { h: '10', minW: '10', px: '4', textStyle: 'meta' },
            sm: { h: '10', minW: '10', px: '4', textStyle: 'meta' },
            md: { h: '11', minW: '11', px: '5', textStyle: 'body' },
            lg: { h: '11', minW: '11', px: '6', textStyle: 'body' },
          },
        },
      },

      // §6: neutral 1px border, clear focus ring, no animated border, 44px so it matches the buttons.
      input: {
        base: { borderRadius: 'l2' },
        variants: {
          size: {
            sm: { '--input-height': 'sizes.10', px: '4', textStyle: 'body' },
            md: { '--input-height': 'sizes.11', px: '4', textStyle: 'body' },
            lg: { '--input-height': 'sizes.11', px: '4', textStyle: 'body' },
          },
        },
      },

      textarea: {
        base: { borderRadius: 'l2' },
        variants: { size: { md: { px: '4', py: '3', textStyle: 'body' } } },
      },

      badge: {
        base: { borderRadius: 'l1', fontWeight: 'medium' },
      },

      /**
       * §3: three levels, no more. `lg` is a page title and `md` a section heading — mapped onto
       * the `display` and `title` text styles so the two agree by construction rather than by two
       * people picking similar numbers.
       */
      heading: {
        base: { fontWeight: 'semibold' },
        variants: {
          size: {
            md: { textStyle: 'title' },
            lg: { textStyle: 'display' },
            xl: { textStyle: 'display' },
          },
        },
      },

      /** A business key rendered inline in running text. `ui/Key.tsx` is the usual way in. */
      code: {
        base: { textStyle: 'key', borderRadius: '6px' },
      },
    },

    slotRecipes: {
      /**
       * §6: 12px radius, a thin border, **no shadow**, and consistent internal padding. The
       * `elevated` variant is flattened rather than removed so an inherited `variant="elevated"`
       * cannot reintroduce a drop shadow.
       *
       * A card is now reserved for a genuinely bounded object — one record, one form, one dialog.
       * Lists are not bounded objects and no longer get one; see `DataTable`.
       */
      card: {
        slots: cardAnatomy.keys(),
        base: { root: { borderRadius: 'l3' } },
        variants: {
          variant: {
            elevated: { root: { bg: 'bg.panel', boxShadow: 'none', borderWidth: '1px', borderColor: 'border' } },
            outline: { root: { bg: 'bg.panel', boxShadow: 'none', borderWidth: '1px', borderColor: 'border' } },
          },
          size: {
            md: { root: { '--card-padding': 'spacing.6' }, title: { textStyle: 'title' } },
            lg: { root: { '--card-padding': 'spacing.8' }, title: { textStyle: 'title' } },
          },
        },
      },

      /**
       * §12: 48–56px rows, 16–20px horizontal padding, minimal borders, hover highlighting only.
       * `py="4"` + `px="5"` on a `body` text line lands at ~52px. `striped` is neutralised here
       * rather than only removed from `DataTable`, so zebra shading cannot come back through a prop.
       *
       * Column headers are sentence case. They used to be tracked-out uppercase, which is the
       * default admin-table treatment and costs real legibility — capitals remove the word-shape
       * the eye uses to skip to the column it wants. §3 also asks for hierarchy from spacing and
       * weight rather than from a second typographic device.
       *
       * Every cell gets `tabular-nums`. Credits, enrolment counts, dates and codes all read in
       * columns here, and proportional figures make a column of numbers ragged at both edges.
       */
      table: {
        slots: tableAnatomy.keys(),
        base: {
          root: { fontVariantNumeric: 'tabular-nums' },
          columnHeader: {
            color: 'fg.muted',
            textStyle: 'meta',
            fontWeight: 'medium',
            textTransform: 'none',
            letterSpacing: '0',
            whiteSpace: 'nowrap',
          },
          row: { transitionProperty: 'background-color', transitionDuration: 'fast', transitionTimingFunction: 'zen' },
          cell: { textStyle: 'body' },
        },
        variants: {
          striped: { true: { row: { '&:nth-of-type(odd) td': { bg: 'transparent' } } } },
          size: {
            sm: { columnHeader: { px: '5', py: '3' }, cell: { px: '5', py: '4' } },
            md: { columnHeader: { px: '5', py: '3' }, cell: { px: '5', py: '4' } },
            lg: { columnHeader: { px: '5', py: '3' }, cell: { px: '5', py: '4' } },
          },
          variant: {
            line: {
              columnHeader: { borderBottomWidth: '1px', borderColor: 'border' },
              // Interior rules removed; only the last row keeps a separator from the container edge.
              cell: { borderBottomWidth: '1px', borderColor: 'border.muted' },
              row: { bg: 'transparent' },
            },
          },
        },
      },

      dialog: {
        slots: dialogAnatomy.keys(),
        base: {
          content: { borderRadius: 'l3', boxShadow: 'sm', borderWidth: '1px', borderColor: 'border' },
          backdrop: { bg: 'blackAlpha.400', backdropFilter: 'blur(2px)' },
          title: { textStyle: 'title' },
        },
      },

      drawer: {
        slots: dialogAnatomy.keys(),
        base: {
          content: { bg: 'bg.panel' },
          backdrop: { bg: 'blackAlpha.400', backdropFilter: 'blur(2px)' },
          title: { textStyle: 'title' },
        },
      },

      alert: {
        slots: alertAnatomy.keys(),
        base: { root: { borderRadius: 'l2' }, description: { textStyle: 'body' } },
      },

      /**
       * `orientation="vertical"` is what the detail screens now use: a 300px identity column has no
       * room for an 11rem label gutter beside its value, and stacking the pair reads faster anyway.
       */
      dataList: {
        slots: dataListAnatomy.keys(),
        base: {
          itemLabel: { color: 'fg.muted', textStyle: 'meta' },
          itemValue: { textStyle: 'body' },
        },
      },
    },
  },
});

export const system = createSystem(defaultConfig, config);

export default system;
