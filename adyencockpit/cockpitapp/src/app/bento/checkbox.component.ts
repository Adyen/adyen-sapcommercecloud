import { ChangeDetectionStrategy, Component, computed, forwardRef, input } from '@angular/core';
import { NG_VALUE_ACCESSOR } from '@angular/forms';

import { BFormControl } from './form-control';

@Component({
  selector: 'b-checkbox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => BCheckbox), multi: true }],
  host: { style: 'display: inline-flex;' },
  template: `
    <label class="b-checkbox b-typography b-typography--body" [class.b-checkbox--disabled]="isDisabled()">
      <input class="b-checkbox__outline" type="checkbox"
             [checked]="value()"
             [disabled]="isDisabled()"
             (change)="update($any($event.target).checked)"
             (blur)="touch()" />
      <span class="b-checkbox__text">
        <span class="b-typography--body-stronger"><ng-content /></span>
        @if (description()) {
          <span class="b-checkbox__description b-typography--caption">{{ description() }}</span>
        }
      </span>
    </label>
  `,
})
export class BCheckbox extends BFormControl<boolean> {
  readonly description = input<string | null>(null);
  readonly disabled = input(false);

  protected readonly isDisabled = computed(() => this.disabled() || this.formDisabled());

  protected emptyValue(): boolean {
    return false;
  }
}
