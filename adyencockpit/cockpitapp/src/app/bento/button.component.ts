import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { BIcon } from './icon.component';

export type BButtonVariant =
  | 'primary'
  | 'secondary'
  | 'tertiary'
  | 'tertiary-with-background'
  | 'primary-critical'
  | 'secondary-critical'
  | 'tertiary-critical';

/**
 * Applied to a native button or link rather than wrapping one, so type, disabled, form submission
 * and keyboard behaviour stay the platform's.
 */
@Component({
  selector: 'button[bButton], a[bButton]',
  imports: [BIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': 'hostClass()',
    '[class.b-button--condensed]': 'condensed()',
    '[class.b-button--icon-only]': 'iconOnly()',
    '[class.b-button--loading]': 'loading()',
    '[attr.aria-busy]': 'loading() || null',
  },
  template: `
    @if (loading()) {
      <span class="b-button__icon"><span class="b-spinner"></span></span>
    } @else if (icon()) {
      <span class="b-button__icon"><b-icon [name]="icon()!" /></span>
    }
    @if (!iconOnly()) {
      <span class="b-typography b-button__label b-typography--body b-typography--body-stronger">
        <ng-content />
      </span>
    }
  `,
})
export class BButton {
  readonly variant = input<BButtonVariant>('primary');
  readonly condensed = input(false);
  readonly iconOnly = input(false);
  readonly icon = input<string | null>(null);
  readonly loading = input(false);

  protected readonly hostClass = computed(() => `b-button b-button--${this.variant()}`);
}
