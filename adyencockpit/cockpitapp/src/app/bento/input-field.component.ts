import { ChangeDetectionStrategy, Component, computed, forwardRef, input } from '@angular/core';
import { NG_VALUE_ACCESSOR } from '@angular/forms';

import { BFormControl, uniqueId } from './form-control';
import { BIcon } from './icon.component';

@Component({
  selector: 'b-input-field',
  imports: [BIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => BInputField), multi: true }],
  host: {
    class: 'b-typography b-input-field b-typography--body',
    '[class.b-input-field--condensed]': 'condensed()',
    '[class.b-input-field--error]': '!!error()',
    '[class.b-input-field--disabled]': 'isDisabled()',
    '[class.b-input-field--readonly]': 'readonly()',
    '[class.b-input-field--mono]': 'mono()',
  },
  template: `
    @if (label()) {
      <label class="b-field-label b-typography--body-stronger" [attr.for]="inputId">
        {{ label() }}
        @if (requirement()) {
          <span class="b-field-label__requirement">{{ requirement() === 'optional' ? 'Optional' : 'Required' }}</span>
        }
      </label>
    }
    <div class="b-input-field__input-box">
      @if (icon()) {
        <span class="b-input-field__icon-before"><b-icon [name]="icon()!" /></span>
      }
      @if (prefix()) {
        <span class="b-input-field__static-value b-input-field__static-value--start">{{ prefix() }}</span>
      }
      <input class="b-input-field__input"
             [id]="inputId"
             [type]="type()"
             [attr.placeholder]="placeholder()"
             [attr.autocomplete]="autocomplete()"
             [attr.aria-invalid]="!!error() || null"
             [attr.aria-describedby]="describedBy()"
             [attr.aria-required]="requirement() === 'required' || null"
             [readonly]="readonly()"
             [disabled]="isDisabled()"
             [value]="value()"
             (input)="update($any($event.target).value)"
             (blur)="touch()" />
      @if (suffix()) {
        <span class="b-input-field__static-value b-input-field__static-value--end">{{ suffix() }}</span>
      }
    </div>
    @if (error()) {
      <div class="b-error-message b-typography--caption" [id]="errorId">
        <b-icon name="critical" [size]="14" />{{ error() }}
      </div>
    } @else if (description() || hint()) {
      <div class="b-input-field__footer b-typography--caption">
        <span class="b-input-field__description" [id]="descriptionId">{{ description() }}</span>
        @if (hint()) {
          <span class="b-input-field__hint">{{ hint() }}</span>
        }
      </div>
    }
  `,
})
export class BInputField extends BFormControl<string> {
  readonly label = input<string | null>(null);
  readonly requirement = input<'required' | 'optional' | null>(null);
  readonly description = input<string | null>(null);
  readonly hint = input<string | null>(null);
  readonly error = input<string | null>(null);
  readonly type = input<'text' | 'password' | 'email' | 'url' | 'search' | 'number'>('text');
  readonly placeholder = input<string | null>(null);
  readonly autocomplete = input<string | null>(null);
  readonly icon = input<string | null>(null);
  readonly prefix = input<string | null>(null);
  readonly suffix = input<string | null>(null);
  readonly condensed = input(false);
  readonly readonly = input(false);
  readonly mono = input(false);
  readonly disabled = input(false);

  protected readonly inputId = uniqueId('b-input');
  protected readonly descriptionId = `${this.inputId}-description`;
  protected readonly errorId = `${this.inputId}-error`;

  protected readonly isDisabled = computed(() => this.disabled() || this.formDisabled());
  protected readonly describedBy = computed(() => {
    if (this.error()) {
      return this.errorId;
    }
    return this.description() ? this.descriptionId : null;
  });

  protected emptyValue(): string {
    return '';
  }
}
