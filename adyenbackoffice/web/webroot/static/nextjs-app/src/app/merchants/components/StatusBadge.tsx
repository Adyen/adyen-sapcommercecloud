import React from 'react';
import { getStatusBadgeColor } from '../utils/merchant.utils';

interface StatusBadgeProps {
  status: string;
}

const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  return (
    <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${getStatusBadgeColor(status)}`}>
      {status || 'Unknown'}
    </span>
  );
};

export default StatusBadge;