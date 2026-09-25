import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, map, of } from 'rxjs';

import { BAlert, BButton, BCard, BPageHeader, BSelect, BSelectOption, BTag } from '../../bento';
import { CockpitApiService } from '../../core/cockpit-api.service';
import { SetupApiService, describeFailure } from '../../core/setup-api.service';
import { SetupStores, StoreSetupView, isConnected } from '../../core/setup.types';
import { ManagementKeyStep } from './management-key.step';
import { MerchantAccountStep } from './merchant-account.step';
import { Readiness } from './readiness.component';
import { StorefrontStep } from './storefront.step';

type StepId = 'key' | 'account' | 'storefront';

const NEXT: Record<StepId, string> = {
  key: 'Management API key saved. Next: choose your merchant account.',
  account: 'Merchant account saved. Next: connect your storefront.',
  storefront: '',
};

/**
 * Customer Area's "Get started" for one base store: verb-first step cards, one open at a time, each
 * ticked when done, and the readiness checklist below. Every step saves as it goes, so a merchant can
 * leave and come back to where they were.
 */
@Component({
  selector: 'adyen-setup-page',
  imports: [FormsModule, BAlert, BButton, BCard, BPageHeader, BSelect, BTag, ManagementKeyStep, MerchantAccountStep, Readiness, StorefrontStep],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'b-page', style: 'display: block;' },
  styles: [`
    .toolbar { display: flex; align-items: flex-end; gap: var(--b-spacer-060); margin-bottom: var(--b-spacer-090); }
    .toolbar b-select { min-width: 320px; }
    .steps { display: flex; flex-direction: column; gap: var(--b-spacer-070); }
    .announcer {
      position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0;
      overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
    }
  `],
  template: `
    <b-page-header [title]="greeting()"
                   description="Follow the steps below to connect SAP Commerce to Adyen. Each step is saved as you go, so you can leave and come back." />

    <div class="announcer" aria-live="polite">{{ announcement() }}</div>

    @if (admin() === false) {
      <b-alert variant="warning" title="An administrator has to do this">
        Connecting a store to Adyen writes credentials into its configuration, so it needs a member of admingroup.
      </b-alert>
    } @else if (loadError(); as message) {
      <b-alert variant="critical" title="The setup could not be loaded">{{ message }}
        <div bAlertActions><button bButton type="button" variant="secondary" [condensed]="true" (click)="reload(null)">Try again</button></div>
      </b-alert>
    } @else if (store(); as current) {
      <div class="toolbar">
        @if (storeOptions().length > 1) {
          <b-select label="Base store" [options]="storeOptions()" [ngModel]="current.uid" (ngModelChange)="selectStore($event)" />
        }
        <b-tag [color]="current.testMode ? 'blue' : 'orange'">{{ current.testMode ? 'Test environment' : 'Live environment' }}</b-tag>
        @if (connected()) {
          <b-tag color="green">Connected</b-tag>
        }
      </div>

      @if (refreshError(); as message) {
        <b-alert variant="warning" title="What is shown may be out of date" style="margin-bottom: var(--b-spacer-070);">
          {{ message }}
          <div bAlertActions><button bButton type="button" variant="secondary" [condensed]="true" (click)="reload(null)">Refresh</button></div>
        </b-alert>
      }

      <div class="steps">
        <b-card data-step="key" title="Add your Management API key" subtitle="Lets SAP Commerce configure Adyen on your behalf."
                [expandable]="true" [complete]="current.hasManagementKey"
                [expanded]="open() === 'key'" (expandedChange)="toggle('key', $event)">
          <adyen-management-key-step [store]="current" (saved)="keySaved()" />
        </b-card>

        <b-card data-step="account" title="Choose your merchant account" subtitle="The account this store takes payments on."
                [expandable]="true" [complete]="!!current.merchantAccount"
                [expanded]="open() === 'account'" (expandedChange)="toggle('account', $event)">
          <adyen-merchant-account-step [store]="current" [keyRevision]="keyRevision()" (saved)="reload('account')" />
        </b-card>

        <b-card data-step="storefront" title="Connect your storefront" subtitle="Creates the credentials and webhook the storefront runs on."
                [expandable]="true" [complete]="connected()"
                [expanded]="open() === 'storefront'" (expandedChange)="toggle('storefront', $event)">
          <adyen-storefront-step [store]="current" [notificationPath]="notificationPath()" (provisioned)="reload(null)" />
        </b-card>
      </div>

      <section class="b-section" style="margin-top: var(--b-spacer-110);">
        <h2 class="b-typography b-typography--title-m">Check what this store holds</h2>
        <adyen-readiness [store]="current" />
      </section>
    } @else if (data()) {
      <b-alert variant="highlight" title="No base stores">Create a base store in Backoffice to connect it to Adyen.</b-alert>
    } @else {
      <p class="b-typography b-typography--body b-typography--secondary" role="status">Loading…</p>
    }
  `,
})
export class SetupPage {
  private readonly api = inject(SetupApiService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  private readonly bootstrap = toSignal(
    inject(CockpitApiService).bootstrap$.pipe(
      map((b) => ({ user: b.user, admin: b.admin as boolean | null })),
      catchError(() => of({ user: null, admin: null })),
    ),
    { initialValue: { user: null, admin: null } },
  );

  protected readonly admin = computed(() => this.bootstrap().admin);
  protected readonly greeting = computed(() => {
    const user = this.bootstrap().user;
    return user ? `Welcome, ${user}!` : 'Connect Adyen';
  });

  protected readonly data = signal<SetupStores | null>(null);
  /** The first load failed: there is nothing to show. */
  protected readonly loadError = signal<string | null>(null);
  /** A later refresh failed: what is shown stays, marked as possibly stale. */
  protected readonly refreshError = signal<string | null>(null);
  protected readonly announcement = signal('');
  protected readonly keyRevision = signal(0);
  private readonly selectedUid = signal<string | null>(null);
  protected readonly open = signal<StepId | null>(null);

  protected readonly store = computed<StoreSetupView | null>(() => {
    const stores = this.data()?.stores ?? [];
    return stores.find((s) => s.uid === this.selectedUid()) ?? stores[0] ?? null;
  });
  protected readonly storeOptions = computed<BSelectOption[]>(() =>
    (this.data()?.stores ?? []).map((s) => ({ value: s.uid, label: `${s.name} (${s.uid})` })),
  );
  protected readonly notificationPath = computed(() => this.data()?.notificationPath ?? '');
  protected readonly connected = computed(() => {
    const store = this.store();
    return !!store && isConnected(store);
  });

  constructor() {
    this.reload(null);
  }

  protected selectStore(uid: string): void {
    this.selectedUid.set(uid);
    this.announcement.set('');
    this.open.set(firstOpenStep(this.store()));
  }

  protected toggle(step: StepId, expanded: boolean): void {
    this.open.set(expanded ? step : null);
  }

  protected keySaved(): void {
    this.keyRevision.update((revision) => revision + 1);
    this.reload('key');
  }

  /**
   * Re-reads where every store stands. After a step completes, the next one still to do opens and takes
   * focus, since the control that was just used has gone with its card; after the last step, the storefront
   * step stays open so its report remains in view.
   */
  protected reload(completed: StepId | null): void {
    this.api.stores().subscribe({
      next: (stores) => {
        this.data.set(stores);
        this.loadError.set(null);
        this.refreshError.set(null);
        if (completed === null && this.open() !== null) {
          return;
        }
        const next = firstOpenStep(this.store());
        this.open.set(next);
        if (completed && next && next !== completed) {
          this.announcement.set(NEXT[completed]);
          this.focusStep(next);
        }
      },
      error: (err: unknown) => {
        if (this.data()) {
          this.refreshError.set(`${describeFailure(err)} The last change was saved, but this page could not re-read the store.`);
        } else {
          this.loadError.set(describeFailure(err));
        }
      },
    });
  }

  private focusStep(step: StepId): void {
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLElement>(`[data-step="${step}"] .b-card__header--expandable`)?.focus(),
      { injector: this.injector },
    );
  }
}

function firstOpenStep(store: StoreSetupView | null): StepId | null {
  if (!store) {
    return null;
  }
  if (!store.hasManagementKey) {
    return 'key';
  }
  if (!store.merchantAccount) {
    return 'account';
  }
  return 'storefront';
}
