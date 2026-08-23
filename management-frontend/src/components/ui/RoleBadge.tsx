'use client';

import { Badge } from '@chakra-ui/react';

import type { Role } from '@/lib/api/types';
import { ROLE_LABELS } from '@/lib/auth/permissions';

/**
 * A role, shown in one neutral style.
 *
 * This replaces the old `ROLE_COLORS` map, which gave each of the five roles its own hue. That was
 * five competing accents in a palette §5 caps at one, and colour was carrying information the label
 * already spells out — "Registrar" is not more legible for being blue. The badge stays because the
 * role is worth marking; the hue goes because it was decoration.
 */
export default function RoleBadge({ role }: { role: Role }) {
  return (
    <Badge variant="outline" colorPalette="gray" letterSpacing="0.02em">
      {ROLE_LABELS[role]}
    </Badge>
  );
}
