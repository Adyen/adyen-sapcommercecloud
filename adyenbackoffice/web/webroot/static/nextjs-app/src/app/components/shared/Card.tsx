"use client"

import React, { useState } from 'react';
import { ChevronDown, ChevronUp, CheckCircle2 } from 'lucide-react';

interface CardProps {
  title: string;
  children: React.ReactNode;
  isInitiallyCollapsed?: boolean;
  isComplete?: boolean;
}

const Card: React.FC<CardProps> = ({ title, children, isInitiallyCollapsed = false, isComplete = false }) => {
  const [isCollapsed, setIsCollapsed] = useState(isInitiallyCollapsed);

  return (
    <section className="bg-white border border-gray-200 rounded-lg shadow-sm">
      <header
        className="flex items-center p-4 cursor-pointer"
        onClick={() => setIsCollapsed(!isCollapsed)}
      >
        <button className="p-1 hover:bg-gray-100 rounded-full mr-2">
          {isCollapsed ? <ChevronDown className="h-5 w-5" /> : <ChevronUp className="h-5 w-5" />}
        </button>
        <h2 className="flex-grow text-lg font-semibold text-gray-700">{title}</h2>
        {isComplete && <CheckCircle2 className="h-6 w-6 text-green-500" />}
      </header>
      {!isCollapsed && (
        <div className="p-6 border-t border-gray-200">
          {children}
        </div>
      )}
    </section>
  );
};

export default Card;