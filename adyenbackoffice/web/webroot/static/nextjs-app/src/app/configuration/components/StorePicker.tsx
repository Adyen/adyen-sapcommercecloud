'use client';

import React from 'react';
import { Store } from 'lucide-react';
import { StoreSetup } from '../types/setup.types';

interface StorePickerProps {
  stores: StoreSetup[];
  selectedUid: string;
  onSelect: (uid: string) => void;
}

const StorePicker: React.FC<StorePickerProps> = ({ stores, selectedUid, onSelect }) => (
  <div className="bg-white rounded-lg shadow-sm border border-gray-200">
    <div className="px-6 py-4 border-b border-gray-200 flex items-center">
      <Store className="h-5 w-5 text-gray-400 mr-2" />
      <h2 className="text-lg font-medium text-gray-900">Base store</h2>
    </div>
    <div className="p-6">
      <label htmlFor="baseStore" className="block text-sm font-medium text-gray-700 mb-2">
        Store to configure *
      </label>
      <select
        id="baseStore"
        value={selectedUid}
        onChange={(e) => onSelect(e.target.value)}
        className="w-full px-3 py-2 border border-gray-300 rounded-md text-gray-900 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
      >
        {stores.map((store) => (
          <option key={store.uid} value={store.uid}>
            {store.name || store.uid} ({store.uid}){store.configured ? ' — configured' : ''}
          </option>
        ))}
      </select>
      <p className="mt-2 text-xs text-gray-500">
        Adyen credentials are held per base store, so each store is configured separately.
      </p>
    </div>
  </div>
);

export default StorePicker;
