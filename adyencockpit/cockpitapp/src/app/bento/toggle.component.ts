import { ChangeDetectionStrategy, Component, computed, forwardRef, input } from '@angular/core';
import { NG_VALUE_ACCESSOR } from '@angular/forms';

import { BFormControl } from './form-control';

@Component({
  selector: 'b-toggle',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => BToggle), multi: true }],
  host: { style: 'display: inline-flex;' },
  template: `
    <label class="b-toggle b-typography b-typography--body" [class.b-toggle--disabled]="isDisabled()">
      <input class="b-toggle__input" type="checkbox" role="switch"
             [checked]="value()"
             [disabled]="isDisabled()"
             (change)="update($any($event.target).checked)"
             (blur)="touch()" />
      <span class="b-toggle__track"><span class="b-toggle__handle"></span></span>
      <span class="b-toggle__text">
        <span class="b-typography--body-stronger"><ng-content /></span>
        @if (description()) {
          <span class="b-toggle__description b-typography--caption">{{ description() }}</span>
        }
      </span>
    </label>
  `,
})
export class BToggle extends BFormControl<boolean> {
  readonly description = input<string | null>(null);
  readonly disabled = input(false);

  protected readonly isDisabled = computed(() => this.disabled() || this.formDisabled());

  protected emptyValue(): boolean {
    return false;
  }
}
