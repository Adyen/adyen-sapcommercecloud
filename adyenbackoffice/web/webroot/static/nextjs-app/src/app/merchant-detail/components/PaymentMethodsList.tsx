'use client';

import React, { useState, useEffect } from 'react';
import { CreditCard, CheckCircle, XCircle, Globe, DollarSign, Settings, Eye } from 'lucide-react';
import { PaymentMethodData, PaymentMethodResponse } from '../../merchants/types/payment-method.types';
import LoadingSpinner from '../../merchants/components/LoadingSpinner';
import ErrorMessage from '../../merchants/components/ErrorMessage';
import Pagination from '../../components/shared/Pagination';
import PaymentMethodSettingsModal from './PaymentMethodSettingsModal';

interface PaymentMethodsListProps {
  merchantId: string;
}

const PaymentMethodsList: React.FC<PaymentMethodsListProps> = ({ merchantId }) => {
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethodData[]>([]);
  const [loading, setLoading] = useState(true);
  const [paginationLoading, setPaginationLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [totalMethods, setTotalMethods] = useState<number>(0);
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [pageSize] = useState<number>(10);
  const [totalPages, setTotalPages] = useState<number>(0);
  
  // Modal state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedPaymentMethod, setSelectedPaymentMethod] = useState<{
    id: string;
    name: string;
  } | null>(null);

  useEffect(() => {
    const fetchPaymentMethods = async (isPageChange = false) => {
      try {
        // For initial load, use main loading state
        // For page changes, use pagination loading state
        if (isPageChange) {
          setPaginationLoading(true);
        } else {
          setLoading(true);
        }
        setError(null);
        
        const params = new URLSearchParams({
          page: currentPage.toString(),
          size: pageSize.toString()
        });
        
        const response = await fetch(`/adyenbackoffice/api/merchants/${merchantId}/payment-methods?${params}`);
        
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
        setTotalPages(Math.ceil((paymentMethodResponse.itemsTotal || 0) / pageSize));
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to fetch payment methods');
      } finally {
        setLoading(false);
        setPaginationLoading(false);
      }
    };

    if (merchantId) {
      // Check if this is a page change (not initial load)
      const isPageChange = currentPage > 1 || paymentMethods.length > 0;
      fetchPaymentMethods(isPageChange);
    }
  }, [merchantId, currentPage, pageSize]);

  const handlePageChange = (page: number) => {
    setCurrentPage(page);
    // Smooth scroll to top of table
    const tableElement = document.querySelector('.payment-methods-table');
    if (tableElement) {
      tableElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

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

  const handlePaymentMethodClick = (paymentMethod: PaymentMethodData) => {
    setSelectedPaymentMethod({
      id: paymentMethod.id,
      name: paymentMethod.name
    });
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setSelectedPaymentMethod(null);
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
            {totalPages > 1 && (
              <span className="ml-2">
                (Page {currentPage} of {totalPages})
              </span>
            )}
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
        <>
          <div className="overflow-x-auto payment-methods-table">
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
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className={`bg-white divide-y divide-gray-200 ${paginationLoading ? 'opacity-50 pointer-events-none' : ''}`}>
                {paginationLoading ? (
                  // Show skeleton rows during pagination loading
                  Array.from({ length: pageSize }).map((_, index) => (
                    <tr key={`skeleton-${index}`} className="animate-pulse">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center">
                          <div className="flex-shrink-0 h-10 w-10">
                            <div className="h-10 w-10 rounded-lg bg-gray-200"></div>
                          </div>
                          <div className="ml-4">
                            <div className="h-4 bg-gray-200 rounded w-32 mb-2"></div>
                            <div className="h-3 bg-gray-200 rounded w-24"></div>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="h-6 bg-gray-200 rounded-full w-20"></div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="h-4 bg-gray-200 rounded w-16"></div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="h-4 bg-gray-200 rounded w-24"></div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="h-4 bg-gray-200 rounded w-20"></div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="h-4 bg-gray-200 rounded w-16"></div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="h-6 bg-gray-200 rounded w-24"></div>
                      </td>
                    </tr>
                  ))
                ) : (
                  paymentMethods.map((method) => (
                  <tr key={method.id} className="hover:bg-gray-50 cursor-pointer" onClick={() => handlePaymentMethodClick(method)}>
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
                    <td className="px-6 py-4 whitespace-nowrap">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handlePaymentMethodClick(method);
                        }}
                        className="inline-flex items-center px-3 py-1.5 border border-gray-300 shadow-sm text-xs font-medium rounded text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
                        title="View payment method settings"
                      >
                        <Eye className="h-3 w-3 mr-1" />
                        View Settings
                      </button>
                    </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalItems={totalMethods}
            pageSize={pageSize}
            onPageChange={handlePageChange}
            loading={paginationLoading}
          />
        </>
      )}
      
      {/* Payment Method Settings Modal */}
      {selectedPaymentMethod && (
        <PaymentMethodSettingsModal
          isOpen={isModalOpen}
          onClose={handleCloseModal}
          merchantId={merchantId}
          paymentMethodId={selectedPaymentMethod.id}
          paymentMethodName={selectedPaymentMethod.name}
        />
      )}
    </div>
  );
};

export default PaymentMethodsList;