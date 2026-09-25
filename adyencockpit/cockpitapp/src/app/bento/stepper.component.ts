import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

import { BIcon } from './icon.component';

export interface BStep {
  id: string;
  label: string;
}

/**
 * The vertical step list Customer Area uses for multi-step setup. A step can be revisited once it is
 * completed; steps not yet reached stay inert, so the order the flow needs cannot be skipped.
 */
@Component({
  selector: 'b-stepper',
  imports: [BIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="b-stepper" [attr.aria-label]="label()">
      <ol class="b-stepper__list">
        @for (step of steps(); track step.id; let i = $index) {
          <li class="b-step"
              [class.b-step--active]="step.id === active()"
              [class.b-step--completed]="isCompleted(step.id)">
            <button type="button" class="b-step__button b-typography b-typography--body b-typography--body-stronger"
                    [attr.aria-current]="step.id === active() ? 'step' : null"
                    [disabled]="!isReachable(step.id)"
                    (click)="active.set(step.id)">
              <span class="b-step__index b-typography--caption b-typography--caption-stronger">
                @if (isCompleted(step.id) && step.id !== active()) {
                  <b-icon name="check" [size]="12" />
                  <span class="b-visually-hidden">Completed:</span>
                } @else {
                  {{ i + 1 }}
                }
              </span>
              <span>{{ step.label }}</span>
            </button>
          </li>
        }
      </ol>
    </nav>
  `,
  styles: [`
    .b-visually-hidden {
      position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0;
      overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
    }
  `],
})
export class BStepper {
  readonly steps = input.required<BStep[]>();
  readonly active = model('');
  readonly completed = input<readonly string[]>([]);
  readonly label = input('Progress');

  protected isCompleted(id: string): boolean {
    return this.completed().includes(id);
  }

  protected isReachable(id: string): boolean {
    return id === this.active() || this.isCompleted(id);
  }
}
