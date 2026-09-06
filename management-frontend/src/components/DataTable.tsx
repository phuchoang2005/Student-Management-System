'use client';

import { Box, Stack, Table, Text } from '@chakra-ui/react';
import { Fragment, type ReactNode } from 'react';

import FadeIn from '@/components/motion/FadeIn';
import TableSkeleton from '@/components/ui/TableSkeleton';

export interface Column<T> {
  key: string;
  header: string;
  /** Cell renderer. Given the row, returns whatever should sit in the `<td>`. */
  cell: (row: T) => ReactNode;
  width?: string;
  align?: 'start' | 'center' | 'end';
  /**
   * How this column behaves when the table stacks below `md`.
   *
   * `'primary'` and `'secondary'` are the two fields that lead each stacked record — in this app
   * almost always the business key and the name, since that is the pair someone scans for. Columns
   * with no priority become labelled rows underneath. `'actions'` drops to the foot of the record
   * and loses its (empty) header, which is where a row's buttons belong once there is no row.
   */
  priority?: 'primary' | 'secondary' | 'actions';
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
 * ## Two changes worth knowing
 *
 * **The card is gone.** A table used to sit inside a bordered white panel on a near-white page,
 * which on a wide screen produced a two-row ribbon of data floating on a large empty slab — the
 * frame ended up more prominent than the rows. A list is not a bounded object; it is the page's
 * content. It now sits on the page ground with a rule under the header and under each row, which
 * is the ledger this data actually is. `SurfaceCard` is still the right answer for a single record
 * or a form, and it kept its border.
 *
 * **It stacks below `md`.** A five-column table on a 390px screen is a horizontal scroll that hides
 * whichever column matters. Under the breakpoint each row is re-laid out as a record: the two
 * `priority` fields lead, everything else becomes a labelled pair, and the actions sit at the foot.
 * The same `columns` array drives both — there is no second definition to keep in sync.
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
  pack = false,
}: {
  columns: Column<T>[];
  rows: T[];
  keyOf: (row: T) => string;
  loading?: boolean;
  /**
   * Keep the columns together at the left and let the leftover width sit at the right edge.
   *
   * A table whose columns are all short — code, name, credits, a count — otherwise stretches its
   * one unsized column across the whole measure, which parks the figures a thousand pixels from
   * the row they belong to and makes "how many credits is CRS00030" a journey. Packing moves that
   * emptiness to the outside of the data, where it reads as margin instead of as a gap.
   *
   * Requires every column to declare a `width`; the spacer is what absorbs the remainder. It is
   * inserted *before* any `priority: 'actions'` column, so row buttons stay pinned to the right
   * edge where they were rather than being dragged into the middle of the table.
   */
  pack?: boolean;
  /** An `EmptyState` (§13). Anything renderable works, but a bare string is no longer the norm. */
  empty?: ReactNode;
  onRowClick?: (row: T) => void;
}) {
  if (loading) {
    return <TableSkeleton columns={columns.length} />;
  }

  if (rows.length === 0) {
    return <FadeIn>{empty}</FadeIn>;
  }

  const activate = (row: T) => onRowClick?.(row);

  /** Enter and Space reach the same drill-down a click does, on a row or on a stacked record. */
  const keyHandler = onRowClick
    ? (row: T) => (event: React.KeyboardEvent) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          activate(row);
        }
      }
    : undefined;

  /** With `pack`, the spacer goes before the row buttons so they stay at the right edge. */
  const spacerAt = pack
    ? columns.findIndex((c) => c.priority === 'actions') === -1
      ? columns.length
      : columns.findIndex((c) => c.priority === 'actions')
    : -1;

  const primary = columns.find((c) => c.priority === 'primary');
  const secondary = columns.find((c) => c.priority === 'secondary');
  const actions = columns.filter((c) => c.priority === 'actions');
  const rest = columns.filter(
    (c) => c !== primary && c !== secondary && !actions.includes(c) && c.header,
  );

  return (
    <FadeIn>
      {/* Wide: the ledger. */}
      <Table.ScrollArea hideBelow="md" borderTopWidth="1px" borderColor="border">
        <Table.Root size="md" interactive={!!onRowClick}>
          <Table.Header>
            <Table.Row>
              {columns.map((column, index) => (
                <Fragment key={column.key}>
                  {index === spacerAt ? <Table.ColumnHeader width="100%" aria-hidden /> : null}
                  <Table.ColumnHeader width={column.width} textAlign={column.align ?? 'start'}>
                    {column.header}
                  </Table.ColumnHeader>
                </Fragment>
              ))}
              {spacerAt === columns.length ? <Table.ColumnHeader width="100%" aria-hidden /> : null}
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {rows.map((row) => (
              <Table.Row
                key={keyOf(row)}
                onClick={onRowClick ? () => activate(row) : undefined}
                cursor={onRowClick ? 'pointer' : undefined}
                tabIndex={onRowClick ? 0 : undefined}
                onKeyDown={keyHandler?.(row)}
              >
                {columns.map((column, index) => (
                  <Fragment key={column.key}>
                    {index === spacerAt ? <Table.Cell aria-hidden /> : null}
                    <Table.Cell textAlign={column.align ?? 'start'}>{column.cell(row)}</Table.Cell>
                  </Fragment>
                ))}
                {spacerAt === columns.length ? <Table.Cell aria-hidden /> : null}
              </Table.Row>
            ))}
          </Table.Body>
        </Table.Root>
      </Table.ScrollArea>

      {/* Narrow: one record per block. */}
      <Stack hideFrom="md" gap="0" borderTopWidth="1px" borderColor="border">
        {rows.map((row) => (
          <Stack
            key={keyOf(row)}
            gap="3"
            py="4"
            borderBottomWidth="1px"
            borderColor="border.muted"
            onClick={onRowClick ? () => activate(row) : undefined}
            cursor={onRowClick ? 'pointer' : undefined}
            tabIndex={onRowClick ? 0 : undefined}
            onKeyDown={keyHandler?.(row)}
            role={onRowClick ? 'button' : undefined}
            _focusVisible={{ outlineWidth: '2px', outlineStyle: 'solid', outlineOffset: '2px' }}
          >
            {primary || secondary ? (
              <Stack gap="1">
                {primary ? <Box>{primary.cell(row)}</Box> : null}
                {secondary ? <Box>{secondary.cell(row)}</Box> : null}
              </Stack>
            ) : null}

            {rest.length > 0 ? (
              <Stack gap="2">
                {rest.map((column) => (
                  <Box key={column.key}>
                    <Text textStyle="meta" color="fg.muted">
                      {column.header}
                    </Text>
                    <Box textStyle="body">{column.cell(row)}</Box>
                  </Box>
                ))}
              </Stack>
            ) : null}

            {actions.length > 0 ? (
              <Box
                onClick={(event) => event.stopPropagation()}
                // The cell renderer right-aligns its buttons for the wide table, where they sit in
                // a narrow trailing column. Stacked, that leaves them marooned across the width of
                // the record, so the alignment is unwound here rather than duplicated per page.
                css={{ '& > * > *': { justifyContent: 'flex-start' } }}
              >
                {actions.map((column) => (
                  <Box key={column.key}>{column.cell(row)}</Box>
                ))}
              </Box>
            ) : null}
          </Stack>
        ))}
      </Stack>
    </FadeIn>
  );
}
