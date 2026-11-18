import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { BAlert, BButton, BInputField, BSelect, BSelectOption, BStatus, BStructuredList } from '../../bento';
import { SetupApiService, describeFailure, isRefusedUpFront } from '../../core/setup-api.service';
import { ProvisionReport, ProvisionStep, StoreSetupView, isConnected } from '../../core/setup.types';

/** In the order the server attempts them; a step the report does not list was never reached. */
const EXPECTED_STEPS: { name: ProvisionStep['name']; label: string }[] = [
  { name: 'credential', label: 'Storefront API credential' },
  { name: 'allowedOrigin', label: 'Allowed origin' },
  { name: 'webhook', label: 'Webhook' },
  { name: 'hmac', label: 'Webhook HMAC key' },
];

const ORIGIN = /^https:\/\/[A-Za-z0-9.-]+(:\d{1,5})?$/;

function normaliseOrigin(value: string): string | null {
  const trimmed = value.trim().replace(/\/+$/, '');
  return ORIGIN.test(trimmed) ? trimmed : null;
}

@Component({
  selector: 'adyen-storefront-step',
  imports: [ReactiveFormsModule, BAlert, BButton, BInputField, BSelect, BStatus, BStructuredList],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`
    :host { display: flex; flex-direction: column; gap: var(--b-spacer-070); }
    .fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--b-spacer-080); }
    .actions { display: flex; gap: var(--b-spacer-040); }
    .report { display: flex; flex-direction: column; gap: var(--b-spacer-060); margin: 0; padding: 0; list-style: none; }
    .report li { display: flex; flex-direction: column; gap: var(--b-spacer-010); }
  `],
  template: `
    @if (!ready()) {
      <b-alert variant="highlight" title="Complete the first two steps">
        Creating the storefront's credentials needs the Management API key and the merchant account.
      </b-alert>
    } @else if (store().sites.length === 0) {
      <b-alert variant="warning" title="This store has no site">
        Adyen's webhook is addressed to a base site, and no site uses this store. Assign the store to a site in Backoffice, then come back.
      </b-alert>
    } @else {
      <p class="b-typography b-typography--body" style="margin: 0;">
        Adyen creates an API credential for the storefront, allows it to run the Drop-in from your storefront's address,
        and sets up a webhook with an HMAC key. What comes back is stored against this store.
      </p>

      <div class="fields">
        <b-input-field label="Storefront origin" requirement="required" placeholder="https://shop.example.com"
                       description="Scheme and host only. The Drop-in is served from here."
                       [error]="originError()" [formControl]="origin" />
        <b-select label="Site receiving notifications" [options]="siteOptions()"
                  description="Adyen posts payment updates to this site's webhook." [formControl]="site" />
        <b-input-field label="Public address for notifications" requirement="optional"
                       [placeholder]="origin.value || 'Same as the storefront origin'"
                       description="Only when Adyen cannot reach the storefront origin, a local tunnel say."
                       [error]="baseError()" [formControl]="notificationBase" />
      </div>

      <b-structured-list [items]="preview()" [mono]="true" />

      @if (alreadyConnected()) {
        <b-alert variant="warning" title="This store is already connected">
          Running this again creates a second credential and a second webhook at Adyen and replaces the ones stored here.
          Remove the old ones in the Customer Area afterwards.
        </b-alert>
      } @else if (partlyConnected()) {
        <b-alert variant="warning" title="An earlier run stopped partway">
          It left some credentials at Adyen. Running this again creates a new credential and webhook rather than
          finishing the old ones, so remove the earlier ones in the Customer Area afterwards.
        </b-alert>
      }

      <div class="actions">
        <button bButton type="button" [loading]="running()" [disabled]="running() || !canRun()" (click)="run()">
          {{ running() ? 'Creating at Adyen…' : 'Create credentials' }}
        </button>
      </div>

      @if (failure(); as problem) {
        <b-alert variant="critical" [title]="problem.title">{{ problem.message }}</b-alert>
      }

      @if (report(); as result) {
        <b-alert [variant]="result.complete ? 'success' : 'critical'"
                 [title]="result.complete ? 'The storefront is connected' : 'The run stopped partway'">
          {{ result.complete
              ? 'Every credential was created and stored.'
              : 'What Adyen already created is listed below and was not undone.' }}
        </b-alert>
        <ul class="report">
          @for (step of expectedSteps; track step.name) {
            @let outcome = outcomeOf(result, step.name);
            <li>
              <b-status [color]="outcome ? (outcome.done ? 'green' : 'red') : 'grey'">
                {{ step.label }} — {{ outcome ? (outcome.done ? 'Completed' : 'Failed') : 'Not reached' }}
              </b-status>
              @if (outcome) {
                <span class="b-typography b-typography--caption b-typography--secondary" style="padding-left: 24px;">{{ outcome.detail }}</span>
              }
            </li>
          }
        </ul>
      }
    }
  `,
})
export class StorefrontStep {
  private readonly api = inject(SetupApiService);

  readonly store = input.required<StoreSetupView>();
  readonly notificationPath = input.required<string>();
  readonly provisioned = output<void>();

  protected readonly expectedSteps = EXPECTED_STEPS;
  protected readonly origin = new FormControl('', { nonNullable: true });
  protected readonly site = new FormControl('', { nonNullable: true });
  protected readonly notificationBase = new FormControl('', { nonNullable: true });

  // Form values as signals, so the preview and validation follow typing without zone-based detection.
  private readonly originValue = toSignal(this.origin.valueChanges, { initialValue: '' });
  private readonly siteValue = toSignal(this.site.valueChanges, { initialValue: '' });
  private readonly baseValue = toSignal(this.notificationBase.valueChanges, { initialValue: '' });

  protected readonly running = signal(false);
  protected readonly report = signal<ProvisionReport | null>(null);
  protected readonly failure = signal<{ title: string; message: string } | null>(null);

  protected readonly ready = computed(() => this.store().hasManagementKey && !!this.store().merchantAccount);
  protected readonly alreadyConnected = computed(() => isConnected(this.store()));
  protected readonly partlyConnected = computed(() => {
    const store = this.store();
    return store.hasCheckoutKey || store.hasClientKey || store.hasWebhookCredentials || store.hasHmacKey;
  });
  protected readonly siteOptions = computed<BSelectOption[]>(() =>
    this.store().sites.map((site) => ({ value: site.uid, label: `${site.name} (${site.uid})` })),
  );

  private readonly normalisedOrigin = computed(() => normaliseOrigin(this.originValue()));
  private readonly normalisedBase = computed(() => (this.baseValue().trim() ? normaliseOrigin(this.baseValue()) : null));

  protected readonly originError = computed(() =>
    this.originValue().trim() && !this.normalisedOrigin()
      ? 'Use https:// followed by a host, with no path.'
      : null,
  );
  protected readonly baseError = computed(() =>
    this.baseValue().trim() && !this.normalisedBase() ? 'Use https:// followed by a host, with no path.' : null,
  );

  protected readonly webhookUrl = computed(() => {
    const base = this.normalisedBase() ?? this.normalisedOrigin();
    const site = this.siteValue();
    return base && site ? base + this.notificationPath().replace('{site}', site) : null;
  });

  protected readonly preview = computed(() => [
    { label: 'Merchant account', value: this.store().merchantAccount },
    { label: 'Allowed origin', value: this.normalisedOrigin() },
    { label: 'Webhook URL', value: this.webhookUrl() },
    { label: 'Credential role', value: 'Checkout webservice role' },
  ]);

  protected readonly canRun = computed(
    () => !!this.normalisedOrigin() && !!this.siteValue() && !this.baseError(),
  );

  /** Only a different store resets the step; a refreshed copy of the same one must keep the report. */
  private readonly storeUid = computed(() => this.store().uid);

  constructor() {
    effect(() => {
      this.storeUid();
      untracked(() => {
        this.report.set(null);
        this.failure.set(null);
        this.site.setValue(this.store().sites[0]?.uid ?? '');
      });
    });
  }

  protected outcomeOf(report: ProvisionReport, name: ProvisionStep['name']): ProvisionStep | undefined {
    return report.steps.find((step) => step.name === name);
  }

  protected run(): void {
    const origin = this.normalisedOrigin();
    if (this.running() || !origin || !this.canRun()) {
      return;
    }
    const uid = this.store().uid;
    this.running.set(true);
    this.failure.set(null);
    this.report.set(null);
    // Exactly what the preview shows, so the server is asked for what the merchant approved.
    this.api.provision(uid, origin, this.site.value, this.normalisedBase()).subscribe({
      next: (result) => {
        this.running.set(false);
        if (uid === this.store().uid) {
          this.report.set(result.report);
        }
        this.provisioned.emit();
      },
      error: (err: unknown) => {
        this.running.set(false);
        if (uid === this.store().uid) {
          this.failure.set(
            isRefusedUpFront(err)
              ? { title: 'Nothing was created', message: describeFailure(err) }
              : {
                  title: 'The result is unknown',
                  message: `${describeFailure(err)} Adyen may already have created some of the credentials. `
                    + 'Check the list below and the Customer Area before running this again.',
                },
          );
        }
        // Whatever happened at Adyen was saved as it went, so the store's state is worth re-reading.
        this.provisioned.emit();
      },
    });
  }
}
