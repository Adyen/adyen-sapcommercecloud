export interface MerchantData {
  id: string;
  name: string;
  status: string;
  merchantCity: string;
  primarySettlementCurrency: string;
  shopWebAddress: string;
}

export interface MerchantResponse {
  data: MerchantData[];
  itemsTotal: number;
  pagesTotal: number;
  _links: {
    self?: { href: string };
    next?: { href: string };
    prev?: { href: string };
    first?: { href: string };
    last?: { href: string };
  };
}

export type MerchantStatus = 'active' | 'inactive' | 'onboarding';

export interface MerchantFilters {
  status: string;
  location: string;
  currency: string;
}

export interface FilterOption {
  value: string;
  label: string;
}