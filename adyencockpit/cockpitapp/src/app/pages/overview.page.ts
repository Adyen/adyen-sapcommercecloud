import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';

import { BAlert, BButton, BCard, BCell, BDataGrid, BDataGridColumn, BPageHeader, BStructuredList, BTag } from '../bento';
import { Bootstrap, CockpitApiService, StoreSetup } from '../core/cockpit-api.service';

type Load = { state: 'loading' } | { state: 'ready'; data: Bootstrap } | { state: 'failed'; message: string };

@Component({
  selector: 'adyen-overview-page',
  imports: [RouterLink, BAlert, BButton, BCard, BCell, BDataGrid, BPageHeader, BStructuredList, BTag],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'b-page', style: 'display: block;' },
  template: `
    <b-page-header title="Overview"
                   description="Adyen for SAP Commerce, running inside Backoffice under the session you are signed in with." />

    @switch (load().state) {
      @case ('failed') {
        <b-alert variant="critical" title="The cockpit could not reach SAP Commerce">{{ failure() }}</b-alert>
      }
      @default {
        @if (unconfigured() > 0) {
          <b-alert variant="warning" title="Adyen is not configured for every store">
            {{ unconfigured() }} of {{ stores().length }} base stores have no Management API key yet.
            <div bAlertActions><a bButton variant="secondary" [condensed]="true" routerLink="/setup">Connect Adyen</a></div>
          </b-alert>
        }

        <div class="b-section" style="margin-top: var(--b-spacer-090);">
          <b-card title="Session" subtitle="Who the cockpit is acting as.">
            <b-structured-list [items]="sessionItems()" />
          </b-card>

          <b-card title="Base stores" subtitle="Adyen credentials are held per store.">
            <b-data-grid [columns]="columns" [rows]="stores()" [loading]="load().state === 'loading'"
                         caption="Base stores and their Adyen configuration"
                         emptyTitle="No base stores" emptyDetails="Create a base store in Backoffice to configure Adyen for it.">
              <ng-template bCell="configured" let-store>
                <b-tag [color]="store.configured ? 'green' : 'orange'">
                  {{ store.configured ? 'Configured' : 'Not configured' }}
                </b-tag>
              </ng-template>
            </b-data-grid>
          </b-card>
        </div>
      }
    }
  `,
})
export class OverviewPage {
  protected readonly columns: BDataGridColumn[] = [
    { key: 'uid', label: 'Store', nowrap: true },
    { key: 'configured', label: 'Management API key' },
  ];

  protected readonly load = toSignal(
    inject(CockpitApiService).bootstrap$.pipe(
      map((data): Load => ({ state: 'ready', data })),
      catchError((err: unknown) => of<Load>({ state: 'failed', message: describe(err) })),
    ),
    { initialValue: { state: 'loading' } as Load },
  );

  protected readonly stores = computed<StoreSetup[]>(() => {
    const load = this.load();
    return load.state === 'ready' ? load.data.stores : [];
  });

  protected readonly unconfigured = computed(() => this.stores().filter((s) => !s.configured).length);

  protected readonly failure = computed(() => {
    const load = this.load();
    return load.state === 'failed' ? load.message : '';
  });

  protected readonly sessionItems = computed(() => {
    const load = this.load();
    const user = load.state === 'ready' ? load.data.user : null;
    return [
      { label: 'Signed in as', value: user },
      { label: 'Authenticated by', value: 'SAP Commerce Backoffice' },
    ];
  });
}

function describe(err: unknown): string {
  if (err && typeof err === 'object' && 'status' in err) {
    const status = (err as { status: number }).status;
    return status === 0
      ? 'The request did not reach the server.'
      : `The server answered ${status}. Reload the page; if it persists, check the Backoffice session.`;
  }
  return 'An unexpected error occurred.';
}
