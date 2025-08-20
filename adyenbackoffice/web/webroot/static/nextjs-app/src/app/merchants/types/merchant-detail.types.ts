export interface MerchantConfiguration {
  currencies?: string[];
  liveEndpointPrefix?: string;
  testEndpointPrefix?: string;
  allowedOrigins?: string[];
  webhookUrl?: string;
  returnUrl?: string;
}

export interface MerchantDetailData {
  id: string;
  name: string;
  status: string;
  merchantCity: string;
  primarySettlementCurrency: string;
  shopWebAddress: string;
  companyId?: string;
  configuration?: MerchantConfiguration;
}