import { useState, useEffect } from 'react';
import { MerchantData, MerchantResponse, MerchantFilters } from '../types/merchant.types';

interface UseMerchantsReturn {
  merchants: MerchantData[];
  loading: boolean;
  error: string | null;
  currentPage: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
  searchTerm: string;
  filters: MerchantFilters;
  setCurrentPage: (page: number) => void;
  setPageSize: (size: number) => void;
  setSearchTerm: (term: string) => void;
  setFilters: (filters: MerchantFilters) => void;
  handlePageChange: (newPage: number) => void;
  handlePageSizeChange: (newSize: number) => void;
  filteredMerchants: MerchantData[];
}

export const useMerchants = (): UseMerchantsReturn => {
  const [merchants, setMerchants] = useState<MerchantData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const [filters, setFilters] = useState<MerchantFilters>({
    status: '',
    location: '',
    currency: '',
  });

  const fetchMerchants = async (page: number, size: number) => {
    try {
      setLoading(true);
      const response = await fetch(`/adyenbackoffice/api/merchants?pageNumber=${page}&pageSize=${size}`);
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const data: MerchantResponse = await response.json();
      setMerchants(data.data || []);
      setTotalItems(data.itemsTotal || 0);
      setTotalPages(data.pagesTotal || 0);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch merchants');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMerchants(currentPage, pageSize);
  }, [currentPage, pageSize]);

  const handlePageChange = (newPage: number) => {
    if (newPage >= 1 && newPage <= totalPages) {
      setCurrentPage(newPage);
    }
  };

  const handlePageSizeChange = (newSize: number) => {
    setPageSize(newSize);
    setCurrentPage(1); // Reset to first page when changing page size
  };

  const filteredMerchants = merchants.filter(merchant => {
    // Search filter
    const matchesSearch = !searchTerm ||
      merchant.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      merchant.id?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      merchant.merchantCity?.toLowerCase().includes(searchTerm.toLowerCase());

    // Status filter
    const matchesStatus = !filters.status ||
      merchant.status?.toLowerCase() === filters.status.toLowerCase();

    // Location filter
    const matchesLocation = !filters.location ||
      merchant.merchantCity?.toLowerCase().includes(filters.location.toLowerCase());

    // Currency filter
    const matchesCurrency = !filters.currency ||
      merchant.primarySettlementCurrency?.toLowerCase() === filters.currency.toLowerCase();

    return matchesSearch && matchesStatus && matchesLocation && matchesCurrency;
  });

  return {
    merchants,
    loading,
    error,
    currentPage,
    pageSize,
    totalItems,
    totalPages,
    searchTerm,
    filters,
    setCurrentPage,
    setPageSize,
    setSearchTerm,
    setFilters,
    handlePageChange,
    handlePageSizeChange,
    filteredMerchants,
  };
};