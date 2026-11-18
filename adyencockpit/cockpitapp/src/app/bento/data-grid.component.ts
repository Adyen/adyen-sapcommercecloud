import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  TemplateRef,
  computed,
  contentChildren,
  inject,
  input,
  output,
} from '@angular/core';

import { BEmptyState } from './empty-state.component';

export interface BDataGridColumn {
  key: string;
  label: string;
  numeric?: boolean;
  nowrap?: boolean;
}

// The grid cannot pass its row type down to a template declared in the consumer, so the row is
// typed loosely here and the consumer's template reads the fields it knows are there.
export interface BCellContext { $implicit: any; value: unknown; }

/** A custom cell: <ng-template bCell="status" let-row>…</ng-template>. */
@Directive({ selector: 'ng-template[bCell]' })
export class BCell {
  readonly bCell = input.required<string>();
  readonly template = inject<TemplateRef<BCellContext>>(TemplateRef);

  static ngTemplateContextGuard(_dir: BCell, ctx: unknown): ctx is BCellContext {
    return true;
  }
}

@Component({
  selector: 'b-data-grid',
  imports: [NgTemplateOutlet, BEmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'b-data-grid', '[class.b-data-grid--condensed]': 'condensed()' },
  template: `
    <table class="b-data-grid__table b-typography b-typography--body">
      @if (caption()) {
        <caption class="b-visually-hidden">{{ caption() }}</caption>
      }
      <thead>
        <tr>
          @for (column of columns(); track column.key) {
            <th scope="col" class="b-data-grid__column" [class.b-data-grid__column--numeric]="!!column.numeric">
              {{ column.label }}
            </th>
          }
        </tr>
      </thead>
      <tbody>
        @if (loading()) {
          <tr><td [attr.colspan]="columns().length"><div class="b-data-grid__loading" role="status">Loading…</div></td></tr>
        } @else {
          @for (row of rows(); track $index) {
            <tr class="b-data-grid__row"
                [class.b-data-grid__row--clickable]="clickable()"
                [attr.tabindex]="clickable() ? 0 : null"
                (click)="clickable() && rowClick.emit(row)"
                (keydown.enter)="clickable() && rowClick.emit(row)">
              @for (column of columns(); track column.key) {
                <td class="b-data-grid__cell"
                    [class.b-data-grid__cell--numeric]="!!column.numeric"
                    [class.b-data-grid__cell--nowrap]="!!column.nowrap">
                  @if (templates().get(column.key); as template) {
                    <ng-container *ngTemplateOutlet="template; context: { $implicit: row, value: valueOf(row, column.key) }" />
                  } @else {
                    {{ valueOf(row, column.key) }}
                  }
                </td>
              }
            </tr>
          } @empty {
            <tr>
              <td [attr.colspan]="columns().length">
                <b-empty-state [title]="emptyTitle()" [details]="emptyDetails()" />
              </td>
            </tr>
          }
        }
      </tbody>
    </table>
  `,
  styles: [`
    .b-visually-hidden {
      position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0;
      overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
    }
  `],
})
export class BDataGrid<T> {
  readonly columns = input.required<BDataGridColumn[]>();
  readonly rows = input<readonly T[]>([]);
  readonly caption = input<string | null>(null);
  readonly loading = input(false);
  readonly clickable = input(false);
  readonly condensed = input(false);
  readonly emptyTitle = input('Nothing here yet');
  readonly emptyDetails = input<string | null>(null);
  readonly rowClick = output<T>();

  private readonly cells = contentChildren(BCell);
  protected readonly templates = computed(
    () => new Map(this.cells().map((cell) => [cell.bCell(), cell.template] as const)),
  );

  protected valueOf(row: T, key: string): unknown {
    return (row as Record<string, unknown>)[key];
  }
}
