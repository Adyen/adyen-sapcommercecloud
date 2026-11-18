import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { BAlert, BButton, BSelect, BSelectOption } from '../../bento';
import { SetupApiService, describeFailure } from '../../core/setup-api.service';
import { MerchantAccounts, StoreSetupView } from '../../core/setup.types';

@Component({
  selector: 'adyen-merchant-account-step',
  imports: [ReactiveFormsModule, BAlert, BButton, BSelect],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [':host { display: flex; flex-direction: column; gap: var(--b-spacer-070); } .actions { display: flex; gap: var(--b-spacer-040); }'],
  template: `
    @if (!store().hasManagementKey) {
      <b-alert variant="highlight" title="Add a Management API key first">
        The merchant accounts are read from Adyen with that key.
      </b-alert>
    } @else {
      <p class="b-typography b-typography--body" style="margin: 0;">
        The list comes from Adyen, so only accounts the stored key can reach appear.
      </p>

      @if (loadError(); as message) {
        <b-alert variant="critical" title="The merchant accounts could not be read">{{ message }}</b-alert>
      } @else if (loading()) {
        <p class="b-typography b-typography--body b-typography--secondary" role="status" style="margin: 0;">Reading merchant accounts from Adyen…</p>
      } @else if (options().length === 0) {
        <b-alert variant="warning" title="No merchant accounts">The key can see no merchant accounts. Check that it is a company-level credential.</b-alert>
      } @else {
        <b-select label="Merchant account" [options]="options()" placeholder="Choose a merchant account" [formControl]="choice" />
        @if (truncated()) {
          <p class="b-typography b-typography--caption b-typography--secondary" style="margin: 0;">
            Showing the first 100 merchant accounts.
          </p>
        }
        <div class="actions">
          <button bButton type="button" [loading]="saving()" [disabled]="saving() || !choice.value" (click)="save()">Save</button>
        </div>
      }

      @if (saveError(); as message) {
        <b-alert variant="critical" title="The merchant account was not saved">{{ message }}</b-alert>
      }
    }
  `,
})
export class MerchantAccountStep {
  private readonly api = inject(SetupApiService);

  readonly store = input.required<StoreSetupView>();
  /** Bumped when a new Management API key is stored; the list then has to be read with that key. */
  readonly keyRevision = input(0);
  readonly saved = output<void>();

  protected readonly choice = new FormControl('', { nonNullable: true });
  protected readonly accounts = signal<MerchantAccounts | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saveError = signal<string | null>(null);

  protected readonly options = computed<BSelectOption[]>(() =>
    (this.accounts()?.accounts ?? []).map((account) => ({
      value: account.id,
      label: account.name === account.id ? account.id : `${account.name} (${account.id})`,
    })),
  );
  protected readonly truncated = computed(() => (this.accounts()?.accounts.length ?? 0) >= 100);

  /** What decides whether the list has to be read again: another store, or a key where there was none. */
  private readonly source = computed(() => `${this.store().uid}|${this.store().hasManagementKey}|${this.keyRevision()}`);

  constructor() {
    effect(() => {
      this.source();
      untracked(() => this.load());
    });
  }

  private load(): void {
    const store = this.store();
    this.accounts.set(null);
    this.loadError.set(null);
    this.saveError.set(null);
    this.choice.setValue(store.merchantAccount ?? '');
    if (!store.hasManagementKey) {
      return;
    }
    this.loading.set(true);
    this.api.merchantAccounts(store.uid).subscribe({
      next: (result) => {
        if (store.uid !== this.store().uid) {
          // Another store's list, arriving after the merchant moved on.
          return;
        }
        this.loading.set(false);
        if (result.failure) {
          this.loadError.set(describeListFailure(result.failure));
          return;
        }
        this.accounts.set(result);
        // A stored account the key can no longer see is not offered as if it were chosen.
        if (!result.accounts.some((account) => account.id === this.choice.value)) {
          this.choice.setValue('');
        }
      },
      error: (err: unknown) => {
        if (store.uid !== this.store().uid) {
          return;
        }
        this.loading.set(false);
        this.loadError.set(describeFailure(err));
      },
    });
  }

  protected save(): void {
    const uid = this.store().uid;
    this.saving.set(true);
    this.saveError.set(null);
    this.api.saveMerchantAccount(uid, this.choice.value).subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.emit();
      },
      error: (err: unknown) => {
        this.saving.set(false);
        if (uid === this.store().uid) {
          this.saveError.set(describeFailure(err));
        }
      },
    });
  }
}

function describeListFailure(failure: string): string {
  switch (failure) {
    case 'unrecognised':
      return 'Adyen no longer recognises the stored key. Add it again in the first step.';
    case 'forbidden':
      return 'The stored key lacks the Management API—Accounts read role.';
    case 'unreachable':
      return 'SAP Commerce could not reach Adyen.';
    case 'no-key':
      return 'This store has no Management API key.';
    default:
      return 'Adyen refused the request.';
  }
}
