import React from 'react';
import { Building2 } from 'lucide-react';

const PageHeader: React.FC = () => {
  return (
    <div className="mb-8">
      <div className="flex items-center mb-4">
        <Building2 className="h-8 w-8 text-blue-600 mr-3" />
        <h1 className="text-3xl font-light text-gray-800">Merchant Accounts</h1>
      </div>
      <p className="text-gray-500">
        Manage and view all merchant accounts that you have permission to access.
      </p>
    </div>
  );
};

export default PageHeader;