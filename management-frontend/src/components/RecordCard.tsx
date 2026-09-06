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
  orientation = 'vertical',
}: {
  title?: string;
  fields: RecordField[];
  actions?: ReactNode;
  /**
   * `vertical` stacks each label above its value. It is the default because a record now sits in a
   * ~21rem identity column (`DetailLayout`), and an 11rem label gutter beside a value leaves almost
   * nothing for the value. Stacking also removes the long empty run between a short label and its
   * value that the full-width horizontal list used to open up.
   */
  orientation?: 'horizontal' | 'vertical';
}) {
  const vertical = orientation === 'vertical';

  return (
    <SurfaceCard title={title} actions={actions}>
      <DataList.Root orientation={orientation} size="md" gap={vertical ? '5' : '4'}>
        {fields.map((field) => (
          <DataList.Item key={field.label}>
            <DataList.ItemLabel minW={vertical ? undefined : '11rem'}>
              {field.label}
            </DataList.ItemLabel>
            <DataList.ItemValue>{field.value ?? '—'}</DataList.ItemValue>
          </DataList.Item>
        ))}
      </DataList.Root>
    </SurfaceCard>
  );
}
