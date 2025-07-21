'use client';

import IntegrationCard from './components/cards/IntegrationCard';
import PaymentMethodsCard from './components/cards/PaymentMethodsCard';
import ChecklistCard from './components/cards/ChecklistCard';
import Card from './components/shared/Card';
import { useAuth } from './context/AuthContext';

export default function Home() {
  const { user } = useAuth();
  const userName = user?.name || 'User';

  return (
    <div className="w-full">
      <div className="p-8">
        <h1 className="text-3xl font-light text-gray-800">Welcome, {userName}!</h1>
        <p className="text-gray-500 mt-1">
          Follow the steps below to start processing your payments with Adyen.
        </p>
        <div className="mt-8 space-y-6">
          <IntegrationCard />
          
          <Card title="Choose your point-of-sale integration" isInitiallyCollapsed>
            <div className="space-y-4">
              <p className="text-gray-600">Select your preferred point-of-sale solution.</p>
              {/* POS integration content would go here */}
            </div>
          </Card>
          
          <PaymentMethodsCard />
          
          <ChecklistCard />
        </div>
      </div>
    </div>
  );
}
