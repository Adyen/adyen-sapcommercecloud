'use client';

import React, { useState, useEffect } from 'react';
import { Store, MapPin, Phone, Building } from 'lucide-react';
import { StoreData, StoreResponse } from '../../merchants/types/store.types';
import LoadingSpinner from '../../merchants/components/LoadingSpinner';
import ErrorMessage from '../../merchants/components/ErrorMessage';

interface StoresListProps {
  merchantId: string;
}

const StoresList: React.FC<StoresListProps> = ({ merchantId }) => {
  const [stores, setStores] = useState<StoreData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [totalStores, setTotalStores] = useState<number>(0);

  useEffect(() => {
    const fetchStores = async () => {
      try {
        setLoading(true);
        setError(null);
        
        const response = await fetch(`/adyenbackoffice/api/merchants/${merchantId}/stores`);
        
        if (!response.ok) {
          if (response.status === 404) {
            setError('No stores found for this merchant');
            return;
          }
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const storeResponse: StoreResponse = await response.json();
        setStores(storeResponse.data || []);
        setTotalStores(storeResponse.itemsTotal || 0);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to fetch stores');
      } finally {
        setLoading(false);
      }
    };

    if (merchantId) {
      fetchStores();
    }
  }, [merchantId]);

  const formatAddress = (address?: any) => {
    if (!address) return 'No address available';
    
    const parts = [
      address.line1,
      address.line2,
      address.line3,
      address.city,
      address.stateOrProvince,
      address.postalCode,
      address.country
    ].filter(Boolean);
    
    return parts.length > 0 ? parts.join(', ') : 'No address available';
  };

  if (loading) {
    return (
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <div className="flex items-center mb-4">
          <Store className="h-5 w-5 text-gray-400 mr-2" />
          <h2 className="text-lg font-medium text-gray-900">Associated Stores</h2>
        </div>
        <LoadingSpinner />
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <div className="flex items-center mb-4">
          <Store className="h-5 w-5 text-gray-400 mr-2" />
          <h2 className="text-lg font-medium text-gray-900">Associated Stores</h2>
        </div>
        <ErrorMessage error={error} />
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200">
      <div className="p-6 border-b border-gray-200">
        <div className="flex items-center justify-between">
          <div className="flex items-center">
            <Store className="h-5 w-5 text-gray-400 mr-2" />
            <h2 className="text-lg font-medium text-gray-900">Associated Stores</h2>
          </div>
          <span className="text-sm text-gray-500">
            {totalStores} {totalStores === 1 ? 'store' : 'stores'}
          </span>
        </div>
      </div>

      {stores.length === 0 ? (
        <div className="p-6 text-center">
          <Building className="mx-auto h-12 w-12 text-gray-400" />
          <h3 className="mt-2 text-sm font-medium text-gray-900">No stores found</h3>
          <p className="mt-1 text-sm text-gray-500">
            This merchant doesn't have any associated stores yet.
          </p>
        </div>
      ) : (
        <div className="divide-y divide-gray-200">
          {stores.map((store) => (
            <div key={store.id} className="p-6">
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <div className="flex items-center">
                    <h3 className="text-sm font-medium text-gray-900">
                      {store.reference || store.description || `Store ${store.id}`}
                    </h3>
                    <span className={`ml-3 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                      store.status === 'active' ? 'bg-green-100 text-green-800' :
                      store.status === 'inactive' ? 'bg-red-100 text-red-800' :
                      'bg-yellow-100 text-yellow-800'
                    }`}>
                      {store.status}
                    </span>
                  </div>
                  
                  <p className="text-xs text-gray-500 mt-1">
                    Store ID: {store.id}
                  </p>

                  {store.description && store.description !== store.reference && (
                    <p className="text-sm text-gray-600 mt-2">
                      {store.description}
                    </p>
                  )}

                  <div className="mt-3 space-y-2">
                    {store.address && (
                      <div className="flex items-start">
                        <MapPin className="h-4 w-4 text-gray-400 mr-2 mt-0.5 flex-shrink-0" />
                        <span className="text-sm text-gray-600">
                          {formatAddress(store.address)}
                        </span>
                      </div>
                    )}

                    {store.phoneNumber && (
                      <div className="flex items-center">
                        <Phone className="h-4 w-4 text-gray-400 mr-2 flex-shrink-0" />
                        <span className="text-sm text-gray-600">
                          {store.phoneNumber}
                        </span>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default StoresList;