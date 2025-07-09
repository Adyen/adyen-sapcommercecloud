"use client"

import React, { useState } from 'react';
import AccountSelector from './AccountSelector';
import Navigation from './Navigation';
import SidebarFooter from './SidebarFooter';

const Sidebar: React.FC = () => {
  const [isCollapsed, setIsCollapsed] = useState<boolean>(false);

  const handleToggle = () => {
    setIsCollapsed(!isCollapsed);
  };

  return (
    <aside
      className={`
        sticky top-0 z-10 flex flex-col bg-[#00112c] text-gray-400 h-screen
        transition-all duration-300 ease-in-out
        ${isCollapsed ? 'w-16' : 'w-64'}
      `}
    >
      <AccountSelector isCollapsed={isCollapsed} />
      <Navigation isCollapsed={isCollapsed} />
      <SidebarFooter 
        isCollapsed={isCollapsed} 
        onToggle={handleToggle} 
      />
    </aside>
  );
};

export default Sidebar;