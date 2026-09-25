import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { BIcon } from './icon.component';

export type BAlertVariant = 'highlight' | 'success' | 'warning' | 'critical';

/**
 * The description is the projected content; actions go in an element marked [bAlertActions].
 * Critical alerts announce assertively, the rest politely.
 */
@Component({
  selector: 'b-alert',
  imports: [BIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': 'hostClass()',
    '[attr.role]': "variant() === 'critical' ? 'alert' : 'status'",
  },
  template: `
    <div class="b-alert__inner-container">
      <div class="b-alert__icon"><b-icon [name]="iconName()" /></div>
      <div class="b-alert__content">
        @if (title()) {
          <div class="b-typography b-alert__title b-typography--body b-typography--body-wide b-typography--body-strongest">{{ title() }}</div>
        }
        <div class="b-typography b-alert__description b-typography--body"><ng-content /></div>
        <div class="b-alert__actions"><ng-content select="[bAlertActions]" /></div>
      </div>
      @if (dismissible()) {
        <button class="b-alert__close-button" type="button" aria-label="Dismiss" (click)="dismissed.emit()">
          <b-icon name="close" />
        </button>
      }
    </div>
  `,
  styles: [':host .b-alert__actions:empty { display: none; }'],
})
export class BAlert {
  readonly variant = input<BAlertVariant>('highlight');
  readonly title = input<string | null>(null);
  readonly dismissible = input(false);
  readonly dismissed = output<void>();

  protected readonly hostClass = computed(() => `b-alert b-alert--${this.variant()}`);
  protected readonly iconName = computed(() => {
    switch (this.variant()) {
      case 'success': return 'success';
      case 'warning': return 'warning';
      case 'critical': return 'critical';
      default: return 'info';
    }
  });
}
