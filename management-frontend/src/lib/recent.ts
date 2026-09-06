'use client';

import { useEffect, useState } from 'react';

/**
 * The records this person opened recently, kept for the length of the session.
 *
 * Everything in this system is addressed by a business key, and the keys are not memorable —
 * `BS7-14-ir7pui` is a perfectly good identifier and a terrible thing to hold in your head between
 * two screens. Staff work by returning to the same handful of records repeatedly, and until now the
 * only way back to one was to search for it again.
 *
 * Kept in `sessionStorage`, beside the session itself (`lib/auth/AuthContext`), for two reasons:
 * it dies with the session, so a shared machine does not leak one user's worklist to the next; and
 * it never travels to the server, so it cannot become a second, unauthorised index of who looked at
 * whom.
 */
const KEY = 'management.recent';
const LIMIT = 6;

export interface RecentRecord {
  /** The business key — what the record actually is. */
  code: string;
  /** Human label, e.g. a student's name or a course title. */
  label: string;
  href: string;
  kind: 'student' | 'course' | 'book';
}

function read(): RecentRecord[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.sessionStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as RecentRecord[]) : [];
  } catch {
    // A malformed or unavailable store is not worth an error path: this is a convenience.
    return [];
  }
}

/** Notifies hooks in this tab; `storage` only fires in *other* tabs, so it cannot do this job. */
const CHANGED = 'management.recent.changed';

export function remember(record: RecentRecord) {
  if (typeof window === 'undefined') return;
  const next = [record, ...read().filter((r) => r.href !== record.href)].slice(0, LIMIT);
  try {
    window.sessionStorage.setItem(KEY, JSON.stringify(next));
    window.dispatchEvent(new Event(CHANGED));
  } catch {
    /* Storage full or blocked — the feature simply does nothing. */
  }
}

export function clearRecent() {
  if (typeof window === 'undefined') return;
  window.sessionStorage.removeItem(KEY);
  window.dispatchEvent(new Event(CHANGED));
}

/**
 * Read the list reactively.
 *
 * Starts empty and fills in an effect rather than reading during render — the same rule
 * `AuthContext` follows, and for the same reason: the server has no `sessionStorage`, so reading it
 * while rendering produces markup the client immediately contradicts.
 */
export function useRecentRecords(): RecentRecord[] {
  const [records, setRecords] = useState<RecentRecord[]>([]);

  useEffect(() => {
    const sync = () => setRecords(read());
    sync();
    window.addEventListener(CHANGED, sync);
    return () => window.removeEventListener(CHANGED, sync);
  }, []);

  return records;
}
