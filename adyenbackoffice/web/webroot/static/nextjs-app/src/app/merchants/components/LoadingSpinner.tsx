import React from 'react';

const LoadingSpinner: React.FC = () => {
  return (
    <div className="w-full">
      <div className="p-8">
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
        </div>
      </div>
    </div>
  );
};

export default LoadingSpinner;