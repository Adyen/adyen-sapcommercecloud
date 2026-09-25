import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { BAlert, BButton, BInputField, BTag } from '../../bento';
import { SetupApiService, describeFailure } from '../../core/setup-api.service';
import { CredentialCheck, StoreSetupView } from '../../core/setup.types';

/** The roles the server checks for, named as Adyen names them in the Customer Area. */
const REQUIRED_ROLES = [
  'Management API—API credentials read and write',
  'Management API—Webhooks read and write',
  'Management API—Accounts read',
];

@Component({
  selector: 'adyen-management-key-step',
  imports: [ReactiveFormsModule, BAlert, BButton, BInputField, BTag],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`
    :host { display: flex; flex-direction: column; gap: var(--b-spacer-070); }
    ol { margin: 0; padding-left: var(--b-spacer-080); }
    .roles { display: flex; flex-wrap: wrap; gap: var(--b-spacer-030); }
    .actions { display: flex; gap: var(--b-spacer-040); }
    ul { margin: var(--b-spacer-020) 0 0; padding-left: var(--b-spacer-080); }
  `],
  template: `
    @if (store().hasManagementKey) {
      <b-alert variant="success" title="This store has a Management API key">
        Adding another replaces it. The storefront credentials it created stay as they are.
      </b-alert>
    }

    <div class="b-typography b-typography--body">
      <p style="margin: 0 0 var(--b-spacer-040);">In your {{ environment() }} Customer Area:</p>
      <ol>
        <li>Go to <strong>Developers → API credentials</strong> and open a company-level credential, one named <code>ws…&#64;Company.&lt;YourCompany&gt;</code>.</li>
        <li>Under <strong>Authentication</strong>, generate an API key and copy it. Adyen shows it once.</li>
        <li>Paste it below. It is checked with Adyen before anything is stored, and stored encrypted.</li>
      </ol>
    </div>

    <div>
      <p class="b-typography b-typography--caption b-typography--secondary" style="margin: 0 0 var(--b-spacer-030);">The credential needs these roles:</p>
      <div class="roles">
        @for (role of requiredRoles; track role) {
          <b-tag color="grey">{{ role }}</b-tag>
        }
      </div>
    </div>

    <b-input-field label="Management API key" requirement="required" type="password" autocomplete="off"
                   [mono]="true" placeholder="AQE…" [formControl]="key" />

    <div class="actions">
      <button bButton type="button" [loading]="saving()" [disabled]="saving()" (click)="save()">
        {{ saving() ? 'Checking with Adyen…' : 'Check and save' }}
      </button>
    </div>

    @if (error(); as message) {
      <b-alert variant="critical" title="The key could not be checked">{{ message }}</b-alert>
    }

    @if (check(); as result) {
      @if (result.usable) {
        <b-alert variant="success" title="Key accepted and stored">
          Adyen identified it as {{ result.username }} on {{ result.companyName }}.
        </b-alert>
      } @else {
        <b-alert variant="critical" [title]="refusalTitle()">
          {{ refusalDetail() }}
          @if (result.missingRoles.length) {
            <ul>
              @for (role of result.missingRoles; track role) {
                <li>{{ role }}</li>
              }
            </ul>
          }
        </b-alert>
      }
    }
  `,
})
export class ManagementKeyStep {
  private readonly api = inject(SetupApiService);

  readonly store = input.required<StoreSetupView>();
  readonly saved = output<void>();

  protected readonly requiredRoles = REQUIRED_ROLES;
  protected readonly key = new FormControl('', { nonNullable: true });
  protected readonly saving = signal(false);
  protected readonly check = signal<CredentialCheck | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly environment = computed(() => (this.store().testMode ? 'test' : 'live'));

  /** Only a different store resets the step; a refreshed copy of the same one keeps its result. */
  private readonly storeUid = computed(() => this.store().uid);

  constructor() {
    effect(() => {
      this.storeUid();
      untracked(() => {
        this.check.set(null);
        this.error.set(null);
        this.key.reset('');
      });
    });
  }

  protected readonly refusalTitle = computed(() => {
    const result = this.check();
    if (!result) {
      return '';
    }
    if (result.rejection === 'unrecognised') {
      return `Adyen does not recognise this key in the ${this.environment()} environment`;
    }
    if (result.rejection) {
      return 'Adyen could not check this key';
    }
    if (!result.active) {
      return 'This credential is inactive';
    }
    return 'This credential is missing roles';
  });

  protected readonly refusalDetail = computed(() => {
    const result = this.check();
    switch (result?.rejection) {
      case 'unrecognised':
        return `Check that the key comes from the ${this.environment()} Customer Area, that it was copied whole, and that it is an API key rather than a client key or an HMAC key. Nothing was stored.`;
      case 'forbidden':
        return 'Adyen recognised the key but refused to describe it. Nothing was stored.';
      case 'unreachable':
        return 'SAP Commerce could not reach Adyen. Check outbound connectivity from the server. Nothing was stored.';
      case 'misconfigured':
        return 'The Management API endpoint configured on the server is not a valid address. Nothing was stored.';
      case 'missing':
        return 'Paste a key first.';
      case 'rejected':
        return 'Adyen refused the request. Nothing was stored.';
      default:
        return result && !result.active
          ? `Adyen knows ${result.username} but it is not active. Activate it in the Customer Area. Nothing was stored.`
          : `Adyen knows ${result?.username}, but it lacks the roles below. Add them in the Customer Area and try again. Nothing was stored.`;
    }
  });

  protected save(): void {
    const uid = this.store().uid;
    this.saving.set(true);
    this.error.set(null);
    this.check.set(null);
    this.api.saveManagementKey(uid, this.key.value).subscribe({
      next: (result) => {
        this.saving.set(false);
        if (uid !== this.store().uid) {
          // The merchant moved to another store meanwhile; this result belongs to the one they left.
          this.saved.emit();
          return;
        }
        this.check.set(result);
        if (result.usable) {
          this.key.reset('');
          this.saved.emit();
        }
      },
      error: (err: unknown) => {
        this.saving.set(false);
        if (uid === this.store().uid) {
          this.error.set(describeFailure(err));
        }
      },
    });
  }
}
