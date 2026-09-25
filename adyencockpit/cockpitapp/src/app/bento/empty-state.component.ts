import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { BIcon } from './icon.component';

/** Customer Area's pattern: a short noun-phrase title, then one sentence saying what to do. */
@Component({
  selector: 'b-empty-state',
  imports: [BIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'b-empty-state' },
  template: `
    @if (icon()) {
      <div class="b-empty-state__icon"><b-icon [name]="icon()!" [size]="32" /></div>
    }
    <p class="b-typography b-empty-state__title b-typography--title">{{ title() }}</p>
    @if (details()) {
      <p class="b-typography b-empty-state__details b-typography--body">{{ details() }}</p>
    }
    <ng-content />
  `,
})
export class BEmptyState {
  readonly title = input.required<string>();
  readonly details = input<string | null>(null);
  readonly icon = input<string | null>(null);
}
