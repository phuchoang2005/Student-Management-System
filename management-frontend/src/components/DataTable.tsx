'use client';

import { Center, Spinner, Table } from '@chakra-ui/react';
import type { ReactNode } from 'react';

import FadeIn from '@/components/motion/FadeIn';

export interface Column<T> {
  key: string;
  header: string;
  /** Cell renderer. Given the row, returns whatever should sit in the `<td>`. */
  cell: (row: T) => ReactNode;
  width?: string;
  align?: 'start' | 'center' | 'end';
}

/**
 * The one table every list screen uses. Rows are optionally clickable — which is how every
 * drill-down in this app works (student → their courses, course → its roster, roster → a profile),
 * so the affordance is built in rather than repeated per screen.
 *
 * §12 shapes the density: ~52px rows, 20px of horizontal padding, and hover highlighting as the only
 * row treatment. `striped` is gone — zebra shading is a second, competing way of separating rows
 * when the spacing already does it, and it makes long sessions noisier rather than easier to scan.
 *
 * The table fades in as one object rather than staggering its rows: a table is scanned, not read in
 * sequence, and a per-row ripple is exactly the motion §8 rules out.
 */
export default function DataTable<T>({
  columns,
  rows,
  keyOf,
  loading,
  empty,
  onRowClick,
}: {
  columns: Column<T>[];
  rows: T[];
  keyOf: (row: T) => string;
  loading?: boolean;
  /** An `EmptyState` (§13). Anything renderable works, but a bare string is no longer the norm. */
  empty?: ReactNode;
  onRowClick?: (row: T) => void;
}) {
  if (loading) {
    return (
      <Center py="16">
        <Spinner size="md" color="fg.subtle" borderWidth="1.5px" />
      </Center>
    );
  }

  if (rows.length === 0) {
    return <FadeIn>{empty}</FadeIn>;
  }

  return (
    <FadeIn>
      <Table.ScrollArea borderWidth="1px" borderColor="border" borderRadius="l3" bg="bg.panel">
        <Table.Root size="md" interactive={!!onRowClick}>
          <Table.Header>
            <Table.Row>
              {columns.map((column) => (
                <Table.ColumnHeader
                  key={column.key}
                  width={column.width}
                  textAlign={column.align ?? 'start'}
                >
                  {column.header}
                </Table.ColumnHeader>
              ))}
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {rows.map((row) => (
              <Table.Row
                key={keyOf(row)}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                cursor={onRowClick ? 'pointer' : undefined}
                // Keyboard parity for the click-to-drill-down affordance above.
                tabIndex={onRowClick ? 0 : undefined}
                onKeyDown={
                  onRowClick
                    ? (event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          onRowClick(row);
                        }
                      }
                    : undefined
                }
              >
                {columns.map((column) => (
                  <Table.Cell key={column.key} textAlign={column.align ?? 'start'}>
                    {column.cell(row)}
                  </Table.Cell>
                ))}
              </Table.Row>
            ))}
          </Table.Body>
        </Table.Root>
      </Table.ScrollArea>
    </FadeIn>
  );
}
