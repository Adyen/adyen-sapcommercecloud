import React, { useMemo } from 'react';
import { Search } from 'lucide-react';
import { MerchantFilters, FilterOption, MerchantData } from '../types/merchant.types';
import FilterControls from './FilterControls';

interface SearchAndControlsProps {
  searchTerm: string;
  onSearchChange: (term: string) => void;
  pageSize: number;
  onPageSizeChange: (size: number) => void;
  filters: MerchantFilters;
  onFiltersChange: (filters: MerchantFilters) => void;
  merchants: MerchantData[];
}

const PAGE_SIZE_OPTIONS = [5, 10, 25, 50];

const SearchAndControls: React.FC<SearchAndControlsProps> = ({
  searchTerm,
  onSearchChange,
  pageSize,
  onPageSizeChange,
  filters,
  onFiltersChange,
  merchants,
}) => {
  // Generate filter options from merchant data
  const filterOptions = useMemo(() => {
    const statusOptions: FilterOption[] = [
      { value: 'active', label: 'Active' },
      { value: 'inactive', label: 'Inactive' },
      { value: 'onboarding', label: 'Onboarding' },
    ];

    const locationOptions: FilterOption[] = Array.from(
      new Set(merchants.map(m => m.merchantCity).filter(Boolean))
    ).map(city => ({ value: city, label: city }));

    const currencyOptions: FilterOption[] = Array.from(
      new Set(merchants.map(m => m.primarySettlementCurrency).filter(Boolean))
    ).map(currency => ({ value: currency, label: currency }));

    return { statusOptions, locationOptions, currencyOptions };
  }, [merchants]);
  return (
    <div className="space-y-4 mb-6">
      <div className="bg-white rounded-lg shadow-sm border border-gray-200">
        <div className="p-6">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div className="relative flex-1 max-w-md">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search merchants..."
                value={searchTerm}
                onChange={(e) => onSearchChange(e.target.value)}
                className="pl-10 pr-4 py-2 w-full border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent text-gray-700"
              />
            </div>
            <div className="flex items-center gap-4">
              <div className="flex items-center gap-2">
                <label htmlFor="pageSize" className="text-sm font-medium text-gray-700">
                  Show:
                </label>
                <select
                  id="pageSize"
                  value={pageSize}
                  onChange={(e) => onPageSizeChange(Number(e.target.value))}
                  className="border border-gray-300 rounded-md px-3 py-1 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent text-gray-700"
                >
                  {PAGE_SIZE_OPTIONS.map((size) => (
                    <option key={size} value={size}>
                      {size}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>
        </div>
      </div>

      <FilterControls
        filters={filters}
        onFiltersChange={onFiltersChange}
        statusOptions={filterOptions.statusOptions}
        locationOptions={filterOptions.locationOptions}
        currencyOptions={filterOptions.currencyOptions}
      />
    </div>
  );
};

export default SearchAndControls;