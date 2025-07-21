import React, { useState, useEffect } from 'react';
import { ChevronDown, Building } from 'lucide-react';

interface Merchant {
  name: string;
  merchantId: string;
  environment: string;
}

interface AccountSelectorProps {
  isCollapsed: boolean;
}

const AccountSelector: React.FC<AccountSelectorProps> = ({ isCollapsed }) => {
  const [merchants, setMerchants] = useState<Merchant[]>([]);
  const [selectedMerchant, setSelectedMerchant] = useState<Merchant | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchMerchants = async () => {
      try {
        const response = await fetch('/adyenbackoffice/api/merchants');
        if (!response.ok) throw new Error('Failed to fetch merchants');
        
        const data = await response.json();
        // Set the list of merchants
        if (data.data && data.data.length > 0) {
          setMerchants(data.data.map((merchant: any) => ({
            name: merchant.name,
            merchantId: merchant.id, // Using id instead of merchantId since that's what's in the response
            environment: 'TEST' // Default environment since it's not provided in the sample
          })));
          
          // Set the first merchant as default
          setSelectedMerchant({
            name: data.data[0].name,
            merchantId: data.data[0].id, 
            environment: 'TEST'
          });
        }
      } catch (error) {
        console.error('Error fetching merchants:', error);
      }
      setLoading(false);
    };

    fetchMerchants();
  }, []);

  if (loading || !selectedMerchant || merchants.length === 0) {
    return (
      <div className={`p-4 border-b border-gray-700 ${isCollapsed ? 'text-center' : ''}`}>
        {isCollapsed ? (
          <div className="flex justify-center">
            <Building className="h-6 w-6 text-blue-400" />
          </div>
        ) : (
          <div className="flex items-center justify-between">
            <div className="flex items-center">
              <Building className="h-5 w-5 text-blue-400 mr-2" />
              <span className="font-medium text-white truncate">
                Loading...
              </span>
            </div>
            <ChevronDown className="h-4 w-4 text-gray-400" />
          </div>
        )}
      </div>
    );
  }

  return (
    <div className={`p-4 border-b border-gray-700 ${isCollapsed ? 'text-center' : ''}`}>
      {isCollapsed ? (
        <div className="flex justify-center">
          <Building className="h-6 w-6 text-blue-400" />
        </div>
      ) : (
        <>
          <div className="flex items-center justify-between">
            <div className="flex items-center">
              <Building className="h-5 w-5 text-blue-400 mr-2" />
              <span 
                className="font-medium text-white truncate"
                onClick={() => {
                  // Toggle dropdown when clicking the merchant name
                  const dropdown = document.getElementById('merchant-dropdown');
                  if (dropdown) {
                    dropdown.classList.toggle('hidden');
                  }
                }}
                style={{ cursor: 'pointer' }}
              >
                {selectedMerchant.name}
              </span>
            </div>
            <ChevronDown className="h-4 w-4 text-gray-400" />
          </div>
          
          {/* Dropdown for selecting merchant */}
          <div 
            id="merchant-dropdown"
            className="absolute mt-2 bg-white rounded-md shadow-lg z-10 hidden"
            style={{ minWidth: '200px' }}
          >
            <ul className="py-1">
              {merchants.map((merchant, index) => (
                <li 
                  key={index}
                  className={`px-4 py-2 hover:bg-gray-100 cursor-pointer ${selectedMerchant && merchant.name === selectedMerchant.name ? 'bg-blue-50 text-blue-600' : ''}`}
                  onClick={() => {
                    setSelectedMerchant(merchant);
                    const dropdown = document.getElementById('merchant-dropdown');
                    if (dropdown) {
                      dropdown.classList.add('hidden');
                    }
                  }}
                >
                  {merchant.name}
                </li>
              ))}
            </ul>
          </div>
          
          <div className="mt-2 flex flex-col">
            <span className="text-xs text-gray-400">
              Merchant ID: {selectedMerchant.merchantId}
            </span>
            <span className="text-xs text-gray-400">
              Environment: <span className="text-green-400">{selectedMerchant.environment}</span>
            </span>
          </div>
        </>
      )}
    </div>
  );
};

export default AccountSelector;