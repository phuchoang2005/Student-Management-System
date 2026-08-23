'use client';

import { Card } from '@chakra-ui/react';
import type { ComponentProps, ReactNode } from 'react';

/**
 * The one card: 12px radius, a 1px border, no shadow, 24px of padding (§6).
 *
 * Every screen used to hand-compose `Card.Root`/`Card.Body`/`Card.Footer`, which is how the login
 * screen ended up with different padding from the enrollments screen. The slots are still available
 * for the rare layout that needs them, but nothing in `src/app` reaches for them any more.
 *
 * "Cards should separate information without dominating attention" — so there is no elevation, no
 * hover state, and no variant. A card here is a boundary, not an object.
 */
export default function SurfaceCard({
  title,
  description,
  actions,
  footer,
  children,
  ...rest
}: {
  title?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  footer?: ReactNode;
  children: ReactNode;
} & Omit<ComponentProps<typeof Card.Root>, 'title'>) {
  return (
    <Card.Root {...rest}>
      {title || actions ? (
        <Card.Header
          display="flex"
          flexDirection="row"
          justifyContent="space-between"
          alignItems="flex-start"
          gap="4"
        >
          <div>
            {title ? <Card.Title>{title}</Card.Title> : null}
            {description ? <Card.Description>{description}</Card.Description> : null}
          </div>
          {actions}
        </Card.Header>
      ) : null}
      <Card.Body>{children}</Card.Body>
      {footer ? <Card.Footer>{footer}</Card.Footer> : null}
    </Card.Root>
  );
}
