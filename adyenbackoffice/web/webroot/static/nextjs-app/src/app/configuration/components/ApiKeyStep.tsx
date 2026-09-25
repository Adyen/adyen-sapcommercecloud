'use client';

import React, { useState } from 'react';
import { AlertCircle, CheckCircle2, KeyRound } from 'lucide-react';
import { postJson } from '../api';
import { CredentialCheck, StoreSetup } from '../types/setup.types';

interface ApiKeyStepProps {
  store: StoreSetup;
  onSaved: () => void;
}

const ApiKeyStep: React.FC<ApiKeyStepProps> = ({ store, onSaved }) => {
  const [apiKey, setApiKey] = useState('');
  const [check, setCheck] = useState<CredentialCheck | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!apiKey.trim()) {
      setError('Paste the Management API key first.');
      return;
    }
    setSaving(true);
    setError(null);
    setCheck(null);
    try {
      const { status, body } = await postJson<CredentialCheck>('/api-key', {
        baseStore: store.uid,
        apiKey: apiKey.trim(),
      });
      setCheck(body);
      if (status === 200 && body.usable) {
        setApiKey('');
        onSaved();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not reach the server.');
    } finally {
      setSaving(false);
    }
  };

  const rejected = check !== null && !check.usable;

  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200">
      <div className="px-6 py-4 border-b border-gray-200 flex items-center">
        <KeyRound className="h-5 w-5 text-gray-400 mr-2" />
        <h2 className="text-lg font-medium text-gray-900">Management API key</h2>
      </div>
      <div className="p-6">
        {store.configured ? (
          <div className="flex items-start">
            <CheckCircle2 className="h-5 w-5 text-green-500 mr-2 shrink-0 mt-0.5" />
            <p className="text-sm text-gray-700">
              <span className="font-medium">{store.name || store.uid}</span> already has a Management API
              key. Submitting a new one below replaces it.
            </p>
          </div>
        ) : (
          <p className="text-sm text-gray-600">
            Create an API credential in your Adyen Customer Area under{' '}
            <span className="font-medium">Developers &rarr; API credentials</span>, give it the two roles
            listed below, then paste its API key here. The key is verified against Adyen before it is
            stored, and it is stored encrypted against this store.
          </p>
        )}

        <form onSubmit={handleSubmit} className="mt-6">
          <label htmlFor="managementApiKey" className="block text-sm font-medium text-gray-700 mb-2">
            API key *
          </label>
          <input
            id="managementApiKey"
            type="password"
            autoComplete="off"
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            placeholder="AQE..."
            className="w-full px-3 py-2 border border-gray-300 rounded-md text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
          <p className="mt-2 text-xs text-gray-500">
            Required roles: Management API&mdash;API credentials read and write, Management
            API&mdash;Webhooks read and write.
          </p>
          <button
            type="submit"
            disabled={saving}
            className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {saving ? 'Verifying with Adyen...' : 'Verify and save'}
          </button>
        </form>

        {error && (
          <div className="mt-6 bg-red-50 border border-red-200 rounded-md p-4 flex items-start">
            <AlertCircle className="h-5 w-5 text-red-500 mr-2 shrink-0 mt-0.5" />
            <p className="text-sm text-red-800">{error}</p>
          </div>
        )}

        {check && check.usable && (
          <div className="mt-6 bg-green-50 border border-green-200 rounded-md p-4 flex items-start">
            <CheckCircle2 className="h-5 w-5 text-green-600 mr-2 shrink-0 mt-0.5" />
            <div className="text-sm text-green-900">
              <p className="font-medium">Key accepted and stored.</p>
              <p className="mt-1">
                Adyen identified it as {check.username || 'an API credential'}
                {check.companyName ? ` on company account ${check.companyName}` : ''}.
              </p>
            </div>
          </div>
        )}

        {rejected && (
          <div className="mt-6 bg-red-50 border border-red-200 rounded-md p-4">
            <div className="flex items-start">
              <AlertCircle className="h-5 w-5 text-red-500 mr-2 shrink-0 mt-0.5" />
              <div className="text-sm text-red-800">
                <p className="font-medium">Adyen did not accept this key. Nothing was stored.</p>
                {check.username && (
                  <p className="mt-1">
                    It belongs to {check.username}
                    {check.companyName ? ` on ${check.companyName}` : ''}
                    {check.active ? '.' : ', and that credential is inactive.'}
                  </p>
                )}
                {check.missingRoles.length > 0 && (
                  <>
                    <p className="mt-2">Missing roles:</p>
                    <ul className="mt-1 list-disc list-inside">
                      {check.missingRoles.map((role) => (
                        <li key={role}>{role}</li>
                      ))}
                    </ul>
                  </>
                )}
                {!check.username && (
                  <p className="mt-1">
                    Adyen rejected the credential outright, so the key, the region of the Customer Area, or
                    the configured Management API endpoint does not match.
                  </p>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ApiKeyStep;
