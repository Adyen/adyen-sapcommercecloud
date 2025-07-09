"use client"

import React from 'react';
import Card from '../shared/Card';

// Payment Method Tile Component
const PaymentMethodTile = ({ name, iconUrl }: { name: string, iconUrl: string }) => (
  <div className="text-center w-24">
    <div className="flex items-center justify-center h-16 w-16 mx-auto border rounded-md">
      <img src={iconUrl} alt={name} className="h-8"/>
    </div>
    <p className="text-xs mt-1 truncate">{name}</p>
  </div>
);

// Payment Method Group Component
const PaymentMethodGroup = ({ 
  merchantAccount, 
  paymentMethods 
}: { 
  merchantAccount: string, 
  paymentMethods: Array<{ name: string, iconUrl: string }> 
}) => (
  <div className="mb-6">
    <h3 className="text-sm font-medium text-gray-700 mb-3">{merchantAccount}</h3>
    <div className="flex flex-wrap gap-4">
      {paymentMethods.map((method, index) => (
        <PaymentMethodTile 
          key={`${merchantAccount}-${method.name}-${index}`} 
          name={method.name} 
          iconUrl={method.iconUrl} 
        />
      ))}
    </div>
  </div>
);

// Static data for payment methods
const paymentMethodsData = [
  {
    merchantAccount: "Adyen Demo Merchant",
    paymentMethods: [
      { name: "Credit Card", iconUrl: "/credit-card.svg" },
      { name: "PayPal", iconUrl: "/paypal.svg" },
      { name: "Apple Pay", iconUrl: "/apple-pay.svg" },
      { name: "Google Pay", iconUrl: "/google-pay.svg" },
      { name: "iDEAL", iconUrl: "/ideal.svg" }
    ]
  },
  {
    merchantAccount: "Adyen Fashion Store",
    paymentMethods: [
      { name: "Credit Card", iconUrl: "/credit-card.svg" },
      { name: "Klarna", iconUrl: "/klarna.svg" },
      { name: "Afterpay", iconUrl: "/afterpay.svg" },
      { name: "Sofort", iconUrl: "/sofort.svg" }
    ]
  }
];

// Main Payment Methods Card Component
const PaymentMethodsCard = () => {
  return (
    <Card title="Add payment methods">
      <div className="space-y-4">
        <p className="text-gray-600">Configure the payment methods you want to accept for each merchant account.</p>
        
        <div className="mt-6">
          {paymentMethodsData.map((group, index) => (
            <PaymentMethodGroup 
              key={`group-${index}`}
              merchantAccount={group.merchantAccount} 
              paymentMethods={group.paymentMethods} 
            />
          ))}
        </div>
        
        <div className="mt-4">
          <button className="text-blue-600 text-sm font-medium flex items-center">
            + Add more payment methods
          </button>
        </div>
      </div>
    </Card>
  );
};

export default PaymentMethodsCard;