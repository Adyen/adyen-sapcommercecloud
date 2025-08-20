import React from 'react';
import { Filter } from 'lucide-react';
import { MerchantFilters, FilterOption } from '../types/merchant.types';

interface FilterControlsProps {
  filters: MerchantFilters;
  onFiltersChange: (filters: MerchantFilters) => void;
  statusOptions: FilterOption[];
  locationOptions: FilterOption[];
  currencyOptions: FilterOption[];
}

const FilterControls: React.FC<FilterControlsProps> = ({
  filters,
  onFiltersChange,
  statusOptions,
  locationOptions,
  currencyOptions,
}) => {
  const handleFilterChange = (filterType: keyof MerchantFilters, value: string) => {
    onFiltersChange({
      ...filters,
      [filterType]: value,
    });
  };

  const clearAllFilters = () => {
    onFiltersChange({
      status: '',
      location: '',
      currency: '',
    });
  };

  const hasActiveFilters = filters.status || filters.location || filters.currency;

  return (
    <div className="bg-gray-50 rounded-lg border border-gray-200 p-4">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Filter className="h-4 w-4 text-gray-500" />
          <span className="text-sm font-medium text-gray-700">Filters</span>
        </div>
        {hasActiveFilters && (
          <button
            onClick={clearAllFilters}
            className="text-xs text-blue-600 hover:text-blue-800 font-medium"
          >
            Clear all
          </button>
        )}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Status Filter */}
        <div className="flex flex-col gap-1">
          <label htmlFor="status-filter" className="text-xs font-medium text-gray-600">
            Status
          </label>
          <select
            id="status-filter"
            value={filters.status}
            onChange={(e) => handleFilterChange('status', e.target.value)}
            className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent text-gray-700 bg-white"
          >
            <option value="">All Statuses</option>
            {statusOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        {/* Location Filter */}
        <div className="flex flex-col gap-1">
          <label htmlFor="location-filter" className="text-xs font-medium text-gray-600">
            Location
          </label>
          <select
            id="location-filter"
            value={filters.location}
            onChange={(e) => handleFilterChange('location', e.target.value)}
            className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent text-gray-700 bg-white"
          >
            <option value="">All Locations</option>
            {locationOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        {/* Currency Filter */}
        <div className="flex flex-col gap-1">
          <label htmlFor="currency-filter" className="text-xs font-medium text-gray-600">
            Currency
          </label>
          <select
            id="currency-filter"
            value={filters.currency}
            onChange={(e) => handleFilterChange('currency', e.target.value)}
            className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent text-gray-700 bg-white"
          >
            <option value="">All Currencies</option>
            {currencyOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {hasActiveFilters && (
        <div className="mt-3 flex flex-wrap gap-2">
          {filters.status && (
            <span className="inline-flex items-center gap-1 px-2 py-1 bg-blue-100 text-blue-800 text-xs rounded-full">
              Status: {statusOptions.find(opt => opt.value === filters.status)?.label || filters.status}
              <button
                onClick={() => handleFilterChange('status', '')}
                className="ml-1 hover:text-blue-600"
              >
                ×
              </button>
            </span>
          )}
          {filters.location && (
            <span className="inline-flex items-center gap-1 px-2 py-1 bg-green-100 text-green-800 text-xs rounded-full">
              Location: {locationOptions.find(opt => opt.value === filters.location)?.label || filters.location}
              <button
                onClick={() => handleFilterChange('location', '')}
                className="ml-1 hover:text-green-600"
              >
                ×
              </button>
            </span>
          )}
          {filters.currency && (
            <span className="inline-flex items-center gap-1 px-2 py-1 bg-purple-100 text-purple-800 text-xs rounded-full">
              Currency: {currencyOptions.find(opt => opt.value === filters.currency)?.label || filters.currency}
              <button
                onClick={() => handleFilterChange('currency', '')}
                className="ml-1 hover:text-purple-600"
              >
                ×
              </button>
            </span>
          )}
        </div>
      )}
    </div>
  );
};

export default FilterControls;