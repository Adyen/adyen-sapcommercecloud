"use client"

import React from 'react';
import Card from '../shared/Card';
import { ArrowRight } from 'lucide-react';

// Integration Link Component
const IntegrationLink = ({ 
  name, 
  description, 
  iconUrl, 
  url 
}: { 
  name: string, 
  description: string, 
  iconUrl: string, 
  url: string 
}) => (
  <a 
    href={url} 
    className="flex items-start p-4 border rounded-lg hover:shadow-md transition-shadow"
  >
    <div className="flex-shrink-0 mr-4">
      <div className="h-12 w-12 flex items-center justify-center bg-gray-100 rounded-md">
        <img src={iconUrl} alt={name} className="h-8 w-8" />
      </div>
    </div>
    <div className="flex-grow">
      <h3 className="font-medium text-gray-800">{name}</h3>
      <p className="text-sm text-gray-600 mt-1">{description}</p>
    </div>
    <div className="flex-shrink-0 self-center ml-2">
      <ArrowRight className="h-5 w-5 text-gray-400" />
    </div>
  </a>
);

// Static data for integration options
const integrationData = [
  {
    category: "Plugins",
    items: [
      {
        name: "Shopify",
        description: "Connect your Shopify store with our official plugin",
        iconUrl: "/shopify.svg",
        url: "/integrations/shopify"
      },
      {
        name: "Magento",
        description: "Integrate with Magento 2 using our extension",
        iconUrl: "/magento.svg",
        url: "/integrations/magento"
      },
      {
        name: "WooCommerce",
        description: "Add Adyen payments to your WordPress store",
        iconUrl: "/woocommerce.svg",
        url: "/integrations/woocommerce"
      }
    ]
  },
  {
    category: "Web Components",
    items: [
      {
        name: "Drop-in",
        description: "Quick integration with all popular payment methods",
        iconUrl: "/dropin.svg",
        url: "/integrations/drop-in"
      },
      {
        name: "Components",
        description: "Customizable payment components for your checkout",
        iconUrl: "/components.svg",
        url: "/integrations/components"
      }
    ]
  }
];

// Main Integration Card Component
const IntegrationCard = () => {
  return (
    <Card title="Choose your ecommerce integration">
      <div className="space-y-6">
        <p className="text-gray-600">Select the ecommerce platform you want to integrate with Adyen.</p>
        
        {integrationData.map((category, index) => (
          <div key={`category-${index}`} className="mt-6">
            <h3 className="text-sm font-medium text-gray-700 mb-3">{category.category}</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {category.items.map((item, itemIndex) => (
                <IntegrationLink 
                  key={`integration-${index}-${itemIndex}`}
                  name={item.name}
                  description={item.description}
                  iconUrl={item.iconUrl}
                  url={item.url}
                />
              ))}
            </div>
          </div>
        ))}
        
        <div className="mt-4">
          <button className="text-blue-600 text-sm font-medium flex items-center">
            View all integration options
          </button>
        </div>
      </div>
    </Card>
  );
};

export default IntegrationCard;