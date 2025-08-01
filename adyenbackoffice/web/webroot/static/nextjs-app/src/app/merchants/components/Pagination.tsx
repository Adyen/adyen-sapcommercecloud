import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { calculatePaginationRange, formatPaginationText } from '../utils/merchant.utils';

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  pageSize: number;
  totalItems: number;
  onPageChange: (page: number) => void;
}

const Pagination: React.FC<PaginationProps> = ({
  currentPage,
  totalPages,
  pageSize,
  totalItems,
  onPageChange,
}) => {
  if (totalPages <= 1) {
    return null;
  }

  const { start, end, total } = formatPaginationText(currentPage, pageSize, totalItems);
  const pageNumbers = calculatePaginationRange(currentPage, totalPages);

  return (
    <div className="bg-white px-4 py-3 border-t border-gray-200 sm:px-6">
      <div className="flex items-center justify-between">
        {/* Mobile pagination */}
        <div className="flex-1 flex justify-between sm:hidden">
          <button
            onClick={() => onPageChange(currentPage - 1)}
            disabled={currentPage === 1}
            className="relative inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Previous
          </button>
          <button
            onClick={() => onPageChange(currentPage + 1)}
            disabled={currentPage === totalPages}
            className="ml-3 relative inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Next
          </button>
        </div>

        {/* Desktop pagination */}
        <div className="hidden sm:flex-1 sm:flex sm:items-center sm:justify-between">
          <div>
            <p className="text-sm text-gray-700">
              Showing <span className="font-medium">{start}</span> to{' '}
              <span className="font-medium">{end}</span> of{' '}
              <span className="font-medium">{total}</span> results
            </p>
          </div>
          <div>
            <nav className="relative z-0 inline-flex rounded-md shadow-sm -space-x-px" aria-label="Pagination">
              <PaginationButton
                onClick={() => onPageChange(currentPage - 1)}
                disabled={currentPage === 1}
                className="rounded-l-md"
                ariaLabel="Previous"
              >
                <ChevronLeft className="h-5 w-5" />
              </PaginationButton>

              {pageNumbers.map((pageNum) => (
                <PaginationButton
                  key={pageNum}
                  onClick={() => onPageChange(pageNum)}
                  isActive={currentPage === pageNum}
                  className="px-4 py-2"
                >
                  {pageNum}
                </PaginationButton>
              ))}

              <PaginationButton
                onClick={() => onPageChange(currentPage + 1)}
                disabled={currentPage === totalPages}
                className="rounded-r-md"
                ariaLabel="Next"
              >
                <ChevronRight className="h-5 w-5" />
              </PaginationButton>
            </nav>
          </div>
        </div>
      </div>
    </div>
  );
};

interface PaginationButtonProps {
  onClick: () => void;
  disabled?: boolean;
  isActive?: boolean;
  className?: string;
  ariaLabel?: string;
  children: React.ReactNode;
}

const PaginationButton: React.FC<PaginationButtonProps> = ({
  onClick,
  disabled = false,
  isActive = false,
  className = '',
  ariaLabel,
  children,
}) => {
  const baseClasses = 'relative inline-flex items-center px-2 py-2 border text-sm font-medium';
  const activeClasses = isActive
    ? 'z-10 bg-blue-50 border-blue-500 text-blue-600'
    : 'bg-white border-gray-300 text-gray-500 hover:bg-gray-50';
  const disabledClasses = disabled ? 'opacity-50 cursor-not-allowed' : '';

  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`${baseClasses} ${activeClasses} ${disabledClasses} ${className}`}
      aria-label={ariaLabel}
    >
      {ariaLabel && <span className="sr-only">{ariaLabel}</span>}
      {children}
    </button>
  );
};

export default Pagination;