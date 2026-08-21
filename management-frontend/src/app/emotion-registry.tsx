'use client';

import createCache, { type EmotionCache } from '@emotion/cache';
import { CacheProvider } from '@emotion/react';
import { useServerInsertedHTML } from 'next/navigation';
import { useState, type ReactNode } from 'react';

/**
 * Emotion still has no first-class App Router support (Next's own CSS-in-JS guide lists it under
 * "currently working on support"), so it needs the three-step registry that guide describes: a
 * cache that collects the rules a render produced, `useServerInsertedHTML` to emit them, and a
 * client component wrapping the tree.
 *
 * Without it the app cannot hydrate at all. Emotion's `<Global>` and per-component `<Insertion>`
 * render a real `<style>` element on the server and `null` in the browser, so Chakra's two
 * `ChakraProvider` globals become two `<style>` tags the client never renders — React lines the
 * client's first real node (next-themes' theme script) up against `<style data-emotion="css-global
 * ...">`, fails on the type mismatch, and throws the whole server render away.
 *
 * `cache.compat = true` is the switch that fixes it: emotion's server branch then caches the rules
 * and returns `null` instead of an element, leaving the markup identical on both sides. The rules
 * have to go somewhere, which is what the flush below is for.
 */
export default function EmotionRegistry({ children }: { children: ReactNode }) {
  const [{ cache, flush }] = useState(() => {
    // The key must stay Emotion's default: Chakra's class names (`css-1edvsk0`) are built from it.
    const cache = createCache({ key: 'css' });
    cache.compat = true;

    let inserted: { name: string; isGlobal: boolean }[] = [];

    // Only on the server: `useServerInsertedHTML` never fires in the browser, so tracking there
    // would be an array that only ever grows.
    if (typeof window === 'undefined') {
      const prevInsert = cache.insert.bind(cache);
      cache.insert = (...args: Parameters<EmotionCache['insert']>) => {
        const [selector, serialized] = args;
        if (cache.inserted[serialized.name] === undefined) {
          // `<Global>` inserts with an empty selector; component styles insert with `.css-hash`.
          inserted.push({ name: serialized.name, isGlobal: selector === '' });
        }
        return prevInsert(...args);
      };
    }

    const flush = () => {
      const flushed = inserted;
      inserted = [];
      return flushed;
    };

    return { cache, flush };
  });

  useServerInsertedHTML(() => {
    const flushed = flush();
    if (flushed.length === 0) return null;

    // The `data-emotion` shapes below are load-bearing, not decoration. On the client emotion's
    // `<Global>` looks for `style[data-emotion="css-global <name>"]` and adopts that node instead
    // of inserting a duplicate, and `createCache` scans `style[data-emotion]` to learn which rules
    // are already in the document.
    const globals: { name: string; style: string }[] = [];
    let componentStyles = '';
    let componentNames = cache.key;

    for (const { name, isGlobal } of flushed) {
      const style = cache.inserted[name];
      if (typeof style !== 'string') continue;
      if (isGlobal) {
        globals.push({ name, style });
      } else {
        componentStyles += style;
        componentNames += ` ${name}`;
      }
    }

    return (
      <>
        {globals.map(({ name, style }) => (
          <style
            key={name}
            data-emotion={`${cache.key}-global ${name}`}
            dangerouslySetInnerHTML={{ __html: style }}
          />
        ))}
        {componentStyles ? (
          <style
            data-emotion={componentNames}
            dangerouslySetInnerHTML={{ __html: componentStyles }}
          />
        ) : null}
      </>
    );
  });

  // Kept on both sides: the browser needs the same cache that adopted the tags above, or it would
  // re-insert every rule into emotion's default cache.
  return <CacheProvider value={cache}>{children}</CacheProvider>;
}
