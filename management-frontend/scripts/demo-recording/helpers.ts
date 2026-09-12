import { expect, type Page } from '@playwright/test';

import { ROLE_LABELS } from '../../src/lib/auth/permissions';
import type { Role } from '../../src/lib/api/types';

/**
 * Signs in through the login page's demo-account board rather than filling the form directly, so
 * the recording shows the board itself — it's a distinctive part of this app's login screen.
 */
export async function loginAs(page: Page, role: Role) {
  await page.goto('/login');
  const entry = page.getByRole('listitem').filter({ hasText: ROLE_LABELS[role] });
  await entry.getByRole('button', { name: 'Use this role' }).click();
  await page.getByRole('button', { name: 'Sign in' }).click();
  // The password hasher's first invocation after a cold backend start can take several seconds
  // (JIT warm-up), well past Playwright's 5s assertion default.
  await expect(page).toHaveURL(/\/home$/, { timeout: 20_000 });
}

/**
 * The sidebar's own link for a section — scoped to the `nav aria-label="Sections"` landmark,
 * since the Home page also renders a quick-link card per section with the same accessible name.
 */
export async function navTo(page: Page, label: string) {
  await page.getByRole('navigation', { name: 'Sections' }).getByRole('link', { name: label, exact: true }).click();
  // Turbopack dev compiles each route lazily on first visit and hot-swaps it in, which can wipe out
  // an interaction (a filled field, a fresh component instance) that lands right after navigation —
  // wait for the network to settle, not just a fixed pause, since first-visit compile time varies.
  await page.waitForLoadState('networkidle', { timeout: 8_000 }).catch(() => {});
  await beat(page, 800);
}

/** The rail's "Sign out" action — used between roles since auth is one shared session cookie. */
export async function logout(page: Page) {
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);
}

/** A pause purely for the viewer's benefit — never the sole wait for an async UI update. */
export async function beat(page: Page, ms = 900) {
  await page.waitForTimeout(ms);
}
