'use client';

import React, { Suspense } from 'react';
import MerchantDetailContent from './MerchantDetailContent';

const MerchantDetailPage: React.FC = () => {
  return (
    <Suspense fallback={
      <div className="w-full p-8">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-2 text-sm text-gray-500">Loading merchant details...</p>
        </div>
      </div>
    }>
      <MerchantDetailContent />
    </Suspense>
  );
};

export default MerchantDetailPage;