import { signal } from '@angular/core';
import { ControlValueAccessor } from '@angular/forms';

let nextId = 0;

/** Ids that tie a label, its control and its help text together for assistive technology. */
export function uniqueId(prefix: string): string {
  nextId += 1;
  return `${prefix}-${nextId}`;
}

/**
 * The plumbing every Bento form control shares with Angular Forms. Values and the disabled flag live
 * in signals, so the controls render correctly without zone-based change detection.
 */
export abstract class BFormControl<T> implements ControlValueAccessor {
  protected readonly value = signal<T>(this.emptyValue());
  protected readonly formDisabled = signal(false);

  private onChange: (value: T) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  protected abstract emptyValue(): T;

  writeValue(value: T | null | undefined): void {
    this.value.set(value ?? this.emptyValue());
  }

  registerOnChange(fn: (value: T) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.formDisabled.set(disabled);
  }

  protected update(value: T): void {
    this.value.set(value);
    this.onChange(value);
  }

  protected touch(): void {
    this.onTouched();
  }
}
