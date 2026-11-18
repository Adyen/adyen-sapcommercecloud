import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type BTagColor = 'grey' | 'white' | 'green' | 'red' | 'orange' | 'blue';

@Component({
  selector: 'b-tag',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'hostClass()' },
  template: `
    <span class="b-typography b-tag__label b-typography--caption b-typography--caption-stronger"><ng-content /></span>
  `,
})
export class BTag {
  readonly color = input<BTagColor>('grey');

  protected readonly hostClass = computed(() => `b-tag b-tag--${this.color()}`);
}

export type BStatusColor = 'green' | 'red' | 'orange' | 'yellow' | 'blue' | 'grey';

@Component({
  selector: 'b-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'b-status' },
  template: `
    <span class="b-status__indicator" [class]="'b-status__indicator b-status__indicator--' + color()" role="presentation"></span>
    <span class="b-typography b-status__label b-typography--body"><ng-content /></span>
  `,
})
export class BStatus {
  readonly color = input<BStatusColor>('grey');
}
