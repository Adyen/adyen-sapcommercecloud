'use client';

import React, { useState } from 'react';
import { AlertCircle, CheckCircle2, Circle, ServerCog, XCircle } from 'lucide-react';
import { postJson } from '../api';
import { ProvisionReport, StoreSetup } from '../types/setup.types';

interface ProvisioningStepProps {
  store: StoreSetup;
  onProvisioned: () => void;
}

/**
 * Listed in the order the backend attempts them. A run that fails early reports fewer steps than
 * this, so every expected step is rendered and the unreported ones are shown as never reached
 * rather than inferred to have failed.
 */
const EXPECTED_STEPS: { name: string; label: string }[] = [
  { name: 'credential', label: 'Storefront API credential' },
  { name: 'allowedOrigin', label: 'Allowed origin' },
  { name: 'webhook', label: 'Webhook' },
  { name: 'hmac', label: 'Webhook HMAC key' },
];

const ProvisioningStep: React.FC<ProvisioningStepProps> = ({ store, onProvisioned }) => {
  const [merchantAccount, setMerchantAccount] = useState(store.merchantAccount || '');
  const [storefrontOrigin, setStorefrontOrigin] = useState('');
  const [notificationUrl, setNotificationUrl] = useState('');
  const [urlEdited, setUrlEdited] = useState(false);
  const [report, setReport] = useState<ProvisionReport | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleOriginChange = (value: string) => {
    setStorefrontOrigin(value);
    if (!urlEdited) {
      const origin = value.trim().replace(/\/+$/, '');
      setNotificationUrl(origin ? `${origin}/adyenv6notificationv2/adyen/v6/notification/${store.uid}/json` : '');
    }
  };

  const validate = (): string | null => {
    if (!merchantAccount.trim()) return 'Enter the merchant account to configure.';
    if (!storefrontOrigin.trim()) return 'Enter the storefront origin.';
    if (!/^https:\/\/[^/]+$/.test(storefrontOrigin.trim().replace(/\/+$/, ''))) {
      return 'The storefront origin must be a scheme and host only, for example https://shop.example.com.';
    }
    if (!notificationUrl.trim()) return 'Enter the notification URL.';
    if (!/^https:\/\//.test(notificationUrl.trim())) return 'The notification URL must start with https://.';
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const invalid = validate();
    if (invalid) {
      setError(invalid);
      return;
    }
    setRunning(true);
    setError(null);
    setReport(null);
    try {
      const { body } = await postJson<ProvisionReport>('/provision', {
        baseStore: store.uid,
        merchantAccount: merchantAccount.trim(),
        storefrontOrigin: storefrontOrigin.trim().replace(/\/+$/, ''),
        notificationUrl: notificationUrl.trim(),
      });
      setReport(body);
      if (body.complete) {
        onProvisioned();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not reach the server.');
    } finally {
      setRunning(false);
    }
  };

  const reported = (name: string) => report?.steps.find((s) => s.name === name);

  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200">
      <div className="px-6 py-4 border-b border-gray-200 flex items-center">
        <ServerCog className="h-5 w-5 text-gray-400 mr-2" />
        <h2 className="text-lg font-medium text-gray-900">Create the storefront configuration</h2>
      </div>
      <div className="p-6">
        <p className="text-sm text-gray-600">
          Using the stored Management API key, Adyen creates a Checkout API credential for this store,
          registers the storefront as an allowed origin, and sets up a standard webhook with an HMAC key.
          The resulting credentials are written to this store.
        </p>
        <div className="mt-4 bg-yellow-50 border border-yellow-200 rounded-md p-4 flex items-start">
          <AlertCircle className="h-5 w-5 text-yellow-600 mr-2 shrink-0 mt-0.5" />
          <p className="text-sm text-yellow-900">
            Running this twice creates a second API credential and a second webhook at Adyen, and replaces
            the credentials held by this store. Run it once per store, and remove the leftovers in the
            Customer Area if you have to repeat it.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          <div>
            <label htmlFor="merchantAccount" className="block text-sm font-medium text-gray-700 mb-2">
              Merchant account *
            </label>
            <input
              id="merchantAccount"
              type="text"
              value={merchantAccount}
              onChange={(e) => setMerchantAccount(e.target.value)}
              placeholder="YourCompanyECOM"
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
            <p className="mt-1 text-xs text-gray-500">
              Shown at the top of the Adyen Customer Area as [YourCompanyAccount] &rsaquo;
              [YourMerchantAccount].
            </p>
          </div>

          <div>
            <label htmlFor="storefrontOrigin" className="block text-sm font-medium text-gray-700 mb-2">
              Storefront origin *
            </label>
            <input
              id="storefrontOrigin"
              type="text"
              value={storefrontOrigin}
              onChange={(e) => handleOriginChange(e.target.value)}
              placeholder="https://shop.example.com"
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
            <p className="mt-1 text-xs text-gray-500">
              Scheme and host only, no path. This is the origin the Drop-in is served from.
            </p>
          </div>

          <div>
            <label htmlFor="notificationUrl" className="block text-sm font-medium text-gray-700 mb-2">
              Notification URL *
            </label>
            <input
              id="notificationUrl"
              type="text"
              value={notificationUrl}
              onChange={(e) => {
                setUrlEdited(true);
                setNotificationUrl(e.target.value);
              }}
              placeholder="https://shop.example.com/adyenv6notificationv2/adyen/v6/notification/electronics/json"
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
            <p className="mt-1 text-xs text-gray-500">
              Suggested from the origin using this store&rsquo;s id. The last path segment must be the base
              <span className="font-medium"> site</span> id that receives the webhook, which is not always
              the same as the store id &mdash; correct it if they differ.
            </p>
          </div>

          <button
            type="submit"
            disabled={running}
            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {running ? 'Configuring at Adyen...' : 'Configure'}
          </button>
        </form>

        {error && (
          <div className="mt-6 bg-red-50 border border-red-200 rounded-md p-4 flex items-start">
            <AlertCircle className="h-5 w-5 text-red-500 mr-2 shrink-0 mt-0.5" />
            <p className="text-sm text-red-800">{error}</p>
          </div>
        )}

        {report && (
          <div className="mt-6">
            <p className={`text-sm font-medium ${report.complete ? 'text-green-800' : 'text-red-800'}`}>
              {report.complete
                ? 'All steps completed. This store is configured.'
                : 'The run stopped partway. What Adyen already created is listed below and was not undone.'}
            </p>
            <ul className="mt-3 divide-y divide-gray-200 border border-gray-200 rounded-md">
              {EXPECTED_STEPS.map(({ name, label }) => {
                const result = reported(name);
                return (
                  <li key={name} className="flex items-start px-4 py-3">
                    {result === undefined ? (
                      <Circle className="h-5 w-5 text-gray-300 mr-3 shrink-0 mt-0.5" />
                    ) : result.done ? (
                      <CheckCircle2 className="h-5 w-5 text-green-500 mr-3 shrink-0 mt-0.5" />
                    ) : (
                      <XCircle className="h-5 w-5 text-red-500 mr-3 shrink-0 mt-0.5" />
                    )}
                    <div>
                      <p className="text-sm font-medium text-gray-900">{label}</p>
                      <p className="text-sm text-gray-600">
                        {result ? result.detail : 'Not reached.'}
                      </p>
                    </div>
                  </li>
                );
              })}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
};

export default ProvisioningStep;
