import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';

import {
  CredentialCheck,
  MerchantAccounts,
  ProvisionResult,
  SetupStores,
  StoreSetupView,
} from './setup.types';

@Injectable({ providedIn: 'root' })
export class SetupApiService {
  private readonly http = inject(HttpClient);

  stores(): Observable<SetupStores> {
    return this.http.get<SetupStores>('api/setup/stores');
  }

  merchantAccounts(store: string): Observable<MerchantAccounts> {
    return this.http.get<MerchantAccounts>('api/setup/merchant-accounts', { params: { store } });
  }

  /** 422 carries the check that explains the refusal, so it is a result here, not an error. */
  saveManagementKey(store: string, apiKey: string): Observable<CredentialCheck> {
    return this.http
      .post<CredentialCheck>('api/setup/management-key', { store, apiKey })
      .pipe(explained<CredentialCheck>((body) => 'usable' in (body as object)));
  }

  saveMerchantAccount(store: string, merchantAccount: string): Observable<StoreSetupView> {
    return this.http.post<StoreSetupView>('api/setup/merchant-account', { store, merchantAccount });
  }

  /** A partial run answers 422 with the report of what did and did not happen. */
  provision(
    store: string,
    storefrontOrigin: string,
    site: string,
    notificationBaseUrl: string | null,
  ): Observable<ProvisionResult> {
    return this.http
      .post<ProvisionResult>('api/setup/provision', { store, storefrontOrigin, site, notificationBaseUrl })
      .pipe(explained<ProvisionResult>((body) => 'report' in (body as object)));
  }
}

/**
 * Passes a 422 whose body is the expected result through as a normal value, and leaves anything else an
 * error. The server answers 422 both for "Adyen said no, here is why" and for a refusal message.
 */
function explained<T>(isResult: (body: unknown) => boolean) {
  return (source: Observable<T>): Observable<T> =>
    source.pipe(
      catchError((err: unknown) =>
        err instanceof HttpErrorResponse && err.status === 422 && err.error && typeof err.error === 'object' && isResult(err.error)
          ? of(err.error as T)
          : throwError(() => err),
      ),
    );
}

/**
 * The server refused the request before doing anything: a precondition or validation message. Only then
 * is it safe to tell a merchant that nothing happened.
 */
export function isRefusedUpFront(err: unknown): boolean {
  return err instanceof HttpErrorResponse && [400, 403, 409, 415, 422].includes(err.status) && serverMessage(err) !== null;
}

/**
 * An ended Backoffice session is answered with the login page. The browser follows that redirect and the
 * request then "succeeds" with HTML, which surfaces as a parse failure on a 200.
 */
function isSessionEnded(err: HttpErrorResponse): boolean {
  if (err.status === 401) {
    return true;
  }
  const text = err.error && typeof err.error === 'object' && 'text' in err.error ? String((err.error as { text: unknown }).text) : '';
  return err.status === 200 || text.trimStart().startsWith('<');
}

function serverMessage(err: HttpErrorResponse): string | null {
  return err.error && typeof err.error === 'object' && 'error' in err.error && typeof (err.error as { error: unknown }).error === 'string'
    ? (err.error as { error: string }).error
    : null;
}

/** A message a merchant can act on, from whatever the request failed with. */
export function describeFailure(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const message = serverMessage(err);
    if (message) {
      return message;
    }
    if (isSessionEnded(err)) {
      return 'Your Backoffice session has ended. Reload the page and sign in again.';
    }
    if (err.status === 0) {
      return 'The request did not reach SAP Commerce.';
    }
    return `SAP Commerce answered ${err.status}.`;
  }
  return 'An unexpected error occurred.';
}
