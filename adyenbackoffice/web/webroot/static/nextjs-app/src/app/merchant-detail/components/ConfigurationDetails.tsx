import React from 'react';
import { MerchantDetailData } from '../../merchants/types/merchant-detail.types';

interface ConfigurationDetailsProps {
  merchant: MerchantDetailData;
}

const ConfigurationDetails: React.FC<ConfigurationDetailsProps> = ({ merchant }) => {
  return (
    <div className="p-6">
      <h2 className="text-lg font-medium text-gray-900 mb-4">Configuration Details</h2>
      
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Basic Information */}
        <div className="space-y-4">
          <h3 className="text-sm font-medium text-gray-700 border-b border-gray-200 pb-2">
            Basic Information
          </h3>
          
          <div>
            <dt className="text-sm font-medium text-gray-500">Merchant City</dt>
            <dd className="mt-1 text-sm text-gray-900">{merchant.merchantCity || '-'}</dd>
          </div>
          
          <div>
            <dt className="text-sm font-medium text-gray-500">Primary Settlement Currency</dt>
            <dd className="mt-1 text-sm text-gray-900">{merchant.primarySettlementCurrency || '-'}</dd>
          </div>
          
          <div>
            <dt className="text-sm font-medium text-gray-500">Shop Web Address</dt>
            <dd className="mt-1 text-sm text-gray-900">
              {merchant.shopWebAddress ? (
                <a 
                  href={merchant.shopWebAddress} 
                  target="_blank" 
                  rel="noopener noreferrer"
                  className="text-blue-600 hover:text-blue-800 hover:underline"
                >
                  {merchant.shopWebAddress}
                </a>
              ) : '-'}
            </dd>
          </div>
          
          {merchant.companyId && (
            <div>
              <dt className="text-sm font-medium text-gray-500">Company ID</dt>
              <dd className="mt-1 text-sm text-gray-900">{merchant.companyId}</dd>
            </div>
          )}
        </div>

        {/* Configuration Settings */}
        {merchant.configuration && (
          <div className="space-y-4">
            <h3 className="text-sm font-medium text-gray-700 border-b border-gray-200 pb-2">
              Configuration Settings
            </h3>
            
            {merchant.configuration.currencies && (
              <div>
                <dt className="text-sm font-medium text-gray-500">Supported Currencies</dt>
                <dd className="mt-1 text-sm text-gray-900">
                  <div className="flex flex-wrap gap-1">
                    {merchant.configuration.currencies.map((currency) => (
                      <span 
                        key={currency}
                        className="inline-flex items-center px-2 py-1 rounded-md text-xs font-medium bg-blue-100 text-blue-800"
                      >
                        {currency}
                      </span>
                    ))}
                  </div>
                </dd>
              </div>
            )}
            
            {merchant.configuration.liveEndpointPrefix && (
              <div>
                <dt className="text-sm font-medium text-gray-500">Live Endpoint Prefix</dt>
                <dd className="mt-1 text-sm text-gray-900 font-mono text-xs bg-gray-50 p-2 rounded">
                  {merchant.configuration.liveEndpointPrefix}
                </dd>
              </div>
            )}
            
            {merchant.configuration.testEndpointPrefix && (
              <div>
                <dt className="text-sm font-medium text-gray-500">Test Endpoint Prefix</dt>
                <dd className="mt-1 text-sm text-gray-900 font-mono text-xs bg-gray-50 p-2 rounded">
                  {merchant.configuration.testEndpointPrefix}
                </dd>
              </div>
            )}
            
            {merchant.configuration.allowedOrigins && (
              <div>
                <dt className="text-sm font-medium text-gray-500">Allowed Origins</dt>
                <dd className="mt-1 text-sm text-gray-900">
                  <ul className="space-y-1">
                    {merchant.configuration.allowedOrigins.map((origin, index) => (
                      <li key={index} className="font-mono text-xs bg-gray-50 p-1 rounded">
                        {origin}
                      </li>
                    ))}
                  </ul>
                </dd>
              </div>
            )}
            
            {merchant.configuration.webhookUrl && (
              <div>
                <dt className="text-sm font-medium text-gray-500">Webhook URL</dt>
                <dd className="mt-1 text-sm text-gray-900 font-mono text-xs bg-gray-50 p-2 rounded break-all">
                  {merchant.configuration.webhookUrl}
                </dd>
              </div>
            )}
            
            {merchant.configuration.returnUrl && (
              <div>
                <dt className="text-sm font-medium text-gray-500">Return URL</dt>
                <dd className="mt-1 text-sm text-gray-900 font-mono text-xs bg-gray-50 p-2 rounded break-all">
                  {merchant.configuration.returnUrl}
                </dd>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default ConfigurationDetails;