'use client';

import React, { useCallback, useEffect, useState } from 'react';
import { AlertCircle, CheckCircle2 } from 'lucide-react';
import LoadingSpinner from '../merchants/components/LoadingSpinner';
import ApiKeyStep from './components/ApiKeyStep';
import ProvisioningStep from './components/ProvisioningStep';
import StorePicker from './components/StorePicker';
import { getJson } from './api';
import { SetupStatus, StoreSetup } from './types/setup.types';

const ConfigurationPage: React.FC = () => {
  const [status, setStatus] = useState<SetupStatus | null>(null);
  const [selectedUid, setSelectedUid] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadStatus = useCallback(async (keepSelection: boolean) => {
    try {
      setError(null);
      const { body } = await getJson<SetupStatus>('/status');
      setStatus(body);
      if (!keepSelection) {
        // Start on the store that still needs attention, so a single-store installation needs no choice.
        const pending = body.stores.find((s) => !s.configured);
        setSelectedUid((pending || body.stores[0])?.uid || '');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load the setup status.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStatus(false);
  }, [loadStatus]);

  if (loading) {
    return <LoadingSpinner />;
  }

  const stores: StoreSetup[] = status?.stores || [];
  const selected = stores.find((s) => s.uid === selectedUid) || null;

  return (
    <div className="w-full">
      <div className="p-8">
        <h1 className="text-3xl font-light text-gray-800">Configuration</h1>
        <p className="text-gray-500 mt-1">
          Connect this installation to Adyen and let it create the credentials your storefront needs.
        </p>

        {error && (
          <div className="mt-8 bg-red-50 border border-red-200 rounded-md p-4 flex items-start">
            <AlertCircle className="h-5 w-5 text-red-500 mr-2 shrink-0 mt-0.5" />
            <p className="text-sm text-red-800">{error}</p>
          </div>
        )}

        {!error && stores.length === 0 && (
          <div className="mt-8 bg-white rounded-lg shadow-sm border border-gray-200 p-6">
            <p className="text-sm text-gray-600">
              This installation has no base stores, so there is nothing to configure yet.
            </p>
          </div>
        )}

        {!error && stores.length > 0 && (
          <div className="mt-8 space-y-6">
            {status && !status.setupRequired && (
              <div className="bg-green-50 border border-green-200 rounded-md p-4 flex items-start">
                <CheckCircle2 className="h-5 w-5 text-green-600 mr-2 shrink-0 mt-0.5" />
                <p className="text-sm text-green-900">
                  Every base store has a Management API key. You can still replace a key or re-run the
                  configuration for a store below.
                </p>
              </div>
            )}

            {stores.length > 1 && (
              <StorePicker stores={stores} selectedUid={selectedUid} onSelect={setSelectedUid} />
            )}

            {selected && (
              <>
                <ApiKeyStep store={selected} onSaved={() => loadStatus(true)} />
                {selected.configured ? (
                  <ProvisioningStep store={selected} onProvisioned={() => loadStatus(true)} />
                ) : (
                  <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
                    <p className="text-sm text-gray-500">
                      Store a Management API key above to continue. The storefront credentials are created
                      with it.
                    </p>
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default ConfigurationPage;
