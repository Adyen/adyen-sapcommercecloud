export interface PaymentMethodSettings {
  id: string;
  type: string;
  name: string;
  description?: string;
  enabled: boolean;
  currencies: string[];
  countries: string[];
  storeId?: string;
  businessLineId?: string;
  merchantId: string;
  shopperInteraction?: string;
  configuration?: Record<string, any>;
  verificationSettings?: Record<string, any>;
  fundingSource?: Record<string, any>;
  cardholderName?: Record<string, any>;
  installmentOptions?: Record<string, any>;
  reference?: Record<string, any>;
  shopperStatement?: Record<string, any>;
  surcharge?: Record<string, any>;
  additionalSettings?: Record<string, any>;
  
  // Specific payment method settings
  applePay?: ApplePaySettings;
  googlePay?: GooglePaySettings;
  paypal?: PayPalSettings;
  card?: CardSettings;
  visa?: VisaSettings;
  amex?: AmexSettings;
  klarna?: KlarnaSettings;
  jcb?: JcbSettings;
  sepadirectdebit?: SepaDirectDebitSettings;
  
  // Common fields
  processingType?: string;
  allowed?: boolean;
  merchantReference?: string;
  verificationStatus?: string;
  storeIds?: string[];
}

// Specific payment method settings interfaces
export interface ApplePaySettings {
  merchantId?: string;
  merchantName?: string;
  domainNames?: string[];
  buttonType?: string;
  buttonStyle?: string;
  checkoutType?: string;
}

export interface GooglePaySettings {
  merchantId?: string;
  merchantName?: string;
  gatewayMerchantId?: string;
  buttonType?: string;
  buttonColor?: string;
  buttonSizeMode?: string;
}

export interface PayPalSettings {
  merchantId?: string;
  clientId?: string;
  intent?: string;
  commit?: boolean;
  vault?: boolean;
  userAction?: string;
}

export interface CardSettings {
  enabledCardTypes?: string[];
  holderNameRequired?: boolean;
  billingAddressRequired?: boolean;
  installments?: Record<string, any>;
  enablePayButton?: boolean;
}

export interface VisaSettings {
  merchantId?: string;
  apiKey?: string;
  sharedSecret?: string;
}

export interface AmexSettings {
  merchantId?: string;
  apiKey?: string;
  sharedSecret?: string;
}

export interface KlarnaSettings {
  merchantId?: string;
  sharedSecret?: string;
  testMode?: boolean;
}

export interface JcbSettings {
  merchantId?: string;
  apiKey?: string;
  sharedSecret?: string;
}

export interface SepaDirectDebitSettings {
  merchantId?: string;
  creditorId?: string;
  creditorName?: string;
}