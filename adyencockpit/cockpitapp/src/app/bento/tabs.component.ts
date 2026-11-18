import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  input,
  model,
  viewChildren,
} from '@angular/core';

import { uniqueId } from './form-control';

export interface BTab {
  id: string;
  label: string;
  counter?: number | null;
  disabled?: boolean;
}

/**
 * Follows the WAI-ARIA tabs pattern: one tab stop for the whole list, arrow keys move between tabs
 * and select as they go. The panel content is projected; the consumer switches it on `active`.
 */
@Component({
  selector: 'b-tabs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'b-tabs' },
  template: `
    <div class="b-tabs__tab-list" role="tablist" [attr.aria-label]="label()">
      @for (tab of tabs(); track tab.id; let i = $index) {
        <button #tabButton type="button" role="tab"
                class="b-tabs__tab b-typography b-typography--body b-typography--body-stronger"
                [class.b-tabs__tab--active]="tab.id === current()"
                [id]="tabId(tab.id)"
                [attr.aria-selected]="tab.id === current()"
                [attr.aria-controls]="panelId"
                [attr.tabindex]="tab.id === current() ? 0 : -1"
                [disabled]="!!tab.disabled"
                (click)="active.set(tab.id)"
                (keydown)="onKeydown($event, i)">
          <span class="b-tabs__tab-label">
            {{ tab.label }}
            @if (tab.counter !== undefined && tab.counter !== null) {
              <span class="b-tabs__tab-counter b-typography--caption">{{ tab.counter }}</span>
            }
          </span>
          <span class="b-tabs__tab-underline"></span>
        </button>
      }
    </div>
    <div class="b-tabs__panel" role="tabpanel" tabindex="0"
         [id]="panelId" [attr.aria-labelledby]="tabId(current())">
      <ng-content />
    </div>
  `,
})
export class BTabs {
  readonly tabs = input.required<BTab[]>();
  readonly active = model('');
  readonly label = input('Sections');

  private readonly prefix = uniqueId('b-tabs');
  protected readonly panelId = `${this.prefix}-panel`;
  private readonly buttons = viewChildren<ElementRef<HTMLButtonElement>>('tabButton');

  protected readonly current = computed(() => this.active() || this.tabs()[0]?.id || '');

  protected tabId(id: string): string {
    return `${this.prefix}-tab-${id}`;
  }

  protected onKeydown(event: KeyboardEvent, index: number): void {
    const enabled = this.tabs()
      .map((tab, i) => ({ tab, i }))
      .filter(({ tab }) => !tab.disabled);
    const position = enabled.findIndex(({ i }) => i === index);
    let target: number | undefined;

    switch (event.key) {
      case 'ArrowRight': target = enabled[(position + 1) % enabled.length]?.i; break;
      case 'ArrowLeft':  target = enabled[(position - 1 + enabled.length) % enabled.length]?.i; break;
      case 'Home':       target = enabled[0]?.i; break;
      case 'End':        target = enabled[enabled.length - 1]?.i; break;
      default: return;
    }

    event.preventDefault();
    if (target !== undefined) {
      this.active.set(this.tabs()[target].id);
      this.buttons()[target]?.nativeElement.focus();
    }
  }
}
