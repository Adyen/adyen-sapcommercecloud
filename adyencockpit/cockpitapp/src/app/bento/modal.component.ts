import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  input,
  model,
  viewChild,
} from '@angular/core';

import { BButton } from './button.component';
import { uniqueId } from './form-control';

/**
 * Built on the native dialog element, so focus trapping, Escape and the top layer come from the
 * browser rather than from code here. Actions go in an element marked [bModalActions].
 */
@Component({
  selector: 'b-modal',
  imports: [BButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog
            [class]="'b-modal b-modal--' + size()"
            [attr.aria-labelledby]="titleId"
            (close)="open.set(false)"
            (click)="onDialogClick($event)">
      <div class="b-modal__page">
        <div class="b-modal__header">
          <h2 class="b-typography b-typography--title-m" [id]="titleId">{{ title() }}</h2>
        </div>
        <div class="b-modal__content b-typography b-typography--body"><ng-content /></div>
        <div class="b-modal__actions"><ng-content select="[bModalActions]" /></div>
        <button bButton type="button" variant="tertiary-with-background" [iconOnly]="true" icon="close"
                class="b-modal__close-button" aria-label="Close" (click)="open.set(false)"></button>
      </div>
    </dialog>
  `,
  styles: [':host .b-modal__actions:empty { display: none; }'],
})
export class BModal {
  readonly open = model(false);
  readonly title = input.required<string>();
  readonly size = input<'small' | 'medium' | 'large'>('medium');
  readonly closeOnBackdrop = input(true);

  protected readonly titleId = uniqueId('b-modal-title');
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      if (this.open() && !element.open) {
        element.showModal();
      } else if (!this.open() && element.open) {
        element.close();
      }
    });
  }

  /** A click whose target is the dialog itself landed on the backdrop, outside the page. */
  protected onDialogClick(event: MouseEvent): void {
    if (this.closeOnBackdrop() && event.target === this.dialog().nativeElement) {
      this.open.set(false);
    }
  }
}
