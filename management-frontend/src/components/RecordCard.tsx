'use client';

import { Card, DataList } from '@chakra-ui/react';
import type { ReactNode } from 'react';

export interface RecordField {
  label: string;
  value: ReactNode;
}

/** The definition-list card every detail screen opens with. */
export default function RecordCard({
  title,
  fields,
  actions,
}: {
  title?: string;
  fields: RecordField[];
  actions?: ReactNode;
}) {
  return (
    <Card.Root>
      {title || actions ? (
        <Card.Header
          display="flex"
          flexDirection="row"
          justifyContent="space-between"
          alignItems="center"
          gap="3"
        >
          {title ? <Card.Title>{title}</Card.Title> : <span />}
          {actions}
        </Card.Header>
      ) : null}
      <Card.Body>
        <DataList.Root orientation="horizontal" size="sm">
          {fields.map((field) => (
            <DataList.Item key={field.label}>
              <DataList.ItemLabel minW="10rem">{field.label}</DataList.ItemLabel>
              <DataList.ItemValue>{field.value ?? '—'}</DataList.ItemValue>
            </DataList.Item>
          ))}
        </DataList.Root>
      </Card.Body>
    </Card.Root>
  );
}
