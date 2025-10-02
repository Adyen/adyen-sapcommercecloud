'use client';

import React, { useState, useEffect } from 'react';
import { Globe, AlertCircle, CheckCircle, XCircle, ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import { WebhookResponse, WebhookData } from '../types/webhook.types';
import LoadingSpinner from '../../merchants/components/LoadingSpinner';
import ErrorMessage from '../../merchants/components/ErrorMessage';
import CreateWebhookDialog, { WebhookCreateRequest } from './CreateWebhookDialog';

interface WebhooksListProps {
  merchantId: string;
}

const WebhooksList: React.FC<WebhooksListProps> = ({ merchantId }) => {
  const [webhooks, setWebhooks] = useState<WebhookData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [hasNextPage, setHasNextPage] = useState(false);
  const [hasPrevPage, setHasPrevPage] = useState(false);
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const pageSize = 10;

  const fetchWebhooks = async (page: number = 1) => {
    try {
      setLoading(true);
      setError(null);
      
      const response = await fetch(
        `/adyenbackoffice/api/webhooks/merchants/${merchantId}?pageSize=${pageSize}&pageNumber=${page}`
      );
      
      if (!response.ok) {
        if (response.status === 404) {
          setError('No webhooks found for this merchant');
          return;
        }
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const webhookData: WebhookResponse = await response.json();
      setWebhooks(webhookData.data || []);
      
      // Set pagination state based on links
      setHasNextPage(!!webhookData._links?.next);
      setHasPrevPage(!!webhookData._links?.prev);
      
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch webhooks');
    } finally {
      setLoading(false);
    }
  };

  const createWebhook = async (webhookData: WebhookCreateRequest) => {
    try {
      const response = await fetch(
        `/adyenbackoffice/api/webhooks/merchants/${merchantId}`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(webhookData),
        }
      );

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to create webhook: ${response.status} ${errorText}`);
      }

      // Refresh the webhooks list after successful creation
      await fetchWebhooks(currentPage);
    } catch (err) {
      throw err; // Re-throw to be handled by the dialog
    }
  };

  useEffect(() => {
    if (merchantId) {
      fetchWebhooks(currentPage);
    }
  }, [merchantId, currentPage]);

  const handleNextPage = () => {
    if (hasNextPage) {
      setCurrentPage(prev => prev + 1);
    }
  };

  const handlePrevPage = () => {
    if (hasPrevPage) {
      setCurrentPage(prev => prev - 1);
    }
  };

  const getStatusIcon = (webhook: WebhookData) => {
    if (webhook.hasError) {
      return <XCircle className="h-5 w-5 text-red-500" />;
    }
    if (webhook.active) {
      return <CheckCircle className="h-5 w-5 text-green-500" />;
    }
    return <AlertCircle className="h-5 w-5 text-yellow-500" />;
  };

  const getStatusText = (webhook: WebhookData) => {
    if (webhook.hasError) {
      return 'Error';
    }
    if (webhook.active) {
      return 'Active';
    }
    return 'Inactive';
  };

  const getStatusColor = (webhook: WebhookData) => {
    if (webhook.hasError) {
      return 'bg-red-100 text-red-800';
    }
    if (webhook.active) {
      return 'bg-green-100 text-green-800';
    }
    return 'bg-yellow-100 text-yellow-800';
  };

  if (loading) {
    return (
      <div className="bg-white rounded-lg shadow-sm border border-gray-200">
        <div className="px-6 py-4 border-b border-gray-200">
          <div className="flex items-center">
            <Globe className="h-5 w-5 text-gray-400 mr-2" />
            <h3 className="text-lg font-medium text-gray-900">Webhooks</h3>
          </div>
        </div>
        <div className="p-8">
          <LoadingSpinner />
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white rounded-lg shadow-sm border border-gray-200">
        <div className="px-6 py-4 border-b border-gray-200">
          <div className="flex items-center">
            <Globe className="h-5 w-5 text-gray-400 mr-2" />
            <h3 className="text-lg font-medium text-gray-900">Webhooks</h3>
          </div>
        </div>
        <div className="p-8">
          <ErrorMessage error={error} />
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200">
      <div className="px-6 py-4 border-b border-gray-200">
        <div className="flex items-center justify-between">
          <div className="flex items-center">
            <Globe className="h-5 w-5 text-gray-400 mr-2" />
            <h3 className="text-lg font-medium text-gray-900">Webhooks</h3>
          </div>
          <div className="flex items-center space-x-4">
            <span className="text-sm text-gray-500">
              {webhooks.length} webhook{webhooks.length !== 1 ? 's' : ''}
            </span>
            <button
              onClick={() => setIsCreateDialogOpen(true)}
              className="inline-flex items-center px-3 py-2 border border-transparent text-sm leading-4 font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors"
            >
              <Plus className="h-4 w-4 mr-1" />
              Create Webhook
            </button>
          </div>
        </div>
      </div>

      {webhooks.length === 0 ? (
        <div className="p-8 text-center">
          <Globe className="mx-auto h-12 w-12 text-gray-400" />
          <h3 className="mt-2 text-sm font-medium text-gray-900">No webhooks configured</h3>
          <p className="mt-1 text-sm text-gray-500">
            This merchant account has no webhook configurations.
          </p>
          <div className="mt-6">
            <button
              onClick={() => setIsCreateDialogOpen(true)}
              className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors"
            >
              <Plus className="h-4 w-4 mr-2" />
              Create Your First Webhook
            </button>
          </div>
        </div>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Webhook ID
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Description
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    URL
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Status
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Subscribed Events
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {webhooks.map((webhook) => (
                  <tr key={webhook.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                      {webhook.id}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-900">
                      <div className="max-w-xs truncate" title={webhook.description}>
                        {webhook.description || 'No description'}
                      </div>
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-900">
                      <div className="max-w-xs truncate" title={webhook.url}>
                        <a 
                          href={webhook.url} 
                          target="_blank" 
                          rel="noopener noreferrer"
                          className="text-blue-600 hover:text-blue-800 hover:underline"
                        >
                          {webhook.url}
                        </a>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center">
                        {getStatusIcon(webhook)}
                        <span className={`ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${getStatusColor(webhook)}`}>
                          {getStatusText(webhook)}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-900">
                      <div className="max-w-xs">
                        {webhook.includeEventCodes && webhook.includeEventCodes.length > 0 ? (
                          <div className="flex flex-wrap gap-1">
                            {webhook.includeEventCodes.slice(0, 3).map((event, index) => (
                              <span 
                                key={index}
                                className="inline-flex items-center px-2 py-1 rounded-md text-xs font-medium bg-blue-100 text-blue-800"
                              >
                                {event}
                              </span>
                            ))}
                            {webhook.includeEventCodes.length > 3 && (
                              <span 
                                className="inline-flex items-center px-2 py-1 rounded-md text-xs font-medium bg-gray-100 text-gray-800"
                                title={webhook.includeEventCodes.slice(3).join(', ')}
                              >
                                +{webhook.includeEventCodes.length - 3} more
                              </span>
                            )}
                          </div>
                        ) : (
                          <span className="text-gray-500 italic">No events configured</span>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {(hasNextPage || hasPrevPage) && (
            <div className="px-6 py-4 border-t border-gray-200">
              <div className="flex items-center justify-between">
                <div className="text-sm text-gray-700">
                  Page {currentPage}
                </div>
                <div className="flex space-x-2">
                  <button
                    onClick={handlePrevPage}
                    disabled={!hasPrevPage}
                    className={`inline-flex items-center px-3 py-2 border border-gray-300 rounded-md text-sm font-medium ${
                      hasPrevPage
                        ? 'text-gray-700 bg-white hover:bg-gray-50'
                        : 'text-gray-400 bg-gray-100 cursor-not-allowed'
                    }`}
                  >
                    <ChevronLeft className="h-4 w-4 mr-1" />
                    Previous
                  </button>
                  <button
                    onClick={handleNextPage}
                    disabled={!hasNextPage}
                    className={`inline-flex items-center px-3 py-2 border border-gray-300 rounded-md text-sm font-medium ${
                      hasNextPage
                        ? 'text-gray-700 bg-white hover:bg-gray-50'
                        : 'text-gray-400 bg-gray-100 cursor-not-allowed'
                    }`}
                  >
                    Next
                    <ChevronRight className="h-4 w-4 ml-1" />
                  </button>
                </div>
              </div>
            </div>
          )}
        </>
      )}

      <CreateWebhookDialog
        isOpen={isCreateDialogOpen}
        onClose={() => setIsCreateDialogOpen(false)}
        onSubmit={createWebhook}
        merchantId={merchantId}
      />
    </div>
  );
};

export default WebhooksList;