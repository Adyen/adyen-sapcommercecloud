/** Mirrors the records the cockpit servlet answers with. */

export interface SiteView {
  uid: string;
  name: string;
}

export interface StoreSetupView {
  uid: string;
  name: string;
  testMode: boolean;
  merchantAccount: string | null;
  hasManagementKey: boolean;
  hasCheckoutKey: boolean;
  hasClientKey: boolean;
  hasWebhookCredentials: boolean;
  hasHmacKey: boolean;
  sites: SiteView[];
}

export interface SetupStores {
  /** Relative to a public base URL; `{site}` is the base site uid. */
  notificationPath: string;
  stores: StoreSetupView[];
}

export type Rejection = 'missing' | 'unrecognised' | 'forbidden' | 'unreachable' | 'misconfigured' | 'rejected';

export interface CredentialCheck {
  usable: boolean;
  active: boolean;
  username: string | null;
  companyName: string | null;
  missingRoles: string[];
  rejection: Rejection | null;
}

export interface MerchantAccountView {
  id: string;
  name: string;
  status: string | null;
}

export interface MerchantAccounts {
  accounts: MerchantAccountView[];
  failure: Rejection | 'no-key' | null;
}

export interface ProvisionStep {
  name: 'credential' | 'allowedOrigin' | 'webhook' | 'hmac';
  done: boolean;
  detail: string;
}

export interface ProvisionReport {
  complete: boolean;
  steps: ProvisionStep[];
}

export interface ProvisionResult {
  report: ProvisionReport;
  store: StoreSetupView;
}

/** A store is connected once the storefront has every credential it runs on. */
export function isConnected(store: StoreSetupView): boolean {
  return store.hasCheckoutKey && store.hasClientKey && store.hasWebhookCredentials && store.hasHmacKey;
}
