'use client';

import React from 'react';
import { useMerchants } from './hooks/useMerchants';
import LoadingSpinner from './components/LoadingSpinner';
import ErrorMessage from './components/ErrorMessage';
import PageHeader from './components/PageHeader';
import SearchAndControls from './components/SearchAndControls';
import MerchantsTable from './components/MerchantsTable';
import Pagination from './components/Pagination';

const MerchantsPage: React.FC = () => {
  const {
    merchants,
    loading,
    paginationLoading,
    error,
    currentPage,
    pageSize,
    totalItems,
    totalPages,
    searchTerm,
    filters,
    setSearchTerm,
    setFilters,
    handlePageChange,
    handlePageSizeChange,
    filteredMerchants,
  } = useMerchants();

  if (loading) {
    return <LoadingSpinner />;
  }

  if (error) {
    return <ErrorMessage error={error} />;
  }

  return (
    <div className="w-full">
      <div className="p-8">
        <PageHeader />
        
        <SearchAndControls
          searchTerm={searchTerm}
          onSearchChange={setSearchTerm}
          pageSize={pageSize}
          onPageSizeChange={handlePageSizeChange}
          filters={filters}
          onFiltersChange={setFilters}
          merchants={merchants}
        />

        <div className="space-y-0">
          <MerchantsTable
            merchants={filteredMerchants}
            searchTerm={searchTerm}
            loading={paginationLoading}
          />
          
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            pageSize={pageSize}
            totalItems={totalItems}
            onPageChange={handlePageChange}
          />
        </div>
      </div>
    </div>
  );
};

export default MerchantsPage;