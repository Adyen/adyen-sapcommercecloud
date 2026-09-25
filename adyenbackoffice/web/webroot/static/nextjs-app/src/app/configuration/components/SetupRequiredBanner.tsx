'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { AlertCircle } from 'lucide-react';
import { getJson } from '../api';
import { SetupStatus } from '../types/setup.types';

/**
 * Shown on the dashboard while any base store still lacks a Management API key. Silent on failure:
 * a nudge that cannot load its own status has nothing useful to say to a merchant.
 */
const SetupRequiredBanner: React.FC = () => {
  const [pending, setPending] = useState<string[]>([]);

  useEffect(() => {
    let cancelled = false;
    getJson<SetupStatus>('/status')
      .then(({ body }) => {
        if (!cancelled && body.setupRequired) {
          setPending(body.stores.filter((s) => !s.configured).map((s) => s.name || s.uid));
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  if (pending.length === 0) {
    return null;
  }

  return (
    <div className="mt-8 bg-yellow-50 border border-yellow-200 rounded-md p-4 flex items-start">
      <AlertCircle className="h-5 w-5 text-yellow-600 mr-2 shrink-0 mt-0.5" />
      <div className="text-sm text-yellow-900">
        <p className="font-medium">Adyen is not configured yet.</p>
        <p className="mt-1">
          {pending.length === 1
            ? `Store ${pending[0]} has no Management API key.`
            : `${pending.length} stores have no Management API key: ${pending.join(', ')}.`}
        </p>
        <Link
          href="/configuration"
          className="inline-block mt-3 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
        >
          Go to configuration
        </Link>
      </div>
    </div>
  );
};

export default SetupRequiredBanner;
