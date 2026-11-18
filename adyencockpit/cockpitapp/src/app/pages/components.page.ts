import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';

import {
  BAlert,
  BButton,
  BCard,
  BCell,
  BCheckbox,
  BCodeSnippet,
  BDataGrid,
  BDataGridColumn,
  BEmptyState,
  BInputField,
  BModal,
  BPageHeader,
  BSelect,
  BStatus,
  BStepper,
  BStructuredList,
  BTab,
  BTabs,
  BTag,
  BToggle,
} from '../bento';

interface WebhookRow {
  id: string;
  url: string;
  type: string;
  active: boolean;
}

/**
 * Every component in every state it has, on one page, so fidelity can be checked side by side with
 * Customer Area and a regression is visible at a glance.
 */
@Component({
  selector: 'adyen-components-page',
  imports: [
    ReactiveFormsModule,
    BAlert, BButton, BCard, BCell, BCheckbox, BCodeSnippet, BDataGrid, BEmptyState, BInputField,
    BModal, BPageHeader, BSelect, BStatus, BStepper, BStructuredList, BTabs, BTag, BToggle,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'b-page', style: 'display: block;' },
  styles: [`
    .row { display: flex; flex-wrap: wrap; align-items: center; gap: var(--b-spacer-060); }
    .grid-2 { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--b-spacer-080); }
    .stack { display: flex; flex-direction: column; gap: var(--b-spacer-060); }
    .stepper-demo { display: grid; grid-template-columns: 240px 1fr; gap: var(--b-spacer-090); }
  `],
  template: `
    <b-page-header title="Components"
                   description="The Bento component layer the cockpit is built from. Class names match Customer Area, so any component can be compared with the original in the inspector.">
      <button bButton type="button" variant="secondary" icon="plus" (click)="modalOpen.set(true)">Open modal</button>
    </b-page-header>

    <section class="b-section">
      <h2 class="b-typography b-typography--title-m">Buttons</h2>
      <div class="row">
        <button bButton type="button" variant="primary">Primary</button>
        <button bButton type="button" variant="secondary">Secondary</button>
        <button bButton type="button" variant="tertiary-with-background">Tertiary with background</button>
        <button bButton type="button" variant="tertiary">Tertiary</button>
      </div>
      <div class="row">
        <button bButton type="button" variant="primary-critical">Delete</button>
        <button bButton type="button" variant="secondary-critical">Revoke</button>
        <button bButton type="button" variant="tertiary-critical">Remove</button>
      </div>
      <div class="row">
        <button bButton type="button" icon="plus">Create new webhook</button>
        <button bButton type="button" variant="secondary" [condensed]="true">Condensed</button>
        <button bButton type="button" variant="secondary" [iconOnly]="true" icon="copy" aria-label="Copy"></button>
        <button bButton type="button" [loading]="true">Saving</button>
        <button bButton type="button" [disabled]="true">Disabled</button>
        <button bButton type="button" variant="secondary" [disabled]="true">Disabled</button>
      </div>
    </section>

    <section class="b-section">
      <h2 class="b-typography b-typography--title-m">Text inputs</h2>
      <div class="grid-2">
        <b-input-field label="Webhook URL" requirement="required" placeholder="https://"
                       description="Where Adyen sends notifications." type="url" [formControl]="url" />
        <b-input-field label="Description" requirement="optional" hint="0/50" [formControl]="description" />
        <b-input-field label="Merchant account" error="This merchant account does not exist." [formControl]="broken" />
        <b-input-field label="API key" [mono]="true" type="password" autocomplete="off" [formControl]="apiKey" />
        <b-input-field label="Amount" prefix="EUR" suffix=".00" [formControl]="amount" />
        <b-input-field label="Search" icon="search" type="search" placeholder="Search by URL, description or ID"
                       [condensed]="true" [formControl]="search" />
        <b-input-field label="Read-only" [readonly]="true" [formControl]="readonly" />
        <b-input-field label="Disabled" [disabled]="true" [formControl]="disabledField" />
      </div>
    </section>

    <section class="b-section">
      <h2 class="b-typography b-typography--title-m">Selection</h2>
      <div class="grid-2">
        <b-select label="Communication format" [options]="formats" placeholder="Select a format"
                  description="JSON is the default for new webhooks." [formControl]="format" />
        <b-select label="Disabled select" [options]="formats" [disabled]="true" [formControl]="format" />
        <div class="stack">
          <b-checkbox description="Retry failed notifications for up to 30 days." [formControl]="retry">Retry failed notifications</b-checkbox>
          <b-checkbox [disabled]="true" [formControl]="retry">Disabled checkbox</b-checkbox>
        </div>
        <div class="stack">
          <b-toggle description="Adyen only sends notifications while the webhook is active." [formControl]="active">Active</b-toggle>
          <b-toggle [disabled]="true" [formControl]="active">Disabled toggle</b-toggle>
        </div>
      </div>
    </section>

    <section class="b-section">
      <h2 class="b-typography b-typography--title-m">Tags and status</h2>
      <div class="row">
        <b-tag color="grey">Merchant account: TEST</b-tag>
        <b-tag color="green">Configured</b-tag>
        <b-tag color="orange">Pending</b-tag>
        <b-tag color="red">Failed</b-tag>
        <b-tag color="blue">Test mode</b-tag>
      </div>
      <div class="row">
        <b-status color="green">Active</b-status>
        <b-status color="orange">Pending</b-status>
        <b-status color="red">Inactive</b-status>
        <b-status color="grey">Unknown</b-status>
      </div>
    </section>

    <section class="b-section">
      <h2 class="b-typography b-typography--title-m">Alerts</h2>
      <b-alert variant="highlight" title="This is a new page">You can still use the old version of this page.</b-alert>
      <b-alert variant="success" title="Webhook created">Adyen will start sending notifications within a minute.</b-alert>
      <b-alert variant="warning" title="HMAC key missing">Notifications cannot be verified until an HMAC key is generated.
        <div bAlertActions><button bButton type="button" variant="secondary" [condensed]="true">Generate HMAC key</button></div>
      </b-alert>
      @if (showCritical()) {
        <b-alert variant="critical" title="The credential was rejected" [dismissible]="true" (dismissed)="showCritical.set(false)">
          Adyen answered 401. Check that the key belongs to this environment.
        </b-alert>
      }
    </section>

    <section class="b-section">
      <h2 class="b-typography b-typography--title-m">Cards and structured lists</h2>
      <div class="grid-2">
        <b-card title="Merchant overview" subtitle="REPLYAccount_AlphaDev_TEST">
          <button bCardHeaderActions bButton type="button" variant="tertiary">Manage</button>
          <b-structured-list [items]="merchantDetails" />
        </b-card>
        <b-card title="Secondary card" variant="secondary">
          <p class="b-typography b-typography--body">A card on the secondary surface, for grouping inside a page.</p>
        </b-card>
      </div>
    </section>

    <section class="b-section">
      <h2 class="b-typography b-typography--title-m">Data grid</h2>
      <b-card>
        <b-data-grid [columns]="webhookColumns" [rows]="webhooks" [clickable]="true" caption="Webhooks"
                     (rowClick)="lastClicked.set($event.id)">
          <ng-template bCell="active" let-row>
            <b-status [color]="row.active ? 'green' : 'grey'">{{ row.active ? 'Active' : 'Inactive' }}</b-status>
          </ng-template>
        </b-data-grid>
        @if (lastClicked()) {
          <p class="b-typography b-typography--caption b-typography--secondary">Last clicked: {{ lastClicked() }}</p>
        }
      </b-card>
      <div class="grid-2">
        <b-card title="Empty"><b-data-grid [columns]="webhookColumns" [rows]="[]" emptyTitle="No webhooks yet"
                                           emptyDetails="Create a webhook to start receiving notifications." /></b-card>
        <b-card title="Loading"><b-data-grid [columns]="webhookColumns" [loading]="true" /></b-card>
      </div>
    </section>

    <section class="b-section">
      <h2 class="b-typography b-typography--title-m">Tabs</h2>
      <b-tabs [tabs]="tabs" [(active)]="activeTab" label="Webhook sections">
        <p class="b-typography b-typography--body">Showing: {{ activeTab() }}</p>
      </b-tabs>
    </section>

    <section class="b-section">
      <h2 class="b-typography b-typography--title-m">Stepper</h2>
      <div class="stepper-demo">
        <b-stepper [steps]="steps" [(active)]="activeStep" [completed]="completedSteps()" label="Setup progress" />
        <b-card [title]="stepTitle()">
          <p class="b-typography b-typography--body">Completed steps can be revisited; later ones stay closed until reached.</p>
          <div bCardActions>
            <button bButton type="button" (click)="advance()">Continue</button>
          </div>
        </b-card>
      </div>
    </section>

    <section class="b-section">
      <h2 class="b-typography b-typography--title-m">Empty state</h2>
      <b-card>
        <b-empty-state icon="webhook" title="No webhooks yet" details="Create a webhook to start receiving notifications.">
          <button bButton type="button" icon="plus">Create new webhook</button>
        </b-empty-state>
      </b-card>
    </section>

    <section class="b-section">
      <h2 class="b-typography b-typography--title-m">Code snippet</h2>
      <b-code-snippet language="JSON" [code]="sampleJson" />
    </section>

    <b-modal title="Create new webhook" size="medium" [(open)]="modalOpen">
      <div class="stack">
        <b-input-field label="Webhook URL" requirement="required" placeholder="https://" type="url" [formControl]="url" />
        <b-select label="Communication format" [options]="formats" [formControl]="format" />
      </div>
      <div bModalActions>
        <button bButton type="button" variant="secondary" (click)="modalOpen.set(false)">Cancel</button>
        <button bButton type="button" (click)="modalOpen.set(false)">Create</button>
      </div>
    </b-modal>
  `,
})
export class ComponentsPage {
  protected readonly url = new FormControl('', { nonNullable: true, validators: Validators.required });
  protected readonly description = new FormControl('', { nonNullable: true });
  protected readonly broken = new FormControl('ReplyAccount_Wrong', { nonNullable: true });
  protected readonly apiKey = new FormControl('AQEyhmfxK4nPblhWw0m', { nonNullable: true });
  protected readonly amount = new FormControl('120', { nonNullable: true });
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly readonly = new FormControl('ws@Company.REPLYAccount', { nonNullable: true });
  protected readonly disabledField = new FormControl('Not editable', { nonNullable: true });
  protected readonly format = new FormControl('json', { nonNullable: true });
  protected readonly retry = new FormControl(true, { nonNullable: true });
  protected readonly active = new FormControl(true, { nonNullable: true });

  protected readonly formats = [
    { value: 'json', label: 'JSON' },
    { value: 'http', label: 'HTTP POST' },
    { value: 'soap', label: 'SOAP', disabled: true },
  ];

  protected readonly merchantDetails = [
    { label: 'Merchant account', value: 'REPLYAccount_AlphaDev_TEST' },
    { label: 'Company account', value: 'REPLYAccount' },
    { label: 'Environment', value: 'Test' },
  ];

  protected readonly webhookColumns: BDataGridColumn[] = [
    { key: 'id', label: 'Webhook ID', nowrap: true },
    { key: 'url', label: 'URL' },
    { key: 'type', label: 'Type', nowrap: true },
    { key: 'active', label: 'Status', nowrap: true },
  ];

  protected readonly webhooks: WebhookRow[] = [
    { id: 'WBHK4225B7…', url: 'https://shop.example.com/adyen/v6/notification/electronics/json', type: 'Standard', active: true },
    { id: 'WBHK8F31C2…', url: 'https://staging.example.com/adyen/v6/notification/apparel/json', type: 'Standard', active: false },
  ];

  protected readonly tabs: BTab[] = [
    { id: 'merchant', label: 'Merchant webhooks', counter: 2 },
    { id: 'company', label: 'Company webhooks', counter: 0 },
    { id: 'logs', label: 'Event logs' },
  ];

  protected readonly steps = [
    { id: 'key', label: 'Management API key' },
    { id: 'account', label: 'Merchant account' },
    { id: 'storefront', label: 'Storefront' },
    { id: 'provision', label: 'Create credentials' },
  ];

  protected readonly sampleJson = JSON.stringify(
    { type: 'standard', communicationFormat: 'json', url: 'https://shop.example.com/adyen/v6/notification/electronics/json', active: true },
    null,
    2,
  );

  protected readonly modalOpen = signal(false);
  protected readonly showCritical = signal(true);
  protected readonly lastClicked = signal<string | null>(null);
  protected readonly activeTab = signal('merchant');
  protected readonly activeStep = signal('key');
  protected readonly completedSteps = signal<string[]>([]);

  protected stepTitle(): string {
    return this.steps.find((s) => s.id === this.activeStep())?.label ?? '';
  }

  protected advance(): void {
    const index = this.steps.findIndex((s) => s.id === this.activeStep());
    const current = this.steps[index].id;
    this.completedSteps.update((done) => (done.includes(current) ? done : [...done, current]));
    const next = this.steps[index + 1];
    if (next) {
      this.activeStep.set(next.id);
    }
  }
}
