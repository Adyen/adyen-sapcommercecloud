'use client';

import React, { useState, useEffect } from 'react';
import { CreditCard, CheckCircle, XCircle, Globe, DollarSign, Settings } from 'lucide-react';
import { PaymentMethodData, PaymentMethodResponse } from '../../merchants/types/payment-method.types';
import LoadingSpinner from '../../merchants/components/LoadingSpinner';
import ErrorMessage from '../../merchants/components/ErrorMessage';

interface PaymentMethodsListProps {
  merchantId: string;
}

const PaymentMethodsList: React.FC<PaymentMethodsListProps> = ({ merchantId }) => {
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethodData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [totalMethods, setTotalMethods] = useState<number>(0);

  useEffect(() => {
    const fetchPaymentMethods = async () => {
      try {
        setLoading(true);
        setError(null);
        
        const response = await fetch(`/adyenbackoffice/api/merchants/${merchantId}/payment-methods`);
        
        if (!response.ok) {
          if (response.status === 404) {
            setError('No payment methods found for this merchant');
            return;
          }
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const paymentMethodResponse: PaymentMethodResponse = await response.json();
        setPaymentMethods(paymentMethodResponse.data || []);
        setTotalMethods(paymentMethodResponse.itemsTotal || 0);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to fetch payment methods');
      } finally {
        setLoading(false);
      }
    };

    if (merchantId) {
      fetchPaymentMethods();
    }
  }, [merchantId]);

  const formatCurrencies = (currencies?: string[]) => {
    if (!currencies || currencies.length === 0) return 'No currencies specified';
    if (currencies.length <= 3) return currencies.join(', ');
    return `${currencies.slice(0, 3).join(', ')} +${currencies.length - 3} more`;
  };

  const formatCountries = (countries?: string[]) => {
    if (!countries || countries.length === 0) return 'No countries specified';
    if (countries.length <= 3) return countries.join(', ');
    return `${countries.slice(0, 3).join(', ')} +${countries.length - 3} more`;
  };

  if (loading) {
    return (
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <div className="flex items-center mb-4">
          <CreditCard className="h-5 w-5 text-gray-400 mr-2" />
          <h2 className="text-lg font-medium text-gray-900">Payment Methods</h2>
        </div>
        <LoadingSpinner />
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <div className="flex items-center mb-4">
          <CreditCard className="h-5 w-5 text-gray-400 mr-2" />
          <h2 className="text-lg font-medium text-gray-900">Payment Methods</h2>
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
            <CreditCard className="h-5 w-5 text-gray-400 mr-2" />
            <h2 className="text-lg font-medium text-gray-900">Payment Methods</h2>
          </div>
          <span className="text-sm text-gray-500">
            {totalMethods} {totalMethods === 1 ? 'method' : 'methods'}
          </span>
        </div>
      </div>

      {paymentMethods.length === 0 ? (
        <div className="p-6 text-center">
          <CreditCard className="mx-auto h-12 w-12 text-gray-400" />
          <h3 className="mt-2 text-sm font-medium text-gray-900">No payment methods found</h3>
          <p className="mt-1 text-sm text-gray-500">
            This merchant doesn't have any configured payment methods yet.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Payment Method
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Type
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Currencies
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Countries
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Store ID
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {paymentMethods.map((method) => (
                <tr key={method.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center">
                      <div className="flex-shrink-0 h-10 w-10">
                        <div className="h-10 w-10 rounded-lg bg-blue-100 flex items-center justify-center">
                          <CreditCard className="h-5 w-5 text-blue-600" />
                        </div>
                      </div>
                      <div className="ml-4">
                        <div className="text-sm font-medium text-gray-900">
                          {method.name}
                        </div>
                        <div className="text-sm text-gray-500">
                          ID: {method.id}
                        </div>
                        {method.description && (
                          <div className="text-xs text-gray-400 mt-1">
                            {method.description}
                          </div>
                        )}
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                      method.enabled 
                        ? 'bg-green-100 text-green-800' 
                        : 'bg-red-100 text-red-800'
                    }`}>
                      {method.enabled ? (
                        <>
                          <CheckCircle className="h-3 w-3 mr-1" />
                          Enabled
                        </>
                      ) : (
                        <>
                          <XCircle className="h-3 w-3 mr-1" />
                          Disabled
                        </>
                      )}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm text-gray-900">{method.type}</div>
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-start">
                      <DollarSign className="h-4 w-4 text-gray-400 mr-1 mt-0.5 flex-shrink-0" />
                      <span className="text-sm text-gray-600">
                        {formatCurrencies(method.currencies)}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-start">
                      <Globe className="h-4 w-4 text-gray-400 mr-1 mt-0.5 flex-shrink-0" />
                      <span className="text-sm text-gray-600">
                        {formatCountries(method.countries)}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm text-gray-900">
                      {method.storeId || 'N/A'}
                    </div>
                    {method.businessLineId && (
                      <div className="text-xs text-gray-500">
                        Business Line: {method.businessLineId}
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default PaymentMethodsList;