import React from 'react';
import { Search } from 'lucide-react';

interface SearchAndControlsProps {
  searchTerm: string;
  onSearchChange: (term: string) => void;
  pageSize: number;
  onPageSizeChange: (size: number) => void;
}

const PAGE_SIZE_OPTIONS = [5, 10, 25, 50];

const SearchAndControls: React.FC<SearchAndControlsProps> = ({
  searchTerm,
  onSearchChange,
  pageSize,
  onPageSizeChange,
}) => {
  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
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
  );
};

export default SearchAndControls;