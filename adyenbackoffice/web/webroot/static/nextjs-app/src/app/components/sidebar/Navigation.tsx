import React from 'react';
import NavigationItem from './NavigationItem';
import { 
  LayoutDashboard, 
  CreditCard, 
  Settings, 
  BarChart, 
  Webhook, 
  AlertCircle, 
  FileText,
  Building2 
} from 'lucide-react';

interface NavigationProps {
  isCollapsed: boolean;
}

const Navigation: React.FC<NavigationProps> = ({ isCollapsed }) => {
  // Navigation sections with their items
  const navigationSections = [
    {
      title: 'Main',
      items: [
        { 
          label: 'Dashboard', 
          icon: LayoutDashboard, 
          href: '/', 
          isActive: true 
        },
        { 
          label: 'Payments', 
          icon: CreditCard, 
          href: '/payments' 
        },
        { 
          label: 'Merchants', 
          icon: Building2, 
          href: '/merchants' 
        },
        { 
          label: 'Configuration', 
          icon: Settings, 
          href: '/configuration',
          isExpandable: true
        },
      ]
    },
    {
      title: 'Analytics',
      items: [
        { 
          label: 'Reports', 
          icon: BarChart, 
          href: '/analytics' 
        },
        { 
          label: 'Webhooks', 
          icon: Webhook, 
          href: '/webhooks' 
        },
        { 
          label: 'Error Logs', 
          icon: AlertCircle, 
          href: '/error-logs' 
        },
      ]
    },
    {
      title: 'Support',
      items: [
        { 
          label: 'Documentation', 
          icon: FileText, 
          href: '/documentation' 
        },
      ]
    }
  ];

  return (
    <nav className="flex-1 overflow-y-auto py-4">
      {navigationSections.map((section) => (
        <div key={section.title} className="mb-6">
          {!isCollapsed && (
            <h3 className="px-4 mb-2 text-xs font-semibold text-gray-500 uppercase">
              {section.title}
            </h3>
          )}
          <ul className="space-y-1 px-2">
            {section.items.map((item) => (
              <li key={item.label}>
                <NavigationItem
                  icon={item.icon}
                  label={item.label}
                  href={item.href}
                  isActive={item.isActive}
                  isExpandable={item.isExpandable}
                  isCollapsed={isCollapsed}
                />
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );
};

export default Navigation;