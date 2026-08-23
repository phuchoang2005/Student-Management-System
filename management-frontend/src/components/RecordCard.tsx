'use client';

import { DataList } from '@chakra-ui/react';
import type { ReactNode } from 'react';

import SurfaceCard from '@/components/ui/SurfaceCard';

export interface RecordField {
  label: string;
  value: ReactNode;
}

/**
 * The definition-list card every detail screen opens with. Built on `SurfaceCard` so its border,
 * radius and padding are the same 12px/1px/24px as every other card in the app (§10).
 */
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
    <SurfaceCard title={title} actions={actions}>
      <DataList.Root orientation="horizontal" size="md" gap="4">
        {fields.map((field) => (
          <DataList.Item key={field.label}>
            <DataList.ItemLabel minW="11rem">{field.label}</DataList.ItemLabel>
            <DataList.ItemValue>{field.value ?? '—'}</DataList.ItemValue>
          </DataList.Item>
        ))}
      </DataList.Root>
    </SurfaceCard>
  );
}
