import React from 'react';
import { Settings, Globe, CreditCard, Link, Webhook, ArrowLeft } from 'lucide-react';
import { MerchantDetailData } from '../../types/merchant-detail.types';

interface ConfigurationDetailsProps {
  merchant: MerchantDetailData;
}

const ConfigurationDetails: React.FC<ConfigurationDetailsProps> = ({ merchant }) => {
  const { configuration } = merchant;

  return (
    <div className="p-6">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Currency Configuration */}
        <div className="space-y-6">
          <div>
            <h3 className="text-lg font-medium text-gray-900 mb-4 flex items-center">
              <CreditCard className="h-5 w-5 mr-2 text-gray-400" />
              Currency Configuration
            </h3>
            <div className="space-y-4">
              <div>
                <dt className="text-sm font-medium text-gray-500">Primary Settlement Currency</dt>
                <dd className="mt-1">
                  <span className="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-blue-100 text-blue-800">
                    {merchant.primarySettlementCurrency || 'N/A'}
                  </span>
                </dd>
              </div>

              {configuration?.currencies && configuration.currencies.length > 0 && (
                <div>
                  <dt className="text-sm font-medium text-gray-500 mb-2">Supported Currencies</dt>
                  <dd className="mt-1">
                    <div className="flex flex-wrap gap-2">
                      {configuration.currencies.map((currency: string) => (
                        <span
                          key={currency}
                          className="inline-flex items-center px-2 py-1 rounded-md text-xs font-medium bg-gray-100 text-gray-800"
                        >
                          {currency}
                        </span>
                      ))}
                    </div>
                  </dd>
                </div>
              )}
            </div>
          </div>

          {/* API Endpoints */}
          <div>
            <h3 className="text-lg font-medium text-gray-900 mb-4 flex items-center">
              <Globe className="h-5 w-5 mr-2 text-gray-400" />
              API Endpoints
            </h3>
            <div className="space-y-4">
              {configuration?.liveEndpointPrefix && (
                <div>
                  <dt className="text-sm font-medium text-gray-500">Live Endpoint Prefix</dt>
                  <dd className="mt-1 text-sm text-gray-900 font-mono bg-gray-50 px-3 py-2 rounded-md">
                    {configuration.liveEndpointPrefix}
                  </dd>
                </div>
              )}

              {configuration?.testEndpointPrefix && (
                <div>
                  <dt className="text-sm font-medium text-gray-500">Test Endpoint Prefix</dt>
                  <dd className="mt-1 text-sm text-gray-900 font-mono bg-gray-50 px-3 py-2 rounded-md">
                    {configuration.testEndpointPrefix}
                  </dd>
                </div>
              )}

              {!configuration?.liveEndpointPrefix && !configuration?.testEndpointPrefix && (
                <div className="text-sm text-gray-500 italic">
                  No endpoint configuration available
                </div>
              )}
            </div>
          </div>

          {/* Allowed Origins */}
          {configuration?.allowedOrigins && configuration.allowedOrigins.length > 0 && (
            <div>
              <h3 className="text-lg font-medium text-gray-900 mb-4 flex items-center">
                <Settings className="h-5 w-5 mr-2 text-gray-400" />
                Security Configuration
              </h3>
              <div>
                <dt className="text-sm font-medium text-gray-500 mb-2">Allowed Origins</dt>
                <dd className="mt-1">
                  <div className="space-y-2">
                    {configuration.allowedOrigins.map((origin: string, index: number) => (
                      <div
                        key={index}
                        className="text-sm text-gray-900 font-mono bg-gray-50 px-3 py-2 rounded-md"
                      >
                        {origin}
                      </div>
                    ))}
                  </div>
                </dd>
              </div>
            </div>
          )}
        </div>

        {/* Integration Configuration */}
        <div className="space-y-6">
          {/* Webhook Configuration */}
          {configuration?.webhookUrl && (
            <div>
              <h3 className="text-lg font-medium text-gray-900 mb-4 flex items-center">
                <Webhook className="h-5 w-5 mr-2 text-gray-400" />
                Webhook Configuration
              </h3>
              <div className="space-y-4">
                <div>
                  <dt className="text-sm font-medium text-gray-500">Webhook URL</dt>
                  <dd className="mt-1 text-sm text-gray-900 font-mono bg-gray-50 px-3 py-2 rounded-md break-all">
                    {configuration.webhookUrl}
                  </dd>
                </div>
              </div>
            </div>
          )}

          {/* Return URL Configuration */}
          {configuration?.returnUrl && (
            <div>
              <h3 className="text-lg font-medium text-gray-900 mb-4 flex items-center">
                <ArrowLeft className="h-5 w-5 mr-2 text-gray-400" />
                Return URL Configuration
              </h3>
              <div className="space-y-4">
                <div>
                  <dt className="text-sm font-medium text-gray-500">Return URL</dt>
                  <dd className="mt-1 text-sm text-gray-900 font-mono bg-gray-50 px-3 py-2 rounded-md break-all">
                    {configuration.returnUrl}
                  </dd>
                </div>
              </div>
            </div>
          )}

          {/* Website Configuration */}
          {merchant.shopWebAddress && (
            <div>
              <h3 className="text-lg font-medium text-gray-900 mb-4 flex items-center">
                <Link className="h-5 w-5 mr-2 text-gray-400" />
                Website Configuration
              </h3>
              <div className="space-y-4">
                <div>
                  <dt className="text-sm font-medium text-gray-500">Shop Web Address</dt>
                  <dd className="mt-1 text-sm text-gray-900">
                    <a
                      href={merchant.shopWebAddress}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-blue-600 hover:text-blue-800 hover:underline font-mono bg-gray-50 px-3 py-2 rounded-md inline-block"
                    >
                      {merchant.shopWebAddress}
                    </a>
                  </dd>
                </div>
              </div>
            </div>
          )}

          {/* Configuration Summary */}
          <div>
            <h3 className="text-lg font-medium text-gray-900 mb-4 flex items-center">
              <Settings className="h-5 w-5 mr-2 text-gray-400" />
              Configuration Summary
            </h3>
            <div className="bg-gray-50 rounded-lg p-4 space-y-3">
              <div className="flex justify-between items-center">
                <span className="text-sm font-medium text-gray-500">Currencies Supported</span>
                <span className="text-sm text-gray-900">
                  {configuration?.currencies?.length || 1}
                </span>
              </div>
              
              <div className="flex justify-between items-center">
                <span className="text-sm font-medium text-gray-500">Live Endpoint Configured</span>
                <span className={`text-sm ${configuration?.liveEndpointPrefix ? 'text-green-600' : 'text-red-600'}`}>
                  {configuration?.liveEndpointPrefix ? 'Yes' : 'No'}
                </span>
              </div>
              
              <div className="flex justify-between items-center">
                <span className="text-sm font-medium text-gray-500">Test Endpoint Configured</span>
                <span className={`text-sm ${configuration?.testEndpointPrefix ? 'text-green-600' : 'text-red-600'}`}>
                  {configuration?.testEndpointPrefix ? 'Yes' : 'No'}
                </span>
              </div>
              
              <div className="flex justify-between items-center">
                <span className="text-sm font-medium text-gray-500">Webhook Configured</span>
                <span className={`text-sm ${configuration?.webhookUrl ? 'text-green-600' : 'text-red-600'}`}>
                  {configuration?.webhookUrl ? 'Yes' : 'No'}
                </span>
              </div>
              
              <div className="flex justify-between items-center">
                <span className="text-sm font-medium text-gray-500">Return URL Configured</span>
                <span className={`text-sm ${configuration?.returnUrl ? 'text-green-600' : 'text-red-600'}`}>
                  {configuration?.returnUrl ? 'Yes' : 'No'}
                </span>
              </div>
              
              <div className="flex justify-between items-center">
                <span className="text-sm font-medium text-gray-500">Allowed Origins</span>
                <span className="text-sm text-gray-900">
                  {configuration?.allowedOrigins?.length || 0}
                </span>
              </div>
            </div>
          </div>

          {/* Technical Details */}
          <div>
            <h3 className="text-lg font-medium text-gray-900 mb-4">Technical Details</h3>
            <div className="space-y-3 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-500">Merchant Account ID:</span>
                <span className="text-gray-900 font-mono">{merchant.id}</span>
              </div>
              
              {merchant.companyId && (
                <div className="flex justify-between">
                  <span className="text-gray-500">Company ID:</span>
                  <span className="text-gray-900 font-mono">{merchant.companyId}</span>
                </div>
              )}
              
              <div className="flex justify-between">
                <span className="text-gray-500">Status:</span>
                <span className={`font-medium ${
                  merchant.status === 'active' ? 'text-green-600' : 
                  merchant.status === 'inactive' ? 'text-red-600' : 
                  'text-yellow-600'
                }`}>
                  {merchant.status}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ConfigurationDetails;