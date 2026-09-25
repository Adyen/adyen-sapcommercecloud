export interface StoreSetup {
  uid: string;
  name: string;
  merchantAccount: string | null;
  configured: boolean;
}

export interface SetupStatus {
  setupRequired: boolean;
  stores: StoreSetup[];
}

export interface CredentialCheck {
  usable: boolean;
  active: boolean;
  companyName: string | null;
  username: string | null;
  roles: string[];
  missingRoles: string[];
}

export interface ProvisionStepResult {
  name: string;
  done: boolean;
  detail: string;
}

export interface ProvisionReport {
  complete: boolean;
  steps: ProvisionStepResult[];
}
