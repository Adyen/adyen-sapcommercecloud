export const getStatusBadgeColor = (status: string): string => {
  switch (status?.toLowerCase()) {
    case 'active':
      return 'bg-green-100 text-green-800';
    case 'inactive':
      return 'bg-red-100 text-red-800';
    case 'onboarding':
      return 'bg-yellow-100 text-yellow-800';
    default:
      return 'bg-gray-100 text-gray-800';
  }
};

export const calculatePaginationRange = (
  currentPage: number,
  totalPages: number,
  maxVisible: number = 5
): number[] => {
  if (totalPages <= maxVisible) {
    return Array.from({ length: totalPages }, (_, i) => i + 1);
  }

  if (currentPage <= 3) {
    return Array.from({ length: maxVisible }, (_, i) => i + 1);
  }

  if (currentPage >= totalPages - 2) {
    return Array.from({ length: maxVisible }, (_, i) => totalPages - maxVisible + 1 + i);
  }

  return Array.from({ length: maxVisible }, (_, i) => currentPage - 2 + i);
};

export const formatPaginationText = (
  currentPage: number,
  pageSize: number,
  totalItems: number
): { start: number; end: number; total: number } => {
  const start = Math.min((currentPage - 1) * pageSize + 1, totalItems);
  const end = Math.min(currentPage * pageSize, totalItems);
  
  return { start, end, total: totalItems };
};