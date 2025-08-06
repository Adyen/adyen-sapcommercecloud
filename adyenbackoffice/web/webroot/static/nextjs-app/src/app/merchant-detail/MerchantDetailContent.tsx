'use client';

import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import MerchantDetailClient from './components/MerchantDetailClient';

const MerchantDetailContent: React.FC = () => {
  const searchParams = useSearchParams();
  const [merchantId, setMerchantId] = useState<string | null>(null);

  useEffect(() => {
    const id = searchParams.get('id');
    setMerchantId(id);
  }, [searchParams]);

  if (!merchantId) {
    return (
      <div className="w-full p-8">
        <div className="text-center">
          <h3 className="text-lg font-medium text-gray-900">No merchant selected</h3>
          <p className="mt-1 text-sm text-gray-500">
            Please provide a merchant ID in the URL parameters.
          </p>
        </div>
      </div>
    );
  }

  return <MerchantDetailClient merchantId={merchantId} />;
};

export default MerchantDetailContent;