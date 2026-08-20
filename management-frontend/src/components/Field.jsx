import { useId } from 'react';

/**
 * Labelled input with inline validation.
 *
 * `error` is the string from ApiError.fieldError(name) -- the `field` values in the backend's
 * ValidationError.errors[] are request-DTO property names, so passing the same `name` through
 * lines them up with no mapping table.
 */
export default function Field({
  label,
  name,
  value,
  onChange,
  type = 'text',
  error,
  hint,
  required = false,
  autoFocus = false,
  disabled = false,
  placeholder,
  children,
}) {
  const id = useId();

  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>

      {children ?? (
        <input
          id={id}
          className={`field__input${error ? ' field__input--invalid' : ''}`}
          name={name}
          type={type}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          autoFocus={autoFocus}
          disabled={disabled}
          placeholder={placeholder}
          aria-invalid={Boolean(error)}
        />
      )}

      {hint && !error && <span className="field__hint">{hint}</span>}
      {error && <span className="field__error">{error}</span>}
    </div>
  );
}
