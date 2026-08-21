#!/usr/bin/env node
/**
 * Compiles the Markdown under `docs/` into standalone HTML pages.
 *
 * Why this exists
 * ---------------
 * `docs/` used to carry a hand-written `.html` twin beside each `.md`. Every doc edit therefore had
 * to be made twice, and they had already drifted apart — `SA-docs/01-system-overview.html` claimed
 * "Part 1 of 5" while its Markdown source said "Part 1 of 6". The HTML is now generated and
 * gitignored (`docs/.gitignore`); the Markdown is the only source.
 *
 *   node util/md-to-html.js                       # compile every docs/**\/*.md
 *   node util/md-to-html.js --watch               # ...and recompile on change
 *   node util/md-to-html.js docs/SA-docs/06-low-level-design.md
 *   node util/md-to-html.js --clean               # delete generated HTML, compile nothing
 *
 * What it reproduces from the hand-written pages
 * ----------------------------------------------
 *   - the embedded light/dark stylesheet, tokens and all (docs-template.js)
 *   - `<title>` of "<doc name> — Student Management System <SET> Docs"
 *   - the `.subtitle` line and `.doc-nav` pill breadcrumb, parsed out of the source's
 *     "… Document — Part N of M (a → b → c)." lead paragraph
 *   - ```mermaid fences as live diagrams, and `![alt](x.svg)` as *inlined* SVG (not `<img>`), both
 *     click-to-zoom through a shared pan/zoom lightbox
 *   - `tree-block` styling for the ASCII package trees
 *   - relative `.md` links rewritten to `.html`
 *
 * What it adds
 * ------------
 * Accessibility that a hand-written page set had drifted away from doing consistently: a skip link,
 * a `<main>` landmark, `scope="col"` on table headers, `<figure>`/`<figcaption>` with an
 * `aria-label` naming each diagram, keyboard-operable diagram triggers, a real `role="dialog"`
 * lightbox with a focus trap and focus restore, a polite live region for the zoom level, visible
 * `:focus-visible` rings, and a `prefers-reduced-motion` block. See docs-template.js.
 */

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { Marked } from 'marked';

import { renderPage } from './docs-template.js';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DOCS_ROOT = path.join(REPO_ROOT, 'docs');

// ---------------------------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------------------------

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * GitHub's heading-anchor algorithm: lowercase, drop anything that isn't a word character, space,
 * or hyphen, then spaces to hyphens. Matching GitHub matters because the Markdown already contains
 * at least one in-document link written against it (`Testing/README.md` → `#uc--file-index`), and
 * that link has to keep working once the page is HTML.
 */
function slugify(text) {
  return text
    .toLowerCase()
    .replace(/<[^>]*>/g, '')
    .replace(/[^\w\s-]/g, '')
    .trim()
    .replace(/\s+/g, '-');
}

/** Makes each slug unique within one page, `-1`, `-2`, … the way GitHub does. */
function uniqueSlugger() {
  const seen = new Map();
  return (text) => {
    const base = slugify(text) || 'section';
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    return count === 0 ? base : `${base}-${count}`;
  };
}

/** `docs/SA-docs/06-…md` → `SA`; `docs/Testing/03-test-cases/book.md` → `Testing`. */
function docSetOf(absPath) {
  const relative = path.relative(DOCS_ROOT, absPath);
  const [top] = relative.split(path.sep);
  return top.endsWith('-docs') ? top.slice(0, -'-docs'.length) : top;
}

// ---------------------------------------------------------------------------------------------
// Header: <h1>, .subtitle, .doc-nav
// ---------------------------------------------------------------------------------------------

/**
 * Every numbered doc set opens with the same shape:
 *
 *   # Component Diagram
 *
 *   Solution Architecture Document — Part 2 of 6 ([System Overview](./01-…md) → Component Diagram → …).
 *
 * The lead paragraph is lifted out of the body and split into the muted `.subtitle` line and the
 * `.doc-nav` breadcrumb, with the current part rendered as a pill instead of a link. Docs without
 * that shape (BA-docs, the READMEs, the companion SA docs) simply keep their lead paragraph as
 * ordinary body text — there is nothing to derive, and inventing a subtitle is what let the
 * hand-written pages drift from their sources in the first place.
 */
const PART_LINE = /^(.+?\s+—\s+Part\s+(\d+)\s+of\s+\d+)\s*\((.+)\)\s*\.?\s*$/;
const MD_LINK = /^\[([^\]]+)\]\(([^)]+)\)$/;

function extractHeader(markdown, { rewriteLink, inline }) {
  const lines = markdown.split('\n');
  let index = 0;
  while (index < lines.length && lines[index].trim() === '') index += 1;

  const titleMatch = /^#\s+(.+?)\s*$/.exec(lines[index] ?? '');
  if (!titleMatch) {
    return { heading: null, headerHtml: '', rest: markdown };
  }
  const heading = titleMatch[1];
  index += 1;

  // Look at the next non-blank paragraph only; a doc-nav line is always the lead.
  let scan = index;
  while (scan < lines.length && lines[scan].trim() === '') scan += 1;
  const lead = (lines[scan] ?? '').trim();
  const partMatch = PART_LINE.exec(lead);

  // The H1 goes through the inline parser, not escapeHtml: several are written with inline code
  // ("Test Cases — `book` Module") and would otherwise show their backticks.
  let headerHtml = `<h1>${inline(heading)}</h1>\n`;
  let rest;
  if (partMatch) {
    const [, subtitle, part, trail] = partMatch;
    const crumbs = trail
      .split('→')
      .map((crumb) => crumb.trim())
      .filter(Boolean)
      .map((crumb) => {
        const link = MD_LINK.exec(crumb);
        return link
          ? `<a href="${escapeHtml(rewriteLink(link[2]))}">${escapeHtml(link[1])}</a>`
          : `<span class="pill">Part ${escapeHtml(part)}</span> ${escapeHtml(crumb)}`;
      });
    headerHtml +=
      `<p class="subtitle">${escapeHtml(subtitle)}</p>\n` +
      `<nav class="doc-nav" aria-label="Document set">\n  ${crumbs.join('\n  &nbsp;→&nbsp;\n  ')}\n</nav>\n`;
    rest = lines.slice(scan + 1).join('\n');
  } else {
    rest = lines.slice(index).join('\n');
  }

  return { heading, headerHtml, rest };
}

/**
 * Trims the redundant "Student Management System — " prefix some H1s carry, since the `<title>`
 * already ends with the system name. `# Student Management System — Use Cases` becomes
 * "Use Cases — Student Management System BA Docs" rather than saying it twice.
 */
function pageTitle(heading, absPath) {
  const name = (heading ?? path.basename(absPath, '.md'))
    .replace(/^Student Management System\s+—\s+/, '')
    // `<title>` is plain text -- an inline-code heading like "Test Cases — `book` Module" would
    // otherwise show its backticks in the browser tab.
    .replace(/[`*_]/g, '');
  return escapeHtml(`${name} — Student Management System ${docSetOf(absPath)} Docs`);
}

// ---------------------------------------------------------------------------------------------
// Markdown → HTML
// ---------------------------------------------------------------------------------------------

/**
 * Builds a `marked` instance bound to one source file — the renderer overrides need the file's
 * directory to resolve `![…](./x.svg)` and to know which sibling `.md` links become `.html`.
 */
function compilerFor(absPath, state) {
  const dir = path.dirname(absPath);
  const slug = uniqueSlugger();

  /** Relative `.md` → `.html`; anything absolute, external, or anchored is left alone. */
  const rewriteLink = (href) => {
    if (!href || /^[a-z][a-z0-9+.-]*:/i.test(href) || href.startsWith('#') || href.startsWith('//')) {
      return href;
    }
    const [pathPart, hash = ''] = href.split('#');
    const suffix = hash ? `#${hash}` : '';
    return pathPart.endsWith('.md') ? `${pathPart.slice(0, -3)}.html${suffix}` : href;
  };

  const marked = new Marked({ gfm: true, breaks: false });

  marked.use({
    renderer: {
      heading({ tokens, depth }) {
        const text = this.parser.parseInline(tokens);
        // Remembered so a mermaid fence further down can name itself after the section it
        // illustrates -- an SVG image has alt text to use, a fenced diagram has nothing else.
        state.lastHeading = text.replace(/<[^>]*>/g, '').trim();
        return `<h${depth} id="${slug(text)}">${text}</h${depth}>\n`;
      },

      link({ href, title, tokens }) {
        const text = this.parser.parseInline(tokens);
        const titleAttr = title ? ` title="${escapeHtml(title)}"` : '';
        return `<a href="${escapeHtml(rewriteLink(href))}"${titleAttr}>${text}</a>`;
      },

      /**
       * ```mermaid fences become live diagrams; unlabelled fences that look like an ASCII package
       * tree get the `tree-block` class the hand-written pages used, which only tightens line
       * height so the box-drawing characters connect.
       */
      code({ text, lang }) {
        if ((lang ?? '').trim().toLowerCase() === 'mermaid') {
          state.hasDiagrams = true;
          state.diagramCount += 1;
          const label = state.lastHeading
            ? `Diagram ${state.diagramCount}: ${state.lastHeading}`
            : `Diagram ${state.diagramCount}`;
          return diagramFigure(
            `<pre class="mermaid">\n${escapeHtml(text)}\n</pre>`,
            'mermaid-wrap',
            label,
            'Click or press Enter to zoom &amp; pan',
          );
        }
        const isTree = !lang && /[├└│─]/.test(text);
        const classAttr = isTree ? ' class="tree-block"' : '';
        const langAttr = lang ? ` class="language-${escapeHtml(lang.split(/\s+/)[0])}"` : '';
        return `<pre${classAttr}><code${langAttr}>${escapeHtml(text)}</code></pre>\n`;
      },

      /**
       * An `.svg` image is inlined rather than referenced. The lightbox clones the live SVG node to
       * zoom it, which an `<img>` would not expose — this is why the hand-written pages pasted the
       * PlantUML output in, and why regenerating those `.svg` files is enough to update the HTML.
       */
      image({ href, text }) {
        if (href && href.toLowerCase().endsWith('.svg')) {
          const svgPath = path.resolve(dir, href.split('#')[0]);
          if (fs.existsSync(svgPath)) {
            const svg = fs.readFileSync(svgPath, 'utf8').replace(/<\?xml[^>]*\?>\s*/g, '');
            return diagramFigure(
              svg,
              'diagram-wrap',
              text || 'Diagram',
              'Click or press Enter to zoom &amp; pan',
            );
          }
          state.warnings.push(`missing SVG: ${path.relative(REPO_ROOT, svgPath)}`);
        }
        return `<img src="${escapeHtml(rewriteLink(href))}" alt="${escapeHtml(text ?? '')}">`;
      },

      /** Wide tables scroll inside their own box instead of pushing the page sideways. */
      table(token) {
        const header = token.header
          .map((cell, i) => renderCell.call(this, cell, 'th', token.align[i]))
          .join('');
        const body = token.rows
          .map(
            (row) =>
              `<tr>${row.map((cell, i) => renderCell.call(this, cell, 'td', token.align[i])).join('')}</tr>\n`,
          )
          .join('');
        return (
          `<div class="table-scroll">\n<table>\n<thead>\n<tr>${header}</tr>\n</thead>\n` +
          `<tbody>\n${body}</tbody>\n</table>\n</div>\n`
        );
      },

      /**
       * A lone `![alt](x.svg)` on its own line is a paragraph as far as Markdown is concerned, but
       * it compiles to a block `<figure>` — which is not valid inside a `<p>`, and which browsers
       * would silently re-parent. A paragraph that is nothing but a figure emits the figure alone.
       */
      paragraph({ tokens }) {
        const text = this.parser.parseInline(tokens);
        return text.startsWith('<figure') ? `${text}\n` : `<p>${text}</p>\n`;
      },

      listitem(token) {
        const text = this.parser.parse(token.tokens, !!token.loose).trim();
        const inner = /^<p>([\s\S]*)<\/p>$/.exec(text);
        const content = token.loose ? text : (inner ? inner[1] : text);
        if (token.task) {
          const checked = token.checked ? ' checked' : '';
          return `<li class="task-list-item"><input type="checkbox" disabled${checked}> ${content}</li>\n`;
        }
        return `<li>${content}</li>\n`;
      },
    },
  });

  return { marked, rewriteLink };
}

function renderCell(cell, tag, align) {
  const content = this.parser.parseInline(cell.tokens);
  const alignAttr = align ? ` style="text-align:${align}"` : '';
  const scopeAttr = tag === 'th' ? ' scope="col"' : '';
  return `<${tag}${scopeAttr}${alignAttr}>${content}</${tag}>`;
}

/**
 * One diagram, wrapped so it is announced as an image, reachable by keyboard, and captioned.
 *
 * `role="button"` on a `<div>` rather than a real `<button>`: a button's content model is phrasing
 * content, and both a `<pre>` (mermaid's pre-render placeholder) and a block `<svg>` layout would
 * break inside one. The keydown handler in docs-template.js supplies Enter/Space activation, which
 * is the rest of what a native button would have given.
 */
function diagramFigure(inner, wrapClass, label, caption) {
  const safeLabel = escapeHtml(label);
  return `<figure role="group" aria-label="${safeLabel}">
<div class="${wrapClass}" role="button" tabindex="0" aria-haspopup="dialog" aria-label="${safeLabel} — open zoomable viewer">
${inner}
</div>
<figcaption class="fig-caption">${caption}</figcaption>
</figure>\n`;
}

// ---------------------------------------------------------------------------------------------
// File-level compile
// ---------------------------------------------------------------------------------------------

export function compileFile(absPath) {
  const state = { hasDiagrams: false, diagramCount: 0, lastHeading: null, warnings: [] };
  const { marked, rewriteLink } = compilerFor(absPath, state);
  const markdown = fs.readFileSync(absPath, 'utf8');

  const { heading, headerHtml, rest } = extractHeader(markdown, {
    rewriteLink,
    inline: (text) => marked.parseInline(text),
  });
  const body = marked.parse(rest);

  const html = renderPage({
    title: pageTitle(heading, absPath),
    header: headerHtml,
    body,
    hasDiagrams: state.hasDiagrams,
  });

  const outPath = absPath.replace(/\.md$/, '.html');
  fs.writeFileSync(outPath, html, 'utf8');
  return { outPath, warnings: state.warnings };
}

// ---------------------------------------------------------------------------------------------
// Walking, cleaning, CLI
// ---------------------------------------------------------------------------------------------

function walk(dir, predicate, found = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name.startsWith('.') || entry.name === 'node_modules') continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, predicate, found);
    else if (predicate(full)) found.push(full);
  }
  return found;
}

const markdownFiles = () => walk(DOCS_ROOT, (f) => f.endsWith('.md')).sort();

/**
 * Removes generated HTML. Deliberately narrow: it deletes an `.html` only when a `.md` of the same
 * name sits beside it, so `api-specification.html` — Redoc's output, regenerated by the pipeline in
 * `docs/SA-docs/api-specification.md` §4 — is only removed because that pairing happens to hold,
 * and nothing else under `docs/` is ever touched.
 */
function clean() {
  let removed = 0;
  for (const html of walk(DOCS_ROOT, (f) => f.endsWith('.html'))) {
    if (fs.existsSync(html.replace(/\.html$/, '.md'))) {
      fs.unlinkSync(html);
      removed += 1;
    }
  }
  return removed;
}

function compileAll(files) {
  const warnings = [];
  for (const file of files) {
    const result = compileFile(file);
    warnings.push(...result.warnings.map((w) => `${path.relative(REPO_ROOT, file)}: ${w}`));
  }
  console.log(`md-to-html: compiled ${files.length} file${files.length === 1 ? '' : 's'}`);
  for (const warning of new Set(warnings)) console.warn(`  warning: ${warning}`);
}

function main(argv) {
  const flags = new Set(argv.filter((a) => a.startsWith('--')));
  const targets = argv
    .filter((a) => !a.startsWith('--'))
    .map((a) => path.resolve(REPO_ROOT, a));

  if (flags.has('--help')) {
    console.log(
      [
        'Usage: node util/md-to-html.js [options] [files...]',
        '',
        '  (no args)   compile every Markdown file under docs/',
        '  files...    compile only those files',
        '  --watch     compile, then recompile on change',
        '  --clean     delete generated HTML and exit',
        '  --help      this message',
      ].join('\n'),
    );
    return;
  }

  if (flags.has('--clean')) {
    console.log(`md-to-html: removed ${clean()} generated file(s)`);
    return;
  }

  const files = targets.length ? targets : markdownFiles();
  compileAll(files);

  if (flags.has('--watch')) {
    console.log('md-to-html: watching docs/ (Ctrl-C to stop)');
    let pending = null;
    fs.watch(DOCS_ROOT, { recursive: true }, (_event, filename) => {
      // Editors save in bursts (write, rename, chmod); one debounced rebuild covers the burst.
      if (!filename || !/\.(md|svg)$/.test(filename)) return;
      clearTimeout(pending);
      pending = setTimeout(() => compileAll(targets.length ? files : markdownFiles()), 120);
    });
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2));
}
