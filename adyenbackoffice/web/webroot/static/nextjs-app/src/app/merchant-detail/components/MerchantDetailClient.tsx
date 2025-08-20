'use client';

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowLeft, Building2 } from 'lucide-react';
import { MerchantDetailData } from '../../merchants/types/merchant-detail.types';
import ConfigurationDetails from './ConfigurationDetails';
import StoresList from './StoresList';
import LoadingSpinner from '../../merchants/components/LoadingSpinner';
import ErrorMessage from '../../merchants/components/ErrorMessage';

interface MerchantDetailClientProps {
  merchantId: string;
}

const MerchantDetailClient: React.FC<MerchantDetailClientProps> = ({ merchantId }) => {
  const router = useRouter();
  const [merchant, setMerchant] = useState<MerchantDetailData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchMerchantDetail = async () => {
      try {
        setLoading(true);
        setError(null);
        
        const response = await fetch(`/adyenbackoffice/api/merchants/${merchantId}`);
        
        if (!response.ok) {
          if (response.status === 404) {
            setError('Merchant not found');
            return;
          }
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const merchantData: MerchantDetailData = await response.json();
        setMerchant(merchantData);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to fetch merchant details');
      } finally {
        setLoading(false);
      }
    };

    if (merchantId) {
      fetchMerchantDetail();
    }
  }, [merchantId]);

  const handleBackClick = () => {
    router.push('/merchants');
  };

  if (loading) {
    return <LoadingSpinner />;
  }

  if (error) {
    return <ErrorMessage error={error} />;
  }

  if (!merchant) {
    return (
      <div className="w-full">
        <div className="p-8">
          <div className="text-center">
            <Building2 className="mx-auto h-12 w-12 text-gray-400" />
            <h3 className="mt-2 text-sm font-medium text-gray-900">Merchant not found</h3>
            <p className="mt-1 text-sm text-gray-500">
              The requested merchant could not be found.
            </p>
            <button
              onClick={handleBackClick}
              className="mt-4 inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700"
            >
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Merchants
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="w-full">
      <div className="p-8">
        {/* Header */}
        <div className="mb-6">
          <button
            onClick={handleBackClick}
            className="inline-flex items-center text-sm text-gray-500 hover:text-gray-700 mb-4"
          >
            <ArrowLeft className="h-4 w-4 mr-1" />
            Back to Merchants
          </button>
          
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-2xl font-bold text-gray-900">
                {merchant.name}
              </h1>
              <p className="text-sm text-gray-500 mt-1">
                Merchant Account ID: {merchant.id}
              </p>
            </div>
            
            <div className="flex items-center space-x-3">
              <span className={`inline-flex items-center px-3 py-1 rounded-full text-sm font-medium ${
                merchant.status === 'active' ? 'bg-green-100 text-green-800' :
                merchant.status === 'inactive' ? 'bg-red-100 text-red-800' :
                'bg-yellow-100 text-yellow-800'
              }`}>
                {merchant.status}
              </span>
            </div>
          </div>
        </div>

        {/* Configuration Details */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
          <ConfigurationDetails merchant={merchant} />
        </div>

        {/* Associated Stores */}
        <StoresList merchantId={merchant.id} />
      </div>
    </div>
  );
};

export default MerchantDetailClient;