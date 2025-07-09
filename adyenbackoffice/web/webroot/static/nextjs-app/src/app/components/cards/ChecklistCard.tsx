"use client"

import React from 'react';
import Card from '../shared/Card';
import { FileText, Globe, Settings, CreditCard } from 'lucide-react';

// Checklist Item Component
const ChecklistItem = ({ 
  title, 
  description, 
  icon: Icon, 
  isCompleted 
}: { 
  title: string, 
  description: string, 
  icon: React.ElementType, 
  isCompleted: boolean 
}) => (
  <div className="flex items-start p-4 border rounded-lg">
    <div className="flex-shrink-0 mr-4">
      <div className="h-10 w-10 flex items-center justify-center bg-blue-50 rounded-full">
        <Icon className="h-5 w-5 text-blue-600" />
      </div>
    </div>
    <div className="flex-grow">
      <div className="flex items-center">
        <h3 className="font-medium text-gray-800">{title}</h3>
        {isCompleted && (
          <span className="ml-2 px-2 py-0.5 text-xs font-medium bg-green-100 text-green-800 rounded-full">
            Completed
          </span>
        )}
      </div>
      <p className="text-sm text-gray-600 mt-1">{description}</p>
    </div>
  </div>
);

// Static data for checklist items
const checklistData = [
  {
    title: "Create API credentials",
    description: "Generate API key and client credentials for your integration",
    icon: Settings,
    isCompleted: true
  },
  {
    title: "Set up webhooks",
    description: "Configure webhook endpoints to receive payment notifications",
    icon: Globe,
    isCompleted: true
  },
  {
    title: "Test payment flow",
    description: "Make test payments to verify your integration works correctly",
    icon: CreditCard,
    isCompleted: false
  },
  {
    title: "Review documentation",
    description: "Check implementation guides for best practices",
    icon: FileText,
    isCompleted: false
  }
];

// Main Checklist Card Component
const ChecklistCard = () => {
  const completedItems = checklistData.filter(item => item.isCompleted).length;
  const totalItems = checklistData.length;
  const progressPercentage = Math.round((completedItems / totalItems) * 100);

  return (
    <Card title="Test your integration" isComplete={completedItems === totalItems}>
      <div className="space-y-4">
        <p className="text-gray-600">Complete these steps to verify your integration is working correctly.</p>
        
        <div className="mt-2">
          <div className="flex items-center justify-between mb-1">
            <span className="text-sm text-gray-600">Progress</span>
            <span className="text-sm font-medium text-gray-700">{completedItems}/{totalItems} completed</span>
          </div>
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div 
              className="bg-blue-600 h-2 rounded-full" 
              style={{ width: `${progressPercentage}%` }}
            ></div>
          </div>
        </div>
        
        <div className="mt-6 space-y-3">
          {checklistData.map((item, index) => (
            <ChecklistItem 
              key={`checklist-${index}`}
              title={item.title}
              description={item.description}
              icon={item.icon}
              isCompleted={item.isCompleted}
            />
          ))}
        </div>
        
        <div className="mt-4">
          <button className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors">
            Continue setup
          </button>
        </div>
      </div>
    </Card>
  );
};

export default ChecklistCard;