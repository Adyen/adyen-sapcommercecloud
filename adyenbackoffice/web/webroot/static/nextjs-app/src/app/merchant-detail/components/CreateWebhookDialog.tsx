'use client';

import React, { useState } from 'react';
import { X, Plus, AlertCircle } from 'lucide-react';

interface CreateWebhookDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (webhookData: WebhookCreateRequest) => Promise<void>;
  merchantId: string;
}

export interface WebhookCreateRequest {
  type: string;
  description: string;
  url: string;
  active: boolean;
  communicationFormat: string;
  includeEventCodes: string[];
  username?: string;
  password?: string;
}

const WEBHOOK_TYPES = [
  { value: 'standard', label: 'Standard' },
  { value: 'account-settings-notification', label: 'Account Settings Notification' },
  { value: 'banktransfer-notification', label: 'Bank Transfer Notification' },
  { value: 'boletobancario-notification', label: 'Boleto Bancario Notification' },
  { value: 'directdebit-notification', label: 'Direct Debit Notification' },
  { value: 'ach-notification', label: 'ACH Notification' },
  { value: 'pending-notification', label: 'Pending Notification' },
];

const COMMUNICATION_FORMATS = [
  { value: 'soap', label: 'SOAP' },
  { value: 'http', label: 'HTTP POST' },
  { value: 'json', label: 'JSON' },
];

const COMMON_EVENT_CODES = [
  'AUTHORISATION',
  'CANCELLATION',
  'REFUND',
  'CANCEL_OR_REFUND',
  'CAPTURE',
  'CAPTURE_FAILED',
  'REFUND_FAILED',
  'REFUNDED_REVERSED',
  'PAYOUT_THIRDPARTY',
  'PAIDOUT_REVERSED',
  'NOTIFICATION_OF_CHARGEBACK',
  'CHARGEBACK',
  'CHARGEBACK_REVERSED',
  'REPORT_AVAILABLE',
  'ORDER_OPENED',
  'ORDER_CLOSED',
];

const CreateWebhookDialog: React.FC<CreateWebhookDialogProps> = ({
  isOpen,
  onClose,
  onSubmit,
  merchantId,
}) => {
  const [formData, setFormData] = useState<WebhookCreateRequest>({
    type: 'standard',
    description: '',
    url: '',
    active: true,
    communicationFormat: 'json',
    includeEventCodes: [],
    username: '',
    password: '',
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [customEventCode, setCustomEventCode] = useState('');

  const handleInputChange = (field: keyof WebhookCreateRequest, value: any) => {
    setFormData(prev => ({
      ...prev,
      [field]: value,
    }));
    setError(null);
  };

  const handleEventCodeToggle = (eventCode: string) => {
    setFormData(prev => ({
      ...prev,
      includeEventCodes: prev.includeEventCodes.includes(eventCode)
        ? prev.includeEventCodes.filter(code => code !== eventCode)
        : [...prev.includeEventCodes, eventCode],
    }));
  };

  const handleAddCustomEventCode = () => {
    if (customEventCode.trim() && !formData.includeEventCodes.includes(customEventCode.trim())) {
      setFormData(prev => ({
        ...prev,
        includeEventCodes: [...prev.includeEventCodes, customEventCode.trim()],
      }));
      setCustomEventCode('');
    }
  };

  const handleRemoveEventCode = (eventCode: string) => {
    setFormData(prev => ({
      ...prev,
      includeEventCodes: prev.includeEventCodes.filter(code => code !== eventCode),
    }));
  };

  const validateForm = (): string | null => {
    if (!formData.description.trim()) {
      return 'Description is required';
    }
    if (!formData.url.trim()) {
      return 'URL is required';
    }
    if (!formData.url.match(/^https?:\/\/.+/)) {
      return 'URL must be a valid HTTP or HTTPS URL';
    }
    if (formData.includeEventCodes.length === 0) {
      return 'At least one event code must be selected';
    }
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    const validationError = validateForm();
    if (validationError) {
      setError(validationError);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      // Remove empty username/password if not provided
      const submitData = {
        ...formData,
        username: formData.username?.trim() || undefined,
        password: formData.password?.trim() || undefined,
      };

      await onSubmit(submitData);
      handleClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create webhook');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setFormData({
      type: 'standard',
      description: '',
      url: '',
      active: true,
      communicationFormat: 'json',
      includeEventCodes: [],
      username: '',
      password: '',
    });
    setError(null);
    setCustomEventCode('');
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h2 className="text-xl font-semibold text-gray-900">Create New Webhook</h2>
          <button
            onClick={handleClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <X className="h-6 w-6" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-6">
          {error && (
            <div className="bg-red-50 border border-red-200 rounded-md p-4">
              <div className="flex">
                <AlertCircle className="h-5 w-5 text-red-400" />
                <div className="ml-3">
                  <p className="text-sm text-red-800">{error}</p>
                </div>
              </div>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Type *
              </label>
              <select
                value={formData.type}
                onChange={(e) => handleInputChange('type', e.target.value)}
                className="w-full px-3 text-gray-900 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                required
              >
                {WEBHOOK_TYPES.map((type) => (
                  <option key={type.value} value={type.value}>
                    {type.label}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Communication Format *
              </label>
              <select
                value={formData.communicationFormat}
                onChange={(e) => handleInputChange('communicationFormat', e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 text-gray-900 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                required
              >
                {COMMUNICATION_FORMATS.map((format) => (
                  <option key={format.value} value={format.value}>
                    {format.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Description *
            </label>
            <input
              type="text"
              value={formData.description}
              onChange={(e) => handleInputChange('description', e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              placeholder="Enter webhook description"
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              URL *
            </label>
            <input
              type="url"
              value={formData.url}
              onChange={(e) => handleInputChange('url', e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              placeholder="https://example.com/webhook"
              required
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Username (Optional)
              </label>
              <input
                type="text"
                value={formData.username}
                onChange={(e) => handleInputChange('username', e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="Basic auth username"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Password (Optional)
              </label>
              <input
                type="password"
                value={formData.password}
                onChange={(e) => handleInputChange('password', e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="Basic auth password"
              />
            </div>
          </div>

          <div>
            <div className="flex items-center mb-4">
              <input
                type="checkbox"
                id="active"
                checked={formData.active}
                onChange={(e) => handleInputChange('active', e.target.checked)}
                className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
              />
              <label htmlFor="active" className="ml-2 block text-sm text-gray-900">
                Active
              </label>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Event Codes *
            </label>
            <div className="space-y-4">
              <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
                {COMMON_EVENT_CODES.map((eventCode) => (
                  <label key={eventCode} className="flex items-center">
                    <input
                      type="checkbox"
                      checked={formData.includeEventCodes.includes(eventCode)}
                      onChange={() => handleEventCodeToggle(eventCode)}
                      className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
                    />
                    <span className="ml-2 text-sm text-gray-700">{eventCode}</span>
                  </label>
                ))}
              </div>

              <div className="flex gap-2">
                <input
                  type="text"
                  value={customEventCode}
                  onChange={(e) => setCustomEventCode(e.target.value)}
                  className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="Add custom event code"
                  onKeyPress={(e) => e.key === 'Enter' && (e.preventDefault(), handleAddCustomEventCode())}
                />
                <button
                  type="button"
                  onClick={handleAddCustomEventCode}
                  className="px-4 py-2 bg-gray-100 text-gray-700 rounded-md hover:bg-gray-200 transition-colors"
                >
                  <Plus className="h-4 w-4" />
                </button>
              </div>

              {formData.includeEventCodes.length > 0 && (
                <div className="flex flex-wrap gap-2">
                  {formData.includeEventCodes.map((eventCode) => (
                    <span
                      key={eventCode}
                      className="inline-flex items-center px-3 py-1 rounded-full text-sm bg-blue-100 text-blue-800"
                    >
                      {eventCode}
                      <button
                        type="button"
                        onClick={() => handleRemoveEventCode(eventCode)}
                        className="ml-2 text-blue-600 hover:text-blue-800"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div className="flex justify-end space-x-4 pt-6 border-t border-gray-200">
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2 text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition-colors"
              disabled={loading}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Creating...' : 'Create Webhook'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateWebhookDialog;