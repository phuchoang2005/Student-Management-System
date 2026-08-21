'use client';

import { Center, Spinner, Table, Text } from '@chakra-ui/react';
import type { ReactNode } from 'react';

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
 */
export default function DataTable<T>({
  columns,
  rows,
  keyOf,
  loading,
  empty = 'Nothing to show.',
  onRowClick,
}: {
  columns: Column<T>[];
  rows: T[];
  keyOf: (row: T) => string;
  loading?: boolean;
  empty?: ReactNode;
  onRowClick?: (row: T) => void;
}) {
  if (loading) {
    return (
      <Center py="10">
        <Spinner />
      </Center>
    );
  }

  if (rows.length === 0) {
    return (
      <Center py="10" borderWidth="1px" borderRadius="md" bg="bg.panel">
        <Text color="fg.muted" fontSize="sm">
          {empty}
        </Text>
      </Center>
    );
  }

  return (
    <Table.ScrollArea borderWidth="1px" borderRadius="md">
      <Table.Root size="sm" interactive={!!onRowClick} striped>
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
  );
}
