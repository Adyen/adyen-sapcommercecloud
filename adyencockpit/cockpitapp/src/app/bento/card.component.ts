import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';

import { uniqueId } from './form-control';
import { BIcon } from './icon.component';

/**
 * Header actions go in an element marked [bCardHeaderActions], footer actions in [bCardActions];
 * everything else is the card body.
 *
 * An expandable card is Customer Area's step card: the whole header toggles the body, and a completed
 * step shows a tick instead of leaving the reader to open it to find out.
 */
@Component({
  selector: 'b-card',
  imports: [BIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'b-card',
    '[class.b-card--secondary]': "variant() === 'secondary'",
    '[class.b-card--expandable]': 'expandable()',
  },
  template: `
    @if (title()) {
      @if (expandable()) {
        <h2 class="b-card__heading">
        <button type="button" class="b-card__header b-card__header--expandable" [id]="headerId"
                [attr.aria-expanded]="expanded()" [attr.aria-controls]="bodyId"
                (click)="expanded.set(!expanded())">
          <span class="b-card__toggle" aria-hidden="true">
            <b-icon [name]="expanded() ? 'chevron-up' : 'chevron-down'" />
          </span>
          <span class="b-card__title-wrapper">
            <span class="b-typography b-card__title b-typography--title">{{ title() }}</span>
            @if (subtitle()) {
              <span class="b-typography b-card__subtitle b-typography--body">{{ subtitle() }}</span>
            }
          </span>
          @if (complete()) {
            <span class="b-card__complete"><b-icon name="success" [size]="20" /><span class="b-visually-hidden">Completed</span></span>
          }
        </button>
        </h2>
      } @else {
        <div class="b-card__header">
          <div class="b-card__title-wrapper">
            <h2 class="b-typography b-card__title b-typography--title">{{ title() }}</h2>
            @if (subtitle()) {
              <p class="b-typography b-card__subtitle b-typography--body">{{ subtitle() }}</p>
            }
          </div>
          <ng-content select="[bCardHeaderActions]" />
        </div>
      }
    }
    @if (isOpen()) {
      <div class="b-card__body" [id]="bodyId" [attr.role]="expandable() ? 'region' : null"
           [attr.aria-labelledby]="expandable() ? headerId : null"><ng-content /></div>
      <div class="b-card__actions"><ng-content select="[bCardActions]" /></div>
    }
  `,
  styles: [`
    :host .b-card__actions:empty { display: none; }
    .b-card__heading { margin: 0; font: inherit; }
    .b-visually-hidden {
      position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0;
      overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
    }
  `],
})
export class BCard {
  readonly title = input<string | null>(null);
  readonly subtitle = input<string | null>(null);
  readonly variant = input<'primary' | 'secondary'>('primary');
  readonly expandable = input(false);
  readonly expanded = model(true);
  readonly complete = input(false);

  protected readonly bodyId = uniqueId('b-card-body');
  protected readonly headerId = `${this.bodyId}-header`;
  protected readonly isOpen = computed(() => !this.expandable() || this.expanded());
}
