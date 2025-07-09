import { ChevronDown } from 'lucide-react';
import React from 'react';
import Link from 'next/link';

interface NavigationItemProps {
  icon: React.ElementType;
  label: string;
  href?: string;
  isActive?: boolean;
  isStatic?: boolean;
  isExpandable?: boolean;
  isCollapsed?: boolean;
}

const NavigationItem: React.FC<NavigationItemProps> = ({ 
  icon: Icon, 
  label, 
  href, 
  isActive, 
  isStatic, 
  isExpandable,
  isCollapsed
}) => {
  const baseClasses = 'flex items-center w-full text-left p-2 rounded-md hover:bg-gray-700 hover:text-white transition-colors';
  const activeClasses = isActive ? 'bg-gray-700 text-white font-semibold' : 'text-gray-400';
  const staticClasses = isStatic ? 'text-xs uppercase font-bold text-gray-500 hover:bg-transparent cursor-default' : '';
  
  const content = (
    <>
      <Icon className="h-5 w-5 mr-3 flex-shrink-0" />
      {!isCollapsed && (
        <>
          <span className="flex-grow truncate">{label}</span>
          {isExpandable && <ChevronDown className="h-4 w-4 ml-2" />}
        </>
      )}
    </>
  );

  if (href && !isStatic) {
    return (
      <Link 
        href={href} 
        className={`${baseClasses} ${activeClasses} ${staticClasses}`}
        title={isCollapsed ? label : undefined}
      >
        {content}
      </Link>
    );
  }

  return (
    <button 
      className={`${baseClasses} ${activeClasses} ${staticClasses}`}
      title={isCollapsed ? label : undefined}
      disabled={isStatic}
    >
      {content}
    </button>
  );
};

export default NavigationItem;