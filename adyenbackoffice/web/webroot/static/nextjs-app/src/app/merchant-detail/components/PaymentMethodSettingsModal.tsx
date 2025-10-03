'use client';

import React, { useState, useEffect } from 'react';
import { X, CreditCard, CheckCircle, XCircle, Globe, DollarSign, Settings, Eye, AlertCircle } from 'lucide-react';
import { PaymentMethodSettings } from '../types/payment-method-settings.types';
import LoadingSpinner from '../../merchants/components/LoadingSpinner';
import ErrorMessage from '../../merchants/components/ErrorMessage';

interface PaymentMethodSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  merchantId: string;
  paymentMethodId: string;
  paymentMethodName: string;
}

const PaymentMethodSettingsModal: React.FC<PaymentMethodSettingsModalProps> = ({
  isOpen,
  onClose,
  merchantId,
  paymentMethodId,
  paymentMethodName
}) => {
  const [settings, setSettings] = useState<PaymentMethodSettings | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen && merchantId && paymentMethodId) {
      fetchPaymentMethodSettings();
    }
  }, [isOpen, merchantId, paymentMethodId]);

  const fetchPaymentMethodSettings = async () => {
    try {
      setLoading(true);
      setError(null);
      
      const response = await fetch(`/adyenbackoffice/api/merchants/${merchantId}/payment-method-settings/${paymentMethodId}`);
      
      if (!response.ok) {
        if (response.status === 404) {
          setError('Payment method settings not found');
          return;
        }
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const settingsData: PaymentMethodSettings = await response.json();
      setSettings(settingsData);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch payment method settings');
    } finally {
      setLoading(false);
    }
  };

  const formatValue = (value: any): string => {
    if (value === null || value === undefined) return 'N/A';
    if (typeof value === 'boolean') return value ? 'Yes' : 'No';
    if (Array.isArray(value)) return value.length > 0 ? value.join(', ') : 'None';
    if (typeof value === 'object') return JSON.stringify(value, null, 2);
    return String(value);
  };

  const renderConfigurationSection = (title: string, config: Record<string, any> | undefined) => {
    if (!config || Object.keys(config).length === 0) return null;

    return (
      <div className="mb-6">
        <h4 className="text-sm font-medium text-gray-900 mb-3 flex items-center">
          <Settings className="h-4 w-4 mr-2 text-gray-500" />
          {title}
        </h4>
        <div className="bg-gray-50 rounded-lg p-4">
          <div className="grid grid-cols-1 gap-3">
            {Object.entries(config).map(([key, value]) => (
              <div key={key} className="flex justify-between items-start">
                <span className="text-sm font-medium text-gray-600 capitalize">
                  {key.replace(/([A-Z])/g, ' $1').trim()}:
                </span>
                <span className="text-sm text-gray-900 text-right max-w-xs break-words">
                  {formatValue(value)}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  };

  const renderSpecificSettings = () => {
    if (!settings) return null;

    const specificSettings = [];

    if (settings.applePay) {
      specificSettings.push(renderConfigurationSection('Apple Pay Settings', settings.applePay));
    }
    if (settings.googlePay) {
      specificSettings.push(renderConfigurationSection('Google Pay Settings', settings.googlePay));
    }
    if (settings.paypal) {
      specificSettings.push(renderConfigurationSection('PayPal Settings', settings.paypal));
    }
    if (settings.card) {
      specificSettings.push(renderConfigurationSection('Card Settings', settings.card));
    }
    if (settings.visa) {
      specificSettings.push(renderConfigurationSection('Visa Settings', settings.visa));
    }
    if (settings.amex) {
      specificSettings.push(renderConfigurationSection('American Express Settings', settings.amex));
    }
    if (settings.klarna) {
      specificSettings.push(renderConfigurationSection('Klarna Settings', settings.klarna));
    }
    if (settings.jcb) {
      specificSettings.push(renderConfigurationSection('JCB Settings', settings.jcb));
    }
    if (settings.sepadirectdebit) {
      specificSettings.push(renderConfigurationSection('SEPA Direct Debit Settings', settings.sepadirectdebit));
    }

    return specificSettings;
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Background overlay */}
      <div
        className="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity"
        onClick={onClose}
      />

      {/* Modal panel */}
      <div className="relative bg-white rounded-lg shadow-xl w-full max-w-4xl max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="flex-shrink-0 bg-white px-6 py-4 border-b border-gray-200">
          <div className="flex items-center justify-between">
            <div className="flex items-center">
              <div className="flex-shrink-0 h-10 w-10">
                <div className="h-10 w-10 rounded-lg bg-blue-100 flex items-center justify-center">
                  <CreditCard className="h-5 w-5 text-blue-600" />
                </div>
              </div>
              <div className="ml-4">
                <h3 className="text-lg font-medium text-gray-900">
                  {paymentMethodName} Settings
                </h3>
                <p className="text-sm text-gray-500">
                  Payment Method ID: {paymentMethodId}
                </p>
              </div>
            </div>
            <button
              onClick={onClose}
              className="bg-white rounded-md text-gray-400 hover:text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <X className="h-6 w-6" />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto bg-white px-6 py-4">
            {loading ? (
              <div className="flex justify-center py-8">
                <LoadingSpinner />
              </div>
            ) : error ? (
              <div className="py-4">
                <ErrorMessage error={error} />
              </div>
            ) : settings ? (
              <div className="space-y-6">
                {/* Basic Information */}
                <div>
                  <h4 className="text-sm font-medium text-gray-900 mb-3 flex items-center">
                    <Eye className="h-4 w-4 mr-2 text-gray-500" />
                    Basic Information
                  </h4>
                  <div className="bg-gray-50 rounded-lg p-4">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div>
                        <span className="text-sm font-medium text-gray-600">Name:</span>
                        <p className="text-sm text-gray-900 mt-1">{settings.name}</p>
                      </div>
                      <div>
                        <span className="text-sm font-medium text-gray-600">Type:</span>
                        <p className="text-sm text-gray-900 mt-1">{settings.type}</p>
                      </div>
                      <div>
                        <span className="text-sm font-medium text-gray-600">Status:</span>
                        <div className="mt-1">
                          <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                            settings.enabled
                              ? 'bg-green-100 text-green-800'
                              : 'bg-red-100 text-red-800'
                          }`}>
                            {settings.enabled ? (
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
                        </div>
                      </div>
                      <div>
                        <span className="text-sm font-medium text-gray-600">Processing Type:</span>
                        <p className="text-sm text-gray-900 mt-1">{settings.processingType || 'N/A'}</p>
                      </div>
                      {settings.description && (
                        <div className="md:col-span-2">
                          <span className="text-sm font-medium text-gray-600">Description:</span>
                          <p className="text-sm text-gray-900 mt-1">{settings.description}</p>
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {/* Currencies and Countries */}
                <div>
                  <h4 className="text-sm font-medium text-gray-900 mb-3 flex items-center">
                    <Globe className="h-4 w-4 mr-2 text-gray-500" />
                    Regional Settings
                  </h4>
                  <div className="bg-gray-50 rounded-lg p-4">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div>
                        <span className="text-sm font-medium text-gray-600 flex items-center">
                          <DollarSign className="h-4 w-4 mr-1" />
                          Supported Currencies:
                        </span>
                        <p className="text-sm text-gray-900 mt-1">
                          {settings.currencies && settings.currencies.length > 0 
                            ? settings.currencies.join(', ') 
                            : 'No currencies specified'}
                        </p>
                      </div>
                      <div>
                        <span className="text-sm font-medium text-gray-600 flex items-center">
                          <Globe className="h-4 w-4 mr-1" />
                          Supported Countries:
                        </span>
                        <p className="text-sm text-gray-900 mt-1">
                          {settings.countries && settings.countries.length > 0 
                            ? settings.countries.join(', ') 
                            : 'No countries specified'}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Store Information */}
                <div>
                  <h4 className="text-sm font-medium text-gray-900 mb-3 flex items-center">
                    <Settings className="h-4 w-4 mr-2 text-gray-500" />
                    Store Configuration
                  </h4>
                  <div className="bg-gray-50 rounded-lg p-4">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div>
                        <span className="text-sm font-medium text-gray-600">Store ID:</span>
                        <p className="text-sm text-gray-900 mt-1">{settings.storeId || 'N/A'}</p>
                      </div>
                      <div>
                        <span className="text-sm font-medium text-gray-600">Business Line ID:</span>
                        <p className="text-sm text-gray-900 mt-1">{settings.businessLineId || 'N/A'}</p>
                      </div>
                      {settings.storeIds && settings.storeIds.length > 0 && (
                        <div className="md:col-span-2">
                          <span className="text-sm font-medium text-gray-600">Associated Store IDs:</span>
                          <p className="text-sm text-gray-900 mt-1">{settings.storeIds.join(', ')}</p>
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {/* Configuration Settings */}
                {renderConfigurationSection('General Configuration', settings.configuration)}
                {renderConfigurationSection('Verification Settings', settings.verificationSettings)}
                {renderConfigurationSection('Funding Source', settings.fundingSource)}
                {renderConfigurationSection('Cardholder Name', settings.cardholderName)}
                {renderConfigurationSection('Installment Options', settings.installmentOptions)}
                {renderConfigurationSection('Reference', settings.reference)}
                {renderConfigurationSection('Shopper Statement', settings.shopperStatement)}
                {renderConfigurationSection('Surcharge', settings.surcharge)}
                {renderConfigurationSection('Additional Settings', settings.additionalSettings)}

                {/* Specific Payment Method Settings */}
                {renderSpecificSettings()}

                {/* Additional Information */}
                {(settings.shopperInteraction || settings.merchantReference || settings.verificationStatus) && (
                  <div>
                    <h4 className="text-sm font-medium text-gray-900 mb-3 flex items-center">
                      <AlertCircle className="h-4 w-4 mr-2 text-gray-500" />
                      Additional Information
                    </h4>
                    <div className="bg-gray-50 rounded-lg p-4">
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {settings.shopperInteraction && (
                          <div>
                            <span className="text-sm font-medium text-gray-600">Shopper Interaction:</span>
                            <p className="text-sm text-gray-900 mt-1">{settings.shopperInteraction}</p>
                          </div>
                        )}
                        {settings.merchantReference && (
                          <div>
                            <span className="text-sm font-medium text-gray-600">Merchant Reference:</span>
                            <p className="text-sm text-gray-900 mt-1">{settings.merchantReference}</p>
                          </div>
                        )}
                        {settings.verificationStatus && (
                          <div>
                            <span className="text-sm font-medium text-gray-600">Verification Status:</span>
                            <p className="text-sm text-gray-900 mt-1">{settings.verificationStatus}</p>
                          </div>
                        )}
                        {settings.allowed !== undefined && (
                          <div>
                            <span className="text-sm font-medium text-gray-600">Allowed:</span>
                            <p className="text-sm text-gray-900 mt-1">{settings.allowed ? 'Yes' : 'No'}</p>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            ) : null}
        </div>

        {/* Footer */}
        <div className="flex-shrink-0 bg-gray-50 px-6 py-3 flex justify-end border-t border-gray-200">
          <button
            onClick={onClose}
            className="inline-flex items-center px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};

export default PaymentMethodSettingsModal;