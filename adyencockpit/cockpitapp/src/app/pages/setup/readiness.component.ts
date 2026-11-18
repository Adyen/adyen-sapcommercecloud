import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { BStatus } from '../../bento';
import { StoreSetupView } from '../../core/setup.types';

/**
 * Customer Area's "test your integration" checklist, for what this store holds. Completion is a discrete
 * status per item, never a progress bar.
 */
@Component({
  selector: 'adyen-readiness',
  imports: [BStatus],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: var(--b-spacer-060); margin: 0; padding: 0; list-style: none; }
    li {
      display: flex; flex-direction: column; gap: var(--b-spacer-040); padding: var(--b-spacer-070);
      border: var(--b-border-width-s) solid var(--b-color-outline-primary); border-radius: var(--b-border-radius-l);
    }
  `],
  template: `
    <ul class="grid">
      @for (item of items(); track item.label) {
        <li>
          <span class="b-typography b-typography--body b-typography--body-stronger">{{ item.label }}</span>
          <b-status [color]="item.done ? 'green' : 'grey'">{{ item.done ? 'Completed' : 'Not started' }}</b-status>
        </li>
      }
    </ul>
  `,
})
export class Readiness {
  readonly store = input.required<StoreSetupView>();

  protected readonly items = computed(() => {
    const store = this.store();
    return [
      { label: 'Management API key', done: store.hasManagementKey },
      { label: 'Merchant account', done: !!store.merchantAccount },
      { label: 'Checkout API key', done: store.hasCheckoutKey },
      { label: 'Client key', done: store.hasClientKey },
      { label: 'Webhook', done: store.hasWebhookCredentials },
      { label: 'Webhook HMAC key', done: store.hasHmacKey },
    ];
  });
}
