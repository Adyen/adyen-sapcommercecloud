'use client';

import { Search, ThumbsUp, Bell, HelpCircle, User, LogOut, ChevronDown } from 'lucide-react';
import React, { useState, useRef, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';

const AppHeader: React.FC = () => {
  const { user } = useAuth();
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);

  // Close the user menu when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target as Node)) {
        setIsUserMenuOpen(false);
      }
    }

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  // Handle logout by redirecting to the spring security logout endpoint
  const handleLogout = () => {
    window.location.href = '/adyenbackoffice/j_spring_security_logout';
    setIsUserMenuOpen(false);
  };

  return (
    <header className="sticky top-0 z-10 bg-white shadow-sm">
      <div className="flex items-center justify-end p-2 h-14">
        {/* Search, Feedback, Notifications, Help, User icons */}
        <div className="flex items-center space-x-4">
           <div className="relative">
               <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-800" />
               <input type="text" placeholder="Search..." className="pl-8 pr-2 py-1 border rounded-md" />
           </div>
           <button className="p-2 rounded-full hover:bg-gray-100"><ThumbsUp className="h-5 w-5 text-gray-800"/></button>
           <button className="p-2 rounded-full hover:bg-gray-100"><Bell className="h-5 w-5 text-gray-800"/></button>
           <button className="p-2 rounded-full hover:bg-gray-100"><HelpCircle className="h-5 w-5 text-gray-800"/></button>
           
            {/* User profile with dropdown */}
            <div className="relative" ref={userMenuRef}>
              <button
                className="flex items-center space-x-2 p-2 rounded-full hover:bg-gray-100 bg-gray-200"
                onClick={() => setIsUserMenuOpen(!isUserMenuOpen)}
              >
                <User className="h-5 w-5 text-gray-800"/>
                {user && (
                  <>
                    <span className="text-sm font-medium text-gray-800 hidden md:block">{user.name}</span>
                    <ChevronDown className="h-4 w-4 text-gray-800 hidden md:block" />
                  </>
                )}
              </button>
              
              {/* Dropdown menu */}
              {isUserMenuOpen && (
                <div className="absolute right-0 mt-2 w-48 bg-white rounded-md shadow-lg py-1 z-20">
                  <div className="px-4 py-2 text-sm text-gray-700 border-b">
                    Signed in as <span className="font-medium text-gray-800">{user?.username}</span>
                  </div>
                  <button
                      onClick={handleLogout}
                      className="flex items-center w-full px-4 py-2 text-sm text-gray-800 hover:bg-gray-100"
                  >
                    <LogOut className="h-4 w-4 mr-2 text-gray-800" />
                    Sign out
                  </button>
                </div>
              )}
            </div>
         </div>
      </div>
    </header>
  );
};

export default AppHeader;
