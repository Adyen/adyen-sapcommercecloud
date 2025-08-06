import React from 'react';
import { useRouter } from 'next/navigation';
import { Building2 } from 'lucide-react';
import { MerchantData } from '../types/merchant.types';
import StatusBadge from './StatusBadge';

interface MerchantsTableProps {
  merchants: MerchantData[];
  searchTerm: string;
}

const MerchantsTable: React.FC<MerchantsTableProps> = ({ merchants, searchTerm }) => {
  if (merchants.length === 0) {
    return (
      <div className="bg-white rounded-lg shadow-sm border border-gray-200">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <TableHeader />
            <tbody className="bg-white divide-y divide-gray-200">
              <tr>
                <td colSpan={6} className="px-6 py-12 text-center">
                  <Building2 className="mx-auto h-12 w-12 text-gray-400" />
                  <h3 className="mt-2 text-sm font-medium text-gray-900">No merchants found</h3>
                  <p className="mt-1 text-sm text-gray-500">
                    {searchTerm ? 'Try adjusting your search criteria.' : 'No merchant accounts are available.'}
                  </p>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200">
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <TableHeader />
          <tbody className="bg-white divide-y divide-gray-200">
            {merchants.map((merchant) => (
              <MerchantRow key={merchant.id} merchant={merchant} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

const TableHeader: React.FC = () => (
  <thead className="bg-gray-50">
    <tr>
      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
        Merchant Account ID
      </th>
      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
        Merchant Name
      </th>
      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
        Status
      </th>
      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
        Location
      </th>
      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
        Currency
      </th>
      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
        Website
      </th>
    </tr>
  </thead>
);

interface MerchantRowProps {
  merchant: MerchantData;
}

const MerchantRow: React.FC<MerchantRowProps> = ({ merchant }) => {
  const router = useRouter();

  const handleRowClick = () => {
    router.push(`/merchant-detail?id=${merchant.id}`);
  };

  return (
    <tr
      className="hover:bg-gray-50 cursor-pointer transition-colors"
      onClick={handleRowClick}
    >
      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-blue-600 hover:text-blue-800">
        {merchant.id}
      </td>
      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
        {merchant.name || '-'}
      </td>
      <td className="px-6 py-4 whitespace-nowrap">
        <StatusBadge status={merchant.status} />
      </td>
      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
        {merchant.merchantCity || '-'}
      </td>
      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
        {merchant.primarySettlementCurrency || '-'}
      </td>
      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
        {merchant.shopWebAddress ? (
          <a
            href={merchant.shopWebAddress}
            target="_blank"
            rel="noopener noreferrer"
            className="text-blue-600 hover:text-blue-800 hover:underline"
            onClick={(e) => e.stopPropagation()} // Prevent row click when clicking website link
          >
            {merchant.shopWebAddress}
          </a>
        ) : (
          '-'
        )}
      </td>
    </tr>
  );
};

export default MerchantsTable;