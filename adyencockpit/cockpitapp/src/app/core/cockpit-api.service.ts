import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

export interface StoreSetup {
  uid: string;
  configured: boolean;
}

export interface Bootstrap {
  user: string | null;
  /** Whether the user may connect stores; the setup endpoints refuse anyone else. */
  admin: boolean;
  stores: StoreSetup[];
}

@Injectable({ providedIn: 'root' })
export class CockpitApiService {
  private readonly http = inject(HttpClient);

  /**
   * Relative to the document base, so the calls follow the app wherever it is mounted. The session
   * is the one the user already holds in Backoffice; nothing here logs in. Shared, because the shell
   * and the page both need it and one round trip serves both.
   */
  readonly bootstrap$: Observable<Bootstrap> = this.http
    .get<Bootstrap>('api/bootstrap')
    .pipe(shareReplay({ bufferSize: 1, refCount: false }));
}
