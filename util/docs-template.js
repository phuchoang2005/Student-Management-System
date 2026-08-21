/**
 * The page shell every generated doc shares: the stylesheet, the diagram lightbox markup, and the
 * client script that drives it.
 *
 * Kept separate from `md-to-html.js` so that file stays about *translating Markdown*, and this one
 * stays about *what a doc page is*. Both are hand-maintained here exactly once, which is the point
 * of the compiler — the HTML under `docs/` used to be hand-edited per file and had already drifted
 * (SA-docs/01's committed copy said "Part 1 of 5" while its Markdown source said "Part 1 of 6").
 *
 * Everything is inlined: no CDN stylesheet, no external font, no build step beyond `node`. Mermaid
 * is the single exception — it is a renderer, not content, and the previous hand-written pages
 * already loaded it from jsDelivr.
 */

const MERMAID_CDN = 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js';

/**
 * Light and dark palettes as custom properties, switched by `prefers-color-scheme`, plus the
 * component rules the generated markup uses. The token names and values are carried over verbatim
 * from the hand-written pages so generated output looks identical to what it replaces; everything
 * from `.skip-link` down is new, and exists to make the page usable without a mouse or without
 * fine motion (see the accessibility notes in md-to-html.js).
 */
const STYLES = `
  :root {
    --bg: #ffffff;
    --fg: #1c1e21;
    --muted: #57606a;
    --border: #d8dee4;
    --code-bg: #f6f8fa;
    --link: #0969da;
    --table-stripe: #f6f8fa;
    --header-bg: #f6f8fa;
    --pill-bg: #eef2ff;
    --pill-fg: #3730a3;
    --focus: #0969da;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg: #0d1117;
      --fg: #e6edf3;
      --muted: #9198a1;
      --border: #30363d;
      --code-bg: #161b22;
      --link: #4493f8;
      --table-stripe: #161b22;
      --header-bg: #161b22;
      --pill-bg: #1c2333;
      --pill-fg: #a5b4fc;
      --focus: #4493f8;
    }
  }
  * { box-sizing: border-box; }
  body {
    background: var(--bg);
    color: var(--fg);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
    line-height: 1.65;
    max-width: 880px;
    margin: 0 auto;
    padding: 2.5rem 1.5rem 5rem;
  }
  h1 {
    font-size: 2rem;
    margin-bottom: 0.4rem;
    border-bottom: 1px solid var(--border);
    padding-bottom: 0.6rem;
  }
  h2 {
    font-size: 1.4rem;
    margin-top: 2.6rem;
    padding-top: 0.4rem;
    border-top: 1px solid var(--border);
  }
  h3 {
    font-size: 1.1rem;
    margin-top: 1.8rem;
    color: var(--fg);
  }
  h4, h5, h6 { font-size: 1rem; margin-top: 1.4rem; }
  p { color: var(--fg); }
  .subtitle {
    color: var(--muted);
    font-size: 0.95rem;
    margin-top: 0;
  }
  .doc-nav {
    display: flex;
    gap: 0.5rem;
    align-items: center;
    flex-wrap: wrap;
    font-size: 0.85rem;
    color: var(--muted);
    margin: 0.6rem 0 1.6rem;
  }
  .doc-nav .pill {
    background: var(--pill-bg);
    color: var(--pill-fg);
    padding: 0.15rem 0.6rem;
    border-radius: 999px;
    font-weight: 600;
  }
  .doc-nav a { color: var(--link); text-decoration: none; }
  .doc-nav a:hover { text-decoration: underline; }
  a { color: var(--link); }
  code {
    background: var(--code-bg);
    padding: 0.15em 0.4em;
    border-radius: 4px;
    font-size: 0.9em;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  }
  pre {
    background: var(--code-bg);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 0.9rem 1.1rem;
    overflow-x: auto;
    font-size: 0.87rem;
    line-height: 1.55;
  }
  pre code {
    background: none;
    padding: 0;
    font-size: inherit;
  }
  pre.tree-block { line-height: 1.4; }
  blockquote {
    margin: 1.2rem 0;
    padding: 0.1rem 1.1rem;
    border-left: 4px solid var(--border);
    color: var(--muted);
  }
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 1.2rem 0;
    font-size: 0.92rem;
  }
  th, td {
    border: 1px solid var(--border);
    padding: 0.55rem 0.8rem;
    text-align: left;
    vertical-align: top;
  }
  th {
    background: var(--header-bg);
    font-weight: 600;
  }
  tr:nth-child(even) td { background: var(--table-stripe); }
  /* Wide tables scroll inside their own box rather than pushing the page sideways. */
  .table-scroll { overflow-x: auto; margin: 1.2rem 0; }
  .table-scroll > table { margin: 0; }
  .mermaid-wrap {
    background: var(--code-bg);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 1.2rem;
    margin: 1.4rem 0 0.5rem;
    overflow-x: auto;
    transition: box-shadow 0.15s ease, border-color 0.15s ease;
  }
  .mermaid-wrap.zoomable { cursor: zoom-in; }
  .mermaid-wrap.zoomable:hover {
    border-color: var(--link);
    box-shadow: 0 0 0 1px var(--link);
  }
  .mermaid { display: flex; justify-content: center; }
  .diagram-wrap {
    background: var(--code-bg);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 1.2rem;
    margin: 1.4rem 0 0.5rem;
    overflow-x: auto;
    transition: box-shadow 0.15s ease, border-color 0.15s ease;
  }
  .diagram-wrap.zoomable { cursor: zoom-in; }
  .diagram-wrap.zoomable:hover {
    border-color: var(--link);
    box-shadow: 0 0 0 1px var(--link);
  }
  .diagram-wrap svg { display: block; margin: 0 auto; max-width: 100%; height: auto; }
  figure { margin: 0; }
  .fig-caption {
    text-align: center;
    color: var(--muted);
    font-size: 0.82rem;
    margin-top: 0;
    margin-bottom: 1.4rem;
  }
  ul, ol { padding-left: 1.4rem; }
  li { margin: 0.3rem 0; }
  li.task-list-item { list-style: none; margin-left: -1.2rem; }
  li.task-list-item input { margin-right: 0.5rem; }
  hr { border: none; border-top: 1px solid var(--border); margin: 2rem 0; }
  em { color: var(--muted); }
  img { max-width: 100%; height: auto; }

  /* Accessibility ------------------------------------------------------------------------- */

  /* Visible only once focused, so a keyboard user can jump the nav straight to the document. */
  .skip-link {
    position: absolute;
    left: -9999px;
    top: 0;
    background: var(--bg);
    color: var(--link);
    border: 1px solid var(--link);
    border-radius: 6px;
    padding: 0.5rem 0.9rem;
    z-index: 1100;
  }
  .skip-link:focus {
    left: 1rem;
    top: 1rem;
  }
  :focus-visible {
    outline: 2px solid var(--focus);
    outline-offset: 2px;
    border-radius: 4px;
  }
  /* Screen-reader-only text: announced, never painted. */
  .visually-hidden {
    position: absolute;
    width: 1px;
    height: 1px;
    margin: -1px;
    padding: 0;
    overflow: hidden;
    clip: rect(0 0 0 0);
    clip-path: inset(50%);
    white-space: nowrap;
    border: 0;
  }
  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after {
      animation-duration: 0.01ms !important;
      animation-iteration-count: 1 !important;
      transition-duration: 0.01ms !important;
      scroll-behavior: auto !important;
    }
  }

  /* Diagram lightbox */
  .lightbox-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.86);
    display: none;
    align-items: center;
    justify-content: center;
    z-index: 1000;
  }
  .lightbox-overlay.open { display: flex; }
  .lightbox-viewport {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    cursor: grab;
    touch-action: none;
  }
  .lightbox-viewport.grabbing { cursor: grabbing; }
  .lightbox-stage {
    transform-origin: center center;
    will-change: transform;
    background: var(--bg);
    border-radius: 8px;
    padding: 1.25rem;
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
  }
  .lightbox-stage svg.lightbox-svg {
    display: block;
  }
  .lightbox-toolbar {
    position: absolute;
    top: 1rem;
    right: 1rem;
    display: flex;
    gap: 0.5rem;
    z-index: 1001;
  }
  .lightbox-toolbar button {
    background: rgba(255, 255, 255, 0.12);
    color: #fff;
    border: 1px solid rgba(255, 255, 255, 0.3);
    border-radius: 6px;
    width: 2.5rem;
    height: 2.5rem;
    font-size: 1.05rem;
    line-height: 1;
    cursor: pointer;
  }
  .lightbox-toolbar button:hover { background: rgba(255, 255, 255, 0.28); }
  .lightbox-toolbar button:focus-visible { outline: 2px solid #fff; outline-offset: 2px; }
  #lightbox-reset { width: auto; padding: 0 0.8rem; font-size: 0.85rem; }
  .lightbox-hint {
    position: absolute;
    bottom: 1.1rem;
    left: 50%;
    transform: translateX(-50%);
    color: rgba(255, 255, 255, 0.65);
    font-size: 0.8rem;
    white-space: nowrap;
  }
`;

/**
 * The lightbox overlay. `role="dialog"` + `aria-modal` + a label make it a real dialog to a screen
 * reader; the script below traps focus inside it while open and restores focus to the diagram that
 * opened it on close.
 */
const LIGHTBOX_MARKUP = `<div id="diagram-lightbox" class="lightbox-overlay" role="dialog" aria-modal="true" aria-label="Diagram viewer" aria-hidden="true">
  <div class="lightbox-toolbar">
    <button id="lightbox-zoom-out" type="button" title="Zoom out" aria-label="Zoom out">&minus;</button>
    <button id="lightbox-reset" type="button" title="Reset zoom" aria-label="Reset zoom to 100%">100%</button>
    <button id="lightbox-zoom-in" type="button" title="Zoom in" aria-label="Zoom in">+</button>
    <button id="lightbox-close" type="button" title="Close (Esc)" aria-label="Close diagram viewer">&#10005;</button>
  </div>
  <div id="lightbox-viewport" class="lightbox-viewport">
    <div id="lightbox-stage" class="lightbox-stage"></div>
  </div>
  <p class="lightbox-hint">Scroll to zoom &middot; Drag to pan &middot; Double-click to reset &middot; Esc to close</p>
  <p id="lightbox-zoom-status" class="visually-hidden" role="status" aria-live="polite"></p>
</div>`;

/**
 * Renders every `<pre class="mermaid">` on the page, then wires the lightbox.
 *
 * Two things carried over from the hand-written pages because they were hard-won:
 *   - zooming resizes the SVG's width/height attributes rather than applying a CSS
 *     `transform: scale()`, so the browser re-renders the vector at the target resolution instead
 *     of stretching an already-rasterised layer (which looked blurry);
 *   - `mermaid.run()` is awaited before the lightbox binds, since the elements it clones don't
 *     exist until then.
 *
 * New here: every diagram is a `role="button"` with `tabindex="0"` that opens on Enter/Space (a
 * real `<button>` can't be used — its content model is phrasing content, and these wrap a `<pre>`),
 * focus is trapped in the dialog while open and restored on close, and the zoom level is announced
 * through a polite live region.
 */
const SCRIPT = `
  (async function () {
    const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
    if (window.mermaid) {
      mermaid.initialize({
        startOnLoad: false,
        theme: prefersDark ? "dark" : "default",
        securityLevel: "strict",
        flowchart: { htmlLabels: true, curve: "basis" }
      });
      try {
        await mermaid.run();
      } catch (err) {
        console.error("mermaid render failed", err);
      }
    }
    initDiagramLightbox();
  })();

  function initDiagramLightbox() {
    const overlay = document.getElementById("diagram-lightbox");
    const viewport = document.getElementById("lightbox-viewport");
    const stage = document.getElementById("lightbox-stage");
    const zoomInBtn = document.getElementById("lightbox-zoom-in");
    const zoomOutBtn = document.getElementById("lightbox-zoom-out");
    const resetBtn = document.getElementById("lightbox-reset");
    const closeBtn = document.getElementById("lightbox-close");
    const zoomStatus = document.getElementById("lightbox-zoom-status");
    if (!overlay) return;

    const MIN_SCALE = 0.15;
    const MAX_SCALE = 10;
    let scale = 1, panX = 0, panY = 0;
    let baseWidth = 800, baseHeight = 600;
    let currentSvg = null;
    let dragging = false, moved = false, startX = 0, startY = 0, startPanX = 0, startPanY = 0;
    let applyQueued = false;
    let lastTrigger = null;

    // Resizes the SVG's actual width/height (not a CSS transform: scale) so the
    // browser re-renders the vector at the target resolution instead of stretching
    // a rasterized layer, which is what caused blur on zoom.
    function apply() {
      if (applyQueued) return;
      applyQueued = true;
      requestAnimationFrame(() => {
        applyQueued = false;
        if (currentSvg) {
          currentSvg.style.width = Math.round(baseWidth * scale) + "px";
          currentSvg.style.height = Math.round(baseHeight * scale) + "px";
        }
        stage.style.transform = \`translate(\${panX}px, \${panY}px)\`;
        const percent = Math.round(scale * 100) + "%";
        resetBtn.textContent = percent;
        if (zoomStatus) zoomStatus.textContent = "Zoom " + percent;
      });
    }
    function resetView() {
      scale = 1; panX = 0; panY = 0;
      apply();
    }
    function zoomBy(factor) {
      scale = Math.min(Math.max(scale * factor, MIN_SCALE), MAX_SCALE);
      apply();
    }
    function openLightbox(svg, trigger) {
      stage.innerHTML = "";
      const clone = svg.cloneNode(true);
      clone.classList.add("lightbox-svg");
      clone.removeAttribute("style");
      clone.removeAttribute("width");
      clone.removeAttribute("height");

      let vbWidth = 800, vbHeight = 600;
      const vb = clone.getAttribute("viewBox");
      if (vb) {
        const parts = vb.trim().split(/\\s+/).map(Number);
        if (parts.length === 4 && parts[2] > 0 && parts[3] > 0) {
          vbWidth = parts[2];
          vbHeight = parts[3];
        }
      }
      const stagePadding = 40;
      const maxW = window.innerWidth * 0.9 - stagePadding;
      const maxH = window.innerHeight * 0.78 - stagePadding;
      const fitScale = Math.min(maxW / vbWidth, maxH / vbHeight, 1);
      baseWidth = Math.round(vbWidth * fitScale);
      baseHeight = Math.round(vbHeight * fitScale);
      clone.style.width = baseWidth + "px";
      clone.style.height = baseHeight + "px";

      stage.appendChild(clone);
      currentSvg = clone;
      resetView();
      lastTrigger = trigger || null;
      overlay.classList.add("open");
      overlay.setAttribute("aria-hidden", "false");
      document.body.style.overflow = "hidden";
      closeBtn.focus();
    }
    function closeLightbox() {
      overlay.classList.remove("open");
      overlay.setAttribute("aria-hidden", "true");
      document.body.style.overflow = "";
      // Return the caret to the diagram the reader opened, not to the top of the document.
      if (lastTrigger && typeof lastTrigger.focus === "function") lastTrigger.focus();
      lastTrigger = null;
    }

    document.querySelectorAll(".mermaid-wrap, .diagram-wrap").forEach((wrap) => {
      wrap.classList.add("zoomable");
      const open = () => {
        const svg = wrap.querySelector("svg");
        if (svg) openLightbox(svg, wrap);
      };
      wrap.addEventListener("click", open);
      wrap.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " " || e.key === "Spacebar") {
          e.preventDefault();
          open();
        }
      });
    });

    closeBtn.addEventListener("click", closeLightbox);
    resetBtn.addEventListener("click", resetView);
    zoomInBtn.addEventListener("click", () => zoomBy(1.3));
    zoomOutBtn.addEventListener("click", () => zoomBy(1 / 1.3));

    document.addEventListener("keydown", (e) => {
      if (!overlay.classList.contains("open")) return;
      if (e.key === "Escape") {
        closeLightbox();
        return;
      }
      // Focus trap: Tab cycles the four toolbar buttons and never escapes back to the page
      // behind the modal.
      if (e.key === "Tab") {
        const focusable = [zoomOutBtn, resetBtn, zoomInBtn, closeBtn];
        const index = focusable.indexOf(document.activeElement);
        const next = e.shiftKey
          ? (index <= 0 ? focusable.length - 1 : index - 1)
          : (index === -1 || index === focusable.length - 1 ? 0 : index + 1);
        e.preventDefault();
        focusable[next].focus();
      }
    });

    viewport.addEventListener("click", (e) => {
      if (!moved && e.target === viewport) closeLightbox();
    });

    viewport.addEventListener("wheel", (e) => {
      e.preventDefault();
      zoomBy(e.deltaY < 0 ? 1.12 : 1 / 1.12);
    }, { passive: false });

    viewport.addEventListener("dblclick", resetView);

    viewport.addEventListener("pointerdown", (e) => {
      dragging = true; moved = false;
      startX = e.clientX; startY = e.clientY;
      startPanX = panX; startPanY = panY;
      viewport.classList.add("grabbing");
      viewport.setPointerCapture(e.pointerId);
    });
    viewport.addEventListener("pointermove", (e) => {
      if (!dragging) return;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      if (Math.abs(dx) > 3 || Math.abs(dy) > 3) moved = true;
      panX = startPanX + dx;
      panY = startPanY + dy;
      apply();
    });
    viewport.addEventListener("pointerup", () => {
      dragging = false;
      viewport.classList.remove("grabbing");
    });
  }
`;

/**
 * Assembles one complete standalone page.
 *
 * @param {object} page
 * @param {string} page.title      contents of `<title>`
 * @param {string} page.header     the `<h1>`, plus the subtitle/doc-nav when the source had one
 * @param {string} page.body       the compiled Markdown
 * @param {boolean} page.hasDiagrams whether the mermaid renderer needs loading at all
 */
export function renderPage({ title, header, body, hasDiagrams }) {
  const mermaidScript = hasDiagrams ? `<script src="${MERMAID_CDN}"></script>\n` : '';
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta name="generator" content="util/md-to-html.js">
<title>${title}</title>
<style>${STYLES}</style>
</head>
<body>
<a class="skip-link" href="#doc-content">Skip to content</a>
${header}
<main id="doc-content">
${body}</main>

${LIGHTBOX_MARKUP}

${mermaidScript}<script>${SCRIPT}</script>

</body>
</html>
`;
}
