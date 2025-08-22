export interface PaymentMethodData {
  id: string;
  type: string;
  name: string;
  description?: string;
  enabled: boolean;
  currencies?: string[];
  countries?: string[];
  configuration?: Record<string, any>;
  storeId?: string;
  businessLineId?: string;
}

export interface PaymentMethodResponse {
  _links?: any;
  data: PaymentMethodData[];
  itemsTotal?: number;
  pagesTotal?: number;
  typesWithErrors?: string[];
}