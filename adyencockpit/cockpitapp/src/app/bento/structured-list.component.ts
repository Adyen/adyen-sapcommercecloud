import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export interface BStructuredListItem {
  label: string;
  value: string | null | undefined;
}

@Component({
  selector: 'b-structured-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dl class="b-structured-list b-typography b-typography--body">
      @for (item of items(); track item.label) {
        <dt class="b-structured-list__label">{{ item.label }}</dt>
        <dd class="b-structured-list__value" [class.b-typography--mono]="mono()">{{ item.value || '—' }}</dd>
      }
    </dl>
  `,
})
export class BStructuredList {
  readonly items = input<BStructuredListItem[]>([]);
  readonly mono = input(false);
}
