'use client';

import { Input, InputGroup } from '@chakra-ui/react';

/** Debounced by `usePagedResource`, so this stays a plain controlled input. */
export default function SearchInput({
  value,
  onChange,
  placeholder = 'Search…',
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}) {
  return (
    <InputGroup maxW="sm" mb="4">
      <Input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        size="sm"
      />
    </InputGroup>
  );
}
