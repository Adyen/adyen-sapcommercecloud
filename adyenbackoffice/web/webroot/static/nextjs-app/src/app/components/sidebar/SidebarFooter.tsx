import React from 'react';
import { ChevronLeft, ChevronRight, User, LogOut, Settings } from 'lucide-react';

interface SidebarFooterProps {
  isCollapsed: boolean;
  onToggle: () => void;
}

const SidebarFooter: React.FC<SidebarFooterProps> = ({ isCollapsed, onToggle }) => {
  // Mock user data
  const user = {
    name: 'Admin User',
    email: 'admin@example.com',
    role: 'Administrator'
  };

  return (
    <div className="mt-auto border-t border-gray-700 p-4">
      {/* User Profile */}
      <div className="flex items-center mb-4">
        <div className="w-8 h-8 bg-blue-500 rounded-full flex items-center justify-center text-white font-bold flex-shrink-0">
          <User className="h-4 w-4" />
        </div>
        
        {!isCollapsed && (
          <div className="ml-3 overflow-hidden">
            <p className="text-sm font-medium text-white truncate">{user.name}</p>
            <p className="text-xs text-gray-400 truncate">{user.email}</p>
          </div>
        )}
      </div>
      
      {/* Action Buttons */}
      {!isCollapsed && (
        <div className="flex space-x-2 mb-4">
          <button className="flex items-center justify-center w-full p-2 text-xs text-gray-400 hover:text-white rounded-md hover:bg-gray-700 transition-colors">
            <Settings className="h-4 w-4 mr-2" />
            Settings
          </button>
          <button className="flex items-center justify-center w-full p-2 text-xs text-gray-400 hover:text-white rounded-md hover:bg-gray-700 transition-colors">
            <LogOut className="h-4 w-4 mr-2" />
            Logout
          </button>
        </div>
      )}
      
      {/* Collapse/Expand Button */}
      <button 
        onClick={onToggle}
        className="flex items-center justify-center w-full p-2 text-xs text-gray-400 hover:text-white rounded-md hover:bg-gray-700 transition-colors"
        aria-label={isCollapsed ? "Expand sidebar" : "Collapse sidebar"}
      >
        {isCollapsed ? (
          <ChevronRight className="h-4 w-4" />
        ) : (
          <>
            <ChevronLeft className="h-4 w-4 mr-2" />
            Collapse
          </>
        )}
      </button>
    </div>
  );
};

export default SidebarFooter;