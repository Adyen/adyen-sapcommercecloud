import { ChangeDetectionStrategy, Component, computed, forwardRef, input } from '@angular/core';
import { NG_VALUE_ACCESSOR } from '@angular/forms';

import { BFormControl, uniqueId } from './form-control';
import { BIcon } from './icon.component';

export interface BSelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

@Component({
  selector: 'b-select',
  imports: [BIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => BSelect), multi: true }],
  host: { class: 'b-typography b-typography--body', style: 'display: flex; flex-direction: column;' },
  template: `
    @if (label()) {
      <label class="b-field-label b-typography--body-stronger" [attr.for]="selectId">{{ label() }}</label>
    }
    <div class="b-select" [class.b-select--error]="!!error()">
      <select class="b-select__control"
              [id]="selectId"
              [disabled]="isDisabled()"
              [attr.aria-invalid]="!!error() || null"
              [attr.aria-describedby]="error() ? errorId : description() ? descriptionId : null"
              (change)="update($any($event.target).value)"
              (blur)="touch()">
        @if (placeholder()) {
          <option value="" disabled [selected]="!hasKnownValue()">{{ placeholder() }}</option>
        }
        @for (option of options(); track option.value) {
          <option [value]="option.value" [disabled]="!!option.disabled" [selected]="option.value === value()">
            {{ option.label }}
          </option>
        }
      </select>
      <span class="b-select__chevron"><b-icon name="chevron-down" /></span>
    </div>
    @if (error()) {
      <div class="b-error-message b-typography--caption" [id]="errorId">
        <b-icon name="critical" [size]="14" />{{ error() }}
      </div>
    } @else if (description()) {
      <div class="b-input-field__footer b-typography--caption">
        <span class="b-input-field__description" [id]="descriptionId">{{ description() }}</span>
      </div>
    }
  `,
})
export class BSelect extends BFormControl<string> {
  readonly label = input<string | null>(null);
  readonly options = input<BSelectOption[]>([]);
  readonly placeholder = input<string | null>(null);
  readonly description = input<string | null>(null);
  readonly error = input<string | null>(null);
  readonly disabled = input(false);

  protected readonly selectId = uniqueId('b-select');
  protected readonly descriptionId = `${this.selectId}-description`;
  protected readonly errorId = `${this.selectId}-error`;
  protected readonly isDisabled = computed(() => this.disabled() || this.formDisabled());
  protected readonly hasKnownValue = computed(() => this.options().some((option) => option.value === this.value()));

  protected emptyValue(): string {
    return '';
  }
}
