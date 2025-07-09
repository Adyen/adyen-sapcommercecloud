'use client';

import React from 'react';
import { usePathname } from 'next/navigation';
import Sidebar from '../sidebar/Sidebar';
import AppHeader from '../main/AppHeader';
type AppLayoutProps = {
  children: React.ReactNode;
};

const AppLayout: React.FC<AppLayoutProps> = ({ children }) => {
  const pathname = usePathname();
  const isLoginPage = pathname === '/login';

  // Don't show sidebar and header on login page
  if (isLoginPage) {
    return <>{children}</>;
  }

  // For all other pages, wrap with ProtectedRoute and show full layout
  return (
      <div className="flex min-h-screen bg-gray-50 font-sans">
        <Sidebar />
        <div className="flex-1 flex flex-col">
          <AppHeader />
          <main className="flex-1 p-8 overflow-auto">
            {children}
          </main>
        </div>
      </div>
  );
};

export default AppLayout;